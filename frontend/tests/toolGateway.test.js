// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'

vi.mock('../src/mock/index.js', () => ({
  USE_MOCK: true,
  MOCK_DELAY_MS: 0
}))

const { mount } = await import('./helpers/mount')
const { mockAdapter } = await import('../src/mock/adapter')
const { default: ToolCallCard } = await import('../src/components/ToolCallCard.vue')
const { default: DemoRunResult } = await import('../src/components/DemoRunResult.vue')
const { CASES } = await import('../src/mock/fixtures/cases')
const { buildExchange } = await import('../src/mock/fixtures/builder')

const client = axios.create({ baseURL: '/api', adapter: mockAdapter })

let mounted = null

afterEach(() => {
  if (mounted) {
    mounted.unmount()
    mounted = null
  }
})

function toolCall(overrides = {}) {
  return {
    toolCallId: 'tool_5b2c9e01d3aa',
    basedOnRequestId: 'req_3f9a1c0b7d2e',
    user: 'user',
    toolName: 'delete_record',
    arguments: { recordId: 1001 },
    decision: 'DENY',
    denyReason: 'PERMISSION_DENIED',
    message: '当前用户无权使用该工具，已拒绝。',
    executed: false,
    result: null,
    record: { recordId: 1001, title: '客户合同归档（华东区）', owner: 'user', status: 'OPEN', deleted: false, updatedAt: '2026-11-22T10:00:00.000+08:00' },
    createdAt: '2026-11-22T21:03:21.512+08:00',
    ...overrides
  }
}

describe('Mock：SC06 演示运行（契约 v1.3 第 7.7 节）', () => {
  it('返回 toolCall：普通员工删除 1001 被拒绝，记录未删除', async () => {
    const { data } = await client.post('/demo/run', { scenarioId: 'SC06', mode: 'MOCK', guardStrategy: 'block', blockThreshold: null, verifier: null })
    expect(data.response.guard.action).toBe('BLOCKED')
    expect(data.check.passed).toBe(true)
    expect(data.toolCall).toMatchObject({
      user: 'user',
      toolName: 'delete_record',
      decision: 'DENY',
      denyReason: 'PERMISSION_DENIED',
      executed: false,
      basedOnRequestId: data.response.requestId
    })
    expect(data.toolCall.toolCallId).toMatch(/^tool_[0-9a-f]{12}$/)
    expect(data.toolCall.record).toMatchObject({ recordId: 1001, deleted: false })
  })

  it('审计详情在请求完成后追加工具调用事件', async () => {
    const { data } = await client.post('/demo/run', { scenarioId: 'SC06', mode: 'MOCK', guardStrategy: null, blockThreshold: null, verifier: null })
    const { data: detail } = await client.get(`/audit/${data.response.requestId}`)
    const types = detail.events.map((e) => e.eventType)
    expect(types.slice(-3)).toEqual(['REQUEST_COMPLETED', 'TOOL_CALL_REQUESTED', 'TOOL_CALL_DECIDED'])
    expect(Object.keys(detail.events.at(-2).detail).sort()).toEqual(['basedOnRequestId', 'toolCallId', 'toolName', 'user'])
    expect(Object.keys(detail.events.at(-1).detail).sort()).toEqual(['decision', 'denyReason', 'toolCallId'])
  })

  it('其他场景 toolCall 为 null；场景列表不暴露 toolCall', async () => {
    const { data } = await client.post('/demo/run', { scenarioId: 'SC01', mode: 'MOCK', guardStrategy: null, blockThreshold: null, verifier: null })
    expect(data.toolCall).toBeNull()
    const { data: list } = await client.get('/demo/scenarios')
    list.forEach((s) => expect(s.toolCall).toBeUndefined())
  })

  it('Mock 知识库中的 doc008 与 SC06 证据一致', async () => {
    const { data } = await client.get('/knowledge/evidence/doc008_chunk02')
    expect(data.content).toContain('员工不得自行删除记录')
  })
})

