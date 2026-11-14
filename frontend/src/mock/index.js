/**
 * 前端 Mock 开关（任务卡 6.4）。
 *
 * 仅在后端未完成时用于独立开发：`npm run dev:mock` 会加载 .env.mock，令 VITE_USE_MOCK=true。
 * 答辩和联调时用 `npm run dev`，此开关为 false，所有请求都发往真实后端。
 */
export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

const DEFAULT_DELAY_MS = 1000

/**
 * 模拟一次成功的接口调用：延迟后返回数据的深拷贝，避免页面修改共享的 Mock 对象。
 * 返回值与真实 API 函数一致（即响应体本身，而不是 axios 的 response 对象）。
 */
export function mockResolve(data, delayMs = DEFAULT_DELAY_MS) {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve(structuredClone(data))
    }, delayMs)
  })
}

/**
 * 模拟一次失败的接口调用：rejected 的值与 http.js 拦截器抛出的结构一致，
 * 即 { errorCode, message, requestId }。
 */
export function mockReject(error, delayMs = DEFAULT_DELAY_MS) {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      reject(structuredClone(error))
    }, delayMs)
  })
}
