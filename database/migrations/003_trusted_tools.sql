-- Additive migration. Select and back up the intended database first. No task/user rows are deleted.
SET @campus_tool_ddl = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE users ADD COLUMN row_version BIGINT DEFAULT 0', 'SELECT 1')
  FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users' AND column_name='row_version');
PREPARE campus_tool_stmt FROM @campus_tool_ddl;
EXECUTE campus_tool_stmt;
DEALLOCATE PREPARE campus_tool_stmt;
SET @campus_tool_ddl = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE demands ADD COLUMN row_version BIGINT DEFAULT 0', 'SELECT 1')
  FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='demands' AND column_name='row_version');
PREPARE campus_tool_stmt FROM @campus_tool_ddl;
EXECUTE campus_tool_stmt;
DEALLOCATE PREPARE campus_tool_stmt;

CREATE TABLE IF NOT EXISTS tool_intents (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  actor_id BIGINT NOT NULL, request_key VARCHAR(64) NOT NULL, request_hash VARCHAR(64) NOT NULL,
  action VARCHAR(16) NOT NULL, state VARCHAR(16) NOT NULL,
  source_session_id BIGINT, source_revision BIGINT, draft_revision BIGINT,
  demand_id BIGINT, demand_version BIGINT, publication_key VARCHAR(100),
  payload_json LONGTEXT NOT NULL, preview_json LONGTEXT NOT NULL, result_json LONGTEXT,
  error_code INT, error_message VARCHAR(500), expires_at DATETIME(6) NOT NULL, created_at DATETIME(6) NOT NULL,
  CONSTRAINT uq_tool_actor_request UNIQUE(actor_id, request_key),
  CONSTRAINT uq_tool_publication UNIQUE(publication_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS tool_audits (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, actor_id BIGINT NOT NULL, intent_id VARCHAR(36),
  action VARCHAR(16) NOT NULL, outcome VARCHAR(20) NOT NULL, message VARCHAR(500) NOT NULL,
  created_at DATETIME(6) NOT NULL, INDEX idx_tool_audit_actor(actor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
