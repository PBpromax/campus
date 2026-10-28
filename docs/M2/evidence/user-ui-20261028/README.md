# 新版借用流程补查（2026-10-28）

本机API26模拟器，连接原开发数据的8081服务。使用原合成账号，以API准备同一个借用草稿；编辑、保存、冲突显示、重新核对和撤回均在原生页面操作。没有发模型请求，也没有最终发布任务。

- [首页](./01-home.png)、[初始借用需求](./02-borrow-before.png)。`open-current.png`保留刚恢复服务时的欢迎页，不能当作就绪首页。
- [归还时间冲突](./03-time-conflict.png)：归还早于使用结束，被明确指出；旧确认执行HTTP409，任务数量未变化。
- [修改后的确认卡](./04-updated-confirmation.png)：归还改为12:30、地点改为图书馆北门服务台；重新确认后新卡同步更新。
- [返回修改后的撤回](./05-revoked.png)：新回执REVOKED，没有发布。
- [机器可读记录](./review.json)。前次完整真实模型/发布/接单/消息/取消证据仍在[10月27日](../user-ui-20261027/README.md)，不记成本轮重跑。

首次脚本在第二次点击“全部信息”时误以为表单开关仍关闭，控件寻找最终超时；此前冲突与旧确认拒绝已通过。修正为重新打开同一草稿、重置页面开关后继续，保留会话65，没有重新注册或清数据。原失败日志保存在本机`/tmp/campus-user-ui-additional-review.log`。

复核命令（私有夹具不提交）：

```sh
python3 scripts/review-user-ui.py --credentials /path/to/private-fixture.json --state /tmp/campus-ui-review-state.json --output /path/to/new-evidence
```

输出目录必须没有完成的review.json；中断可使用同一state继续。仅适用于本项目本机8081和已安装鸿蒙应用，不能指向生产系统。脚本不会调用付费模型或创建真实委托。
