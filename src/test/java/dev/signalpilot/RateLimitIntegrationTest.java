package dev.signalpilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:rate-limit;DB_CLOSE_DELAY=-1",
      "signalpilot.operator-token=rate-test-token",
      "signalpilot.mcp-token=mcp-rate-test-token",
      "signalpilot.rate-limit-per-minute=3",
      "signalpilot.ai-provider=local"
    })
class RateLimitIntegrationTest {
  @LocalServerPort int port;

  @Test
  void appliesPerPrincipalRateLimit() throws Exception {
    var client = HttpClient.newHttpClient();
    for (int attempt = 0; attempt < 3; attempt++) {
      assertThat(send(client).statusCode()).isEqualTo(200);
    }
    var limited = send(client);
    assertThat(limited.statusCode()).isEqualTo(429);
    int retryAfter =
        Integer.parseInt(limited.headers().firstValue("Retry-After").orElseThrow());
    assertThat(retryAfter).isBetween(1, 60);
  }

  private HttpResponse<String> send(HttpClient client) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/incidents"))
            .header("Authorization", "Bearer rate-test-token")
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
