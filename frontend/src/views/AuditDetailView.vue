<script setup>
// 占位页面：脚手架阶段只验证路由参数，具体功能见任务卡 6.7。
defineProps({
  requestId: {
    type: String,
    required: true
  }
})
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">审计详情</h2>
    <el-descriptions
      :column="1"
      border
    >
      <el-descriptions-item label="requestId">
        {{ requestId }}
      </el-descriptions-item>
    </el-descriptions>
    <el-empty description="基本信息、原始回答、结论表、证据列表、事件时间线（任务卡 6.7）" />
  </div>
</template>
