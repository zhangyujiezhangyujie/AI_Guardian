// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

const listAudits = vi.fn()
const getAuditDetail = vi.fn()

vi.mock('../src/api/audit.js', () => ({
  listAudits: (...args) => listAudits(...args),
  getAuditDetail: (...args) => getAuditDetail(...args)
}))

const { mount } = await import('./helpers/mount')
const { default: AuditListView } = await import('../src/views/AuditListView.vue')
const { default: AuditDetailView } = await import('../src/views/AuditDetailView.vue')
const { CASES, GENERATION_FAILED_CASE } = await import('../src/mock/fixtures/cases')
const { buildExchange, buildGenerationFailure } = await import('../src/mock/fixtures/builder')

let mounted = null

async function flush() {
  for (let i = 0; i < 6; i++) {
    await Promise.resolve()
    await nextTick()
  }
  // 路由导航需要额外的宏任务才能完成
  await new Promise((resolve) => setTimeout(resolve, 0))
  await nextTick()
}

function listItem(overrides = {}) {
  return {
    requestId: 'req_aaaaaaaaaaaa',
    createdAt: '2026-11-19T20:43:05.512+08:00',
    mode: 'MOCK',
    scenarioId: 'SC03',
    questionPreview: '公司的报销和补贴政策是什么？',
    verificationState: 'COMPLETED',
    guardStrategy: 'block',
    guardAction: 'BLOCKED',
    totalClaims: 3,
    supportedClaims: 1,
    insufficientClaims: 2,
    totalMs: 6034,
    errorCode: null,
    ...overrides
  }
}

beforeEach(() => {
  listAudits.mockReset()
  getAuditDetail.mockReset()
})

afterEach(() => {
  if (mounted) {
    mounted.unmount()
    mounted = null
  }
})

describe('AuditListView', () => {
  it('按 URL 中的筛选和页码请求接口，并渲染行', async () => {
    listAudits.mockResolvedValue({ items: [listItem()], total: 21, page: 2, size: 20 })
    mounted = await mount(AuditListView, {}, { path: '/audit?page=2&action=BLOCKED&mode=MOCK' })
    await flush()

    expect(listAudits).toHaveBeenCalledWith({ page: 2, size: 20, action: 'BLOCKED', mode: 'MOCK' })
    const row = mounted.el.querySelector('.audit-table .el-table__body .el-table__row')
    expect(row.textContent).toContain('11-19 20:43:05')
    expect(row.textContent).toContain('req_aaaaaaaaaaaa')
    expect(row.textContent).toContain('已拦截')
    expect(row.querySelector('.has-insufficient').textContent).toBe('1 / 3')
    expect(row.textContent).toContain('6.0 秒')
  })

  it('生成失败行：核验状态、处理结果、结论显示“—”，错误码标红', async () => {
    listAudits.mockResolvedValue({
      items: [listItem({ verificationState: null, guardStrategy: null, guardAction: null, totalClaims: 0, supportedClaims: 0, insufficientClaims: 0, errorCode: 'LLM_GENERATION_FAILED' })],
      total: 1,
      page: 1,
      size: 20
    })
    mounted = await mount(AuditListView, {}, { path: '/audit' })
    await flush()
    const row = mounted.el.querySelector('.audit-table .el-table__body .el-table__row')
    expect(row.querySelector('.error-code').textContent).toBe('LLM_GENERATION_FAILED')
    expect(row.querySelector('.has-insufficient')).toBeNull()
    expect(row.textContent).not.toContain('0 / 0')
  })

  it('点击行跳转详情；翻页把页码写入 URL 并保留筛选', async () => {
    listAudits.mockResolvedValue({ items: [listItem()], total: 45, page: 1, size: 20 })
    mounted = await mount(AuditListView, {}, { path: '/audit?action=BLOCKED' })
    await flush()

    mounted.el.querySelectorAll('.el-pager li')[1].click()
    await flush()
    expect(mounted.router.currentRoute.value.fullPath).toBe('/audit?action=BLOCKED&page=2')
    expect(listAudits).toHaveBeenLastCalledWith({ page: 2, size: 20, action: 'BLOCKED' })

    mounted.el.querySelector('.audit-table .el-table__body .el-table__row').click()
    await flush()
    expect(mounted.router.currentRoute.value.fullPath).toBe('/audit/req_aaaaaaaaaaaa')
  })

  it('重置清空筛选回到列表首页', async () => {
    listAudits.mockResolvedValue({ items: [], total: 0, page: 1, size: 20 })
    mounted = await mount(AuditListView, {}, { path: '/audit?action=BLOCKED&page=3' })
    await flush()
    const resetButton = [...mounted.el.querySelectorAll('.audit-filter button')].find((b) => b.textContent.includes('重置'))
    resetButton.click()
    await flush()
    expect(mounted.router.currentRoute.value.fullPath).toBe('/audit')
    expect(listAudits).toHaveBeenLastCalledWith({ page: 1, size: 20 })
  })

  it('加载失败：空表显示失败原因和重试', async () => {
    listAudits.mockRejectedValue({ errorCode: 'INTERNAL_ERROR', message: '系统内部错误。', requestId: null, httpStatus: 500 })
    mounted = await mount(AuditListView, {}, { path: '/audit' })
    await flush()
    expect(mounted.el.querySelector('.audit-empty').textContent).toContain('审计记录加载失败：系统内部错误。')
    expect(mounted.el.querySelector('.audit-empty').textContent).toContain('重试')
  })
})

