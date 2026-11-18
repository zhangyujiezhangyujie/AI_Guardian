import http from './http'

/** 去掉空值，避免把 requestId= 这类空参数发给后端。 */
function compactParams(params) {
  const result = {}
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') {
      return
    }
    result[key] = value
  })
  return result
}

/**
 * GET /api/audit（契约 7.8）。
 * params：page、size、requestId、state、action、mode、from、to（from/to 为 ISO-8601 带偏移字符串，axios 会负责 URL 编码）。
 */
export function listAudits(params) {
  const query = compactParams(params)
  return http.get('/audit', {
    params: query
  })
}

/** GET /api/audit/{requestId}（契约 7.9）。 */
export function getAuditDetail(requestId) {
  return http.get(`/audit/${encodeURIComponent(requestId)}`)
}
