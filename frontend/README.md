# AI Guardian 前端（成员 5）

Vue 3 + Vite + Element Plus + axios + vue-router。规格见任务卡 `06_任务卡_成员5_前端与演示.md` 与接口契约第 3、6、7 章。

## 运行

需要 Node 18+（开发时使用 Node 22）。

```bash
cd frontend
npm ci            # 按 package-lock.json 安装
npm run dev       # 联调 / 答辩：请求经 Vite 代理 /api → http://localhost:8080
npm run dev:mock  # 后端未就绪时独立开发：加载 .env.mock，VITE_USE_MOCK=true
npm run build     # 产物在 dist/
```

开发服务器端口 5173。后端用 Stub 模式启动即可联调：`cd backend && mvn spring-boot:run '-Dspring-boot.run.profiles=stub'`。

Mock 模式下页面顶部会显示橙色标签“前端 Mock 模式（答辩前必须关闭）”。**答辩时必须使用 `npm run dev`**（任务卡第 8 节第 4 条）。

## 路由

| 路由 | 页面 |
| --- | --- |
| `/` | 重定向到 `/chat` |
| `/chat` | 问答核验 |
| `/audit` | 审计日志列表 |
| `/audit/:requestId` | 审计详情 |
| `/demo` | 演示场景 |

## 约定

- 禁止使用 `v-html` 渲染任何模型输出。
- 前端只展示后端给出的 `status`、`guard.action`、`guard.answerVisible`、`guard.message`，不自行判断结论真假或计算拦截比例。
- 枚举到中文文案、颜色的映射集中在 `src/constants/labels.js`。
