import { describe, expect, it } from 'vitest'

import {
  AUDIT_EVENT_TYPE,
  CLAIM_STATUS,
  GUARD_ACTION,
  INSUFFICIENT_REASON,
  RUN_MODE,
  TOOL_DECISION,
  TOOL_DENY_REASON,
  VERIFICATION_FAILURE_REASON,
  VERIFICATION_STATE,
  labelOptions,
  labelText,
  labelType
} from '../src/constants/labels'

// 契约 v1.3 第 3.1 节的枚举值，逐字抄录；契约变更时这里同步修改
const CONTRACT_ENUMS = {
  RunMode: [RUN_MODE, ['REAL', 'MOCK']],
  ClaimStatus: [CLAIM_STATUS, ['SUPPORTED', 'INSUFFICIENT']],
  VerificationState: [VERIFICATION_STATE, ['COMPLETED', 'PARTIAL', 'FAILED', 'NO_CLAIMS', 'NO_EVIDENCE']],
  VerificationFailureReason: [VERIFICATION_FAILURE_REASON, ['LLM_TIMEOUT', 'LLM_API_ERROR', 'INVALID_JSON', 'SCHEMA_INVALID']],
  InsufficientReason: [INSUFFICIENT_REASON, [
    'MODEL_UNSUPPORTED',
    'MODEL_NO_CITATION',
    'EVIDENCE_NOT_IN_CONTEXT',
    'QUOTE_TOO_SHORT',
    'QUOTE_NOT_FOUND'
  ]],
  GuardAction: [GUARD_ACTION, ['ANNOTATED', 'BLOCKED', 'NOT_VERIFIED', 'NO_CLAIMS', 'NO_EVIDENCE']],
  AuditEventType: [AUDIT_EVENT_TYPE, [
    'REQUEST_RECEIVED',
    'RETRIEVAL_DONE',
    'GENERATION_DONE',
    'GENERATION_FAILED',
    'VERIFICATION_DONE',
    'CITATION_CHECKED',
    'GUARD_APPLIED',
    'REQUEST_COMPLETED',
    'REQUEST_FAILED',
    'TOOL_CALL_REQUESTED',
    'TOOL_CALL_DECIDED',
    'TOOL_CALL_EXECUTED'
  ]],
  ToolDecision: [TOOL_DECISION, ['ALLOW', 'DENY', 'REQUIRE_APPROVAL']],
  ToolDenyReason: [TOOL_DENY_REASON, [
    'TOOL_NOT_ALLOWED',
    'PERMISSION_DENIED',
    'INVALID_ARGUMENTS',
    'RECORD_NOT_FOUND',
    'BASIS_NOT_FOUND',
    'UNVERIFIED_BASIS'
  ]]
}

const VALID_TYPES = ['primary', 'success', 'warning', 'danger', 'info']

describe('labels 与契约枚举一致', () => {
  Object.entries(CONTRACT_ENUMS).forEach(([name, [map, values]]) => {
    it(`${name} 覆盖且只覆盖契约中的全部取值`, () => {
      expect(Object.keys(map).sort()).toEqual([...values].sort())
    })

    it(`${name} 每项都有中文文案和合法颜色`, () => {
      Object.values(map).forEach((entry) => {
        expect(entry.text.length).toBeGreaterThan(0)
        expect(VALID_TYPES).toContain(entry.type)
      })
    })
  })
})

describe('颜色约定（任务卡 6.9）', () => {
  it('有证据为 success，证据不足为 warning', () => {
    expect(labelType(CLAIM_STATUS, 'SUPPORTED')).toBe('success')
    expect(labelType(CLAIM_STATUS, 'INSUFFICIENT')).toBe('warning')
  })

  it('拦截与失败为 danger', () => {
    expect(labelType(GUARD_ACTION, 'BLOCKED')).toBe('danger')
    expect(labelType(GUARD_ACTION, 'NOT_VERIFIED')).toBe('danger')
    expect(labelType(VERIFICATION_STATE, 'FAILED')).toBe('danger')
  })
})

describe('文案（任务卡 6.5、6.7）', () => {
  it('InsufficientReason 使用任务卡默认文案', () => {
    expect(labelText(INSUFFICIENT_REASON, 'EVIDENCE_NOT_IN_CONTEXT')).toBe('引用的证据编号不在本次检索结果中')
    expect(labelText(INSUFFICIENT_REASON, 'QUOTE_NOT_FOUND')).toBe('引用的原文在证据中不存在，可能是编造的引用')
  })

  it('审计事件使用任务卡中文名', () => {
    expect(labelText(AUDIT_EVENT_TYPE, 'CITATION_CHECKED')).toBe('完成引用校验')
    expect(labelText(AUDIT_EVENT_TYPE, 'GUARD_APPLIED')).toBe('执行输出防护')
  })
})

describe('辅助函数', () => {
  it('未知值原样显示、颜色回退为 info', () => {
    expect(labelText(GUARD_ACTION, 'SOMETHING_NEW')).toBe('SOMETHING_NEW')
    expect(labelType(GUARD_ACTION, 'SOMETHING_NEW')).toBe('info')
  })

  it('null 显示为空串', () => {
    expect(labelText(INSUFFICIENT_REASON, null)).toBe('')
    expect(labelType(INSUFFICIENT_REASON, null)).toBe('info')
  })

  it('labelOptions 生成下拉选项', () => {
    expect(labelOptions(RUN_MODE)).toEqual([
      { value: 'REAL', label: '真实' },
      { value: 'MOCK', label: '模拟' }
    ])
  })
})
