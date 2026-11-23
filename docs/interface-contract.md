# AI Guardian 接口契约附录 v1.4

> 适用项目：AI Guardian（AI 幻觉防火墙）V1.2
> 契约负责人：成员 1
> 冻结日期：2026-11-13（组会确认后生效）
> 适用范围：全组 5 人，所有代码、前端调用、测试用例、模拟场景文件

---

## 0. 契约的地位与使用规则

### 0.1 这份文件是什么

本文件是《AI Guardian 项目实施方案 V1.2》的技术补充，规定所有模块之间的**交接方式**：类名、字段名、枚举值、JSON 结构、REST API、数据库表、配置项、异常处理、模拟场景格式。

方案回答“做什么、为什么”，本文件回答“模块之间怎么对接”。两者冲突时，**以本文件为准**；本文件没有写到的，以方案为准。

### 0.2 必须遵守的规则

1. 本文件中出现的**类名、方法签名、字段名、枚举值、API 路径、JSON 键名、表名、列名、配置键**，任何人不得自行修改、重命名、增删。
2. 需要修改时：在群里提出“契约变更请求”，写明改什么、为什么、影响哪些成员；成员 1 与受影响成员同意后，由成员 1 修改本文件，版本号加 0.1，并在第 14 章变更记录登记。
3. 自己模块**内部**的类、私有方法、辅助工具可以自由设计，不受本文件约束。
4. 使用 AI 编程助手时，必须把本文件完整提供给 AI，并要求 AI 不得修改契约内容（模板见《00_使用说明》）。
5. 发现本文件有遗漏、矛盾或无法实现之处，**不要自行假设**，先在群里提出，由成员 1 当天答复。

### 0.3 代码所有权总表

| 路径 / 内容 | 所有者 | 说明 |
| --- | --- | --- |
| `domain/model/` 全部共享数据类与枚举 | 成员 1 | 第一天按本文件生成，之后只能按 0.2 流程修改 |
| `domain/` 下全部接口定义 | 成员 1 | 实现类由各负责人编写 |
| `common/` 异常、错误码、工具类 | 成员 1 | |
| `application/ChatOrchestrator` 及编排相关 | 成员 1 | |
| `domain/guard/` 全部 OutputGuard 实现 | 成员 1 | |
| `infrastructure/llm/RealLLMClient`、`RoutingLLMClient` | 成员 1 | |
| `infrastructure/lucene/` 全部 | 成员 2 | |
| `infrastructure/llm/MockLLMClient`、场景加载器 | 成员 2 | |
| `knowledge-base/` 全部文档 | 成员 2 | |
| `test-cases/` 场景 JSON 文件 | 成员 2 编写，成员 4 审核期望值 | |
| `domain/verification/` 全部实现类 | 成员 3 | |
| `audit/`、`infrastructure/persistence/` | 成员 4 | |
| `db/init.sql` | 成员 4 | |
| `test/` 下场景回归测试、期望比对器 | 成员 4 | 各模块单元测试由各自负责人编写 |
| `frontend/` 全部 | 成员 5 | |
| P1 工具网关：`domain/tool/`、`application/ToolCallService`、`controller/ToolController`、`infrastructure/persistence/JdbcBusinessRecordRepository`、`test-cases/SC06.json` | 成员 4（经组内决定由成员 5 代为实现） | 见 3.11、7.10、10.7 |
| P1 简化审批：`application/ApprovalService`、`application/ToolExecutor`、`controller/ApprovalController`、`infrastructure/persistence/JdbcApprovalRepository`、前端“审批中心” | 成员 5（经组内决定） | 见 3.12、7.11 |
| `docs/` | 各自写自己的部分，成员 1 统稿 | |

---

## 1. 技术基线

| 项目 | 规定 |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.3.x |
| 构建 | Maven，单模块项目 `backend/` |
| Lucene | 9.11.x，依赖 `lucene-core`、`lucene-analysis-common`、`lucene-analysis-smartcn`、`lucene-queryparser` |
| 数据库 | MySQL 8.0，库名 `ai_guardian`，字符集 `utf8mb4`，排序规则 `utf8mb4_0900_ai_ci` |
| 持久化 | Spring JDBC（`JdbcTemplate`）或 MyBatis 二选一，由成员 4 在 11-13 前确定；其他成员不直接访问数据库 |
| JSON | Jackson，字段命名 camelCase，`null` 字段**照常输出**（不省略） |
| 时间 | 统一 `Asia/Shanghai`；JSON 中时间为 ISO-8601 带偏移字符串，例如 `2026-11-20T14:03:21.512+08:00` |
| Lombok | 允许使用，数据类统一 `@Data @Builder @NoArgsConstructor @AllArgsConstructor` |
| 前端 | Vue 3 + Vite + Element Plus + axios + vue-router |
| 端口 | 后端 8080，前端开发服务器 5173（通过 Vite 代理把 `/api` 转发到 8080），MySQL 3306 |
| 大模型 | 一个兼容 OpenAI Chat Completions 格式的 API（如 DeepSeek、通义千问兼容模式），由成员 1 在 11-12 前确定并在群里公布 |

---

## 2. 包结构（后端）

```
backend/src/main/java/com/aiguardian/
├── AiGuardianApplication.java
├── controller/
│   ├── ChatController.java            # 成员 1
│   ├── GuardConfigController.java     # 成员 1
│   ├── KnowledgeController.java       # 成员 2
│   ├── DemoController.java            # 成员 2
│   ├── AuditController.java           # 成员 4
│   └── GlobalExceptionHandler.java    # 成员 1
├── application/
│   ├── ChatOrchestrator.java          # 成员 1
│   ├── ChatCommand.java               # 成员 1
│   └── PromptTemplates.java           # 成员 1（仅生成回答的 Prompt）
├── domain/
│   ├── model/                         # 成员 1：第 3 章全部数据类和枚举
│   ├── evidence/
│   │   ├── EvidenceRetriever.java     # 接口，成员 1 定义
│   │   └── EvidenceFormatter.java     # 成员 1
│   ├── verification/
│   │   ├── ClaimVerifier.java         # 接口，成员 1 定义
│   │   ├── CitationValidator.java     # 接口，成员 1 定义
│   │   ├── LLMBatchClaimVerifier.java # 成员 3
│   │   ├── PerClaimClaimVerifier.java # 成员 3（性能实验用）
│   │   ├── ExactQuoteCitationValidator.java # 成员 3
│   │   └── TextNormalizer.java        # 成员 3
│   └── guard/
│       ├── OutputGuard.java           # 接口，成员 1
│       ├── AnnotateOutputGuard.java   # 成员 1
│       ├── BlockOutputGuard.java      # 成员 1
│       └── OutputGuardRegistry.java   # 成员 1
├── infrastructure/
│   ├── llm/
│   │   ├── LLMClient.java             # 接口，成员 1
│   │   ├── RoutingLLMClient.java      # 成员 1，@Primary
│   │   ├── RealLLMClient.java         # 成员 1
│   │   ├── MockLLMClient.java         # 成员 2
│   │   └── LLMException.java          # 成员 1
│   ├── lucene/                        # 成员 2
│   └── persistence/                   # 成员 4
├── audit/
│   ├── AuditService.java              # 接口，成员 1 定义签名，成员 4 实现
│   └── ...                            # 成员 4
├── demo/
│   ├── ScenarioRepository.java        # 成员 2
│   └── ScenarioExpectationChecker.java # 成员 4
├── domain/tool/                       # P1 工具网关（3.11、7.10）
│   ├── ToolGateway.java               # 决策链：白名单 → 权限 → 参数 → 审批 → 依据核验
│   ├── ToolCatalog.java               # 三个工具的参数约束
│   └── BusinessRecordRepository.java  # 接口，模拟业务记录
├── application/ToolCallService.java   # 审计、依据查询、决策；REQUIRE_APPROVAL 时生成审批单
├── application/ToolExecutor.java      # 工具执行与记录快照（网关放行或审批通过后调用）
├── application/ApprovalService.java   # 审批：批准后重新检查再执行、禁止自批、过期
├── controller/ToolController.java     # POST /api/tools/execute
├── controller/ApprovalController.java # /api/approvals
├── common/
│   ├── ErrorCode.java                 # 成员 1
│   ├── BusinessException.java         # 成员 1
│   └── IdGenerator.java               # 成员 1
└── config/
    └── AiGuardianProperties.java      # 成员 1
```

---

## 3. 共享数据结构（`com.aiguardian.domain.model`）

### 3.1 枚举

