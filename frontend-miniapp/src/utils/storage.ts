export const STORAGE_KEYS = {
  config: 'ai-registration:config',
  chatHistory: 'ai-registration:chat-history',
  appointments: 'ai-registration:appointments',
} as const

export function readStorage<T>(key: string, fallback: T): T {
  try {
    const raw = uni.getStorageSync(key)
    if (raw === '' || raw === undefined || raw === null) {
      return fallback
    }
    if (typeof raw === 'string') {
      return JSON.parse(raw) as T
    }
    return raw as T
  }
  catch {
    return fallback
  }
}

export function writeStorage(key: string, value: unknown) {
  uni.setStorageSync(key, JSON.stringify(value))
}

export function removeStorage(key: string) {
  uni.removeStorageSync(key)
}

export function createLocalId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}
