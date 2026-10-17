package dev.signalpilot;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:access;DB_CLOSE_DELAY=-1",
      "signalpilot.operator-token=operator-test-token",
      "signalpilot.mcp-token=mcp-test-token",
      "signalpilot.ai-provider=local"
    })
class AccessIntegrationTest {
  @LocalServerPort int port;

  @Test
  void tokenRequiredForApiAndMcpButNotLandingPage() throws Exception {
    var client = HttpClient.newHttpClient();
    for (String path : new String[] {"/api/incidents", "/mcp"}) {
      var r =
          client.send(
              HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(r.statusCode()).isEqualTo(401);
    }
    var r =
        client.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/incidents"))
                .header("Authorization", "Bearer operator-test-token")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(r.statusCode()).isEqualTo(200);
    r =
        client.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/incidents"))
                .header("Authorization", "Bearer mcp-test-token")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(r.statusCode()).isEqualTo(401);
    r =
        client.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Authorization", "Bearer operator-test-token")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(r.statusCode()).isEqualTo(401);
    r =
        client.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(r.statusCode()).isEqualTo(200);
    assertThat(r.headers().firstValue("Content-Security-Policy")).isPresent();
  }

  @Test
  void effectivePortIsAcceptedAsSameOrigin() throws Exception {
    var r =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/incidents"))
                    .header("Authorization", "Bearer operator-test-token")
                    .header("Origin", "http://127.0.0.1:" + port)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(r.statusCode()).isEqualTo(200);
  }

  @Test
  void chunkedBodiesAreRejectedAtTheConfiguredLimit() throws Exception {
    byte[] body =
        ("{\"scenario\":\"custom\",\"title\":\""
                + "x".repeat(140_000)
                + "\",\"service\":\"checkout\"}")
            .getBytes(StandardCharsets.UTF_8);
    var r =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/incidents"))
                    .header("Authorization", "Bearer operator-test-token")
                    .header("Content-Type", "application/json")
                    .POST(
                        HttpRequest.BodyPublishers.ofInputStream(
                            () -> new ByteArrayInputStream(body)))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(r.statusCode()).isEqualTo(413);
  }
}
