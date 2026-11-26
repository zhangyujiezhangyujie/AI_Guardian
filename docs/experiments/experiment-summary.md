# 全组实验材料汇总（成员 4）

> 2026-11-20 成员 2 更新：10 题检索统计已交付，见[检索命中率评测](retrieval_eval.md)。
> 2026-11-21 成员 4 更新：`origin/dev` 基线 `2b158cb` 完整离线回归 149 项，144 项通过、5 项实验按默认规则跳过，无失败或错误；MySQL 审计集成测试 2 项通过。详见[可靠性与异常处理回归](reliability.md)。
> 2026-11-26 成员 4 更新：P1 网关已合入 `dev`（`dbd12b8`）；同一代码树的后端全量回归 194 项，0 失败、0 错误、5 项显式跳过；MySQL 审计集成测试 2/2 通过，网关 MySQL 8.0.46 安全实验 10 条请求符合预期。详见[可靠性记录](reliability.md)和[工具网关实验](tool_security.md)。

## 成员3于2026年11月20日补充

已完成AI辅助编制、成员2和成员3人工确认的30条固定证据真实评估：28条对齐、27条正确，条件准确率96.43%，全标注正确覆盖率90%，详见[效果结果](../verification/phase4/results.md)。真实完整业务链路A/B/C各20次亦已完成，平均耗时分别2.495/22.107/16.299秒，总模型调用20/142/40次；B/C分别8/6次PARTIAL，没有API失败或超时。详见[性能结果与独立数据库证据](performance/results.md)。本段及下表同步更新成员3交付状态。成员2的10题检索统计已于同日交付，见[检索命中率评测](retrieval_eval.md)。

汇总日期：2026-11-26。这里只汇总仓库中已有的实验记录，不把计划中的实验当作已完成结果。

| 实验 | 当前可引用结果 | 记录 | 状态 / 后续 |
| --- | --- | --- | --- |
| 中文检索与阈值 | 15 个问题中，14 个正常问题的 Top 1 均命中正确制度片段；SC07 最高噪声分为 0.019，`min-score=1.0` 后返回空结果 | [`retrieval_tuning.md`](retrieval_tuning.md) | 已有初步记录 |
| 检索 10 题命中率 | 10 条口语化提问：Top 1 命中 7/10 = 70%，Top 3 命中 8/10 = 80%；2 例同义词断层、1 例同文档内排序 | [`retrieval_eval.md`](retrieval_eval.md)、[`RetrievalEvalTest.java`](../../backend/src/test/java/com/aiguardian/infrastructure/lucene/RetrievalEvalTest.java) | 成员 2 已交付；离线可复跑，10 题工程自检口径 |
| 核验 API 连通与 JSON 稳定性 | 10/10 固定样例首次响应为结构有效 JSON，共 10 次模型调用；这不代表准确率评估 | [`verification/results.md`](../verification/results.md) | 历史连通性样例；当前30条效果及A/B/C各20次结果已交付，见上方链接 |
| 核验实现与证据集 | 已有 batch 与 per-claim 两种实现；30 条正式知识库结论集为 15 条支持、15 条证据不足，由AI辅助编制并经成员2和成员3人工确认 | [`verification/phase2.md`](../verification/phase2.md)、[`verification/annotations-reviewed.json`](../verification/annotations-reviewed.json) | 已完成真实效果及性能实验；不声称双人独立盲标，条件准确率需同时报告覆盖率 |
| 可修改性 | 新增策略只改 1 个文件、32 行；将阈值从 0.5 改为 0.3 后，同一证据不足比例 0.5 的请求由 `ANNOTATED` 变为 `BLOCKED` | [`modifiability.md`](../modifiability.md) | 已有记录，成员 1 负责 |
| 固定场景回归 | 8 个 P0 场景读取成功；预期结果、失败审计事件和请求终态均通过 | [`scenarios.md`](scenarios.md)、[`reliability.md`](reliability.md) | 已完成本轮回放；场景 JSON 或知识库变更后需重跑 |
| 可靠性 / 异常处理 | 超时、生成服务错误、无效 JSON、错误引用、检索为空五类场景均符合预期；网关分支后端完整回归 194 项中 189 项通过、5 项显式跳过；MySQL 审计读写集成测试 2/2 通过 | [`reliability.md`](reliability.md) | 已完成本轮故障回归；尚未覆盖真实模型长期稳定性 |
| 架构评估（AC-11） | 已有 30 条固定结论真实评估及 A/B/C 各 20 次完整业务链路测量；架构选择和取舍见成员 1 架构说明 | [`../verification/phase4/results.md`](../verification/phase4/results.md)、[`performance/results.md`](performance/results.md)、[`../architecture/member1.md`](../architecture/member1.md) | 有实测数据和架构依据；报告需同时保留样本范围及局限 |
| 工具调用安全（P1） | H2 与 MySQL 8.0.46 各完成同一组 10 条请求（4 合法、6 越权/非法）：放行 4、拦截 6（5 拒绝、1 转审批）；越权/非法请求造成的数据修改 0 条，由独立 SQL 前后快照证明 | [`tool_security.md`](tool_security.md)、[MySQL 原始结果](tool_security_mysql_report.json)、[`ToolSecurityExperimentTest.java`](../../backend/src/test/java/com/aiguardian/tool/ToolSecurityExperimentTest.java) | 2026-11-22 经组内决定启动并升级契约 v1.3；2026-11-23 已完成 MySQL 8.0.46 复现 |

