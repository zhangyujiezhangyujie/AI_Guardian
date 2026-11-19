<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'

import { getAuditDetail } from '@/api/audit'
import ClaimTable from '@/components/ClaimTable.vue'
import EvidenceDrawer from '@/components/EvidenceDrawer.vue'
import HighlightedAnswer from '@/components/HighlightedAnswer.vue'
import {
  AUDIT_EVENT_TYPE,
  GUARD_ACTION,
  GUARD_STRATEGY,
  RUN_MODE,
  VERIFICATION_FAILURE_REASON,
  VERIFICATION_STATE,
  labelText,
  labelType
} from '@/constants/labels'
import { formatSeconds } from '@/utils/format'
import { formatClockTime, formatFullTime } from '@/utils/time'

/**
 * 页面二：审计详情（任务卡 6.7、契约 7.9）。
 *
 * 面向管理员：显示**未遮蔽**的原始回答 rawAnswer，并按 verification.claims 的下标高亮；
 * 即使该回答在用户侧被拦截，这里也能看到原文。
 * 生成失败的记录 verification 为 null，只显示基本信息、问题、错误码和事件时间线。
 */
const props = defineProps({
  requestId: {
    type: String,
    required: true
  }
})

const router = useRouter()

const detail = ref(null)
const loading = ref(false)
const notFound = ref(false)
const loadError = ref('')

const drawerVisible = ref(false)
const drawerEvidenceId = ref(null)
const drawerQuote = ref(null)

let requestToken = 0

const verification = computed(() => detail.value?.verification ?? null)

const guard = computed(() => detail.value?.guard ?? null)

const claims = computed(() => verification.value?.claims ?? [])

const canHighlight = computed(() => {
  const state = verification.value?.verificationState
  return state === 'COMPLETED' || state === 'PARTIAL'
})

/** 管理员视图的提示：只依据后端保存的处理结果，不做判断。 */
const userSideNote = computed(() => {
  const action = guard.value?.action
  if (action === 'BLOCKED') {
    return '该回答在用户侧已被拦截'
  }
  if (action === 'NOT_VERIFIED') {
    return '该回答未经核验'
  }
  return ''
})

const ratioText = computed(() => {
  const ratio = verification.value?.unsupportedRatio
  return ratio === null || ratio === undefined ? '—' : String(ratio)
})

async function load() {
  const token = ++requestToken
  loading.value = true
  notFound.value = false
  loadError.value = ''
  detail.value = null
  try {
    const result = await getAuditDetail(props.requestId)
    if (token === requestToken) {
      detail.value = result
    }
  } catch (error) {
    if (token !== requestToken) {
      return
    }
    if (error.errorCode === 'AUDIT_NOT_FOUND') {
      notFound.value = true
    } else {
      loadError.value = error.message
    }
  } finally {
    if (token === requestToken) {
      loading.value = false
    }
  }
}

watch(() => props.requestId, load, { immediate: true })

/** 从列表进入时返回原列表（保留筛选和页码），否则回到列表首页。 */
function goBack() {
  const back = window.history.state && window.history.state.back
  if (typeof back === 'string' && back.startsWith('/audit') && !back.startsWith('/audit/')) {
    router.back()
  } else {
    router.push('/audit')
  }
}

function openEvidence(claim) {
  drawerEvidenceId.value = claim.evidenceId
  drawerQuote.value = claim.quote
  drawerVisible.value = true
}

function evidenceTitle(evidence) {
  const section = evidence.section ? ` · ${evidence.section}` : ''
  return `[${evidence.rank}] 《${evidence.documentTitle}》${section}`
}

function detailJson(value) {
  return JSON.stringify(value, null, 2)
}
</script>

