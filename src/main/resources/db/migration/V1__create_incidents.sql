CREATE TABLE IF NOT EXISTS incidents (
  id VARCHAR(40) PRIMARY KEY,
  payload TEXT NOT NULL,
  updated_at VARCHAR(40) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_incidents_updated_at ON incidents(updated_at);
