// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

const runScenario = vi.fn()
const listScenarios = vi.fn()
const getGuardConfig = vi.fn()
const listDocuments = vi.fn()
const getDocument = vi.fn()

vi.mock('../src/api/demo.js', () => ({
  runScenario: (...args) => runScenario(...args),
  listScenarios: (...args) => listScenarios(...args)
}))
vi.mock('../src/api/guard.js', () => ({
  getGuardConfig: (...args) => getGuardConfig(...args)
}))
vi.mock('../src/api/knowledge.js', () => ({
  listDocuments: (...args) => listDocuments(...args),
  getDocument: (...args) => getDocument(...args),
  getEvidence: vi.fn()
}))

const { mount } = await import('./helpers/mount')
const { default: DemoView } = await import('../src/views/DemoView.vue')
const { default: DemoRunResult } = await import('../src/components/DemoRunResult.vue')
const { default: KnowledgeDrawer } = await import('../src/components/KnowledgeDrawer.vue')
const { demoSession, runCompare, runSelected, selectScenario } = await import('../src/stores/demoSession')
const { CASES } = await import('../src/mock/fixtures/cases')
const { buildExchange } = await import('../src/mock/fixtures/builder')

const SCENARIOS = [
  { scenarioId: 'SC03', name: '输出策略切换', description: '…', question: '公司的报销和补贴政策是什么？', priority: 'P0', supportsReal: true, recommendedStrategies: ['annotate', 'block'] },
  { scenarioId: 'SC05', name: '核验返回无效 JSON', description: '…', question: '产品的保修期是多久？', priority: 'P0', supportsReal: false, recommendedStrategies: ['annotate', 'block'] },
  { scenarioId: 'SC06', name: '越权工具调用', description: '…', question: '帮我删除编号 1001 的记录', priority: 'P1', supportsReal: false, recommendedStrategies: [] }
]

const PASSED = { checked: true, passed: true, mismatches: [] }

let mounted = null

async function flush() {
  for (let i = 0; i < 6; i++) {
    await Promise.resolve()
    await nextTick()
  }
  await new Promise((resolve) => setTimeout(resolve, 0))
  await nextTick()
}

function resetSession() {
  selectScenario({ scenarioId: null, supportsReal: true })
  demoSession.selectedId = null
  demoSession.mode = 'MOCK'
  demoSession.strategy = ''
  demoSession.threshold = null
}

beforeEach(() => {
  resetSession()
  runScenario.mockReset()
  listScenarios.mockReset()
  getGuardConfig.mockReset()
  listDocuments.mockReset()
  getDocument.mockReset()
})

afterEach(() => {
  if (mounted) {
    mounted.unmount()
    mounted = null
  }
})

describe('demoSession', () => {
  it('单次运行：请求体按契约 7.7，“使用默认”时 guardStrategy 为 null，verifier 恒为 null', async () => {
    runScenario.mockResolvedValue({ response: null, check: PASSED })
    selectScenario(SCENARIOS[0])
    demoSession.threshold = 0.3
    await runSelected()
    expect(runScenario).toHaveBeenCalledWith({ scenarioId: 'SC03', mode: 'MOCK', guardStrategy: null, blockThreshold: 0.3, verifier: null })
    expect(demoSession.results.kind).toBe('single')
  })

  it('对比运行：依次以 annotate、block 运行同一场景', async () => {
    runScenario.mockResolvedValue({ response: null, check: PASSED })
    selectScenario(SCENARIOS[0])
    demoSession.strategy = 'block'
    await runCompare()
    expect(runScenario.mock.calls.map((c) => c[0].guardStrategy)).toEqual(['annotate', 'block'])
    expect(demoSession.results.runs.map((r) => r.strategy)).toEqual(['annotate', 'block'])
  })

  it('请求失败时记录错误，不中断对比的另一侧', async () => {
    runScenario
      .mockRejectedValueOnce({ errorCode: 'INVALID_REQUEST', message: '请求参数不正确：x', requestId: null, httpStatus: 400 })
      .mockResolvedValueOnce({ response: null, check: PASSED })
    selectScenario(SCENARIOS[0])
    await runCompare()
    expect(demoSession.results.runs[0].error.errorCode).toBe('INVALID_REQUEST')
    expect(demoSession.results.runs[1].data.check.passed).toBe(true)
  })

  it('切换到不支持真实模式的场景时强制改回模拟，并清空结果', async () => {
    runScenario.mockResolvedValue({ response: null, check: PASSED })
    selectScenario(SCENARIOS[0])
    demoSession.mode = 'REAL'
    await runSelected()
    selectScenario(SCENARIOS[1])
    expect(demoSession.mode).toBe('MOCK')
    expect(demoSession.results).toBeNull()
  })

  it('运行中切换场景，旧结果被丢弃', async () => {
    let resolve
    runScenario.mockReturnValueOnce(new Promise((r) => { resolve = r }))
    selectScenario(SCENARIOS[0])
    const running = runSelected()
    selectScenario(SCENARIOS[1])
    resolve({ response: null, check: PASSED })
    await running
    expect(demoSession.results).toBeNull()
  })
})