```java
public enum RunMode {
    REAL,   // 调用真实大模型
    MOCK    // 由 MockLLMClient 按场景文件返回固定内容
}

public enum LLMPurpose {
    GENERATE_ANSWER,  // 第一次调用：根据证据生成回答
    VERIFY_CLAIMS     // 第二次调用：批量提取并核验结论
}

public enum ClaimStatus {
    SUPPORTED,     // 模型判断有证据，且引用经程序校验通过
    INSUFFICIENT   // 其他所有情况
}

public enum VerificationState {
    COMPLETED,     // 核验完成，结论数 1~8，且未截断
    PARTIAL,       // 核验完成，但回答中结论超过 8 条，只核验了前 8 条
    FAILED,        // 核验调用失败（超时、接口错误、JSON 无效），claims 为空
    NO_CLAIMS,     // 核验调用成功，但回答中没有事实性结论
    NO_EVIDENCE    // 检索为空，未调用模型生成回答，也未核验
}

public enum VerificationFailureReason {
    LLM_TIMEOUT,
    LLM_API_ERROR,
    INVALID_JSON,      // 两次尝试都无法解析为 JSON
    SCHEMA_INVALID     // 能解析但缺少 claims 数组等必需结构
}

public enum InsufficientReason {
    MODEL_UNSUPPORTED,      // 核验模型判断 supported=false
    MODEL_NO_CITATION,      // 模型判断 supported=true，但 evidenceId 或 quote 为空
    EVIDENCE_NOT_IN_CONTEXT,// evidenceId 不在本次检索结果中
    QUOTE_TOO_SHORT,        // 归一化后 quote 少于 min-quote-chars 个字符
    QUOTE_NOT_FOUND         // 归一化后 quote 不是对应片段内容的子串
}

public enum GuardAction {
    ANNOTATED,     // 正常展示回答，并逐条标注
    BLOCKED,       // 拦截整段回答
    NOT_VERIFIED,  // 核验失败（state=FAILED）时的处理结果
    NO_CLAIMS,     // 无可核验结论
    NO_EVIDENCE    // 知识库无相关资料
}

public enum AuditEventType {
    REQUEST_RECEIVED,
    RETRIEVAL_DONE,
    GENERATION_DONE,
    GENERATION_FAILED,
    VERIFICATION_DONE,
    CITATION_CHECKED,
    GUARD_APPLIED,
    REQUEST_COMPLETED,
    REQUEST_FAILED,
    TOOL_CALL_REQUESTED,   // P1：收到工具调用请求
    TOOL_CALL_DECIDED,     // P1：网关给出决策
    TOOL_CALL_EXECUTED,    // P1：工具实际执行（网关放行，或审批通过且重新检查通过）
    APPROVAL_REQUESTED,    // P1 v1.4：生成审批单
    APPROVAL_DECIDED       // P1 v1.4：审批人批准或驳回
}

public enum ApprovalStatus {   // P1 v1.4
    PENDING,   // 待审批
    APPROVED,  // 已批准，正在重新检查与执行（瞬时状态）
    EXECUTED,  // 已批准，重新检查通过并已执行
    FAILED,    // 已批准，但重新检查不通过或执行异常，未执行（原因见 failureReason）
    REJECTED,  // 已驳回，不执行
    EXPIRED    // 超过有效期未处理，作废
}

public enum ToolDecision {     // P1
    ALLOW,             // 通过全部检查并执行
    DENY,              // 拒绝，原因见 ToolDenyReason
    REQUIRE_APPROVAL   // 需要人工审批，网关不执行（审批流程不在本项目范围）
}

public enum ToolDenyReason {   // P1，按决策链顺序
    TOOL_NOT_ALLOWED,  // 工具不在白名单
    PERMISSION_DENIED, // 当前演示用户的角色无权使用该工具
    INVALID_ARGUMENTS, // 参数类型、取值或字段不符合约束
    RECORD_NOT_FOUND,  // 目标业务记录不存在或已删除
    BASIS_NOT_FOUND,   // basedOnRequestId 对应的问答记录不存在
    UNVERIFIED_BASIS   // 写操作依据的回答存在未经证据支持的结论（strict-for-tool-call）
}
```

枚举值在 JSON 中以**字符串原样**输出，例如 `"status": "SUPPORTED"`。

### 3.2 Evidence（证据片段）

| 字段 | Java 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| evidenceId | String | 否 | 格式 `{documentId}_chunk{两位序号}`，如 `doc001_chunk03` |
| documentId | String | 否 | 文档文件名前缀，如 `doc001` |
| documentTitle | String | 否 | 文档第一行 `# ` 后的标题 |
| section | String | 是 | 片段所在 `## ` 二级标题；文档无二级标题时为 null |
| content | String | 否 | 片段原文，**与索引中存储的文本逐字一致** |
| score | double | 否 | BM25 相关性分数，**不是可信度概率** |
| rank | int | 否 | 本次检索中的排名，从 1 开始 |

生成片段时，在统一换行为 `\n` 的文档正文中按起止下标直接截取连续区间，保留区间内原有的换行与空行。即使跨段落，`content` 也必须是该正文的连续子串。

```json
{
  "evidenceId": "doc001_chunk03",
  "documentId": "doc001",
  "documentTitle": "员工手册",
  "section": "年假制度",
  "content": "正式员工每年享有15天带薪年假，试用期员工不享有年假。",
  "score": 4.52,
  "rank": 1
}
```

### 3.3 ClaimVerification（单条结论核验结果）

| 字段 | Java 类型 | 可空 | 由谁填写 | 说明 |
| --- | --- | --- | --- | --- |
| claimId | String | 否 | ClaimVerifier | `c1` 到 `c8`，按模型输出顺序编号 |
| claim | String | 否 | ClaimVerifier | 规范化后的结论文本 |
| originalText | String | 否 | ClaimVerifier | 该结论在回答中对应的原文片段（模型输出） |
| startIndex | Integer | 是 | CitationValidator | originalText 在回答中的起始下标（Java `String.indexOf` 结果）；找不到为 null |
| endIndex | Integer | 是 | CitationValidator | startIndex + originalText.length()；找不到为 null |
| modelSupported | boolean | 否 | ClaimVerifier | 核验模型给出的 supported 原值 |
| status | ClaimStatus | 否 | ClaimVerifier 初判，CitationValidator 终判 | 见第 5.3 节规则 |
| evidenceId | String | 是 | ClaimVerifier | 模型给出的证据 ID |
| quote | String | 是 | ClaimVerifier | 模型给出的引用原文 |
| quoteExists | Boolean | 是 | CitationValidator | null = 未执行校验；true/false = 校验结果 |
| insufficientReason | InsufficientReason | 是 | ClaimVerifier / CitationValidator | status=SUPPORTED 时必须为 null |

```json
{
  "claimId": "c2",
  "claim": "员工每月有2000元交通补贴",
  "originalText": "每月还有2,000元交通补贴",
  "startIndex": 12,
  "endIndex": 26,
  "modelSupported": false,
  "status": "INSUFFICIENT",
  "evidenceId": null,
  "quote": null,
  "quoteExists": null,
  "insufficientReason": "MODEL_UNSUPPORTED"
}
```

### 3.4 VerificationReport（核验报告）

| 字段 | Java 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| requestId | String | 否 | |
| verificationState | VerificationState | 否 | |
| failureReason | VerificationFailureReason | 是 | 仅 state=FAILED 时非空 |
| answer | String | 是 | 模型生成的原始回答；state=NO_EVIDENCE 时为 null；**返回给前端且 guard.action=BLOCKED 时置为 null**（见 6.2） |
| totalClaims | int | 否 | claims 列表长度（0~8） |
| supportedClaims | int | 否 | status=SUPPORTED 的数量 |
| insufficientClaims | int | 否 | status=INSUFFICIENT 的数量 |
| unsupportedRatio | Double | 是 | insufficientClaims / totalClaims，保留 4 位小数；totalClaims=0 时为 null |
| truncated | boolean | 否 | 回答结论数超过 8 条时为 true（此时 state=PARTIAL） |
| claims | List&lt;ClaimVerification&gt; | 否 | 可为空列表，不能为 null |
| verifierName | String | 否 | 实现类标识：`batch` 或 `per-claim`，NO_EVIDENCE 时为 `none` |
| llmCalls | int | 否 | 核验阶段实际调用模型的次数（含重试） |

计数字段由 `VerificationReport#recalculate()` 统一计算（成员 1 在数据类中实现），任何模块修改 claims 后必须调用它，**禁止手动赋值计数字段**。

```java
public void recalculate() {
    this.totalClaims = claims.size();
    this.supportedClaims = (int) claims.stream()
            .filter(c -> c.getStatus() == ClaimStatus.SUPPORTED)
            .count();
    this.insufficientClaims = totalClaims - supportedClaims;
    if (totalClaims == 0) {
        this.unsupportedRatio = null;
    } else {
        double ratio = (double) insufficientClaims / totalClaims;
        this.unsupportedRatio = Math.round(ratio * 10000) / 10000.0;
    }
}
```

### 3.5 GuardPolicy 与 GuardResult（输出防护）

GuardPolicy（本次请求使用的策略）：

| 字段 | Java 类型 | 说明 |
| --- | --- | --- |
| strategy | String | `annotate` 或 `block`（小写） |
| blockThreshold | double | 0~1，默认 0.5 |

GuardResult：

| 字段 | Java 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| strategy | String | 否 | 实际使用的策略名 |
| blockThreshold | double | 否 | 实际使用的阈值 |
| action | GuardAction | 否 | 处理结果 |
| answerVisible | boolean | 否 | 前端是否展示回答正文 |
| displayAnswer | String | 是 | 前端应展示的回答正文；answerVisible=false 时为 null |
| message | String | 是 | 前端应展示的提示语（固定文案见 6.3） |
| unsupportedRatio | Double | 是 | 复制自核验报告 |

### 3.6 Timing（耗时）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| retrievalMs | long | 检索耗时 |
| generationMs | Long | 生成耗时；未调用时为 null |
| verificationMs | Long | 核验（含引用校验）耗时；未执行时为 null |
| totalMs | long | 从收到请求到返回响应的总耗时 |

### 3.7 ChatResponse（问答接口统一响应）

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| requestId | String | 否 | 格式 `req_` + 12 位小写十六进制 |
| sessionId | String | 否 | 格式 `ses_` + 12 位小写十六进制 |
| mode | RunMode | 否 | |
| scenarioId | String | 是 | 仅模拟或演示请求非空 |
| question | String | 否 | |
| evidence | List&lt;Evidence&gt; | 否 | 本次检索结果，可为空列表 |
| verification | VerificationReport | 否 | |
| guard | GuardResult | 否 | |
| timing | Timing | 否 | |
| createdAt | String | 否 | ISO-8601 |

完整示例（场景 SC02，annotate 策略）：

