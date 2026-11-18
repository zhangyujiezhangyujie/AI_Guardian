import http from './http'

/** GET /api/guard/config（契约 7.2）。 */
export function getGuardConfig() {
  return http.get('/guard/config')
}
