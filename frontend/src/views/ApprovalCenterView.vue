<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

import { approveApproval, listApprovals, rejectApproval } from '@/api/approvals'
import { executeTool } from '@/api/tools'
import ToolCallCard from '@/components/ToolCallCard.vue'
import {
  APPROVAL_FAILURE_REASON,
  APPROVAL_STATUS,
  DEMO_USER,
  RECORD_STATUS,
  TOOL_NAME,
  labelText,
  labelType
} from '@/constants/labels'
import { DEMO_USERS, demoUser } from '@/stores/demoUser'
import { formatShortTime } from '@/utils/time'

/**
 * 审批中心（契约 v1.4 第 7.11 节，P1 简化审批）。
 * 审批权限、禁止自批、过期、批准后的重新检查全部由后端判断；前端只提交操作并展示后端结果。
 */

const route = useRoute()

const TOOL_OPTIONS = ['delete_record', 'update_record', 'search_document']

const STATUS_FILTERS = ['', 'PENDING', 'EXECUTED', 'FAILED', 'REJECTED', 'EXPIRED']

const form = reactive({
  toolName: 'delete_record',
  recordId: 1001,
  status: 'CLOSED',
  keyword: '删除业务记录',
  basedOnRequestId: ''
})

const submitting = ref(false)
const lastToolCall = ref(null)

const statusFilter = ref('')
const approvals = ref([])
const loading = ref(false)
const loadError = ref('')

const dialog = reactive({
  visible: false,
  action: 'approve',
  approval: null,
  comment: '',
  submitting: false
})
const lastDecision = ref(null)

const focusId = computed(() => route.query.focus || null)

function buildArguments() {
  if (form.toolName === 'search_document') {
    return { keyword: form.keyword }
  }
  if (form.toolName === 'update_record') {
    return { recordId: form.recordId, status: form.status }
  }
  return { recordId: form.recordId }
}

async function submitToolCall() {
  submitting.value = true
  try {
    const basedOn = form.basedOnRequestId.trim()
    lastToolCall.value = await executeTool({
      toolName: form.toolName,
      arguments: buildArguments(),
      basedOnRequestId: basedOn || null
    }, demoUser.current)
    await loadApprovals()
  } catch (error) {
    lastToolCall.value = null
  } finally {
    submitting.value = false
  }
}

async function loadApprovals() {
  loading.value = true
  loadError.value = ''
  try {
    approvals.value = await listApprovals(statusFilter.value)
  } catch (error) {
    approvals.value = []
    loadError.value = error.message
  } finally {
    loading.value = false
  }
}

function changeFilter(value) {
  statusFilter.value = value
  loadApprovals()
}

function openDialog(action, approval) {
  dialog.action = action
  dialog.approval = approval
  dialog.comment = ''
  dialog.visible = true
}

async function confirmDialog() {
  dialog.submitting = true
  const call = dialog.action === 'approve' ? approveApproval : rejectApproval
  try {
    const result = await call(dialog.approval.approvalId, demoUser.current, dialog.comment.trim())
    lastDecision.value = result
    dialog.visible = false
    ElMessage.success(`审批单 ${result.approvalId}：${labelText(APPROVAL_STATUS, result.status)}`)
    await loadApprovals()
  } catch (error) {
    // 错误提示（如“申请人不能审批自己的申请”）已由统一拦截器显示
  } finally {
    dialog.submitting = false
  }
}

function argumentsText(args) {
  return JSON.stringify(args ?? {})
}

function rowClass({ row }) {
  return row.approvalId === focusId.value ? 'is-focused' : ''
}

onMounted(loadApprovals)
</script>

