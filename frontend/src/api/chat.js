import http from './http'

/** POST /api/chat（契约 7.1）。sessionId 首次为 null，question 由页面保证 1–500 字。 */
export function sendQuestion(sessionId, question) {
  return http.post('/chat', {
    sessionId,
    question
  })
}
