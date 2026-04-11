import type { ChatApiResponse, ChatPayload } from '@/types/app'

export async function postChat(baseUrl: string, payload: ChatPayload) {
  const url = `${normalizeBaseUrl(baseUrl)}/api/chat`

  return new Promise<ChatApiResponse>((resolve, reject) => {
    uni.request({
      url,
      method: 'POST',
      header: {
        'Content-Type': 'application/json',
      },
      data: payload,
      success: (response) => {
        const body = normalizeResponseBody(response.data)
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve({
            chatId: readString(body.chatId) ?? payload.chatId,
            route: readRoute(body.route),
            message: readString(body.message) ?? '服务端没有返回消息。',
            requiresConfirmation: Boolean(body.requiresConfirmation),
            data: readRecord(body.data),
          })
          return
        }

        reject(new Error(readString(body.message) ?? `请求失败，HTTP ${response.statusCode}`))
      },
      fail: (error) => {
        reject(new Error(error.errMsg || '网络请求失败。'))
      },
    })
  })
}

function normalizeBaseUrl(baseUrl: string) {
  return baseUrl.trim().replace(/\/+$/, '')
}

function normalizeResponseBody(data: unknown) {
  if (typeof data === 'string') {
    try {
      return JSON.parse(data) as Record<string, unknown>
    }
    catch {
      return { message: data }
    }
  }
  return readRecord(data)
}

function readRecord(value: unknown) {
  return value && typeof value === 'object' ? value as Record<string, unknown> : {}
}

function readString(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

function readRoute(value: unknown): ChatApiResponse['route'] {
  if (value === 'TRIAGE' || value === 'REGISTRATION' || value === 'GUIDE' || value === 'HUMAN_REVIEW') {
    return value
  }
  return 'GUIDE'
}
