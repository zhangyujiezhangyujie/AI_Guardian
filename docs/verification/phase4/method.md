# 成员3阶段4核验效果实验方法

2026-11-19，运行真实实验之前固定本方法。标注数据采用 annotations-reviewed.json 1.2-human-confirmed，10个固定回答、30条结论，15条支持与15条证据不足。AI辅助编制、成员2和成员3人工确认；不是双人独立盲标。不得修改已确认标签以适应输出。

## 实验边界与配置

本轮仅运行一次 batch 真实模型评估：逐个回答提供固定问题、回答和正式证据片段，不提供预期标签或参考引文。复用 RealLLMClient、LLMBatchClaimVerifier、ExactQuoteCitationValidator，不启动Spring和数据库。它测量固定证据下的提取、语义核验及程序引用校验，不测量检索召回、生成回答质量或完整系统性能。

使用现有Prompt，temperature=0、JSON模式、最多8条结论、最短引文6字符、核验超时45000ms、最多2次尝试。每回答最多2次调用，总上限20次；仅现有核验器的JSON/结构错误有限重试，不增加网络重试、不扩充样本。进程环境优先，其次读取本地.env；只记录模型名与服务主机名，不保存密钥。每次调用后落盘，意外中断时保留部分结果。

## 对齐规则

对每个回答分别对齐。以originalText对应的Java UTF-16原文区间为准，不使用输出顺序和模型编号。允许去掉外侧空白和句末。.!！?？;；后进行完全相同的核心文本及区间匹配；不改写、不做语义模糊匹配，不忽略内部标点和空白。模型给出的定位还必须等于原文逐字截取。原文重复且无法唯一定位、重复输出竞争同一标注均记无法对齐。标注与输出的核心区间一对一匹配才进入混淆矩阵。

CALL_FAILED表示整例核验失败；MISSING_EXTRACTION表示该标注未提取；EXPECTED_AMBIGUOUS表示标注本身不能唯一定位；DUPLICATE_AMBIGUOUS表示模型重复提取。所有这些项均不算正确。无法定位的输出单列unalignableOutputs，未匹配的输出单列extraOutputs（两类可能重叠，不能相加当作不同结论）。输出切分或合并导致的文本差异按缺失及额外输出处理，保留原始响应便于人工解释；不事后改规则提高分数。

## 指标与分母

阳性=INSUFFICIENT（证据不足），阴性=SUPPORTED（证据支持），预测标签采用引用校验后的最终status。

| 真实标签 | 预测证据不足 | 预测支持 |
| --- | --- | --- |
| 证据不足 | TP | FN |
| 支持 | FP | TN |

- 可对齐结论准确率=(TP+TN)/(TP+TN+FP+FN)。
- 误报率FPR=FP/(FP+TN)，即已对齐的真实支持结论中被报为证据不足的比例。
- 漏报率FNR=FN/(FN+TP)，即已对齐的真实证据不足结论中被报为支持的比例。
- 对齐覆盖率=(TP+TN+FP+FN)/30；全标注正确覆盖率=(TP+TN)/30，所有未解决项均不能贡献正确数。
- 无法对齐/漏提取/失败的真实阳性与阴性另计unresolvedPositive/unresolvedNegative。它们不是二分类输出，不塞进TN或TP，也不静默丢弃；条件指标必须同时报告覆盖率和这两项。分母为0时记录null（不可估计）。

按全部实际输出分别统计quoteExists=true的引用校验成功、false的失败及失败原因、null的未执行（例如模型判不支持或缺引文）。模型本来判不支持不属于程序引用校验失败。引用通过仅代表逐字来源存在，不保证语义蕴含。

## 可复现入口与原始数据

在backend执行 `mvn test '-Dtest=Phase4RealEvaluationTest' '-DrunPhase4RealEvaluation=true'`。普通mvn test默认跳过此入口。只检查离线指标算法可运行 `mvn test '-Dtest=Phase4MetricsTest,AnnotationCandidatesTest,OfficialEvidenceBindingTest'`。

runs/独立运行目录保存dataset.json输入快照、evaluation.json。记录每例输入/标注、每次调用的Prompt和原始content、失败类型、调用耗时、引用校验后完整报告、逐条对齐与汇总。另记代码源文件哈希、数据集哈希、Git HEAD/MERGE_HEAD/工作区状态、Java和运行参数。每次独立目录，不覆盖历史样例结果。模型异常只保留类型，不输出可能含敏感内容的异常消息。
