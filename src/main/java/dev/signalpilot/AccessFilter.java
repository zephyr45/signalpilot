package dev.signalpilot;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AccessFilter extends OncePerRequestFilter {
  private final String token, origin;

  public AccessFilter(
      @Value("${signalpilot.token}") String token, @Value("${signalpilot.origin}") String origin) {
    this.token = token;
    this.origin = origin;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("Referrer-Policy", "no-referrer");
    res.setHeader(
        "Content-Security-Policy",
        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src"
            + " 'self'; frame-ancestors 'none'; base-uri 'self'");
    String path = req.getRequestURI();
    if (path.startsWith("/api/") || path.equals("/mcp")) {
      res.setHeader("Cache-Control", "no-store");
      String o = req.getHeader("Origin");
      if (o != null
          && !java.util.List.of(origin, "http://localhost:8091", "http://127.0.0.1:8091")
              .contains(o)) {
        reject(res, 403, "Origin not allowed");
        return;
      }
      if (token.isBlank()
          && !Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1").contains(req.getRemoteAddr())) {
        reject(res, 403, "Configure SIGNALPILOT_TOKEN for remote access");
        return;
      }
      if (!token.isBlank()
          && !MessageDigest.isEqual(
              ("Bearer " + token).getBytes(StandardCharsets.UTF_8),
              ObjectsSafe.header(req).getBytes(StandardCharsets.UTF_8))) {
        reject(res, 401, "Access token required");
        return;
      }
      if (req.getContentLengthLong() > 131072) {
        reject(res, 413, "Request too large");
        return;
      }
    }
    chain.doFilter(req, res);
  }

  static void reject(HttpServletResponse r, int code, String s) throws IOException {
    r.setStatus(code);
    r.setContentType("application/json");
    r.getWriter().write("{\"error\":\"" + s + "\"}");
  }

  private static class ObjectsSafe {
    static String header(HttpServletRequest r) {
      String h = r.getHeader("Authorization");
      return h == null ? "" : h;
    }
  }
}
