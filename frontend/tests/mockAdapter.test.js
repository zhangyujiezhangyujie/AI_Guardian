import { describe, expect, it, vi } from 'vitest'
import axios from 'axios'

vi.mock('../src/mock/index.js', () => ({
  USE_MOCK: true,
  MOCK_DELAY_MS: 0
}))

const { mockAdapter } = await import('../src/mock/adapter')
const { normalizeError } = await import('../src/api/errors')

const client = axios.create({
  baseURL: '/api',
  adapter: mockAdapter
})

describe('Mock 适配器', () => {
  it('已配置的接口返回 Mock 数据', async () => {
    const response = await client.get('/guard/config')

    expect(response.status).toBe(200)
    expect(response.data.availableStrategies).toEqual(['annotate', 'block'])
  })

  it('每次返回深拷贝，页面修改不影响 Mock 源数据', async () => {
    const first = await client.get('/guard/config')
    first.data.availableStrategies.push('changed')

    const second = await client.get('/guard/config')
    expect(second.data.availableStrategies).toEqual(['annotate', 'block'])
  })

  it('未配置的接口以 404 错误响应抛出，可被统一错误处理识别', async () => {
    const error = await client.get('/not-a-contract-api').catch((e) => e)

    expect(error.response.status).toBe(404)
    expect(normalizeError(error)).toMatchObject({
      errorCode: 'MOCK_NOT_CONFIGURED',
      httpStatus: 404
    })
    expect(normalizeError(error).message).toContain('GET /not-a-contract-api')
  })
})
