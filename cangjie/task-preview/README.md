# 仓颉任务状态投影组件

用途：为AI原生客户端提供只读状态预览，并用同一任务模型对照ArkTS。它不连接业务数据库或模型，不接管原有业务事务。源码使用仓颉1.2.0，只有标准库依赖；`cjpm.lock`不包含第三方库。

## 本机工具链

已在macOS arm64使用官方[Cangjie 1.2.0下载页](https://cangjie-lang.cn/download/1.2.0)的`cangjie-sdk-mac-aarch64-1.2.0.tar.gz`。SHA256：

```text
de88d6ba2185c94979e0ab2fe8ac1d81535dfdff3fcb51fbba71e4b99d851f97
```

本机SDK位于`~/.cache/campus-toolchains/cangjie-1.2.0/cangjie`，归档和SDK均在Git之外；没有修改shell启动文件。构建脚本只对子进程设置编译/运行路径，不执行SDK envsetup中的额外系统操作。已用Xcode自带的macOS SDK成功编译链接；其他平台还未验证。

## 构建与验证

在仓库根目录执行：

```bash
python3 scripts/build-task-preview.py
python3 scripts/verify-task-preview.py
```

如SDK安装在其他目录，构建传`--sdk /absolute/path/to/cangjie`，验证设置CANGJIE_HOME。构建产物`cangjie/task-preview/build/task-preview`被Git忽略。也已验证在该工程内、正确设置SDK环境后`cjpm build`可成功；其产物路径与便捷构建脚本不同，开发启动器使用便捷脚本产物。

验证不调用模型或数据库：读取240行公共样本；检查截断/非法输入、完整生命周期、版本上限、重复/冲突事件；四worker模式中128个相同事件只推进一次；运行24轮同版本竞争小实验。进程级时间包含启动开销，不能充当服务器SLO或证明并行提速。

## 运行与后端连接

进程从stdin接收一个固定批次、向stdout输出结果后退出。标准库spawn创建最多4个worker，AtomicInt64分配下标，Mutex保护共享投影和去重表；结果按输入下标输出。[完整协议](../../contracts/task-transitions/README.md)。

开发启动器在找到本仓库编译产物及默认SDK运行库时，启用可选桥；原有数据库/DeepSeek配置仍从被Git忽略的私有文件读取，不会传给子进程：

```bash
python3 scripts/dev-backend.py
```

其他部署必须显式设置`TASK_PREVIEW_BINARY`和`TASK_PREVIEW_RUNTIME`；二进制路径应为服务端控制的绝对路径。组件可通过设空TASK_PREVIEW_BINARY停用，原有确认/执行流程仍可继续。当前没有OS沙箱或容器隔离，不能把清空子进程环境变量等同于沙箱。

后端每实例最多2个子进程，等待子进程最长默认3000ms，另有读取结果的短等待；读入输出上限4096字节，异常只返回通用503。超时会终止子进程并释放准入，不自动重试。程序参数固定，不使用shell或用户传入的可执行路径。

本轮运行证据见[第二步交付](../../docs/M3/03-双语言任务模型与仓颉组件.md)。
