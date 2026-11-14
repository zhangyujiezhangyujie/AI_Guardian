import { describe, expect, it, vi } from 'vitest'
import axios from 'axios'

vi.mock('../src/mock/index.js', () => ({
  USE_MOCK: true,
  MOCK_DELAY_MS: 0
}))

const { mockAdapter } = await import('../src/mock/adapter')

const client = axios.create({
  baseURL: '/api',
  adapter: mockAdapter
})

async function call(promise) {
  try {
    const response = await promise
    return {
      status: response.status,
      data: response.data
    }
  } catch (error) {
    return {
      status: error.response.status,
      data: error.response.data
    }
  }
}

describe('POST /chat', () => {
  it('按关键词返回用例，question 与 sessionId 取请求值', async () => {
    const r = await call(client.post('/chat', { sessionId: 'ses_0123456789ab', question: '  公司员工有哪些福利？ ' }))
    expect(r.status).toBe(200)
    expect(r.data.question).toBe('公司员工有哪些福利？')
    expect(r.data.sessionId).toBe('ses_0123456789ab')
    expect(r.data.mode).toBe('REAL')
    expect(r.data.scenarioId).toBeNull()
    expect(r.data.guard.action).toBe('ANNOTATED')
  })

  it('sessionId 为 null 时新建会话', async () => {
    const r = await call(client.post('/chat', { sessionId: null, question: '年假有几天' }))
    expect(r.data.sessionId).toMatch(/^ses_[0-9a-f]{12}$/)
    expect(r.data.verification.insufficientClaims).toBe(0)
  })

  it('“拦截”关键词返回 BLOCKED', async () => {
    const r = await call(client.post('/chat', { sessionId: null, question: '演示拦截' }))
    expect(r.data.guard.action).toBe('BLOCKED')
    expect(r.data.verification.answer).toBeNull()
  })

  it('未匹配关键词返回 NO_EVIDENCE', async () => {
    const r = await call(client.post('/chat', { sessionId: null, question: '今天天气怎么样' }))
    expect(r.data.guard.action).toBe('NO_EVIDENCE')
  })

  it('“生成失败”返回 502 契约错误体，且可在审计中查到', async () => {
    const r = await call(client.post('/chat', { sessionId: null, question: '模拟生成失败' }))
    expect(r.status).toBe(502)
    expect(r.data.errorCode).toBe('LLM_GENERATION_FAILED')
    expect(r.data.requestId).toMatch(/^req_[0-9a-f]{12}$/)
    const audit = await call(client.get(`/audit/${r.data.requestId}`))
    expect(audit.status).toBe(200)
    expect(audit.data.verification).toBeNull()
  })

  it('空白问题返回 400 INVALID_REQUEST', async () => {
    const r = await call(client.post('/chat', { sessionId: null, question: '   ' }))
    expect(r.status).toBe(400)
    expect(r.data.errorCode).toBe('INVALID_REQUEST')
    expect(r.data.requestId).toBeNull()
  })

  it('超过 500 字返回 400', async () => {
    const r = await call(client.post('/chat', { sessionId: null, question: '字'.repeat(501) }))
    expect(r.status).toBe(400)
  })

  it('sessionId 格式错误返回 400', async () => {
    const r = await call(client.post('/chat', { sessionId: 'abc', question: '年假' }))
    expect(r.status).toBe(400)
  })

  it('问答后可按 requestId 查到未遮蔽的审计详情', async () => {
    const chat = await call(client.post('/chat', { sessionId: null, question: '演示拦截' }))
    const audit = await call(client.get(`/audit/${chat.data.requestId}`))
    expect(audit.data.rawAnswer).not.toBeNull()
    expect(audit.data.verification.answer).toBe(audit.data.rawAnswer)
    expect(audit.data.verification.claims[0].originalText).not.toBeNull()
  })
})

