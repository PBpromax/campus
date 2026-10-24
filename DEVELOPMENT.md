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

## 本机Java21与M1原型

本次已在本机缓存安装Temurin21用于验证；当前系统默认java可能仍是其他版本。需要复用时：

```sh
export JAVA_HOME="$HOME/.cache/campus-toolchains/jdk-21.0.12.1+1/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
```

其他电脑配置自己的JDK21路径即可，不要求相同缓存目录。

M1独立原型：`cd docs/M1/prototype` 后执行 `npm ci`、`npm test`、`npm run build`、`npm run preview`，地址 http://127.0.0.1:5187 。Node需20.19+或22.12+；无需数据库和密钥。[完整交付](./docs/M1/README.md)。


## M3仓颉只读组件

本机Mac ARM64已使用独立Cangjie 1.2.0 SDK；在仓库根目录运行`python3 scripts/build-task-preview.py`与`python3 scripts/verify-task-preview.py`。构建后开发启动器自动检测默认路径，启用只读状态预览；生产默认不启用，需明确配置TASK_PREVIEW_BINARY和TASK_PREVIEW_RUNTIME。SDK/二进制不进入Git，子进程不接收DB/JWT/模型密钥。详见[组件说明](./cangjie/task-preview/README.md)及[M3第二步](./docs/M3/03-双语言任务模型与仓颉组件.md)。


## M3模型可靠性与日额度

后端需要005增量迁移。默认模型HTTP全程45秒，连续3次故障冷却30秒；上海自然日按本人/全局次数、Token和估算USD额度持久化预留。客户端可查看本人额度和停止后台回复；原有确认执行不依赖模型额度。默认报价、控制变量、有限出域检查及真实账单边界见[第三步说明](./docs/M3/05-模型网关可靠性与额度.md)。本地模型替身脚本只用于隔离开发库，联调后恢复正常启动器与私有Key配置。