```json
{
  "requestId": "req_3f9a1c0b7d2e",
  "sessionId": "ses_8a1b2c3d4e5f",
  "mode": "MOCK",
  "scenarioId": "SC02",
  "question": "公司员工有哪些福利？",
  "evidence": [
    {
      "evidenceId": "doc005_chunk01",
      "documentId": "doc005",
      "documentTitle": "员工福利规定",
      "section": "总则与餐费补贴",
      "content": "为提升员工归属感，公司根据经营情况为员工提供多项福利。本规定适用于公司全体正式员工，试用期员工自转正之日起享受本规定中的福利项目。\n\n福利项目的具体标准和发放方式由人力资源部负责解释。公司可根据经营情况调整福利项目，调整前会提前告知员工。\n\n公司为员工提供餐费补贴，标准为每月300元，随工资发放。餐费补贴按实际出勤折算，当月缺勤超过5天的部分相应扣减。\n\n餐费补贴直接计入工资，员工无需另行申请。员工对餐费补贴金额有疑问的，可向人力资源部查询当月出勤记录。",
      "score": 2.2112,
      "rank": 1
    },
    {
      "evidenceId": "doc005_chunk03",
      "documentId": "doc005",
      "documentTitle": "员工福利规定",
      "section": "其他福利",
      "content": "员工生日当月发放200元生日礼金，随当月工资发放。各部门每季度组织一次团建活动，费用在部门预算内列支。\n\n公司为员工提供必要的办公设备和劳动保护用品。员工因公出差、加班产生的合理费用，按《报销制度》执行。\n\n团建活动应安排在非工作时间或经员工同意的工作时间，不得强制员工自费参加。各部门组织团建应提前报备，活动方案和费用预算需经部门经理审批。公司鼓励员工合理使用福利，福利项目不与考勤处罚相互抵扣。员工对福利发放有异议的，可在发放当月向人力资源部提出复核，人力资源部应在核实后答复。",
      "score": 2.1641,
      "rank": 2
    },
    {
      "evidenceId": "doc005_chunk02",
      "documentId": "doc005",
      "documentTitle": "员工福利规定",
      "section": "健康体检与节日福利",
      "content": "公司每年为员工安排一次免费体检，体检项目由公司统一确定。体检时间一般安排在工作日，员工参加体检视为正常出勤。体检结果仅用于员工健康管理，不作为岗位调整的依据。\n\n员工因个人原因未参加统一体检的，可在当年内申请补检一次。新入职员工在转正后参加次年的统一体检。\n\n春节、中秋发放节日礼品，具体形式由公司统一安排。节日礼品在节前发放，员工应在规定时间内领取。\n\n公司在其他传统节日可根据经营情况安排慰问活动，具体以人力资源部通知为准。",
      "score": 1.4327,
      "rank": 3
    }
  ],
  "verification": {
    "requestId": "req_3f9a1c0b7d2e",
    "verificationState": "COMPLETED",
    "failureReason": null,
    "answer": "公司每月提供 300 元餐费补贴，每月还提供 2,000 元交通补贴。",
    "totalClaims": 2,
    "supportedClaims": 1,
    "insufficientClaims": 1,
    "unsupportedRatio": 0.5,
    "truncated": false,
    "verifierName": "batch",
    "llmCalls": 1,
    "claims": [
      {
        "claimId": "c1",
        "claim": "公司每月提供 300 元餐费补贴",
        "originalText": "公司每月提供 300 元餐费补贴",
        "startIndex": 0,
        "endIndex": 16,
        "modelSupported": true,
        "status": "SUPPORTED",
        "evidenceId": "doc005_chunk01",
        "quote": "公司为员工提供餐费补贴，标准为每月300元，随工资发放。",
        "quoteExists": true,
        "insufficientReason": null
      },
      {
        "claimId": "c2",
        "claim": "公司每月还提供 2,000 元交通补贴",
        "originalText": "每月还提供 2,000 元交通补贴",
        "startIndex": 17,
        "endIndex": 34,
        "modelSupported": false,
        "status": "INSUFFICIENT",
        "evidenceId": null,
        "quote": null,
        "quoteExists": null,
        "insufficientReason": "MODEL_UNSUPPORTED"
      }
    ]
  },
  "guard": {
    "strategy": "annotate",
    "blockThreshold": 0.5,
    "action": "ANNOTATED",
    "answerVisible": true,
    "displayAnswer": "公司每月提供 300 元餐费补贴，每月还提供 2,000 元交通补贴。",
    "message": "部分结论未在知识库中找到充分证据，已用黄色标出。",
    "unsupportedRatio": 0.5
  },
  "timing": {
    "retrievalMs": 18,
    "generationMs": 3,
    "verificationMs": 5,
    "totalMs": 41
  },
  "createdAt": "2026-11-20T14:03:21.512+08:00"
}
```

### 3.8 错误响应（所有非 2xx 响应）

```json
{
  "requestId": "req_3f9a1c0b7d2e",
  "errorCode": "LLM_GENERATION_FAILED",
  "message": "大模型生成回答失败，请稍后重试。",
  "timestamp": "2026-11-20T14:03:21.512+08:00"
}
```

请求在生成 requestId 之前就失败（如参数校验）时，requestId 为 null。

### 3.9 AuditEvent（审计事件）

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| requestId | String | 否 | |
| eventType | AuditEventType | 否 | |
| success | boolean | 否 | |
| durationMs | Long | 是 | 该步骤耗时 |
| errorCode | String | 是 | 失败时填写 ErrorCode 或 VerificationFailureReason 的名称 |
| detail | Map&lt;String, Object&gt; | 是 | 各事件的详情，键名见 8.2 |
| createdAt | OffsetDateTime | 否 | |

### 3.10 RequestRecord（一次请求的持久化记录）

由 ChatOrchestrator 在请求结束时（成功或失败）构造，交给 `AuditService.saveRequestRecord`。

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| requestId | String | 否 | |
| sessionId | String | 否 | |
| mode | RunMode | 否 | |
| scenarioId | String | 是 | |
| question | String | 否 | |
| rawAnswer | String | 是 | **未经任何遮蔽的**原始回答，供管理员审计 |
| evidence | List&lt;Evidence&gt; | 否 | |
| report | VerificationReport | 是 | 生成失败时为 null |
| guard | GuardResult | 是 | 生成失败时为 null |
| timing | Timing | 否 | |
| errorCode | String | 是 | 请求失败时的错误码 |
| createdAt | OffsetDateTime | 否 | |

---

### 3.11 工具调用（P1，v1.3 新增）

ToolCallRequest（`POST /api/tools/execute` 请求体，也用于 SC06 场景中“模型提出的工具调用”）：

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| toolName | String | 否 | 工具名，见 7.10 工具清单 |
| arguments | Map&lt;String, Object&gt; | 否 | 工具参数，可为空对象 |
| basedOnRequestId | String | 是 | 本次操作依据的问答 requestId；非空时必须匹配 `^req_[0-9a-f]{12}$` |

ToolCallResult（网关决策结果）：

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| toolCallId | String | 否 | `tool_` + 12 位小写十六进制 |
| basedOnRequestId | String | 是 | 原样返回 |
| user | String | 否 | 演示用户：`user` 或 `manager` |
| toolName | String | 否 | 原样返回 |
| arguments | Map&lt;String, Object&gt; | 否 | 原样返回 |
| decision | ToolDecision | 否 | |
| denyReason | ToolDenyReason | 是 | 仅 decision=DENY 时非空 |
| message | String | 否 | 固定文案，见 7.10 |
| executed | boolean | 否 | 是否实际执行（仅 ALLOW 时可能为 true） |
| result | Map&lt;String, Object&gt; | 是 | 执行结果，未执行时为 null；键见 7.10 |
| record | BusinessRecord | 是 | 记录类工具在**决策之后**读取的目标记录当前状态，用于展示“数据是否被修改”；搜索类工具或记录不存在时为 null |
| approvalId | String | 是 | v1.4：decision=REQUIRE_APPROVAL 时为新生成的审批单号，其他决策为 null |
| createdAt | String | 否 | ISO-8601 |

BusinessRecord（模拟业务记录，表 `mock_business_record`）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| recordId | long | 记录编号 |
| title | String | 标题 |
| owner | String | 负责人（演示用户名） |
| status | String | `OPEN` / `CLOSED` / `ARCHIVED` |
| deleted | boolean | 是否已删除（软删除） |
| updatedAt | String | ISO-8601 |

### 3.12 ApprovalRequest（审批单，P1 v1.4）

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| approvalId | String | 否 | `apr_` + 12 位小写十六进制 |
| toolCallId | String | 否 | 生成审批单的那次工具调用 |
| toolName | String | 否 | |
| arguments | Map&lt;String, Object&gt; | 否 | |
| requester | String | 否 | 申请人（演示用户） |
| basedOnRequestId | String | 是 | |
| status | ApprovalStatus | 否 | |
| approver | String | 是 | 批准或驳回的人 |
| comment | String | 是 | 审批意见，最多 200 字 |
| failureReason | String | 是 | status=FAILED 时为 ToolDenyReason 名称或 `EXECUTION_FAILED` |
| result | Map&lt;String, Object&gt; | 是 | 仅批准接口的响应中、且已执行时非空（键同 7.10）；列表与查询中为 null |
| record | BusinessRecord | 是 | 仅批准、驳回接口的响应中非空：处理后目标记录的当前状态 |
| createdAt | String | 否 | ISO-8601 |
| expiresAt | String | 否 | createdAt + `tool-gateway.approval-ttl-minutes` |
| decidedAt | String | 是 | 批准、驳回或过期的时间 |

---

## 4. 核心接口（Java）

所有接口由成员 1 在第一天提交到 `dev` 分支，签名不得私自修改。

### 4.1 LLMClient

```java
package com.aiguardian.infrastructure.llm;

public interface LLMClient {

    /**
     * 调用大模型，返回模型输出的纯文本 content。
     * 成功：返回非空字符串。
     * 失败：抛出 LLMException，绝不返回 null。
     */
    String generate(LLMRequest request);
}
```

LLMRequest（放在 `domain/model`）：

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| requestId | String | 否 | |
| mode | RunMode | 否 | RoutingLLMClient 据此选择真实或模拟实现 |
| scenarioId | String | 是 | mode=MOCK 时必须非空 |
| purpose | LLMPurpose | 否 | MockLLMClient 据此返回场景中对应的响应 |
| attempt | int | 否 | 第几次尝试，从 1 开始（重试时递增） |
| systemPrompt | String | 否 | |
| userPrompt | String | 否 | |
| temperature | double | 否 | 生成回答用 0.3，核验用 0.0 |
| jsonMode | boolean | 否 | 核验调用为 true；真实实现据此设置 `response_format: {"type":"json_object"}` |
| timeoutMs | long | 否 | 生成用配置 `generate-timeout-ms`，核验用 `verify-timeout-ms` |

LLMException（运行时异常）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| type | `LLMException.Type` 枚举 | `TIMEOUT`、`API_ERROR`、`EMPTY_RESPONSE` |
| message | String | 不得包含 API Key |

实现与路由：

- `RoutingLLMClient`（成员 1）标注 `@Primary`，是其他模块唯一应当注入的 LLMClient。它按 `request.mode` 委托给 `RealLLMClient` 或 `MockLLMClient`。
- `RealLLMClient`（成员 1）：调用 OpenAI 兼容的 `/chat/completions`，取 `choices[0].message.content`。HTTP 非 2xx 抛 `API_ERROR`，超时抛 `TIMEOUT`，content 为空抛 `EMPTY_RESPONSE`。
- `MockLLMClient`（成员 2）：按第 10 章场景文件返回内容，不访问网络。

