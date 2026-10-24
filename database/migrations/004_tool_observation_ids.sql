-- Additive, rerunnable. Apply after 003 to the selected, backed-up database.
-- Existing audit rows intentionally retain NULL identifiers; do not fabricate historical traces.
SET @campus_trace_ddl = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE tool_audits ADD COLUMN trace_id VARCHAR(36) NULL', 'SELECT 1')
  FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tool_audits' AND column_name='trace_id');
PREPARE campus_trace_stmt FROM @campus_trace_ddl;
EXECUTE campus_trace_stmt;
DEALLOCATE PREPARE campus_trace_stmt;
SET @campus_trace_ddl = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE tool_audits ADD COLUMN request_id VARCHAR(36) NULL', 'SELECT 1')
  FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tool_audits' AND column_name='request_id');
PREPARE campus_trace_stmt FROM @campus_trace_ddl;
EXECUTE campus_trace_stmt;
DEALLOCATE PREPARE campus_trace_stmt;
