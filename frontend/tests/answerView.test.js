import { describe, expect, it } from 'vitest'

import { resolveAnswerView } from '../src/utils/answerView'
import { formatSeconds } from '../src/utils/format'
import { CASES } from '../src/mock/fixtures/cases'
import { buildExchange } from '../src/mock/fixtures/builder'

function response(key, strategy) {
  return buildExchange(CASES[key], { strategy }).response
}

function view(key, strategy = 'annotate') {
  return resolveAnswerView(response(key, strategy))
}

describe('任务卡 6.5 展示规则表', () => {
  it('ANNOTATED 且全部有证据：绿色，高亮', () => {
    expect(view('allSupported')).toMatchObject({ bannerType: 'success', body: 'highlight', showClaims: true })
  })

  it('ANNOTATED 且有证据不足：黄色，高亮', () => {
    expect(view('partial')).toMatchObject({ bannerType: 'warning', body: 'highlight' })
  })

  it('ANNOTATED 且截断（即使不足为 0 也是黄色）', () => {
    const r = response('truncated', 'annotate')
    r.verification.insufficientClaims = 0
    expect(resolveAnswerView(r)).toMatchObject({ bannerType: 'warning', body: 'highlight' })
  })

  it('BLOCKED：红色，不显示正文，占位“回答已被拦截”，结论表仍显示', () => {
    expect(view('blocked', 'block')).toEqual({
      bannerType: 'error',
      body: 'hidden',
      placeholder: '回答已被拦截',
      showClaims: true
    })
  })

  it('NOT_VERIFIED 且可见：红色，不高亮（灰色边框）', () => {
    expect(view('notVerified', 'annotate')).toMatchObject({ bannerType: 'error', body: 'unverified' })
  })

  it('NOT_VERIFIED 且不可见：红色，占位“回答未经核验，已暂停展示”', () => {
    expect(view('notVerified', 'block')).toMatchObject({
      bannerType: 'error',
      body: 'hidden',
      placeholder: '回答未经核验，已暂停展示'
    })
  })

  it('NO_CLAIMS：灰蓝，不高亮', () => {
    expect(view('noClaims')).toMatchObject({ bannerType: 'info', body: 'plain' })
  })

  it('NO_EVIDENCE：灰蓝，不显示结论列表', () => {
    expect(view('noEvidence')).toMatchObject({ bannerType: 'info', body: 'plain', showClaims: false })
  })

  it('只看后端字段：即使比例很高，ANNOTATED 也不会被前端改成拦截', () => {
    const r = response('partial', 'annotate')
    r.verification.unsupportedRatio = 1
    r.guard.unsupportedRatio = 1
    r.guard.blockThreshold = 0
    expect(resolveAnswerView(r).body).toBe('highlight')
  })

  it('失败即关闭：answerVisible=false 时无论 action 是什么都不显示正文', () => {
    const r = response('partial', 'annotate')
    r.guard.answerVisible = false
    expect(resolveAnswerView(r).body).toBe('hidden')
    r.guard.action = 'SOMETHING_NEW'
    expect(resolveAnswerView(r).body).toBe('hidden')
  })
})

describe('formatSeconds', () => {
  it('毫秒转秒保留 1 位小数，null 显示“—”', () => {
    expect(formatSeconds(41)).toBe('0.0 秒')
    expect(formatSeconds(3812)).toBe('3.8 秒')
    expect(formatSeconds(10076)).toBe('10.1 秒')
    expect(formatSeconds(null)).toBe('—')
  })
})