### 4.2 EvidenceRetriever

```java
package com.aiguardian.domain.evidence;

public interface EvidenceRetriever {

    /**
     * 检索与 query 相关的片段，按 score 降序，最多 topK 条。
     * 低于配置 min-score 的结果丢弃。
     * 没有结果时返回空列表，绝不返回 null。
     * 索引不可用等内部错误抛出 BusinessException(ErrorCode.RETRIEVAL_FAILED)。
     */
    List<Evidence> retrieve(String query, int topK);

    /**
     * 按 ID 获取片段，供前端查看原文等场景使用。找不到返回 Optional.empty()。
     */
    Optional<Evidence> findById(String evidenceId);
}
```

> 契约修订说明：方案 4.5 中 `retrieve(String query)` 增加了 `topK` 参数和 `findById` 方法，以支持 Top 3 / Top 5 切换和原文查看。

### 4.3 ClaimVerifier

```java
package com.aiguardian.domain.verification;

public interface ClaimVerifier {

    /**
     * 提取并核验 answer 中的事实性结论。
     * 绝不抛出异常：模型超时、接口错误、JSON 无效都返回 state=FAILED 的报告。
     * 返回的报告中 status 只是“初判”，quoteExists 全部为 null，
     * 由随后的 CitationValidator 给出最终状态。
     */
    VerificationReport verify(VerificationInput input);

    /** 返回 "batch" 或 "per-claim"，写入报告的 verifierName。 */
    String name();
}
```

VerificationInput：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| requestId | String | |
| mode | RunMode | 透传给 LLMRequest |
| scenarioId | String | 透传给 LLMRequest |
| question | String | 用户问题 |
| answer | String | 待核验的回答，非空 |
| evidence | List&lt;Evidence&gt; | 本次检索结果，非空 |

> 契约修订说明：方案 4.5 中 `verify(String answer, List<Evidence> evidence)` 改为传入 VerificationInput，因为核验需要 requestId、运行模式和场景编号来构造模型请求。

### 4.4 CitationValidator

```java
package com.aiguardian.domain.verification;

public interface CitationValidator {

    /**
     * 对 state 为 COMPLETED 或 PARTIAL 的报告逐条执行确定性校验，
     * 就地修改 claims 的 status、quoteExists、insufficientReason、startIndex、endIndex，
     * 最后调用 report.recalculate() 并返回同一个对象。
     * 对其他 state 的报告原样返回，不做任何修改。
     * 不调用大模型，不抛出异常。
     */
    VerificationReport validate(VerificationReport report, List<Evidence> evidence);
}
```

### 4.5 OutputGuard

```java
package com.aiguardian.domain.guard;

public interface OutputGuard {

    /** 策略名，小写，如 "annotate"、"block"，作为配置和请求中的取值。 */
    String name();

    /** 根据核验报告和策略参数决定展示方式。不修改 report，不抛出异常。 */
    GuardResult evaluate(VerificationReport report, GuardPolicy policy);
}
```

`OutputGuardRegistry`（成员 1）自动收集所有 OutputGuard Bean，按 `name()` 查找；找不到时抛 `BusinessException(ErrorCode.INVALID_REQUEST)`。**新增策略只需新增一个实现类并注册为 Bean，不得修改 ChatOrchestrator 和核验模块**，这是可修改性实验的验证点。

> 契约修订说明：方案 4.5 中 `evaluate(VerificationReport report)` 增加了 GuardPolicy 参数，以支持演示时按次覆盖策略和阈值。

### 4.6 AuditService

```java
package com.aiguardian.audit;

public interface AuditService {

    /** 记录一条审计事件。内部异常必须自行捕获并写日志，绝不向调用方抛出。 */
    void record(AuditEvent event);

    /**
     * 请求结束时保存完整记录：会话、两条消息、核验汇总、逐条结论。
     * 同样绝不向调用方抛出异常。
     */
    void saveRequestRecord(RequestRecord record);
}
```

### 4.7 ChatOrchestrator（应用层入口）

```java
package com.aiguardian.application;

public interface ChatOrchestrator {
    ChatResponse handle(ChatCommand command);
}
```

ChatCommand：

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| sessionId | String | 是 | 为空时由编排器新建 |
| question | String | 否 | 已经过长度校验 |
| mode | RunMode | 否 | |
| scenarioId | String | 是 | mode=MOCK 时非空 |
| guardStrategyOverride | String | 是 | 仅演示接口传入 |
| blockThresholdOverride | Double | 是 | 仅演示接口传入 |
| verifierOverride | String | 是 | `batch`、`per-claim`、`none`，仅性能实验使用 |

---

## 5. 主链路处理规则

### 5.1 编排步骤（ChatOrchestrator，成员 1）

| 步骤 | 动作 | 审计事件 | 失败处理 |
| --- | --- | --- | --- |
| 1 | 生成 requestId；sessionId 为空时生成新的 | REQUEST_RECEIVED | — |
| 2 | `retriever.retrieve(question, topK)` | RETRIEVAL_DONE | 抛 RETRIEVAL_FAILED → 记录 REQUEST_FAILED，保存 RequestRecord，返回 500 |
| 3 | 检索结果为空：构造 state=NO_EVIDENCE 的报告，跳到步骤 7 | — | — |
| 4 | 用 `PromptTemplates` 构造生成请求，调用 LLMClient（purpose=GENERATE_ANSWER，不重试） | GENERATION_DONE / GENERATION_FAILED | 失败 → REQUEST_FAILED，保存 RequestRecord，返回 502 LLM_GENERATION_FAILED |
| 5 | 按 verifierOverride 或配置选择 ClaimVerifier，调用 verify | VERIFICATION_DONE | 不会抛异常；FAILED 状态照常进入步骤 7 |
| 6 | state 为 COMPLETED 或 PARTIAL 时调用 `citationValidator.validate` | CITATION_CHECKED | 不会抛异常 |
| 7 | 确定 GuardPolicy（覆盖值优先，否则用配置），调用对应 OutputGuard | GUARD_APPLIED | — |
| 8 | 组装 ChatResponse；若 action=BLOCKED，按 6.2 遮蔽回答 | — | — |
| 9 | 保存 RequestRecord（rawAnswer 用未遮蔽的回答） | REQUEST_COMPLETED | 审计失败不影响响应 |

`verifierOverride = none` 时跳过步骤 5、6，构造 state=NO_CLAIMS、verifierName=`none` 的空报告，**仅用于性能实验方案 A**，正常问答不允许出现。

### 5.2 会话与上下文

- V1 中每次提问**独立检索、独立生成、独立核验**，生成回答时**不带入历史消息**。原因：历史消息中的内容没有经过本次检索证据的支持，会让核验结果无法解释。
- 会话只用于把同一窗口的消息归组展示和审计查询。
- 前端在内存中保存当前会话消息，刷新页面后会话消失，这是允许的。

### 5.3 结论状态判定规则

ClaimVerifier 初判（成员 3）：

| 模型输出 | 初判 status | insufficientReason |
| --- | --- | --- |
| supported=false | INSUFFICIENT | MODEL_UNSUPPORTED |
| supported=true，evidenceId 或 quote 为空或空白 | INSUFFICIENT | MODEL_NO_CITATION |
| supported=true，evidenceId 和 quote 都非空 | SUPPORTED（待校验） | null |

CitationValidator 终判（成员 3），**只对初判为 SUPPORTED 的结论**执行，按顺序检查，第一个失败即停止：

| 顺序 | 检查 | 失败时 |
| --- | --- | --- |
| 1 | evidenceId 在本次 evidence 列表中存在 | status=INSUFFICIENT，reason=EVIDENCE_NOT_IN_CONTEXT，quoteExists=false |
| 2 | 归一化后 quote 长度 ≥ `min-quote-chars`（默认 6） | status=INSUFFICIENT，reason=QUOTE_TOO_SHORT，quoteExists=false |
| 3 | 归一化后 quote 是归一化后片段 content 的子串 | status=INSUFFICIENT，reason=QUOTE_NOT_FOUND，quoteExists=false |
| 全部通过 | | status 保持 SUPPORTED，quoteExists=true |

所有结论（无论状态）都计算 startIndex / endIndex：`answer.indexOf(originalText)`，结果为 -1 时两者都为 null。**定位使用原文精确匹配，不做归一化**，因为前端要按下标高亮。

### 5.4 文本归一化规则（TextNormalizer，成员 3）

用于引用比对，quote 和片段 content 必须使用完全相同的归一化：

1. `java.text.Normalizer.normalize(s, Normalizer.Form.NFKC)`（全角字母、数字、英文标点转半角）
2. 删除所有空白字符（正则 `\s+`，以及 `\u3000`）
3. 替换中文标点：`。`→`.`，`，`→`,`，`；`→`;`，`：`→`:`，`！`→`!`，`？`→`?`，`、`→`,`
4. 替换引号：`“` `”` `「` `」` → `"`，`‘` `’` → `'`
5. 删除数字中的千分位逗号：正则 `(?<=\d),(?=\d{3})` 替换为空
6. 英文字母转小写

### 5.5 截断规则

- 核验模型被要求最多输出 8 条结论，并在结论超过 8 条时输出 `"truncated": true`。
- 若模型实际输出超过 8 条，ClaimVerifier 只保留前 8 条，并设 truncated=true。
- truncated=true 时 state=PARTIAL；否则结论数 ≥1 时 state=COMPLETED，结论数为 0 时 state=NO_CLAIMS。

### 5.6 重试与超时

| 调用 | 超时 | 重试 |
| --- | --- | --- |
| 生成回答 | `generate-timeout-ms`，默认 30000 | 不重试 |
| 批量核验 | `verify-timeout-ms`，默认 45000 | 共最多 `verify-max-attempts` 次（默认 2）；仅在 INVALID_JSON、SCHEMA_INVALID、EMPTY_RESPONSE 时重试，TIMEOUT 和 API_ERROR 不重试 |

---

## 6. 输出防护规则（OutputGuard，成员 1）

### 6.1 判定表

