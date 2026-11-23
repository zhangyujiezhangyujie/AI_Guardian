# AI Guardian

AI Guardian 是面向企业制度知识库的问答与证据核验原型。`dev` 集成后端编排、Lucene 检索、模拟模型、批量核验与引用校验，以及 Vue 3 问答前端。

## 环境要求

| 部分 | 需要 | 端口 |
| --- | --- | --- |
| 后端 `backend/` | JDK 17、Maven 3.9 | 8080 |
| 前端 `frontend/` | Node.js 20.19 及以上或 22.12 及以上（自带 npm） | 5173 |

用 `java -version`、`mvn -v`、`node -v` 检查版本。Node.js 从 [nodejs.org](https://nodejs.org/) 下载 LTS 版本即可，Node 18 无法运行本前端使用的 Vite 7。

## 快速启动

### 1. 启动后端（Stub 模式）

首次构建需从 Maven 仓库下载依赖。Stub 模式使用固定回答、证据和核验结果，审计写入应用日志，可以离线演示业务流程。

```powershell
cd backend
mvn spring-boot:run '-Dspring-boot.run.profiles=stub'
```

### 2. 启动前端

另开一个终端，在仓库根目录执行：

```powershell
cd frontend
npm ci
npm run dev
```

- `npm ci` 按 `package-lock.json` 安装依赖，只需在首次运行或依赖变更（`git pull` 后 `package-lock.json` 有改动）时执行。
- 浏览器打开 <http://localhost:5173>。页面通过 Vite 代理把 `/api` 请求转发到后端 8080，后端无需配置跨域。
- 在“问答核验”页提问“公司员工有哪些福利？”可看到部分结论被标黄，提问“公司食堂周末几点开门？”可看到无相关资料。

后端还没准备好时，可以只启动前端的 Mock 模式，所有接口返回前端自带的模拟数据，不需要后端：

```powershell
cd frontend
npm run dev:mock
```

Mock 模式下页面顶部有橙色标签“前端 Mock 模式”。**答辩和联调必须用 `npm run dev`**。Mock 问答的关键词表和其他说明见 [frontend/README.md](frontend/README.md)。

### 3. 只测后端接口（可选）

不打开前端时，可以另开终端直接发送问答请求。PowerShell 示例使用 UTF-8 编码发送中文 JSON。

```powershell
$body = @{ question = '公司员工有哪些福利？' } | ConvertTo-Json -Compress
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/chat' -ContentType 'application/json; charset=UTF-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
Invoke-RestMethod 'http://localhost:8080/api/guard/config'
```

问福利时返回两条结论，其中一条证据不足。问“公司食堂周末几点开门？”可触发 `NO_EVIDENCE`。用环境变量 `AIGUARDIAN_OUTPUTGUARD_STRATEGY=block` 和 `AIGUARDIAN_OUTPUTGUARD_BLOCKTHRESHOLD=0.3` 启动可复现拦截，也可修改 `backend/src/main/resources/application.yml` 后重启。

## 正式联调

1. 从 `dev` 开始联调。正式运行还需要合入成员 4 的审计实现和 `db/init.sql`。正式模式使用 Lucene 检索和批量核验，模拟路由按 Bean 名 `mockLLMClient` 注入，核验器按 `ClaimVerifier.name()` 查找。
2. 使用 MySQL 8，在 MySQL 客户端执行成员 4 的 `db/init.sql`。默认连接 `localhost:3306/ai_guardian`。按根目录 `.env.example` 配置 `DB_USERNAME`、`DB_PASSWORD`、`LLM_BASE_URL`、`LLM_MODEL` 和 `LLM_API_KEY`。将这些键导出到启动后端的进程环境。Spring Boot 不会自动加载 `.env` 文件。
3. 从 `backend/` 运行 `mvn spring-boot:run`。从该目录启动可正确解析知识库和场景文件的默认相对路径。
4. 另开终端，从 `frontend/` 执行 `npm ci`（首次）和 `npm run dev`，浏览器打开 <http://localhost:5173>。前端通过 Vite 代理访问后端的 `/api`，正式联调不要使用 `npm run dev:mock`。

示例模型配置使用 DeepSeek 的 OpenAI 兼容 Chat Completions，Base URL 为 `https://api.deepseek.com`，模型为 `deepseek-flash`。核验使用 JSON 模式时，核验 Prompt 应明确要求输出 JSON 并提供结构示例。API Key 由部署环境提供。[DeepSeek JSON 模式](https://api-docs.deepseek.com/guides/json_mode/)

## P1 工具调用安全网关

模型除了回答问题，还可能提出“删除记录”等工具调用。网关在执行前用 Java 代码依次检查：白名单 → 角色权限 → 参数 → 是否需审批 → 所依据的回答是否全部有证据。工具只操作模拟表 `mock_business_record`，不连接真实系统。规格见[接口契约 v1.3](docs/interface-contract.md) 第 3.11、7.10 节，实验见[工具网关实验](docs/experiments/tool_security.md)。

1. **建表**：已经执行过 `db/init.sql` 的数据库，重新执行一次即可补上新表和 1001–1005 五条记录（脚本均为 `IF NOT EXISTS` / `INSERT IGNORE`，不影响已有数据）。
2. **演示**：演示页运行 SC06。模拟模型替普通员工发起删除编号 1001 的请求，网关以“当前用户无权使用该工具”拒绝，卡片中可见记录未被删除；点击“依据的问答”进入审计详情，时间线末尾是工具调用事件。
3. **直接调用接口**（请求头 `X-Demo-User` 为 `user` 或 `manager`，仅用于演示，不是真实身份认证）：

   ```powershell
   $body = '{"toolName":"delete_record","arguments":{"recordId":1001}}'
   Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/tools/execute' -Headers @{ 'X-Demo-User' = 'user' } -ContentType 'application/json; charset=UTF-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
   ```

4. **恢复演示数据**：执行 `mysql -u root -p < db/reset_mock_business_record.sql`，把 1001–1005 恢复为初始状态。
5. **安全实验**：`cd backend` 后运行 `mvn test -Dtest=ToolSecurityExperimentTest`（默认用 H2）；在 MySQL 上复现的命令见实验文档第 4 节。

## 测试与联调材料

```powershell
cd backend
mvn test
mvn test '-DexportChatExamples=true'
```

第二条命令将五种输出动作的联调样例写入 `backend/target/chat-examples/`。LLM 适配器测试使用本地 HTTP 服务验证协议、异常和超时。成员 4 的场景回归套件合入后，在同时包含检索和核验实现的 `dev` 上从 `backend/` 运行 `mvn test`。

前端单元测试不访问网络和后端：

```powershell
cd frontend
npm test
```

其中 Mock 数据测试会读取 `docs/examples/` 中的后端样例，比对字段是否一致，因此要在完整仓库中运行。

## 常见问题

| 现象 | 处理 |
| --- | --- |
| PowerShell 提示“无法加载文件 npm.ps1，因为在此系统上禁止运行脚本” | 执行 `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` 后重开终端，或改用 `npm.cmd ci`、`npm.cmd run dev` |
| `npm ci` 下载很慢或超时 | 可临时使用国内镜像：`npm ci --registry=https://registry.npmmirror.com` |
| `npm ci` 提示 Node 版本不符（EBADENGINE）或 Vite 启动报错 | 升级 Node.js 到 20.19 及以上或 22.12 及以上 |
| 页面提示“无法连接后端服务” | 后端没有启动或不在 8080 端口，先完成“启动后端” |
| 5173 端口被占用 | 关闭占用端口的程序；Vite 会自动换用 5174 等端口，以终端输出的地址为准 |
| 页面顶部有橙色“前端 Mock 模式”标签 | 当前用的是 `npm run dev:mock`，停止后改用 `npm run dev` |

- [前端说明](frontend/README.md)
- [问答与输出策略接口](docs/api.md)
- [接口契约附录 v1.2](docs/interface-contract.md)
- [架构设计与报告素材](docs/architecture/member1.md)
- [可修改性实验](docs/modifiability.md)
