# 第六步：当前评审与排练证据

2026-10-29，本机演示服务8081及独立部署18082可用，API26模拟器真实操作。当前目录不包含用户访谈或教师验收意见。总览与现场脚本见[08](../../08-评审总览.md)、[09](../../09-现场演示与变化挑战.md)。

- [只读环境预检](./runtime-preflight.json)：服务、模拟器、组件、私有文件存在与权限；未读取并展示私有值，没有模型请求。它是当时快照，现场仍应重新预检。
- [仓颉重新复核](./cangjie-recheck.json)：240共同样本、非法输入、生命周期/重放与并发实验；与第二步历史结果分开，不据18ms进程小实验宣称系统吞吐。
- [串行借用排练](./borrow-ui-serial/review.json)：实际编辑/保存、归还时间冲突、旧确认HTTP409、修改后新卡同步、撤回，无发布与付费。
- [修改后确认卡](./borrow-ui-serial/04-updated-confirmation.png)、[冲突提示](./borrow-ui-serial/03-time-conflict.png)、[撤回](./borrow-ui-serial/05-revoked.png)。截图中的日期是当次合成输入，未来现场需另选未来时间。
- [仓颉预览记录](./preview-review.json)、[新版“查看影响”截图](./06-state-preview.png)：应用真实任务仍待接单/版本0/无接单者；之后独立明确确认取消了这条合成任务，保留审计。
- [结束首页](./07-ready-home.png)：应用恢复到可操作的新对话；不把历史截图说成当前实时屏幕。
- [Python16项检查](./python-tests.txt)：本轮实际重跑，未把历史后端/设备数量重复相加。
- [排练阶段日志](./rehearsal.txt)、[失败与纠正说明](./rehearsal-notes.json)、[初次失败日志](./borrow-initial-failure.txt)。初次误并行改变了全局任务数量基线，`borrow-ui/`只保留该未整体通过的截图；后来另建串行草稿重跑，未覆盖失败、未删历史。
- [交付清单与SHA256](./delivery-manifest.json)：离线验证文档、版本、证据关系和文件内容；生成时的Git HEAD是准备起点，不是声称它已包含尚未提交文件。

本阶段付费模型请求0次、真实付款0次、注册0个账号；使用已有合成账号。额外创建了1条只读预览的合成任务，已单独取消。两条借用排练草稿及失败/撤回回执保留，没有通过清库恢复基线。

安全复核：`python3 scripts/verify-m3-delivery.py`。现场只读预检：`python3 scripts/verify-m3-delivery.py --preflight`。刷新交付清单必须显式`--write`并先核对实际变更；不自动重写原始实验结果。
