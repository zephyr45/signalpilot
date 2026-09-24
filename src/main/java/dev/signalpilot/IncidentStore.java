package dev.signalpilot;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class IncidentStore {
  private final JdbcTemplate db;
  private final JsonMapper json = JsonMapper.builder().build();

  public IncidentStore(JdbcTemplate db) {
    this.db = db;
  }

  public List<Incident> list() {
    return db.query(
        "SELECT payload FROM incidents ORDER BY updated_at DESC",
        (r, n) -> json.readValue(r.getString(1), Incident.class));
  }

  public Incident get(String id, boolean lock) {
    var rows =
        db.query(
            "SELECT payload FROM incidents WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            (r, n) -> json.readValue(r.getString(1), Incident.class),
            id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
    return rows.getFirst();
  }

  public void insert(Incident i) {
    db.update(
        "INSERT INTO incidents(id,payload,updated_at) VALUES (?,?,?)",
        i.id,
        json.writeValueAsString(i),
        i.updatedAt);
  }

  public void save(Incident i) {
    db.update(
        "UPDATE incidents SET payload=?,updated_at=? WHERE id=?",
        json.writeValueAsString(i),
        i.updatedAt,
        i.id);
  }
}
