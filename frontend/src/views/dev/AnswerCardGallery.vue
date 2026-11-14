<script setup>
/**
 * 前端 Mock 专用：AnswerCard 全部展示情况一览（任务卡 5 阶段二“用 Mock 数据把每种 action 都显示正确”）。
 * 仅在 VITE_USE_MOCK=true 时注册路由 /dev/cards，不出现在导航中，答辩环境不可访问。
 */
import AnswerCard from '@/components/AnswerCard.vue'
import { CASES } from '@/mock/fixtures/cases'
import { buildExchange } from '@/mock/fixtures/builder'

const items = []
Object.entries(CASES).forEach(([key, def]) => {
  Object.keys(def.guards).forEach((strategy) => {
    items.push({
      label: `${key} · ${strategy}`,
      response: buildExchange(def, { strategy }).response
    })
  })
})
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">AnswerCard 展示一览（前端 Mock）</h2>
    <div
      v-for="item in items"
      :key="item.label"
      class="gallery-item"
      :data-case="item.label"
    >
      <div class="gallery-label">
        {{ item.label }}
      </div>
      <AnswerCard :response="item.response" />
    </div>
  </div>
</template>

<style scoped>
.gallery-item {
  margin-bottom: 28px;
}

.gallery-label {
  margin-bottom: 6px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
