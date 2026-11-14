<script setup>
import {
  CLAIM_STATUS,
  INSUFFICIENT_REASON,
  MODEL_JUDGEMENT,
  QUOTE_CHECK,
  labelText,
  labelType
} from '@/constants/labels'

/**
 * 逐条结论表（任务卡 6.5；审计详情页传 admin 额外显示模型初判、引用校验、引用原文，任务卡 6.7）。
 * 状态只展示后端给出的 status，不做任何判断。
 */
const props = defineProps({
  claims: {
    type: Array,
    required: true
  },
  evidence: {
    type: Array,
    default: () => []
  },
  admin: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['open-evidence'])

function evidenceLabel(evidenceId) {
  const found = props.evidence.find((e) => e.evidenceId === evidenceId)
  if (!found) {
    return evidenceId
  }
  return found.section
    ? `《${found.documentTitle}》· ${found.section}`
    : `《${found.documentTitle}》`
}
</script>

<template>
  <el-table
    class="claim-table"
    :data="claims"
    border
    size="small"
  >
    <el-table-column
      label="序号"
      width="60"
      align="center"
    >
      <template #default="{ $index }">
        {{ $index + 1 }}
      </template>
    </el-table-column>

    <el-table-column
      label="结论"
      min-width="220"
    >
      <template #default="{ row }">
        {{ row.claim }}
      </template>
    </el-table-column>

    <el-table-column
      label="状态"
      width="100"
      align="center"
    >
      <template #default="{ row }">
        <el-tag
          :type="labelType(CLAIM_STATUS, row.status)"
          disable-transitions
        >
          {{ labelText(CLAIM_STATUS, row.status) }}
        </el-tag>
      </template>
    </el-table-column>

    <el-table-column
      label="原因"
      min-width="200"
    >
      <template #default="{ row }">
        {{ labelText(INSUFFICIENT_REASON, row.insufficientReason) }}
      </template>
    </el-table-column>

    <template v-if="admin">
      <el-table-column
        label="模型初判"
        width="90"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            :type="labelType(MODEL_JUDGEMENT, row.modelSupported)"
            effect="plain"
            disable-transitions
          >
            {{ labelText(MODEL_JUDGEMENT, row.modelSupported) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column
        label="程序校验"
        width="110"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            :type="labelType(QUOTE_CHECK, row.quoteExists)"
            effect="plain"
            disable-transitions
          >
            {{ labelText(QUOTE_CHECK, row.quoteExists) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column
        label="引用原文（quote）"
        min-width="200"
      >
        <template #default="{ row }">
          {{ row.quote ?? '' }}
        </template>
      </el-table-column>
    </template>

    <el-table-column
      label="证据"
      min-width="180"
    >
      <template #default="{ row }">
        <el-link
          v-if="row.evidenceId"
          type="primary"
          underline="never"
          @click="emit('open-evidence', row)"
        >
          {{ evidenceLabel(row.evidenceId) }}
        </el-link>
      </template>
    </el-table-column>
  </el-table>
</template>
