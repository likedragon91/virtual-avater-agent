import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import {
  cancelRun,
  createSession as createSessionApi,
  listMessages,
  listSessions,
  streamMessage
} from '@/shared/api/agent'
import { useChatStore } from '../chat'
import { useUiStore } from '../ui'

vi.mock('@/shared/api/agent', () => ({
  createClientMessageId: () => 'c1',
  createSession: vi.fn(),
  deleteSession: vi.fn(),
  listMessages: vi.fn(),
  listSessions: vi.fn(),
  streamMessage: vi.fn(),
  updateSession: vi.fn(),
  cancelRun: vi.fn()
}))

function setup() {
  const uiStore = useUiStore()
  uiStore.setPanelMode(true)
  return useChatStore()
}

function lastReply() {
  const calls = window.desktopBridge.sendChatReply.mock.calls
  return calls[calls.length - 1]?.[0]
}

/** 让 150ms 防抖 watcher 跑完，避免定时器泄漏到下一个用例 */
async function settle() {
  await new Promise((resolve) => setTimeout(resolve, 200))
}

beforeEach(() => {
  setActivePinia(createPinia())
  window.desktopBridge = {
    sendChatReply: vi.fn(),
    hideReplyBubble: vi.fn()
  }

  vi.mocked(createSessionApi).mockImplementation(async () => ({ id: 1, title: '新会话' }))
  vi.mocked(listMessages).mockImplementation(async () => ({
    records: [],
    nextCursor: null,
    hasMore: false
  }))
  vi.mocked(listSessions).mockImplementation(async () => ({
    records: [{ id: 1, title: '新会话', messageCount: 0 }],
    total: 1
  }))
  vi.mocked(cancelRun).mockImplementation(async () => ({}))

  vi.mocked(streamMessage).mockImplementation(async ({ onEvent }) => {
    onEvent('meta', { runId: 9, assistantMessageId: 100 })
    onEvent('delta', { text: '你' })
    onEvent('delta', { text: '好' })
    onEvent('done', {
      finishReason: 'stop',
      usage: { completionTokens: 2 },
      emotion: null
    })
  })
})

describe('chat store reply bubble sync', () => {
  it('done 后用最终内容更新气泡', async () => {
    const chat = setup()
    await chat.sendMessage('hi')

    expect(lastReply()).toEqual({
      text: '你好',
      pending: false,
      error: '',
      status: 1
    })
    await settle()
  })

  it('流式 delta 会持续同步气泡，而不是只等结束', async () => {
    let finish
    vi.mocked(streamMessage).mockImplementation(({ onEvent }) => {
      onEvent('meta', { runId: 9 })
      onEvent('delta', { text: '流式' })
      return new Promise((resolve) => {
        finish = () => {
          onEvent('delta', { text: '内容' })
          onEvent('done', { finishReason: 'stop' })
          resolve()
        }
      })
    })
    const chat = setup()
    const sending = chat.sendMessage('hi')
    await vi.waitFor(() => expect(chat.messages.at(-1)?.content).toBe('流式'))
    await new Promise((resolve) => setTimeout(resolve, 180))

    expect(lastReply()).toEqual(
      expect.objectContaining({ text: '流式', pending: true })
    )

    finish()
    await sending
    expect(lastReply()).toEqual(
      expect.objectContaining({ text: '流式内容', pending: false, status: 1 })
    )
    await settle()
  })

  it('取消生成时状态为 3', async () => {
    vi.mocked(streamMessage).mockImplementation(async ({ onEvent }) => {
      onEvent('meta', { runId: 9 })
      onEvent('delta', { text: '一半' })
      onEvent('done', { finishReason: 'cancelled' })
    })
    const chat = setup()
    await chat.sendMessage('hi')

    expect(lastReply()).toEqual({
      text: '一半',
      pending: false,
      error: '',
      status: 3
    })
    await settle()
  })

  it('error 事件把错误同步给气泡', async () => {
    vi.mocked(streamMessage).mockImplementation(async ({ onEvent }) => {
      onEvent('meta', { runId: 9 })
      onEvent('error', { msg: '连接失败' })
    })
    const chat = setup()
    await chat.sendMessage('hi')

    expect(lastReply()).toEqual(
      expect.objectContaining({
        text: '',
        pending: false,
        error: '连接失败',
        status: 2
      })
    )
    await settle()
  })

  it('停止生成后同步兜底内容', async () => {
    vi.mocked(streamMessage).mockImplementation(({ onEvent, signal }) => {
      onEvent('meta', { runId: 9 })
      onEvent('delta', { text: '生成中' })
      return new Promise((_resolve, reject) => {
        signal.addEventListener('abort', () => {
          const error = new Error('aborted')
          error.name = 'AbortError'
          reject(error)
        })
      })
    })
    const chat = setup()
    const sending = chat.sendMessage('hi')
    await vi.waitFor(() => expect(chat.streaming).toBe(true))
    chat.stopStreaming()
    await sending

    expect(lastReply()).toEqual(
      expect.objectContaining({
        text: '生成中',
        pending: false,
        status: 3
      })
    )
    await settle()
  })

  it('草稿在面板与聊天页之间保留，reset 时清空', async () => {
    const chat = setup()
    await chat.initialize('user-a')
    chat.setDraft('abc')
    await chat.initialize('user-a')
    expect(chat.draft).toBe('abc')

    chat.reset()
    expect(chat.draft).toBe('')
    await settle()
  })

  it('关闭面板时隐藏回复气泡', async () => {
    const uiStore = useUiStore()
    uiStore.setPanelMode(true)
    useChatStore()
    uiStore.setPanelMode(false)
    await nextTick()

    expect(window.desktopBridge.hideReplyBubble).toHaveBeenCalled()
  })
})