| verificationState | annotate 策略 | block 策略 |
| --- | --- | --- |
| NO_EVIDENCE | action=NO_EVIDENCE，answerVisible=true，displayAnswer=固定文案 N1 | 同左 |
| FAILED | action=NOT_VERIFIED，answerVisible=true，displayAnswer=原回答，message=M3 | action=NOT_VERIFIED，answerVisible=false，displayAnswer=null，message=M4 |
| NO_CLAIMS | action=NO_CLAIMS，answerVisible=true，displayAnswer=原回答，message=M5 | 同左 |
| COMPLETED / PARTIAL，且 ratio ≤ threshold | action=ANNOTATED，answerVisible=true，displayAnswer=原回答，message 见 6.3 | 同左 |
| COMPLETED / PARTIAL，且 ratio > threshold（**严格大于**） | 同上一行（annotate 从不拦截） | action=BLOCKED，answerVisible=false，displayAnswer=null，message=M6 |

说明：block 策略在核验失败时也不展示回答（失败即关闭，Fail-Closed）；annotate 策略在核验失败时展示回答，但必须带醒目警告。

### 6.2 BLOCKED 时的响应遮蔽

guard.action=BLOCKED 或 guard.answerVisible=false 时，返回给前端的 ChatResponse 中：

- `verification.answer` 置为 null；
- 每条 claim 的 `originalText` 置为 null，`startIndex`、`endIndex` 置为 null；
- `claim`、`status`、`evidenceId`、`quote`、`insufficientReason` 保留，用于向用户解释为什么被拦截。

数据库中的 RequestRecord.rawAnswer 和 claim_record 保存**未遮蔽**的完整内容，供审计页面查看。

### 6.3 固定提示文案

| 编号 | 使用条件 | 文案 |
| --- | --- | --- |
| N1 | NO_EVIDENCE 的 displayAnswer | 知识库中没有找到与该问题相关的资料，系统未生成回答。 |
| M1 | ANNOTATED，insufficientClaims=0，未截断 | 回答中的全部结论均有知识库证据支持。 |
| M2 | ANNOTATED，insufficientClaims>0，未截断 | 部分结论未在知识库中找到充分证据，已用黄色标出。 |
| M2P | ANNOTATED，truncated=true | 回答包含的结论较多，仅核验了前 8 条，其余内容未经核验。 |
| M3 | NOT_VERIFIED，annotate | 核验未完成，以下回答未经证据核验，请谨慎参考。 |
| M4 | NOT_VERIFIED，block | 核验未完成，为避免输出未经核验的内容，系统已暂停展示回答。 |
| M5 | NO_CLAIMS | 回答中没有需要核验的事实性结论。 |
| M6 | BLOCKED | 当前回答中有较多结论缺乏充分证据，系统已限制输出，请核对原始资料。 |

M2 与 M2P 同时满足时使用 M2P。

---

## 7. REST API

所有接口前缀 `/api`，请求和响应均为 `application/json; charset=UTF-8`。

### 7.1 POST /api/chat（成员 1）

请求：

```json
{
  "sessionId": null,
  "question": "公司正式员工一年有多少天年假？"
}
```

| 字段 | 规则 |
| --- | --- |
| sessionId | 可空；非空时必须匹配 `^ses_[0-9a-f]{12}$` |
| question | 必填；去除首尾空白后长度 1~500 |

本接口固定使用 `mode=REAL`，不接受模拟参数。响应：200 + ChatResponse（3.7）。

### 7.2 GET /api/guard/config（成员 1）

```json
{
  "strategy": "annotate",
  "blockThreshold": 0.5,
  "strictForToolCall": true,
  "availableStrategies": ["annotate", "block"]
}
```

availableStrategies 由 OutputGuardRegistry 动态生成。

### 7.3 GET /api/knowledge/documents（成员 2）

```json
[
  {
    "documentId": "doc001",
    "title": "员工手册",
    "fileName": "doc001_员工手册.md",
    "chunkCount": 9,
    "charCount": 2310
  }
]
```

按 documentId 升序。

### 7.4 GET /api/knowledge/documents/{documentId}（成员 2）

```json
{
  "documentId": "doc001",
  "title": "员工手册",
  "content": "# 员工手册\n\n## 年假制度\n\n正式员工每年享有15天带薪年假……",
  "chunks": [
    {
      "evidenceId": "doc001_chunk01",
      "section": "总则",
      "content": "……"
    }
  ]
}
```

不存在返回 404 DOCUMENT_NOT_FOUND。

### 7.5 GET /api/knowledge/evidence/{evidenceId}（成员 2）

返回单个 Evidence（score 为 0，rank 为 0）。不存在返回 404 EVIDENCE_NOT_FOUND。

### 7.6 GET /api/demo/scenarios（成员 2）

```json
[
  {
    "scenarioId": "SC02",
    "name": "部分结论证据不足",
    "description": "知识库有餐费补贴规定，模拟回答额外声称有交通补贴。",
    "question": "公司员工有哪些福利？",
    "priority": "P0",
    "supportsReal": true,
    "recommendedStrategies": ["annotate"]
  }
]
```

按 scenarioId 升序。priority 为 P1 的场景前端显示“扩展”标签。

### 7.7 POST /api/demo/run（成员 2，调用成员 1 的编排器和成员 4 的比对器）

请求：

```json
{
  "scenarioId": "SC03",
  "mode": "MOCK",
  "guardStrategy": "block",
  "blockThreshold": 0.5,
  "verifier": null
}
```

| 字段 | 规则 |
| --- | --- |
| scenarioId | 必填，必须存在 |
| mode | 必填，`MOCK` 或 `REAL`；REAL 时使用场景中的 question 调用真实模型 |
| guardStrategy | 可空；非空时必须在 availableStrategies 中 |
| blockThreshold | 可空；非空时 0~1 |
| verifier | 可空；`batch`、`per-claim`、`none`；**仅 mode=REAL 时允许非空**，用于性能实验 |

响应：

```json
{
  "response": { "...": "ChatResponse 完整结构" },
  "check": {
    "checked": true,
    "passed": true,
    "mismatches": []
  }
}
```

- mode=MOCK 时，由 `ScenarioExpectationChecker`（成员 4）按场景文件中的 expected 比对，`checked=true`。
- mode=REAL 时不比对，`checked=false`，`passed=null`，`mismatches=[]`。
- 场景期望 httpStatus 为 502 的（如 SC09），接口本身仍返回 200，response 为 null，check 中比对 errorCode。
- **v1.3 新增 `toolCall` 字段（ToolCallResult，可空）**：仅当场景定义了 `toolCall`（10.7，目前只有 SC06）、mode=MOCK 且 response 非空时，DemoController 在编排结束后把场景中“模型提出的工具调用”交给工具网关，`basedOnRequestId` 取本次 response.requestId，依据核验直接使用本次 response.verification。其他情况为 null。
- mode=MOCK 且场景定义了 `expected.toolCall` 时，工具决策的比对结果并入 `check.mismatches`，格式如 `toolCall.decision 期望 DENY，实际 ALLOW`。

### 7.8 GET /api/audit（成员 4）

查询参数：

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| page | int | 1 | 从 1 开始 |
| size | int | 20 | 最大 100 |
| requestId | String | — | 精确匹配 |
| state | String | — | VerificationState 名称 |
| action | String | — | GuardAction 名称 |
| mode | String | — | REAL / MOCK |
| from | String | — | ISO-8601，含 |
| to | String | — | ISO-8601，不含 |

响应：

```json
{
  "items": [
    {
      "requestId": "req_3f9a1c0b7d2e",
      "createdAt": "2026-11-20T14:03:21.512+08:00",
      "mode": "MOCK",
      "scenarioId": "SC02",
      "questionPreview": "公司员工有哪些福利？",
      "verificationState": "COMPLETED",
      "guardStrategy": "annotate",
      "guardAction": "ANNOTATED",
      "totalClaims": 2,
      "supportedClaims": 1,
      "insufficientClaims": 1,
      "totalMs": 41,
      "errorCode": null
    }
  ],
  "total": 37,
  "page": 1,
  "size": 20
}
```

按 createdAt 降序。questionPreview 为问题前 50 个字符。

### 7.9 GET /api/audit/{requestId}（成员 4）

```json
{
  "requestId": "req_3f9a1c0b7d2e",
  "sessionId": "ses_8a1b2c3d4e5f",
  "createdAt": "2026-11-20T14:03:21.512+08:00",
  "mode": "MOCK",
  "scenarioId": "SC02",
  "question": "公司员工有哪些福利？",
  "rawAnswer": "公司每月提供 300 元餐费补贴，每月还提供 2,000 元交通补贴。",
  "evidence": [{ "...": "同 3.7 的三个 Evidence 对象" }],
  "verification": { "...": "VerificationReport，未遮蔽" },
  "guard": { "...": "GuardResult" },
  "timing": { "...": "Timing" },
  "errorCode": null,
  "events": [
    {
      "eventType": "RETRIEVAL_DONE",
      "success": true,
      "durationMs": 18,
      "errorCode": null,
      "detail": { "topK": 3, "hitCount": 3, "evidenceIds": ["doc005_chunk01", "doc005_chunk03", "doc005_chunk02"], "topScore": 2.2112 },
      "createdAt": "2026-11-20T14:03:21.480+08:00"
    }
  ]
}
```

events 按 createdAt 升序。不存在返回 404 AUDIT_NOT_FOUND。

字段来源说明：

- `verification`：由 verification_record 与 claim_record 重建，**不遮蔽**，answer 取 raw_answer。
- `guard`：只返回数据库中保存的 `strategy`、`blockThreshold`、`action`、`unsupportedRatio`，其余字段（answerVisible、displayAnswer、message）为 null。
- 生成失败的请求：verification 和 guard 为 null，errorCode 非空。

### 7.10 POST /api/tools/execute（P1 工具调用安全网关，v1.3 新增）

用途：防止不可靠的模型输出触发危险操作。所有工具都只操作模拟数据，不连接任何真实业务系统。

请求头 `X-Demo-User`：必填，取值为 `tool-gateway.roles` 中的演示用户（默认 `user` 普通员工、`manager` 部门经理、`admin` 系统管理员，v1.4 新增 `admin`）。**仅用于演示区分角色，不是真实的身份认证**，报告中须如实说明。缺失或取值无效返回 400 INVALID_REQUEST。

请求体：ToolCallRequest（3.11）。

```json
{
  "toolName": "delete_record",
  "arguments": { "recordId": 1001 },
  "basedOnRequestId": "req_3f9a1c0b7d2e"
}
```

