/**
 * 把 cases.js 中的用例组装成契约结构（仅 Mock 使用）。
 *
 * 只做机械推导，不做任何“是否拦截”的判断：
 * - startIndex / endIndex：answer.indexOf(originalText)，找不到为 null（契约 5.3，与 Java UTF-16 下标一致）；
 * - 计数字段：与契约 3.4 recalculate() 相同；
 * - guard.action / answerVisible：直接取用例中按策略写好的值；
 * - 遮蔽：契约 6.2；
 * - 提示文案：契约 6.3 固定文案。
 *
 * 返回 { response, record }：response 是 ChatResponse（3.7），record 是审计详情（7.9，未遮蔽）。
 */

import { evidenceById } from './knowledge'
import { newRequestId, newSessionId, shanghaiIso } from './ids'

export const DEFAULT_THRESHOLD = 0.5

const TOP_K = 3

// 契约 6.3 固定文案（由后端返回，前端 Mock 仅用于模拟后端）
const N1 = '知识库中没有找到与该问题相关的资料，系统未生成回答。'
const M1 = '回答中的全部结论均有知识库证据支持。'
const M2 = '部分结论未在知识库中找到充分证据，已用黄色标出。'
const M2P = '回答包含的结论较多，仅核验了前 8 条，其余内容未经核验。'
const M3 = '核验未完成，以下回答未经证据核验，请谨慎参考。'
const M4 = '核验未完成，为避免输出未经核验的内容，系统已暂停展示回答。'
const M5 = '回答中没有需要核验的事实性结论。'
const M6 = '当前回答中有较多结论缺乏充分证据，系统已限制输出，请核对原始资料。'

const GENERATION_FAILED_MESSAGE = '大模型生成回答失败，请稍后重试。'

const CITATION_REASONS = ['EVIDENCE_NOT_IN_CONTEXT', 'QUOTE_TOO_SHORT', 'QUOTE_NOT_FOUND']

function buildEvidence(pairs) {
  return pairs.map(([evidenceId, score], index) => {
    const evidence = evidenceById(evidenceId, score, index + 1)
    if (!evidence) {
      throw new Error(`Mock 用例引用了不存在的片段：${evidenceId}`)
    }
    return evidence
  })
}

function buildClaims(answer, claims) {
  return claims.map((c, index) => {
    const position = answer === null ? -1 : answer.indexOf(c.originalText)
    const found = position >= 0
    return {
      claimId: `c${index + 1}`,
      claim: c.claim,
      originalText: c.originalText,
      startIndex: found ? position : null,
      endIndex: found ? position + c.originalText.length : null,
      modelSupported: c.modelSupported,
      status: c.status,
      evidenceId: c.evidenceId,
      quote: c.quote,
      quoteExists: c.quoteExists,
      insufficientReason: c.insufficientReason
    }
  })
}

/** 与契约 3.4 recalculate() 相同。 */
function counts(claims) {
  const totalClaims = claims.length
  const supportedClaims = claims.filter((c) => c.status === 'SUPPORTED').length
  const insufficientClaims = totalClaims - supportedClaims
  const unsupportedRatio = totalClaims === 0
    ? null
    : Math.round((insufficientClaims / totalClaims) * 10000) / 10000
  return {
    totalClaims,
    supportedClaims,
    insufficientClaims,
    unsupportedRatio
  }
}

function buildReport(requestId, def) {
  const claims = buildClaims(def.answer, def.claims)
  const c = counts(claims)
  return {
    requestId,
    verificationState: def.verificationState,
    failureReason: def.failureReason,
    answer: def.answer,
    totalClaims: c.totalClaims,
    supportedClaims: c.supportedClaims,
    insufficientClaims: c.insufficientClaims,
    unsupportedRatio: c.unsupportedRatio,
    truncated: def.truncated,
    claims,
    verifierName: def.verificationState === 'NO_EVIDENCE' ? 'none' : 'batch',
    llmCalls: def.llmCalls
  }
}

