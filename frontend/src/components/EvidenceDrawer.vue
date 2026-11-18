<script setup>
import { computed, ref, watch } from 'vue'

import { getEvidence } from '@/api/knowledge'
import { splitByQuote } from '@/utils/highlight'

/**
 * 证据抽屉（任务卡 6.5）：显示文档标题、章节、证据编号、片段原文。
 *
 * - 证据优先从本次响应的 evidence 中取，取不到再调用 GET /api/knowledge/evidence/{id}；
 * - 若有 quote 且 content.indexOf(quote) >= 0，加粗高亮 quote；找不到不高亮（不做模糊匹配）。
 */
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  evidenceId: {
    type: String,
    default: null
  },
  quote: {
    type: String,
    default: null
  },
  evidenceList: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue'])

const evidence = ref(null)
const loading = ref(false)
const errorMessage = ref('')
const fromResponse = ref(true)

let loadToken = 0

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const parts = computed(() => {
  return evidence.value ? splitByQuote(evidence.value.content, props.quote) : []
})

const quoteFound = computed(() => parts.value.some((p) => p.highlight))

async function load() {
  const token = ++loadToken
  evidence.value = null
  errorMessage.value = ''

  if (!props.evidenceId) {
    return
  }

  const local = props.evidenceList.find((e) => e.evidenceId === props.evidenceId)
  if (local) {
    evidence.value = local
    fromResponse.value = true
    return
  }

  loading.value = true
  fromResponse.value = false
  try {
    const result = await getEvidence(props.evidenceId, { silent: true })
    if (token === loadToken) {
      evidence.value = result
    }
  } catch (error) {
    if (token === loadToken) {
      errorMessage.value = error.message
    }
  } finally {
    if (token === loadToken) {
      loading.value = false
    }
  }
}

watch(
  () => [props.modelValue, props.evidenceId],
  ([open]) => {
    if (open) {
      load()
    }
  },
  { immediate: true }
)
</script>

<template>
  <el-drawer
    v-model="visible"
    title="证据原文"
    direction="rtl"
    size="480px"
    append-to-body
  >
    <div
      v-loading="loading"
      class="evidence-drawer"
    >
      <template v-if="evidence">
        <el-descriptions
          :column="1"
          border
          size="small"
        >
          <el-descriptions-item label="文档">
            《{{ evidence.documentTitle }}》
          </el-descriptions-item>
          <el-descriptions-item label="章节">
            {{ evidence.section ?? '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="证据编号">
            {{ evidence.evidenceId }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="!fromResponse"
          class="evidence-note"
          type="warning"
          :closable="false"
          title="该片段不在本次检索结果中，以下为知识库原文。"
        />

        <div class="evidence-content"><span
          v-for="(part, index) in parts"
          :key="index"
          :class="{ 'quote-hit': part.highlight }"
        >{{ part.text }}</span></div>

        <div
          v-if="quote"
          class="quote-status"
        >
          <template v-if="quoteFound">
            加粗部分为核验模型给出的引用原文。
          </template>
          <template v-else>
            核验模型给出的引用原文未在该片段中找到：“{{ quote }}”
          </template>
        </div>
      </template>

      <el-result
        v-else-if="errorMessage"
        icon="warning"
        title="证据片段无法加载"
        :sub-title="`${evidenceId}：${errorMessage}`"
      />
    </div>
  </el-drawer>
</template>

<style scoped>
.evidence-drawer {
  min-height: 120px;
}

.evidence-note {
  margin-top: 12px;
}

.evidence-content {
  margin-top: 16px;
  padding: 12px 16px;
  font-size: 15px;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  background-color: var(--el-fill-color-lighter);
  border-radius: 4px;
}

.quote-hit {
  font-weight: 600;
  background-color: var(--el-color-success-light-8);
  border-radius: 2px;
}

.quote-status {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
