/**
 * Mock 用例定义（任务卡 6.4）：纯数据。
 *
 * - claims 写的是**终判结果**（CitationValidator 之后）；claimId、startIndex、endIndex、计数字段由 builder.js 机械生成。
 * - guards 按策略**直接写出**处理结果，前端代码（包括 Mock）不做“比例与阈值比较”。
 *   结果与契约 6.1 判定表、10.5 场景清单一致，由单元测试核对。
 * - 后端未完成时用于开发；拿到成员 1 的真实示例后，形状由 tests/mockFixtures.test.js 与 docs/examples 比对。
 */

import { LONG_ANSWER } from './longAnswer'

// ---------- 结论构造（终判结果） ----------

function supported(claim, originalText, evidenceId, quote) {
  return {
    claim,
    originalText,
    modelSupported: true,
    status: 'SUPPORTED',
    evidenceId,
    quote,
    quoteExists: true,
    insufficientReason: null
  }
}

function unsupported(claim, originalText) {
  return {
    claim,
    originalText,
    modelSupported: false,
    status: 'INSUFFICIENT',
    evidenceId: null,
    quote: null,
    quoteExists: null,
    insufficientReason: 'MODEL_UNSUPPORTED'
  }
}

function noCitation(claim, originalText, evidenceId, quote) {
  return {
    claim,
    originalText,
    modelSupported: true,
    status: 'INSUFFICIENT',
    evidenceId,
    quote,
    quoteExists: null,
    insufficientReason: 'MODEL_NO_CITATION'
  }
}

/** 模型判断有依据并给出引用，但程序校验失败（契约 5.3 终判表）。 */
function citationFailed(claim, originalText, evidenceId, quote, reason) {
  return {
    claim,
    originalText,
    modelSupported: true,
    status: 'INSUFFICIENT',
    evidenceId,
    quote,
    quoteExists: false,
    insufficientReason: reason
  }
}

// ---------- 处理结果（按策略） ----------

const SHOWN = (action) => ({
  action,
  visible: true
})

const HIDDEN = (action) => ({
  action,
  visible: false
})

const SAME_FOR_BOTH = (spec) => ({
  annotate: spec,
  block: spec
})

// ---------- 用例 ----------