<template>
  <div class="page-container approval-center">
    <div class="approval-header">
      <h2 class="page-title">
        审批中心
      </h2>
      <div class="identity">
        <span class="identity__label">当前演示身份</span>
        <el-radio-group
          v-model="demoUser.current"
          size="small"
        >
          <el-radio-button
            v-for="u in DEMO_USERS"
            :key="u"
            :value="u"
          >
            {{ labelText(DEMO_USER, u) }}
          </el-radio-button>
        </el-radio-group>
      </div>
    </div>
    <el-alert
      class="approval-intro"
      type="info"
      :closable="false"
      title="依据《内部操作规范》：删除业务记录需部门经理审批，审批通过后由系统管理员执行。"
      description="演示身份通过请求头 X-Demo-User 区分，仅用于演示，不是真实的身份认证；工具只操作模拟数据。批准后系统会重新检查权限、参数、记录与依据，通过才执行。"
    />

    <el-card
      class="section"
      shadow="never"
    >
      <template #header>
        发起工具调用（以当前演示身份）
      </template>
      <el-form
        inline
        label-width="auto"
        class="tool-form"
        @submit.prevent="submitToolCall"
      >
        <el-form-item label="工具">
          <el-select
            v-model="form.toolName"
            class="tool-form__select"
          >
            <el-option
              v-for="t in TOOL_OPTIONS"
              :key="t"
              :value="t"
              :label="`${labelText(TOOL_NAME, t)}（${t}）`"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="form.toolName !== 'search_document'"
          label="记录编号"
        >
          <el-input-number
            v-model="form.recordId"
            :min="1"
            :step="1"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item
          v-if="form.toolName === 'update_record'"
          label="新状态"
        >
          <el-select
            v-model="form.status"
            class="tool-form__status"
          >
            <el-option
              v-for="(entry, key) in RECORD_STATUS"
              :key="key"
              :value="key"
              :label="entry.text"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="form.toolName === 'search_document'"
          label="关键词"
        >
          <el-input
            v-model="form.keyword"
            maxlength="50"
          />
        </el-form-item>
        <el-form-item label="依据的问答">
          <el-input
            v-model="form.basedOnRequestId"
            placeholder="可空，如 req_3f9a1c0b7d2e"
            clearable
            class="tool-form__basis"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            native-type="submit"
            :loading="submitting"
          >
            提交
          </el-button>
        </el-form-item>
      </el-form>

      <ToolCallCard
        v-if="lastToolCall"
        :tool-call="lastToolCall"
      />
    </el-card>

    <el-card
      class="section"
      shadow="never"
    >
      <template #header>
        <div class="list-header">
          <span>审批单</span>
          <div class="list-header__actions">
            <el-radio-group
              :model-value="statusFilter"
              size="small"
              @change="changeFilter"
            >
              <el-radio-button
                v-for="s in STATUS_FILTERS"
                :key="s || 'ALL'"
                :value="s"
              >
                {{ s ? labelText(APPROVAL_STATUS, s) : '全部' }}
              </el-radio-button>
            </el-radio-group>
            <el-button
              :icon="Refresh"
              size="small"
              @click="loadApprovals"
            >
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="lastDecision"
        class="last-decision"
        :type="lastDecision.status === 'EXECUTED' ? 'success' : (lastDecision.status === 'FAILED' ? 'error' : 'info')"
        :closable="false"
        show-icon
        :title="`审批单 ${lastDecision.approvalId}：${labelText(APPROVAL_STATUS, lastDecision.status)}`"
      >
        <div class="last-decision__facts">
          <span v-if="lastDecision.failureReason">未执行原因：{{ labelText(APPROVAL_FAILURE_REASON, lastDecision.failureReason) }}</span>
          <span v-if="lastDecision.record">
            目标记录 {{ lastDecision.record.recordId }}《{{ lastDecision.record.title }}》当前：{{ labelText(RECORD_STATUS, lastDecision.record.status) }}，{{ lastDecision.record.deleted ? '已删除' : '未删除' }}
          </span>
        </div>
      </el-alert>

      <el-table
        v-loading="loading"
        :data="approvals"
        :row-class-name="rowClass"
        class="approval-table"
        row-key="approvalId"
      >
        <template #empty>
          {{ loadError ? `审批单加载失败：${loadError}` : '暂无审批单' }}
        </template>
        <el-table-column
          label="审批单号"
          width="150"
        >
          <template #default="{ row }">
            <span class="mono">{{ row.approvalId }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="工具与参数"
          min-width="190"
        >
          <template #default="{ row }">
            <div>{{ labelText(TOOL_NAME, row.toolName) }}</div>
            <div class="mono muted">
              {{ argumentsText(row.arguments) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="申请人"
          width="96"
        >
          <template #default="{ row }">
            {{ labelText(DEMO_USER, row.requester) }}
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="130"
        >
          <template #default="{ row }">
            <el-tag
              :type="labelType(APPROVAL_STATUS, row.status)"
              size="small"
              disable-transitions
            >
              {{ labelText(APPROVAL_STATUS, row.status) }}
            </el-tag>
            <div
              v-if="row.failureReason"
              class="failure"
            >
              {{ labelText(APPROVAL_FAILURE_REASON, row.failureReason) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="创建 / 过期"
          width="130"
        >
          <template #default="{ row }">
            <div>{{ formatShortTime(row.createdAt) }}</div>
            <div class="muted">
              至 {{ formatShortTime(row.expiresAt) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="审批人与意见"
          min-width="140"
        >
          <template #default="{ row }">
            <template v-if="row.approver">
              <div>{{ labelText(DEMO_USER, row.approver) }}</div>
              <div class="muted">
                {{ row.comment ?? '' }}
              </div>
            </template>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="140"
          fixed="right"
        >
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button
                type="primary"
                size="small"
                @click="openDialog('approve', row)"
              >
                批准
              </el-button>
              <el-button
                size="small"
                @click="openDialog('reject', row)"
              >
                驳回
              </el-button>
            </template>
            <router-link
              v-else-if="row.basedOnRequestId"
              :to="`/audit/${row.basedOnRequestId}`"
              class="audit-link"
            >
              审计详情
            </router-link>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialog.visible"
      :title="dialog.action === 'approve' ? '批准审批单' : '驳回审批单'"
      width="460px"
      append-to-body
      class="decision-dialog"
    >
      <div
        v-if="dialog.approval"
        class="dialog-body"
      >
        <div>
          审批单 <span class="mono">{{ dialog.approval.approvalId }}</span>：{{ labelText(DEMO_USER, dialog.approval.requester) }}申请{{ labelText(TOOL_NAME, dialog.approval.toolName) }}
          <span class="mono">{{ argumentsText(dialog.approval.arguments) }}</span>
        </div>
        <div class="muted">
          当前操作身份：{{ labelText(DEMO_USER, demoUser.current) }}
        </div>
        <el-input
          v-model="dialog.comment"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
          placeholder="审批意见（可选）"
        />
      </div>
      <template #footer>
        <el-button @click="dialog.visible = false">
          取消
        </el-button>
        <el-button
          :type="dialog.action === 'approve' ? 'primary' : 'danger'"
          :loading="dialog.submitting"
          class="dialog-confirm"
          @click="confirmDialog"
        >
          {{ dialog.action === 'approve' ? '确认批准' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.approval-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.identity {
  display: flex;
  align-items: center;
  gap: 10px;
}

.identity__label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.approval-intro {
  margin-bottom: 16px;
}

.section {
  margin-bottom: 16px;
}

.section :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tool-form :deep(.el-form-item) {
  margin-bottom: 8px;
}

.tool-form__select {
  width: 220px;
}

.tool-form__status {
  width: 120px;
}

.tool-form__basis {
  width: 200px;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.list-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.last-decision__facts {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  margin-top: 4px;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.failure {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-color-danger);
}

.approval-table :deep(.is-focused td.el-table__cell) {
  background-color: var(--el-color-warning-light-9);
}

.audit-link {
  font-size: 13px;
  color: var(--el-color-primary);
}

.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 14px;
  line-height: 1.7;
}
</style>
