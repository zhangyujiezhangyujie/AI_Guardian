import { AxiosError } from 'axios'

import { MOCK_DELAY_MS } from './index'
import { mockRoutes } from './routes'

/**
 * axios Mock 适配器：按 method + 路径匹配 mock/routes.js 中的路由。
 *
 * 路由处理函数接收 { params, body, match }，返回 { status, data }：
 * - status 为 2xx 时按成功响应返回；
 * - 其他 status 按 HTTP 错误抛出，data 应为契约 3.8 的错误体，由 http.js 拦截器统一处理。
 *
 * 未配置的接口返回 404 及说明文字，提醒补充 Mock 数据。
 */

function parseBody(data) {
  if (typeof data !== 'string' || data === '') {
    return data ?? null
  }
  try {
    return JSON.parse(data)
  } catch (e) {
    return data
  }
}

function findRoute(method, path) {
  for (const route of mockRoutes) {
    if (route.method !== method) {
      continue
    }
    const match = path.match(route.pattern)
    if (match) {
      return {
        route,
        match
      }
    }
  }
  return null
}

function notConfigured(method, path) {
  return {
    status: 404,
    data: {
      requestId: null,
      errorCode: 'MOCK_NOT_CONFIGURED',
      message: `前端 Mock 未配置该接口：${method.toUpperCase()} ${path}`,
      timestamp: new Date().toISOString()
    }
  }
}

function delay(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms)
  })
}

export async function mockAdapter(config) {
  const method = (config.method || 'get').toLowerCase()
  const path = config.url.split('?')[0]

  await delay(MOCK_DELAY_MS)

  const found = findRoute(method, path)
  const result = found
    ? found.route.handler({
      params: config.params || {},
      body: parseBody(config.data),
      match: found.match
    })
    : notConfigured(method, path)

  const response = {
    // 深拷贝，避免页面修改共享的 Mock 对象
    data: structuredClone(result.data),
    status: result.status,
    statusText: String(result.status),
    headers: {},
    config,
    request: {}
  }

  if (result.status >= 200 && result.status < 300) {
    return response
  }

  throw new AxiosError(
    `Request failed with status code ${result.status}`,
    AxiosError.ERR_BAD_RESPONSE,
    config,
    response.request,
    response
  )
}
