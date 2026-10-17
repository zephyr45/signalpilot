package dev.signalpilot;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AccessFilter extends OncePerRequestFilter {
  public static final String PRINCIPAL_ATTRIBUTE = "signalpilot.principal";
  private static final long BODY_LIMIT_BYTES = 131_072;

  private final String operatorToken;
  private final String mcpToken;
  private final Set<String> configuredOrigins;
  private final int requestsPerMinute;
  private final int briefRequestsPerMinute;
  private final int maxConcurrent;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public AccessFilter(
      @Value("${signalpilot.operator-token:}") String operatorToken,
      @Value("${signalpilot.mcp-token:}") String mcpToken,
      @Value("${signalpilot.origin:}") String origins,
      @Value("${signalpilot.rate-limit-per-minute:120}") int requestsPerMinute,
      @Value("${signalpilot.brief-rate-limit-per-minute:6}") int briefRequestsPerMinute,
      @Value("${signalpilot.max-concurrent-requests:8}") int maxConcurrent) {
    this.operatorToken = operatorToken.strip();
    this.mcpToken = mcpToken.strip();
    if (!this.operatorToken.isBlank() && this.operatorToken.equals(this.mcpToken)) {
      throw new IllegalStateException(
          "SIGNALPILOT_OPERATOR_TOKEN and SIGNALPILOT_MCP_TOKEN must be different");
    }
    this.configuredOrigins =
        origins.isBlank()
            ? Set.of()
            : Arrays.stream(origins.split(","))
                .map(String::strip)
                .filter(s -> !s.isBlank())
                .map(AccessFilter::normalizeOrigin)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    this.requestsPerMinute = positive(requestsPerMinute, "rate limit");
    this.briefRequestsPerMinute = positive(briefRequestsPerMinute, "brief rate limit");
    this.maxConcurrent = positive(maxConcurrent, "concurrency limit");
  }

  private static int positive(int value, String name) {
    if (value < 1) throw new IllegalArgumentException(name + " must be positive");
    return value;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    securityHeaders(res);
    String path = req.getRequestURI();
    if (!path.startsWith("/api/") && !path.equals("/mcp")) {
      chain.doFilter(req, res);
      return;
    }

    res.setHeader("Cache-Control", "no-store");
    String requestOrigin = req.getHeader("Origin");
    if (requestOrigin != null && !originAllowed(requestOrigin, req)) {
      reject(res, 403, "Origin not allowed");
      return;
    }

    boolean mcp = path.equals("/mcp");
    String requiredToken = mcp ? mcpToken : operatorToken;
    String principal = mcp ? "mcp-client" : "dashboard-operator";
    if (requiredToken.isBlank() && !isLoopback(req.getRemoteAddr())) {
      reject(res, 403, "Configure a scoped access token for remote access");
      return;
    }
    if (!requiredToken.isBlank() && !tokenMatches(req, requiredToken)) {
      reject(res, 401, mcp ? "MCP token required" : "Operator token required");
      return;
    }
    req.setAttribute(PRINCIPAL_ATTRIBUTE, principal);

    String rateKey = principal + ":" + identityKey(requiredToken, req.getRemoteAddr());
    int limit = path.matches("/api/incidents/[^/]+/brief") ? briefRequestsPerMinute : requestsPerMinute;
    Window window = windows.computeIfAbsent(rateKey + ":" + limit, ignored -> new Window());
    Acquisition acquisition = window.acquire(limit, maxConcurrent);
    if (!acquisition.accepted()) {
      res.setHeader("Retry-After", Long.toString(acquisition.retryAfterSeconds()));
      reject(res, 429, "Request limit exceeded; retry later");
      return;
    }

    try {
      if (req.getContentLengthLong() > BODY_LIMIT_BYTES) {
        reject(res, 413, "Request too large");
        return;
      }
      HttpServletRequest bounded = req;
      if (hasRequestBody(req)) {
        byte[] body = req.getInputStream().readNBytes((int) BODY_LIMIT_BYTES + 1);
        if (body.length > BODY_LIMIT_BYTES) {
          reject(res, 413, "Request too large");
          return;
        }
        bounded = new CachedBodyRequest(req, body);
      }
      chain.doFilter(bounded, res);
    } finally {
      window.release();
    }
  }

  private static boolean hasRequestBody(HttpServletRequest req) {
    return !Set.of("GET", "HEAD", "OPTIONS").contains(req.getMethod());
  }

  private static void securityHeaders(HttpServletResponse res) {
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("Referrer-Policy", "no-referrer");
    res.setHeader(
        "Content-Security-Policy",
        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src"
            + " 'self'; frame-ancestors 'none'; base-uri 'self'");
  }

  private boolean originAllowed(String origin, HttpServletRequest req) {
    String normalized;
    try {
      normalized = normalizeOrigin(origin);
    } catch (IllegalArgumentException e) {
      return false;
    }
    if (configuredOrigins.contains(normalized)) return true;
    URI parsed = URI.create(normalized);
    int originPort = effectivePort(parsed.getScheme(), parsed.getPort());
    int requestPort = effectivePort(req.getScheme(), req.getServerPort());
    return parsed.getScheme().equalsIgnoreCase(req.getScheme())
        && parsed.getHost() != null
        && parsed.getHost().equalsIgnoreCase(req.getServerName())
        && originPort == requestPort;
  }

  private static String normalizeOrigin(String value) {
    URI uri = URI.create(value);
    if (uri.getScheme() == null
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || (uri.getPath() != null && !uri.getPath().isBlank() && !uri.getPath().equals("/"))
        || uri.getQuery() != null
        || uri.getFragment() != null) throw new IllegalArgumentException("Invalid origin");
    int port = effectivePort(uri.getScheme(), uri.getPort());
    boolean defaultPort =
        (uri.getScheme().equalsIgnoreCase("http") && port == 80)
            || (uri.getScheme().equalsIgnoreCase("https") && port == 443);
    return uri.getScheme().toLowerCase(Locale.ROOT)
        + "://"
        + uri.getHost().toLowerCase(Locale.ROOT)
        + (defaultPort ? "" : ":" + port);
  }

  private static int effectivePort(String scheme, int port) {
    if (port >= 0) return port;
    return "https".equalsIgnoreCase(scheme) ? 443 : 80;
  }

  private static boolean tokenMatches(HttpServletRequest req, String token) {
    return MessageDigest.isEqual(
        ("Bearer " + token).getBytes(StandardCharsets.UTF_8),
        header(req).getBytes(StandardCharsets.UTF_8));
  }

  private static String header(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    return header == null ? "" : header;
  }

  private static boolean isLoopback(String address) {
    return Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1").contains(address);
  }

  private static String identityKey(String token, String remoteAddress) {
    if (token.isBlank()) return remoteAddress;
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 8) + ":" + remoteAddress;
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  static void reject(HttpServletResponse response, int code, String message) throws IOException {
    response.setStatus(code);
    response.setContentType("application/json");
    response.getWriter().write("{\"error\":\"" + message + "\"}");
  }

  private static final class Window {
    private long startedAt = System.currentTimeMillis();
    private int requests;
    private int active;

    synchronized Acquisition acquire(int limit, int concurrency) {
      long now = System.currentTimeMillis();
      if (now - startedAt >= 60_000) {
        startedAt = now;
        requests = 0;
      }
      if (requests >= limit) {
        long remainingMillis = Math.max(1, 60_000 - (now - startedAt));
        return new Acquisition(false, Math.max(1, (remainingMillis + 999) / 1000));
      }
      if (active >= concurrency) return new Acquisition(false, 1);
      requests++;
      active++;
      return new Acquisition(true, 0);
    }

    synchronized void release() {
      active = Math.max(0, active - 1);
    }
  }

  private record Acquisition(boolean accepted, long retryAfterSeconds) {}

  private static final class CachedBodyRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    CachedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    @Override
    public ServletInputStream getInputStream() {
      return new CachedServletInputStream(body);
    }

    @Override
    public BufferedReader getReader() throws IOException {
      String encoding = getCharacterEncoding();
      return new BufferedReader(
          new InputStreamReader(
              getInputStream(),
              encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding)));
    }
  }

  private static final class CachedServletInputStream extends ServletInputStream {
    private final ByteArrayInputStream delegate;

    CachedServletInputStream(byte[] body) {
      this.delegate = new ByteArrayInputStream(body);
    }

    @Override
    public int read() throws IOException {
      int value = delegate.read();
      return value;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      int count = delegate.read(bytes, offset, length);
      return count;
    }

    @Override
    public boolean isFinished() {
      return delegate.available() == 0;
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setReadListener(ReadListener listener) {
      throw new UnsupportedOperationException("Async request reads are not supported");
    }
  }
}
