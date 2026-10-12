# CampusHub 开发环境与协作

## 原平台开发环境

Java 17、Maven、Node.js 20+、npm、Docker。原平台与M1原型分别运行，原型无需数据库。

在根目录执行 `docker compose up -d`，MySQL使用宿主机3307端口、数据库campushub_db，Redis使用6379端口。Docker服务需要先启动。当前初始化使用根mysql-init脚本；后端旧脚本差异记录在M1现状文档，勿将清库脚本用于已有数据升级。

后端启动前提供私有JWT密钥：

```sh
export JWT_SECRET_BASE64="$(openssl rand -base64 32)"
cd backend
mvn spring-boot:run
```

这是本地生成示例，不要提交生成值。需要登录跨重启有效时，安全保存并注入同一私有密钥。更换密钥会使旧Token失效。Spring Boot不会自动加载根目录.env；无JWT_SECRET_BASE64时启动缺少必要配置。

原前端：

```sh
cd frontend
npm ci
npm run dev
```

## M1独立原型

```sh
cd docs/M1/prototype
npm ci
npm test
npm run build
npm run preview
```

完整说明见 [M1原型README](./docs/M1/prototype/README.md)。默认预览地址为http://127.0.0.1:5181。所有推理与业务动作明确为模拟。

## 协作与验证

origin为https://github.com/PBpromax/campus.git。main保存初始干净快照，后续使用feature/*分支。提交说明标注[AI-assisted]或[Human-written]，AI结果须由团队审查；测试、用户反馈、贡献与实验结果必须真实。

当前CI验证原前端构建、后端评价/JWT独立单测、M1规则/构建/浏览器；没有将全Spring上下文与数据库端到端测试冒充已通过。
