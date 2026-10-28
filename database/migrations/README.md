# 数据库增量脚本

`001_need_drafts.sql`为M2四业务会话、草稿、消息的新增表。已在本地隔离的`campus_m2_dev`应用并通过真实MySQL9.6读写及后端重启恢复验证。

先确认连接和目标数据库并备份，再用MySQL客户端执行该脚本。它不删除旧表，也不更改`users`/`demands`。`CREATE TABLE IF NOT EXISTS`仅避免重复创建，不会自动修复结构不一致的同名旧表；有差异时先审查结构，不要清库重来。

当前开发配置继续由Hibernate执行自动结构更新；独立发布环境已使用Flyway冻结版本与Hibernate validate，并通过MySQL8.0.46新库/恢复验证。现有开发库不自动baseline接管，详见deploy/README.md。不要使用项目早期建库/清库脚本代替增量升级。


`002_ai_message_roles.sql`在001之后执行，为消息补充USER/ASSISTANT角色。先检查列，重复执行不删除数据；旧消息保持USER。本机campus_m2_dev已应用。


`003_trusted_tools.sql`在前两项之后执行：为users/demands补充乐观行版本，增加tool_intents/tool_audits。当前本机campus_m2_dev已重复执行两次验证；不删除用户、任务或旧对话。唯一索引保护同一用户请求键和成功发布的草稿版本；同名旧表结构不一致仍需人工审查。内部事务失败回滚，外部支付补偿未实现。

`004_tool_observation_ids.sql`在003之后给工具审计增加可空trace_id/request_id，不补造历史编号。本机campus_m2_dev已备份并重复应用两次，前后用户/任务/审计行数一致；见docs/M3/evidence/01-mysql-migration.json。

`005_model_budgets.sql`新增日预留与调用账本，不回填历史模型成本。隔离库已在备份后重复应用两次，既有用户、任务、会话与审计行数不变；详见docs/M3/evidence/03-mysql-migration.json。
