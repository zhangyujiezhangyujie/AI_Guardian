# 成员3阶段4评估交付

本目录交付核验效果评估方法、显式真实实验入口及原始数据位置。报告统稿材料见member3-report.md；完整业务链路性能规程见../../experiments/performance/protocol.md。

2026-11-19已获用户明确授权并完成本轮10次真实调用。结果见[results.md](results.md)：28/30条对齐，27条正确；条件准确率96.43%，全标注正确覆盖率90%。不得省略对齐覆盖率。正式A/B/C各20次已于2026-11-20完成，见../../experiments/performance/results.md；成员2的检索统计尚待交付。

- 数据来源：../annotations-reviewed.json，AI辅助编制，成员2和成员3人工确认；保留原始标签与来源，不声称独立盲标。
- 方法预注册：[method.md](method.md)，包含阳性定义、指标分母、位置对齐和失败处理。
- 真实实验：backend/src/test/java/com/aiguardian/domain/verification/Phase4RealEvaluationTest.java；普通单元测试默认不调用网络。
- 可复现脚本：在backend用 `./run-phase4.ps1 -Action Effect` 显式启用真实模块评估；可指定-Maven和-JavaHome。本轮需向api.deepseek.com发送固定评估输入，只有明确授权后执行。
- 运行归档：runs/下每次独立目录保存原始输入、原始模型content、终判、对齐及指标；latest-run.txt只在一轮正常结束后指向最新目录。
- 性能实验：`./run-phase4.ps1 -Action Offline`成功后才能尝试PerformanceSmoke/PerformanceFormal；真实数据库与完整链路还需运行时验证。普通单测通过不能替代这项验证。

本轮保留已有未提交合并及人工确认修改，没有提交或推送。效果数据与性能数据采用独立目录和独立kind字段，不能互相替代。
