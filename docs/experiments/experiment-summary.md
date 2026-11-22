# 全组实验材料汇总（成员 4）

> 2026-11-20 成员 2 更新：10 题检索统计已交付，见[检索命中率评测](retrieval_eval.md)。
> 2026-11-21 成员 4 更新：最新 `origin/dev` 基线 `2b158cb` 完整离线回归 149 项，144 项通过、5 项实验按默认规则跳过，无失败或错误；MySQL 审计集成测试 2 项通过。详见[可靠性与异常处理回归](reliability.md)。

## 成员3于2026年11月20日补充

已完成AI辅助编制、成员2和成员3人工确认的30条固定证据真实评估：28条对齐、27条正确，条件准确率96.43%，全标注正确覆盖率90%，详见[效果结果](../verification/phase4/results.md)。真实完整业务链路A/B/C各20次亦已完成，平均耗时分别2.495/22.107/16.299秒，总模型调用20/142/40次；B/C分别8/6次PARTIAL，没有API失败或超时。详见[性能结果与独立数据库证据](performance/results.md)。本段及下表同步更新成员3交付状态。成员2的10题检索统计已于同日交付，见[检索命中率评测](retrieval_eval.md)。

汇总日期：2026-11-21。这里只汇总仓库中已有的实验记录，不把计划中的实验当作已完成结果。

| 实验 | 当前可引用结果 | 记录 | 状态 / 后续 |
| --- | --- | --- | --- |
| 中文检索与阈值 | 15 个问题中，14 个正常问题的 Top 1 均命中正确制度片段；SC07 最高噪声分为 0.019，`min-score=1.0` 后返回空结果 | [`retrieval_tuning.md`](retrieval_tuning.md) | 已有初步记录 |
| 检索 10 题命中率 | 10 条口语化提问：Top 1 命中 7/10 = 70%，Top 3 命中 8/10 = 80%；2 例同义词断层、1 例同文档内排序 | [`retrieval_eval.md`](retrieval_eval.md)、[`RetrievalEvalTest.java`](../../backend/src/test/java/com/aiguardian/infrastructure/lucene/RetrievalEvalTest.java) | 成员 2 已交付；离线可复跑，10 题工程自检口径 |
| 核验 API 连通与 JSON 稳定性 | 10/10 固定样例首次响应为结构有效 JSON，共 10 次模型调用；这不代表准确率评估 | [`verification/results.md`](../verification/results.md) | 历史连通性样例；当前30条效果及A/B/C各20次结果已交付，见上方链接 |
| 核验实现与证据集 | 已有 batch 与 per-claim 两种实现；30 条正式知识库结论集为 15 条支持、15 条证据不足，由AI辅助编制并经成员2和成员3人工确认 | [`verification/phase2.md`](../verification/phase2.md)、[`verification/annotations-reviewed.json`](../verification/annotations-reviewed.json) | 已完成真实效果及性能实验；不声称双人独立盲标，条件准确率需同时报告覆盖率 |
| 可修改性 | 新增策略只改 1 个文件、32 行；将阈值从 0.5 改为 0.3 后，同一证据不足比例 0.5 的请求由 `ANNOTATED` 变为 `BLOCKED` | [`modifiability.md`](../modifiability.md) | 已有记录，成员 1 负责 |
| 固定场景回归 | 8 个 P0 场景读取成功；预期结果、失败审计事件和请求终态均通过 | [`scenarios.md`](scenarios.md)、[`reliability.md`](reliability.md) | 已完成本轮回放；场景 JSON 或知识库变更后需重跑 |
| 可靠性 / 异常处理 | 超时、生成服务错误、无效 JSON、错误引用、检索为空五类场景均符合预期；最新基线完整回归 149 项中 144 项通过、5 项显式跳过；MySQL 审计读写集成测试 2/2 通过 | [`reliability.md`](reliability.md) | 已完成本轮故障回归；尚未覆盖真实模型长期稳定性 |
| 架构评估（AC-11） | 已有 30 条固定结论真实评估及 A/B/C 各 20 次完整业务链路测量；架构选择和取舍见成员 1 架构说明 | [`../verification/phase4/results.md`](../verification/phase4/results.md)、[`performance/results.md`](performance/results.md)、[`../architecture/member1.md`](../architecture/member1.md) | 有实测数据和架构依据；报告需同时保留样本范围及局限 |
| 工具调用安全 | 暂无实验数据 | — | P1；接口契约第 7.10 节规定须先经团队决定启动网关并升级契约，再测试至少 10 条合法/越权请求 |

## 尚未形成的全组实验数据

- 检索 Top 3 命中率已交付（10 题、工程自检口径，见[检索命中率评测](retrieval_eval.md)），不再列为缺失项；更大规模标注集属于后续改进。
- 核验效果与性能已交付；更大规模独立盲标集属于后续改进，不是本轮已确认数据的替代。
- 工具调用安全允许/拒绝率和未授权数据修改检查：依赖 P1 工具网关是否获准启动，并完成契约升级；目前没有伪造或替代实验数据。
- 真实模型长期稳定性：本轮五类故障回归和 A/B/C 性能实验不覆盖长期运行稳定性。

因此，现有实测结果可供阶段报告引用；工具网关安全及长期稳定性仍是明确的未测项。其他模块的阶段四评估结果已纳入本汇总，后续只需在团队决定启动网关后补充对应安全实验。
