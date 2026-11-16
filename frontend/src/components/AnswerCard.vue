<script setup>
import { computed, ref } from 'vue'
import { Lock } from '@element-plus/icons-vue'

import GuardBanner from './GuardBanner.vue'
import HighlightedAnswer from './HighlightedAnswer.vue'
import ClaimTable from './ClaimTable.vue'
import EvidenceDrawer from './EvidenceDrawer.vue'
import TimingBar from './TimingBar.vue'
import { VERIFICATION_FAILURE_REASON, labelText } from '@/constants/labels'
import { resolveAnswerView } from '@/utils/answerView'

/**
 * 核心组件（任务卡 6.5）：输入一个 ChatResponse（契约 3.7），自上而下显示
 * GuardBanner → 回答正文 → 逐条结论表 → 耗时。
 *
 * 展示规则见 utils/answerView.js，只依据后端给出的 action、answerVisible、insufficientClaims、truncated。
 * answerVisible=false 时不渲染任何回答正文。
 */
const props = defineProps({
  response: {
    type: Object,
    required: true
  },
  showRequestId: {
    type: Boolean,
    default: true
  }
})

const view = computed(() => resolveAnswerView(props.response))

const guard = computed(() => props.response.guard)

const verification = computed(() => props.response.verification)

const claims = computed(() => verification.value.claims || [])

const claimsTitle = computed(() => {
  const v = verification.value
  const base = `逐条核验结果（共 ${v.totalClaims} 条：有证据 ${v.supportedClaims} 条，证据不足 ${v.insufficientClaims} 条）`
  return v.truncated ? `${base}，仅核验了前 8 条` : base
})

const failureText = computed(() => {
  const v = verification.value
  if (v.verificationState !== 'FAILED') {
    return ''
  }
  return labelText(VERIFICATION_FAILURE_REASON, v.failureReason)
})

const expanded = ref(['claims'])

const drawerVisible = ref(false)
const drawerEvidenceId = ref(null)
const drawerQuote = ref(null)

function openEvidence(claim) {
  drawerEvidenceId.value = claim.evidenceId
  drawerQuote.value = claim.quote
  drawerVisible.value = true
}
</script>

<template>
  <el-card
    class="answer-card"
    shadow="never"
  >
    <div
      v-if="showRequestId"
      class="answer-card__meta"
    >
      <router-link
        class="request-id"
        :to="`/audit/${response.requestId}`"
        title="查看审计详情"
      >
        {{ response.requestId }}
      </router-link>
    </div>

    <GuardBanner
      :guard="guard"
      :type="view.bannerType"
    />

    <div
      v-if="failureText"
      class="answer-card__failure"
    >
      核验失败原因：{{ failureText }}
    </div>

    <div class="answer-card__body">
      <div
        v-if="view.body === 'hidden'"
        class="answer-placeholder"
      >
        <el-icon :size="18">
          <Lock />
        </el-icon>
        <span>{{ view.placeholder }}</span>
      </div>

      <HighlightedAnswer
        v-else-if="view.body === 'highlight'"
        :text="guard.displayAnswer"
        :claims="claims"
        @open-evidence="openEvidence"
      />

      <div
        v-else-if="view.body === 'unverified'"
        class="answer-unverified"
      >
        <div class="answer-unverified__label">
          未经核验
        </div>
        <HighlightedAnswer
          :text="guard.displayAnswer"
          :highlight="false"
        />
      </div>

      <HighlightedAnswer
        v-else
        :text="guard.displayAnswer"
        :highlight="false"
      />
    </div>

    <el-collapse
      v-if="view.showClaims && claims.length > 0"
      v-model="expanded"
      class="answer-card__claims"
    >
      <el-collapse-item
        name="claims"
        :title="claimsTitle"
      >
        <ClaimTable
          :claims="claims"
          :evidence="response.evidence"
          @open-evidence="openEvidence"
        />
      </el-collapse-item>
    </el-collapse>

    <TimingBar
      class="answer-card__timing"
      :timing="response.timing"
      :guard="guard"
    />

    <EvidenceDrawer
      v-model="drawerVisible"
      :evidence-id="drawerEvidenceId"
      :quote="drawerQuote"
      :evidence-list="response.evidence"
    />
  </el-card>
</template>

<style scoped>
.answer-card {
  max-width: 100%;
}

.answer-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.answer-card__meta {
  display: flex;
  justify-content: flex-end;
  margin-bottom: -4px;
}

.request-id {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-decoration: none;
}

.request-id:hover {
  color: var(--el-color-primary);
  text-decoration: underline;
}

.answer-card__failure {
  font-size: 13px;
  color: var(--el-color-danger);
}

.answer-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 28px 16px;
  font-size: 15px;
  color: var(--el-text-color-secondary);
  background-color: var(--el-fill-color-light);
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
}

.answer-unverified {
  padding: 12px 16px;
  border: 2px solid var(--el-border-color-darker);
  border-radius: 4px;
  background-color: var(--el-fill-color-lighter);
}

.answer-unverified__label {
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.answer-card__claims {
  border-top: none;
}

.answer-card__claims :deep(.el-collapse-item__header) {
  font-size: 14px;
  font-weight: 600;
}
</style>
