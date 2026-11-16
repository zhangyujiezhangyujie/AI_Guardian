import axios from 'axios'
import { ElMessage } from 'element-plus'

import { normalizeError } from './errors'
import { USE_MOCK } from '@/mock'
import { mockAdapter } from '@/mock/adapter'

/**
 * axios 实例（任务卡 6.3）。
 *
 * - 成功：直接返回响应体（response.data），页面拿到的就是契约中的 JSON。
 * - 失败：弹出 ElMessage 错误提示，并以 { errorCode, message, requestId, httpStatus } 作为 rejected 的值。
 * - 单个请求可传 { silent: true } 不弹提示，由页面自行处理（如证据抽屉的兜底查询）。
 * - Mock 模式（VITE_USE_MOCK=true）下替换 adapter，不发出真实请求。
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 120000
})

if (USE_MOCK) {
  http.defaults.adapter = mockAdapter
}

http.interceptors.response.use(
  (response) => {
    return response.data
  },
  (error) => {
    const normalized = normalizeError(error)
    const silent = Boolean(error && error.config && error.config.silent)

    if (!silent) {
      ElMessage.error(normalized.message)
    }

    return Promise.reject(normalized)
  }
)

export default http