describe('AuditDetailView', () => {
  it('被拦截的回答：管理员视图显示未遮蔽原文并高亮，标注“该回答在用户侧已被拦截”', async () => {
    const { record } = buildExchange(CASES.blocked, { strategy: 'block', mode: 'MOCK', scenarioId: 'SC03' })
    getAuditDetail.mockResolvedValue(record)
    mounted = await mount(AuditDetailView, { requestId: record.requestId }, { path: `/audit/${record.requestId}` })
    await flush()

    expect(getAuditDetail).toHaveBeenCalledWith(record.requestId)
    const raw = mounted.el.querySelector('.raw-answer')
    expect(raw.textContent).toContain('管理员视图：原始回答')
    expect(raw.textContent).toContain('该回答在用户侧已被拦截')
    expect(raw.querySelector('.answer-text').textContent).toContain('员工每月可领取500元通讯补贴')
    expect(raw.querySelectorAll('.claim')).toHaveLength(3)
    // 结论表为管理员版本
    expect(mounted.el.querySelector('.claim-table').textContent).toContain('模型初判')
    expect(mounted.el.querySelector('.claim-table').textContent).toContain('程序校验')
    // 时间线
    const events = [...mounted.el.querySelectorAll('.event-name')].map((e) => e.textContent)
    expect(events).toEqual(['收到请求', '完成检索', '完成生成', '完成核验', '完成引用校验', '执行输出防护', '请求完成'])
  })

  it('生成失败的记录：只显示基本信息、问题、错误码和事件时间线', async () => {
    const { record } = buildGenerationFailure(GENERATION_FAILED_CASE, { mode: 'MOCK', scenarioId: 'SC09' })
    getAuditDetail.mockResolvedValue(record)
    mounted = await mount(AuditDetailView, { requestId: record.requestId }, { path: `/audit/${record.requestId}` })
    await flush()

    const headers = [...mounted.el.querySelectorAll('.detail-section .el-card__header')].map((h) => h.textContent.trim())
    expect(headers).toEqual(['基本信息', '问题与原始回答', '事件时间线'])
    expect(mounted.el.querySelector('.raw-answer')).toBeNull()
    expect(mounted.el.querySelector('.no-answer').textContent).toContain('LLM_GENERATION_FAILED')
    expect(mounted.el.querySelectorAll('.event-name')[2].textContent).toBe('生成失败')
  })

  it('核验失败（annotate）：原文不高亮，标注“该回答未经核验”', async () => {
    const { record } = buildExchange(CASES.notVerified, { strategy: 'annotate' })
    getAuditDetail.mockResolvedValue(record)
    mounted = await mount(AuditDetailView, { requestId: record.requestId }, { path: `/audit/${record.requestId}` })
    await flush()
    expect(mounted.el.querySelector('.raw-answer').textContent).toContain('该回答未经核验')
    expect(mounted.el.querySelector('.raw-answer .claim')).toBeNull()
    expect(mounted.el.textContent).toContain('核验结果无法解析为 JSON')
  })

  it('审计记录不存在：显示提示和返回按钮', async () => {
    getAuditDetail.mockRejectedValue({ errorCode: 'AUDIT_NOT_FOUND', message: '审计记录不存在。', requestId: null, httpStatus: 404 })
    mounted = await mount(AuditDetailView, { requestId: 'req_000000000000' }, { path: '/audit/req_000000000000' })
    await flush()
    expect(mounted.el.querySelector('.el-result').textContent).toContain('审计记录不存在')
  })

  it('从其他页面直接进入时，“返回列表”回到 /audit', async () => {
    const { record } = buildExchange(CASES.partial)
    getAuditDetail.mockResolvedValue(record)
    mounted = await mount(AuditDetailView, { requestId: record.requestId }, { path: `/audit/${record.requestId}` })
    await flush()
    mounted.el.querySelector('.detail-header button').click()
    await flush()
    expect(mounted.router.currentRoute.value.fullPath).toBe('/audit')
  })
})
