import http from './http'

/** GET /api/demo/scenarios（契约 7.6）。 */
export function listScenarios() {
  return http.get('/demo/scenarios')
}

/**
 * POST /api/demo/run（契约 7.7）。
 * payload：{ scenarioId, mode, guardStrategy, blockThreshold, verifier }，可空字段传 null。
 */
export function runScenario(payload) {
  return http.post('/demo/run', payload)
}
