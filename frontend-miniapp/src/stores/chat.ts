import type { ChatApiResponse, ChatMessage, MessageAction } from '@/types/app'
import { defineStore } from 'pinia'
import { postChat } from '@/services/api'
import { useAppointmentsStore } from '@/stores/appointments'
import { useConfigStore } from '@/stores/config'
import { STORAGE_KEYS, createLocalId, readStorage, writeStorage } from '@/utils/storage'

const WELCOME_MESSAGE: ChatMessage = {
  id: createLocalId('assistant'),
  role: 'assistant',
  content: '我是 AI 挂号助手。你可以直接说“帮我挂明天下午呼吸内科”或者“查询挂号 REG-1234ABCD”。',
  createdAt: formatTimestamp(new Date()),
  route: 'GUIDE',
}

export const useChatStore = defineStore('chat', {
  state: () => ({
    messages: readStorage<ChatMessage[]>(STORAGE_KEYS.chatHistory, [WELCOME_MESSAGE]),
    sending: false,
  }),
  getters: {
    hasMessages(state) {
      return state.messages.length > 0
    },
  },
  actions: {
    ensureWelcome() {
      if (!this.messages.length) {
        this.messages = [WELCOME_MESSAGE]
        this.persist()
      }
    },
    clearHistory() {
      this.messages = [{
        ...WELCOME_MESSAGE,
        id: createLocalId('assistant'),
        createdAt: formatTimestamp(new Date()),
      }]
      this.persist()
    },
    async sendMessage(message: string, metadata: Record<string, string> = {}) {
      const content = message.trim()
      if (!content || this.sending) {
        return undefined
      }

      const configStore = useConfigStore()
      const appointmentsStore = useAppointmentsStore()

      this.appendMessage({
        id: createLocalId('user'),
        role: 'user',
        content,
        createdAt: formatTimestamp(new Date()),
      })

      this.sending = true
      try {
        const response = await postChat(configStore.baseUrl, {
          chatId: configStore.chatId,
          userId: configStore.userId,
          message: content,
          metadata,
        })

        this.appendMessage({
          id: createLocalId('assistant'),
          role: 'assistant',
          content: response.message,
          createdAt: formatTimestamp(new Date()),
          route: response.route,
          requiresConfirmation: response.requiresConfirmation,
          data: response.data,
          actions: buildActions(response),
        })
        appointmentsStore.syncFromResponse(response)
        return response
      }
      catch (error) {
        const reason = error instanceof Error ? error.message : '网络请求失败。'
        this.appendMessage({
          id: createLocalId('system'),
          role: 'system',
          content: `请求失败：${reason}`,
          createdAt: formatTimestamp(new Date()),
        })
        return undefined
      }
      finally {
        this.sending = false
      }
    },
    appendMessage(message: ChatMessage) {
      this.messages = [...this.messages, message].slice(-60)
      this.persist()
    },
    persist() {
      writeStorage(STORAGE_KEYS.chatHistory, this.messages)
    },
  },
})

function buildActions(response: ChatApiResponse): MessageAction[] {
  const actions: MessageAction[] = []
  const data = toStringMap(response.data)
  const action = data.action

  if (response.requiresConfirmation && action) {
    actions.push({
      label: action === 'cancel' ? '确认取消' : action === 'reschedule' ? '确认改约' : '确认提交',
      message: confirmationText(action),
      metadata: {
        ...data,
        action,
        confirmed: 'true',
      },
      tone: action === 'cancel' ? 'danger' : 'primary',
    })
    actions.push({
      label: '稍后再说',
      message: '先不处理',
      tone: 'default',
    })
  }

  if (response.route === 'TRIAGE' && data.departmentCode) {
    actions.push({
      label: '挂这个科室',
      message: `帮我挂${data.departmentName || data.departmentCode}最近的号`,
      metadata: {
        action: 'create',
      },
      tone: 'primary',
    })
  }

  if (!response.requiresConfirmation && data.registrationId) {
    actions.push({
      label: '查询状态',
      message: `查询挂号 ${data.registrationId}`,
      metadata: {
        action: 'query',
        registrationId: data.registrationId,
      },
      tone: 'default',
    })
    if (data.status !== 'CANCELLED') {
      actions.push({
        label: '取消挂号',
        message: `取消挂号 ${data.registrationId}`,
        metadata: {
          action: 'cancel',
          registrationId: data.registrationId,
        },
        tone: 'danger',
      })
    }
  }

  return actions
}

function confirmationText(action: string) {
  if (action === 'cancel') {
    return '确认取消挂号'
  }
  if (action === 'reschedule') {
    return '确认改约'
  }
  return '确认提交挂号'
}

function toStringMap(data: Record<string, unknown>) {
  return Object.entries(data || {}).reduce<Record<string, string>>((result, [key, value]) => {
    if (typeof value === 'string' && value.trim()) {
      result[key] = value
    }
    else if (typeof value === 'number' || typeof value === 'boolean') {
      result[key] = String(value)
    }
    return result
  }, {})
}

function formatTimestamp(date: Date) {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}
