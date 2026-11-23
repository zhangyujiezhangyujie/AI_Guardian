/**
 * 枚举 → 中文文案、颜色的唯一映射表（任务卡 6.5、6.7、6.9）。
 *
 * 页面和组件不得散落硬编码的枚举文案或颜色，一律从这里取。
 * 枚举值来自接口契约 3.1，不得增删改名。
 *
 * 颜色约定（任务卡 6.9），取值为 Element Plus 的 type：
 * - 有证据：success
 * - 证据不足：warning
 * - 拦截与失败：danger
 * - 其他：info
 */

/** ClaimStatus（契约 3.1）。 */
export const CLAIM_STATUS = {
  SUPPORTED: {
    text: '有证据',
    type: 'success'
  },
  INSUFFICIENT: {
    text: '证据不足',
    type: 'warning'
  }
}

/** InsufficientReason（契约 3.1），文案为任务卡 6.5 默认值，成员 3 可修改。 */
export const INSUFFICIENT_REASON = {
  MODEL_UNSUPPORTED: {
    text: '知识库中未找到支持该结论的内容',
    type: 'warning'
  },
  MODEL_NO_CITATION: {
    text: '核验模型认为有依据，但未给出引用',
    type: 'warning'
  },
  EVIDENCE_NOT_IN_CONTEXT: {
    text: '引用的证据编号不在本次检索结果中',
    type: 'warning'
  },
  QUOTE_TOO_SHORT: {
    text: '引用原文过短，无法作为有效证据',
    type: 'warning'
  },
  QUOTE_NOT_FOUND: {
    text: '引用的原文在证据中不存在，可能是编造的引用',
    type: 'warning'
  }
}

/** GuardAction（契约 3.1）。 */
export const GUARD_ACTION = {
  ANNOTATED: {
    text: '已标注',
    type: 'info'
  },
  BLOCKED: {
    text: '已拦截',
    type: 'danger'
  },
  NOT_VERIFIED: {
    text: '未核验',
    type: 'danger'
  },
  NO_CLAIMS: {
    text: '无可核验结论',
    type: 'info'
  },
  NO_EVIDENCE: {
    text: '无相关资料',
    type: 'info'
  }
}

/** VerificationState（契约 3.1）。 */
export const VERIFICATION_STATE = {
  COMPLETED: {
    text: '核验完成',
    type: 'info'
  },
  PARTIAL: {
    text: '部分核验（已截断）',
    type: 'warning'
  },
  FAILED: {
    text: '核验失败',
    type: 'danger'
  },
  NO_CLAIMS: {
    text: '无事实性结论',
    type: 'info'
  },
  NO_EVIDENCE: {
    text: '无相关资料',
    type: 'info'
  }
}

/** VerificationFailureReason（契约 3.1）。 */
export const VERIFICATION_FAILURE_REASON = {
  LLM_TIMEOUT: {
    text: '核验模型调用超时',
    type: 'danger'
  },
  LLM_API_ERROR: {
    text: '核验模型接口错误',
    type: 'danger'
  },
  INVALID_JSON: {
    text: '核验结果无法解析为 JSON',
    type: 'danger'
  },
  SCHEMA_INVALID: {
    text: '核验结果缺少必需结构',
    type: 'danger'
  }
}

/** RunMode（契约 3.1）。 */
export const RUN_MODE = {
  REAL: {
    text: '真实',
    type: 'primary'
  },
  MOCK: {
    text: '模拟',
    type: 'info'
  }
}

/** AuditEventType（契约 3.1），中文见任务卡 6.7。 */
export const AUDIT_EVENT_TYPE = {
  REQUEST_RECEIVED: {
    text: '收到请求',
    type: 'info'
  },
  RETRIEVAL_DONE: {
    text: '完成检索',
    type: 'info'
  },
  GENERATION_DONE: {
    text: '完成生成',
    type: 'info'
  },
  GENERATION_FAILED: {
    text: '生成失败',
    type: 'danger'
  },
  VERIFICATION_DONE: {
    text: '完成核验',
    type: 'info'
  },
  CITATION_CHECKED: {
    text: '完成引用校验',
    type: 'info'
  },
  GUARD_APPLIED: {
    text: '执行输出防护',
    type: 'info'
  },
  REQUEST_COMPLETED: {
    text: '请求完成',
    type: 'success'
  },
  REQUEST_FAILED: {
    text: '请求失败',
    type: 'danger'
  },
  TOOL_CALL_REQUESTED: {
    text: '工具调用请求',
    type: 'info'
  },
  TOOL_CALL_DECIDED: {
    text: '工具网关判定',
    type: 'info'
  },
  TOOL_CALL_EXECUTED: {
    text: '工具已执行',
    type: 'warning'
  }
}

