// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest'

import { mount } from './helpers/mount'
import AnswerCard from '../src/components/AnswerCard.vue'
import { CASES } from '../src/mock/fixtures/cases'
import { buildExchange } from '../src/mock/fixtures/builder'

let mounted = null

afterEach(() => {
  if (mounted) {
    mounted.unmount()
    mounted = null
  }
})

async function render(key, strategy = 'annotate') {
  const built = buildExchange(CASES[key], { strategy })
  mounted = await mount(AnswerCard, { response: built.response })
  return {
    el: mounted.el,
    response: built.response,
    rawAnswer: built.record.rawAnswer
  }
}

describe('AnswerCard', () => {
  it('部分无证据：黄色提示、有证据下划线带序号、证据不足标黄带 ⚠', async () => {
    const { el } = await render('partial')
    expect(el.querySelector('.el-alert--warning').textContent).toContain('部分结论未在知识库中找到充分证据')
    expect(el.querySelector('.claim--supported').textContent).toBe('公司每月提供 300 元餐费补贴[1]')
    expect(el.querySelector('.claim--insufficient').textContent).toBe('⚠每月还提供 2,000 元交通补贴')
    expect(el.querySelectorAll('.claim-table .el-table__body tr')).toHaveLength(2)
  })

  it('XSS：回答和结论中的 HTML 原样显示为文本，不生成 img/script 元素', async () => {
    const { el } = await render('xss')
    expect(el.querySelector('img')).toBeNull()
    expect(el.querySelector('script')).toBeNull()
    expect(el.querySelector('.answer-text').textContent).toContain('<img src=x onerror=alert(1)>')
    expect(el.querySelector('.answer-text').textContent).toContain("<script>alert('xss')</script>")
    expect(el.querySelector('.claim-table').textContent).toContain('<img src=x onerror=alert(1)>')
  })

  it('BLOCKED：不渲染回答正文，显示占位，结论表保留 3 条', async () => {
    const { el, rawAnswer } = await render('blocked', 'block')
    expect(el.querySelector('.el-alert--error')).not.toBeNull()
    expect(el.querySelector('.answer-text')).toBeNull()
    expect(el.querySelector('.answer-placeholder').textContent).toContain('回答已被拦截')
    expect(el.textContent).not.toContain(rawAnswer)
    expect(el.textContent).not.toContain('加班满2小时还可报销打车费用')
    expect(el.querySelectorAll('.claim-table .el-table__body tr')).toHaveLength(3)
  })

  it('NOT_VERIFIED（block）：不渲染回答正文', async () => {
    const { el, rawAnswer } = await render('notVerified', 'block')
    expect(el.querySelector('.answer-text')).toBeNull()
    expect(el.querySelector('.answer-placeholder').textContent).toContain('回答未经核验，已暂停展示')
    expect(el.textContent).not.toContain(rawAnswer)
    expect(el.textContent).toContain('核验失败原因：核验结果无法解析为 JSON')
  })

  it('NOT_VERIFIED（annotate）：显示正文但不高亮，带“未经核验”边框', async () => {
    const { el, rawAnswer } = await render('notVerified', 'annotate')
    expect(el.querySelector('.answer-unverified .answer-text').textContent).toBe(rawAnswer)
    expect(el.querySelector('.claim')).toBeNull()
  })

  it('NO_EVIDENCE：显示固定文案，无结论表', async () => {
    const { el } = await render('noEvidence')
    expect(el.querySelector('.answer-text').textContent).toBe('知识库中没有找到与该问题相关的资料，系统未生成回答。')
    expect(el.querySelector('.claim-table')).toBeNull()
    expect(el.querySelector('.timing-bar').textContent).toContain('生成 —')
  })

  it('NO_CLAIMS：显示正文、不高亮', async () => {
    const { el } = await render('noClaims')
    expect(el.querySelector('.el-alert--info')).not.toBeNull()
    expect(el.querySelector('.claim')).toBeNull()
  })

  it('截断：标题注明仅核验前 8 条', async () => {
    const { el } = await render('truncated')
    expect(el.textContent).toContain('仅核验了前 8 条')
  })

  it('右上角 requestId 链接到审计详情', async () => {
    const { el, response } = await render('allSupported')
    const link = el.querySelector('.request-id')
    expect(link.textContent.trim()).toBe(response.requestId)
    expect(link.getAttribute('href')).toBe(`/audit/${response.requestId}`)
  })

  it('耗时：毫秒转秒保留 1 位小数', async () => {
    const { el } = await render('allSupported')
    expect(el.querySelector('.timing-bar').textContent).toContain('生成 3.8 秒')
  })
})
