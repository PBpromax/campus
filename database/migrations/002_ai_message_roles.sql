-- Add assistant/user distinction to existing messages. Run after 001, in the intended database.
-- No user/task data is deleted; existing messages remain USER.
SET @campus_role_ddl = (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE need_messages ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT ''USER''',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='need_messages' AND column_name='role'
);
PREPARE campus_role_stmt FROM @campus_role_ddl;
EXECUTE campus_role_stmt;
DEALLOCATE PREPARE campus_role_stmt;
