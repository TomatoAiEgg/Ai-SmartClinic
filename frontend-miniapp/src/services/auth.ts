import type { LoginResponse, WechatLoginPayload } from '@/types/app'
import type { RequestAuthContext } from '@/services/request'
import { apiRequest } from '@/services/request'

export async function postWechatLogin(payload: WechatLoginPayload) {
  const body = await apiRequest<Record<string, unknown>>({
    path: '/api/auth/wechat-login',
    method: 'POST',
    auth: false,
    data: payload,
  })

  return toLoginResponse(body, '微信登录响应缺少 token 或 userId。')
}

export async function postLogout(authContext: RequestAuthContext) {
  await apiRequest<Record<string, unknown>>({
    path: '/api/auth/logout',
    method: 'POST',
    authContext,
  })
}

function toLoginResponse(body: Record<string, unknown>, missingMessage: string) {
  const token = readString(body.token)
  const userId = readString(body.userId)
  if (!token || !userId) {
    throw new Error(missingMessage)
  }

  return {
    token,
    userId,
    expiresIn: Number(body.expiresIn ?? 0),
    tokenHeader: readString(body.tokenHeader) ?? 'X-Auth-Token',
  } satisfies LoginResponse
}

function readString(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}
