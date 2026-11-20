# 可靠性与异常处理回归（成员 4）

执行日期：2026-11-20

代码基线：本地 `backend` 已合入 `origin/dev` 的 `408627f`（SC02 集成）。

## 验证范围

用本地 Lucene 检索、固定模拟模型和成员 4 的场景回放框架检查五类故障：核验超时、模型服务错误、无效 JSON、引用错误和检索无结果。每次回放都检查预期状态、最终防护动作、请求终态和审计事件；失败核验必须保持 `FAILED / NOT_VERIFIED`，不得出现 `SUPPORTED` 结论。

| 故障类型 | 场景 | 预期与本次观测 | 审计检查 | 结果 |
| --- | --- | --- | --- | --- |
| 引用证据 ID 不在本次上下文、引文不存在 | SC04 | 核验整体为 `COMPLETED`；错误引用分别为 `EVIDENCE_NOT_IN_CONTEXT`、`QUOTE_NOT_FOUND`，合法引用仍可支持 | `CITATION_CHECKED` 包含两类失败原因；请求以 `REQUEST_COMPLETED` 结束 | 通过 |
| 核验返回无效 JSON | SC05 | 两次尝试后为 `FAILED / INVALID_JSON`；防护为 `NOT_VERIFIED`，结论列表为空 | `VERIFICATION_DONE.success=false`、`GUARD_APPLIED.action=NOT_VERIFIED`，请求记录已保存 | 通过 |
| 检索无结果 | SC07 | `NO_EVIDENCE`；不调用生成和核验模型 | 请求以 `REQUEST_COMPLETED` 结束，未产生 `GENERATION_DONE` 或 `VERIFICATION_DONE` | 通过 |
| 核验模型超时 | SC08 | `FAILED / LLM_TIMEOUT`；防护为 `NOT_VERIFIED`，结论列表为空 | `VERIFICATION_DONE.success=false`，请求记录和最终动作已保存 | 通过 |
| 生成模型服务错误 | SC09 | HTTP 502、`LLM_GENERATION_FAILED`；不进入核验 | 写入 `GENERATION_FAILED` 和 `REQUEST_FAILED`，请求记录保留错误码 | 通过 |

场景逐条依据见 [`scenarios.md`](scenarios.md)。场景回放使用内存审计收集器断言每次请求的事件和终态；独立 MySQL 集成测试再检查正常请求与 `FAILED / LLM_TIMEOUT / NOT_VERIFIED` 请求均可落库并由审计详情接口读回。

## 执行结果

- 成员 4 的回归、期望比对、仓库读取和审计单元测试：12 项通过。
- `RetrievalVerificationIntegrationTest`：5 项通过。测试关闭数据源自动配置，并 mock `AuditQueryService` 与 `JdbcAuditPersistenceRepository`，保留真实 Lucene 检索和核验链路，将数据库审计隔离给成员 4 的独立测试。
- 完整 `mvn test`：110 项，0 失败、0 错误、3 项按默认规则跳过（真实 API 与默认关闭的两个 MySQL 测试）。
- 成员 4 的 MySQL 审计集成测试：2 项通过，覆盖正常记录和核验失败记录；测试后清理记录并删除临时低权限数据库账号。
- `JdbcAuditServiceTest` 验证审计 detail 会丢弃 `apiKey`、完整 prompt 等契约外字段；审计写入异常不会向问答调用方抛出。

## 集成测试配置修正

原问题是该测试排除了 `DataSourceAutoConfiguration`，但仍创建依赖 `JdbcTemplate` 的审计查询与持久化组件。修正后在测试中 mock `AuditQueryService` 和 `JdbcAuditPersistenceRepository`，不启动数据库；测试仍运行真实 Lucene 检索与核验。成员 4 的 MySQL 集成测试独立覆盖正式模式下审计读写。

## P1 工具网关

本次没有执行工具网关安全实验。接口契约第 7.10 节写明，`POST /api/tools/execute` 需待 11 月 23 日团队决定启动后再补充；因此目前没有伪造工具调用的安全测试结果。若启动网关，下一步需按契约变更流程补齐接口后，再准备至少 10 条合法/越权请求并记录拦截及数据是否变化。
