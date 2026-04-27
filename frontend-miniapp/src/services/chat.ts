import type { ChatApiResponse, ChatPayload } from '@/types/app'
import type { RequestAuthContext } from '@/services/request'
import { apiRequest } from '@/services/request'

export async function postChat(payload: ChatPayload, authContext: RequestAuthContext) {
  const body = await apiRequest<Record<string, unknown>>({
    path: '/api/chat',
    method: 'POST',
    data: payload,
    authContext,
  })

  return {
    chatId: readString(body.chatId) ?? payload.chatId,
    route: readRoute(body.route),
    message: readString(body.message) ?? '服务端没有返回消息。',
    requiresConfirmation: Boolean(body.requiresConfirmation),
    data: readRecord(body.data),
  } satisfies ChatApiResponse
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
