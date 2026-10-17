CREATE TABLE incident_evidence (
  incident_id VARCHAR(40) NOT NULL,
  sequence_no INTEGER NOT NULL,
  evidence_id VARCHAR(40) NOT NULL,
  kind VARCHAR(40) NOT NULL,
  source VARCHAR(120) NOT NULL,
  summary TEXT NOT NULL,
  observed_at VARCHAR(40) NOT NULL,
  PRIMARY KEY (incident_id, sequence_no),
  CONSTRAINT fk_evidence_incident FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE
);

CREATE INDEX idx_evidence_incident_kind ON incident_evidence(incident_id, kind);

CREATE TABLE incident_events (
  incident_id VARCHAR(40) NOT NULL,
  sequence_no INTEGER NOT NULL,
  event_at VARCHAR(40) NOT NULL,
  actor VARCHAR(80) NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  detail TEXT NOT NULL,
  PRIMARY KEY (incident_id, sequence_no),
  CONSTRAINT fk_events_incident FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE
);

CREATE INDEX idx_events_incident_time ON incident_events(incident_id, event_at);

CREATE TABLE incident_metrics (
  incident_id VARCHAR(40) NOT NULL,
  sequence_no INTEGER NOT NULL,
  metric_at VARCHAR(40) NOT NULL,
  error_rate DOUBLE PRECISION NOT NULL,
  p95_ms INTEGER NOT NULL,
  source VARCHAR(120) NOT NULL,
  PRIMARY KEY (incident_id, sequence_no),
  CONSTRAINT fk_metrics_incident FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE
);

CREATE INDEX idx_metrics_incident_time ON incident_metrics(incident_id, metric_at);