export const CASES = {
  /** SC01：全部有证据 → ANNOTATED + M1 */
  allSupported: {
    question: '公司正式员工一年有多少天年假？',
    evidence: [
      ['doc001_chunk03', 4.52],
      ['doc001_chunk05', 1.87]
    ],
    answer: '根据《员工手册》，正式员工每年享有15天带薪年假，试用期员工不享有年假。',
    verificationState: 'COMPLETED',
    failureReason: null,
    truncated: false,
    llmCalls: 1,
    claims: [
      supported('正式员工每年享有15天带薪年假', '正式员工每年享有15天带薪年假', 'doc001_chunk03', '正式员工每年享有15天带薪年假'),
      supported('试用期员工不享有年假', '试用期员工不享有年假', 'doc001_chunk03', '试用期员工不享有年假')
    ],
    guards: SAME_FOR_BOTH(SHOWN('ANNOTATED')),
    timing: [21, 3812, 6240]
  },

  /** SC02：部分无证据 → ANNOTATED + M2（与契约 3.7 示例一致） */
  partial: {
    question: '公司员工有哪些福利？',
    evidence: [
      ['doc005_chunk01', 2.2112],
      ['doc005_chunk03', 2.1641],
      ['doc005_chunk02', 1.4327]
    ],
    answer: '公司每月提供 300 元餐费补贴，每月还提供 2,000 元交通补贴。',
    verificationState: 'COMPLETED',
    failureReason: null,
    truncated: false,
    llmCalls: 1,
    claims: [
      supported('公司每月提供 300 元餐费补贴', '公司每月提供 300 元餐费补贴', 'doc005_chunk01', '公司为员工提供餐费补贴，标准为每月300元，随工资发放。'),
      unsupported('公司每月还提供 2,000 元交通补贴', '每月还提供 2,000 元交通补贴')
    ],
    // 比例 0.5 不大于阈值 0.5，block 也不拦截（契约 10.5）
    guards: SAME_FOR_BOTH(SHOWN('ANNOTATED')),
    timing: [18, 3, 5]
  },

  /** 结论超过 8 条被截断 → PARTIAL + ANNOTATED + M2P */
  truncated: {
    question: '员工手册主要规定了哪些内容？',
    evidence: [
      ['doc001_chunk02', 3.91],
      ['doc001_chunk03', 3.42],
      ['doc001_chunk05', 2.76]
    ],
    answer: '员工手册主要包括以下内容：工作日为周一至周五；上班时间为9:00至18:00；午休时间为12:00至13:00；'
      + '正式员工每年享有15天带薪年假；试用期员工不享有年假；员工主动离职应提前30天以书面形式通知公司；'
      + '试用期内应提前3天通知；员工每年可享受一次免费体检；入职满五年的员工可额外获得5天年假。',
    verificationState: 'PARTIAL',
    failureReason: null,
    truncated: true,
    llmCalls: 1,
    claims: [
      supported('工作日为周一至周五', '工作日为周一至周五', 'doc001_chunk02', '工作日为周一至周五'),
      supported('上班时间为9:00至18:00', '上班时间为9:00至18:00', 'doc001_chunk02', '上班时间为9:00至18:00'),
      supported('午休时间为12:00至13:00', '午休时间为12:00至13:00', 'doc001_chunk02', '午休时间为12:00至13:00'),
      supported('正式员工每年享有15天带薪年假', '正式员工每年享有15天带薪年假', 'doc001_chunk03', '正式员工每年享有15天带薪年假'),
      supported('试用期员工不享有年假', '试用期员工不享有年假', 'doc001_chunk03', '试用期员工不享有年假'),
      supported('员工主动离职应提前30天书面通知公司', '员工主动离职应提前30天以书面形式通知公司', 'doc001_chunk05', '员工主动离职应提前30天以书面形式通知公司'),
      supported('试用期内离职应提前3天通知', '试用期内应提前3天通知', 'doc001_chunk05', '试用期内应提前3天通知'),
      unsupported('员工每年可享受一次免费体检', '员工每年可享受一次免费体检')
    ],
    // 比例 0.125，block 也不拦截
    guards: SAME_FOR_BOTH(SHOWN('ANNOTATED')),
    timing: [24, 5120, 9873]
  },

  /** SC03：3 条结论 1 条有证据 → annotate 标注，block 拦截（0.6667） */
  blocked: {
    question: '公司的报销和补贴政策是什么？',
    evidence: [
      ['doc002_chunk02', 3.88],
      ['doc005_chunk02', 2.35],
      ['doc002_chunk01', 2.11]
    ],
    answer: '差旅费用需在出差结束后10个工作日内提交报销申请。员工每月可领取500元通讯补贴，加班满2小时还可报销打车费用。',
    verificationState: 'COMPLETED',
    failureReason: null,
    truncated: false,
    llmCalls: 1,
    claims: [
      supported('差旅费用需在出差结束后10个工作日内报销', '差旅费用需在出差结束后10个工作日内提交报销申请', 'doc002_chunk02', '差旅费用需在出差结束后10个工作日内提交报销申请'),
      unsupported('员工每月可领取500元通讯补贴', '员工每月可领取500元通讯补贴'),
      unsupported('加班满2小时可报销打车费用', '加班满2小时还可报销打车费用')
    ],
    guards: {
      annotate: SHOWN('ANNOTATED'),
      block: HIDDEN('BLOCKED')
    },
    timing: [19, 4, 6]
  },

  /** SC04：引用无效 → c1 证据编号不在检索结果中，c2 引用原文编造，c3 正确；block 下拦截（0.6667） */
  citationInvalid: {
    question: '员工请假需要提前几天申请？',
    evidence: [
      ['doc003_chunk02', 4.07],
      ['doc003_chunk01', 2.64]
    ],
    answer: '员工请事假需提前3个工作日在系统中提交申请，病假可在返岗后补交医院证明，并经直属主管审批后生效。',
    verificationState: 'COMPLETED',
    failureReason: null,
    truncated: false,
    llmCalls: 1,
    claims: [
      citationFailed('员工请事假需提前3个工作日申请', '员工请事假需提前3个工作日在系统中提交申请', 'doc009_chunk01', '员工请事假需提前3个工作日在OA系统中提交申请', 'EVIDENCE_NOT_IN_CONTEXT'),
      citationFailed('病假可在返岗后补交医院证明', '病假可在返岗后补交医院证明', 'doc003_chunk01', '病假可在返岗后三日内补交医院证明', 'QUOTE_NOT_FOUND'),
      supported('请假经直属主管审批后生效', '经直属主管审批后生效', 'doc003_chunk02', '经直属主管审批后生效')
    ],
    guards: {
      annotate: SHOWN('ANNOTATED'),
      block: HIDDEN('BLOCKED')
    },
    timing: [17, 4, 5]
  },

  /** SC05：核验返回无效 JSON（两次）→ annotate 展示带警告（M3），block 暂停展示（M4） */
  notVerified: {
    question: '产品的保修期是多久？',
    evidence: [
      ['doc004_chunk01', 4.73],
      ['doc004_chunk02', 2.08]
    ],
    answer: '本公司产品自购买之日起享受两年免费保修服务，人为损坏不在免费保修范围内。',
    verificationState: 'FAILED',
    failureReason: 'INVALID_JSON',
    truncated: false,
    llmCalls: 2,
    claims: [],
    guards: {
      annotate: SHOWN('NOT_VERIFIED'),
      block: HIDDEN('NOT_VERIFIED')
    },
    timing: [16, 3, 9]
  },

  /** SC08：核验超时 */
  verifyTimeout: {
    question: '员工迟到如何处理？',
    evidence: [
      ['doc003_chunk03', 4.41]
    ],
    answer: '每月迟到累计超过3次的将扣除当月全勤奖，单次迟到超过30分钟按旷工半天处理。',
    verificationState: 'FAILED',
    failureReason: 'LLM_TIMEOUT',
    truncated: false,
    llmCalls: 1,
    claims: [],
    guards: {
      annotate: SHOWN('NOT_VERIFIED'),
      block: HIDDEN('NOT_VERIFIED')
    },
    timing: [15, 3, 2012]
  },

  /** SC07：知识库无相关资料 → NO_EVIDENCE（未调用模型） */
  noEvidence: {
    question: '公司食堂周末几点开门？',
    evidence: [],
    answer: null,
    verificationState: 'NO_EVIDENCE',
    failureReason: null,
    truncated: false,
    llmCalls: 0,
    claims: [],
    guards: SAME_FOR_BOTH(SHOWN('NO_EVIDENCE')),
    timing: [12, null, null]
  },

  /** 回答中没有事实性结论 → NO_CLAIMS + M5 */
  noClaims: {
    question: '我应该怎样向这个系统提问？',
    evidence: [
      ['doc001_chunk01', 0.92]
    ],
    answer: '您可以直接用自然语言描述想了解的制度问题，例如“年假有多少天”，系统会检索知识库后作答，并逐条标注结论是否有证据支持。',
    verificationState: 'NO_CLAIMS',
    failureReason: null,
    truncated: false,
    llmCalls: 1,
    claims: [],
    guards: SAME_FOR_BOTH(SHOWN('NO_CLAIMS')),
    timing: [14, 2950, 4102]
  },

  /** XSS 测试（任务卡 6.10）：回答、结论文本中都含 HTML，必须原样显示为文本 */
  xss: {
    question: '回答中包含 HTML 标签时页面如何显示？',
    evidence: [
      ['doc001_chunk01', 1.35]
    ],
    answer: '以下内容用于测试页面安全：<img src=x onerror=alert(1)>，以及<script>alert(\'xss\')</script>。本手册适用于公司全体正式员工及试用期员工。',
    verificationState: 'COMPLETED',
    failureReason: null,
    truncated: false,
    llmCalls: 1,
    claims: [
      unsupported('<img src=x onerror=alert(1)>', '<img src=x onerror=alert(1)>'),
      supported('本手册适用于公司全体正式员工及试用期员工', '本手册适用于公司全体正式员工及试用期员工', 'doc001_chunk01', '本手册适用于公司全体正式员工及试用期员工')
    ],
    guards: SAME_FOR_BOTH(SHOWN('ANNOTATED')),
    timing: [13, 3, 4]
  },

  /**
   * 长回答（任务卡 6.10）：约 1000 字，含换行。
   * 同时覆盖高亮算法边界：c5 与 c4 区间重叠（高亮时跳过，结论表仍显示）；c7 的原文在回答中找不到（下标为 null）。
   */
  long: {
    question: '详细介绍公司的考勤、请假和报销制度',
    evidence: [
      ['doc003_chunk02', 3.96],
      ['doc003_chunk03', 3.71],
      ['doc002_chunk02', 3.15]
    ],
    answer: LONG_ANSWER,
    verificationState: 'COMPLETED',
    failureReason: null,
    truncated: false,
    llmCalls: 1,
    claims: [
      supported('单次迟到超过30分钟按旷工半天处理', '单次迟到超过30分钟按旷工半天处理', 'doc003_chunk03', '单次迟到超过30分钟按旷工半天处理'),
      unsupported('年度考勤优秀的员工可获得额外3天带薪假期', '年度考勤优秀的员工可获得额外3天带薪假期'),
      supported('员工请事假需提前3个工作日在OA系统中提交申请', '员工请事假需提前3个工作日在OA系统中提交申请', 'doc003_chunk02', '员工请事假需提前3个工作日在OA系统中提交申请'),
      supported('每月迟到累计超过3次的扣除当月全勤奖', '每月迟到累计超过3次的，扣除当月全勤奖', 'doc003_chunk03', '每月迟到累计超过3次的，扣除当月全勤奖'),
      citationFailed('迟到会扣除全勤奖', '扣除当月全勤奖', 'doc003_chunk03', '全勤奖', 'QUOTE_TOO_SHORT'),
      supported('差旅费用需在出差结束后10个工作日内提交报销申请', '差旅费用需在出差结束后10个工作日内提交报销申请', 'doc002_chunk02', '差旅费用需在出差结束后10个工作日内提交报销申请'),
      noCitation('差旅报销需附行程单', '差旅报销还要附上行程单', 'doc002_chunk03', null)
    ],
    // 比例 3/7 ≈ 0.4286，block 也不拦截
    guards: SAME_FOR_BOTH(SHOWN('ANNOTATED')),
    timing: [26, 9874, 12510]
  }
}

