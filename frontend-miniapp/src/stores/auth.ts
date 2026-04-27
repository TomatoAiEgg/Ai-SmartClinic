import type { AuthState, LoginResponse, WechatProfile } from '@/types/app'
import type { RequestAuthContext } from '@/services/request'
import { defineStore } from 'pinia'
import { postLogout, postWechatLogin } from '@/services/auth'
import { readStorage, removeStorage, STORAGE_KEYS, writeStorage } from '@/utils/storage'

const DEFAULT_USERNAME = '微信用户'
const GENERATED_USER_ID_RE = /^user-\d{10,}-[a-z0-9]{4,}$/i

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => loadInitialAuth(),
  getters: {
    loggedIn(state) {
      return Boolean(state.authToken && state.userId)
    },
    displayName(state) {
      return state.wechatNickname || state.username || '未登录用户'
    },
  },
  actions: {
    async loginWithWechatProfile(profile: WechatProfile) {
      const code = normalizeText(profile.loginCode)
      if (!code) {
        throw new Error('微信登录 code 不能为空。')
      }

      const nickname = normalizeText(profile.nickname) || '微信用户'
      const avatarUrl = normalizeText(profile.avatarUrl)
      const response = await postWechatLogin({
        code,
        nickname,
        avatarUrl,
      })
      this.applyLoginSession(response, {
        username: nickname,
        wechatNickname: nickname,
        wechatAvatarUrl: avatarUrl,
        loginMode: 'wechat',
      })
      return response
    },
    async logout() {
      try {
        if (this.authToken) {
          await postLogout(this.toRequestContext())
        }
      }
      finally {
        this.clearSession()
      }
    },
    clearSession() {
      this.userId = ''
      this.username = DEFAULT_USERNAME
      this.authToken = ''
      this.tokenHeader = 'X-Auth-Token'
      this.wechatNickname = ''
      this.wechatAvatarUrl = ''
      this.loginMode = 'none'
      this.persist()
    },
    applyLoginSession(response: LoginResponse, partial: Partial<AuthState>) {
      this.userId = response.userId
      this.authToken = response.token
      this.tokenHeader = response.tokenHeader || 'X-Auth-Token'
      this.username = normalizeText(partial.username) || this.username || DEFAULT_USERNAME
      this.wechatNickname = normalizeText(partial.wechatNickname ?? this.wechatNickname)
      this.wechatAvatarUrl = normalizeText(partial.wechatAvatarUrl ?? this.wechatAvatarUrl)
      this.loginMode = partial.loginMode ?? this.loginMode
      this.persist()
    },
    toRequestContext() {
      return {
        loggedIn: this.loggedIn,
        authToken: this.authToken,
        tokenHeader: this.tokenHeader,
        clearSession: () => this.clearSession(),
      } satisfies RequestAuthContext
    },
    persist() {
      if (!this.authToken || !this.userId) {
        removeStorage(STORAGE_KEYS.auth)
        return
      }

      writeStorage(STORAGE_KEYS.auth, {
        userId: this.userId,
        username: this.username,
        authToken: this.authToken,
        tokenHeader: this.tokenHeader,
        wechatNickname: this.wechatNickname,
        wechatAvatarUrl: this.wechatAvatarUrl,
        loginMode: this.loginMode,
      } satisfies AuthState)
    },
  },
})

function loadInitialAuth(): AuthState {
  const stored = readStorage<Partial<AuthState>>(STORAGE_KEYS.auth, {})

  return {
    userId: normalizeUserId(stored.userId),
    username: normalizeText(stored.username) || DEFAULT_USERNAME,
    authToken: normalizeText(stored.authToken),
    tokenHeader: normalizeText(stored.tokenHeader) || 'X-Auth-Token',
    wechatNickname: normalizeText(stored.wechatNickname),
    wechatAvatarUrl: normalizeText(stored.wechatAvatarUrl),
    loginMode: stored.loginMode === 'wechat' ? 'wechat' : 'none',
  }
}

function normalizeText(value?: string) {
  return typeof value === 'string' ? value.trim() : ''
}

function normalizeUserId(userId?: string) {
  const normalized = normalizeText(userId)
  if (!normalized || GENERATED_USER_ID_RE.test(normalized)) {
    return ''
  }
  return normalized
}