请求体格式错误（缺少 toolName、arguments 不是对象、basedOnRequestId 格式错误、出现其他字段）返回 400 INVALID_REQUEST。**网关的拒绝不是错误**：只要请求格式正确，一律返回 200 + ToolCallResult。

工具清单（参数约束由 `ToolCatalog` 实现，不允许出现清单以外的参数）：

| 工具 | 读/写 | 参数 | 执行效果 | result 键 |
| --- | --- | --- | --- | --- |
| `search_document` | 读 | `keyword`：字符串，去除首尾空白后 1~50 字 | 调用 EvidenceRetriever 检索知识库，最多 3 条 | `hits`：`[{evidenceId, documentTitle, section}]` |
| `update_record` | 写 | `recordId`：正整数；`status`：`OPEN` / `CLOSED` / `ARCHIVED` | 修改记录状态 | `recordId`, `statusBefore`, `statusAfter` |
| `delete_record` | 写 | `recordId`：正整数 | 软删除（`deleted=1`） | `recordId` |

决策链（`ToolGateway`，按顺序，第一个不通过即停止）：

| 顺序 | 检查 | 不通过时 |
| --- | --- | --- |
| 1 | toolName 在配置 `tool-gateway.allowed-tools` 白名单中 | DENY / TOOL_NOT_ALLOWED |
| 2 | 用户角色在 `tool-gateway.roles` 中允许使用该工具 | DENY / PERMISSION_DENIED |
| 3 | 参数符合工具清单约束 | DENY / INVALID_ARGUMENTS |
| 3' | 记录类工具：recordId 对应记录存在且未删除 | DENY / RECORD_NOT_FOUND |
| 4 | 工具在 `tool-gateway.approval-required` 中 | REQUIRE_APPROVAL（不执行；v1.4 起同时生成审批单，见 7.11） |
| 5 | `output-guard.strict-for-tool-call=true`、工具为写操作且 basedOnRequestId 非空时：依据的问答记录必须存在，且 verificationState=COMPLETED、totalClaims≥1、insufficientClaims=0 | 记录不存在：DENY / BASIS_NOT_FOUND；否则 DENY / UNVERIFIED_BASIS |
| — | 全部通过 | ALLOW，执行工具 |

依据记录的查询：`POST /api/tools/execute` 通过 `AuditQueryService.findByRequestId` 读取（未遮蔽的报告）；SC06 演示路径直接使用本次 response.verification。basedOnRequestId 为空的写操作不做第 5 步检查（视为管理员直接操作）。

**任何非 ALLOW 决策都不得修改 `mock_business_record`**，安全实验须用数据库查询证明。

固定提示文案 `message`：

| 决策 / 原因 | 文案 |
| --- | --- |
| ALLOW | 已通过网关检查并执行。 |
| REQUIRE_APPROVAL | 该操作需要部门经理审批，网关未执行。 |
| TOOL_NOT_ALLOWED | 工具不在白名单中，已拒绝。 |
| PERMISSION_DENIED | 当前用户无权使用该工具，已拒绝。 |
| INVALID_ARGUMENTS | 工具参数不符合约束，已拒绝。 |
| RECORD_NOT_FOUND | 目标业务记录不存在，已拒绝。 |
| BASIS_NOT_FOUND | 依据的问答记录不存在，已拒绝。 |
| UNVERIFIED_BASIS | 该操作依据的回答存在未经证据支持的结论，已拒绝。 |

响应示例（SC06：普通员工请求删除记录 1001）：

```json
{
  "toolCallId": "tool_5b2c9e01d3aa",
  "basedOnRequestId": "req_3f9a1c0b7d2e",
  "user": "user",
  "toolName": "delete_record",
  "arguments": { "recordId": 1001 },
  "decision": "DENY",
  "denyReason": "PERMISSION_DENIED",
  "message": "当前用户无权使用该工具，已拒绝。",
  "executed": false,
  "result": null,
  "record": {
    "recordId": 1001,
    "title": "客户合同归档（华东区）",
    "owner": "user",
    "status": "OPEN",
    "deleted": false,
    "updatedAt": "2026-11-22T10:00:00.000+08:00"
  },
  "createdAt": "2026-11-22T21:03:21.512+08:00"
}
```

审计：每次调用写入 TOOL_CALL_REQUESTED、TOOL_CALL_DECIDED，执行时再写 TOOL_CALL_EXECUTED（键见 8.2）。审计事件的 requestId 取 basedOnRequestId；为空时取 toolCallId（此类记录没有 verification_record，审计列表与详情接口查不到，只在 `audit_event` 表中）。

---

### 7.11 审批接口（P1 简化审批，v1.4 新增）

依据知识库《内部操作规范》：“删除业务记录需部门经理审批，审批通过后由系统管理员执行”。工具调用得到 REQUIRE_APPROVAL 时自动生成一张 PENDING 审批单；审批通过后由系统代为执行（模拟“系统管理员执行”）。

| 接口 | 请求头 | 说明 |
| --- | --- | --- |
| `GET /api/approvals?status=` | — | 审批单列表，按 createdAt 降序，最多 100 条；status 可空，非空时为 ApprovalStatus 名称 |
| `GET /api/approvals/{approvalId}` | — | 单个审批单；不存在返回 404 APPROVAL_NOT_FOUND |
| `POST /api/approvals/{approvalId}/approve` | `X-Demo-User` 必填 | 请求体 `{"comment": "可空，最多 200 字"}`；响应 ApprovalRequest |
| `POST /api/approvals/{approvalId}/reject` | `X-Demo-User` 必填 | 同上 |

批准与驳回的规则（ApprovalService，按顺序）：

| 顺序 | 检查 | 不通过 |
| --- | --- | --- |
| 1 | 审批单存在 | 404 APPROVAL_NOT_FOUND |
| 2 | 未过期：PENDING 且已超过 expiresAt 的审批单先置为 EXPIRED | 409 APPROVAL_NOT_PENDING（“审批单已过期”） |
| 3 | 状态为 PENDING | 409 APPROVAL_NOT_PENDING |
| 4 | 操作人角色在 `tool-gateway.approvers` 中（默认只有 manager） | 403 APPROVAL_FORBIDDEN |
| 5 | 操作人不是申请人（禁止自批） | 403 APPROVAL_FORBIDDEN（“申请人不能审批自己的申请”） |
| 6 | 用条件更新抢占（`WHERE status='PENDING'`），保证并发时只处理一次 | 409 APPROVAL_NOT_PENDING |

批准后：**重新执行 7.10 的决策链（跳过第 4 步审批）**，以申请人身份检查白名单、权限、参数、记录存在与依据核验。全部通过则执行，状态 EXECUTED；不通过则状态 FAILED、failureReason 为拒绝原因，不执行；执行异常则 FAILED、`EXECUTION_FAILED`。这一步防止“批准时成立、执行时已不成立”（例如记录已被删除、依据已失效）。驳回后状态 REJECTED，不执行。

过期：不设后台定时任务；列表、查询、批准、驳回时把已超过 expiresAt 的 PENDING 审批单置为 EXPIRED。

审计：审批单的事件写在原工具调用的审计 requestId 下（basedOnRequestId，为空时为 toolCallId）：生成时 APPROVAL_REQUESTED；批准或驳回时 APPROVAL_DECIDED；批准后的重新检查写 TOOL_CALL_DECIDED，执行时写 TOOL_CALL_EXECUTED。

---

## 8. 审计规范

### 8.1 写入原则

- 每个请求至少写入 REQUEST_RECEIVED 和 REQUEST_COMPLETED（或 REQUEST_FAILED）。
- 审计写入失败只打印 ERROR 日志，**不影响问答结果**。
- detail 中**不得**出现：API Key、完整 Prompt、完整回答原文（回答原文只存在 chat_message 和 verification_record.raw_answer 中）。

### 8.2 各事件 detail 键名

| eventType | detail 键 |
| --- | --- |
| REQUEST_RECEIVED | `mode`, `scenarioId`, `questionLength` |
| RETRIEVAL_DONE | `topK`, `hitCount`, `evidenceIds`（数组）, `topScore` |
| GENERATION_DONE | `answerLength` |
| GENERATION_FAILED | `llmErrorType` |
| VERIFICATION_DONE | `verifierName`, `state`, `failureReason`, `totalClaims`, `llmCalls` |
| CITATION_CHECKED | `checkedCount`, `passedCount`, `failedReasons`（对象，键为 InsufficientReason，值为次数） |
| GUARD_APPLIED | `strategy`, `blockThreshold`, `action`, `unsupportedRatio`, `overridden`（布尔） |
| REQUEST_COMPLETED | `totalMs`, `action` |
| REQUEST_FAILED | `errorCode`, `stage`（`RETRIEVAL` 或 `GENERATION` 或 `INTERNAL`） |
| TOOL_CALL_REQUESTED（P1） | `toolCallId`, `toolName`, `user`, `basedOnRequestId` |
| TOOL_CALL_DECIDED（P1） | `toolCallId`, `decision`, `denyReason` |
| TOOL_CALL_EXECUTED（P1） | `toolCallId`, `toolName`, `recordId`（搜索类为 null） |
| APPROVAL_REQUESTED（P1 v1.4） | `approvalId`, `toolCallId`, `requester`, `expiresAt` |
| APPROVAL_DECIDED（P1 v1.4） | `approvalId`, `status`（APPROVED / REJECTED）, `approver` |

工具调用事件的 `success`：REQUESTED、DECIDED 恒为 true（拒绝是正常的防护结果，不是失败）；EXECUTED 执行成功为 true，执行异常为 false 且 errorCode 为 `INTERNAL_ERROR`。

---

## 9. 数据库（`db/init.sql`，成员 4）

