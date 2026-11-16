import { readFileSync, readdirSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

import { CASES, GENERATION_FAILED_CASE } from '../src/mock/fixtures/cases'
import { buildExchange, buildGenerationFailure } from '../src/mock/fixtures/builder'
import { evidenceById } from '../src/mock/fixtures/knowledge'

/**
 * 校验 Mock 数据符合契约，避免前端按错误的数据开发。
 * 期望值来自契约 3.x、5.3、6.1–6.3 与任务卡 6.4，不得为了通过测试而修改。
 */

const EXAMPLES_DIR = new URL('../../docs/examples/', import.meta.url)

const backendExamples = readdirSync(EXAMPLES_DIR)
  .filter((name) => name.endsWith('.json'))
  .map((name) => ({
    name,
    data: JSON.parse(readFileSync(new URL(name, EXAMPLES_DIR), 'utf8'))
  }))

const ALL = []
Object.entries(CASES).forEach(([key, def]) => {
  Object.keys(def.guards).forEach((strategy) => {
    ALL.push({
      label: `${key}/${strategy}`,
      key,
      strategy,
      def,
      ...buildExchange(def, { strategy })
    })
  })
})

function keys(obj) {
  return Object.keys(obj).sort()
}

describe('与成员 1 后端示例（docs/examples）字段一致', () => {
  it('能读到后端示例', () => {
    expect(backendExamples.length).toBeGreaterThanOrEqual(5)
  })

  const sample = backendExamples[0]

  ALL.forEach(({ label, response }) => {
    it(`${label} 的字段集合与后端示例相同`, () => {
      const ref = sample.data
      expect(keys(response)).toEqual(keys(ref))
      expect(keys(response.verification)).toEqual(keys(ref.verification))
      expect(keys(response.guard)).toEqual(keys(ref.guard))
      expect(keys(response.timing)).toEqual(keys(ref.timing))
      const refEvidence = backendExamples.flatMap((e) => e.data.evidence)[0]
      response.evidence.forEach((e) => {
        expect(keys(e)).toEqual(keys(refEvidence))
      })
      const refClaim = backendExamples.flatMap((e) => e.data.verification.claims)[0]
      response.verification.claims.forEach((c) => {
        expect(keys(c)).toEqual(keys(refClaim))
      })
    })
  })
})

describe('Mock 响应符合契约规则', () => {
  ALL.forEach(({ label, def, response, record }) => {
    it(`${label}`, () => {
      const v = response.verification
      const g = response.guard
      const raw = record.verification

      // 3.7 ID 与时间格式
      expect(response.requestId).toMatch(/^req_[0-9a-f]{12}$/)
      expect(response.sessionId).toMatch(/^ses_[0-9a-f]{12}$/)
      expect(response.createdAt).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}\+08:00$/)
      expect(v.requestId).toBe(response.requestId)

      // 3.4 计数字段 = recalculate()
      const supported = raw.claims.filter((c) => c.status === 'SUPPORTED').length
      expect(raw.totalClaims).toBe(raw.claims.length)
      expect(raw.supportedClaims).toBe(supported)
      expect(raw.insufficientClaims).toBe(raw.claims.length - supported)
      if (raw.totalClaims === 0) {
        expect(raw.unsupportedRatio).toBeNull()
      } else {
        expect(raw.unsupportedRatio).toBe(Math.round((raw.insufficientClaims / raw.totalClaims) * 10000) / 10000)
      }
      expect(raw.claims.length).toBeLessThanOrEqual(8)
      expect(g.unsupportedRatio).toBe(raw.unsupportedRatio)

      // 3.4 / 5.5 状态与截断
      expect(raw.truncated).toBe(raw.verificationState === 'PARTIAL')
      expect(raw.failureReason !== null).toBe(raw.verificationState === 'FAILED')
      if (['FAILED', 'NO_CLAIMS', 'NO_EVIDENCE'].includes(raw.verificationState)) {
        expect(raw.claims).toEqual([])
      }
      if (raw.verificationState === 'NO_EVIDENCE') {
        expect(response.evidence).toEqual([])
        expect(raw.answer).toBeNull()
        expect(raw.verifierName).toBe('none')
        expect(response.timing.generationMs).toBeNull()
        expect(response.timing.verificationMs).toBeNull()
      } else {
        expect(response.evidence.length).toBeGreaterThan(0)
        expect(response.evidence.length).toBeLessThanOrEqual(3)
      }

      // 3.2 证据按排名，rank 从 1 开始
      response.evidence.forEach((e, i) => {
        expect(e.rank).toBe(i + 1)
        expect(e.evidenceId).toBe(`${e.documentId}_chunk${e.evidenceId.slice(-2)}`)
      })

      // 3.3 / 5.3 每条结论
      const evidenceIds = response.evidence.map((e) => e.evidenceId)
      raw.claims.forEach((c, i) => {
        expect(c.claimId).toBe(`c${i + 1}`)
        if (c.status === 'SUPPORTED') {
          expect(c.insufficientReason).toBeNull()
          expect(c.modelSupported).toBe(true)
          expect(c.quoteExists).toBe(true)
          expect(evidenceIds).toContain(c.evidenceId)
          expect(evidenceById(c.evidenceId).content).toContain(c.quote)
        } else {
          expect(c.insufficientReason).not.toBeNull()
        }
        if (c.insufficientReason === 'MODEL_UNSUPPORTED') {
          expect(c.modelSupported).toBe(false)
          expect(c.quoteExists).toBeNull()
        }
        if (c.insufficientReason === 'EVIDENCE_NOT_IN_CONTEXT') {
          expect(evidenceIds).not.toContain(c.evidenceId)
          expect(c.quoteExists).toBe(false)
        }
        if (c.insufficientReason === 'QUOTE_NOT_FOUND') {
          expect(evidenceIds).toContain(c.evidenceId)
          expect(evidenceById(c.evidenceId).content).not.toContain(c.quote)
          expect(c.quoteExists).toBe(false)
        }
        if (c.insufficientReason === 'QUOTE_TOO_SHORT') {
          expect(c.quote.length).toBeLessThan(6)
          expect(c.quoteExists).toBe(false)
        }
        // 下标：Java String.indexOf 语义
        if (c.startIndex === null) {
          expect(raw.answer.indexOf(c.originalText)).toBe(-1)
          expect(c.endIndex).toBeNull()
        } else {
          expect(raw.answer.slice(c.startIndex, c.endIndex)).toBe(c.originalText)
        }
      })

      // 6.1 / 6.2 展示与遮蔽
      const hidden = g.action === 'BLOCKED' || !g.answerVisible
      if (hidden) {
        expect(g.displayAnswer).toBeNull()
        expect(v.answer).toBeNull()
        v.claims.forEach((c, i) => {
          expect(c.originalText).toBeNull()
          expect(c.startIndex).toBeNull()
          expect(c.endIndex).toBeNull()
          // 保留用于解释的字段
          expect(c.claim).toBe(raw.claims[i].claim)
          expect(c.status).toBe(raw.claims[i].status)
          expect(c.evidenceId).toBe(raw.claims[i].evidenceId)
          expect(c.quote).toBe(raw.claims[i].quote)
          expect(c.insufficientReason).toBe(raw.claims[i].insufficientReason)
        })
      } else {
        expect(v).toEqual(raw)
        if (g.action === 'NO_EVIDENCE') {
          expect(g.displayAnswer).toBe('知识库中没有找到与该问题相关的资料，系统未生成回答。')
        } else {
          expect(g.displayAnswer).toBe(raw.answer)
        }
      }

      // 审计记录未遮蔽
      expect(record.rawAnswer).toBe(def.answer)
      expect(record.guard.answerVisible).toBeNull()
      expect(record.guard.displayAnswer).toBeNull()
      expect(record.guard.message).toBeNull()
    })
  })
})

