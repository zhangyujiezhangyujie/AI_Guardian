# 输出防护可修改性实验

2026 年 11 月 12 日在 Java 17、Spring Boot 3.3.13 下完成。运行可执行 JAR，启用 `stub`，通过 HTTP 请求验证配置和问答结果。使用与契约 SC02 相同的问题和结论比例，报告包含一条支持结论和一条证据不足结论。完整 SC02 场景回放需要成员 2 的检索和场景模块。

## 实验一 新增策略

`954c3ab` 仅新增 `StrictOutputGuard.java`，共 1 个文件、32 行新增。从写入类文件到提交约 1.89 秒，计时只覆盖实际文件操作和 Git 提交。以下命令可复查差异。

```text
git show --stat --oneline 954c3ab
git show 954c3ab
```

配置端点返回 `["annotate", "block", "strict"]`。同一个 Stub 核验报告在 `block` 阈值为 0.5 时返回 `ANNOTATED`，使用 `strict` 时返回 `BLOCKED`，正文已遮蔽。编排器、核验模块与 Controller 在该提交中的改动均为 0。

## 实验二 降低拦截阈值

启动参数固定输出策略为 `block`。使用同一个 JAR，启动时额外加载 `backend/src/main/resources/` 的配置。修改一处阈值后重启，向 `POST /api/chat` 发送相同问题。文件写入用时约 7.9 毫秒，计时不包含重启和接口验证。

```diff
  output-guard:
    strategy: annotate
-   block-threshold: 0.5
+   block-threshold: 0.3
    strict-for-tool-call: true
```

`git diff --numstat` 显示 1 个文件、1 行新增、1 行删除。

| 策略 | 阈值 | 证据不足比例 | 实际动作 | requestId |
| --- | ---: | ---: | --- | --- |
| block | 0.5 | 0.5 | ANNOTATED | req_c4621f1a6caf |
| strict | 0.5 | 0.5 | BLOCKED | req_37d4e4fc94be |
| block | 0.3 | 0.5 | BLOCKED | req_46c896c142f2 |

从 `backend/` 执行 `mvn package` 后，用下列命令启动并访问问答接口。实验结束后已恢复默认阈值 0.5。

```powershell
java -jar target/ai-guardian-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=stub --aiguardian.output-guard.strategy=block --spring.config.additional-location=file:./src/main/resources/
```

41 项单元测试和集成测试通过，包含 0.5 与 0.5001 的严格大于边界、遮蔽与审计隔离。
