<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ChatDotRound, Tickets, Monitor } from '@element-plus/icons-vue'

import { USE_MOCK } from '@/mock'

const route = useRoute()

const navItems = [
  {
    path: '/chat',
    label: '问答核验',
    icon: ChatDotRound
  },
  {
    path: '/audit',
    label: '审计日志',
    icon: Tickets
  },
  {
    path: '/demo',
    label: '演示场景',
    icon: Monitor
  }
]

// 审计详情页 /audit/:requestId 也高亮“审计日志”
const activeMenu = computed(() => {
  const matched = navItems.find((item) => route.path.startsWith(item.path))
  return matched ? matched.path : route.path
})
</script>

<template>
  <el-container class="layout">
    <el-header class="layout-header">
      <span class="layout-title">AI Guardian · AI 幻觉防火墙</span>
      <el-tag
        v-if="USE_MOCK"
        type="warning"
        effect="dark"
      >
        前端 Mock 模式（答辩前必须关闭）
      </el-tag>
    </el-header>

    <el-container class="layout-body">
      <el-aside
        width="200px"
        class="layout-aside"
      >
        <el-menu
          :default-active="activeMenu"
          router
          class="layout-menu"
        >
          <el-menu-item
            v-for="item in navItems"
            :key="item.path"
            :index="item.path"
          >
            <el-icon>
              <component :is="item.icon" />
            </el-icon>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 24px;
  background-color: #1f2d3d;
  color: #fff;
}

.layout-title {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.layout-body {
  height: calc(100% - 56px);
}

.layout-aside {
  background-color: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-light);
}

.layout-menu {
  border-right: none;
}

.layout-main {
  padding: 24px;
  overflow-y: auto;
}
</style>
