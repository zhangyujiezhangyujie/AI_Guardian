<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ChatDotRound, Tickets, Monitor } from '@element-plus/icons-vue'

import { USE_MOCK } from '@/mock'

/**
 * 整体布局（任务卡 6.1）：左侧深色导航（品牌 + 三项导航）+ 右侧浅色内容区。
 * 标题文字保持“AI Guardian · AI 幻觉防火墙”。
 */

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
  <div
    class="layout"
    :style="{ '--ag-banner-height': USE_MOCK ? '32px' : '0px' }"
  >
    <aside class="layout-aside">
      <div class="brand">
        <svg
          class="brand-logo"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path
            d="M12 2.5 4.5 5.4v6.1c0 4.6 3.1 8.6 7.5 9.9 4.4-1.3 7.5-5.3 7.5-9.9V5.4L12 2.5Z"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linejoin="round"
          />
          <path
            d="m8.6 12.1 2.4 2.4 4.4-4.6"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
        <div class="brand-text">
          <div class="brand-name">
            AI Guardian
          </div>
          <div class="brand-sub">
            AI 幻觉防火墙
          </div>
        </div>
      </div>

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
    </aside>

    <main class="layout-main">
      <div
        v-if="USE_MOCK"
        class="mock-banner"
      >
        前端 Mock 模式：数据为前端模拟，答辩前必须改用 npm run dev
      </div>
      <div class="layout-content">
        <router-view />
      </div>
    </main>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  height: 100%;
}

.layout-aside {
  flex: none;
  display: flex;
  flex-direction: column;
  width: 220px;
  padding: 20px 14px;
  background-color: var(--ag-sidebar-bg);
  color: var(--ag-sidebar-text);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 8px 22px;
}

.brand-logo {
  flex: none;
  width: 28px;
  height: 28px;
  color: #fff;
}

.brand-name {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.3px;
}

.brand-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--ag-sidebar-muted);
}

.layout-menu {
  border-right: none;
  background-color: transparent;
}

.layout-menu .el-menu-item {
  height: 44px;
  margin-bottom: 4px;
  padding: 0 14px !important;
  border-radius: 8px;
  color: var(--ag-sidebar-text);
  font-size: 15px;
}

.layout-menu .el-menu-item .el-icon {
  color: inherit;
}

.layout-menu .el-menu-item:hover {
  background-color: var(--ag-sidebar-hover);
  color: #fff;
}

.layout-menu .el-menu-item.is-active {
  background-color: var(--ag-sidebar-active);
  color: #fff;
  font-weight: 600;
}

.layout-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow-y: auto;
  background-color: var(--el-bg-color-page);
}

.mock-banner {
  flex: none;
  display: flex;
  align-items: center;
  height: var(--ag-banner-height);
  padding: 0 24px;
  font-size: 13px;
  color: var(--el-color-warning-dark-2);
  background-color: var(--el-color-warning-light-8);
  border-bottom: 1px solid var(--el-color-warning-light-7);
}

.layout-content {
  flex: 1 0 auto;
  padding: 28px 32px;
}
</style>
