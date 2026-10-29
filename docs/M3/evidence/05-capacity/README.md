# 第五步证据索引

本地实验2026-10-28；真实模型观察与交付整理2026-10-29。结论、口径和未验证项见[主报告](../../07-容量SLO与故障演练.md)。

| 文件 | 来源与用途 |
| --- | --- |
| [report.json](./report.json) | 本地实验分阶段检查、资源快照、观测、费用未知项、准备失败；不含真实模型Key |
| [samples.jsonl](./samples.jsonl) | 133条原始操作记录：85条模型入口尝试（79派发＋6准入拒绝）及48条工具重放；正常52条只是其中一个子集 |
| [live-model.json](./live-model.json) | 原开发服务的2次真实DeepSeek调用；与隔离负载分开，没有自动重试 |
| [summary.json](./summary.json) | 独立核验器从原始证据复算，含源文件SHA256；不是手填测试结论 |
| [pricing-snapshot.json](./pricing-snapshot.json) | 10月29日核对的官方峰值报价事实；不是账单 |
| [python-tests.txt](./python-tests.txt) | 本轮16项Python检查结果；没有重复宣称后端/设备全套测试再次运行 |
| [backend-test-followup.json](./backend-test-followup.json)、[日志](./backend-test-followup.txt) | 后续Linux测试夹具修正及本机289项重跑，未更改生产行为 |
| [run.txt](./run.txt) | 实验阶段完成日志 |
| [environment.txt](./environment.txt) | 环境启动、固定转发入口和停止记录；仅规范化行尾空格，保留原语义 |
| [final-review.json](./final-review.json) | 交付时环境核对；压测容器已停止、卷保留，原演示部署仍可用 |

已完成的report和付费抽样文件不能用运行命令覆盖。离线核验不需数据库、Docker或模型Key：

```sh
python3 scripts/verify-capacity-evidence.py docs/M3/evidence/05-capacity
```

要重做实验，使用新的受限项目名及输出目录，不清空本轮私有库。每次命令必须保持相同`--project`。固定环回端口意味着一次只能运行一组容量环境；不要停止其他项目来抢占端口。

```sh
python3 scripts/review-capacity.py --project campus-m3-capacity-demo --output /tmp/campus-capacity-demo init
python3 scripts/review-capacity.py --project campus-m3-capacity-demo --output /tmp/campus-capacity-demo up
python3 scripts/review-capacity.py --project campus-m3-capacity-demo --output /tmp/campus-capacity-demo run
python3 scripts/review-capacity.py --project campus-m3-capacity-demo --output /tmp/campus-capacity-demo down
```

需要已构建的第四步镜像/manifest、Docker和支持`!reset`端口合并的Compose；本次Compose实际版本5.1.3。Python只使用标准库。若报告中断，先查看失败，再用同一目录显式加`--resume`；已完成阶段不会重跑，保留同一组账号、数据和失败。本轮结束后仅停止`campus-m3-capacity`，不删除卷，不影响原演示部署。

两次真实抽样脚本`review-live-model.py`另行要求原开发服务8081、3308/campus_m2_dev的私有配置与合成账号夹具；会消耗真实API额度，文件存在时拒绝重跑。私有配置、用户名/密码/token、数据库备份均不在此目录。

当前实现的边界：本机固定TCP入口不等于云端HTTPS反向代理；SSE首段不等于原生页面完成渲染；供应商替身的注释心跳不等于应用下行周期心跳；故障后重新载入上下文不等于逐Token续传。真实两样本不构成真实模型P95、并发容量或长期可用率证据。
