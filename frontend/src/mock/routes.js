/**
 * Mock 路由表（任务卡 6.4）。路径不含 /api 前缀（与 http.js 的 baseURL 对应）。
 *
 * 问答页按问题中的关键词选择用例（见 CHAT_TRIGGERS），覆盖全部 GuardAction；
 * 演示页按场景编号和策略选择用例；两者都会写入内存审计记录。
 *
 * Mock 不模拟阈值：演示运行时传入的 blockThreshold 只做范围校验，结果中的阈值固定为 0.5。
 */

import { CASES, GENERATION_FAILED_CASE } from './fixtures/cases'
import { buildExchange, buildGenerationFailure } from './fixtures/builder'
import { buildToolCall } from './fixtures/tools'
import { documentDetail, documentList, evidenceById } from './fixtures/knowledge'
import { findScenario, scenarioList } from './fixtures/scenarios'
import { addRecord, findRecord, listRecords } from './fixtures/audit'
import { shanghaiIso } from './fixtures/ids'

const AVAILABLE_STRATEGIES = ['annotate', 'block']

const SESSION_ID_PATTERN = /^ses_[0-9a-f]{12}$/

/**
 * 问答页关键词 → [用例, 策略]，按顺序匹配第一个。
 * 未匹配时使用 noEvidence（知识库无相关资料）。
 */
export const CHAT_TRIGGERS = [
  [/生成失败/, 'generationFailed', 'annotate'],
  [/xss|html|标签/i, 'xss', 'annotate'],
  [/长回答|详细介绍/, 'long', 'annotate'],
  [/暂停/, 'notVerified', 'block'],
  [/核验失败|保修/, 'notVerified', 'annotate'],
  [/超时|迟到/, 'verifyTimeout', 'annotate'],
  [/拦截/, 'blocked', 'block'],
  [/报销|补贴政策/, 'blocked', 'annotate'],
  [/引用|请假/, 'citationInvalid', 'annotate'],
  [/截断|员工手册/, 'truncated', 'annotate'],
  [/年假/, 'allSupported', 'annotate'],
  [/福利/, 'partial', 'annotate'],
  [/怎样提问|你好/, 'noClaims', 'annotate']
]

function ok(data) {
  return {
    status: 200,
    data
  }
}

/** 契约 3.8 错误体。 */
function fail(status, errorCode, message, requestId = null) {
  return {
    status,
    data: {
      requestId,
      errorCode,
      message,
      timestamp: shanghaiIso(new Date())
    }
  }
}

function invalid(reason) {
  return fail(400, 'INVALID_REQUEST', `请求参数不正确：${reason}`)
}

function pickChatCase(question) {
  const trigger = CHAT_TRIGGERS.find(([pattern]) => pattern.test(question))
  return trigger ? [trigger[1], trigger[2]] : ['noEvidence', 'annotate']
}

/** 执行一个用例并写入审计；生成失败返回 502 错误体。 */
function run(caseKey, options) {
  if (caseKey === 'generationFailed') {
    const failure = buildGenerationFailure(GENERATION_FAILED_CASE, options)
    addRecord(failure.record)
    return {
      response: null,
      failure
    }
  }
  const built = buildExchange(CASES[caseKey], options)

  // 契约 v1.3 第 7.7 节：带工具调用的场景，在问答之后把工具网关结果与审计事件接在同一 requestId 下
  let toolCall = null
  if (options.toolCall && options.mode === 'MOCK') {
    const lastEvent = built.record.events[built.record.events.length - 1]
    const start = new Date(Date.parse(lastEvent.createdAt) + 1)
    const tool = buildToolCall(options.toolCall, built.response.requestId, start)
    toolCall = tool.result
    built.record.events.push(...tool.events)
  }

  addRecord(built.record)
  return {
    response: built.response,
    failure: null,
    toolCall
  }
}

// ---------- POST /chat（契约 7.1） ----------

function handleChat({ body }) {
  const question = body && typeof body.question === 'string' ? body.question.trim() : ''
  if (question.length < 1 || question.length > 500) {
    return invalid('question 去除首尾空白后的长度必须为 1 到 500。')
  }
  const sessionId = body.sessionId ?? null
  if (sessionId !== null && !SESSION_ID_PATTERN.test(sessionId)) {
    return invalid('sessionId 格式无效。')
  }

  const [caseKey, strategy] = pickChatCase(question)
  const result = run(caseKey, {
    strategy,
    mode: 'REAL',
    scenarioId: null,
    sessionId,
    question
  })

  if (result.failure) {
    return {
      status: result.failure.status,
      data: result.failure.error
    }
  }
  return ok(result.response)
}

