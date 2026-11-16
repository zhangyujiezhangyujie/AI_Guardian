import { reactive } from 'vue'

import { sendQuestion } from '@/api/chat'

/**
 * 问答页的会话状态（任务卡 6.6、契约 5.2）。
 *
 * 放在模块级而不是组件内：从回答跳到审计详情再返回时，消息与进行中的请求都不丢失；
 * 刷新页面后会话消失（契约 5.2 允许）。
 *
 * 消息类型：
 * - { id, type: 'question', text }
 * - { id, type: 'answer', response }      // response 为 ChatResponse（契约 3.7）
 * - { id, type: 'error', error }          // error 为 { errorCode, message, requestId, httpStatus }
 */

export const QUESTION_MAX_LENGTH = 500

export const chatSession = reactive({
  sessionId: null,
  messages: [],
  pending: false,
  pendingSince: null
})

let nextMessageId = 1

// 每次“新会话”递增；旧会话中尚未返回的请求结果会被丢弃
let generation = 0

function push(message) {
  chatSession.messages.push({
    id: nextMessageId++,
    ...message
  })
}

/** “新会话”：清空消息和 sessionId（任务卡 6.6）。 */
export function resetChatSession() {
  generation += 1
  chatSession.sessionId = null
  chatSession.messages = []
  chatSession.pending = false
  chatSession.pendingSince = null
}

/**
 * 发送问题。question 去除首尾空白后为空、超长或已有请求进行中时不发送，返回 false。
 * 成功后保存响应中的 sessionId，后续请求带上；失败插入错误卡片。
 */
export async function ask(question) {
  const text = typeof question === 'string' ? question.trim() : ''
  if (text.length === 0 || text.length > QUESTION_MAX_LENGTH || chatSession.pending) {
    return false
  }

  const myGeneration = generation
  push({
    type: 'question',
    text
  })
  chatSession.pending = true
  chatSession.pendingSince = Date.now()

  try {
    const response = await sendQuestion(chatSession.sessionId, text)
    if (myGeneration !== generation) {
      return true
    }
    chatSession.sessionId = response.sessionId
    push({
      type: 'answer',
      response
    })
  } catch (error) {
    if (myGeneration !== generation) {
      return true
    }
    push({
      type: 'error',
      error
    })
  } finally {
    if (myGeneration === generation) {
      chatSession.pending = false
      chatSession.pendingSince = null
    }
  }
  return true
}