/** 契约 6.3：按处理结果选固定文案。 */
function messageFor(action, visible, report) {
  switch (action) {
    case 'ANNOTATED':
      if (report.truncated) {
        return M2P
      }
      return report.insufficientClaims === 0 ? M1 : M2
    case 'BLOCKED':
      return M6
    case 'NOT_VERIFIED':
      return visible ? M3 : M4
    case 'NO_CLAIMS':
      return M5
    default:
      return null
  }
}

function buildGuard(spec, strategy, report) {
  let displayAnswer = null
  if (spec.visible) {
    displayAnswer = spec.action === 'NO_EVIDENCE' ? N1 : report.answer
  }
  return {
    strategy,
    blockThreshold: DEFAULT_THRESHOLD,
    action: spec.action,
    answerVisible: spec.visible,
    displayAnswer,
    message: messageFor(spec.action, spec.visible, report),
    unsupportedRatio: report.unsupportedRatio
  }
}

/** 契约 6.2：返回给前端的副本中遮蔽回答原文和定位下标。 */
function maskReport(report) {
  const masked = structuredClone(report)
  masked.answer = null
  masked.claims.forEach((claim) => {
    claim.originalText = null
    claim.startIndex = null
    claim.endIndex = null
  })
  return masked
}

function buildTiming([retrievalMs, generationMs, verificationMs]) {
  const totalMs = retrievalMs + (generationMs ?? 0) + (verificationMs ?? 0) + 4
  return {
    retrievalMs,
    generationMs,
    verificationMs,
    totalMs
  }
}

// ---------- 审计事件（契约 8.2 键名） ----------

function eventFactory(requestId, start) {
  const events = []
  let elapsed = 0
  return {
    events,
    add(eventType, success, durationMs, errorCode, detail) {
      elapsed += durationMs ?? 0
      events.push({
        eventType,
        success,
        durationMs,
        errorCode,
        detail,
        createdAt: shanghaiIso(new Date(start.getTime() + elapsed))
      })
    }
  }
}

function citationDetail(claims) {
  const failedReasons = {}
  let checkedCount = 0
  let passedCount = 0
  claims.forEach((claim) => {
    if (claim.status === 'SUPPORTED') {
      checkedCount += 1
      passedCount += 1
    } else if (CITATION_REASONS.includes(claim.insufficientReason)) {
      checkedCount += 1
      failedReasons[claim.insufficientReason] = (failedReasons[claim.insufficientReason] || 0) + 1
    }
  })
  return {
    checkedCount,
    passedCount,
    failedReasons
  }
}

function retrievalDetail(evidence) {
  return {
    topK: TOP_K,
    hitCount: evidence.length,
    evidenceIds: evidence.map((e) => e.evidenceId),
    topScore: evidence.length > 0 ? evidence[0].score : null
  }
}

function successEvents(ctx, report, guard, timing, evidence, overridden) {
  const f = eventFactory(ctx.requestId, ctx.start)
  f.add('REQUEST_RECEIVED', true, null, null, {
    mode: ctx.mode,
    scenarioId: ctx.scenarioId,
    questionLength: ctx.question.length
  })
  f.add('RETRIEVAL_DONE', true, timing.retrievalMs, null, retrievalDetail(evidence))
  if (report.verificationState !== 'NO_EVIDENCE') {
    f.add('GENERATION_DONE', true, timing.generationMs, null, {
      answerLength: report.answer.length
    })
    const failed = report.verificationState === 'FAILED'
    f.add('VERIFICATION_DONE', !failed, timing.verificationMs, failed ? report.failureReason : null, {
      verifierName: report.verifierName,
      state: report.verificationState,
      failureReason: report.failureReason,
      totalClaims: report.totalClaims,
      llmCalls: report.llmCalls
    })
  }
  if (report.verificationState === 'COMPLETED' || report.verificationState === 'PARTIAL') {
    f.add('CITATION_CHECKED', true, 1, null, citationDetail(report.claims))
  }
  f.add('GUARD_APPLIED', true, 0, null, {
    strategy: guard.strategy,
    blockThreshold: guard.blockThreshold,
    action: guard.action,
    unsupportedRatio: guard.unsupportedRatio,
    overridden
  })
  f.add('REQUEST_COMPLETED', true, 3, null, {
    totalMs: timing.totalMs,
    action: guard.action
  })
  return f.events
}