describe('知识库', () => {
  it('文档列表按 documentId 升序', async () => {
    const r = await call(client.get('/knowledge/documents'))
    const ids = r.data.map((d) => d.documentId)
    expect(ids).toEqual([...ids].sort())
    expect(r.data[0]).toEqual(expect.objectContaining({ fileName: 'doc001_员工手册.md' }))
  })

  it('福利规定中没有交通补贴（演示要点）', async () => {
    const r = await call(client.get('/knowledge/documents/doc005'))
    expect(r.data.content).not.toContain('交通补贴')
    expect(r.data.chunks.length).toBeGreaterThan(0)
  })

  it('单个片段 score、rank 为 0；不存在返回 404', async () => {
    const ok = await call(client.get('/knowledge/evidence/doc001_chunk03'))
    expect(ok.data.score).toBe(0)
    expect(ok.data.rank).toBe(0)
    const missing = await call(client.get('/knowledge/evidence/doc009_chunk01'))
    expect(missing.status).toBe(404)
    expect(missing.data.errorCode).toBe('EVIDENCE_NOT_FOUND')
    const doc = await call(client.get('/knowledge/documents/doc999'))
    expect(doc.data.errorCode).toBe('DOCUMENT_NOT_FOUND')
  })
})

describe('演示', () => {
  it('场景列表按编号升序，不暴露 Mock 内部字段', async () => {
    const r = await call(client.get('/demo/scenarios'))
    const ids = r.data.map((s) => s.scenarioId)
    expect(ids.filter((id) => id.startsWith('SC'))).toEqual(['SC01', 'SC02', 'SC03', 'SC04', 'SC05', 'SC06', 'SC07', 'SC08', 'SC09'])
    r.data.forEach((s) => {
      expect(Object.keys(s).sort()).toEqual(['description', 'name', 'priority', 'question', 'recommendedStrategies', 'scenarioId', 'supportsReal'])
    })
    expect(r.data.find((s) => s.scenarioId === 'SC06').priority).toBe('P1')
  })

  it('SC03：annotate 标注、block 拦截', async () => {
    const a = await call(client.post('/demo/run', { scenarioId: 'SC03', mode: 'MOCK', guardStrategy: 'annotate', blockThreshold: null, verifier: null }))
    const b = await call(client.post('/demo/run', { scenarioId: 'SC03', mode: 'MOCK', guardStrategy: 'block', blockThreshold: null, verifier: null }))
    expect(a.data.response.guard.action).toBe('ANNOTATED')
    expect(b.data.response.guard.action).toBe('BLOCKED')
    expect(b.data.response.scenarioId).toBe('SC03')
    expect(b.data.response.mode).toBe('MOCK')
    expect(b.data.check).toEqual({ checked: true, passed: true, mismatches: [] })
  })

  it('策略为空时使用默认 annotate', async () => {
    const r = await call(client.post('/demo/run', { scenarioId: 'SC05', mode: 'MOCK', guardStrategy: null, blockThreshold: null, verifier: null }))
    expect(r.data.response.guard.strategy).toBe('annotate')
    expect(r.data.response.guard.action).toBe('NOT_VERIFIED')
    expect(r.data.response.guard.answerVisible).toBe(true)
  })

  it('SC09：接口返回 200，response 为 null', async () => {
    const r = await call(client.post('/demo/run', { scenarioId: 'SC09', mode: 'MOCK', guardStrategy: null, blockThreshold: null, verifier: null }))
    expect(r.status).toBe(200)
    expect(r.data.response).toBeNull()
    expect(r.data.check.checked).toBe(true)
  })

  it('REAL 模式不比对', async () => {
    const r = await call(client.post('/demo/run', { scenarioId: 'SC01', mode: 'REAL', guardStrategy: null, blockThreshold: null, verifier: null }))
    expect(r.data.check).toEqual({ checked: false, passed: null, mismatches: [] })
    expect(r.data.response.mode).toBe('REAL')
  })

  it('MK04 返回不符合预期及 mismatches', async () => {
    const r = await call(client.post('/demo/run', { scenarioId: 'MK04', mode: 'MOCK', guardStrategy: null, blockThreshold: null, verifier: null }))
    expect(r.data.check.passed).toBe(false)
    expect(r.data.check.mismatches.length).toBeGreaterThan(0)
  })

  it('参数校验', async () => {
    const base = { scenarioId: 'SC01', mode: 'MOCK', guardStrategy: null, blockThreshold: null, verifier: null }
    expect((await call(client.post('/demo/run', { ...base, scenarioId: 'SC99' }))).data.errorCode).toBe('SCENARIO_NOT_FOUND')
    expect((await call(client.post('/demo/run', { ...base, guardStrategy: 'remove' }))).status).toBe(400)
    expect((await call(client.post('/demo/run', { ...base, blockThreshold: 1.5 }))).status).toBe(400)
    expect((await call(client.post('/demo/run', { ...base, verifier: 'batch' }))).status).toBe(400)
    expect((await call(client.post('/demo/run', { ...base, scenarioId: 'SC05', mode: 'REAL' }))).status).toBe(400)
  })
})