/**
 * SC06（P1）：模型声称员工可直接删除记录、无需审批（与 doc008 相矛盾，2 条证据不足）；
 * 模型另外提出的工具调用见 scenarios.js 中 SC06 的 toolCall。
 */
CASES.toolDenied = {
  question: '帮我删除编号 1001 的记录',
  evidence: [
    ['doc008_chunk02', 2.4384],
    ['doc008_chunk01', 1.3969]
  ],
  answer: '好的，已为您删除编号 1001 的记录。员工可以直接删除自己负责的业务记录，无需部门经理审批。',
  verificationState: 'COMPLETED',
  failureReason: null,
  truncated: false,
  llmCalls: 1,
  claims: [
    unsupported('员工可以直接删除自己负责的业务记录', '员工可以直接删除自己负责的业务记录'),
    unsupported('删除业务记录无需部门经理审批', '无需部门经理审批')
  ],
  // 比例 1.0：annotate 标注，block 拦截（与 test-cases/SC06.json 一致）
  guards: {
    annotate: SHOWN('ANNOTATED'),
    block: HIDDEN('BLOCKED')
  },
  timing: [15, 4, 6]
}

/** SC09：生成回答的模型调用失败 → 502 LLM_GENERATION_FAILED（契约 5.1 步骤 4） */
export const GENERATION_FAILED_CASE = {
  question: '报销需要提交哪些材料？',
  evidence: [
    ['doc002_chunk03', 4.25],
    ['doc002_chunk01', 2.37]
  ],
  timing: [18, 37, null]
}
