<script setup>
import { computed } from 'vue'

import { GUARD_STRATEGY, labelText } from '@/constants/labels'
import { formatSeconds } from '@/utils/format'

/** 耗时与策略（任务卡 6.5）：灰色小字，毫秒转秒保留 1 位小数，未执行显示“—”。 */
const props = defineProps({
  timing: {
    type: Object,
    required: true
  },
  guard: {
    type: Object,
    default: null
  }
})

const items = computed(() => [
  ['检索', props.timing.retrievalMs],
  ['生成', props.timing.generationMs],
  ['核验', props.timing.verificationMs],
  ['总计', props.timing.totalMs]
])
</script>

<template>
  <div class="timing-bar">
    <span
      v-for="[label, ms] in items"
      :key="label"
      class="timing-item"
    >
      {{ label }} {{ formatSeconds(ms) }}
    </span>
    <span
      v-if="guard"
      class="timing-item"
    >
      策略 {{ labelText(GUARD_STRATEGY, guard.strategy) }} · 阈值 {{ guard.blockThreshold }}
    </span>
  </div>
</template>

<style scoped>
.timing-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
