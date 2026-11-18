import { createApp, h, nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus from 'element-plus'

/**
 * 在 jsdom 中挂载组件（不依赖 @vue/test-utils）。
 * 返回 { el, app, router, unmount }。
 */
export async function mount(component, props = {}) {
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
      { path: '/audit/:requestId', component: { render: () => null } }
    ]
  })

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
