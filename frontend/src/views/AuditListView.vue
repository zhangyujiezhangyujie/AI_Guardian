<script setup>
import { reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { listAudits } from '@/api/audit'
import {
  GUARD_ACTION,
  GUARD_STRATEGY,
  RUN_MODE,
  VERIFICATION_STATE,
  labelOptions,
  labelText,
  labelType
} from '@/constants/labels'
import { formatSeconds } from '@/utils/format'
import { formatShortTime, toShanghaiIso } from '@/utils/time'
import {
  AUDIT_PAGE_SIZE,
  formToQuery,
  queryToApiParams,
  queryToForm,
  queryToPage
} from '@/utils/auditQuery'

/**
 * 页面二：审计日志列表（任务卡 6.7、契约 7.8）。
 * 筛选条件与页码保存在 URL 中，从详情页返回时保持不变。
 */

const route = useRoute()
const router = useRouter()

const form = reactive(queryToForm({}))
const items = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const loadError = ref('')

let requestToken = 0

const stateOptions = labelOptions(VERIFICATION_STATE)
const actionOptions = labelOptions(GUARD_ACTION)
const modeOptions = labelOptions(RUN_MODE)

const rangeShortcuts = [
  {
    text: '最近 1 小时',
    value: () => [new Date(Date.now() - 60 * 60 * 1000), new Date()]
  },
  {
    text: '今天',
    value: () => {
      const start = new Date()
      start.setHours(0, 0, 0, 0)
      return [start, new Date()]
    }
  },
  {
    text: '最近 7 天',
    value: () => [new Date(Date.now() - 7 * 24 * 60 * 60 * 1000), new Date()]
  }
]

async function load(query) {
  const token = ++requestToken
  loading.value = true
  loadError.value = ''
  try {
    const result = await listAudits(queryToApiParams(query))
    if (token !== requestToken) {
      return
    }
    items.value = result.items
    total.value = result.total
  } catch (error) {
    if (token !== requestToken) {
      return
    }
    items.value = []
    total.value = 0
    loadError.value = error.message
  } finally {
    if (token === requestToken) {
      loading.value = false
    }
  }
}

watch(
  () => route.query,
  (query) => {
    // 跳转到详情页时路由先变化、组件后卸载，此时不重新加载
    if (route.name !== 'audit-list') {
      return
    }
    Object.assign(form, queryToForm(query))
    page.value = queryToPage(query)
    load(query)
  },
  { immediate: true }
)

function search() {
  router.push({
    path: '/audit',
    query: formToQuery(form, toShanghaiIso)
  })
}

function reset() {
  router.push({ path: '/audit' })
}

function changePage(newPage) {
  router.push({
    path: '/audit',
    query: {
      ...route.query,
      page: String(newPage)
    }
  })
}

function reload() {
  load(route.query)
}

function openDetail(row) {
  router.push(`/audit/${row.requestId}`)
}
</script>

<template>
  <div class="page-container audit-list">
    <h2 class="page-title">
      审计日志
    </h2>

    <el-card
      class="audit-filter"
      shadow="never"
    >
      <el-form
        :model="form"
        inline
        label-width="auto"
        @submit.prevent="search"
      >
        <el-form-item label="requestId">
          <el-input
            v-model="form.requestId"
            placeholder="精确匹配，如 req_3f9a1c0b7d2e"
            clearable
            class="filter-request-id"
          />
        </el-form-item>
        <el-form-item label="核验状态">
          <el-select
            v-model="form.state"
            placeholder="全部"
            clearable
            class="filter-select"
          >
            <el-option
              v-for="option in stateOptions"
              :key="option.value"
              :value="option.value"
              :label="option.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="处理结果">
          <el-select
            v-model="form.action"
            placeholder="全部"
            clearable
            class="filter-select"
          >
            <el-option
              v-for="option in actionOptions"
              :key="option.value"
              :value="option.value"
              :label="option.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模式">
          <el-select
            v-model="form.mode"
            placeholder="全部"
            clearable
            class="filter-select"
          >
            <el-option
              v-for="option in modeOptions"
              :key="option.value"
              :value="option.value"
              :label="option.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="form.range"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            range-separator="~"
            :shortcuts="rangeShortcuts"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            native-type="submit"
          >
            查询
          </el-button>
          <el-button @click="reset">
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card
      class="audit-table-card"
      shadow="never"
    >
      <el-table
        v-loading="loading"
        :data="items"
        row-key="requestId"
        class="audit-table"
        @row-click="openDetail"
      >
        <template #empty>
          <div class="audit-empty">
            <template v-if="loadError">
              <span>审计记录加载失败：{{ loadError }}</span>
              <el-button
                link
                type="primary"
                @click.stop="reload"
              >
                重试
              </el-button>
            </template>
            <template v-else>
              暂无审计记录
            </template>
          </div>
        </template>

        <el-table-column
          label="时间"
          width="122"
          fixed="left"
        >
          <template #default="{ row }">
            {{ formatShortTime(row.createdAt) }}
          </template>
        </el-table-column>

        <el-table-column
          label="requestId"
          width="140"
          fixed="left"
        >
          <template #default="{ row }">
            <span class="mono">{{ row.requestId }}</span>
          </template>
        </el-table-column>

        <el-table-column
          label="模式"
          width="66"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="labelType(RUN_MODE, row.mode)"
              size="small"
              disable-transitions
            >
              {{ labelText(RUN_MODE, row.mode) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column
          label="场景"
          width="62"
          align="center"
        >
          <template #default="{ row }">
            {{ row.scenarioId ?? '—' }}
          </template>
        </el-table-column>

        <el-table-column
          label="问题"
          min-width="100"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.questionPreview }}
          </template>
        </el-table-column>

        <el-table-column
          label="核验状态"
          width="136"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              v-if="row.verificationState"
              :type="labelType(VERIFICATION_STATE, row.verificationState)"
              size="small"
              effect="plain"
              disable-transitions
            >
              {{ labelText(VERIFICATION_STATE, row.verificationState) }}
            </el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>

        <el-table-column
          label="策略"
          width="84"
          align="center"
        >
          <template #default="{ row }">
            <span :title="labelText(GUARD_STRATEGY, row.guardStrategy)">{{ row.guardStrategy ?? '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column
          label="处理结果"
          width="112"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              v-if="row.guardAction"
              :type="labelType(GUARD_ACTION, row.guardAction)"
              size="small"
              disable-transitions
            >
              {{ labelText(GUARD_ACTION, row.guardAction) }}
            </el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>

        <el-table-column
          label="结论"
          width="64"
          align="center"
        >
          <template #default="{ row }">
            <span
              v-if="row.verificationState"
              :class="{ 'has-insufficient': row.insufficientClaims > 0 }"
              :title="`有证据 ${row.supportedClaims} 条 / 共 ${row.totalClaims} 条`"
            >{{ row.supportedClaims }} / {{ row.totalClaims }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>

        <el-table-column
          label="耗时"
          width="72"
          align="right"
        >
          <template #default="{ row }">
            {{ formatSeconds(row.totalMs) }}
          </template>
        </el-table-column>

        <el-table-column
          label="错误码"
          min-width="176"
        >
          <template #default="{ row }">
            <span
              v-if="row.errorCode"
              class="error-code mono"
            >{{ row.errorCode }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="audit-pagination">
        <el-pagination
          :current-page="page"
          :page-size="AUDIT_PAGE_SIZE"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="changePage"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.audit-filter {
  margin-bottom: 16px;
}

.audit-filter :deep(.el-form-item) {
  margin-bottom: 12px;
}

.filter-request-id {
  width: 220px;
}

.filter-select {
  width: 140px;
}

.audit-table :deep(.el-table__row) {
  cursor: pointer;
}

/* 单元格不换行，避免行高不一；放不下时表格内部横向滚动 */
.audit-table :deep(.cell) {
  white-space: nowrap;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

.has-insufficient {
  padding: 1px 4px;
  border-radius: 3px;
  background-color: var(--el-color-warning-light-8);
}

.error-code {
  color: var(--el-color-danger);
}

.audit-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.audit-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
