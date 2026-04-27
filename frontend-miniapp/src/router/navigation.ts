import type { AppRouteName } from '@/types/app'
import { ensureRouteAccess } from '@/router/guard'
import { getRouteByName, normalizePath } from '@/router/routes'

type NavigationMode = 'navigateTo' | 'redirectTo'

const BOTTOM_NAV_STACK_LIMIT = 8

export async function openPage(path: string) {
  return navigate(path, 'navigateTo')
}

export async function replacePage(path: string) {
  return navigate(path, 'redirectTo')
}

export async function openBottomPage(path: string) {
  const url = normalizePath(path)
  if (!await ensureRouteAccess(url)) {
    return false
  }

  const pages = getCurrentPages() as Array<{ route?: string }>
  const currentPath = normalizeRuntimePath(pages.at(-1)?.route)
  if (currentPath === url) {
    return true
  }

  const mode: NavigationMode = pages.length >= BOTTOM_NAV_STACK_LIMIT ? 'redirectTo' : 'navigateTo'
  return runNavigation(mode, url)
}

export async function openRouteByName(name: AppRouteName) {
  const route = getRouteByName(name)
  if (!route) {
    return false
  }

  return route.bottomNav ? openBottomPage(route.path) : replacePage(route.path)
}

async function navigate(path: string, mode: NavigationMode) {
  const url = normalizePath(path)
  if (!await ensureRouteAccess(url)) {
    return false
  }

  return runNavigation(mode, url)
}

function runNavigation(mode: NavigationMode, url: string) {
  return new Promise<boolean>((resolve) => {
    const options = {
      url,
      success: () => resolve(true),
      fail: () => resolve(false),
    }

    if (mode === 'navigateTo') {
      uni.navigateTo(options)
      return
    }
    uni.redirectTo(options)
  })
}

function normalizeRuntimePath(path?: string) {
  return path ? normalizePath(path) : ''
}
