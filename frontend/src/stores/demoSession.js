import { reactive } from 'vue'

import { runScenario } from '@/api/demo'

/**
 * 演示页状态（任务卡 6.8）。放在模块级：跳到审计详情再返回时，选中的场景和运行结果仍在。
 *
 * results：
 * - null：尚未运行
 * - { kind: 'single', runs: [run] }
 * - { kind: 'compare', runs: [annotateRun, blockRun] }
 * run：{ strategy, mode, scenarioId, data, error }
 *   data 为 POST /api/demo/run 的响应 { response, check }（契约 7.7），error 为请求失败时的规范化错误。
 */

export const COMPARE_STRATEGIES = ['annotate', 'block']

export const demoSession = reactive({
  selectedId: null,
  mode: 'MOCK',
  strategy: '',
  threshold: null,
  running: false,
  runningSince: null,
  runningLabel: '',
  results: null
})

let generation = 0

/** 切换场景：清空上一次结果；不支持真实模式的场景强制使用模拟模式。 */
export function selectScenario(scenario) {
  generation += 1
  demoSession.selectedId = scenario.scenarioId
  demoSession.results = null
  demoSession.running = false
  demoSession.runningSince = null
  if (!scenario.supportsReal) {
    demoSession.mode = 'MOCK'
  }
}

function payload(scenarioId, strategy) {
  return {
    scenarioId,
    mode: demoSession.mode,
    guardStrategy: strategy || null,
    blockThreshold: demoSession.threshold ?? null,
    verifier: null
  }
}

async function runOnce(scenarioId, strategy) {
  const body = payload(scenarioId, strategy)
  try {
    const data = await runScenario(body)
    return {
      strategy: body.guardStrategy,
      mode: body.mode,
      scenarioId,
      data,
      error: null
    }
  } catch (error) {
    return {
      strategy: body.guardStrategy,
      mode: body.mode,
      scenarioId,
      data: null,
      error
    }
  }
}

async function execute(kind, strategies) {
  const scenarioId = demoSession.selectedId
  if (!scenarioId || demoSession.running) {
    return
  }
  const myGeneration = ++generation
  demoSession.running = true
  demoSession.runningSince = Date.now()
  demoSession.results = null

  const runs = []
  for (const strategy of strategies) {
    demoSession.runningLabel = strategy ? `正在以 ${strategy} 策略运行` : '正在运行'
    const run = await runOnce(scenarioId, strategy)
    if (myGeneration !== generation) {
      return
    }
    runs.push(run)
  }

  demoSession.results = {
    kind,
    runs
  }
  demoSession.running = false
  demoSession.runningSince = null
  demoSession.runningLabel = ''
}

/** 单次运行：使用面板中选择的策略（空表示后端默认）。 */
export function runSelected() {
  return execute('single', [demoSession.strategy])
}

/** 两种策略对比运行：依次以 annotate 和 block 运行同一场景（任务卡 6.8）。 */
export function runCompare() {
  return execute('compare', COMPARE_STRATEGIES)
}
