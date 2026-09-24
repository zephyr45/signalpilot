package dev.signalpilot;

import static dev.signalpilot.Incident.*;

import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IncidentService {
  private final IncidentStore store;

  public IncidentService(IncidentStore store) {
    this.store = store;
  }

  public List<Incident> list() {
    return store.list();
  }

  public Incident get(String id) {
    return store.get(id, false);
  }

  static String now() {
    return Instant.now().toString();
  }

  static void fail(String message) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  static String clean(String s, int max) {
    if (s == null || s.isBlank() || s.length() > max)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Text must contain 1–" + max + " characters");
    return s.strip();
  }

  static void event(Incident i, String actor, String type, String detail) {
    i.timeline.add(new Event(now(), actor, type, detail));
  }

  static void add(Incident i, String kind, String source, String summary) {
    i.evidence.add(new Evidence("E" + (i.evidence.size() + 1), kind, source, summary, now()));
  }

  @Transactional
  public Incident create(String scenario, String title, String service) {
    if (scenario == null
        || !List.of("deployment", "provider", "unknown", "custom").contains(scenario))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown scenario");
    Incident i = new Incident();
    i.id = UUID.randomUUID().toString();
    i.scenario = scenario;
    i.title = clean(title, 160);
    i.service = clean(service, 80);
    i.createdAt = now();
    i.updatedAt = i.createdAt;
    event(
        i, "Operator", "OPENED", "Incident opened. All remediation in this project is simulated.");
    if (!scenario.equals("custom")) {
      i.metrics.add(
          new Metric(Instant.now().minusSeconds(120).toString(), 0.2, 180, "Simulator baseline"));
      i.metrics.add(
          new Metric(Instant.now().minusSeconds(60).toString(), 4.2, 1400, "Simulator alert"));
      i.metrics.add(new Metric(now(), 12.8, 3200, "Simulator alert"));
      add(
          i,
          "ALERT",
          "Simulator / checkout",
          "Checkout errors 12.8%, p95 latency 3200 ms; customers cannot complete purchases.");
      if (scenario.equals("deployment")) {
        add(
            i,
            "DEPLOYMENT",
            "Simulator / deploy events",
            "checkout v2.8 deployed two minutes before errors increased; previous version v2.7 was"
                + " healthy.");
        add(
            i,
            "POOL_EXHAUSTION",
            "Simulator / application logs",
            "Connection acquisition timed out; active database connections 50/50 after pool-size"
                + " configuration changed in v2.8.");
        add(
            i,
            "PROVIDER_HEALTHY",
            "Simulator / payment health",
            "Payment provider success rate 99.98%; upstream p95 120 ms. No matching provider"
                + " degradation.");
      } else if (scenario.equals("provider")) {
        add(
            i,
            "PROVIDER_DEGRADED",
            "Simulator / upstream traces",
            "Payment provider p95 7800 ms and timeout rate 22%; degradation began before checkout"
                + " errors.");
        add(
            i,
            "NO_DEPLOYMENT",
            "Simulator / deploy events",
            "No checkout deployments in the preceding 24 hours; pool occupancy 20/50.");
      }
    }
    assess(i);
    store.insert(i);
    return i;
  }

  private void assess(Incident i) {
    if (i.metrics.isEmpty()) {
      i.severity = "UNASSESSED";
      i.severityReason = "No quantified impact yet. Gather error rate and affected customer scope.";
      return;
    }
    Metric m = i.metrics.getLast();
    i.severity = m.errorRate() >= 30 ? "SEV-1" : m.errorRate() >= 5 ? "SEV-2" : "SEV-3";
    i.severityReason =
        "Demo policy: ≥30% errors = SEV-1; ≥5% = SEV-2. Latest observed error rate "
            + m.errorRate()
            + "%. Severity remains the incident's historical assessment after recovery.";
  }

  @Transactional
  public Incident command(
      String id, String operation, String value, int revision, String requestId, String actor) {
    var i = store.get(id, true);
    requestId = clean(requestId, 100);
    actor = clean(actor, 80);
    if (i.requestIds.contains(requestId)) return i;
    if (i.revision != revision)
      fail("This incident changed. Refresh and review the latest evidence before acting.");
    if (i.status.equals("RESOLVED"))
      fail("Resolved incidents are read-only. Open a new incident for new symptoms.");
    switch (operation) {
      case "analyze" -> analyze(i);
      case "assign" -> {
        i.commander = clean(value, 80);
        event(i, actor, "ASSIGNED", "Incident commander: " + i.commander);
      }
      case "note" -> event(i, actor, "DECISION", clean(value, 2000));
      case "approve" -> {
        var a = action(i, value);
        if (!a.status.equals("PROPOSED")) fail("Action must be proposed before approval");
        a.status = "APPROVED";
        a.approvedBy = actor;
        event(
            i,
            actor,
            "APPROVED",
            a.title + " — approval applies to this proposal and current evidence only.");
      }
      case "execute" -> {
        var a = action(i, value);
        if (!a.status.equals("APPROVED"))
          fail("A human must approve the current proposal before simulation");
        a.status = "EXECUTED";
        a.executedAt = now();
        i.status = "MONITORING";
        i.verifiedAt = "";
        event(
            i,
            actor,
            "SIMULATED",
            a.title + ". Simulator changed state; no production infrastructure was contacted.");
      }
      case "sample" -> {
        if (!i.status.equals("MONITORING"))
          fail("Apply an approved simulation before collecting recovery samples");
        var executed =
            i.actions.stream().filter(a -> a.status.equals("EXECUTED")).findFirst().orElseThrow();
        if (!value.equals("healthy") && !value.equals("unhealthy"))
          fail("Sample must be healthy or unhealthy");
        boolean healthy = value.equals("healthy");
        i.metrics.add(
            new Metric(
                now(), healthy ? 0.3 : 9.1, healthy ? 210 : 2500, "Simulator recovery check"));
        i.verifiedAt = "";
        var samples =
            i.metrics.stream()
                .filter(
                    m ->
                        m.at().compareTo(executed.executedAt) >= 0
                            && m.source().equals("Simulator recovery check"))
                .toList();
        if (samples.size() >= 2
            && samples.subList(samples.size() - 2, samples.size()).stream()
                .allMatch(m -> m.errorRate() < 1 && m.p95Ms() < 500)) i.verifiedAt = now();
        event(
            i,
            "Simulator",
            "VERIFICATION",
            healthy
                ? "Healthy sample: errors 0.3%, p95 210 ms."
                : "Unhealthy sample: errors 9.1%, p95 2500 ms. Recovery gate reset.");
      }
      case "resolve" -> {
        if (i.verifiedAt.isBlank())
          fail("Resolution requires two consecutive healthy samples after the simulated action");
        i.resolution = clean(value, 2000);
        i.status = "RESOLVED";
        event(
            i,
            actor,
            "RESOLVED",
            i.resolution + " Recovery confirmed by two consecutive simulator samples.");
      }
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown operation");
    }
    i.requestIds.add(requestId);
    i.revision++;
    i.updatedAt = now();
    store.save(i);
    return i;
  }

  private Action action(Incident i, String id) {
    return i.actions.stream()
        .filter(a -> a.id.equals(id))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Action not found"));
  }

  private void analyze(Incident i) {
    if (!i.status.equals("INVESTIGATING"))
      fail("Investigation is complete; open a new incident if symptoms change");
    i.hypotheses.clear();
    i.actions.clear();
    i.brief = "";
    i.briefProvider = "Not generated";
    var pool = ids(i, "POOL_EXHAUSTION");
    var deploy = ids(i, "DEPLOYMENT");
    var provider = ids(i, "PROVIDER_DEGRADED");
    if (!provider.isEmpty()) {
      i.hypotheses.add(
          new Hypothesis(
              "Payment dependency is degrading checkout",
              "Strong",
              "Upstream timeouts precede the customer-facing failures. A checkout rollback would"
                  + " not address this evidence.",
              provider));
      i.actions.add(
          new Action(
              "A-" + UUID.randomUUID(),
              "Enable payment circuit breaker (simulation)",
              "Stop retry amplification while the dependency recovers.",
              "Error rate below 1%; p95 below 500 ms across two checks.",
              provider));
    } else if (!pool.isEmpty() && !deploy.isEmpty()) {
      var refs = new ArrayList<>(deploy);
      refs.addAll(pool);
      i.hypotheses.add(
          new Hypothesis(
              "Deployment-associated connection pool exhaustion",
              "Strong",
              "The timing and pool exhaustion support a deployment regression. Correlation is not"
                  + " definitive proof; compare configuration before rollback.",
              refs));
      i.hypotheses.add(
          new Hypothesis(
              "Independent database saturation",
              "Possible",
              "Pool saturation can also result from database load. Inspect slow queries before"
                  + " asserting a final root cause.",
              pool));
      i.actions.add(
          new Action(
              "A-" + UUID.randomUUID(),
              "Roll back checkout to v2.7 (simulation)",
              "Restore the last known healthy configuration, then verify customer-facing signals.",
              "Error rate below 1%; p95 below 500 ms across two checks.",
              refs));
    } else {
      i.hypotheses.add(
          new Hypothesis(
              "Insufficient evidence to recommend a change",
              "Unknown",
              "Collect deployment history, connection-pool telemetry, and upstream traces. An alert"
                  + " alone cannot establish root cause.",
              ids(i, "ALERT")));
    }
    event(
        i,
        "Evidence engine",
        "ANALYZED",
        "Evidence evaluated. Strength labels are rule-based, not statistical probabilities."
            + " Previous approvals invalidated.");
  }

  private List<String> ids(Incident i, String kind) {
    return i.evidence.stream().filter(e -> e.kind().equals(kind)).map(Evidence::id).toList();
  }

  @Transactional
  public Incident evidence(
      String id, String kind, String source, String summary, int revision, String requestId) {
    var i = store.get(id, true);
    requestId = clean(requestId, 100);
    if (i.requestIds.contains(requestId)) return i;
    if (i.revision != revision) fail("Refresh the incident before adding evidence");
    if (!i.status.equals("INVESTIGATING")) fail("New evidence requires an investigating incident");
    if (i.evidence.size() >= 100) fail("Evidence limit reached for this demo incident");
    if (kind == null
        || !List.of(
                "ALERT",
                "DEPLOYMENT",
                "POOL_EXHAUSTION",
                "PROVIDER_DEGRADED",
                "PROVIDER_HEALTHY",
                "NO_DEPLOYMENT",
                "NOTE")
            .contains(kind))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported evidence kind");
    add(i, kind, clean(source, 120), clean(summary, 3000));
    i.actions.clear();
    i.hypotheses.clear();
    i.brief = "";
    i.briefProvider = "Not generated";
    event(
        i,
        "Operator / MCP",
        "EVIDENCE",
        "Added "
            + i.evidence.getLast().id()
            + ". Reanalysis required; earlier proposals and approvals invalidated.");
    i.requestIds.add(requestId);
    i.revision++;
    i.updatedAt = now();
    store.save(i);
    return i;
  }

  @Transactional
  public Incident saveBrief(String id, int revision, String text, String provider) {
    var i = store.get(id, true);
    if (i.revision != revision)
      fail("Evidence changed while the brief was generated; retry with current evidence");
    if (i.status.equals("RESOLVED")) fail("Resolved incidents are read-only");
    i.brief = text;
    i.briefProvider = provider;
    event(i, "Brief writer", "BRIEF", "Brief generated using " + provider);
    i.revision++;
    i.updatedAt = now();
    store.save(i);
    return i;
  }

  public String update(String id, String audience) {
    var i = get(id);
    boolean done = i.status.equals("RESOLVED");
    return switch (audience) {
      case "customer" ->
          done
              ? "Service has recovered and checks are passing. We are monitoring stability and"
                    + " reviewing preventative improvements."
              : "We are investigating elevated errors affecting "
                  + i.service
                  + ". Our team is working to restore normal service. We will share another update"
                  + " as verified information becomes available.";
      case "executive" ->
          i.title
              + " | "
              + i.severity
              + " | "
              + i.status
              + ". Commander: "
              + i.commander
              + ". "
              + (done
                  ? "Recovery verified in the simulator."
                  : "Customer impact is under investigation; no recovery ETA confirmed.");
      case "engineering" ->
          i.title
              + " ["
              + i.status
              + "]\n"
              + (i.hypotheses.isEmpty()
                  ? "Analysis pending."
                  : i.hypotheses.getFirst().title() + " — " + i.hypotheses.getFirst().explanation())
              + "\nEvidence: "
              + i.evidence.stream().map(Evidence::id).toList()
              + ". All operational actions are simulated.";
      default ->
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "Audience must be customer, executive, or engineering");
    };
  }

  public String postmortem(String id) {
    var i = get(id);
    StringBuilder b =
        new StringBuilder(
            "# "
                + i.title
                + "\n\nIncident: "
                + i.id
                + "\nStatus: "
                + i.status
                + "\nCommander: "
                + i.commander
                + "\n\n"
                + "> Demonstration environment. Telemetry and remediation are simulated; findings"
                + " are hypotheses, not independently confirmed production root causes.\n\n"
                + "## Impact\n"
                + i.severityReason
                + "\n\n## Leading hypothesis\n");
    if (i.hypotheses.isEmpty()) b.append("Not yet established.\n");
    for (var h : i.hypotheses)
      b.append("- ")
          .append(h.title())
          .append(" (")
          .append(h.strength())
          .append(") — ")
          .append(h.explanation())
          .append(" Evidence: ")
          .append(h.evidenceIds())
          .append('\n');
    b.append("\n## Evidence\n");
    for (var e : i.evidence)
      b.append("- ")
          .append(e.id())
          .append(" · ")
          .append(e.source())
          .append(": ")
          .append(e.summary())
          .append('\n');
    b.append("\n## Timeline (UTC)\n");
    for (var e : i.timeline)
      b.append("- ")
          .append(e.at())
          .append(" · ")
          .append(e.actor())
          .append(" · ")
          .append(e.type())
          .append(": ")
          .append(e.detail())
          .append('\n');
    b.append("\n## Recovery\n")
        .append(
            i.verifiedAt.isBlank()
                ? "Not verified. Do not claim resolution."
                : "Two consecutive simulator checks below 1% errors and 500 ms p95 at "
                    + i.verifiedAt)
        .append("\n")
        .append(i.resolution);
    b.append(
            "\n\n"
                + "## Follow-up actions\n"
                + "- [ ] Service owner: add a regression test reproducing the observed failure.\n"
                + "- [ ] On-call lead: review the runbook and alert thresholds before the next"
                + " release.\n"
                + "- [ ] Incident commander: validate the leading hypothesis against independent"
                + " evidence.\n\n"
                + "## Method\n"
                + "Evidence strength is rule-based. ")
        .append(i.briefProvider)
        .append(" supplied the optional briefing. No production actions executed.\n");
    return b.toString();
  }
}
