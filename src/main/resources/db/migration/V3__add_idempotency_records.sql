CREATE TABLE idempotency_records (
  incident_id VARCHAR(40) NOT NULL,
  request_id VARCHAR(100) NOT NULL,
  operation VARCHAR(80) NOT NULL,
  payload_hash VARCHAR(64) NOT NULL,
  response_revision INTEGER NOT NULL,
  response_payload TEXT NOT NULL,
  created_at VARCHAR(40) NOT NULL,
  PRIMARY KEY (incident_id, request_id),
  CONSTRAINT fk_idempotency_incident FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE
);

CREATE INDEX idx_idempotency_incident_created ON idempotency_records(incident_id, created_at);
