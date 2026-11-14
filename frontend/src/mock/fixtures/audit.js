/**
 * 内存中的审计记录（模拟契约 7.8、7.9）。
 *
 * Mock 模式下每次问答、演示运行都会写入一条，便于测试“点击 requestId 跳转审计详情”；
 * 另预置一批历史记录，用于测试分页（每页 20 条）和筛选。刷新页面后重置。
 */

import { CASES, GENERATION_FAILED_CASE } from './cases'
import { buildExchange, buildGenerationFailure } from './builder'

const records = []

export function addRecord(record) {
  records.push(record)
}

export function findRecord(requestId) {
  return records.find((r) => r.requestId === requestId) || null
}

function toItem(record) {
  const verification = record.verification
  return {
    requestId: record.requestId,
    createdAt: record.createdAt,
    mode: record.mode,
    scenarioId: record.scenarioId,
    questionPreview: record.question.slice(0, 50),
    verificationState: verification ? verification.verificationState : null,
    guardStrategy: record.guard ? record.guard.strategy : null,
    guardAction: record.guard ? record.guard.action : null,
    totalClaims: verification ? verification.totalClaims : 0,
    supportedClaims: verification ? verification.supportedClaims : 0,
    insufficientClaims: verification ? verification.insufficientClaims : 0,
    totalMs: record.timing.totalMs,
    errorCode: record.errorCode
  }
}

function matches(item, query) {
  if (query.requestId && item.requestId !== query.requestId) {
    return false
  }
  if (query.state && item.verificationState !== query.state) {
    return false
  }
  if (query.action && item.guardAction !== query.action) {
    return false
  }
  if (query.mode && item.mode !== query.mode) {
    return false
  }
  const time = Date.parse(item.createdAt)
  if (query.from && time < Date.parse(query.from)) {
    return false
  }
  if (query.to && time >= Date.parse(query.to)) {
    return false
  }
  return true
}

/** GET /api/audit 的响应：按 createdAt 降序分页。 */
export function listRecords(query) {
  const page = Math.max(1, Number(query.page) || 1)
  const size = Math.min(100, Math.max(1, Number(query.size) || 20))
  const filtered = records
    .map(toItem)
    .filter((item) => matches(item, query))
    .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
  return {
    items: filtered.slice((page - 1) * size, page * size),
    total: filtered.length,
    page,
    size
  }
}

// ---------- 预置历史记录 ----------

const SEED_PLAN = [
  ['allSupported', 'annotate', 'REAL', null],
  ['partial', 'annotate', 'REAL', null],
  ['noEvidence', 'annotate', 'REAL', null],
  ['noClaims', 'annotate', 'REAL', null],
  ['long', 'annotate', 'REAL', null],
  ['allSupported', 'annotate', 'MOCK', 'SC01'],
  ['partial', 'annotate', 'MOCK', 'SC02'],
  ['partial', 'block', 'MOCK', 'SC02'],
  ['blocked', 'annotate', 'MOCK', 'SC03'],
  ['blocked', 'block', 'MOCK', 'SC03'],
  ['citationInvalid', 'annotate', 'MOCK', 'SC04'],
  ['citationInvalid', 'block', 'MOCK', 'SC04'],
  ['notVerified', 'annotate', 'MOCK', 'SC05'],
  ['notVerified', 'block', 'MOCK', 'SC05'],
  ['noEvidence', 'annotate', 'MOCK', 'SC07'],
  ['verifyTimeout', 'annotate', 'MOCK', 'SC08'],
  ['verifyTimeout', 'block', 'MOCK', 'SC08'],
  ['generationFailed', 'annotate', 'MOCK', 'SC09'],
  ['truncated', 'annotate', 'REAL', null],
  ['xss', 'annotate', 'REAL', null],
  ['allSupported', 'annotate', 'REAL', null],
  ['partial', 'block', 'REAL', null],
  ['generationFailed', 'annotate', 'REAL', null],
  ['blocked', 'block', 'REAL', null]
]

const SEED_INTERVAL_MS = 47 * 60 * 1000

function seed() {
  const base = Date.now() - SEED_PLAN.length * SEED_INTERVAL_MS
  SEED_PLAN.forEach(([caseKey, strategy, mode, scenarioId], index) => {
    const options = {
      strategy,
      mode,
      scenarioId,
      now: new Date(base + index * SEED_INTERVAL_MS),
      overridden: scenarioId !== null && strategy !== 'annotate'
    }
    const built = caseKey === 'generationFailed'
      ? buildGenerationFailure(GENERATION_FAILED_CASE, options)
      : buildExchange(CASES[caseKey], options)
    addRecord(built.record)
  })
}

seed()
