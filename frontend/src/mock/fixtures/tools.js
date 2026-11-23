/**
 * Mock 工具调用结果（契约 v1.3 第 3.11、7.10 节，P1）。
 *
 * 只按场景中直接写出的 decision / denyReason 组装 ToolCallResult 与审计事件，
 * 不实现网关的判断逻辑（判断属于后端）。
 */

import { shanghaiIso } from './ids'

// 与 db/init.sql 初始数据一致
const RECORDS = {
  1001: { recordId: 1001, title: '客户合同归档（华东区）', owner: 'user', status: 'OPEN' },
  1002: { recordId: 1002, title: '季度采购对账单', owner: 'user', status: 'OPEN' },
  1003: { recordId: 1003, title: '供应商资质复核', owner: 'manager', status: 'OPEN' },
  1004: { recordId: 1004, title: '客户投诉处理记录', owner: 'user', status: 'CLOSED' },
  1005: { recordId: 1005, title: '年度设备盘点表', owner: 'manager', status: 'ARCHIVED' }
}

// 契约 7.10 固定文案（由后端返回，Mock 仅模拟后端）
const MESSAGES = {
  ALLOW: '已通过网关检查并执行。',
  REQUIRE_APPROVAL: '该操作需要部门经理审批，网关未执行。',
  TOOL_NOT_ALLOWED: '工具不在白名单中，已拒绝。',
  PERMISSION_DENIED: '当前用户无权使用该工具，已拒绝。',
  INVALID_ARGUMENTS: '工具参数不符合约束，已拒绝。',
  RECORD_NOT_FOUND: '目标业务记录不存在，已拒绝。',
  BASIS_NOT_FOUND: '依据的问答记录不存在，已拒绝。',
  UNVERIFIED_BASIS: '该操作依据的回答存在未经证据支持的结论，已拒绝。'
}

function randomHex(length) {
  let result = ''
  for (let i = 0; i < length; i++) {
    result += Math.floor(Math.random() * 16).toString(16)
  }
  return result
}

/**
 * @param spec 场景中的 toolCall：{ user, toolName, arguments, decision, denyReason }
 * @param basedOnRequestId 本次演示问答的 requestId
 * @param start 审计事件起始时间（接在问答事件之后）
 * @returns {{ result: object, events: object[] }}
 */
export function buildToolCall(spec, basedOnRequestId, start) {
  const toolCallId = `tool_${randomHex(12)}`
  const recordId = spec.arguments ? spec.arguments.recordId : undefined
  const base = Number.isInteger(recordId) ? RECORDS[recordId] : undefined
  const message = spec.decision === 'DENY' ? MESSAGES[spec.denyReason] : MESSAGES[spec.decision]

  const result = {
    toolCallId,
    basedOnRequestId,
    user: spec.user,
    toolName: spec.toolName,
    arguments: structuredClone(spec.arguments),
    decision: spec.decision,
    denyReason: spec.denyReason ?? null,
    message,
    executed: false,
    result: null,
    record: base
      ? { ...base, deleted: false, updatedAt: '2026-11-22T10:00:00.000+08:00' }
      : null,
    createdAt: shanghaiIso(start)
  }

  const events = [
    {
      eventType: 'TOOL_CALL_REQUESTED',
      success: true,
      durationMs: null,
      errorCode: null,
      detail: {
        toolCallId,
        toolName: spec.toolName,
        user: spec.user,
        basedOnRequestId
      },
      createdAt: shanghaiIso(start)
    },
    {
      eventType: 'TOOL_CALL_DECIDED',
      success: true,
      durationMs: 1,
      errorCode: null,
      detail: {
        toolCallId,
        decision: spec.decision,
        denyReason: spec.denyReason ?? null
      },
      createdAt: shanghaiIso(new Date(start.getTime() + 1))
    }
  ]

  return {
    result,
    events
  }
}
