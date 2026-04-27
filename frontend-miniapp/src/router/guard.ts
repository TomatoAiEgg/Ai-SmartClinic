import { getRouteByPath } from '@/router/routes'
import { useAuthStore } from '@/stores/auth'
import { ensureLogin } from '@/utils/auth'

export async function requireAuthForPage(path: string) {
  const route = getRouteByPath(path)
  if (!route?.requiresAuth) {
    return true
  }

  const authStore = useAuthStore()
  if (authStore.loggedIn) {
    return true
  }

  return ensureLogin(false)
}

export async function ensureRouteAccess(path: string) {
  return requireAuthForPage(path)
}

export async function runProtectedPageTask(
  path: string,
  task?: () => Promise<void> | void,
) {
  if (!await requireAuthForPage(path)) {
    return false
  }

  await task?.()
  return true
}