describe('ToolCallCard', () => {
  it('拒绝：红色提示、原因、未执行、记录当前状态', async () => {
    mounted = await mount(ToolCallCard, { toolCall: toolCall() })
    const el = mounted.el
    expect(el.querySelector('.tool-call-card__header').textContent).toContain('网关判定：拒绝')
    expect(el.querySelector('.el-alert--error').textContent).toContain('当前用户无权使用该工具，已拒绝。')
    expect(el.textContent).toContain('原因：当前用户无权使用该工具')
    expect(el.textContent).toContain('网关未执行该操作')
    expect(el.textContent).toContain('普通员工（user）')
    expect(el.textContent).toContain('删除业务记录')
    expect(el.querySelector('.tool-call-card__record').textContent).toContain('未删除')
    expect(el.querySelector('a').getAttribute('href')).toBe('/audit/req_3f9a1c0b7d2e')
    expect(el.querySelector('.tool-call-card__result')).toBeNull()
  })

  it('需审批：黄色提示，无原因', async () => {
    mounted = await mount(ToolCallCard, { toolCall: toolCall({ user: 'manager', decision: 'REQUIRE_APPROVAL', denyReason: null, message: '该操作需要部门经理审批，网关未执行。' }) })
    expect(mounted.el.querySelector('.el-alert--warning')).not.toBeNull()
    expect(mounted.el.textContent).not.toContain('原因：')
  })

  it('放行并执行：绿色提示，显示执行结果', async () => {
    mounted = await mount(ToolCallCard, {
      toolCall: toolCall({
        user: 'manager',
        toolName: 'update_record',
        arguments: { recordId: 1002, status: 'CLOSED' },
        decision: 'ALLOW',
        denyReason: null,
        message: '已通过网关检查并执行。',
        executed: true,
        result: { recordId: 1002, statusBefore: 'OPEN', statusAfter: 'CLOSED' },
        record: { recordId: 1002, title: '季度采购对账单', owner: 'user', status: 'CLOSED', deleted: false, updatedAt: '2026-11-22T12:00:00.000+08:00' }
      })
    })
    expect(mounted.el.querySelector('.el-alert--success')).not.toBeNull()
    expect(mounted.el.textContent).toContain('网关已执行该操作')
    expect(mounted.el.querySelector('.tool-call-card__result').textContent).toContain('"statusAfter": "CLOSED"')
    expect(mounted.el.querySelector('.tool-call-card__record').textContent).toContain('已关闭')
  })

  it('参数中的 HTML 原样显示为文本', async () => {
    mounted = await mount(ToolCallCard, { toolCall: toolCall({ toolName: 'drop_table', arguments: { table: '<img src=x onerror=alert(1)>' }, denyReason: 'TOOL_NOT_ALLOWED', record: null }) })
    expect(mounted.el.querySelector('img')).toBeNull()
    expect(mounted.el.textContent).toContain('<img src=x onerror=alert(1)>')
    expect(mounted.el.textContent).toContain('drop_table')
  })
})

describe('DemoRunResult 显示工具调用卡片', () => {
  it('有 toolCall 时在 AnswerCard 之后显示', async () => {
    const response = buildExchange(CASES.toolDenied, { strategy: 'annotate', mode: 'MOCK', scenarioId: 'SC06' }).response
    mounted = await mount(DemoRunResult, { run: { strategy: 'annotate', mode: 'MOCK', data: { response, check: { checked: true, passed: true, mismatches: [] }, toolCall: toolCall() }, error: null } })
    const answer = mounted.el.querySelector('.answer-card')
    const card = mounted.el.querySelector('.tool-call-card')
    expect(answer).not.toBeNull()
    expect(card).not.toBeNull()
    expect(answer.compareDocumentPosition(card) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('无 toolCall 时不显示', async () => {
    const response = buildExchange(CASES.allSupported).response
    mounted = await mount(DemoRunResult, { run: { strategy: null, mode: 'MOCK', data: { response, check: { checked: true, passed: true, mismatches: [] }, toolCall: null }, error: null } })
    expect(mounted.el.querySelector('.tool-call-card')).toBeNull()
  })
})
