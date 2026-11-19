<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'

import AnswerCard from '@/components/AnswerCard.vue'
import ErrorCard from '@/components/ErrorCard.vue'
import {
  QUESTION_MAX_LENGTH,
  ask,
  chatSession,
  resetChatSession
} from '@/stores/chatSession'

/**
 * 页面一：AI 问答与幻觉核验（任务卡 6.6）。
 * 回答在后端核验完成后一次性返回，等待期间只显示加载卡片，不展示任何未核验内容。
 */

const EXAMPLE_QUESTIONS = [
  '公司正式员工一年有多少天年假？',
  '公司员工有哪些福利？',
  '公司食堂周末几点开门？'
]

const input = ref('')
const inputRef = ref(null)
const listRef = ref(null)
const now = ref(Date.now())

let timer = null

const canSend = computed(() => {
  const text = input.value.trim()
  return !chatSession.pending
    && text.length > 0
    && text.length <= QUESTION_MAX_LENGTH
})

const elapsedSeconds = computed(() => {
  if (!chatSession.pendingSince) {
    return 0
  }
  return Math.max(0, Math.floor((now.value - chatSession.pendingSince) / 1000))
})

const roundCount = computed(() => {
  return chatSession.messages.filter((m) => m.type === 'question').length
})

function submit() {
  if (!canSend.value) {
    return
  }
  const question = input.value
  input.value = ''
  ask(question)
}

function onKeydown(event) {
  if (event.key !== 'Enter' || event.shiftKey) {
    return
  }
  // 中文输入法组字时按 Enter 是确认拼音，不发送
  if (event.isComposing || event.keyCode === 229) {
    return
  }
  event.preventDefault()
  submit()
}

function newSession() {
  resetChatSession()
  input.value = ''
  focusInput()
}

function useExample(question) {
  input.value = question
  focusInput()
}

function focusInput() {
  nextTick(() => {
    if (inputRef.value) {
      inputRef.value.focus()
    }
  })
}

function scrollToBottom() {
  nextTick(() => {
    const el = listRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

/** 回答到达后，把对应的问题滚到顶部，让提示条和回答开头可见（长回答时尤其重要）。 */
function scrollToLatestQuestion() {
  nextTick(() => {
    const list = listRef.value
    if (!list) {
      return
    }
    const questions = list.querySelectorAll('.chat-row--user')
    const latest = questions[questions.length - 1]
    if (latest) {
      list.scrollTop = Math.max(0, latest.offsetTop - 8)
    }
  })
}

watch(
  () => [chatSession.messages.length, chatSession.pending],
  ([, pending], previous) => {
    const finished = previous && previous[1] && !pending
    if (finished) {
      scrollToLatestQuestion()
      focusInput()
    } else {
      scrollToBottom()
    }
  }
)

onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
  scrollToBottom()
  focusInput()
})

onBeforeUnmount(() => {
  clearInterval(timer)
})
</script>

<template>
  <div class="chat-view page-container">
    <div class="chat-header">
      <h2 class="page-title">
        问答核验
      </h2>
      <span class="chat-session">
        <template v-if="chatSession.sessionId">
          会话 {{ chatSession.sessionId }} · 第 {{ roundCount }} 轮
        </template>
        <template v-else>
          新会话
        </template>
      </span>
    </div>

    <div
      ref="listRef"
      class="chat-messages"
    >
      <div
        v-if="chatSession.messages.length === 0 && !chatSession.pending"
        class="chat-welcome"
      >
        <p class="chat-welcome__text">
          向公司制度知识库提问。系统会先检索资料、生成回答，再逐条核验结论是否有知识库证据支持，核验完成后一次性展示结果。
        </p>
        <div class="chat-welcome__examples">
          <el-button
            v-for="q in EXAMPLE_QUESTIONS"
            :key="q"
            round
            @click="useExample(q)"
          >
            {{ q }}
          </el-button>
        </div>
      </div>

      <template
        v-for="message in chatSession.messages"
        :key="message.id"
      >
        <div
          v-if="message.type === 'question'"
          class="chat-row chat-row--user"
        >
          <div class="chat-bubble">{{ message.text }}</div>
        </div>

        <div
          v-else-if="message.type === 'answer'"
          class="chat-row chat-row--assistant"
        >
          <AnswerCard :response="message.response" />
        </div>

        <div
          v-else-if="message.type === 'error'"
          class="chat-row chat-row--assistant"
        >
          <ErrorCard
            :message="message.error.message"
            :error-code="message.error.errorCode"
            :request-id="message.error.requestId"
            :http-status="message.error.httpStatus"
          />
        </div>
      </template>

      <div
        v-if="chatSession.pending"
        class="chat-row chat-row--assistant"
      >
        <el-card
          class="chat-loading"
          shadow="never"
        >
          <el-icon class="is-loading">
            <Loading />
          </el-icon>
          <span>正在检索资料、生成回答并核验，通常需要 10–40 秒</span>
          <span class="chat-loading__elapsed">已等待 {{ elapsedSeconds }} 秒</span>
        </el-card>
      </div>
    </div>

    <div class="chat-input">
      <el-input
        ref="inputRef"
        v-model="input"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 6 }"
        :maxlength="QUESTION_MAX_LENGTH"
        show-word-limit
        resize="none"
        :disabled="chatSession.pending"
        placeholder="请输入问题，Enter 发送，Shift+Enter 换行"
        @keydown="onKeydown"
      />
      <div class="chat-input__actions">
        <el-button
          type="primary"
          :disabled="!canSend"
          @click="submit"
        >
          发送
        </el-button>
        <el-button
          :disabled="chatSession.pending"
          @click="newSession"
        >
          新会话
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 占满内容区可见高度（上下内边距 28px × 2，Mock 提示条高度见 App.vue），消息列表内部滚动 */
.chat-view {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 56px - var(--ag-banner-height, 0px));
  min-height: 420px;
}

.chat-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.chat-session {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.chat-messages {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 4px 16px;
  overflow-y: auto;
}

.chat-welcome {
  margin: auto;
  max-width: 640px;
  text-align: center;
}

.chat-welcome__text {
  font-size: 15px;
  line-height: 1.8;
  color: var(--el-text-color-regular);
}

.chat-welcome__examples {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.chat-welcome__examples .el-button + .el-button {
  margin-left: 0;
}

.chat-row {
  display: flex;
}

.chat-row--user {
  justify-content: flex-end;
}

.chat-row--assistant {
  justify-content: flex-start;
}

.chat-row--assistant > * {
  width: 100%;
}

.chat-bubble {
  max-width: 75%;
  padding: 10px 14px;
  font-size: 15px;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  color: #fff;
  background-color: var(--el-color-primary);
  border-radius: 12px 12px 2px 12px;
}

.chat-loading :deep(.el-card__body) {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 15px;
  color: var(--el-text-color-regular);
}

.chat-loading__elapsed {
  margin-left: auto;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.chat-input {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-light);
}

.chat-input :deep(.el-textarea__inner) {
  font-size: 15px;
  line-height: 1.6;
}

.chat-input__actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chat-input__actions .el-button + .el-button {
  margin-left: 0;
}
</style>
