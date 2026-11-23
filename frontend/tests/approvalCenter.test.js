// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

const listApprovals = vi.fn()
const approveApproval = vi.fn()
const rejectApproval = vi.fn()
const executeTool = vi.fn()

vi.mock('../src/api/approvals.js', () => ({
  listApprovals: (...a) => listApprovals(...a),
  approveApproval: (...a) => approveApproval(...a),
  rejectApproval: (...a) => rejectApproval(...a)
}))
vi.mock('../src/api/tools.js', () => ({
  executeTool: (...a) => executeTool(...a)
}))

const { mount } = await import('./helpers/mount')
const { default: ApprovalCenterView } = await import('../src/views/ApprovalCenterView.vue')
const { default: ToolCallCard } = await import('../src/components/ToolCallCard.vue')
const { demoUser } = await import('../src/stores/demoUser')

let mounted = null

async function flush() {
  for (let i = 0; i < 6; i++) {
    await Promise.resolve()
    await nextTick()
  }
  await new Promise((resolve) => setTimeout(resolve, 0))
  await nextTick()
}

function approval(overrides = {}) {
  return {
    approvalId: 'apr_aaaaaaaaaaaa',
    toolCallId: 'tool_aaaaaaaaaaaa',
    toolName: 'delete_record',
    arguments: { recordId: 1001 },
    requester: 'admin',
    basedOnRequestId: null,
    status: 'PENDING',
    approver: null,
    comment: null,
    failureReason: null,
    result: null,
    record: null,
    createdAt: '2026-11-23T10:00:00.000+08:00',
    expiresAt: '2026-11-24T10:00:00.000+08:00',
    decidedAt: null,
    ...overrides
  }
}

function buttons(text, root = mounted.el) {
  return [...root.querySelectorAll('button')].filter((b) => b.textContent.trim() === text)
}

beforeEach(() => {
  listApprovals.mockReset()
  approveApproval.mockReset()
  rejectApproval.mockReset()
  executeTool.mockReset()
  demoUser.current = 'admin'
})

afterEach(() => {
  if (mounted) {
    mounted.unmount()
    mounted = null
  }
  document.body.innerHTML = ''
})