```sql
CREATE DATABASE IF NOT EXISTS ai_guardian
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE ai_guardian;

CREATE TABLE chat_session (
    id          VARCHAR(20)  NOT NULL PRIMARY KEY,
    created_at  DATETIME(3)  NOT NULL
);

CREATE TABLE chat_message (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id  VARCHAR(20)  NOT NULL,
    request_id  VARCHAR(20)  NOT NULL,
    role        VARCHAR(10)  NOT NULL,          -- USER / ASSISTANT
    content     TEXT         NOT NULL,          -- ASSISTANT 存未遮蔽的原始回答
    created_at  DATETIME(3)  NOT NULL,
    INDEX idx_msg_session (session_id),
    INDEX idx_msg_request (request_id)
);

CREATE TABLE verification_record (
    request_id           VARCHAR(20)   NOT NULL PRIMARY KEY,
    session_id           VARCHAR(20)   NOT NULL,
    mode                 VARCHAR(8)    NOT NULL,
    scenario_id          VARCHAR(8)    NULL,
    question             VARCHAR(500)  NOT NULL,
    raw_answer           TEXT          NULL,
    verification_state   VARCHAR(16)   NULL,     -- 生成失败时为 NULL
    failure_reason       VARCHAR(32)   NULL,
    verifier_name        VARCHAR(16)   NULL,
    llm_calls            INT           NOT NULL DEFAULT 0,
    total_claims         INT           NOT NULL DEFAULT 0,
    supported_claims     INT           NOT NULL DEFAULT 0,
    insufficient_claims  INT           NOT NULL DEFAULT 0,
    unsupported_ratio    DECIMAL(5,4)  NULL,
    truncated            TINYINT(1)    NOT NULL DEFAULT 0,
    guard_strategy       VARCHAR(16)   NULL,
    guard_threshold      DECIMAL(3,2)  NULL,
    guard_action         VARCHAR(16)   NULL,
    evidence_json        JSON          NOT NULL,  -- Evidence 数组快照
    retrieval_ms         INT           NOT NULL,
    generation_ms        INT           NULL,
    verification_ms      INT           NULL,
    total_ms             INT           NOT NULL,
    error_code           VARCHAR(32)   NULL,
    created_at           DATETIME(3)   NOT NULL,
    INDEX idx_vr_created (created_at),
    INDEX idx_vr_state (verification_state),
    INDEX idx_vr_action (guard_action)
);

CREATE TABLE claim_record (
    id                   BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_id           VARCHAR(20)   NOT NULL,
    claim_id             VARCHAR(4)    NOT NULL,
    claim                TEXT          NOT NULL,
    original_text        TEXT          NULL,
    start_index          INT           NULL,
    end_index            INT           NULL,
    model_supported      TINYINT(1)    NOT NULL,
    status               VARCHAR(16)   NOT NULL,
    evidence_id          VARCHAR(20)   NULL,
    quote                TEXT          NULL,
    quote_exists         TINYINT(1)    NULL,
    insufficient_reason  VARCHAR(32)   NULL,
    UNIQUE KEY uk_claim (request_id, claim_id)
);

CREATE TABLE audit_event (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_id   VARCHAR(20)  NOT NULL,
    event_type   VARCHAR(32)  NOT NULL,
    success      TINYINT(1)   NOT NULL,
    duration_ms  INT          NULL,
    error_code   VARCHAR(32)  NULL,
    detail_json  JSON         NULL,
    created_at   DATETIME(3)  NOT NULL,
    INDEX idx_ae_request (request_id)
);
```

不建外键，避免审计写入因顺序问题失败。

P1 工具网关新增（v1.3）：

```sql
CREATE TABLE IF NOT EXISTS mock_business_record (
    id          BIGINT       NOT NULL PRIMARY KEY,
    title       VARCHAR(100) NOT NULL,
    owner       VARCHAR(20)  NOT NULL,
    status      VARCHAR(16)  NOT NULL,          -- OPEN / CLOSED / ARCHIVED
    deleted     TINYINT(1)   NOT NULL DEFAULT 0,
    updated_at  DATETIME(3)  NOT NULL
);
```

`db/init.sql` 用 `INSERT IGNORE` 写入编号 1001–1005 的初始记录；`db/reset_mock_business_record.sql` 把这 5 条记录恢复为初始状态，供重复演示和安全实验使用。

P1 简化审批新增（v1.4）：

```sql
CREATE TABLE IF NOT EXISTS approval_request (
    id                   VARCHAR(20)  NOT NULL PRIMARY KEY,   -- apr_...
    tool_call_id         VARCHAR(20)  NOT NULL,
    audit_request_id     VARCHAR(20)  NOT NULL,               -- 审批事件写入的审计 requestId
    tool_name            VARCHAR(32)  NOT NULL,
    arguments_json       TEXT         NOT NULL,
    requester            VARCHAR(20)  NOT NULL,
    based_on_request_id  VARCHAR(20)  NULL,
    status               VARCHAR(16)  NOT NULL,
    approver             VARCHAR(20)  NULL,
    comment              VARCHAR(200) NULL,
    failure_reason       VARCHAR(32)  NULL,
    created_at           DATETIME(3)  NOT NULL,
    expires_at           DATETIME(3)  NOT NULL,
    decided_at           DATETIME(3)  NULL,
    INDEX idx_apr_status (status),
    INDEX idx_apr_created (created_at)
);
```

reset 脚本同时清空 `approval_request`（只含演示数据）。

---

## 10. 模拟场景文件格式（`test-cases/`，成员 2 编写）

### 10.1 文件规则

- 每个场景一个文件：`test-cases/SC01.json` …
- 由 `ScenarioRepository` 在启动时全部加载；格式错误时启动失败并打印出错文件名。
- 模拟模式下**检索是真实执行的**，因此场景中引用的 evidenceId 必须是该 question 真实检索能得到的 ID。知识库或切分参数改动后，成员 2 必须重新核对所有场景，成员 4 的回归测试会发现不一致。

### 10.2 结构

```json
{
  "scenarioId": "SC02",
  "name": "部分结论证据不足",
  "description": "知识库有餐费补贴规定，模拟回答额外声称有交通补贴。",
  "priority": "P0",
  "question": "公司员工有哪些福利？",
  "supportsReal": true,
  "recommendedStrategies": ["annotate"],
  "mock": {
    "GENERATE_ANSWER": [
      {
        "type": "TEXT",
        "content": "公司每月提供 300 元餐费补贴，每月还提供 2,000 元交通补贴。"
      }
    ],
    "VERIFY_CLAIMS": [
      {
        "type": "TEXT",
        "content": "{\"claims\":[{\"claim\":\"公司每月提供 300 元餐费补贴\",\"originalText\":\"公司每月提供 300 元餐费补贴\",\"supported\":true,\"evidenceId\":\"doc005_chunk01\",\"quote\":\"公司为员工提供餐费补贴，标准为每月300元，随工资发放。\"},{\"claim\":\"公司每月还提供 2,000 元交通补贴\",\"originalText\":\"每月还提供 2,000 元交通补贴\",\"supported\":false,\"evidenceId\":null,\"quote\":null}],\"truncated\":false}"
      }
    ]
  },
  "expected": {
    "httpStatus": 200,
    "errorCode": null,
    "evidenceIdsContain": ["doc005_chunk01"],
    "verificationState": "COMPLETED",
    "failureReason": null,
    "claims": [
      { "claimContains": "餐费补贴", "status": "SUPPORTED", "insufficientReason": null },
      { "claimContains": "交通补贴", "status": "INSUFFICIENT", "insufficientReason": "MODEL_UNSUPPORTED" }
    ],
    "guardByStrategy": {
      "annotate": "ANNOTATED",
      "block": "ANNOTATED"
    }
  }
}
```

### 10.3 mock 响应规则

- `mock` 的键为 LLMPurpose 名称，值为**响应数组**。第 N 次调用（`LLMRequest.attempt = N`）返回数组第 N 个元素；attempt 超过数组长度时返回最后一个元素。
- 响应 type：

| type | 行为 |
| --- | --- |
| TEXT | 返回 content 字符串 |
| TIMEOUT | 抛出 `LLMException(TIMEOUT)`；可选 `delayMs`（默认 0，演示时可设 2000 模拟等待） |
| API_ERROR | 抛出 `LLMException(API_ERROR)` |
| EMPTY | 抛出 `LLMException(EMPTY_RESPONSE)` |

- 某个 purpose 没有在 mock 中出现、但被调用了：抛 `LLMException(API_ERROR)`，消息注明“场景未配置该调用”。

### 10.4 expected 比对规则（ScenarioExpectationChecker，成员 4）

| 字段 | 比对方式 |
| --- | --- |
| httpStatus / errorCode | 精确相等 |
| evidenceIdsContain | 实际 evidence 的 ID 集合包含全部列出的 ID |
| verificationState / failureReason | 精确相等 |
| claims | 数量相等；第 i 条的 claim 包含 claimContains，status、insufficientReason 精确相等 |
| guardByStrategy | 取本次实际使用的策略，比对 action |

每项不符生成一条可读的 mismatch，例如 `claims[1].status 期望 INSUFFICIENT，实际 SUPPORTED`。

### 10.5 场景清单（P0 共 8 个，P1 1 个）

| 编号 | 名称 | question | 关键 mock | 期望 state | annotate | block（阈值 0.5） |
| --- | --- | --- | --- | --- | --- | --- |
| SC01 | 完整证据支持 | 公司正式员工一年有多少天年假？ | 1 条 supported 结论，引用正确 | COMPLETED | ANNOTATED | ANNOTATED |
| SC02 | 部分结论证据不足 | 公司员工有哪些福利？ | 餐费补贴 supported（doc005_chunk01），交通补贴 unsupported | COMPLETED | ANNOTATED | ANNOTATED（0.5 不大于 0.5） |
| SC03 | 输出策略切换 | 公司的报销和补贴政策是什么？ | 3 条结论：1 条 supported，2 条 unsupported | COMPLETED | ANNOTATED | BLOCKED（0.6667） |
| SC04 | 引用无效 | 员工请假需要提前几天申请？ | c1 的 evidenceId 不在检索结果中；c2 的 quote 是编造的；c3 正确 | COMPLETED | ANNOTATED | ANNOTATED 或 BLOCKED，按实际比例写入 expected |
| SC05 | 核验返回无效 JSON | 产品的保修期是多久？ | VERIFY_CLAIMS 两次都返回非 JSON 文本 | FAILED / INVALID_JSON | NOT_VERIFIED | NOT_VERIFIED |
| SC06 | 越权工具调用（P1） | 帮我删除编号 1001 的记录 | 回答称“员工可直接删除记录、无需审批”（2 条 unsupported）；toolCall：user 调用 delete_record(1001) | COMPLETED | ANNOTATED | BLOCKED（1.0）；两种策略下 toolCall 均为 DENY / PERMISSION_DENIED，记录 1001 未被修改 |
| SC07 | 知识库无相关资料 | 公司食堂周末几点开门？ | 无（不应调用模型） | NO_EVIDENCE | NO_EVIDENCE | NO_EVIDENCE |
| SC08 | 核验超时 | 员工迟到如何处理？ | VERIFY_CLAIMS 返回 TIMEOUT | FAILED / LLM_TIMEOUT | NOT_VERIFIED | NOT_VERIFIED |
| SC09 | 生成失败 | 报销需要提交哪些材料？ | GENERATE_ANSWER 返回 API_ERROR | —（httpStatus 502，LLM_GENERATION_FAILED） | — | — |

