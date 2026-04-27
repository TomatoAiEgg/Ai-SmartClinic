import type { AppRoute, AppRouteName } from '@/types/app'

export const APP_ROUTES: AppRoute[] = [
  {
    name: 'chat',
    path: '/pages/index',
    title: 'AI挂号',
    requiresAuth: true,
    bottomNav: true,
  },
  {
    name: 'appointments',
    path: '/pages/appointments/index',
    title: '我的预约',
    requiresAuth: true,
    bottomNav: true,
  },
  {
    name: 'guide',
    path: '/pages/guide/index',
    title: '就诊指引',
    requiresAuth: true,
    bottomNav: true,
  },
  {
    name: 'profile',
    path: '/pages/profile/index',
    title: '个人中心',
    requiresAuth: false,
    bottomNav: true,
  },
  {
    name: 'patients',
    path: '/pages/patients/index',
    title: '就诊人管理',
    requiresAuth: true,
    bottomNav: false,
  },
]

export function getRouteByPath(path: string) {
  const normalized = normalizePath(path)
  return APP_ROUTES.find(route => route.path === normalized)
}

export function getRouteByName(name: AppRouteName) {
  return APP_ROUTES.find(route => route.name === name)
}

export function normalizePath(path: string) {
  const clean = path.split('?')[0]
  return clean.startsWith('/') ? clean : `/${clean}`
}
