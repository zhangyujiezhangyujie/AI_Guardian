# 问答与输出策略接口

后端默认地址为 `http://localhost:8080`，请求及响应使用 UTF-8 JSON。所有可空响应字段照常输出。完整跨模块定义见[接口契约](interface-contract.md)。

## POST /api/chat

```json
{"sessionId": null, "question": "公司员工有哪些福利？"}
```

`question` 必填，首尾空白去除后长度为 1 至 500。`sessionId` 可空，非空时为 `ses_` 加 12 位小写十六进制。留空时后端生成新会话 ID，后续请求可复用。每次请求独立检索、生成和核验。该入口固定为 `REAL`，仅接受这两个字段。

成功返回 HTTP 200，包含 `requestId`、`sessionId`、`mode`、`scenarioId`、`question`、`evidence`、`verification`、`guard`、`timing` 和 `createdAt`。前端正文取 `guard.displayAnswer`，结合 `guard.answerVisible` 和 `guard.message` 展示。返回中的 `startIndex` 和 `endIndex` 使用 Java 字符串的 UTF-16 下标，区间为左闭右开。

| 输出动作 | 供前端联调的响应 |
| --- | --- |
| ANNOTATED | [annotated.json](examples/annotated.json) |
| BLOCKED | [blocked.json](examples/blocked.json) |
| NOT_VERIFIED | [not-verified.json](examples/not-verified.json) |
| NO_CLAIMS | [no-claims.json](examples/no-claims.json) |
| NO_EVIDENCE | [no-evidence.json](examples/no-evidence.json) |

样例由后端在 Stub 集成测试中生成。`NOT_VERIFIED` 和 `NO_CLAIMS` 样例由测试提供指定核验状态。

输入校验失败返回 400 `INVALID_REQUEST`，检索失败返回 500 `RETRIEVAL_FAILED`，生成失败返回 502 `LLM_GENERATION_FAILED`。错误体字段为 `requestId`、`errorCode`、`message` 和 `timestamp`。核验失败返回 200，`verification.verificationState=FAILED`。当 `guard.answerVisible=false` 时，回答正文、结论的回答原文和定位下标均被遮蔽。

## GET /api/guard/config

返回默认配置及注册的全部策略。`availableStrategies` 由服务端动态生成。

```json
{
  "strategy": "annotate",
  "blockThreshold": 0.5,
  "strictForToolCall": true,
  "availableStrategies": ["annotate", "block", "strict"]
}
```

`block` 在证据不足占比严格大于阈值时拦截，核验失败时暂停展示。`strict` 是可修改性实验策略，存在证据不足结论时拦截。
