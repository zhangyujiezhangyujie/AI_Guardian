# AI Guardian 前端（成员 5）

Vue 3 + Vite + Element Plus + axios + vue-router。规格见任务卡 `06_任务卡_成员5_前端与演示.md` 与接口契约第 3、6、7 章。

## 运行

需要 Node.js 20.19 及以上或 22.12 及以上（Vite 7 的要求，`package.json` 的 `engines` 已声明）。启动步骤与常见问题见[仓库根目录 README](../README.md#快速启动)。

```bash
cd frontend
npm ci            # 按 package-lock.json 安装
npm run dev       # 联调 / 答辩：请求经 Vite 代理 /api → http://localhost:8080
npm run dev:mock  # 后端未就绪时独立开发：加载 .env.mock，VITE_USE_MOCK=true
npm run build     # 产物在 dist/
npm test          # Vitest 单元测试，不访问网络和后端
```

开发服务器端口 5173。后端用 Stub 模式启动即可联调：`cd backend && mvn spring-boot:run '-Dspring-boot.run.profiles=stub'`。

Mock 模式下页面顶部会显示橙色标签“前端 Mock 模式（答辩前必须关闭）”。**答辩时必须使用 `npm run dev`**（任务卡第 8 节第 4 条）。

## 目录要点

- `src/api/`：每个接口一个函数，路径与参数按契约第 7 章；`http.js` 统一处理错误（契约 3.8），失败时弹出提示并以 `{ errorCode, message, requestId, httpStatus }` reject，传 `{ silent: true }` 可不弹提示。
- `src/constants/labels.js`：全部枚举 → 中文文案、颜色。
- `src/styles/theme.css`：视觉主题（深色导航、浅色主界面、蓝色主操作、语义化风险色），只覆盖 Element Plus 的 CSS 变量。改配色只需改这个文件。
- `src/mock/`：Mock 模式下替换 axios adapter，路由表在 `routes.js`；未配置的接口返回 404 `MOCK_NOT_CONFIGURED`。

> 安装依赖如遇 npm 10 报错 `Cannot read properties of null (reading 'edgesOut')`（npm 自身缺陷，仅在新增依赖时出现），可用 `npx npm@11 install`。日常 `npm ci` 不受影响。

## Mock 数据（`npm run dev:mock`）

数据定义在 `src/mock/fixtures/`，规则如下：

- `cases.js` 只放数据，每种策略的处理结果都直接写出，Mock 不做“比例与阈值比较”。
- `builder.js` 按契约机械推导下标、计数，并做遮蔽。
- `tests/mockFixtures.test.js` 逐条核对契约规则，并与成员 1 的 `docs/examples` 比对字段。

问答页根据问题中的关键词选择用例，按下表从上往下取第一个匹配：

| 问题包含 | 结果 |
| --- | --- |
| 生成失败 | 502 `LLM_GENERATION_FAILED` |
| xss / html / 标签 | 回答含 `<img onerror>`、`<script>`（XSS 测试） |
| 长回答 / 详细介绍 | 约 1000 字回答，含重叠结论和定位不到的结论 |
| 暂停 | `NOT_VERIFIED`（block，不展示正文） |
| 核验失败 / 保修 | `NOT_VERIFIED`（annotate，展示正文带警告） |
| 超时 / 迟到 | `NOT_VERIFIED`（核验超时） |
| 拦截 | `BLOCKED` |
| 报销 / 补贴政策 | SC03 用 annotate：`ANNOTATED`，3 条结论 2 条证据不足 |
| 引用 / 请假 | SC04 引用无效 |
| 截断 / 员工手册 | `PARTIAL` 截断（M2P） |
| 年假 | 全部有证据（M1） |
| 福利 | 部分证据不足（M2） |
| 怎样提问 / 你好 | `NO_CLAIMS` |
| 其他 | `NO_EVIDENCE` |

Mock 模式下另有一个不在导航中的页面 `/dev/cards`，一次列出全部用例在两种策略下的 AnswerCard，便于检查样式和截图。这个页面只在 `VITE_USE_MOCK=true` 时注册，联调和答辩环境访问不到。

演示页提供 SC01–SC09（SC06 为 P1 工具网关场景，结果中额外显示工具调用卡片；Mock 中网关决策按场景直接写出，不在前端判断），另有 4 个前端 Mock 专用场景：MK01 XSS、MK02 长回答、MK03 截断、MK04 比对不符合预期。Mock 不模拟阈值，结果中的阈值固定为 0.5。问答和演示的每次请求都会写入内存审计记录，另预置 24 条历史记录用于测试分页，刷新页面后重置。

模拟知识库只供前端开发使用，正式知识库由成员 2 维护。

## 路由

| 路由 | 页面 |
| --- | --- |
| `/` | 重定向到 `/chat` |
| `/chat` | 问答核验 |
| `/audit` | 审计日志列表 |
| `/audit/:requestId` | 审计详情 |
| `/demo` | 演示场景 |
| `/approvals` | 审批中心（P1，契约 v1.4；依赖真实后端，Mock 模式下列表为空） |

## 约定

- 禁止使用 `v-html` 渲染任何模型输出。
- 前端只展示后端给出的 `status`、`guard.action`、`guard.answerVisible`、`guard.message`，不自行判断结论真假或计算拦截比例。
- 枚举到中文文案、颜色的映射集中在 `src/constants/labels.js`。