describe('GET /audit', () => {
  it('默认每页 20 条，按时间降序，预置记录超过一页', async () => {
    const r = await call(client.get('/audit', { params: { page: 1, size: 20 } }))
    expect(r.data.size).toBe(20)
    expect(r.data.items).toHaveLength(20)
    expect(r.data.total).toBeGreaterThan(20)
    const times = r.data.items.map((i) => Date.parse(i.createdAt))
    expect([...times].sort((a, b) => b - a)).toEqual(times)
  })

  it('第二页接着第一页', async () => {
    const p1 = await call(client.get('/audit', { params: { page: 1, size: 20 } }))
    const p2 = await call(client.get('/audit', { params: { page: 2, size: 20 } }))
    expect(p2.data.items.length).toBe(p1.data.total - 20)
  })

  it('按 action、mode、state 筛选', async () => {
    const r = await call(client.get('/audit', { params: { action: 'BLOCKED', mode: 'MOCK' } }))
    expect(r.data.total).toBeGreaterThan(0)
    r.data.items.forEach((i) => {
      expect(i.guardAction).toBe('BLOCKED')
      expect(i.mode).toBe('MOCK')
    })
    const failed = await call(client.get('/audit', { params: { state: 'FAILED' } }))
    failed.data.items.forEach((i) => {
      expect(i.verificationState).toBe('FAILED')
    })
  })

  it('按 requestId 精确匹配', async () => {
    const all = await call(client.get('/audit'))
    const target = all.data.items[3].requestId
    const r = await call(client.get('/audit', { params: { requestId: target } }))
    expect(r.data.total).toBe(1)
    expect(r.data.items[0].requestId).toBe(target)
  })

  it('时间范围 from 含、to 不含', async () => {
    const all = await call(client.get('/audit', { params: { size: 100 } }))
    const t = all.data.items[5].createdAt
    const from = await call(client.get('/audit', { params: { from: t, size: 100 } }))
    const to = await call(client.get('/audit', { params: { to: t, size: 100 } }))
    expect(from.data.items.map((i) => i.createdAt)).toContain(t)
    expect(to.data.items.map((i) => i.createdAt)).not.toContain(t)
    expect(from.data.total + to.data.total).toBe(all.data.total)
  })

  it('生成失败记录：verificationState、guardAction 为 null，计数为 0', async () => {
    const r = await call(client.get('/audit', { params: { size: 100 } }))
    const failed = r.data.items.find((i) => i.errorCode === 'LLM_GENERATION_FAILED')
    expect(failed.verificationState).toBeNull()
    expect(failed.guardAction).toBeNull()
    expect(failed.totalClaims).toBe(0)
  })

  it('不存在的 requestId 返回 404 AUDIT_NOT_FOUND', async () => {
    const r = await call(client.get('/audit/req_000000000000'))
    expect(r.status).toBe(404)
    expect(r.data.errorCode).toBe('AUDIT_NOT_FOUND')
  })
})
