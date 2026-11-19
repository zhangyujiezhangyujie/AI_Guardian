import { createApp, h, nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus from 'element-plus'

/**
 * 在 jsdom 中挂载组件（不依赖 @vue/test-utils）。
 * options.path：挂载前先导航到的地址（如 '/audit?page=2'），供依赖 route 的页面测试使用。
 * 返回 { el, app, router, unmount }。
 */
export async function mount(component, props = {}, options = {}) {
  if (!globalThis.ResizeObserver) {
    globalThis.ResizeObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    }
  }

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { render: () => null } },
      { path: '/chat', name: 'chat', component: { render: () => null } },
      { path: '/audit', name: 'audit-list', component: { render: () => null } },
      { path: '/audit/:requestId', name: 'audit-detail', component: { render: () => null } }
    ]
  })

  // 必须在 app.use(router) 之前完成导航，否则安装时的初始导航会覆盖到 '/'
  if (options.path) {
    await router.push(options.path)
  }

  const el = document.createElement('div')
  document.body.appendChild(el)

  const app = createApp({
    render: () => h(component, props)
  })
  app.use(ElementPlus)
  app.use(router)
  app.mount(el)

  await router.isReady()
  await nextTick()

  return {
    el,
    app,
    router,
    unmount() {
      app.unmount()
      el.remove()
    }
  }
}