describe('6.1 判定表：处理结果与策略、状态一致', () => {
  ALL.forEach(({ label, strategy, response }) => {
    it(`${label}`, () => {
      const state = response.verification.verificationState
      const g = response.guard
      const expectedByState = {
        NO_EVIDENCE: ['NO_EVIDENCE'],
        FAILED: ['NOT_VERIFIED'],
        NO_CLAIMS: ['NO_CLAIMS'],
        COMPLETED: strategy === 'annotate' ? ['ANNOTATED'] : ['ANNOTATED', 'BLOCKED'],
        PARTIAL: strategy === 'annotate' ? ['ANNOTATED'] : ['ANNOTATED', 'BLOCKED']
      }
      expect(expectedByState[state]).toContain(g.action)
      if (state === 'FAILED') {
        expect(g.answerVisible).toBe(strategy === 'annotate')
      }
      if (g.action === 'BLOCKED') {
        expect(g.answerVisible).toBe(false)
      }
    })
  })
})

describe('任务卡 6.4：至少 8 种 ChatResponse', () => {
  const find = (key, strategy) => ALL.find((x) => x.key === key && x.strategy === strategy).response

  it('全部有证据：ANNOTATED + M1', () => {
    const r = find('allSupported', 'annotate')
    expect(r.guard.action).toBe('ANNOTATED')
    expect(r.verification.insufficientClaims).toBe(0)
    expect(r.guard.message).toBe('回答中的全部结论均有知识库证据支持。')
  })

  it('部分无证据：ANNOTATED + M2，与契约 3.7 示例一致', () => {
    const r = find('partial', 'annotate')
    expect(r.guard.message).toBe('部分结论未在知识库中找到充分证据，已用黄色标出。')
    expect(r.verification.unsupportedRatio).toBe(0.5)
    expect(r.verification.claims.map((c) => [c.startIndex, c.endIndex])).toEqual([[0, 11], [12, 26]])
  })

  it('截断：PARTIAL + M2P', () => {
    const r = find('truncated', 'annotate')
    expect(r.verification.verificationState).toBe('PARTIAL')
    expect(r.verification.totalClaims).toBe(8)
    expect(r.guard.message).toBe('回答包含的结论较多，仅核验了前 8 条，其余内容未经核验。')
  })

  it('BLOCKED：M6，answer 与 originalText、下标为 null', () => {
    const r = find('blocked', 'block')
    expect(r.guard.action).toBe('BLOCKED')
    expect(r.guard.message).toBe('当前回答中有较多结论缺乏充分证据，系统已限制输出，请核对原始资料。')
    expect(r.verification.unsupportedRatio).toBe(0.6667)
    expect(r.verification.answer).toBeNull()
    expect(r.verification.claims).toHaveLength(3)
  })

  it('NOT_VERIFIED（annotate）：M3，展示原回答', () => {
    const r = find('notVerified', 'annotate')
    expect(r.guard.answerVisible).toBe(true)
    expect(r.guard.message).toBe('核验未完成，以下回答未经证据核验，请谨慎参考。')
  })

  it('NOT_VERIFIED（block）：M4，不展示', () => {
    const r = find('notVerified', 'block')
    expect(r.guard.answerVisible).toBe(false)
    expect(r.guard.message).toBe('核验未完成，为避免输出未经核验的内容，系统已暂停展示回答。')
    expect(r.verification.answer).toBeNull()
  })

  it('NO_EVIDENCE：固定文案 N1，message 为 null（与后端示例一致）', () => {
    const r = find('noEvidence', 'annotate')
    expect(r.guard.action).toBe('NO_EVIDENCE')
    expect(r.guard.message).toBeNull()
  })

  it('NO_CLAIMS：M5', () => {
    const r = find('noClaims', 'annotate')
    expect(r.guard.message).toBe('回答中没有需要核验的事实性结论。')
  })

  it('SC04：c1 证据编号不在结果中，c2 引用不存在，c3 有证据', () => {
    const r = find('citationInvalid', 'annotate')
    expect(r.verification.claims.map((c) => c.insufficientReason)).toEqual(['EVIDENCE_NOT_IN_CONTEXT', 'QUOTE_NOT_FOUND', null])
    expect(find('citationInvalid', 'block').guard.action).toBe('BLOCKED')
  })

  it('XSS 用例原样包含 HTML', () => {
    const r = find('xss', 'annotate')
    expect(r.guard.displayAnswer).toContain('<img src=x onerror=alert(1)>')
    expect(r.verification.claims[0].claim).toBe('<img src=x onerror=alert(1)>')
  })

  it('长回答不少于 1000 字，包含重叠结论和定位不到的结论', () => {
    const r = find('long', 'annotate')
    expect(r.guard.displayAnswer.length).toBeGreaterThanOrEqual(1000)
    const claims = r.verification.claims
    const c4 = claims[3]
    const c5 = claims[4]
    expect(c5.startIndex).toBeGreaterThanOrEqual(c4.startIndex)
    expect(c5.startIndex).toBeLessThan(c4.endIndex)
    expect(claims[6].startIndex).toBeNull()
  })
})

