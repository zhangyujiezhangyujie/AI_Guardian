/**
 * 前端 Mock 开关（任务卡 6.4）。
 *
 * 仅在后端未完成时用于独立开发：`npm run dev:mock` 会加载 .env.mock，令 VITE_USE_MOCK=true。
 * 答辩和联调时用 `npm run dev`，此开关为 false，所有请求都发往真实后端。
 *
 * 开启后 http.js 会把 axios 的 adapter 换成 mock/adapter.js：
 * API 函数本身不区分 Mock 与真实，Mock 响应（包括错误响应）同样经过统一的响应拦截器。
 */
export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

/** 每次 Mock 请求的模拟延迟（任务卡 6.4：1 秒）。 */
export const MOCK_DELAY_MS = 1000