describe('ApprovalCenterView', () => {
  it('加载审批单列表；待审批行显示批准与驳回；按 focus 高亮', async () => {
    listApprovals.mockResolvedValue([approval(), approval({ approvalId: 'apr_bbbbbbbbbbbb', status: 'REJECTED', approver: 'manager', comment: '不同意' })])
    mounted = await mount(ApprovalCenterView, {}, { path: '/approvals?focus=apr_aaaaaaaaaaaa' })
    await flush()
    expect(listApprovals).toHaveBeenCalledWith('')
    const rows = mounted.el.querySelectorAll('.approval-table .el-table__body-wrapper .el-table__row')
    expect(rows).toHaveLength(2)
    expect(rows[0].classList.contains('is-focused')).toBe(true)
    expect(rows[0].textContent).toContain('待审批')
    expect(rows[0].textContent).toContain('系统管理员')
    expect(rows[1].textContent).toContain('已驳回')
    expect(rows[1].textContent).toContain('不同意')
    expect(buttons('批准')).toHaveLength(1)
  })

  it('状态筛选按状态重新查询', async () => {
    listApprovals.mockResolvedValue([])
    mounted = await mount(ApprovalCenterView, {}, { path: '/approvals' })
    await flush()
    const pending = [...mounted.el.querySelectorAll('.list-header .el-radio-button')].find((e) => e.textContent.includes('待审批'))
    pending.querySelector('input').click()
    await flush()
    expect(listApprovals).toHaveBeenLastCalledWith('PENDING')
  })

  it('发起工具调用：按工具组装参数，以当前演示身份提交，显示结果卡片', async () => {
    listApprovals.mockResolvedValue([])
    executeTool.mockResolvedValue({
      toolCallId: 'tool_cccccccccccc', basedOnRequestId: null, user: 'admin', toolName: 'delete_record', arguments: { recordId: 1001 },
      decision: 'REQUIRE_APPROVAL', denyReason: null, message: '该操作需要部门经理审批，网关未执行。', executed: false, result: null,
      record: { recordId: 1001, title: '客户合同归档（华东区）', owner: 'user', status: 'OPEN', deleted: false, updatedAt: '2026-11-22T10:00:00.000+08:00' },
      approvalId: 'apr_cccccccccccc', createdAt: '2026-11-23T10:00:00.000+08:00'
    })
    mounted = await mount(ApprovalCenterView, {}, { path: '/approvals' })
    await flush()
    buttons('提交')[0].click()
    await flush()
    expect(executeTool).toHaveBeenCalledWith({ toolName: 'delete_record', arguments: { recordId: 1001 }, basedOnRequestId: null }, 'admin')
    expect(mounted.el.querySelector('.tool-call-card').textContent).toContain('需人工审批')
    expect(mounted.el.querySelector('.tool-call-card__approval').textContent).toContain('apr_cccccccccccc')
    // 提交后刷新列表
    expect(listApprovals).toHaveBeenCalledTimes(2)
  })

  it('批准：以当前身份和意见调用接口，显示处理结果与记录状态', async () => {
    listApprovals.mockResolvedValue([approval()])
    approveApproval.mockResolvedValue(approval({
      status: 'EXECUTED', approver: 'manager', comment: '同意', result: { recordId: 1001 },
      record: { recordId: 1001, title: '客户合同归档（华东区）', owner: 'user', status: 'OPEN', deleted: true, updatedAt: '2026-11-23T10:05:00.000+08:00' }
    }))
    demoUser.current = 'manager'
    mounted = await mount(ApprovalCenterView, {}, { path: '/approvals' })
    await flush()
    buttons('批准')[0].click()
    await flush()
    const textarea = document.querySelector('.decision-dialog textarea')
    textarea.value = ' 同意 '
    textarea.dispatchEvent(new Event('input'))
    await flush()
    buttons('确认批准', document.body)[0].click()
    await flush()
    expect(approveApproval).toHaveBeenCalledWith('apr_aaaaaaaaaaaa', 'manager', '同意')
    const alert = mounted.el.querySelector('.last-decision')
    expect(alert.textContent).toContain('已批准并执行')
    expect(alert.textContent).toContain('已删除')
  })

  it('后端拒绝（如自批）时不显示处理结果', async () => {
    listApprovals.mockResolvedValue([approval({ requester: 'manager' })])
    approveApproval.mockRejectedValue({ errorCode: 'APPROVAL_FORBIDDEN', message: '无权处理该审批单：申请人不能审批自己的申请。', requestId: null, httpStatus: 403 })
    demoUser.current = 'manager'
    mounted = await mount(ApprovalCenterView, {}, { path: '/approvals' })
    await flush()
    buttons('批准')[0].click()
    await flush()
    buttons('确认批准', document.body)[0].click()
    await flush()
    expect(approveApproval).toHaveBeenCalledTimes(1)
    expect(mounted.el.querySelector('.last-decision')).toBeNull()
  })

  it('驳回调用驳回接口', async () => {
    listApprovals.mockResolvedValue([approval()])
    rejectApproval.mockResolvedValue(approval({ status: 'REJECTED', approver: 'manager' }))
    demoUser.current = 'manager'
    mounted = await mount(ApprovalCenterView, {}, { path: '/approvals' })
    await flush()
    buttons('驳回')[0].click()
    await flush()
    buttons('确认驳回', document.body)[0].click()
    await flush()
    expect(rejectApproval).toHaveBeenCalledWith('apr_aaaaaaaaaaaa', 'manager', '')
    expect(mounted.el.querySelector('.last-decision').textContent).toContain('已驳回')
  })
})

describe('ToolCallCard 审批单链接', () => {
  it('有 approvalId 时链接到审批中心并定位', async () => {
    mounted = await mount(ToolCallCard, { toolCall: {
      toolCallId: 'tool_cccccccccccc', basedOnRequestId: null, user: 'admin', toolName: 'delete_record', arguments: { recordId: 1001 },
      decision: 'REQUIRE_APPROVAL', denyReason: null, message: '该操作需要部门经理审批，网关未执行。', executed: false, result: null,
      record: null, approvalId: 'apr_cccccccccccc', createdAt: '2026-11-23T10:00:00.000+08:00'
    } })
    const link = mounted.el.querySelector('.tool-call-card__approval a')
    expect(link.getAttribute('href')).toBe('/approvals?focus=apr_cccccccccccc')
  })
})
