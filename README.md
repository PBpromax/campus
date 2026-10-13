# 校园互助平台：老师项目接管与 AI 原生升级

本仓库从老师提供的 `sec-ii-2026` 项目接管，来源为学校 GitLab 的 even-begging-has-to-wait-in-line/sec-ii-2026，基线提交 `68d8484f3a8a9ebeab69dc0d7e164bd44348f1fc`。原团队源码是继承成果，不将其历史作业、访谈或测试结论作为我们的交付。

GitHub 保留先前项目历史；当前代码已替换为老师项目快照，旧的 feature/m1-personalized-needs 分支仅对应旧项目。老师原始历史及 P0—P4 文档保存在本地备份，未合并到 GitHub。

## 当前业务和技术

分类：跑腿代办 RUNNING、学习辅导 STUDY、物品借用 BORROW、其他 OTHER。后端 Java 21 / Spring Boot 3.2 / JPA / MySQL，前端 Vue 3 / Vite / Element Plus。

存在注册登录、需求发布与接单、交付和完成确认、取消、通知、任务双方聊天、评价信用及申诉实现。支付状态字段不代表已接入真实支付。

M1 聚焦物品借用与学习辅导的个性化需求、本体和对话卡片原型；M2 再落实鸿蒙客户端与真实 AI 工具执行。

- [运行与配置](./DEVELOPMENT.md)
- [接管记录](./docs/接管记录.md)
- [课程课件](./docs/reference/00-课程介绍.pdf)

后续 M1 交付在 docs/M1/。原团队旧文档仅作本地参考，不是本学期任务要求。
