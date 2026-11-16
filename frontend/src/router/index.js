import { createRouter, createWebHistory } from 'vue-router'

import ChatView from '@/views/ChatView.vue'
import AuditListView from '@/views/AuditListView.vue'
import AuditDetailView from '@/views/AuditDetailView.vue'
import DemoView from '@/views/DemoView.vue'
import { USE_MOCK } from '@/mock'

const routes = [
  {
    path: '/',
    redirect: '/chat'
  },
  {
    path: '/chat',
    name: 'chat',
    component: ChatView,
    meta: {
      title: '问答核验'
    }
  },
  {
    path: '/audit',
    name: 'audit-list',
    component: AuditListView,
    meta: {
      title: '审计日志'
    }
  },
  {
    path: '/audit/:requestId',
    name: 'audit-detail',
    component: AuditDetailView,
    props: true,
    meta: {
      title: '审计详情'
    }
  },
  {
    path: '/demo',
    name: 'demo',
    component: DemoView,
    meta: {
      title: '演示场景'
    }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/chat'
  }
]

// 前端 Mock 专用页面：仅 VITE_USE_MOCK=true 时注册，答辩环境不可访问
if (USE_MOCK) {
  routes.unshift({
    path: '/dev/cards',
    name: 'dev-cards',
    component: () => import('@/views/dev/AnswerCardGallery.vue'),
    meta: {
      title: 'AnswerCard 展示一览'
    }
  })
}

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.afterEach((to) => {
  const pageTitle = to.meta.title
  document.title = pageTitle
    ? `${pageTitle} · AI Guardian`
    : 'AI Guardian · AI 幻觉防火墙'
})

export default router
