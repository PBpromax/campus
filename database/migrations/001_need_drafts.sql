-- M2 step 2: additive migration, MySQL. Select the intended development database first.
-- No DROP/TRUNCATE or changes to users/demands. Back up before applying to another environment.
CREATE TABLE IF NOT EXISTS need_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner_id BIGINT NOT NULL,
  revision BIGINT NOT NULL DEFAULT 0,
  active_category ENUM('BORROW','OTHER','RUNNING','STUDY') NULL,
  route_pending BIT NOT NULL DEFAULT 1,
  intake_text LONGTEXT NULL,
  updated_at DATETIME(6) NOT NULL,
  row_version BIGINT NULL,
  INDEX idx_need_owner(owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS need_drafts (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT NOT NULL,
  category ENUM('BORROW','OTHER','RUNNING','STUDY') NOT NULL,
  revision BIGINT NOT NULL DEFAULT 1,
  confirmed_revision BIGINT NULL,
  fields_json LONGTEXT NOT NULL,
  contexts_json LONGTEXT NOT NULL,
  CONSTRAINT uq_need_session_category UNIQUE(session_id, category),
  CONSTRAINT fk_need_draft_session FOREIGN KEY(session_id) REFERENCES need_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS need_messages (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT NOT NULL,
  category ENUM('BORROW','OTHER','RUNNING','STUDY') NULL,
  text VARCHAR(4000) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  INDEX idx_need_message_session(session_id),
  CONSTRAINT fk_need_message_session FOREIGN KEY(session_id) REFERENCES need_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
