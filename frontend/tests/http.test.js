import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError } from 'axios'

const elMessageError = vi.fn()

vi.mock('element-plus', () => ({
  ElMessage: {
    error: (...args) => elMessageError(...args)
  }
}))

const { default: http } = await import('../src/api/http')

function respond(status, data) {
  return async (config) => {
    const response = {
      data,
      status,
      statusText: String(status),
      headers: {},
      config,
      request: {}
    }
    if (status >= 200 && status < 300) {
      return response
    }
    throw new AxiosError('fail', AxiosError.ERR_BAD_RESPONSE, config, {}, response)
  }
}

describe('http 拦截器', () => {
  const originalAdapter = http.defaults.adapter

  beforeEach(() => {
    elMessageError.mockClear()
  })

  afterEach(() => {
    http.defaults.adapter = originalAdapter
  })

  it('baseURL 为 /api，超时 120 秒', () => {
    expect(http.defaults.baseURL).toBe('/api')
    expect(http.defaults.timeout).toBe(120000)
  })

  it('成功时直接返回响应体', async () => {
    http.defaults.adapter = respond(200, { strategy: 'annotate' })

    await expect(http.get('/guard/config')).resolves.toEqual({ strategy: 'annotate' })
  })

  it('失败时弹出 message，并以规范化错误对象 reject', async () => {
    http.defaults.adapter = respond(502, {
      requestId: 'req_3f9a1c0b7d2e',
      errorCode: 'LLM_GENERATION_FAILED',
      message: '大模型生成回答失败，请稍后重试。',
      timestamp: '2026-11-20T14:03:21.512+08:00'
    })

    await expect(http.post('/chat', {})).rejects.toEqual({
      errorCode: 'LLM_GENERATION_FAILED',
      message: '大模型生成回答失败，请稍后重试。',
      requestId: 'req_3f9a1c0b7d2e',
      httpStatus: 502
    })
    expect(elMessageError).toHaveBeenCalledWith('大模型生成回答失败，请稍后重试。')
  })

  it('silent 请求失败时不弹提示', async () => {
    http.defaults.adapter = respond(404, {
      requestId: null,
      errorCode: 'EVIDENCE_NOT_FOUND',
      message: '证据片段不存在。',
      timestamp: '2026-11-20T14:03:21.512+08:00'
    })

    await expect(http.get('/knowledge/evidence/doc009_chunk01', { silent: true }))
      .rejects.toMatchObject({ errorCode: 'EVIDENCE_NOT_FOUND' })
    expect(elMessageError).not.toHaveBeenCalled()
  })
})
