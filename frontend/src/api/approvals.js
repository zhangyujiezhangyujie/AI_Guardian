import http from './http'

/** GET /api/approvals（契约 7.11，P1 v1.4）。status 为空时返回全部（最多 100 条）。 */
export function listApprovals(status) {
  return http.get('/approvals', {
    params: status ? { status } : {}
  })
}

/** POST /api/approvals/{id}/approve。comment 可空。 */
export function approveApproval(approvalId, demoUser, comment) {
  return http.post(`/approvals/${encodeURIComponent(approvalId)}/approve`, { comment: comment || null }, {
    headers: {
      'X-Demo-User': demoUser
    }
  })
}

/** POST /api/approvals/{id}/reject。comment 可空。 */
export function rejectApproval(approvalId, demoUser, comment) {
  return http.post(`/approvals/${encodeURIComponent(approvalId)}/reject`, { comment: comment || null }, {
    headers: {
      'X-Demo-User': demoUser
    }
  })
}
