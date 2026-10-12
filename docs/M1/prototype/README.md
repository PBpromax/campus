# M1 Vue 规则原型

独立工程，使用 Vue 3 / Vite。快递跑腿五场景、学习求助五场景，无数据库、模型密钥或生产接口依赖。所有操作为模拟，规则仅识别有限中文表达；未知或不确定的信息通过追问与手动字段补充。

## 启动

需要 Node.js 20+ 与 npm：

```sh
cd docs/M1/prototype
npm ci
npm test
npm run dev
```

开发地址默认为 <http://127.0.0.1:5180>。生产构建与预览：

```sh
npm run build
npm run preview
```

预览地址默认为 <http://127.0.0.1:5181>。`release/` 已提交构建产物，可由任意静态 HTTP 服务器提供；直接双击 HTML 可能触发模块跨源限制。未安装依赖时也可执行 `python3 -m http.server 5181 --bind 127.0.0.1 --directory release`。

## 浏览器验证

先保持预览服务运行，在另一个终端执行：

```sh
npm run verify:browser
```

默认使用本机 Google Chrome。没有 Chrome 时，可使用 Playwright Chromium：

```sh
npx playwright install chromium
M1_BROWSER=chromium npm run verify:browser
```

验证可通过 `M1_URL` 指定服务地址；报告与截图写入上一级 `evidence/`，仅使用演示输入。环境不是 Chrome 时浏览器版本与截图会相应不同。

## 源码结构

- `src/scenarios.js`：十个设计样本、状态显示文案。
- `src/engine.js`：会话、字段识别、缺口/冲突、修改与模拟确认规则。
- `src/App.vue`：对话、方式比较、动态字段、确认/轻量学习区域、回退与模拟接管。
- `tests/`：规则、候选方案反例及真实浏览器验证。
- `release/`：构建产物，使用相对资产路径，无 CDN 运行依赖。

## 能力限制

- 不生成真实模型答案，不解析任意自然语言，未实现地图、翻译服务、上传照片或真实通知。
- 自动识别只覆盖演示用校区、驿站、件数和部分学习词语，其他课程/地点可用固定表单补充。
- 原型采用同日时间校验，复杂跨日安排需额外澄清；真实资源可用性未验证。
- 上下文与模拟确认只在页面内存；刷新、重置或确认切换样本时清空。
- 用户动作的前端校验不是生产授权，不应作为真实发布/接单安全边界。
