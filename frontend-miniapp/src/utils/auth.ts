import {
  addRequestInterceptor,
  addResponseErrorInterceptor,
  type RequestOptions,
} from '@/services/request'
import { useAuthStore } from '@/stores/auth'

let loginPromise: Promise<boolean> | undefined
let retryPromptPromise: Promise<boolean> | undefined
let authInterceptorsInstalled = false

export async function ensureLogin(showLoading = true) {
  const authStore = useAuthStore()
  if (authStore.loggedIn) {
    return true
  }

  if (!loginPromise) {
    loginPromise = (
      showLoading
        ? performWechatLogin(true)
        : promptLoginRequired(new Error('未检测到登录状态，请先完成微信登录。'))
    ).finally(() => {
      loginPromise = undefined
    })
  }

  return loginPromise
}

export function installAuthInterceptors() {
  if (authInterceptorsInstalled) {
    return
  }

  addRequestInterceptor((options) => {
    if (options.auth === false) {
      return options
    }

    const authStore = useAuthStore()
    if (!authStore.loggedIn) {
      return options
    }

    return attachAuthContext(options)
  })

  addResponseErrorInterceptor((error, options) => {
    if (options.auth === false || !error.unauthorized) {
      return
    }

    useAuthStore().clearSession()
  })

  authInterceptorsInstalled = true
}

async function performWechatLogin(showLoading: boolean): Promise<boolean> {
  let loadingShown = false

  try {
    const authStore = useAuthStore()
    const profile = await requestOptionalWechatUserProfile()

    if (showLoading) {
      uni.showLoading({
        title: '微信登录中',
        mask: true,
      })
      loadingShown = true
    }

    const code = await requestWechatLoginCode()
    if (!code) {
      throw new Error('没有拿到微信登录 code。')
    }

    await authStore.loginWithWechatProfile({
      nickname: profile?.nickname || authStore.wechatNickname || authStore.username || '微信用户',
      avatarUrl: profile?.avatarUrl || authStore.wechatAvatarUrl || '',
      loginCode: code,
    })
    return true
  }
  catch (error) {
    return promptLoginRequired(error)
  }
  finally {
    if (loadingShown) {
      uni.hideLoading()
    }
  }
}

async function promptLoginRequired(error: unknown) {
  if (!retryPromptPromise) {
    retryPromptPromise = showLoginRequiredModal(error)
      .then((retry) => {
        if (!retry) {
          return false
        }

        return performWechatLogin(true)
      })
      .finally(() => {
        retryPromptPromise = undefined
      })
  }

  return retryPromptPromise
}

function showLoginRequiredModal(error: unknown) {
  const reason = error instanceof Error ? error.message : '微信登录失败。'

  return new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '需要微信登录',
      content: `${reason}\n点击“去登录”后，将获取微信登录凭证并向后端换取登录 token。`,
      confirmText: '去登录',
      cancelText: '暂不使用',
      success: result => resolve(result.confirm),
      fail: () => resolve(false),
    })
  })
}

function requestOptionalWechatUserProfile() {
  return new Promise<{ nickname: string, avatarUrl: string } | undefined>((resolve) => {
    if (!uni.getUserProfile) {
      resolve(undefined)
      return
    }

    uni.getUserProfile({
      desc: '用于同步微信昵称头像并完成小程序登录',
      success: (result) => {
        const userInfo = result.userInfo
        resolve({
          nickname: userInfo?.nickName || '微信用户',
          avatarUrl: userInfo?.avatarUrl || '',
        })
      },
      fail: () => resolve(undefined),
    })
  })
}

function requestWechatLoginCode() {
  return new Promise<string | undefined>((resolve) => {
    uni.login({
      provider: 'weixin',
      success: result => resolve(result.code),
      fail: () => resolve(undefined),
    })
  })
}

function attachAuthContext(options: RequestOptions) {
  const authContext = options.authContext ?? useAuthStore().toRequestContext()
  return {
    ...options,
    authContext,
  }
}