describe('DemoRunResult', () => {
  it('符合预期 + AnswerCard', async () => {
    const response = buildExchange(CASES.blocked, { strategy: 'block', mode: 'MOCK', scenarioId: 'SC03' }).response
    mounted = await mount(DemoRunResult, { run: { strategy: 'block', mode: 'MOCK', data: { response, check: PASSED }, error: null }, showStrategy: true })
    expect(mounted.el.querySelector('.demo-run__strategy').textContent).toContain('拦截（block）')
    expect(mounted.el.querySelector('.demo-check').textContent).toContain('符合预期')
    expect(mounted.el.querySelector('.answer-placeholder').textContent).toContain('回答已被拦截')
  })

  it('不符合预期：列出 mismatches', async () => {
    const response = buildExchange(CASES.allSupported).response
    const check = { checked: true, passed: false, mismatches: ['claims[0].status 期望 INSUFFICIENT，实际 SUPPORTED'] }
    mounted = await mount(DemoRunResult, { run: { strategy: null, mode: 'MOCK', data: { response, check }, error: null } })
    const alert = mounted.el.querySelector('.demo-check')
    expect(alert.textContent).toContain('不符合预期')
    expect(alert.querySelectorAll('li')).toHaveLength(1)
  })

  it('response 为 null（SC09）：显示未返回回答，并链接审计日志', async () => {
    mounted = await mount(DemoRunResult, { run: { strategy: null, mode: 'MOCK', data: { response: null, check: PASSED }, error: null } })
    expect(mounted.el.querySelector('.demo-no-response').textContent).toContain('本次运行没有返回回答')
    expect(mounted.el.querySelector('.demo-no-response a').getAttribute('href')).toBe('/audit?mode=MOCK')
    expect(mounted.el.querySelector('.answer-card')).toBeNull()
  })

  it('真实模式：提示不做期望比对', async () => {
    const response = buildExchange(CASES.allSupported, { mode: 'REAL' }).response
    mounted = await mount(DemoRunResult, { run: { strategy: null, mode: 'REAL', data: { response, check: { checked: false, passed: null, mismatches: [] } }, error: null } })
    expect(mounted.el.querySelector('.demo-check').textContent).toContain('真实模式不做期望比对')
  })

  it('请求失败：显示错误卡片', async () => {
    mounted = await mount(DemoRunResult, { run: { strategy: null, mode: 'MOCK', data: null, error: { errorCode: 'SCENARIO_NOT_FOUND', message: '测试场景不存在。', requestId: null, httpStatus: 404 } } })
    expect(mounted.el.querySelector('.error-card').textContent).toContain('测试场景不存在。')
  })
})

describe('DemoView', () => {
  beforeEach(() => {
    listScenarios.mockResolvedValue(SCENARIOS)
    getGuardConfig.mockResolvedValue({ strategy: 'annotate', blockThreshold: 0.5, strictForToolCall: true, availableStrategies: ['annotate', 'block', 'strict'] })
  })

  it('加载场景并默认选中第一个；P1 显示“扩展”；问题只读显示', async () => {
    mounted = await mount(DemoView, {}, { path: '/demo' })
    await flush()
    const cards = mounted.el.querySelectorAll('.scenario-card')
    expect(cards).toHaveLength(3)
    expect(cards[0].classList.contains('is-active')).toBe(true)
    expect(cards[2].textContent).toContain('扩展')
    expect(mounted.el.querySelector('.run-panel__question-text').textContent).toBe('公司的报销和补贴政策是什么？')
  })

  it('supportsReal=false 时“真实”不可选', async () => {
    mounted = await mount(DemoView, {}, { path: '/demo' })
    await flush()
    mounted.el.querySelectorAll('.scenario-card')[1].click()
    await flush()
    const real = [...mounted.el.querySelectorAll('.el-radio-button')].find((e) => e.textContent.includes('真实'))
    expect(real.classList.contains('is-disabled')).toBe(true)
  })

  it('对比运行后左右并排两个结果，分别标注策略', async () => {
    runScenario.mockImplementation(async (body) => ({
      response: buildExchange(CASES.blocked, { strategy: body.guardStrategy, mode: 'MOCK', scenarioId: 'SC03' }).response,
      check: PASSED
    }))
    mounted = await mount(DemoView, {}, { path: '/demo' })
    await flush()
    const compare = [...mounted.el.querySelectorAll('.run-panel__actions button')].find((b) => b.textContent.includes('两种策略对比运行'))
    compare.click()
    await flush()
    const runs = mounted.el.querySelectorAll('.compare-grid .demo-run')
    expect(runs).toHaveLength(2)
    expect(runs[0].querySelector('.demo-run__strategy').textContent).toContain('annotate')
    expect(runs[0].querySelector('.answer-text')).not.toBeNull()
    expect(runs[1].querySelector('.demo-run__strategy').textContent).toContain('block')
    expect(runs[1].querySelector('.answer-text')).toBeNull()
  })
})

describe('KnowledgeDrawer', () => {
  it('打开时加载文档列表，点击文档显示全文与片段（纯文本）', async () => {
    listDocuments.mockResolvedValue([{ documentId: 'doc005', title: '员工福利规定', fileName: 'doc005_员工福利规定.md', chunkCount: 1, charCount: 30 }])
    getDocument.mockResolvedValue({
      documentId: 'doc005',
      title: '员工福利规定',
      content: '# 员工福利规定\n\n## 餐费补贴\n\n<b>每月300元</b>',
      chunks: [{ evidenceId: 'doc005_chunk01', section: '餐费补贴', content: '<b>每月300元</b>' }]
    })
    mounted = await mount(KnowledgeDrawer, { modelValue: true })
    await flush()
    const doc = document.querySelector('.kd-doc')
    expect(doc.textContent).toContain('员工福利规定')
    doc.click()
    await flush()
    const content = document.querySelector('.kd-content')
    expect(content.textContent).toContain('<b>每月300元</b>')
    expect(content.querySelector('b')).toBeNull()
  })
})
