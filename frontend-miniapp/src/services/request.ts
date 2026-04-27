type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'
type RequestData = string | ArrayBuffer | object

export interface RequestAuthContext {
  loggedIn: boolean
  authToken: string
  tokenHeader: string
  clearSession?: () => void
}

export interface RequestOptions {
  path: string
  method?: HttpMethod
  data?: RequestData
  auth?: boolean
  authContext?: RequestAuthContext
  header?: Record<string, string>
}

interface RequestRuntime {
  request: UniNamespace.RequestOptions
  authContext?: RequestAuthContext
}

interface RequestErrorOptions {
  statusCode?: number
  code?: number
  details?: Record<string, unknown>
}

type RequestInterceptor = (
  options: RequestOptions,
) => Promise<RequestOptions | void> | RequestOptions | void

type ResponseErrorInterceptor = (
  error: ApiRequestError,
  options: RequestOptions,
  authContext?: RequestAuthContext,
) => Promise<void> | void

const TRAILING_SLASH_RE = /\/+$/
const ABSOLUTE_URL_RE = /^https?:\/\//i
const API_BASE_URL = normalizeBaseUrl(__APP_API_BASE_URL__ || import.meta.env.VITE_API_BASE_URL)

const requestInterceptors: RequestInterceptor[] = []
const responseErrorInterceptors: ResponseErrorInterceptor[] = []

export class ApiRequestError extends Error {
  readonly statusCode?: number
  readonly code?: number
  readonly details?: Record<string, unknown>

  constructor(message: string, options: RequestErrorOptions = {}) {
    super(message)
    this.name = 'ApiRequestError'
    this.statusCode = options.statusCode
    this.code = options.code
    this.details = options.details
  }

  get unauthorized() {
    return this.statusCode === 401 || this.statusCode === 403
  }
}

export function addRequestInterceptor(interceptor: RequestInterceptor) {
  requestInterceptors.push(interceptor)
}

export function addResponseErrorInterceptor(interceptor: ResponseErrorInterceptor) {
  responseErrorInterceptors.push(interceptor)
}

export async function apiRequest<T = Record<string, unknown>>(options: RequestOptions) {
  const resolvedOptions = await runRequestInterceptors(options)
  const runtime = buildRequestRuntime(resolvedOptions)

  return new Promise<T>((resolve, reject) => {
    uni.request({
      ...runtime.request,
      success: (response) => {
        void (async () => {
          try {
            resolve(afterResponse<T>(response))
          }
          catch (error) {
            const requestError = normalizeRequestError(error)
            await runResponseErrorInterceptors(requestError, resolvedOptions, runtime.authContext)
            reject(requestError)
          }
        })()
      },
      fail: (error) => {
        void (async () => {
          const requestError = new ApiRequestError(error.errMsg || '网络请求失败。')
          await runResponseErrorInterceptors(requestError, resolvedOptions, runtime.authContext)
          reject(requestError)
        })()
      },
    })
  })
}

async function runRequestInterceptors(initialOptions: RequestOptions) {
  let currentOptions = { ...initialOptions }

  for (const interceptor of requestInterceptors) {
    const nextOptions = await interceptor(currentOptions)
    if (nextOptions) {
      currentOptions = nextOptions
    }
  }

  return currentOptions
}

async function runResponseErrorInterceptors(
  error: ApiRequestError,
  options: RequestOptions,
  authContext?: RequestAuthContext,
) {
  for (const interceptor of responseErrorInterceptors) {
    await interceptor(error, options, authContext)
  }
}

function buildRequestRuntime(options: RequestOptions): RequestRuntime {
  const requiresAuth = options.auth !== false
  const authContext = options.authContext

  if (requiresAuth && !authContext?.loggedIn) {
    throw new ApiRequestError('请先登录。', { statusCode: 401 })
  }

  return {
    authContext,
    request: {
      url: buildRequestUrl(options.path),
      method: options.method ?? 'GET',
      header: {
        'Content-Type': 'application/json',
        ...buildAuthHeaders(requiresAuth, authContext),
        ...options.header,
      },
      data: options.data as UniNamespace.RequestOptions['data'],
    },
  }
}

function afterResponse<T>(response: UniNamespace.RequestSuccessCallbackResult) {
  const body = normalizeResponseBody(response.data)
  if (response.statusCode >= 200 && response.statusCode < 300) {
    return body as T
  }

  throw new ApiRequestError(
    readString(body.message) ?? `请求失败，HTTP ${response.statusCode}`,
    {
      statusCode: response.statusCode,
      code: readNumber(body.code),
      details: readRecord(body.details),
    },
  )
}

function buildAuthHeaders(requiresAuthHeader: boolean, context?: RequestAuthContext) {
  if (!requiresAuthHeader || !context?.authToken) {
    return {}
  }

  return {
    [context.tokenHeader || 'X-Auth-Token']: context.authToken,
    Authorization: `Bearer ${context.authToken}`,
  }
}

function buildRequestUrl(path: string) {
  if (ABSOLUTE_URL_RE.test(path)) {
    return path
  }

  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  if (!API_BASE_URL) {
    throw new ApiRequestError('缺少后端网关地址，请检查前端环境变量配置。')
  }

  return `${API_BASE_URL}${normalizedPath}`
}

function normalizeBaseUrl(baseUrl: string | undefined) {
  return (baseUrl ?? '').trim().replace(TRAILING_SLASH_RE, '')
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

function normalizeRequestError(error: unknown) {
  if (error instanceof ApiRequestError) {
    return error
  }

  if (error instanceof Error) {
    return new ApiRequestError(error.message)
  }

  return new ApiRequestError('请求失败。')
}

function readRecord(value: unknown) {
  return value && typeof value === 'object' ? value as Record<string, unknown> : {}
}

function readString(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

function readNumber(value: unknown) {
  return typeof value === 'number' ? value : undefined
}
