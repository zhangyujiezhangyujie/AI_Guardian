<script setup>
/**
 * 请求失败卡片（任务卡 6.6；演示页 SC09 也复用，任务卡 6.8）。
 * 显示后端错误信息与 requestId（有 requestId 时可点击跳转审计详情）。
 */
defineProps({
  message: {
    type: String,
    required: true
  },
  errorCode: {
    type: String,
    default: null
  },
  requestId: {
    type: String,
    default: null
  },
  httpStatus: {
    type: Number,
    default: null
  }
})
</script>

<template>
  <el-alert
    class="error-card"
    type="error"
    :title="message"
    :closable="false"
    show-icon
  >
    <div class="error-card__meta">
      <span v-if="httpStatus">HTTP {{ httpStatus }}</span>
      <span v-if="errorCode">{{ errorCode }}</span>
      <span v-if="requestId">
        requestId：<router-link
          class="error-card__link"
          :to="`/audit/${requestId}`"
          title="查看审计详情"
        >{{ requestId }}</router-link>
      </span>
    </div>
  </el-alert>
</template>

<style scoped>
.error-card :deep(.el-alert__title) {
  font-size: 15px;
}

.error-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  margin-top: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

.error-card__link {
  color: inherit;
}
</style>
