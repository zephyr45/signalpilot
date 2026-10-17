package dev.signalpilot;

import static dev.signalpilot.Incident.*;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class IncidentStore {
  private static final int MAX_IDEMPOTENCY_RECORDS = 500;

  private final JdbcTemplate db;
  private final JsonMapper json = JsonMapper.builder().build();

  public IncidentStore(JdbcTemplate db) {
    this.db = db;
  }

  public List<Incident> list(int limit, int offset) {
    List<Incident> incidents =
        db.query(
        "SELECT payload FROM incidents ORDER BY updated_at DESC LIMIT ? OFFSET ?",
        (row, number) -> decode(row.getString(1)),
        limit,
        offset);
    hydrateAll(incidents);
    return incidents;
  }

  public Incident get(String id, boolean lock) {
    var rows =
        db.query(
            "SELECT payload FROM incidents WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            (row, number) -> decode(row.getString(1)),
            id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
    return hydrate(rows.getFirst());
  }

  public void insert(Incident incident) {
    db.update(
        "INSERT INTO incidents(id,payload,updated_at) VALUES (?,?,?)",
        incident.id,
        encodeCore(incident),
        incident.updatedAt);
    appendHistory(incident);
  }

  public void save(Incident incident) {
    db.update(
        "UPDATE incidents SET payload=?,updated_at=? WHERE id=?",
        encodeCore(incident),
        incident.updatedAt,
        incident.id);
    appendHistory(incident);
  }

  public Optional<RequestReplay> findRequest(String incidentId, String requestId) {
    return db.query(
            "SELECT operation,payload_hash,response_payload FROM idempotency_records"
                + " WHERE incident_id=? AND request_id=?",
            (row, number) ->
                new RequestReplay(row.getString(1), row.getString(2), row.getString(3)),
            incidentId,
            requestId)
        .stream()
        .findFirst();
  }

  public Incident replay(RequestReplay replay) {
    return decode(replay.responsePayload());
  }

  public void recordRequest(
      Incident incident, String requestId, String operation, String payloadHash) {
    db.update(
        "INSERT INTO idempotency_records(incident_id,request_id,operation,payload_hash,"
            + "response_revision,response_payload,created_at) VALUES (?,?,?,?,?,?,?)",
        incident.id,
        requestId,
        operation,
        payloadHash,
        incident.revision,
        encodeFull(incident),
        IncidentService.now());
    trimRequests(incident.id);
  }

  private Incident hydrate(Incident incident) {
    List<Evidence> evidence =
        db.query(
            "SELECT evidence_id,kind,source,summary,observed_at FROM incident_evidence"
                + " WHERE incident_id=? ORDER BY sequence_no",
            (row, number) ->
                new Evidence(
                    row.getString(1),
                    row.getString(2),
                    row.getString(3),
                    row.getString(4),
                    row.getString(5)),
            incident.id);
    List<Event> timeline =
        db.query(
            "SELECT event_at,actor,event_type,detail FROM incident_events"
                + " WHERE incident_id=? ORDER BY sequence_no",
            (row, number) ->
                new Event(
                    row.getString(1), row.getString(2), row.getString(3), row.getString(4)),
            incident.id);
    List<Metric> metrics =
        db.query(
            "SELECT metric_at,error_rate,p95_ms,source FROM incident_metrics"
                + " WHERE incident_id=? ORDER BY sequence_no",
            (row, number) ->
                new Metric(
                    row.getString(1), row.getDouble(2), row.getInt(3), row.getString(4)),
            incident.id);

    // An existing pre-Flyway database can still have history embedded in payload. Preserve it
    // until the next successful write migrates those rows into the normalized tables.
    if (!evidence.isEmpty()) incident.evidence = new ArrayList<>(evidence);
    if (!timeline.isEmpty()) incident.timeline = new ArrayList<>(timeline);
    if (!metrics.isEmpty()) incident.metrics = new ArrayList<>(metrics);
    return incident;
  }

  private void hydrateAll(List<Incident> incidents) {
    if (incidents.isEmpty()) return;
    List<String> ids = incidents.stream().map(incident -> incident.id).toList();
    String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
    Object[] arguments = ids.toArray();

    Map<String, List<Evidence>> evidenceByIncident = new HashMap<>();
    db.query(
        "SELECT incident_id,evidence_id,kind,source,summary,observed_at FROM incident_evidence"
            + " WHERE incident_id IN ("
            + placeholders
            + ") ORDER BY incident_id,sequence_no",
        (row, number) -> {
          evidenceByIncident
              .computeIfAbsent(row.getString(1), ignored -> new ArrayList<>())
              .add(
                  new Evidence(
                      row.getString(2),
                      row.getString(3),
                      row.getString(4),
                      row.getString(5),
                      row.getString(6)));
          return null;
        },
        arguments);

    Map<String, List<Event>> eventsByIncident = new HashMap<>();
    db.query(
        "SELECT incident_id,event_at,actor,event_type,detail FROM incident_events"
            + " WHERE incident_id IN ("
            + placeholders
            + ") ORDER BY incident_id,sequence_no",
        (row, number) -> {
          eventsByIncident
              .computeIfAbsent(row.getString(1), ignored -> new ArrayList<>())
              .add(
                  new Event(
                      row.getString(2), row.getString(3), row.getString(4), row.getString(5)));
          return null;
        },
        arguments);

    Map<String, List<Metric>> metricsByIncident = new HashMap<>();
    db.query(
        "SELECT incident_id,metric_at,error_rate,p95_ms,source FROM incident_metrics"
            + " WHERE incident_id IN ("
            + placeholders
            + ") ORDER BY incident_id,sequence_no",
        (row, number) -> {
          metricsByIncident
              .computeIfAbsent(row.getString(1), ignored -> new ArrayList<>())
              .add(
                  new Metric(
                      row.getString(2), row.getDouble(3), row.getInt(4), row.getString(5)));
          return null;
        },
        arguments);

    for (Incident incident : incidents) {
      if (evidenceByIncident.containsKey(incident.id))
        incident.evidence = new ArrayList<>(evidenceByIncident.get(incident.id));
      if (eventsByIncident.containsKey(incident.id))
        incident.timeline = new ArrayList<>(eventsByIncident.get(incident.id));
      if (metricsByIncident.containsKey(incident.id))
        incident.metrics = new ArrayList<>(metricsByIncident.get(incident.id));
    }
  }

  private void appendHistory(Incident incident) {
    appendEvidence(incident);
    appendEvents(incident);
    appendMetrics(incident);
  }

  private void appendEvidence(Incident incident) {
    int stored = count("incident_evidence", incident.id);
    requireAppendOnly("evidence", stored, incident.evidence.size());
    for (int index = stored; index < incident.evidence.size(); index++) {
      Evidence evidence = incident.evidence.get(index);
      db.update(
          "INSERT INTO incident_evidence(incident_id,sequence_no,evidence_id,kind,source,summary,"
              + "observed_at) VALUES (?,?,?,?,?,?,?)",
          incident.id,
          index,
          evidence.id(),
          evidence.kind(),
          evidence.source(),
          evidence.summary(),
          evidence.at());
    }
  }

  private void appendEvents(Incident incident) {
    int stored = count("incident_events", incident.id);
    requireAppendOnly("timeline", stored, incident.timeline.size());
    for (int index = stored; index < incident.timeline.size(); index++) {
      Event event = incident.timeline.get(index);
      db.update(
          "INSERT INTO incident_events(incident_id,sequence_no,event_at,actor,event_type,detail)"
              + " VALUES (?,?,?,?,?,?)",
          incident.id,
          index,
          event.at(),
          event.actor(),
          event.type(),
          event.detail());
    }
  }

  private void appendMetrics(Incident incident) {
    int stored = count("incident_metrics", incident.id);
    requireAppendOnly("metrics", stored, incident.metrics.size());
    for (int index = stored; index < incident.metrics.size(); index++) {
      Metric metric = incident.metrics.get(index);
      db.update(
          "INSERT INTO incident_metrics(incident_id,sequence_no,metric_at,error_rate,p95_ms,source)"
              + " VALUES (?,?,?,?,?,?)",
          incident.id,
          index,
          metric.at(),
          metric.errorRate(),
          metric.p95Ms(),
          metric.source());
    }
  }

  private int count(String table, String incidentId) {
    Integer count =
        db.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE incident_id=?", Integer.class, incidentId);
    return count == null ? 0 : count;
  }

  private static void requireAppendOnly(String collection, int stored, int current) {
    if (stored > current)
      throw new IllegalStateException(collection + " history cannot be removed or reordered");
  }

  private void trimRequests(String incidentId) {
    db.update(
        "DELETE FROM idempotency_records WHERE incident_id=? AND request_id IN ("
            + "SELECT request_id FROM idempotency_records WHERE incident_id=?"
            + " ORDER BY created_at DESC,request_id DESC OFFSET ? ROWS)",
        incidentId,
        incidentId,
        MAX_IDEMPOTENCY_RECORDS);
  }

  private String encodeCore(Incident incident) {
    @SuppressWarnings("unchecked")
    Map<String, Object> core = json.readValue(json.writeValueAsString(incident), Map.class);
    core.remove("evidence");
    core.remove("timeline");
    core.remove("metrics");
    core.remove("requestIds");
    return json.writeValueAsString(core);
  }

  private String encodeFull(Incident incident) {
    return json.writeValueAsString(incident);
  }

  private Incident decode(String payload) {
    return json.readValue(payload, Incident.class);
  }

  public record RequestReplay(String operation, String payloadHash, String responsePayload) {}
}
