# Prompt v1 真实 API 样例结果

执行时间：2026-11-14T18:30:57.772894400+08:00 至 2026-11-14T18:31:16.592296100+08:00。模型：`deepseek-flash`。

使用仓库根目录本地 `.env`，通过现有 `RealLLMClient` 程序逐条执行 10 个预先设计的虚构制度样例，代替手工发送请求；以下为本轮结果检查，仍建议组员对语义判断独立复核。

| 样例 | 预期 | 实际状态 | 结论数 | 支持数 | 调用数 | 耗时 ms |
|---|---|---|---:|---:|---:|---:|
| V01 | COMPLETED；1 条支持 | COMPLETED | 1 | 1 | 1 | 1586 |
| V02 | COMPLETED；年假支持，交通补贴证据不足 | COMPLETED | 2 | 1 | 1 | 2304 |
| V03 | COMPLETED；证据不足 | COMPLETED | 1 | 0 | 1 | 893 |
| V04 | COMPLETED；与证据矛盾 | COMPLETED | 1 | 0 | 1 | 933 |
| V05 | COMPLETED；支持 | COMPLETED | 1 | 1 | 1 | 1543 |
| V06 | COMPLETED；支持 | COMPLETED | 1 | 1 | 1 | 1059 |
| V07 | COMPLETED；支持，原文全角数字定位 | COMPLETED | 1 | 1 | 1 | 1252 |
| V08 | NO_CLAIMS | NO_CLAIMS | 0 | 0 | 1 | 1136 |
| V09 | COMPLETED；两条支持，UTF-16 定位 | COMPLETED | 2 | 2 | 1 | 3806 |
| V10 | PARTIAL；最多8条，truncated=true | PARTIAL | 8 | 4 | 1 | 4204 |

本轮 10/10 首次响应均可解析为符合结构要求的 JSON，总计 10 次模型调用，没有触发重试或失败。各例状态与预设期望一致；V02 区分年假与虚构交通补贴，V04 拒绝与证据矛盾的试用期年假，V08 返回无事实性结论，V10 截断到前 8 条并返回 PARTIAL。

V07 全角数字的引用经归一化匹配通过；V09 emoji 后的原文位置按 Java UTF-16 下标定位。所有样例的 originalText 都能在回答原文中找到，支持结论的引用校验均通过。

这只是固定证据、10 个样例、每例一次的连通性与 JSON 稳定性观察，不能证明长期稳定、一般化准确率或检索效果。超时、无效 JSON、有限重试和伪造引用等边界通过离线测试验证；准确率、误报率、漏报率与性能对比留待后续标注集实验。

## 离线验证记录

完整回归共 54 项：53 项通过、1 项真实 API 入口按默认规则跳过；新增离线测试 12 项全部通过。随后单独启用真实 API 实验入口执行通过。编译目标为 Java 17，本机执行使用 IDEA 附带 Java 21 与 Maven。

本机默认 Java 为 25，未用于此次构建。当前 Windows 环境的 Java 本地域连接失败，运行测试时通过以下临时 JVM 参数让 Java 使用自带的 TCP 回退；未修改共享代码或公共配置，其他机器通常无需该参数：

```powershell
mvn test '-DargLine=-Djdk.net.unixdomain.tmpdir=C:/Users/Administrator/AppData/Local/Temp/guardian-no-unix-nonexistent'
```

上述目录必须不存在，不要创建它。Maven 未加入 PATH 时可使用 IDEA 的 Maven，依赖缓存在本地忽略的 `.maven-cache/`；对应运行参数为 `-Dmaven.repo.local=../.maven-cache`（从 backend 执行）。
