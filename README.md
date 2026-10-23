# 校园互助平台：老师项目接管与 AI 原生升级

本仓库从老师提供的 `sec-ii-2026` 项目接管，来源为学校 GitLab 的 even-begging-has-to-wait-in-line/sec-ii-2026，基线提交 `68d8484f3a8a9ebeab69dc0d7e164bd44348f1fc`。原团队源码是继承成果，不将其历史作业、访谈或测试结论作为我们的交付。

GitHub 保留先前项目历史；当前代码已替换为老师项目快照，旧的 feature/m1-personalized-needs 分支仅对应旧项目。老师原始历史及 P0—P4 文档保存在本地备份，未合并到 GitHub。

## 当前业务和技术

分类：跑腿代办 RUNNING、学习辅导 STUDY、物品借用 BORROW、其他 OTHER。后端 Java 21 / Spring Boot 3.2 / JPA / MySQL，前端 Vue 3 / Vite / Element Plus。

存在注册登录、需求发布与接单、交付和完成确认、取消、通知、任务双方聊天、评价信用及申诉实现。支付状态字段不代表已接入真实支付。

M1 覆盖物品借用、学习辅导、跑腿代办与其他互助的个性化需求、本体和对话卡片原型；M2 再落实鸿蒙客户端与真实 AI 工具执行。

[M2开发进度](docs/M2/README.md)：共用环境与四业务草稿持久化已完成本机部署；动态卡片、模型和可信工具闭环继续按步骤推进。

- [运行与配置](./DEVELOPMENT.md)
- [接管记录](./docs/接管记录.md)
- [课程课件](./docs/reference/00-课程介绍.pdf)

[M1交付索引](./docs/M1/README.md)已在feature/m1-teacher-baseline制作，包含四业务二十种设计情境、统一需求入口与业务切换、业务本体和可运行Vue原型。原团队旧文档仅作本地参考，不是本学期任务要求。
