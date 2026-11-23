import { reactive } from 'vue'

/**
 * 审批中心当前的演示身份（请求头 X-Demo-User）。仅用于演示，不是真实的身份认证。
 * 放在模块级：跳到审计详情再返回时保持不变。
 */
export const DEMO_USERS = ['user', 'manager', 'admin']

export const demoUser = reactive({
  current: 'admin'
})
