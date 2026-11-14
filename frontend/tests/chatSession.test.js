import { beforeEach, describe, expect, it, vi } from 'vitest'

const sendQuestion = vi.fn()

vi.mock('../src/api/chat.js', () => ({
  sendQuestion: (...args) => sendQuestion(...args)
}))

const { ask, chatSession, resetChatSession, QUESTION_MAX_LENGTH } = await import('../src/stores/chatSession')

function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

function fakeResponse(sessionId = 'ses_aaaaaaaaaaaa') {
  return {
    requestId: 'req_bbbbbbbbbbbb',
    sessionId
  }
}

beforeEach(() => {
  resetChatSession()
  sendQuestion.mockReset()
})

describe('chatSession.ask', () => {
  it('去除首尾空白后发送，首次 sessionId 为 null，之后带上响应中的 sessionId', async () => {
    sendQuestion.mockResolvedValueOnce(fakeResponse('ses_111111111111'))
    await ask('  公司员工有哪些福利？ ')
    expect(sendQuestion).toHaveBeenLastCalledWith(null, '公司员工有哪些福利？')
    expect(chatSession.sessionId).toBe('ses_111111111111')

    sendQuestion.mockResolvedValueOnce(fakeResponse('ses_111111111111'))
    await ask('年假有几天')
    expect(sendQuestion).toHaveBeenLastCalledWith('ses_111111111111', '年假有几天')
    expect(chatSession.messages.map((m) => m.type)).toEqual(['question', 'answer', 'question', 'answer'])
  })

  it('请求期间 pending=true，结束后恢复', async () => {
    const d = deferred()
    sendQuestion.mockReturnValueOnce(d.promise)
    const running = ask('问题')
    expect(chatSession.pending).toBe(true)
    expect(chatSession.pendingSince).not.toBeNull()
    d.resolve(fakeResponse())
    await running
    expect(chatSession.pending).toBe(false)
    expect(chatSession.pendingSince).toBeNull()
  })

  it('空白、超过 500 字、请求进行中时不发送', async () => {
    expect(await ask('   ')).toBe(false)
    expect(await ask('字'.repeat(QUESTION_MAX_LENGTH + 1))).toBe(false)
    expect(sendQuestion).not.toHaveBeenCalled()

    const d = deferred()
    sendQuestion.mockReturnValueOnce(d.promise)
    const running = ask('第一个')
    expect(await ask('第二个')).toBe(false)
    expect(sendQuestion).toHaveBeenCalledTimes(1)
    d.resolve(fakeResponse())
    await running
  })

  it('恰好 500 字可以发送', async () => {
    sendQuestion.mockResolvedValueOnce(fakeResponse())
    expect(await ask('字'.repeat(QUESTION_MAX_LENGTH))).toBe(true)
  })

  it('失败时插入错误消息，sessionId 不变', async () => {
    sendQuestion.mockResolvedValueOnce(fakeResponse('ses_222222222222'))
    await ask('第一个')
    const error = { errorCode: 'LLM_GENERATION_FAILED', message: '大模型生成回答失败，请稍后重试。', requestId: 'req_cccccccccccc', httpStatus: 502 }
    sendQuestion.mockRejectedValueOnce(error)
    await ask('第二个')
    expect(chatSession.messages[3]).toMatchObject({ type: 'error', error })
    expect(chatSession.sessionId).toBe('ses_222222222222')
    expect(chatSession.pending).toBe(false)
  })

  it('新会话清空消息与 sessionId；旧会话未返回的结果被丢弃', async () => {
    const d = deferred()
    sendQuestion.mockReturnValueOnce(d.promise)
    const running = ask('旧会话的问题')
    resetChatSession()
    expect(chatSession.messages).toEqual([])
    expect(chatSession.pending).toBe(false)
    d.resolve(fakeResponse('ses_333333333333'))
    await running
    expect(chatSession.messages).toEqual([])
    expect(chatSession.sessionId).toBeNull()
  })
})
