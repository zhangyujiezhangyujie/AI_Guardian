# 固定测试场景（阶段三）

2026 年 11 月 16 日。`test-cases/` 下已实现 7 个场景：SC01、SC03、SC04、SC05、SC07、SC08、SC09。**SC02 暂缺**，原因见文末。

## 场景与证据

| 场景 | 问题 | 用到的证据 | annotate | block |
| --- | --- | --- | --- | --- |
| SC01 | 公司正式员工一年有多少天年假？ | doc001_chunk02 年假制度 | ANNOTATED | ANNOTATED |
| SC03 | 公司的报销和补贴政策是什么？ | doc005_chunk01 总则与餐费补贴 | ANNOTATED | BLOCKED（2/3 证据不足） |
| SC04 | 员工请假需要提前几天申请？ | doc002_chunk01、doc002_chunk02 | ANNOTATED | BLOCKED（2/3 证据不足） |
| SC05 | 产品的保修期是多久？ | doc007_chunk01 保修范围与期限 | NOT_VERIFIED | NOT_VERIFIED |
| SC07 | 公司食堂周末几点开门？ | 无（检索为空） | NO_EVIDENCE | NO_EVIDENCE |
| SC08 | 员工迟到如何处理？ | doc004_chunk02 迟到、早退与旷工 | NOT_VERIFIED | NOT_VERIFIED |
| SC09 | 报销需要提交哪些材料？ | 无（生成失败，返回 502） | — | — |

## 各场景的模拟设计

- **SC01**：1 条 supported 结论，quote 逐字取自 doc001_chunk02。
- **SC03**：3 条结论，餐费补贴有证据，交通补贴与话费补贴是知识库不存在的内容（`MODEL_UNSUPPORTED`），证据不足比例 0.6667，block 阈值 0.5 下被拦截。
- **SC04**：3 条结论分别命中三种引用失败——不存在的 evidenceId（`EVIDENCE_NOT_IN_CONTEXT`）、编造的 quote（`QUOTE_NOT_FOUND`）、正确引用（`SUPPORTED`）。
- **SC05**：`VERIFY_CLAIMS` 两次都返回非 JSON 文本，核验器重试后仍失败，状态 `FAILED / INVALID_JSON`。
- **SC07**：`mock` 为空对象，检索为空时编排器不调用模型，直接返回 `NO_EVIDENCE`。
- **SC08**：`VERIFY_CLAIMS` 返回 `TIMEOUT`（`delayMs=2000`），状态 `FAILED / LLM_TIMEOUT`。
- **SC09**：`GENERATE_ANSWER` 返回 `API_ERROR`，接口返回 502 `LLM_GENERATION_FAILED`，不进入核验。

## 验证方式与结果

因为成员 3 的核验器在 `feature/verifier`（`1f31d3d`）上，本次验证**临时合并**该分支，用真实检索器 + 真实核验器 + 真实引用校验 + 真实输出防护 + 模拟模型，对每个场景的每种策略各跑一遍，审计服务用空实现。验证后已撤销合并，测试类未提交。

| 场景 | 策略 | verificationState | guard.action |
| --- | --- | --- | --- |
| SC01 | annotate | COMPLETED | ANNOTATED |
| SC01 | block | COMPLETED | ANNOTATED |
| SC03 | annotate | COMPLETED | ANNOTATED |
| SC03 | block | COMPLETED | BLOCKED |
| SC04 | annotate | COMPLETED | ANNOTATED |
| SC04 | block | COMPLETED | BLOCKED |
| SC05 | annotate / block | FAILED / INVALID_JSON | NOT_VERIFIED |
| SC07 | annotate / block | NO_EVIDENCE | NO_EVIDENCE |
| SC08 | annotate / block | FAILED / LLM_TIMEOUT | NOT_VERIFIED |
| SC09 | — | 502 LLM_GENERATION_FAILED | — |

全部与 `expected` 一致。

## 验证中发现并修正的问题

SC04 的第 3 条结论原来用 `婚假10天` 作为 quote，只有 5 个字符，低于 `min-quote-chars=6`，会被判成 `QUOTE_TOO_SHORT` 而不是预期的 `SUPPORTED`。已改为知识库原文中的 `可享受婚假10天`。

这条说明：场景里的 quote 不仅要逐字存在，还要满足最小长度，否则“引用校验”会以另一种原因失败，`expected` 必然对不上。

## 注意事项

1. `evidenceId` 与 `quote` 都依赖当前知识库和切分参数。知识库、章节或切分参数一改，**所有场景必须重新核对**；成员 4 的回归测试是这件事的兜底。
2. SC08 的 `delayMs=2000` 会让该场景真实等待 2 秒，回归测试耗时需计入。
3. SC02 未实现：契约 10.5 期望“年假 SUPPORTED”，但“公司员工有哪些福利？”检索到的前 3 条全是《员工福利规定》的片段（doc005），年假片段（doc001_chunk02）不在本次证据里，引用校验会把年假结论判成 `EVIDENCE_NOT_IN_CONTEXT`。需成员 1 在“改模拟回答为餐补 + 交通补贴”“该场景放宽 topK”“改问题或期望”三者中裁决后再补。
