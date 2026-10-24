# Campus 鸿蒙客户端

M2第一步已完成，第二步已增加四业务需求草稿，第三步已接入按需规则卡片。包名 `com.pbpromax.campus`，手机入口模块 `entry`。启动页检查后端连接；点击“进入统一需求与草稿”，登录后可选择跑腿代办、学习辅导、物品借用和其他互助，保存与恢复服务端草稿。第四步已接DeepSeek真实流式对话，第五步已接确认后的真实发布、接单和取消。

本机工具：DevEco Studio26.0.0（26.0.0.851），目标SDK26.0.0，兼容SDK6.1.1（API24）。已用IDE自带SDK构建出未签名HAP，并在API26本地手机模拟器安装运行、验证后端HTTP200；发布签名与真机仍未验证。

## 打开项目

仓库目录含空格，创建向导不接受该路径。本机创建了无空格目录别名：

`/Users/pbpromax/Desktop/campus/teacher-project` → `/Users/pbpromax/Desktop/campus/sec-ii-2026 2`

在DevEco Studio中打开 `/Users/pbpromax/Desktop/campus/teacher-project/harmony`。实际源码仍保存在当前仓库，不需要复制第二份项目。

## 本机重复构建

```sh
cd /Users/pbpromax/Desktop/campus/teacher-project/harmony
export DEVECO_SDK_HOME=/Applications/DevEco-Studio.app/Contents/sdk
export JAVA_HOME=/Applications/DevEco-Studio.app/Contents/jbr/Contents/Home
export PATH=/Applications/DevEco-Studio.app/Contents/tools/node/bin:/Applications/DevEco-Studio.app/Contents/tools/ohpm/bin:$PATH
node /Applications/DevEco-Studio.app/Contents/tools/hvigor/bin/hvigorw.js --mode module -p product=default -p module=entry@default -p buildMode=debug assembleHap --no-daemon
```

产物：`entry/build/default/outputs/default/entry-default-unsigned.hap`。未配置签名时构建提示跳过签名属于当前预期，构建成功不代表已经在设备运行。

## 初始化问题与修复

IDE初始化出现插件内部错误，模板只生成部分资源，缺少页面、EntryAbility与main_pages。本次补齐三个文件及module.json5中的Ability声明后，通过真实构建。未将内部错误报告发送给华为。后续还需要检查IDE同步与运行配置。

配置`local.properties`、`.idea`、依赖`oh_modules`、缓存与构建产物均由本目录.gitignore排除；不要提交签名私钥和证书配置。

## 2026-10-23：模拟器运行和后端连接

本机已创建`Campus_M2_Phone`，镜像位于`/Users/pbpromax/Library/Huawei/Sdk`，实例位于`/Users/pbpromax/.Huawei/Emulator/deployed`。在设备管理器启动即可；也可使用官方工具：

```sh
/Applications/DevEco-Studio.app/Contents/tools/emulator/Emulator -start Campus_M2_Phone -instancePath /Users/pbpromax/.Huawei/Emulator/deployed -imageRoot /Users/pbpromax/Library/Huawei/Sdk
```

先在仓库根目录运行`python3 scripts/dev-backend.py`（要求本机数据库3307已启动）。模拟器启动后，使用本机实际设备ID；本次为`127.0.0.1:5555`：

```sh
/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/toolchains/hdc list targets
/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/toolchains/hdc -t 127.0.0.1:5555 rport tcp:8081 tcp:8081
/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/toolchains/hdc -t 127.0.0.1:5555 install entry/build/default/outputs/default/entry-default-unsigned.hap
/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/toolchains/hdc -t 127.0.0.1:5555 shell aa start -a EntryAbility -b com.pbpromax.campus
```

后三条安装/启动命令的工作目录为本文件所在的`harmony`目录。端口转发后，模拟器内的127.0.0.1:8081才会通向Mac的开发后端；该地址和转发方式仅用于当前环境验证。手机端`CampusEnv`标签的`BACKEND_OK HTTP=200 count=0`记录证明请求成功；未通过模拟文本代替网络请求。


## 第二步：四业务草稿

使用已有平台账号登录；本机开发账号在仓库根目录的`.env.m2.accounts.local`中，仅本地保存。登录后自动加载最近的会话，也可以新建/恢复其他会话。先表达目标或手动选择一类业务，补充情境与字段，保存后查看缺失信息和冲突。

确认按钮只表示核对当前草稿，不会发布任务。切换业务会保留各自草稿，恢复后需要重新确认。学习辅导可以只保存当前问题上下文，不必先填写任务报酬等发布资料。第三步已增加动态Schema卡片，完整表单保留为补充和降级入口。

发生版本冲突时，本地输入保留；先记录需要的修改，再使用“放弃未保存输入并重新加载”，按新版本重新编辑。不自动覆盖另一处已保存内容。

后端需包含本次`domain`代码和数据库增量脚本。详见[第二步实现、部署与验收](../docs/M2/02-四业务领域模型与草稿持久化.md)。

## 在模拟器运行测试

先启动开发后端并完成上面的hdc反向端口转发。用相同的环境变量，将构建参数改为`-p module=entry@ohosTest`生成测试HAP，再安装：

```sh
/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/toolchains/hdc -t 127.0.0.1:5555 install entry/build/default/outputs/ohosTest/entry-ohosTest-unsigned.hap
/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/toolchains/hdc -t 127.0.0.1:5555 shell aa test -b com.pbpromax.campus -m entry_test -s unittest OpenHarmonyTestRunner -w 60
```

应先安装主HAP，再安装测试HAP。测试包含一个真实HTTP用例，会在本地隔离开发数据库创建临时测试账号，校验四类草稿及中文上下文并清理自己创建的数据；不要指向真实用户数据库。判断通过必须查看报告中的Failure/Error/Pass，不能只看hdc退出码。


## 第三步：按需动态卡片

需求页默认按缺失信息及已有内容展示四业务分组卡片，保存后按服务端最新校验结果更新。可切换完整表单补充其他条件，未保存内容保留。卡片渲染器本身由本地规则生成，操作经白名单与会话/草稿版本检查；第四步在其前接入模型字段建议，渲染器仍不执行发布工具。协议与验收边界见[第三步说明](../docs/M2/03-动态卡片与固定表单降级.md)。


## 第四步：DeepSeek真实流式对话

登录需求页后可使用“与DeepSeek澄清目标”或“发送给DeepSeek”；原来的仅保存消息/规则候选入口仍保留。模型字段建议须核对后点击采用，才会保存草稿并更新规则卡片；不会确认或发布任务。可停止接收，失败后重新加载核对已保存消息。密钥只在后端。详情及显式付费测试说明见[第四步文档](../docs/M2/05-DeepSeek流式对话与草稿建议.md)。


## 第五步：确认后的真实操作

登录后在需求页下方找到“可信操作”。发布前先保存并确认草稿，再明确填写标题、正数报酬、地点和未来完成时间，点击准备确认卡；核对卡片后才能点击“我已核对，确认执行”。AI操作建议只可填入待核对动作，不会自动准备或执行。

接单／取消可从可接任务或本人任务列表选择编号。接单方取消需说明理由并确认扣5分信用；支付仍只是状态字段。可撤销待执行卡、查询原回执、恢复本人历史请求及读取审计；请求异常后先查询同一回执，不自动重发。

后端需要003增量迁移。常规设备测试现为27项，包含真实发布、重复执行、权限拒绝后继续查询、取消及审计；不隐性调用DeepSeek。详见[第五步与证据](../docs/M2/06-可信工具执行与审计.md)。四业务完整视觉验收、发布签名和真机仍待后续验证。
