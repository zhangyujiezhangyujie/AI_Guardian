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
    title: '福利规定',
    chunks: [
      ['社会保险', '公司按国家规定为正式员工缴纳五险一金，缴纳基数以员工上年度月平均工资为准。'],
      ['餐费补贴', '正式员工可按规定申请工作日餐费补贴。'],
      ['节日福利', '公司在春节、中秋节为全体员工发放节日礼品。']
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