// ---------- POST /demo/run（契约 7.7） ----------

function handleDemoRun({ body }) {
  const payload = body || {}
  const scenario = findScenario(payload.scenarioId)
  if (!scenario) {
    return fail(404, 'SCENARIO_NOT_FOUND', '测试场景不存在。')
  }
  if (payload.mode !== 'MOCK' && payload.mode !== 'REAL') {
    return invalid('mode 必须为 MOCK 或 REAL。')
  }
  const strategy = payload.guardStrategy ?? null
  if (strategy !== null && !AVAILABLE_STRATEGIES.includes(strategy)) {
    return invalid(`策略 ${strategy} 不存在。`)
  }
  const threshold = payload.blockThreshold ?? null
  if (threshold !== null && (typeof threshold !== 'number' || threshold < 0 || threshold > 1)) {
    return invalid('blockThreshold 必须在 0 到 1 之间。')
  }
  if ((payload.verifier ?? null) !== null && payload.mode !== 'REAL') {
    return invalid('verifier 仅在 mode=REAL 时允许非空。')
  }
  if (!scenario.caseKey) {
    return fail(404, 'MOCK_NOT_CONFIGURED', `${scenario.scenarioId} 为 P1 扩展场景，前端 Mock 未配置。`)
  }
  if (payload.mode === 'REAL' && !scenario.supportsReal) {
    return invalid(`场景 ${scenario.scenarioId} 不支持真实模式。`)
  }

  const result = run(scenario.caseKey, {
    strategy: strategy || 'annotate',
    mode: payload.mode,
    scenarioId: scenario.scenarioId,
    question: scenario.question,
    overridden: strategy !== null || threshold !== null,
    toolCall: scenario.toolCall || null
  })

  const mismatches = scenario.forcedMismatches || []
  const check = payload.mode === 'MOCK'
    ? {
      checked: true,
      passed: mismatches.length === 0,
      mismatches: [...mismatches]
    }
    : {
      checked: false,
      passed: null,
      mismatches: []
    }

  return ok({
    response: result.response,
    check,
    toolCall: result.toolCall ?? null
  })
}

// ---------- 路由表 ----------

export const mockRoutes = [
  {
    method: 'get',
    pattern: /^\/guard\/config$/,
    handler: () => ok({
      strategy: 'annotate',
      blockThreshold: 0.5,
      strictForToolCall: true,
      availableStrategies: [...AVAILABLE_STRATEGIES]
    })
  },
  {
    method: 'post',
    pattern: /^\/chat$/,
    handler: handleChat
  },
  {
    method: 'get',
    pattern: /^\/knowledge\/documents$/,
    handler: () => ok(documentList())
  },
  {
    method: 'get',
    pattern: /^\/knowledge\/documents\/([^/]+)$/,
    handler: ({ match }) => {
      const detail = documentDetail(decodeURIComponent(match[1]))
      return detail ? ok(detail) : fail(404, 'DOCUMENT_NOT_FOUND', '文档不存在。')
    }
  },
  {
    method: 'get',
    pattern: /^\/knowledge\/evidence\/([^/]+)$/,
    handler: ({ match }) => {
      const evidence = evidenceById(decodeURIComponent(match[1]))
      return evidence ? ok(evidence) : fail(404, 'EVIDENCE_NOT_FOUND', '证据片段不存在。')
    }
  },
  {
    method: 'get',
    pattern: /^\/demo\/scenarios$/,
    handler: () => ok(scenarioList())
  },
  {
    method: 'post',
    pattern: /^\/demo\/run$/,
    handler: handleDemoRun
  },
  {
    method: 'get',
    pattern: /^\/approvals$/,
    handler: () => ok([])
  },
  {
    method: 'post',
    pattern: /^\/(tools\/execute|approvals\/[^/]+\/(approve|reject))$/,
    handler: () => fail(404, 'MOCK_NOT_CONFIGURED', '审批中心依赖真实后端的工具网关，前端 Mock 未模拟，请使用 npm run dev 联调。')
  },
  {
    method: 'get',
    pattern: /^\/audit$/,
    handler: ({ params }) => ok(listRecords(params))
  },
  {
    method: 'get',
    pattern: /^\/audit\/([^/]+)$/,
    handler: ({ match }) => {
      const record = findRecord(decodeURIComponent(match[1]))
      return record ? ok(record) : fail(404, 'AUDIT_NOT_FOUND', '审计记录不存在。')
    }
  }
]
