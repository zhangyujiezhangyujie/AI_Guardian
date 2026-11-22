<script setup>
import { computed, ref, watch } from 'vue'
import { ArrowLeft, Document } from '@element-plus/icons-vue'

import { getDocument, listDocuments } from '@/api/knowledge'

/**
 * 知识库抽屉（任务卡 6.8，只读）：文档列表 → 点击查看全文与片段。
 * 答辩时用来证明“知识库里确实没有交通补贴”。全文按纯文本显示，不渲染 Markdown、不使用 HTML 插值。
 */
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const documents = ref([])
const listLoading = ref(false)
const listError = ref('')

const current = ref(null)
const detailLoading = ref(false)
const detailError = ref('')
const tab = ref('content')

let detailToken = 0

async function loadList() {
  listLoading.value = true
  listError.value = ''
  try {
    documents.value = await listDocuments()
  } catch (error) {
    listError.value = error.message
  } finally {
    listLoading.value = false
  }
}

async function openDocument(doc) {
  const token = ++detailToken
  current.value = { documentId: doc.documentId, title: doc.title }
  detailLoading.value = true
  detailError.value = ''
  tab.value = 'content'
  try {
    const detail = await getDocument(doc.documentId)
    if (token === detailToken) {
      current.value = detail
    }
  } catch (error) {
    if (token === detailToken) {
      detailError.value = error.message
    }
  } finally {
    if (token === detailToken) {
      detailLoading.value = false
    }
  }
}

function backToList() {
  detailToken += 1
  current.value = null
}

watch(
  visible,
  (open) => {
    if (open && documents.value.length === 0 && !listLoading.value) {
      loadList()
    }
  },
  { immediate: true }
)
</script>

<template>
  <el-drawer
    v-model="visible"
    direction="rtl"
    size="640px"
    append-to-body
    class="knowledge-drawer"
  >
    <template #header>
      <div class="kd-header">
        <el-button
          v-if="current"
          :icon="ArrowLeft"
          link
          @click="backToList"
        >
          文档列表
        </el-button>
        <span class="kd-title">{{ current ? `《${current.title}》` : '知识库（只读）' }}</span>
      </div>
    </template>

    <!-- 文档列表 -->
    <div
      v-if="!current"
      v-loading="listLoading"
      class="kd-list"
    >
      <el-result
        v-if="listError"
        icon="error"
        title="知识库加载失败"
        :sub-title="listError"
      >
        <template #extra>
          <el-button
            type="primary"
            @click="loadList"
          >
            重试
          </el-button>
        </template>
      </el-result>

      <button
        v-for="doc in documents"
        :key="doc.documentId"
        type="button"
        class="kd-doc"
        @click="openDocument(doc)"
      >
        <el-icon
          class="kd-doc__icon"
          :size="20"
        >
          <Document />
        </el-icon>
        <span class="kd-doc__main">
          <span class="kd-doc__title">{{ doc.title }}</span>
          <span class="kd-doc__meta">{{ doc.fileName }}</span>
        </span>
        <span class="kd-doc__stats">{{ doc.chunkCount }} 个片段 · {{ doc.charCount }} 字</span>
      </button>
    </div>

    <!-- 文档详情 -->
    <div
      v-else
      v-loading="detailLoading"
      class="kd-detail"
    >
      <el-result
        v-if="detailError"
        icon="error"
        title="文档加载失败"
        :sub-title="detailError"
      />
      <el-tabs
        v-else-if="current.content !== undefined"
        v-model="tab"
      >
        <el-tab-pane
          label="全文"
          name="content"
        >
          <div class="kd-content">{{ current.content }}</div>
        </el-tab-pane>
        <el-tab-pane
          :label="`片段（${current.chunks.length}）`"
          name="chunks"
        >
          <div
            v-for="chunk in current.chunks"
            :key="chunk.evidenceId"
            class="kd-chunk"
          >
            <div class="kd-chunk__head">
              <span class="kd-chunk__id">{{ chunk.evidenceId }}</span>
              <span class="kd-chunk__section">{{ chunk.section ?? '（无章节）' }}</span>
            </div>
            <div class="kd-content">{{ chunk.content }}</div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>
</template>

<style scoped>
.kd-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.kd-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.kd-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 120px;
}

.kd-doc {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  font: inherit;
  text-align: left;
  cursor: pointer;
  background-color: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
}

.kd-doc:hover {
  border-color: var(--el-color-primary-light-5);
  background-color: var(--el-color-primary-light-9);
}

.kd-doc__icon {
  color: var(--el-color-primary);
}

.kd-doc__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.kd-doc__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.kd-doc__meta,
.kd-doc__stats {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.kd-doc__stats {
  flex: none;
}

.kd-detail {
  min-height: 120px;
}

.kd-content {
  font-size: 14px;
  line-height: 1.9;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  color: var(--el-text-color-regular);
}

.kd-chunk {
  margin-bottom: 14px;
  padding: 12px 14px;
  background-color: var(--el-fill-color-lighter);
  border-radius: 10px;
}

.kd-chunk__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.kd-chunk__id {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--el-color-primary);
}

.kd-chunk__section {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
</style>
