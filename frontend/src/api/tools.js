import http from './http'

/**
 * POST /api/tools/execute（契约 7.10，P1）。
 * demoUser 作为请求头 X-Demo-User（user / manager / admin），仅用于演示，不是真实身份认证。
 */
export function executeTool(payload, demoUser) {
  return http.post('/tools/execute', payload, {
    headers: {
      'X-Demo-User': demoUser
    }
  })
}
