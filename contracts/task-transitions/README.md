# transition-v1：同模型双语言契约

模型对应既有任务状态：`PENDING → IN_PROGRESS → PENDING_CONFIRMATION → COMPLETED`，以及`CANCELLED`。四业务分类沿用RUNNING/STUDY/BORROW/OTHER，不引入旧项目枚举。

这是**只读状态投影**：APPLIED仅表示投影内状态推进；不创建任务、不接单、不取消任务，不判断信用、30分钟取消窗口、材料和资金。真正业务仍由现有可信工具在执行时鉴权、校验并记录持久化回执。

## 类型与范围

- TaskSnapshot：id、category、status、version。
- TaskEvent：taskId、id、expectedVersion、kind、participant。
- Decision：outcome、status、version。
- id为1—9007199254740991，版本为0—9007199254740991。这个共同范围用于兼容ArkTS安全整数；仓颉Int64有更大范围，本组件主动收窄。
- 事件id仅允许1—64位ASCII字母、数字、`_`、`-`，在单次投影的taskId范围内去重。版本到上限返回VERSION_EXHAUSTED，不溢出。
- 状态、事件、参与者均用有限枚举。HTTP调用者只提交任务ID和事件；服务器从数据库推导状态、版本及当前账号的参与者身份。

## 状态表

只有以下组合推进，其他组合返回STATE_OR_ROLE_REJECTED。

| 原状态 | 事件 | 参与者 | 预计状态 |
| --- | --- | --- | --- |
| PENDING | ACCEPT | OTHER | IN_PROGRESS |
| IN_PROGRESS | SUBMIT | ACCEPTOR | PENDING_CONFIRMATION |
| PENDING_CONFIRMATION | CONFIRM | PUBLISHER | COMPLETED |
| PENDING | CANCEL | PUBLISHER | CANCELLED |
| IN_PROGRESS | CANCEL | ACCEPTOR | CANCELLED |
| PENDING_CONFIRMATION | CANCEL | ACCEPTOR | CANCELLED |

任务ID不符返回TASK_MISMATCH，版本不符返回VERSION_CONFLICT。版本与ID检查先于状态表。两个语言实现的纯reduce可以表达这些结果；CLI以已加载任务查询，未知taskId作为整体输入错误拒绝。

## 重复与乱序语义

EventProjection仅存在于一次进程调用/一次ArkTS对象生命周期。相同(taskId,eventId)和相同事件内容重放时返回REPLAY，status/version是**首次投影的历史结果**，不是最新状态。首次结果也可能是拒绝，REPLAY本身不能当作首次推进成功；需要首次outcome时应保留首次响应。相同id但不同内容返回EVENT_ID_CONFLICT，不改变状态。

并发模式不保证事件按输入顺序拿到锁。未来版本的事件若先执行会返回VERSION_CONFLICT，不自动等待或重排、不自动重试。生命周期顺序测试使用SERIAL；PARALLEL用于独立任务和同版本竞争。生产业务的跨重启幂等继续由MySQL工具回执负责。

## 固定进程协议

避免为小组件新增第三方JSON包，采用ASCII TSV；HTTP边界仍使用JSON。二进制为仓库源码编译的固定程序，不接受脚本、路径、模型代码或任意命令。

```text
CAMPUS_TRANSITION_V1\tPARALLEL
TASK\t17\tSTUDY\tPENDING\t0
EVENT\t17\tpreview\t0\tCANCEL\tPUBLISHER
END
```

示例中的`\t`表示实际制表符。输入头也可为SERIAL。每批最多128个快照、512个事件、单行256字节、总量131072字节；先解析整个批次，再处理事件。过量、未知枚举、重复任务或缺失END整体拒绝。Java桥每次只发送一个从数据库读取的快照和一个事件，输入远小于上限。

```text
CAMPUS_TRANSITION_V1
RESULT\t0\t17\tpreview\tAPPLIED\tCANCELLED\t1
SUMMARY\t1\t1
```

RESULT字段：输入下标、taskId、eventId、outcome、预计status、预计version。SUMMARY为worker数和事件数。错误只输出`ERROR\tINVALID_INPUT_OR_EXECUTION`，退出码2；成功退出码0。输出按输入下标排列，不能以输出顺序推断并发锁顺序。

## 一致性样本

唯一公共样本文件是`harmony/entry/src/main/resources/rawfile/task-transition-cases.tsv`：4分类×5状态×4事件×3身份=240行。每行是category、原status、version、event、participant、预期outcome、预期status、预期version；用例表由独立状态表生成，ArkTS设备测试与仓颉CLI验证读取同一文件。

修改语义时先评审本契约，再更新两种实现与公共样本。`python3 scripts/verify-task-preview.py`默认检查样本漂移，不静默重写；`--write-fixtures`仅用于明确更新契约后的生成。样本没有真实用户数据。
