# 开发和验证

工作目录为老师项目文件夹。需要 Java 21、Maven、Node.js 20+，完整平台需要 MySQL；原型无需数据库和模型密钥。

## 本地数据库与后端

复制 `.env.example` 为 `.env`，填写自己的 DB_PASSWORD、MYSQL_ROOT_PASSWORD、JWT_SECRET。可用 `openssl rand -base64 48` 在本机生成 JWT_SECRET，不提交生成值。Docker Compose 会读取 .env；Spring Boot 和 shell 不会自动读取它。

```sh
docker compose up -d
export DB_PASSWORD='本机数据库密码'
export JWT_SECRET='本机生成并安全保存的随机值'
cd backend
mvn spring-boot:run
```

默认 MySQL 地址为 127.0.0.1:3307/campus_help，用户 campus。也可用 DB_URL / DB_USERNAME 指定自己的隔离数据库，不使用原团队云端连接。现有 JPA ddl-auto=update 仅适合开发，生产迁移列为技术债。

默认不创建演示账号。仅在隔离演示数据库需要时设置 DEMO_SEED_ENABLED=true；会创建 testuser1、testuser2、admin，演示密码123456，勿用于真实部署。

Redis 已提供可选服务，当前主要用户/任务流程没有 Redis 调用。短信默认模拟，不代表真实发送。数据库 init.sql 仅保留 UTF-8 建表，未导入原用户数据；它不是已有数据库升级脚本。

## 原平台前端

```sh
cd frontend
npm ci
npm run dev
```

默认 localhost:5173，/api 代理到 localhost:8080。改后端端口时同步调整代理。

## 后端隔离验证

```sh
cd backend
mvn -B -Dspring.profiles.active=test test
```

test profile 使用 H2 create-drop，不连接云数据库，Redis 自动配置关闭，短信模拟。测试环境 JWT 仅为公开测试夹具，与私有运行配置分开。必须使用 Java 21；先用 java -version 和 mvn -version 检查，不以旧 jar 或原团队覆盖率作为本次测试证据。

## 协作

origin 为 PBpromax/campus，upstream 为老师原 GitLab。只向 origin 推送。main 是迁移后的稳定快照；M1 在 feature/m1-teacher-baseline 开发。老师原历史在本地 archive/teacher-original 和外部 Git bundle 中保存。
