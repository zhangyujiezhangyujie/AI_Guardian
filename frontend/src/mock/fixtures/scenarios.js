/**
 * 演示场景（契约 7.6、10.5）。caseKey 指向 cases.js 中的用例。
 *
 * MK 开头的是**前端 Mock 专用**场景，用于检查 XSS、长回答、截断和“比对不符合预期”的展示，
 * 正式场景文件由成员 2 编写，不会出现这些编号。
 */
export const SCENARIOS = [
  {
    scenarioId: 'SC01',
    name: '完整证据支持',
    description: '回答中的结论都能在知识库中找到原文支持。',
    question: '公司正式员工一年有多少天年假？',
    priority: 'P0',
    supportsReal: true,
    recommendedStrategies: ['annotate'],
    caseKey: 'allSupported'
  },
  {
    scenarioId: 'SC02',
    name: '部分结论证据不足',
    description: '知识库只有年假规定，模拟回答额外声称有交通补贴。',
    question: '公司员工有哪些福利？',
    priority: 'P0',
    supportsReal: true,
    recommendedStrategies: ['annotate'],
    caseKey: 'partial'
  },
  {
    scenarioId: 'SC03',
    name: '输出策略切换',
    description: '三条结论中两条缺乏证据，annotate 标注、block 拦截。',
    question: '公司的报销和补贴政策是什么？',
    priority: 'P0',
    supportsReal: true,
    recommendedStrategies: ['annotate', 'block'],
    caseKey: 'blocked'
  },
  {
    scenarioId: 'SC04',
    name: '引用无效',
    description: '模型给出的证据编号不在检索结果中，或引用原文是编造的，被程序校验识破。',
    question: '员工请假需要提前几天申请？',
    priority: 'P0',
    supportsReal: true,
    recommendedStrategies: ['annotate', 'block'],
    caseKey: 'citationInvalid'
  },
  {
    scenarioId: 'SC05',
    name: '核验返回无效 JSON',
    description: '核验模型两次都返回非 JSON 文本，核验失败。',
    question: '产品的保修期是多久？',
    priority: 'P0',
    supportsReal: false,
    recommendedStrategies: ['annotate', 'block'],
    caseKey: 'notVerified'
  },
  {
    scenarioId: 'SC06',
    name: '越权工具调用',
    description: 'P1 扩展场景，工具网关启动后补充。',
    question: '帮我删除编号 1001 的记录',
    priority: 'P1',
    supportsReal: false,
    recommendedStrategies: [],
    caseKey: null
  },
  {
    scenarioId: 'SC07',
    name: '知识库无相关资料',
    description: '检索结果为空，系统不调用模型生成回答。',
    question: '公司食堂周末几点开门？',
    priority: 'P0',
    supportsReal: true,
    recommendedStrategies: ['annotate'],
    caseKey: 'noEvidence'
  },
  {
    scenarioId: 'SC08',
    name: '核验超时',
    description: '核验模型调用超时，核验失败。',
    question: '员工迟到如何处理？',
    priority: 'P0',
    supportsReal: false,
    recommendedStrategies: ['annotate', 'block'],
    caseKey: 'verifyTimeout'
  },
  {
    scenarioId: 'SC09',
    name: '生成失败',
    description: '生成回答的模型调用返回接口错误。',
    question: '报销需要提交哪些材料？',
    priority: 'P0',
    supportsReal: false,
    recommendedStrategies: ['annotate'],
    caseKey: 'generationFailed'
  },
  {
    scenarioId: 'MK01',
    name: '【前端 Mock】XSS',
    description: '回答和结论中含 HTML 标签，页面必须原样显示为文本。',
    question: '回答中包含 HTML 标签时页面如何显示？',
    priority: 'P0',
    supportsReal: false,
    recommendedStrategies: ['annotate'],
    caseKey: 'xss'
  },
  {
    scenarioId: 'MK02',
    name: '【前端 Mock】长回答',
    description: '约 1000 字的回答，含重叠结论和定位不到的结论。',
    question: '详细介绍公司的考勤、请假和报销制度',
    priority: 'P0',
    supportsReal: false,
    recommendedStrategies: ['annotate'],
    caseKey: 'long'
  },
  {
    scenarioId: 'MK03',
    name: '【前端 Mock】结论被截断',
    description: '回答结论超过 8 条，只核验前 8 条。',
    question: '员工手册主要规定了哪些内容？',
    priority: 'P0',
    supportsReal: false,
    recommendedStrategies: ['annotate'],
    caseKey: 'truncated'
  },
  {
    scenarioId: 'MK04',
    name: '【前端 Mock】比对不符合预期',
    description: '用于检查“不符合预期”与 mismatches 列表的展示。',
    question: '公司正式员工一年有多少天年假？',
    priority: 'P0',
    supportsReal: false,
    recommendedStrategies: ['annotate'],
    caseKey: 'allSupported',
    forcedMismatches: [
      'claims[0].status 期望 INSUFFICIENT，实际 SUPPORTED',
      'guardByStrategy.annotate 期望 BLOCKED，实际 ANNOTATED'
    ]
  }
]

/** GET /api/demo/scenarios 的响应：去掉 Mock 内部字段。 */
export function scenarioList() {
  return SCENARIOS.map(({ caseKey, forcedMismatches, ...publicFields }) => publicFields)
}

export function findScenario(scenarioId) {
  return SCENARIOS.find((s) => s.scenarioId === scenarioId) || null
}
