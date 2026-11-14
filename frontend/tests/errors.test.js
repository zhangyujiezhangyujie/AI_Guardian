import { describe, expect, it } from 'vitest'

import { MSG_NETWORK, MSG_TIMEOUT, normalizeError } from '../src/api/errors'

function httpError(status, data) {
  return {
    response: {
      status,
      data
    }
  }
}

describe('normalizeError', () => {
  it('使用契约 3.8 错误体中的 errorCode、message、requestId', () => {
    const result = normalizeError(httpError(502, {
      requestId: 'req_3f9a1c0b7d2e',
      errorCode: 'LLM_GENERATION_FAILED',
      message: '大模型生成回答失败，请稍后重试。',
      timestamp: '2026-11-20T14:03:21.512+08:00'
    }))

    expect(result).toEqual({
      errorCode: 'LLM_GENERATION_FAILED',
      message: '大模型生成回答失败，请稍后重试。',
      requestId: 'req_3f9a1c0b7d2e',
      httpStatus: 502
    })
  })

  it('参数校验失败时 requestId 为 null', () => {
    const result = normalizeError(httpError(400, {
      requestId: null,
      errorCode: 'INVALID_REQUEST',
      message: '请求参数不正确：question 不能为空',
      timestamp: '2026-11-20T14:03:21.512+08:00'
    }))

    expect(result.errorCode).toBe('INVALID_REQUEST')
    expect(result.requestId).toBeNull()
    expect(result.httpStatus).toBe(400)
  })

  it('超时提示“请求超时，请稍后重试”', () => {
    const result = normalizeError({
      code: 'ECONNABORTED'
    })

    expect(result.message).toBe(MSG_TIMEOUT)
    expect(result.errorCode).toBeNull()
  })

  it('没有响应（网络不通）提示“无法连接后端服务”', () => {
    const result = normalizeError({
      code: 'ERR_NETWORK'
    })

    expect(result.message).toBe(MSG_NETWORK)
    expect(result.errorCode).toBeNull()
    expect(result.httpStatus).toBeNull()
  })

  it('后端未启动时 Vite 代理返回空响应体的 500，提示“无法连接后端服务”', () => {
    const result = normalizeError(httpError(500, ''))

    expect(result.message).toBe(MSG_NETWORK)
    expect(result.httpStatus).toBe(500)
  })

  it('非契约格式的错误体给出通用提示，不伪造错误码', () => {
    const result = normalizeError(httpError(404, {
      timestamp: '2026-11-20T06:03:21.512+00:00',
      status: 404,
      error: 'Not Found',
      path: '/api/audit'
    }))

    expect(result).toEqual({
      errorCode: null,
      message: '请求失败（HTTP 404）',
      requestId: null,
      httpStatus: 404
    })
  })

  it('error 为空时也不抛异常', () => {
    expect(normalizeError(undefined).message).toBe(MSG_NETWORK)
  })
})
