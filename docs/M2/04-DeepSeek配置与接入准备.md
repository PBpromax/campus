# 第四步准备：DeepSeek API配置

日期：2026-10-24。**这是接入前的历史准备记录。当前已完成真实联调，最新结果见[第四步实现](./05-DeepSeek流式对话与草稿建议.md)。**

用户选择DeepSeek官方API。本次仅完成本地配置加载与连通性检查工具；真实模型驱动的流式对话、结构化建议及卡片更新尚未实现，不能把准备工作当作第四步完成。

## 用户需要做的事

1. 在[DeepSeek官方平台](https://platform.deepseek.com/)创建自己的API Key，并确认账户有可用额度。
2. 打开仓库根目录`.env.deepseek.local`，将Key填写到`DEEPSEEK_API_KEY=`后面并保存。不要发送到聊天，不要截图密钥，不要提交Git。
3. 告知“本地已填好”，继续真实API连通性验证与模型接入。

模型名暂设`deepseek-flash`，可在同一文件修改；该名称来自本次核对的[官方首次调用文档](https://api-docs.deepseek.com/)，不沿用未核对的历史模型别名。官方请求地址为`https://api.deepseek.com/chat/completions`。

私有文件权限600，已被Git忽略。与数据库配置分开，只允许DEEPSEEK_API_KEY、DEEPSEEK_MODEL两项；启动脚本`dev-backend.py`会将其作为环境变量加载，仍不执行配置文本中的shell表达式。环境变量就绪不代表后端已经实现了模型调用端点。

## 检查命令

在仓库根目录运行：

```sh
python3 scripts/check-deepseek.py
```

默认只报告是否已配置及模型名，不打印Key、不发起网络请求。

填好Key后进行真实检查：

```sh
python3 scripts/check-deepseek.py --probe
```

这会向官方API发送一次极小的“Reply with OK.”请求，非思考模式、最大输出16 tokens，可能产生API费用。只返回成功状态/HTTP状态，不打印请求头、Key或供应商错误正文。401、402、429分别提示Key无效、余额不足、限流，依据[官方错误码文档](https://api-docs.deepseek.com/quick_start/error_codes/)。不自动重试或连续消耗额度。

## 本次实际验证

运行`python3 -m unittest discover -s scripts/tests -v`：7项离线测试通过。覆盖字面量密钥解析、禁止覆盖数据库配置、重复/损坏配置拒绝且不泄漏值、无Key不发送请求、最小请求格式、错误正文脱敏、无效响应拒绝。

默认本地检查结果为configured=false、networkRequestMade=false。尚未有真实DeepSeek调用，不声称模型名在当前账号可用或账户鉴权已通过。用户填好Key后再执行真实验证。

## 后续实现

后端持有Key → 构造当前业务草稿和必要上下文 → 接收DeepSeek流 → 向鸿蒙发送文字与结构化建议事件 → 按字段白名单、约束和版本校验 → 用户核对建议后更新草稿/卡片。模型不得自行切换已选业务、声称资源可用、取得对方授权或直接发布任务。流式超时、中断与固定表单回退需要单独验证，后续交付另记结果。