/** ToolDecision（契约 v1.3 第 3.1 节，P1 工具网关）。 */
export const TOOL_DECISION = {
  ALLOW: {
    text: '放行',
    type: 'success'
  },
  DENY: {
    text: '拒绝',
    type: 'danger'
  },
  REQUIRE_APPROVAL: {
    text: '需人工审批',
    type: 'warning'
  }
}

/** ToolDenyReason（契约 v1.3 第 3.1 节），按决策链顺序。 */
export const TOOL_DENY_REASON = {
  TOOL_NOT_ALLOWED: {
    text: '工具不在白名单中',
    type: 'danger'
  },
  PERMISSION_DENIED: {
    text: '当前用户无权使用该工具',
    type: 'danger'
  },
  INVALID_ARGUMENTS: {
    text: '工具参数不符合约束',
    type: 'danger'
  },
  RECORD_NOT_FOUND: {
    text: '目标业务记录不存在',
    type: 'danger'
  },
  BASIS_NOT_FOUND: {
    text: '依据的问答记录不存在',
    type: 'danger'
  },
  UNVERIFIED_BASIS: {
    text: '依据的回答存在证据不足的结论',
    type: 'danger'
  }
}

/** 工具名（契约 v1.3 第 7.10 节工具清单）；未知工具原样显示。 */
export const TOOL_NAME = {
  search_document: {
    text: '检索知识库',
    type: 'info'
  },
  update_record: {
    text: '修改业务记录',
    type: 'warning'
  },
  delete_record: {
    text: '删除业务记录',
    type: 'danger'
  }
}

/** 演示用户（请求头 X-Demo-User，仅用于演示，不是真实身份认证）。 */
export const DEMO_USER = {
  user: {
    text: '普通员工',
    type: 'info'
  },
  manager: {
    text: '部门经理',
    type: 'primary'
  }
}

/** 模拟业务记录状态（契约 v1.3 第 3.11 节 BusinessRecord.status）。 */
export const RECORD_STATUS = {
  OPEN: {
    text: '处理中',
    type: 'primary'
  },
  CLOSED: {
    text: '已关闭',
    type: 'info'
  },
  ARCHIVED: {
    text: '已归档',
    type: 'info'
  }
}

/**
 * 输出策略名（契约 3.5）。策略列表由后端 availableStrategies 动态给出，
 * 这里只为已知策略提供中文名，未知策略原样显示。
 */
export const GUARD_STRATEGY = {
  annotate: {
    text: '标注（annotate）',
    type: 'info'
  },
  block: {
    text: '拦截（block）',
    type: 'info'
  }
}

/** 场景优先级（契约 7.6）：P1 显示“扩展”标签。 */
export const SCENARIO_PRIORITY = {
  P0: {
    text: '核心',
    type: 'info'
  },
  P1: {
    text: '扩展',
    type: 'warning'
  }
}

/**
 * 审计页“模型初判 → 程序校验”列（任务卡 6.7）：ClaimVerification.modelSupported（boolean）。
 * 键为字符串化的值。
 */
export const MODEL_JUDGEMENT = {
  true: {
    text: '有依据',
    type: 'success'
  },
  false: {
    text: '无依据',
    type: 'warning'
  }
}

/**
 * ClaimVerification.quoteExists（Boolean，可空）：null = 未执行校验。
 * 键为字符串化的值。
 */
export const QUOTE_CHECK = {
  true: {
    text: '引用通过',
    type: 'success'
  },
  false: {
    text: '引用未通过',
    type: 'danger'
  },
  null: {
    text: '未校验',
    type: 'info'
  }
}

/**
 * 取某个枚举值的中文文案；未知值（含 null）原样返回，避免页面因后端新增值而报错。
 */
export function labelText(map, value) {
  if (value === undefined || (value === null && !('null' in map))) {
    return ''
  }
  const entry = map[String(value)]
  return entry ? entry.text : String(value)
}

/**
 * 取某个枚举值对应的 Element Plus type；未知值返回 info。
 */
export function labelType(map, value) {
  if (value === undefined || (value === null && !('null' in map))) {
    return 'info'
  }
  const entry = map[String(value)]
  return entry ? entry.type : 'info'
}

/**
 * 生成下拉选项 [{ value, label }]，用于审计页筛选栏等。
 */
export function labelOptions(map) {
  return Object.keys(map).map((value) => ({
    value,
    label: map[value].text
  }))
}