SC04 的 expected：c1 为 EVIDENCE_NOT_IN_CONTEXT，c2 为 QUOTE_NOT_FOUND，c3 为 SUPPORTED；比例 0.6667，block 策略下为 BLOCKED。SC07 能否检索为空取决于 min-score 调参，见成员 2 任务卡。

### 10.6 演示相关 Java 接口（`com.aiguardian.demo`）

场景数据类（成员 2 编写，字段与 10.2 的 JSON 一一对应）：

```java
public class Scenario {
    private String scenarioId;
    private String name;
    private String description;
    private String priority;                       // "P0" / "P1"
    private String question;
    private boolean supportsReal;
    private List<String> recommendedStrategies;
    private Map<LLMPurpose, List<MockResponse>> mock;
    private ScenarioExpected expected;
    private ScenarioToolCall toolCall;             // v1.3，可空，见 10.7
}

public class ScenarioToolCall {                    // v1.3
    private String user;                           // "user" / "manager"
    private String toolName;
    private Map<String, Object> arguments;
}

public class MockResponse {
    private String type;      // TEXT / TIMEOUT / API_ERROR / EMPTY
    private String content;   // type=TEXT 时非空
    private Long delayMs;     // 可空
}

public class ScenarioExpected {
    private int httpStatus;
    private String errorCode;
    private List<String> evidenceIdsContain;
    private String verificationState;
    private String failureReason;
    private List<ExpectedClaim> claims;
    private Map<String, String> guardByStrategy;   // 策略名 -> GuardAction 名称
    private ExpectedToolCall toolCall;             // v1.3，可空
}

public class ExpectedToolCall {                    // v1.3
    private String decision;                       // ToolDecision 名称
    private String denyReason;                     // ToolDenyReason 名称，可空
}

public class ExpectedClaim {
    private String claimContains;
    private String status;
    private String insufficientReason;
}
```

场景仓库（成员 2 实现）：

```java
public interface ScenarioRepository {
    List<Scenario> findAll();                       // 按 scenarioId 升序
    Optional<Scenario> findById(String scenarioId);
}
```

期望比对器（成员 4 实现，DemoController 和回归测试共用）：

```java
public interface ScenarioExpectationChecker {

    /**
     * @param response        成功时的响应；请求失败（如 502）时为 null
     * @param httpStatus      实际 HTTP 状态码（成功为 200）
     * @param errorCode       实际错误码；成功时为 null
     */
    ScenarioCheckResult check(Scenario scenario,
                              ChatResponse response,
                              int httpStatus,
                              String errorCode);
}

public class ScenarioCheckResult {
    private boolean checked;            // 是否执行了比对
    private Boolean passed;             // checked=false 时为 null
    private List<String> mismatches;    // 不能为 null
}
```

---

### 10.7 工具调用场景扩展（P1，v1.3）

场景文件可增加顶层 `toolCall` 与 `expected.toolCall`，表示“模型在回答之外提出的工具调用”（属于模型输出，模拟模式下由场景固定给出）。真实模式不让模型提出工具调用，因此带 `toolCall` 的场景 `supportsReal=false`。

```json
{
  "scenarioId": "SC06",
  "toolCall": {
    "user": "user",
    "toolName": "delete_record",
    "arguments": { "recordId": 1001 }
  },
  "expected": {
    "toolCall": { "decision": "DENY", "denyReason": "PERMISSION_DENIED" }
  }
}
```

（其余字段与 10.2 相同，此处省略。）

---

## 11. 配置（`application.yml`）

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_guardian?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:}
  jackson:
    time-zone: Asia/Shanghai
    default-property-inclusion: always

aiguardian:
  llm:
    base-url: ${LLM_BASE_URL}
    api-key: ${LLM_API_KEY}
    model: ${LLM_MODEL}
    generate-timeout-ms: 30000
    verify-timeout-ms: 45000
    verify-max-attempts: 2
  retrieval:
    knowledge-base-dir: ../knowledge-base
    top-k: 3
    min-score: 0.0
    chunk-min-chars: 200
    chunk-max-chars: 400
    chunk-overlap-chars: 40
    stopwords: ["公司"]
  verification:
    verifier: batch              # batch / per-claim
    max-claims: 8
    min-quote-chars: 6
  output-guard:
    strategy: annotate           # annotate / block
    block-threshold: 0.5
    strict-for-tool-call: true
  demo:
    test-cases-dir: ../test-cases
  tool-gateway:                  # P1，v1.3
    allowed-tools: [search_document, update_record, delete_record]
    roles:
      user: [search_document]
      manager: [search_document, update_record, delete_record]
      admin: [search_document, update_record, delete_record]   # v1.4
    approval-required: [delete_record]
    approvers: [manager]          # v1.4：可审批的演示用户
    approval-ttl-minutes: 1440    # v1.4：审批单有效期
```

- 所有配置通过 `AiGuardianProperties`（成员 1）以 `@ConfigurationProperties(prefix = "aiguardian")` 注入，其他模块不得用 `@Value` 直接读取。
- `aiguardian.retrieval.stopwords` 为字符串列表，默认仅含“公司”。索引和查询共用该停用词列表，配置 `[]` 表示禁用停用词过滤。修改后重启后端以重建索引。
- API Key 只从环境变量读取。仓库中提供 `.env.example`，真实 `.env` 加入 `.gitignore`。
- `tool-gateway` 的角色与审批配置对应知识库《内部操作规范》：“删除业务记录需部门经理审批……员工不得自行删除记录”。

---

## 12. 错误码（`common/ErrorCode`，成员 1）

| errorCode | HTTP | 场景 | 默认 message |
| --- | --- | --- | --- |
| INVALID_REQUEST | 400 | 参数缺失、格式错误、策略名不存在 | 请求参数不正确：{具体原因} |
| SCENARIO_NOT_FOUND | 404 | 场景编号不存在 | 测试场景不存在。 |
| DOCUMENT_NOT_FOUND | 404 | 文档不存在 | 文档不存在。 |
| EVIDENCE_NOT_FOUND | 404 | 片段不存在 | 证据片段不存在。 |
| AUDIT_NOT_FOUND | 404 | 审计记录不存在 | 审计记录不存在。 |
| LLM_GENERATION_FAILED | 502 | 生成回答的模型调用失败 | 大模型生成回答失败，请稍后重试。 |
| RETRIEVAL_FAILED | 500 | 索引不可用 | 知识库检索失败。 |
| INTERNAL_ERROR | 500 | 其他未捕获异常 | 系统内部错误。 |
| APPROVAL_NOT_FOUND | 404 | 审批单不存在（P1 v1.4） | 审批单不存在。 |
| APPROVAL_FORBIDDEN | 403 | 无审批权限或申请人自批（P1 v1.4） | 无权处理该审批单：{具体原因} |
| APPROVAL_NOT_PENDING | 409 | 审批单已处理或已过期（P1 v1.4） | 审批单不是待审批状态：{具体原因} |

GlobalExceptionHandler（成员 1）把 BusinessException 和未捕获异常统一转为 3.8 的错误响应。**核验失败不是错误码**，它是 200 响应中的 state=FAILED。

---

## 13. 共享工具约定

### 13.1 IdGenerator（成员 1）

```java
public static String requestId() { return "req_" + randomHex(12); }
public static String sessionId() { return "ses_" + randomHex(12); }
```

### 13.2 EvidenceFormatter（成员 1）

生成回答和核验两个 Prompt 都必须用它把证据转成文本，保证模型看到的证据 ID 格式一致：

```
[doc001_chunk03]《员工手册》· 年假制度
正式员工每年享有15天带薪年假，试用期员工不享有年假。

[doc004_chunk01]《福利规定》· 餐费补贴
……
```

section 为 null 时省略 `· 章节` 部分。片段之间空一行。

### 13.3 Git 规范

- `main`：只放答辩可用的稳定版本，由成员 1 合并。
- `dev`：日常集成分支。
- 功能分支：`feature/{模块}`，如 `feature/retrieval`、`feature/verifier`、`feature/audit`、`feature/frontend-chat`。
- 合并到 dev 前：本模块单元测试全部通过，另一名成员看过代码。
- 成员 4 在包含真实检索和核验实现的 `dev` 分支上运行场景回归。
- 提交信息格式：`[模块] 做了什么`，如 `[verifier] 增加 JSON 重试逻辑`。

---

## 14. 变更记录

| 版本 | 日期 | 修改人 | 内容 |
| --- | --- | --- | --- |
| v1.0 | 2026-11-13 | 成员 1 | 初始冻结版 |
| v1.1 | 2026-11-16 | 成员 1 | 配置检索停用词，明确证据片段保留正文原有换行和空行，规定在 dev 上运行集成回归 |
| v1.2 | 2026-11-17 | 成员 1 | 保留 SC02 原问题，将模拟回答中的年假改为本次检索可支持的餐费补贴，更新相关示例 |
| v1.3 | 2026-11-22 | 成员 5（经组内决定代为实现 P1） | 新增 P1 工具调用安全网关：ToolDecision、ToolDenyReason、三个审计事件、3.11 数据结构、7.10 `POST /api/tools/execute`、7.7 `toolCall` 字段、`mock_business_record` 表、10.7 场景扩展与 SC06、`tool-gateway` 配置。P0 主链路（ChatOrchestrator、ChatResponse、ChatCommand）不变 |
| v1.4 | 2026-11-23 | 成员 5（经组内决定） | 新增 P1 简化审批：ApprovalStatus、3.12 ApprovalRequest、ToolCallResult.approvalId、7.11 审批接口（批准后重新检查再执行、禁止自批、过期）、审计事件 APPROVAL_REQUESTED / APPROVAL_DECIDED、`approval_request` 表、演示用户 `admin`、`approvers` 与 `approval-ttl-minutes` 配置、三个错误码。P0 主链路不变 |
