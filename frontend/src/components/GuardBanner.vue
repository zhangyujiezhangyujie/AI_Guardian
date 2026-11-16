<script setup>
import { computed } from 'vue'

import { GUARD_ACTION, labelText } from '@/constants/labels'

/**
 * 输出防护提示条（任务卡 6.5）：显示后端给出的 guard.message。
 * message 为 null（如 NO_EVIDENCE）时显示处理结果的中文名。
 */
const props = defineProps({
  guard: {
    type: Object,
    required: true
  },
  type: {
    type: String,
    required: true
  }
})

const title = computed(() => {
  return props.guard.message ?? labelText(GUARD_ACTION, props.guard.action)
})
</script>

<template>
  <el-alert
    class="guard-banner"
    :type="type"
    :title="title"
    :closable="false"
    show-icon
  />
</template>

<style scoped>
.guard-banner :deep(.el-alert__title) {
  font-size: 15px;
  line-height: 1.6;
}
</style>