## 尚未形成的全组实验数据

- 检索 Top 3 命中率已交付（10 题、工程自检口径，见[检索命中率评测](retrieval_eval.md)），不再列为缺失项；更大规模标注集属于后续改进。
- 核验效果与性能已交付；更大规模独立盲标集属于后续改进，不是本轮已确认数据的替代。
- 工具调用安全：H2 与 MySQL 8.0.46 上的 10 条请求实验均已完成；报告如实限定为模拟工具、模拟数据及本轮决策分支覆盖，不外推为真实身份认证或生产系统安全结论。
- 真实模型长期稳定性：本轮五类故障回归和 A/B/C 性能实验不覆盖长期运行稳定性。

因此，现有实测结果可供阶段报告引用；真实模型长期稳定性仍未测。工具网关实现、接口契约 v1.3 与安全实验记录已合入 `dev` 的 `dbd12b8`。

## 风险分析与控制建议（报告素材）

| 风险 | 当前依据与影响 | 建议控制 |
| --- | --- | --- |
| 演示身份不能作为真实授权 | 网关用请求头 `X-Demo-User` 区分 `user` 与 `manager`，客户端可自行构造；当前实验不能证明真实身份认证有效 | 接入可信身份认证，并由服务端映射角色后再连接真实业务数据 |
| 审批流程尚未实现 | 删除请求返回 `REQUIRE_APPROVAL` 并停止执行；项目没有实际审批流 | 保持默认拒绝执行，接入并验证审批闭环后才允许敏感操作 |
| 安全测试覆盖范围有限 | H2 与 MySQL 各覆盖同一组 10 条模拟请求；工具操作的是演示表，未覆盖提示词注入或真实业务系统 | 把结果限定为当前决策分支回归；上线前补充对抗性测试和独立安全评审 |
| 审计记录包含未遮蔽内容 | 审计详情按契约保存并返回原始回答和核验信息，便于追溯但可能承载敏感内容 | 部署时落实审计访问控制、数据保留期限和受保护存储 |
| 长期可靠性尚未测量 | 已覆盖五类可控故障和有限次数性能实验，没有长时间运行、并发压力或真实服务持续故障实验 | 后续进行持续运行和并发测试，并为模型服务及数据库配置运行监控和告警 |
