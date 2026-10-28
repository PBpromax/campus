# 独立后端发布

在仓库根目录运行命令。此目录独立于根目录旧版`compose.yml`：只管理显式命名的`campus-*`项目，不复用开发数据库，不开放MySQL主机端口。

## 首次启动

需要Docker Engine/Compose v2、Python 3.11+、Maven及Java 21。构建会运行后端测试、打包Jar、校验官方仓颉1.2.0 SDK的SHA256，并在Linux容器里编译组件；Linux arm64已实测，amd64配置尚未实测。首次下载SDK约193MB，工具缓存放在用户目录，源码仓库不收SDK、镜像或数据库备份。

```sh
export JAVA_HOME="$HOME/.cache/campus-toolchains/jdk-21.0.12.1+1/Contents/Home"
python3 scripts/release.py init --port 18082
python3 scripts/release.py build --tag campus-m3:release-a
python3 scripts/release.py up --image campus-m3:release-a
python3 scripts/release.py status
curl -fsS http://127.0.0.1:18082/actuator/health/readiness
```

JAVA_HOME按本机Java21安装位置调整；它不会自动修改shell启动文件。构建使用固定digest的官方Java21镜像和固定校验值的仓颉SDK；数据库镜像同样固定digest。包依赖仍来自Maven/Ubuntu软件源，本流程不声称构建逐字节可重现、离线构建或完成多架构认证。

`init`只执行一次，创建`deploy/private/campus-m3-review/`。父目录与secrets目录0700，状态/备份0600；secret文件0444使容器内UID10001可读取，但主机其他用户不能穿过0700父目录。文件按服务挂载：后端不能读取数据库root密码。密钥不写进镜像、Compose环境、构建参数或公开证据。Compose文件挂载不等于集中式密钥管理，能控制Docker的管理员仍能读取。

模型密钥默认留空，此时登录、草稿、可信工具、仓颉预览与真人协商可用。需要模型时，在本机编辑该项目的`secrets/DEEPSEEK_API_KEY`并保持权限；重建后端进程才会重新加载。原开发私有配置不会被自动复制。不要在聊天、命令参数或截图中粘贴Key。

## 检查与停止

- `GET /actuator/health/liveness`：进程存活；不依赖数据库或DeepSeek。
- `GET /actuator/health/readiness`：进程可接收流量且数据库可连接；不付费探测模型。
- 两者只公开`status`；`env/configprops/flyway/shutdown`未暴露。检查模型与工具观测仍走已有管理员权限。
- `python3 scripts/release.py down`停止本项目并保留数据卷/备份；没有自动清卷命令。

后端以UID10001运行，根文件系统只读、临时目录有界、无额外capability、限制1GiB与2CPU，日志轮转。SIGTERM最长预留65秒，Spring优雅关闭阶段50秒；已验证空闲进程正常关闭，尚未证明任意长流的无损接续。

## 升级与回滚

先审查新迁移是否向后兼容，再声明`--compatible`。脚本先保存私有逻辑备份，再解析本地不可变镜像ID并启动候选；健康/启动失败会恢复上一应用镜像。状态记录上一健康版本，不使用漂移的tag作为回滚依据。

```sh
python3 scripts/release.py build --tag campus-m3:release-b
python3 scripts/release.py up --image campus-m3:release-b --compatible
python3 scripts/release.py rollback --compatible
```

这是单实例有停机窗口的替换，不是蓝绿或零停机发布。回滚只替换应用，不执行SQL降级；新增表/列保留。破坏性或不兼容迁移不应使用自动应用回退，需先另行设计兼容发布或独立恢复方案。首个发布没有上一健康版本，失败后保留诊断状态，不能承诺自动恢复。

## 备份与恢复

```sh
python3 scripts/release.py backup
python3 scripts/release.py --project campus-restore init --port 18083
python3 scripts/release.py --project campus-restore restore --backup /absolute/private/backup.sql
python3 scripts/release.py --project campus-restore up --image campus-m3:release-a
```

备份只包含本项目数据库，使用一致性事务、不含DROP TABLE；restore先检查目标数据库为空，非空立即拒绝。恢复后用新实例JWT密钥重新登录，核对会话、任务、回执、审计和迁移校验。DDL导入并非整库事务，导入中断后保留错误与现场，不自动清库重试。备份不能直接当作对现有数据库的升级脚本。

## 数据库版本

部署profile启用Flyway与Hibernate validate，禁用自动改表、Flyway clean与自动baseline；开发/test profile不自动接管旧库。V1是原团队六张表的纯DDL，V2—V6分别冻结现有001—005增量脚本。已应用的版本不再编辑，后续加V7及之后文件。新库初始化和已存在、带有效版本记录的本项目升级可使用此流程。

现有开发库没有Flyway记录，不能直接套部署profile或开启baseline-on-migrate跳过检查。接管旧库需要先备份、比较实际DDL与全部版本，在恢复副本上评审显式baseline方案；本轮没有自动接管旧库，也没有给它补造迁移历史。

## 网络与云端边界

当前入口仅`127.0.0.1:18082`的本机HTTP，MySQL只有项目内网。后端可向固定DeepSeek HTTPS端点出站；仓颉只读子进程不接收业务自由文本或凭据。没有Redis部署依赖。

鸿蒙开发客户端仍连接原8081端口，未自动切换到18082的新实例。上云时需提供服务器、域名/证书，配置HTTPS反向代理、SSE禁缓存/禁缓冲、超过60秒的代理读取超时和网络访问规则，并修改客户端基址、重新构建及实测。当前未声称云端、HTTPS、外网客户端或高可用部署已完成。

## 本轮复核

```sh
python3 -m unittest discover -s scripts/tests -v
python3 scripts/review-deployment.py seed
python3 scripts/review-deployment.py restart-fault
```

`seed`仅对新建空的`campus-m3-review`执行一次，使用合成账号。已有本轮夹具不要重新seed；`seed-check`仅复核已保存的会话/任务。`restart-fault`会停止/恢复这个项目的数据库和后端，并独立确认取消自己的测试任务，不对任意项目运行。它用于隔离演练，不应当作日常健康探针。

回滚的故障夹具在`deploy/fixtures/`。对已验证的镜像传`--build-arg BASE_IMAGE=sha256:...`，分别构建`Dockerfile.bad`与`Dockerfile.configuration-b`。前者故意退出，后者保持同一Jar、仅改变配置标记；依次验证失败自动回退、健康B发布及`rollback --compatible`。本轮未把配置回滚冒充不同业务代码版本的兼容性证明。

备份恢复到`campus-m3-restore-review`后，可运行`review-deployment.py restore-check`；`checksum-fault`仅对该恢复副本临时破坏V6账本校验值、验证拒绝启动，并在finally恢复原值。本轮完成后已停止恢复副本，保留私有数据卷供检查。完整结果和边界见[M3第四步](../docs/M3/06-可重复部署与恢复.md)。


已有完成的评审夹具可安全复核：

```sh
python3 scripts/review-deployment.py verify-current
```

此命令检查原会话、六个迁移及已成功回执的幂等重放；不重新取消任务。它只用于已有`campus-m3-review`夹具，且恢复副本应已停止。2026-10-28升级到当前工作区后已实测。初始`restart-fault`包含取消操作，不可对已取消的同一任务盲目重跑。
