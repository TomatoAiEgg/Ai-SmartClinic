<script setup lang="ts">
import type { AppRouteName } from '@/types/app'
import { openBottomPage } from '@/router/navigation'
import { APP_ROUTES } from '@/router/routes'

type TabKey = AppRouteName

const TAB_META: Record<TabKey, { label: string, icon: string }> = {
  chat: { label: '对话', icon: '问' },
  appointments: { label: '预约', icon: '号' },
  guide: { label: '指引', icon: '导' },
  profile: { label: '我的', icon: '我' },
  patients: { label: '就诊人', icon: '人' },
}

interface TabItem {
  key: AppRouteName
  label: string
  icon: string
  path: string
}

const props = defineProps<{
  current: TabKey
}>()

const tabs: TabItem[] = APP_ROUTES
  .filter(route => route.bottomNav)
  .map(route => ({
    key: route.name,
    label: TAB_META[route.name].label,
    icon: TAB_META[route.name].icon,
    path: route.path,
  }))

function navigate(path: string, key: TabKey) {
  if (props.current === key) {
    return
  }
  void openBottomPage(path)
}
</script>

<template>
  <view class="tabbar glass-card">
    <view
      v-for="item in tabs"
      :key="item.key"
      class="tab-item"
      :class="{ active: current === item.key }"
      @tap="navigate(item.path, item.key)"
    >
      <text class="tab-icon">{{ item.icon }}</text>
      <text class="tab-label">{{ item.label }}</text>
    </view>
  </view>
</template>

<style scoped lang="scss">
.tabbar {
  position: fixed;
  right: 24rpx;
  bottom: 24rpx;
  left: 24rpx;
  display: flex;
  justify-content: space-between;
  padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));
  z-index: 30;
}

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  color: #7184a0;
}

.tab-item.active {
  color: #237bff;
}

.tab-icon {
  width: 56rpx;
  height: 56rpx;
  line-height: 56rpx;
  border-radius: 18rpx;
  background: rgba(35, 123, 255, 0.08);
  text-align: center;
  font-size: 24rpx;
  font-weight: 700;
}

.tab-item.active .tab-icon {
  background: linear-gradient(135deg, #237bff, #60a7ff);
  color: #fff;
  box-shadow: 0 10rpx 24rpx rgba(35, 123, 255, 0.25);
}

.tab-label {
  font-size: 22rpx;
}
</style>
