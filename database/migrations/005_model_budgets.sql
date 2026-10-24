-- Additive migration after 004. Back up and select the intended database first.
-- Does not backfill historical model costs or delete existing user/task data.
CREATE TABLE IF NOT EXISTS model_budget_days (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  attempts BIGINT NOT NULL DEFAULT 0,
  reserved_tokens BIGINT NOT NULL DEFAULT 0,
  reserved_usd DECIMAL(20,8) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS model_invocations (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  owner_id BIGINT NOT NULL,
  budget_day DATE NOT NULL,
  trace_id VARCHAR(36), request_id VARCHAR(36),
  model VARCHAR(64) NOT NULL, price_version VARCHAR(64) NOT NULL,
  state VARCHAR(20) NOT NULL, failure_kind VARCHAR(40),
  reserved_tokens BIGINT NOT NULL, reserved_usd DECIMAL(20,8) NOT NULL,
  prompt_tokens BIGINT, completion_tokens BIGINT, estimated_usd DECIMAL(20,8), estimate_basis VARCHAR(40),
  created_at DATETIME(6) NOT NULL, finished_at DATETIME(6),
  INDEX idx_model_owner_day(owner_id,budget_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