<template>
  <div class="page-container audit-detail">
    <div class="detail-header">
      <el-button
        :icon="ArrowLeft"
        @click="goBack"
      >
        返回列表
      </el-button>
      <h2 class="page-title detail-title">
        审计详情 <span class="mono">{{ requestId }}</span>
      </h2>
    </div>

    <div
      v-if="loading"
      v-loading="true"
      class="detail-loading"
    />

    <el-result
      v-else-if="notFound"
      icon="warning"
      title="审计记录不存在"
      :sub-title="requestId"
    >
      <template #extra>
        <el-button
          type="primary"
          @click="router.push('/audit')"
        >
          返回列表
        </el-button>
      </template>
    </el-result>

    <el-result
      v-else-if="loadError"
      icon="error"
      title="审计详情加载失败"
      :sub-title="loadError"
    >
      <template #extra>
        <el-button
          type="primary"
          @click="load"
        >
          重试
        </el-button>
      </template>
    </el-result>

    <template v-else-if="detail">
      <!-- ① 基本信息 -->
      <el-card
        class="detail-section"
        shadow="never"
      >
        <template #header>
          基本信息
        </template>
        <el-descriptions
          :column="3"
          border
          size="small"
        >
          <el-descriptions-item label="requestId">
            <span class="mono">{{ detail.requestId }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="sessionId">
            <span class="mono">{{ detail.sessionId }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="时间">
            {{ formatFullTime(detail.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="模式">
            <el-tag
              :type="labelType(RUN_MODE, detail.mode)"
              size="small"
              disable-transitions
            >
              {{ labelText(RUN_MODE, detail.mode) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="场景">
            {{ detail.scenarioId ?? '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="错误码">
            <span
              v-if="detail.errorCode"
              class="error-code mono"
            >{{ detail.errorCode }}</span>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item label="策略">
            {{ guard ? labelText(GUARD_STRATEGY, guard.strategy) : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="阈值">
            {{ guard?.blockThreshold ?? '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="处理结果">
            <el-tag
              v-if="guard"
              :type="labelType(GUARD_ACTION, guard.action)"
              size="small"
              disable-transitions
            >
              {{ labelText(GUARD_ACTION, guard.action) }}
            </el-tag>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item label="核验状态">
            <template v-if="verification">
              <el-tag
                :type="labelType(VERIFICATION_STATE, verification.verificationState)"
                size="small"
                effect="plain"
                disable-transitions
              >
                {{ labelText(VERIFICATION_STATE, verification.verificationState) }}
              </el-tag>
              <span
                v-if="verification.failureReason"
                class="failure-reason"
              >{{ labelText(VERIFICATION_FAILURE_REASON, verification.failureReason) }}</span>
            </template>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item label="证据不足比例">
            {{ ratioText }}
          </el-descriptions-item>
          <el-descriptions-item label="核验器">
            {{ verification ? `${verification.verifierName}（调用 ${verification.llmCalls} 次）` : '—' }}
          </el-descriptions-item>
          <el-descriptions-item
            label="耗时"
            :span="3"
          >
            检索 {{ formatSeconds(detail.timing.retrievalMs) }} ·
            生成 {{ formatSeconds(detail.timing.generationMs) }} ·
            核验 {{ formatSeconds(detail.timing.verificationMs) }} ·
            总计 {{ formatSeconds(detail.timing.totalMs) }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- ② 问题与原始回答 -->
      <el-card
        class="detail-section"
        shadow="never"
      >
        <template #header>
          问题与原始回答
        </template>
        <div class="question">
          <span class="field-label">问题</span>
          <span class="question-text">{{ detail.question }}</span>
        </div>

        <div
          v-if="detail.rawAnswer !== null"
          class="raw-answer"
        >
          <div class="raw-answer__header">
            <span class="raw-answer__label">管理员视图：原始回答</span>
            <el-tag
              v-if="userSideNote"
              type="danger"
              size="small"
              disable-transitions
            >
              {{ userSideNote }}
            </el-tag>
          </div>
          <HighlightedAnswer
            :text="detail.rawAnswer"
            :claims="claims"
            :highlight="canHighlight"
            @open-evidence="openEvidence"
          />
        </div>
        <div
          v-else
          class="no-answer"
        >
          <template v-if="detail.errorCode">
            未生成回答（{{ detail.errorCode }}）
          </template>
          <template v-else>
            未生成回答（{{ labelText(VERIFICATION_STATE, verification?.verificationState) || '无回答' }}）
          </template>
        </div>
      </el-card>

      <!-- ③ 逐条结论 -->
      <el-card
        v-if="claims.length > 0"
        class="detail-section"
        shadow="never"
      >
        <template #header>
          逐条结论（模型初判 → 程序校验）
        </template>
        <ClaimTable
          :claims="claims"
          :evidence="detail.evidence"
          admin
          @open-evidence="openEvidence"
        />
      </el-card>

      <!-- ④ 证据列表 -->
      <el-card
        v-if="verification"
        class="detail-section"
        shadow="never"
      >
        <template #header>
          证据列表（共 {{ detail.evidence.length }} 条）
        </template>
        <el-collapse v-if="detail.evidence.length > 0">
          <el-collapse-item
            v-for="evidence in detail.evidence"
            :key="evidence.evidenceId"
            :name="evidence.evidenceId"
          >
            <template #title>
              <span class="evidence-title">{{ evidenceTitle(evidence) }}</span>
              <span class="evidence-meta mono">{{ evidence.evidenceId }} · BM25 {{ evidence.score }}</span>
            </template>
            <div class="evidence-content">{{ evidence.content }}</div>
          </el-collapse-item>
        </el-collapse>
        <div
          v-else
          class="no-answer"
        >
          本次检索无结果
        </div>
      </el-card>

      <!-- ⑤ 事件时间线 -->
      <el-card
        class="detail-section"
        shadow="never"
      >
        <template #header>
          事件时间线
        </template>
        <el-timeline class="event-timeline">
          <el-timeline-item
            v-for="(event, index) in detail.events"
            :key="index"
            :type="event.success ? 'success' : 'danger'"
            :timestamp="formatClockTime(event.createdAt)"
            placement="top"
          >
            <div class="event-row">
              <span class="event-name">{{ labelText(AUDIT_EVENT_TYPE, event.eventType) }}</span>
              <el-tag
                :type="event.success ? 'success' : 'danger'"
                size="small"
                effect="plain"
                disable-transitions
              >
                {{ event.success ? '成功' : '失败' }}
              </el-tag>
              <span
                v-if="event.durationMs !== null"
                class="event-duration"
              >{{ formatSeconds(event.durationMs) }}</span>
              <span
                v-if="event.errorCode"
                class="error-code mono"
              >{{ event.errorCode }}</span>
            </div>
            <details
              v-if="event.detail"
              class="event-detail"
            >
              <summary>详情</summary>
              <pre>{{ detailJson(event.detail) }}</pre>
            </details>
          </el-timeline-item>
        </el-timeline>
      </el-card>

      <EvidenceDrawer
        v-model="drawerVisible"
        :evidence-id="drawerEvidenceId"
        :quote="drawerQuote"
        :evidence-list="detail.evidence"
      />
    </template>
  </div>
</template>

<style scoped>
.detail-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.detail-title {
  margin: 0;
}

.detail-loading {
  min-height: 240px;
}

.detail-section {
  margin-bottom: 16px;
}

.detail-section :deep(.el-card__header) {
  font-weight: 600;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
}

.error-code {
  color: var(--el-color-danger);
}

.failure-reason {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-color-danger);
}

.question {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  font-size: 15px;
  line-height: 1.8;
}

.field-label {
  flex: none;
  color: var(--el-text-color-secondary);
}

.question-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.raw-answer {
  padding: 12px 16px;
  border: 1px solid var(--el-border-color);
  border-left: 4px solid var(--el-color-primary-light-3);
  border-radius: 4px;
}

.raw-answer__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.raw-answer__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-primary);
}

.no-answer {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.evidence-title {
  margin-right: 12px;
  font-size: 14px;
}

.evidence-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.evidence-content {
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.event-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.event-name {
  font-size: 14px;
  font-weight: 600;
}

.event-duration {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.event-detail {
  margin-top: 4px;
  font-size: 12px;
}

.event-detail summary {
  cursor: pointer;
  color: var(--el-color-primary);
}

.event-detail pre {
  margin: 4px 0 0;
  padding: 8px 12px;
  overflow-x: auto;
  font-size: 12px;
  background-color: var(--el-fill-color-lighter);
  border-radius: 4px;
}
</style>
