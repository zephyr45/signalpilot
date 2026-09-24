package dev.signalpilot;

import java.util.*;

/** Stored aggregate. All mutations are serialized by a database row lock. */
public class Incident {
  public String id, title, service, scenario, createdAt, updatedAt;
  public String status = "INVESTIGATING",
      severity = "SEV-2",
      severityReason = "Customer impact under assessment";
  public int revision;
  public String commander = "Unassigned", resolution = "";
  public List<Evidence> evidence = new ArrayList<>();
  public List<Hypothesis> hypotheses = new ArrayList<>();
  public List<Action> actions = new ArrayList<>();
  public List<Event> timeline = new ArrayList<>();
  public List<Metric> metrics = new ArrayList<>();
  public List<String> requestIds = new ArrayList<>();
  public String brief = "", briefProvider = "Not generated", verifiedAt = "";

  public record Evidence(String id, String kind, String source, String summary, String at) {}

  public record Hypothesis(
      String title, String strength, String explanation, List<String> evidenceIds) {}

  public record Event(String at, String actor, String type, String detail) {}

  public record Metric(String at, double errorRate, int p95Ms, String source) {}

  public static class Action {
    public String id,
        title,
        rationale,
        expectedOutcome,
        status = "PROPOSED",
        approvedBy = "",
        executedAt = "";
    public List<String> evidenceIds = new ArrayList<>();

    public Action() {}

    public Action(
        String id,
        String title,
        String rationale,
        String expectedOutcome,
        List<String> evidenceIds) {
      this.id = id;
      this.title = title;
      this.rationale = rationale;
      this.expectedOutcome = expectedOutcome;
      this.evidenceIds = evidenceIds;
    }
  }
}
