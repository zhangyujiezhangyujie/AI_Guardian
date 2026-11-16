import http from './http'

/** GET /api/knowledge/documents（契约 7.3）。 */
export function listDocuments() {
  return http.get('/knowledge/documents')
}

/** GET /api/knowledge/documents/{documentId}（契约 7.4）。 */
export function getDocument(documentId) {
  return http.get(`/knowledge/documents/${encodeURIComponent(documentId)}`)
}

/**
 * GET /api/knowledge/evidence/{evidenceId}（契约 7.5）。
 * options 透传给 axios，例如 { silent: true } 时失败不弹提示。
 */
export function getEvidence(evidenceId, options = {}) {
  return http.get(`/knowledge/evidence/${encodeURIComponent(evidenceId)}`, options)
}
