import type { AppConfig } from '@/types/app'
import { defineStore } from 'pinia'
import { STORAGE_KEYS, createLocalId, readStorage, writeStorage } from '@/utils/storage'

const DEFAULT_BASE_URL = 'http://127.0.0.1:8080'

function createDefaultConfig(): AppConfig {
  return {
    baseUrl: DEFAULT_BASE_URL,
    userId: createLocalId('user'),
    chatId: createLocalId('chat'),
  }
}

export const useConfigStore = defineStore('config', {
  state: (): AppConfig => readStorage(STORAGE_KEYS.config, createDefaultConfig()),
  actions: {
    save(partial: Partial<AppConfig>) {
      this.baseUrl = normalizeBaseUrl(partial.baseUrl ?? this.baseUrl)
      this.userId = normalizeText(partial.userId) || this.userId
      this.chatId = normalizeText(partial.chatId) || this.chatId
      this.persist()
    },
    rotateChatId() {
      this.chatId = createLocalId('chat')
      this.persist()
      return this.chatId
    },
    resetUserId() {
      this.userId = createLocalId('user')
      this.persist()
    },
    persist() {
      writeStorage(STORAGE_KEYS.config, {
        baseUrl: this.baseUrl,
        userId: this.userId,
        chatId: this.chatId,
      } satisfies AppConfig)
    },
  },
})

function normalizeBaseUrl(value: string) {
  return normalizeText(value)?.replace(/\/+$/, '') || DEFAULT_BASE_URL
}

function normalizeText(value?: string) {
  return typeof value === 'string' ? value.trim() : ''
}
