/**
 * 前端开发用的模拟知识库（不是正式知识库，正式内容由成员 2 维护）。
 *
 * 演示要点：福利规定中只有餐费补贴，**没有交通补贴**。
 */

const DOCUMENTS = [
  {
    documentId: 'doc001',
    title: '员工手册',
    chunks: [
      ['总则', '本手册适用于公司全体正式员工及试用期员工。员工应遵守公司各项规章制度，制度文件可在内部知识库中查阅。'],
      ['工作时间', '公司实行标准工时制，工作日为周一至周五，上班时间为9:00至18:00，午休时间为12:00至13:00。'],
      ['年假制度', '正式员工每年享有15天带薪年假，试用期员工不享有年假。'],
      ['入职培训', '新员工入职后需在30天内完成入职培训，培训内容包括公司文化、信息安全和岗位技能。'],
      ['离职', '员工主动离职应提前30天以书面形式通知公司，试用期内应提前3天通知。']
    ]
  },
  {
    documentId: 'doc002',
    title: '报销制度',
    chunks: [
      ['报销范围', '可报销的费用包括差旅费、业务招待费和办公用品采购费，个人消费不予报销。'],
      ['报销时限', '差旅费用需在出差结束后10个工作日内提交报销申请，逾期需部门负责人说明原因。'],
      ['报销材料', '报销时需提交发票原件、费用明细表和审批单，差旅报销还需附行程单。']
    ]
  },
  {
    documentId: 'doc003',
    title: '考勤与请假',
    chunks: [
      ['请假类型', '请假类型包括事假、病假、婚假、产假和丧假，各类假期的天数按国家规定执行。'],
      ['请假流程', '员工请事假需提前3个工作日在OA系统中提交申请，经直属主管审批后生效。'],
      ['迟到处理', '每月迟到累计超过3次的，扣除当月全勤奖；单次迟到超过30分钟按旷工半天处理。']
    ]
  },
  {
    documentId: 'doc004',
    title: '产品保修说明',
    chunks: [
      ['保修期限', '本公司产品自购买之日起享受两年免费保修服务，保修期内凭购买凭证维修。'],
      ['保修范围', '人为损坏、私自拆机及不可抗力造成的故障不在免费保修范围内。']
    ]
  },
  {
    documentId: 'doc005',
    title: '员工福利规定',
    chunks: [
      ['总则与餐费补贴', '为提升员工归属感，公司根据经营情况为员工提供多项福利。本规定适用于公司全体正式员工，试用期员工自转正之日起享受本规定中的福利项目。\n\n福利项目的具体标准和发放方式由人力资源部负责解释。公司可根据经营情况调整福利项目，调整前会提前告知员工。\n\n公司为员工提供餐费补贴，标准为每月300元，随工资发放。餐费补贴按实际出勤折算，当月缺勤超过5天的部分相应扣减。\n\n餐费补贴直接计入工资，员工无需另行申请。员工对餐费补贴金额有疑问的，可向人力资源部查询当月出勤记录。'],
      ['健康体检与节日福利', '公司每年为员工安排一次免费体检，体检项目由公司统一确定。体检时间一般安排在工作日，员工参加体检视为正常出勤。体检结果仅用于员工健康管理，不作为岗位调整的依据。\n\n员工因个人原因未参加统一体检的，可在当年内申请补检一次。新入职员工在转正后参加次年的统一体检。\n\n春节、中秋发放节日礼品，具体形式由公司统一安排。节日礼品在节前发放，员工应在规定时间内领取。\n\n公司在其他传统节日可根据经营情况安排慰问活动，具体以人力资源部通知为准。'],
      ['其他福利', '员工生日当月发放200元生日礼金，随当月工资发放。各部门每季度组织一次团建活动，费用在部门预算内列支。\n\n公司为员工提供必要的办公设备和劳动保护用品。员工因公出差、加班产生的合理费用，按《报销制度》执行。\n\n团建活动应安排在非工作时间或经员工同意的工作时间，不得强制员工自费参加。各部门组织团建应提前报备，活动方案和费用预算需经部门经理审批。公司鼓励员工合理使用福利，福利项目不与考勤处罚相互抵扣。员工对福利发放有异议的，可在发放当月向人力资源部提出复核，人力资源部应在核实后答复。']
    ]
  },
  {
    documentId: 'doc008',
    title: '内部操作规范',
    chunks: [
      ['总则与客户数据管理', '为规范公司内部系统操作，保护客户数据和业务记录安全，制定本规范。本规范适用于所有接触公司业务系统的员工，包括正式员工、试用期员工和外包人员。\n\n员工应按照岗位职责和授权范围操作系统，不得越权访问、修改或删除数据。发现异常操作或数据泄露风险时，应立即向部门经理和信息安全负责人报告。'],
      ['业务记录与账号权限', '删除业务记录需部门经理审批，审批通过后由系统管理员执行。员工不得自行删除记录，也不得通过技术手段规避审批流程。\n\n修改业务记录需留存操作日志，日志内容包括操作人、操作时间和修改原因。操作日志由系统自动记录，任何人不得删除或篡改。']
    ]
  }
]

function chunkId(documentId, index) {
  return `${documentId}_chunk${String(index + 1).padStart(2, '0')}`
}

function chunksOf(doc) {
  return doc.chunks.map(([section, content], index) => ({
    evidenceId: chunkId(doc.documentId, index),
    section,
    content
  }))
}

function contentOf(doc) {
  const parts = [`# ${doc.title}`]
  doc.chunks.forEach(([section, content]) => {
    parts.push(`## ${section}`)
    parts.push(content)
  })
  return parts.join('\n\n')
}

/** GET /api/knowledge/documents 的响应（契约 7.3），按 documentId 升序。 */
export function documentList() {
  return DOCUMENTS.map((doc) => ({
    documentId: doc.documentId,
    title: doc.title,
    fileName: `${doc.documentId}_${doc.title}.md`,
    chunkCount: doc.chunks.length,
    charCount: contentOf(doc).length
  }))
}

/** GET /api/knowledge/documents/{documentId} 的响应（契约 7.4）；不存在返回 null。 */
export function documentDetail(documentId) {
  const doc = DOCUMENTS.find((d) => d.documentId === documentId)
  if (!doc) {
    return null
  }
  return {
    documentId: doc.documentId,
    title: doc.title,
    content: contentOf(doc),
    chunks: chunksOf(doc)
  }
}

/**
 * 返回 Evidence（契约 3.2）；不存在返回 null。
 * 单独查询（契约 7.5）时 score、rank 为 0。
 */
export function evidenceById(evidenceId, score = 0, rank = 0) {
  for (const doc of DOCUMENTS) {
    const chunk = chunksOf(doc).find((c) => c.evidenceId === evidenceId)
    if (chunk) {
      return {
        evidenceId: chunk.evidenceId,
        documentId: doc.documentId,
        documentTitle: doc.title,
        section: chunk.section,
        content: chunk.content,
        score,
        rank
      }
    }
  }
  return null
}
