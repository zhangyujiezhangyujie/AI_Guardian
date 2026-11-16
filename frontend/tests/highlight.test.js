import { describe, expect, it } from 'vitest'

import { buildSegments, splitByQuote } from '../src/utils/highlight'

function claim(text, originalText, status = 'SUPPORTED', extra = {}) {
  const start = text.indexOf(originalText)
  return {
    claimId: extra.claimId || 'c?',
    originalText,
    startIndex: start >= 0 ? start : null,
    endIndex: start >= 0 ? start + originalText.length : null,
    status,
    ...extra
  }
}

function joined(segments) {
  return segments.map((s) => s.text).join('')
}

describe('buildSegments（任务卡 6.5 高亮算法）', () => {
  const text = '员工每年享有15天年假，每月还有2,000元交通补贴。'

  it('契约 3.7 示例：切成 结论/普通/结论/普通，拼接后与原文一致', () => {
    const claims = [
      claim(text, '员工每年享有15天年假'),
      claim(text, '每月还有2,000元交通补贴', 'INSUFFICIENT')
    ]
    const segments = buildSegments(text, claims)
    expect(segments.map((s) => [s.text, s.number])).toEqual([
      ['员工每年享有15天年假', 1],
      ['，', null],
      ['每月还有2,000元交通补贴', 2],
      ['。', null]
    ])
    expect(joined(segments)).toBe(text)
  })

  it('下标为 null 的结论不高亮', () => {
    const claims = [
      claim(text, '不存在的原文'),
      claim(text, '员工每年享有15天年假')
    ]
    const segments = buildSegments(text, claims)
    expect(segments.filter((s) => s.claim).map((s) => s.number)).toEqual([2])
  })

  it('按 startIndex 升序处理，序号仍取结论在列表中的位置', () => {
    const claims = [
      claim(text, '每月还有2,000元交通补贴'),
      claim(text, '员工每年享有15天年假')
    ]
    const numbers = buildSegments(text, claims).filter((s) => s.claim).map((s) => s.number)
    expect(numbers).toEqual([2, 1])
  })

  it('与前一个已采用区间重叠的结论被跳过', () => {
    const claims = [
      claim(text, '员工每年享有15天年假'),
      claim(text, '15天年假'),
      claim(text, '每月还有2,000元交通补贴')
    ]
    const highlighted = buildSegments(text, claims).filter((s) => s.claim).map((s) => s.number)
    expect(highlighted).toEqual([1, 3])
  })

  it('首尾相接（end = 下一个 start）不算重叠', () => {
    const t = 'ABCDEF'
    const claims = [
      { originalText: 'ABC', startIndex: 0, endIndex: 3, status: 'SUPPORTED' },
      { originalText: 'DEF', startIndex: 3, endIndex: 6, status: 'INSUFFICIENT' }
    ]
    expect(buildSegments(t, claims).map((s) => s.text)).toEqual(['ABC', 'DEF'])
  })

  it('防御：越界或下标与 originalText 不一致的结论不高亮', () => {
    const claims = [
      { originalText: '员工', startIndex: 100, endIndex: 102, status: 'SUPPORTED' },
      { originalText: '错误文本', startIndex: 0, endIndex: 4, status: 'SUPPORTED' },
      { originalText: '员工', startIndex: 2, endIndex: 1, status: 'SUPPORTED' }
    ]
    const segments = buildSegments(text, claims)
    expect(segments).toEqual([{ text, claim: null, number: null }])
  })

  it('UTF-16 下标：含表情等代理对字符时与 Java String.indexOf 一致', () => {
    const t = '😀正式员工每年享有15天带薪年假'
    const claims = [claim(t, '正式员工')]
    expect(claims[0].startIndex).toBe(2)
    expect(buildSegments(t, claims)[1].text).toBe('正式员工')
  })

  it('空文本或无结论', () => {
    expect(buildSegments('', [])).toEqual([])
    expect(buildSegments(text, [])).toEqual([{ text, claim: null, number: null }])
    expect(buildSegments(text, null)).toEqual([{ text, claim: null, number: null }])
  })
})

describe('splitByQuote（证据抽屉 quote 高亮）', () => {
  const content = '正式员工每年享有15天带薪年假，试用期员工不享有年假。'

  it('找到则切成三段，中间高亮', () => {
    expect(splitByQuote(content, '正式员工每年享有15天带薪年假')).toEqual([
      { text: '正式员工每年享有15天带薪年假', highlight: true },
      { text: '，试用期员工不享有年假。', highlight: false }
    ])
  })

  it('找不到不高亮，不做模糊匹配（标点或空白不同也不算）', () => {
    expect(splitByQuote(content, '正式员工每年享有 15 天带薪年假')).toEqual([{ text: content, highlight: false }])
    expect(splitByQuote(content, '正式员工每年享有15天带薪年假,')).toEqual([{ text: content, highlight: false }])
  })

  it('quote 为空时不高亮', () => {
    expect(splitByQuote(content, null)).toEqual([{ text: content, highlight: false }])
    expect(splitByQuote(content, '')).toEqual([{ text: content, highlight: false }])
  })
})