describe('SC09 生成失败', () => {
  it('502 + LLM_GENERATION_FAILED，审计记录 verification、guard 为 null', () => {
    const failure = buildGenerationFailure(GENERATION_FAILED_CASE, { mode: 'MOCK', scenarioId: 'SC09' })
    expect(failure.status).toBe(502)
    expect(failure.error.errorCode).toBe('LLM_GENERATION_FAILED')
    expect(failure.error.requestId).toBe(failure.record.requestId)
    expect(failure.record.verification).toBeNull()
    expect(failure.record.guard).toBeNull()
    expect(failure.record.errorCode).toBe('LLM_GENERATION_FAILED')
    expect(failure.record.events.map((e) => e.eventType)).toEqual([
      'REQUEST_RECEIVED',
      'RETRIEVAL_DONE',
      'GENERATION_FAILED',
      'REQUEST_FAILED'
    ])
  })
})

describe('审计事件（契约 8.2）', () => {
  const DETAIL_KEYS = {
    REQUEST_RECEIVED: ['mode', 'questionLength', 'scenarioId'],
    RETRIEVAL_DONE: ['evidenceIds', 'hitCount', 'topK', 'topScore'],
    GENERATION_DONE: ['answerLength'],
    VERIFICATION_DONE: ['failureReason', 'llmCalls', 'state', 'totalClaims', 'verifierName'],
    CITATION_CHECKED: ['checkedCount', 'failedReasons', 'passedCount'],
    GUARD_APPLIED: ['action', 'blockThreshold', 'overridden', 'strategy', 'unsupportedRatio'],
    REQUEST_COMPLETED: ['action', 'totalMs']
  }

  ALL.forEach(({ label, record }) => {
    it(`${label} 的事件 detail 键名与顺序`, () => {
      const types = record.events.map((e) => e.eventType)
      expect(types[0]).toBe('REQUEST_RECEIVED')
      expect(types[types.length - 1]).toBe('REQUEST_COMPLETED')
      record.events.forEach((e) => {
        expect(keys(e.detail)).toEqual(DETAIL_KEYS[e.eventType])
      })
      const times = record.events.map((e) => Date.parse(e.createdAt))
      expect([...times].sort((a, b) => a - b)).toEqual(times)
      const state = record.verification.verificationState
      expect(types.includes('CITATION_CHECKED')).toBe(state === 'COMPLETED' || state === 'PARTIAL')
    })
  })
})
