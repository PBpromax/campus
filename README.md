# CampusHub / campus

校园互助服务平台，基于 [Octonauts](https://github.com/jiahuizhong205/Octonauts) 的 P4-yrh 代码升级开发。新仓库：<https://github.com/PBpromax/campus>。

## 现有代码

- `frontend/`：Vue 3 + Vite 校园互助页面。
- `backend/`：Java 17 / Spring Boot 业务服务。
- `mysql-init/` 和 Docker Compose：开发用 MySQL / Redis。
- `docs/AI-Native/`：新课程课件与升级规划。

当前业务分类和通用发布/接单能力保留；源码存在不代表已完成端到端验收。M1 聚焦快递跑腿与学习求助，交付包见 [docs/M1](./docs/M1/README.md)，独立原型运行方式见 [prototype](./docs/M1/prototype/README.md)。

## 开发环境

先阅读 [DEVELOPMENT.md](./DEVELOPMENT.md)。后端 JWT 密钥必须通过 `JWT_SECRET_BASE64` 环境变量提供，没有内置密钥。Compose 中数据库口令仅为本地演示配置。

## 代码来源与历史

新仓库从清理后的代码快照开始，未上传旧作业交付物及旧提交历史。旧版本保留在本地 `archive/original-code` 和 `archive/original-main` 分支。原项目作者的代码贡献来源保留如上。
