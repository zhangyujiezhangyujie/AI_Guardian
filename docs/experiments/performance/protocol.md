# A B C 完整业务链路性能实验规程

状态：2026-11-20已完成正式A/B/C各20次。方法在运行前固定，结果见results.md；下述早期阻碍保留为历史。

2026-11-20更新：完整离线回归已实际通过（148项，143通过、5显式实验跳过）。本机MySQL服务与端口正常，但项目.env账号认证失败（1045）；真实试跑和正式测量仍为0次。本轮数据外发及审计写入已获用户明确授权，当前等待有效数据库凭据，不再以旧集成测试错误作为阻碍。

## 统一设计

A=none，仅生成；B=per-claim，生成后先提取再逐条核验；C=batch，生成后批量提取和核验。入口为真实Spring应用的ChatOrchestrator.handle及既有verifierOverride，完整执行Lucene检索、真实生成、所选核验、引用校验、输出防护与真实JDBC审计。它是进程内完整业务链路性能，不包含HTTP传输、浏览器渲染或应用冷启动；报告不得称为HTTP端到端延迟。

先以“公司正式员工一年有多少天年假？”对A/B/C各预热1次，同时作为真实环境探测；预热结果单独保存，不进入正式统计。任何一臂不能通过真实审计读回或B/C没有实际结论及成功引用校验，立即停止，不开始正式测量。独立smoke只运行这3次探测，不是正式数据，也不能补进20次。smoke和formal各自使用独立目录。

正式20轮，每轮同一问题执行三臂：奇数轮年假问题，偶数轮“公司员工有哪些福利？”，每臂每题10次。轮序按ABC、BCA、CAB循环，单线程无并发；顺序不随机更改、不挑选更快结果。最后20轮不能被3整除，顺序平衡近似而非完全。生成temperature=0.3，核验temperature=0，固定同一模型、服务、Prompt、检索参数、知识库、annotate策略和0.5阈值；真实生成不保证答案完全相同，保存答案长度与内容供解释，不将回答数量差异全部归因于核验方式。

主计时用System.nanoTime，从调用handle前到返回或抛出后，包含同步审计写入；不含启动、预热、结果文件序列化及事后审计查询。响应timing亦原样保存作阶段诊断，不替代外部主计时。RealLLMClient只做观察spy，始终调用真实方法，保存每次输入与原始content、失败类型与耗时。提取调用、逐条核验和所有有限重试都算一次实际模型调用；分别保留报告llmCalls和观察调用列表并核对（报告只含核验调用，总调用还含生成）。

## 门槛及可复现执行

1. 成员1牵头与测试负责人、成员4修复RetrievalVerificationIntegrationTest数据库依赖配置；从backend执行run-phase4.ps1 -Action Offline，完整回归实际成功才生成regression-gate.json。跳过的真实API/MySQL专用实验不证明正式环境可用。
2. gate记录本轮所有新XML报告及其哈希、源文件哈希和检索集成测试通过证据。性能入口核对报告未变化、源码/配置未变化，防止使用过期通过证明。若门槛失败不调用模型。
3. 使用MySQL8执行已有db/init.sql，向进程导出DB_USERNAME、DB_PASSWORD及模型环境。不得用stub、内存库或替身审计。性能入口会检查实际Bean为Lucene、RealLLMClient、两种真实核验器、确定性引用校验、输出策略、JdbcAuditService及JdbcAuditQueryService。启动成功仍不算过门槛。
4. 显式运行run-phase4.ps1 -Action PerformanceSmoke。真实请求完成后通过生产AuditQueryService从MySQL读回本次requestId、原始回答、报告及REQUEST_COMPLETED/CITATION_CHECKED等事件，证实真实数据库写后读。B/C还需成功引用校验，A需确认没有核验调用。审计失败会被生产代码吞掉，所以必须另外读回核实。
5. 全部满足并固定工作区后，显式运行run-phase4.ps1 -Action PerformanceFormal；同样先核查门槛并预热，然后各20次。可通过-Maven和-JavaHome指定本机Maven和Java路径。普通mvn test不会执行真实性能入口。

原始文件位于smoke-时间戳或formal-时间戳/performance.json，保留Git HEAD/MERGE_HEAD、未提交状态、代码和配置哈希、模型、安全配置白名单、每条问题/次序/调用/响应/审计读回/耗时/异常类型。允许未提交合并，但必须记录其状态和实际内容哈希，绝不以HEAD单独冒充完整实验代码。所有结果保留，审计记录不自动删除。不给配置对象序列化LLM密钥或数据库密码。

## 失败与统计

正式每臂固定20次尝试，不以成功次数补测。请求错误、FAILED/PARTIAL/NO_EVIDENCE、无结论、未完成所选链路、引用或审计门槛异常均标记失败，保留原始状态。超时按观察到的TIMEOUT异常计数。不增加核验器以外的重试。运行意外中断保留已有记录，只能标记部分完成，不能声称各20次。

summary对正式记录按臂统计attempts、successes、failures、timeouts、模型调用总数；主延迟为全部尝试的平均值、P50/P95（nearest-rank法，排序后取ceil(p*n)项），另提供成功请求平均延迟。失败尝试延迟也必须保留，不能用快速失败宣称加速；成功样本不足时应结合失败率解释。预热和smoke全部排除。n=0时耗时为null，不填0当测量结果。

正式结果已保存至formal-1795158337163/performance.json，详见results.md。早期认证/库表问题解决后，实际门槛通过才开始测量。
