<script setup>
import { computed } from 'vue'

import { INSUFFICIENT_REASON, labelText } from '@/constants/labels'
import { buildSegments } from '@/utils/highlight'

/**
 * 按下标高亮的回答正文（任务卡 6.5）。
 *
 * 安全：模型输出可能含 HTML，这里只用文本插值渲染 <span>，禁止把模型输出当作 HTML 渲染。
 * highlight=false 时整段作为普通文本显示（未核验、无结论、无资料）。
 */
const props = defineProps({
  text: {
    type: String,
    required: true
  },
  claims: {
    type: Array,
    default: () => []
  },
  highlight: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['open-evidence'])

const segments = computed(() => {
  if (!props.highlight) {
    return [{ text: props.text, claim: null, number: null }]
  }
  return buildSegments(props.text, props.claims)
})

function reasonText(claim) {
  return labelText(INSUFFICIENT_REASON, claim.insufficientReason) || '证据不足'
}

function openEvidence(claim) {
  emit('open-evidence', claim)
}
</script>

<template>
  <div class="answer-text"><template
    v-for="(seg, index) in segments"
    :key="index"
  ><span v-if="!seg.claim">{{ seg.text }}</span><span
    v-else-if="seg.claim.status === 'SUPPORTED'"
    class="claim claim--supported"
    :data-claim-id="seg.claim.claimId"
    @click="openEvidence(seg.claim)"
  >{{ seg.text }}<sup
    class="claim-ref"
    role="button"
    tabindex="0"
    :title="`查看证据 [${seg.number}]`"
    @keydown.enter.stop="openEvidence(seg.claim)"
  >[{{ seg.number }}]</sup></span><el-tooltip
    v-else
    :content="reasonText(seg.claim)"
    placement="top"
  ><span
    class="claim claim--insufficient"
    :data-claim-id="seg.claim.claimId"
  ><span class="claim-icon">⚠</span>{{ seg.text }}</span></el-tooltip></template></div>
</template>

<style scoped>
.answer-text {
  font-size: 15px;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  color: var(--el-text-color-primary);
}

.claim--supported {
  cursor: pointer;
  text-decoration: underline;
  text-decoration-color: var(--el-color-success-light-3);
  text-decoration-thickness: 2px;
  text-underline-offset: 4px;
}

.claim-ref {
  margin-left: 1px;
  font-size: 11px;
  color: var(--el-color-success);
  cursor: pointer;
}

.claim-ref:focus-visible {
  outline: 2px solid var(--el-color-success);
  border-radius: 2px;
}

.claim--insufficient {
  padding: 1px 2px;
  border-radius: 3px;
  background-color: var(--el-color-warning-light-8);
  cursor: help;
}

.claim-icon {
  margin-right: 2px;
  color: var(--el-color-warning);
}
</style>
