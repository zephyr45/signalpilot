package dev.signalpilot;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final IncidentService service;
  private final BriefService brief;

  public ApiController(IncidentService service, BriefService brief) {
    this.service = service;
    this.brief = brief;
  }

  public record NewIncident(String scenario, String title, String service) {}

  public record Command(String operation, String value, int revision, String requestId) {}

  public record EvidenceInput(
      String kind, String source, String summary, int revision, String requestId) {}

  public record Chat(String message, int revision, String requestId) {}

  @GetMapping("/config")
  Map<String, Object> config() {
    return Map.of(
        "name",
        "SignalPilot",
        "version",
        "1.0.0",
        "aiProvider",
        brief.provider(),
        "mode",
        "simulation",
        "mcpEndpoint",
        "/mcp");
  }

  @GetMapping("/incidents")
  List<Incident> list() {
    return service.list();
  }

  @PostMapping("/incidents")
  @ResponseStatus(HttpStatus.CREATED)
  Incident create(@RequestBody NewIncident n) {
    return service.create(n.scenario(), n.title(), n.service());
  }

  @GetMapping("/incidents/{id}")
  Incident get(@PathVariable String id) {
    return service.get(id);
  }

  @PostMapping("/incidents/{id}/commands")
  Incident command(@PathVariable String id, @RequestBody Command c) {
    if (c.operation() == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operation required");
    return service.command(
        id, c.operation(), c.value(), c.revision(), c.requestId(), "Dashboard operator");
  }

  @PostMapping("/incidents/{id}/evidence")
  Incident evidence(@PathVariable String id, @RequestBody EvidenceInput e) {
    return service.evidence(id, e.kind(), e.source(), e.summary(), e.revision(), e.requestId());
  }

  @GetMapping("/incidents/{id}/update")
  Map<String, String> update(
      @PathVariable String id, @RequestParam(defaultValue = "customer") String audience) {
    return Map.of("text", service.update(id, audience));
  }

  @GetMapping(value = "/incidents/{id}/postmortem", produces = "text/markdown")
  ResponseEntity<String> postmortem(@PathVariable String id) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=signalpilot-" + id + ".md")
        .body(service.postmortem(id));
  }

  @PostMapping("/incidents/{id}/brief")
  Incident brief(@PathVariable String id) {
    var i = service.get(id);
    var b = brief.generate(i);
    return service.saveBrief(id, i.revision, b.text(), b.provider());
  }

  @PostMapping("/incidents/{id}/chat")
  Map<String, Object> chat(@PathVariable String id, @RequestBody Chat c) {
    String message = IncidentService.clean(c.message(), 1000).toLowerCase(Locale.ROOT);
    if (message.matches(".*\\b(approve|execute|rollback|roll back|resolve)\\b.*"))
      return Map.of(
          "reply",
          "Review and approve the proposed action in the action panel. Chat cannot authorize"
              + " operational changes.",
          "incident",
          service.get(id));
    if (message.contains("analy") || message.contains("diagnos") || message.contains("cause")) {
      var i = service.command(id, "analyze", "", c.revision(), c.requestId(), "Command console");
      return Map.of(
          "reply",
          i.hypotheses.getFirst().title() + ". " + i.hypotheses.getFirst().explanation(),
          "incident",
          i);
    }
    if (message.contains("update") || message.contains("customer"))
      return Map.of("reply", service.update(id, "customer"), "incident", service.get(id));
    return Map.of(
        "reply",
        "Available commands: analyze incident, draft customer update. Use the action panel for"
            + " approvals, recovery verification and resolution. This local command router is not"
            + " an LLM; connect an MCP agent for natural-language reasoning.",
        "incident",
        service.get(id));
  }
}
