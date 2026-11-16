// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

const sendQuestion = vi.fn()

vi.mock('../src/api/chat.js', () => ({
  sendQuestion: (...args) => sendQuestion(...args)
}))

const { mount } = await import('./helpers/mount')
const { default: ChatView } = await import('../src/views/ChatView.vue')
const { resetChatSession } = await import('../src/stores/chatSession')
const { CASES } = await import('../src/mock/fixtures/cases')
const { buildExchange } = await import('../src/mock/fixtures/builder')

let mounted = null

function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

async function flush() {
  for (let i = 0; i < 5; i++) {
    await Promise.resolve()
    await nextTick()
  }
}

function textarea() {
  return mounted.el.querySelector('.chat-input textarea')
}

function sendButton() {
  return mounted.el.querySelector('.chat-input__actions .el-button--primary')
}

async function type(value) {
  const el = textarea()
  el.value = value
  el.dispatchEvent(new Event('input'))
  await flush()
}

async function keydown(init) {
  textarea().dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, cancelable: true, ...init }))
  await flush()
}

beforeEach(async () => {
  resetChatSession()
  sendQuestion.mockReset()
  mounted = await mount(ChatView)
})

afterEach(() => {
  mounted.unmount()
})

describe('ChatView', () => {
  it('初始显示欢迎语，点击示例问题填入输入框（不直接发送）', async () => {
    expect(mounted.el.querySelector('.chat-welcome')).not.toBeNull()
    mounted.el.querySelector('.chat-welcome__examples .el-button').click()
    await flush()
    expect(textarea().value).toBe('公司正式员工一年有多少天年假？')
    expect(sendQuestion).not.toHaveBeenCalled()
  })

  it('空白时发送按钮禁用；输入框限制 500 字并显示字数', async () => {
    expect(sendButton().disabled).toBe(true)
    await type('   ')
    expect(sendButton().disabled).toBe(true)
    expect(textarea().getAttribute('maxlength')).toBe('500')
    expect(mounted.el.querySelector('.el-input__count').textContent).toContain('/ 500')
  })

  it('Enter 发送；Shift+Enter 不发送；输入法组字中按 Enter 不发送', async () => {
    sendQuestion.mockReturnValue(new Promise(() => {}))
    await type('年假有几天')

    await keydown({ key: 'Enter', shiftKey: true })
    expect(sendQuestion).not.toHaveBeenCalled()

    await keydown({ key: 'Enter', isComposing: true })
    expect(sendQuestion).not.toHaveBeenCalled()

    await keydown({ key: 'Enter', keyCode: 229 })
    expect(sendQuestion).not.toHaveBeenCalled()

    await keydown({ key: 'Enter' })
    expect(sendQuestion).toHaveBeenCalledWith(null, '年假有几天')
  })

  it('发送后：问题右侧气泡、输入框清空并禁用、显示加载卡片；返回后显示 AnswerCard', async () => {
    const d = deferred()
    sendQuestion.mockReturnValueOnce(d.promise)
    await type('公司员工有哪些福利？')
    sendButton().click()
    await flush()

    expect(mounted.el.querySelector('.chat-row--user .chat-bubble').textContent).toBe('公司员工有哪些福利？')
    expect(textarea().value).toBe('')
    expect(textarea().disabled).toBe(true)
    expect(mounted.el.querySelector('.chat-loading').textContent).toContain('正在检索资料、生成回答并核验，通常需要 10–40 秒')
    expect(mounted.el.querySelector('.chat-loading').textContent).toContain('已等待')

    d.resolve(buildExchange(CASES.partial).response)
    await flush()

    expect(mounted.el.querySelector('.chat-loading')).toBeNull()
    expect(mounted.el.querySelector('.chat-row--assistant .answer-card')).not.toBeNull()
    expect(textarea().disabled).toBe(false)
    expect(mounted.el.querySelector('.chat-session').textContent).toContain('第 1 轮')
  })

  it('请求失败：插入错误卡片，显示 message 与 requestId', async () => {
    sendQuestion.mockRejectedValueOnce({
      errorCode: 'LLM_GENERATION_FAILED',
      message: '大模型生成回答失败，请稍后重试。',
      requestId: 'req_cccccccccccc',
      httpStatus: 502
    })
    await type('报销需要提交哪些材料？')
    await keydown({ key: 'Enter' })
    await flush()

    const card = mounted.el.querySelector('.error-card')
    expect(card.textContent).toContain('大模型生成回答失败，请稍后重试。')
    expect(card.textContent).toContain('req_cccccccccccc')
    expect(card.querySelector('a').getAttribute('href')).toBe('/audit/req_cccccccccccc')
  })

  it('无法连接后端：错误卡片只显示提示，不显示 requestId', async () => {
    sendQuestion.mockRejectedValueOnce({ errorCode: null, message: '无法连接后端服务', requestId: null, httpStatus: null })
    await type('任意问题')
    await keydown({ key: 'Enter' })
    await flush()
    const card = mounted.el.querySelector('.error-card')
    expect(card.textContent).toContain('无法连接后端服务')
    expect(card.textContent).not.toContain('requestId')
  })

  it('新会话：清空消息与 sessionId', async () => {
    sendQuestion.mockResolvedValueOnce(buildExchange(CASES.allSupported).response)
    await type('年假有几天')
    await keydown({ key: 'Enter' })
    await flush()
    expect(mounted.el.querySelectorAll('.chat-row')).toHaveLength(2)

    const newSessionButton = [...mounted.el.querySelectorAll('.chat-input__actions .el-button')].find((b) => b.textContent.includes('新会话'))
    newSessionButton.click()
    await flush()
    expect(mounted.el.querySelectorAll('.chat-row')).toHaveLength(0)
    expect(mounted.el.querySelector('.chat-session').textContent).toContain('新会话')
  })

  it('请求进行中“新会话”按钮禁用', async () => {
    sendQuestion.mockReturnValueOnce(new Promise(() => {}))
    await type('问题')
    await keydown({ key: 'Enter' })
    const newSessionButton = [...mounted.el.querySelectorAll('.chat-input__actions .el-button')].find((b) => b.textContent.includes('新会话'))
    expect(newSessionButton.disabled).toBe(true)
  })
})
