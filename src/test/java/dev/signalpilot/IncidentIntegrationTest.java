package dev.signalpilot;

import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
      "signalpilot.ai-provider=local",
      "signalpilot.token="
    })
class IncidentIntegrationTest {
  @Autowired IncidentService service;
  @Autowired IncidentTools tools;
  @LocalServerPort int port;
  final JsonMapper json = JsonMapper.builder().build();
  final HttpClient http = HttpClient.newHttpClient();

  Incident fresh(String scenario) {
    return service.create(scenario, "Checkout incident", "checkout-api");
  }

  Incident cmd(Incident i, String op, String value) {
    return service.command(
        i.id, op, value, i.revision, UUID.randomUUID().toString(), "Test operator");
  }

  @Test
  void completeLifecycleRequiresApprovalAndTwoChecks() {
    var i = cmd(fresh("deployment"), "analyze", "");
    var start = i;
    var action = i.actions.getFirst().id;
    assertThatThrownBy(() -> cmd(start, "execute", action))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("approve");
    i = cmd(i, "approve", action);
    i = cmd(i, "execute", action);
    i = cmd(i, "sample", "healthy");
    var one = i;
    assertThatThrownBy(() -> cmd(one, "resolve", "Fixed")).hasMessageContaining("two consecutive");
    i = cmd(i, "sample", "healthy");
    i = cmd(i, "resolve", "Recovered through simulated rollback");
    assertThat(service.get(i.id).status).isEqualTo("RESOLVED");
    assertThat(service.postmortem(i.id))
        .contains("E2", "APPROVED", "SIMULATED", "Two consecutive", "Follow-up actions");
    var resolved = i;
    assertThatThrownBy(() -> cmd(resolved, "note", "extra")).hasMessageContaining("read-only");
  }

  @Test
  void failedCheckResetsRecoveryGate() {
    var i = cmd(fresh("deployment"), "analyze", "");
    String a = i.actions.getFirst().id;
    i = cmd(i, "approve", a);
    i = cmd(i, "execute", a);
    i = cmd(i, "sample", "healthy");
    i = cmd(i, "sample", "unhealthy");
    i = cmd(i, "sample", "healthy");
    assertThat(i.verifiedAt).isBlank();
    i = cmd(i, "sample", "healthy");
    assertThat(i.verifiedAt).isNotBlank();
  }

  @Test
  void providerScenarioDoesNotRecommendRollback() {
    var i = cmd(fresh("provider"), "analyze", "");
    assertThat(i.actions.getFirst().title).contains("circuit breaker").doesNotContain("Roll back");
    assertThat(i.hypotheses.getFirst().evidenceIds()).contains("E2");
  }

  @Test
  void missingEvidenceDoesNotInventRemediation() {
    var i = cmd(fresh("unknown"), "analyze", "");
    assertThat(i.actions).isEmpty();
    assertThat(i.hypotheses.getFirst().strength()).isEqualTo("Unknown");
    assertThat(service.postmortem(i.id)).contains("Not verified");
  }

  @Test
  void newEvidenceInvalidatesApprovalAndStaleRevision() {
    var i = cmd(fresh("deployment"), "analyze", "");
    String a = i.actions.getFirst().id;
    i = cmd(i, "approve", a);
    var old = i;
    var revised =
        service.evidence(
            i.id, "NOTE", "Engineer", "Evidence changed", i.revision, UUID.randomUUID().toString());
    assertThat(revised.actions).isEmpty();
    assertThatThrownBy(() -> cmd(old, "execute", a)).hasMessageContaining("changed");
  }

  @Test
  void retriesAreIdempotentAndRejectedWritesDoNotPersist() {
    var i = fresh("deployment");
    String key = UUID.randomUUID().toString();
    var first = service.command(i.id, "note", "Decision", i.revision, key, "Tester");
    var second = service.command(i.id, "note", "Decision", i.revision, key, "Tester");
    assertThat(second.revision).isEqualTo(first.revision);
    assertThat(second.timeline).hasSize(first.timeline.size());
    assertThatThrownBy(() -> cmd(second, "execute", "missing"))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(service.get(i.id).revision).isEqualTo(first.revision);
  }

  @Test
  void concurrentRequestsCannotApplyTwoChangesAtOneRevision() throws Exception {
    var i = fresh("unknown");
    try (var executor = Executors.newFixedThreadPool(2)) {
      Callable<Boolean> write =
          () -> {
            try {
              cmd(i, "note", "Concurrent decision");
              return true;
            } catch (ResponseStatusException e) {
              return false;
            }
          };
      var results = executor.invokeAll(List.of(write, write));
      int succeeded = 0;
      for (var r : results) if (r.get()) succeeded++;
      assertThat(succeeded).isEqualTo(1);
      assertThat(service.get(i.id).revision).isEqualTo(1);
    }
  }

  @Test
  void mcpHasNoApprovalOrExecutionTool() {
    var names =
        Arrays.stream(IncidentTools.class.getDeclaredMethods())
            .filter(m -> m.isAnnotationPresent(org.springframework.ai.tool.annotation.Tool.class))
            .map(java.lang.reflect.Method::getName)
            .toList();
    assertThat(names).hasSize(9).doesNotContain("approve", "execute", "resolve_incident");
  }

  @Test
  void httpOriginProtectionAndInvalidJson() throws Exception {
    var r =
        http.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/incidents"))
                .header("Origin", "https://attacker.example")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(r.statusCode()).isEqualTo(403);
    r =
        http.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/incidents"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(r.statusCode()).isEqualTo(400);
  }

  @Test
  void streamableMcpNegotiatesRequiredProtocolAndListsTools() throws Exception {
    String body =
        json.writeValueAsString(
            Map.of(
                "jsonrpc",
                "2.0",
                "id",
                1,
                "method",
                "initialize",
                "params",
                Map.of(
                    "protocolVersion",
                    "2025-11-25",
                    "capabilities",
                    Map.of(),
                    "clientInfo",
                    Map.of("name", "signalpilot-test", "version", "1.0"))));
    var req =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    var r = http.send(req, HttpResponse.BodyHandlers.ofString());
    assertThat(r.statusCode()).isEqualTo(200);
    assertThat(r.body()).contains("2025-11-25", "signalpilot");
    String session = r.headers().firstValue("Mcp-Session-Id").orElseThrow();
    var list =
        http.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("Mcp-Session-Id", session)
                .header("MCP-Protocol-Version", "2025-11-25")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(list.statusCode()).isEqualTo(200);
    assertThat(list.body()).contains("analyze_incident", "generate_postmortem", "ingest_evidence");
  }
}
