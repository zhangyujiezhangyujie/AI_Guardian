<script setup>
import { computed } from 'vue'

import {
  DEMO_USER,
  RECORD_STATUS,
  TOOL_DECISION,
  TOOL_DENY_REASON,
  TOOL_NAME,
  labelText,
  labelType
} from '@/constants/labels'

/**
 * 工具调用网关决策卡片（契约 v1.3 第 3.11、7.7 节，P1）。
 *
 * 只展示后端 ToolCallResult：decision、denyReason、message、executed、record 均为后端给出，
 * 前端不判断是否越权，也不推断数据是否被修改。
 */
const props = defineProps({
  toolCall: {
    type: Object,
    required: true
  }
})

const alertType = computed(() => {
  const map = {
    ALLOW: 'success',
    DENY: 'error',
    REQUIRE_APPROVAL: 'warning'
  }
  return map[props.toolCall.decision] || 'info'
})

const argumentsText = computed(() => JSON.stringify(props.toolCall.arguments ?? {}))

const resultText = computed(() => {
  return props.toolCall.result ? JSON.stringify(props.toolCall.result, null, 2) : ''
})

const record = computed(() => props.toolCall.record)
</script>

<template>
  <el-card
    class="tool-call-card"
    shadow="never"
  >
    <template #header>
      <div class="tool-call-card__header">
        <span class="tool-call-card__title">模型提出的工具调用</span>
        <el-tag
          :type="labelType(TOOL_DECISION, toolCall.decision)"
          effect="dark"
          disable-transitions
        >
          网关判定：{{ labelText(TOOL_DECISION, toolCall.decision) }}
        </el-tag>
      </div>
    </template>

    <el-descriptions
      :column="2"
      border
      size="small"
    >
      <el-descriptions-item label="演示用户">
        {{ labelText(DEMO_USER, toolCall.user) }}（{{ toolCall.user }}）
      </el-descriptions-item>
      <el-descriptions-item label="工具">
        {{ labelText(TOOL_NAME, toolCall.toolName) }}
        <span class="mono">{{ toolCall.toolName }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="参数">
        <span class="mono">{{ argumentsText }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="依据的问答">
        <router-link
          v-if="toolCall.basedOnRequestId"
          class="mono"
          :to="`/audit/${toolCall.basedOnRequestId}`"
        >
          {{ toolCall.basedOnRequestId }}
        </router-link>
        <span v-else>—</span>
      </el-descriptions-item>
    </el-descriptions>

    <el-alert
      class="tool-call-card__decision"
      :type="alertType"
      :title="toolCall.message"
      :closable="false"
      show-icon
    >
      <div class="tool-call-card__facts">
        <span v-if="toolCall.denyReason">原因：{{ labelText(TOOL_DENY_REASON, toolCall.denyReason) }}</span>
        <span>{{ toolCall.executed ? '网关已执行该操作' : '网关未执行该操作' }}</span>
      </div>
    </el-alert>

    <div
      v-if="record"
      class="tool-call-card__record"
    >
      <span class="field-label">目标记录当前状态</span>
      <span class="mono">{{ record.recordId }}</span>
      <span>《{{ record.title }}》</span>
      <el-tag
        :type="labelType(RECORD_STATUS, record.status)"
        size="small"
        disable-transitions
      >
        {{ labelText(RECORD_STATUS, record.status) }}
      </el-tag>
      <el-tag
        :type="record.deleted ? 'danger' : 'success'"
        size="small"
        effect="plain"
        disable-transitions
      >
        {{ record.deleted ? '已删除' : '未删除' }}
      </el-tag>
    </div>

    <pre
      v-if="resultText"
      class="tool-call-card__result"
    >{{ resultText }}</pre>

    <div class="tool-call-card__note">
      演示用户由请求头 X-Demo-User 区分，仅用于演示，不是真实的身份认证；工具只操作模拟数据。
    </div>
  </el-card>
</template>

<style scoped>
.tool-call-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tool-call-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.tool-call-card__title {
  font-weight: 600;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

.tool-call-card__decision :deep(.el-alert__title) {
  font-size: 15px;
}

.tool-call-card__facts {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  margin-top: 4px;
}

.tool-call-card__record {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 14px;
}

.field-label {
  color: var(--el-text-color-secondary);
}

.tool-call-card__result {
  margin: 0;
  padding: 8px 12px;
  overflow-x: auto;
  font-size: 12px;
  background-color: var(--el-fill-color-lighter);
  border-radius: 8px;
}

.tool-call-card__note {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
