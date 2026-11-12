# AI Guardian

AI Guardian 是面向企业制度知识库的问答与证据核验原型。本分支提供成员 1 负责的后端工程、模型适配器、问答编排、输出防护、共享契约与联调材料。

## 快速启动

需要 JDK 17、Maven 3.9 和可用的 8080 端口。首次构建需从 Maven 仓库下载依赖。Stub 模式使用固定回答、证据和核验结果，审计写入应用日志，可以离线演示业务流程。

```powershell
cd backend
mvn spring-boot:run '-Dspring-boot.run.profiles=stub'
```

另开终端发送问答请求。PowerShell 示例使用 UTF-8 编码发送中文 JSON。

```powershell
$body = @{ question = '公司员工有哪些福利？' } | ConvertTo-Json -Compress
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/chat' -ContentType 'application/json; charset=UTF-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
Invoke-RestMethod 'http://localhost:8080/api/guard/config'
```

问福利时返回两条结论，其中一条证据不足。问“公司食堂周末几点开门？”可触发 `NO_EVIDENCE`。用环境变量 `AIGUARDIAN_OUTPUTGUARD_STRATEGY=block` 和 `AIGUARDIAN_OUTPUTGUARD_BLOCKTHRESHOLD=0.3` 启动可复现拦截，也可修改 `backend/src/main/resources/application.yml` 后重启。

## 正式联调

1. 合入成员 2 的知识库、检索及场景模拟模块，成员 3 的核验和引用校验模块，以及成员 4 的审计实现和 `db/init.sql`。正式运行启用这些 Bean，使用 `stub` profile 时只启用 Stub 依赖。模拟路由按 Bean 名 `mockLLMClient` 注入；核验器按 `ClaimVerifier.name()` 查找。
2. 使用 MySQL 8，在 MySQL 客户端执行成员 4 的 `db/init.sql`。默认连接 `localhost:3306/ai_guardian`。按根目录 `.env.example` 配置 `DB_USERNAME`、`DB_PASSWORD`、`LLM_BASE_URL`、`LLM_MODEL` 和 `LLM_API_KEY`。将这些键导出到启动后端的进程环境。Spring Boot 不会自动加载 `.env` 文件。
3. 从 `backend/` 运行 `mvn spring-boot:run`。从该目录启动可正确解析知识库和场景文件的默认相对路径。
4. 前端合入后，从 `frontend/` 按前端项目锁文件安装依赖并运行开发服务器，默认端口 5173，通过 Vite 代理访问后端的 `/api`。

示例模型配置使用 DeepSeek 的 OpenAI 兼容 Chat Completions，Base URL 为 `https://api.deepseek.com`，模型为 `deepseek-flash`。核验使用 JSON 模式时，核验 Prompt 应明确要求输出 JSON 并提供结构示例。API Key 由部署环境提供。[DeepSeek JSON 模式](https://api-docs.deepseek.com/guides/json_mode/)

## 测试与联调材料

```powershell
cd backend
mvn test
mvn test '-DexportChatExamples=true'
```

第二条命令将五种输出动作的联调样例写入 `backend/target/chat-examples/`。LLM 适配器测试使用本地 HTTP 服务验证协议、异常和超时。全组固定场景回归需要成员 2 的场景文件和成员 4 的测试框架，合入后从后端执行 Maven 测试。

- [问答与输出策略接口](docs/api.md)
- [接口契约附录 v1.0](docs/interface-contract.md)
- [架构设计与报告素材](docs/architecture/member1.md)
- [可修改性实验](docs/modifiability.md)
