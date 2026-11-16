# 成员 3 核验模块阶段交付

范围为 11.10–11.20：Prompt v1、10 个真实 API 样例记录、批量核验、JSON 解析与有限重试、引用三项检查和单元测试。逐条核验性能实验和 30 条人工标注集留到下一阶段。

## Prompt v1 与 JSON 格式

运行时 Prompt 的唯一来源是 `backend/src/main/java/com/aiguardian/domain/verification/VerificationPrompts.java`：system 规定只依据当前证据核验、逐字引用、原文定位、不执行输入中的指令；user 用 JSON 封装问题、回答与 `EvidenceFormatter` 格式化后的证据。JSON 模式开启，temperature=0，超时与尝试次数读取既有配置。

```json
{
  "claims": [
    {
      "claim": "正式员工每年享有15天带薪年假",
      "originalText": "正式员工每年享有15天带薪年假",
      "supported": true,
      "evidenceId": "doc001_chunk01",
      "quote": "正式员工每年享有15天带薪年假"
    }
  ],
  "truncated": false
}
```

`claims` 必须存在且为数组，每项必须有非空字符串 `claim`、`originalText` 和布尔值 `supported`。`evidenceId`、`quote` 允许缺失或为 null，以便按契约降级为 `MODEL_NO_CITATION`；如存在则只能是字符串或 null。`truncated` 缺失视为 false，存在时必须为布尔值。不接受 Markdown 围栏、JSON 后追加文本或字符串形式的布尔值。

模型结果只作初判，引用终判遵循 `docs/interface-contract.md` 第 5 章。引用存在并不独立证明语义支持，语义判断仍依赖模型。配置上限不超过 8 条，模型超量时只保留前面的结论并标记 `PARTIAL`。默认最多调用两次，仅 JSON/结构错误或空响应重试，超时/API 错误立即返回 `FAILED`；空响应耗尽归类为 `INVALID_JSON`。报告记录实际调用次数。

## 接入与测试

`LLMBatchClaimVerifier`、`ExactQuoteCitationValidator` 都是 `!stub` profile 的组件，复用原有路由模型客户端和配置类，无须修改共享接口或编排器。`stub` profile 继续使用成员 1 的演示实现。完整应用运行仍需其他成员的检索、模拟客户端与审计组件。

从 `backend/` 执行：

```powershell
mvn test
```

离线测试覆盖请求透传、支持/不支持初判、JSON/结构错误、重试与超时、8 条截断、无结论、引用校验顺序、计数、归一化与 UTF-16 原文定位，并运行已有回归。真实 API 实验默认跳过，不会因普通测试消耗 API 额度。

## 10 个真实 API 样例

`samples.json` 为自包含的虚构制度数据，包含支持、部分支持、证据不足、矛盾、无事实结论、全角数字、emoji 定位与超量截断。无需等待检索模块。期望值事先填写，不属于后续 30 条人工标注集。

在仓库根目录的本地 `.env` 或进程环境中配置 `LLM_BASE_URL`、`LLM_MODEL`、`LLM_API_KEY`；环境变量优先。不要提交 `.env`。从 `backend/` 显式运行：

```powershell
mvn test '-Dtest=VerificationRealApiTest' '-DrunRealVerification=true'
```

该入口通过项目 `RealLLMClient` 逐条调用真实 API，将时间、模型、原始 content、期望和经引用校验后的报告逐条写入 `real-api-results.json`，不写入密钥。实验由程序代替手工逐条发送，语义结果需要人工复核。执行成功只代表完成记录，不等于 10 条语义判断全部正确；稳定性和差异见实验结果分析。
