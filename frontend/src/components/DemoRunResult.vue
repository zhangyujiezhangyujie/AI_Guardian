<script setup>
import { computed } from 'vue'

import AnswerCard from './AnswerCard.vue'
import ErrorCard from './ErrorCard.vue'
import ToolCallCard from './ToolCallCard.vue'
import { GUARD_STRATEGY, RUN_MODE, labelText } from '@/constants/labels'

/**
 * 演示运行结果（任务卡 6.8）：比对结果卡片 + AnswerCard。
 * 比对结果只展示后端 check 字段（成员 4 的期望比对器），前端不做比对。
 */
const props = defineProps({
  run: {
    type: Object,
    required: true
  },
  showStrategy: {
    type: Boolean,
    default: false
  }
})

const check = computed(() => props.run.data?.check ?? null)

const response = computed(() => props.run.data?.response ?? null)

// 契约 v1.3 第 7.7 节：仅 SC06 等带工具调用的场景非空
const toolCall = computed(() => props.run.data?.toolCall ?? null)

const strategyText = computed(() => {
  return props.run.strategy ? labelText(GUARD_STRATEGY, props.run.strategy) : '默认策略'
})

const auditLink = computed(() => `/audit?mode=${props.run.mode}`)
</script>

<template>
  <div class="demo-run">
    <div
      v-if="showStrategy"
      class="demo-run__strategy"
    >
      <el-tag
        type="primary"
        effect="dark"
        disable-transitions
      >
        {{ strategyText }}
      </el-tag>
      <span class="demo-run__mode">{{ labelText(RUN_MODE, run.mode) }}模式</span>
    </div>

    <ErrorCard
      v-if="run.error"
      :message="run.error.message"
      :error-code="run.error.errorCode"
      :request-id="run.error.requestId"
      :http-status="run.error.httpStatus"
    />

    <template v-else>
      <el-alert
        v-if="check && check.checked && check.passed"
        class="demo-check"
        type="success"
        title="符合预期"
        description="运行结果与场景文件中的期望一致。"
        :closable="false"
        show-icon
      />
      <el-alert
        v-else-if="check && check.checked"
        class="demo-check"
        type="error"
        title="不符合预期"
        :closable="false"
        show-icon
      >
        <ul class="demo-check__mismatches">
          <li
            v-for="(item, index) in check.mismatches"
            :key="index"
          >
            {{ item }}
          </li>
        </ul>
      </el-alert>
      <el-alert
        v-else
        class="demo-check"
        type="info"
        title="真实模式不做期望比对"
        :closable="false"
        show-icon
      />

      <AnswerCard
        v-if="response"
        :response="response"
      />

      <ToolCallCard
        v-if="toolCall"
        :tool-call="toolCall"
        :compact="showStrategy"
      />

      <el-card
        v-else
        class="demo-no-response"
        shadow="never"
      >
        <div class="demo-no-response__title">
          本次运行没有返回回答
        </div>
        <div class="demo-no-response__text">
          请求在生成回答前失败，是否符合场景期望见上方比对结果。演示接口不返回错误码，可在
          <router-link :to="auditLink">
            审计日志
          </router-link>
          中查看本次请求的错误码与事件时间线。
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.demo-run {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.demo-run__strategy {
  display: flex;
  align-items: center;
  gap: 8px;
}

.demo-run__mode {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.demo-check__mismatches {
  margin: 4px 0 0;
  padding-left: 18px;
}

.demo-no-response__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-color-danger);
}

.demo-no-response__text {
  margin-top: 6px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--el-text-color-regular);
}
</style>