function context(question, options) {
  return {
    requestId: newRequestId(),
    sessionId: options.sessionId || newSessionId(),
    mode: options.mode || 'REAL',
    scenarioId: options.scenarioId ?? null,
    question: options.question || question,
    start: options.now || new Date()
  }
}

/**
 * 组装一次成功请求。
 * options：strategy（默认 annotate）、mode、scenarioId、sessionId、question（覆盖用例问题）、now、overridden。
 */
export function buildExchange(def, options = {}) {
  const strategy = options.strategy || 'annotate'
  const spec = def.guards[strategy]
  if (!spec) {
    throw new Error(`Mock 用例未定义策略 ${strategy} 的处理结果`)
  }

  const ctx = context(def.question, options)
  const evidence = buildEvidence(def.evidence)
  const report = buildReport(ctx.requestId, def)
  const guard = buildGuard(spec, strategy, report)
  const timing = buildTiming(def.timing)
  const hidden = guard.action === 'BLOCKED' || !guard.answerVisible
  const createdAt = shanghaiIso(ctx.start)

  const response = {
    requestId: ctx.requestId,
    sessionId: ctx.sessionId,
    mode: ctx.mode,
    scenarioId: ctx.scenarioId,
    question: ctx.question,
    evidence,
    verification: hidden ? maskReport(report) : structuredClone(report),
    guard,
    timing,
    createdAt
  }

  // 审计详情（契约 7.9）：verification 不遮蔽；guard 只保留数据库中保存的字段
  const record = {
    requestId: ctx.requestId,
    sessionId: ctx.sessionId,
    createdAt,
    mode: ctx.mode,
    scenarioId: ctx.scenarioId,
    question: ctx.question,
    rawAnswer: report.answer,
    evidence: structuredClone(evidence),
    verification: structuredClone(report),
    guard: {
      strategy: guard.strategy,
      blockThreshold: guard.blockThreshold,
      action: guard.action,
      answerVisible: null,
      displayAnswer: null,
      message: null,
      unsupportedRatio: guard.unsupportedRatio
    },
    timing: structuredClone(timing),
    errorCode: null,
    events: successEvents(ctx, report, guard, timing, evidence, Boolean(options.overridden))
  }

  return {
    response,
    record
  }
}

/**
 * 组装一次生成失败的请求（SC09）：HTTP 502 + LLM_GENERATION_FAILED。
 * 返回 { status, error, record }：error 是契约 3.8 错误体，record 是审计详情（verification、guard 为 null）。
 */
export function buildGenerationFailure(def, options = {}) {
  const ctx = context(def.question, options)
  const evidence = buildEvidence(def.evidence)
  const timing = buildTiming(def.timing)
  const createdAt = shanghaiIso(ctx.start)

  const f = eventFactory(ctx.requestId, ctx.start)
  f.add('REQUEST_RECEIVED', true, null, null, {
    mode: ctx.mode,
    scenarioId: ctx.scenarioId,
    questionLength: ctx.question.length
  })
  f.add('RETRIEVAL_DONE', true, timing.retrievalMs, null, retrievalDetail(evidence))
  f.add('GENERATION_FAILED', false, timing.generationMs, 'LLM_GENERATION_FAILED', {
    llmErrorType: 'API_ERROR'
  })
  f.add('REQUEST_FAILED', false, 2, 'LLM_GENERATION_FAILED', {
    errorCode: 'LLM_GENERATION_FAILED',
    stage: 'GENERATION'
  })

  return {
    status: 502,
    error: {
      requestId: ctx.requestId,
      errorCode: 'LLM_GENERATION_FAILED',
      message: GENERATION_FAILED_MESSAGE,
      timestamp: shanghaiIso(new Date(ctx.start.getTime() + timing.totalMs))
    },
    record: {
      requestId: ctx.requestId,
      sessionId: ctx.sessionId,
      createdAt,
      mode: ctx.mode,
      scenarioId: ctx.scenarioId,
      question: ctx.question,
      rawAnswer: null,
      evidence,
      verification: null,
      guard: null,
      timing,
      errorCode: 'LLM_GENERATION_FAILED',
      events: f.events
    }
  }
}
