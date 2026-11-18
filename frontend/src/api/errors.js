/**
 * 把 axios 错误统一转换为页面可用的错误对象（任务卡 6.3）。
 *
 * 返回结构：{ errorCode, message, requestId, httpStatus }
 * - errorCode / message / requestId 取自契约 3.8 的错误响应体；
 * - 网络不通、超时等前端侧错误没有契约错误码，errorCode 为 null；
 * - httpStatus 为前端内部字段，便于排查，不属于契约。
 *
 * 本文件不依赖 Element Plus，便于单元测试。
 */

export const MSG_NETWORK = '无法连接后端服务'

export const MSG_TIMEOUT = '请求超时，请稍后重试'

const TIMEOUT_CODES = ['ECONNABORTED', 'ETIMEDOUT']

function isContractErrorBody(data) {
  return data !== null
    && typeof data === 'object'
    && typeof data.errorCode === 'string'
}

function isEmptyBody(data) {
  if (data === undefined || data === null) {
    return true
  }
  if (typeof data === 'string') {
    return data.trim() === ''
  }
  return false
}

export function normalizeError(error) {
  if (error && TIMEOUT_CODES.includes(error.code)) {
    return {
      errorCode: null,
      message: MSG_TIMEOUT,
      requestId: null,
      httpStatus: null
    }
  }

  const response = error ? error.response : undefined

  if (!response) {
    return {
      errorCode: null,
      message: MSG_NETWORK,
      requestId: null,
      httpStatus: null
    }
  }

  const status = response.status
  const data = response.data

  if (isContractErrorBody(data)) {
    return {
      errorCode: data.errorCode,
      message: data.message || `请求失败（HTTP ${status}）`,
      requestId: data.requestId ?? null,
      httpStatus: status
    }
  }

  // 后端未启动时，Vite 代理返回 5xx 且响应体为空；该状态码来自代理而非后端，不展示
  if (status >= 500 && isEmptyBody(data)) {
    return {
      errorCode: null,
      message: MSG_NETWORK,
      requestId: null,
      httpStatus: null
    }
  }

  return {
    errorCode: null,
    message: `请求失败（HTTP ${status}）`,
    requestId: null,
    httpStatus: status
  }
}
