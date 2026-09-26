CREATE TABLE IF NOT EXISTS snapshots (
  user_id TEXT PRIMARY KEY,
  payload_json TEXT NOT NULL,
  received_at_epoch_seconds INTEGER NOT NULL,
  device_id TEXT
) STRICT;

CREATE TABLE IF NOT EXISTS commands (
  command_id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  idempotency_key TEXT NOT NULL,
  expected_state_version INTEGER NOT NULL,
  user_confirmed INTEGER NOT NULL,
  queued_at_epoch_seconds INTEGER NOT NULL,
  status TEXT NOT NULL,
  device_id TEXT,
  command_json TEXT NOT NULL,
  receipt_json TEXT,
  completed_at_epoch_seconds INTEGER,
  UNIQUE(user_id, idempotency_key)
) STRICT;

CREATE INDEX IF NOT EXISTS commands_user_status
  ON commands(user_id, status, queued_at_epoch_seconds);
