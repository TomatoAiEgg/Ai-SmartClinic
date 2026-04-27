<script setup lang="ts">
import { computed } from 'vue'
import AppTabBar from '@/components/AppTabBar.vue'
import { replacePage } from '@/router/navigation'
import { useAuthStore } from '@/stores/auth'
import { usePatientsStore } from '@/stores/patients'
import { ensureLogin } from '@/utils/auth'

const authStore = useAuthStore()
const patientsStore = usePatientsStore()

const displayName = computed(() => authStore.loggedIn ? authStore.displayName : '未登录')
const defaultPatient = computed(() => patientsStore.defaultPatient)
const patientCountLabel = computed(() => {
  const count = patientsStore.records.length
  return count ? `${count} 位` : '未同步'
})

async function login() {
  await ensureLogin()
}

async function logout() {
  try {
    await authStore.logout()
    patientsStore.clear()
    uni.showToast({
      title: '已退出登录',
      icon: 'success',
    })
  }
  catch (error) {
    patientsStore.clear()
    uni.showModal({
      title: '已清除本地登录',
      content: error instanceof Error ? `后端退出失败：${error.message}` : '后端退出失败。',
      showCancel: false,
    })
  }
}

function goPatients() {
  void replacePage('/pages/patients/index')
}

function goAppointments() {
  void replacePage('/pages/appointments/index')
}
</script>

<template>
  <view class="page-shell profile-page">
    <view class="identity-panel glass-card">
      <view class="avatar">
        <image v-if="authStore.wechatAvatarUrl" class="avatar-img" :src="authStore.wechatAvatarUrl" mode="aspectFill" />
        <text v-else class="avatar-text">{{ authStore.loggedIn ? '我' : '未' }}</text>
      </view>
      <view class="identity-copy">
        <text class="identity-name">{{ displayName }}</text>
        <text class="identity-sub">{{ authStore.loggedIn ? authStore.userId : '登录后同步就诊人、预约和挂号状态' }}</text>
      </view>
      <button v-if="!authStore.loggedIn" class="compact-btn" @tap="login">
        登录
      </button>
      <button v-else class="compact-btn" @tap="logout">
        退出
      </button>
    </view>

    <view class="panel glass-card">
      <view class="panel-head">
        <view class="panel-title-block">
          <text class="section-title">就诊人</text>
          <text class="section-subtitle">默认就诊人用于挂号前自动带入。</text>
        </view>
        <button class="ghost-btn small" @tap="goPatients">
          管理
        </button>
      </view>
      <view class="info-row">
        <text class="info-label">已绑定</text>
        <text class="info-value">{{ patientCountLabel }}</text>
      </view>
      <view class="info-row">
        <text class="info-label">默认就诊人</text>
        <text class="info-value">{{ defaultPatient?.name || '未设置' }}</text>
      </view>
    </view>

    <view class="menu-list glass-card">
      <view class="menu-item" @tap="goPatients">
        <view class="menu-icon">
          <text>人</text>
        </view>
        <view class="menu-copy">
          <text class="menu-title">就诊人管理</text>
          <text class="menu-desc">新增、查看和设置默认就诊人</text>
        </view>
        <text class="menu-arrow">›</text>
      </view>

      <view class="menu-item" @tap="goAppointments">
        <view class="menu-icon">
          <text>号</text>
        </view>
        <view class="menu-copy">
          <text class="menu-title">我的预约</text>
          <text class="menu-desc">查看挂号状态和预约记录</text>
        </view>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <AppTabBar current="profile" />
  </view>
</template>

<style scoped lang="scss">
.profile-page {
  padding-bottom: 260rpx;
}

.identity-panel,
.panel,
.menu-list {
  padding: 30rpx;
}

.identity-panel {
  display: flex;
  align-items: center;
  gap: 22rpx;
}

.avatar {
  flex: 0 0 96rpx;
  width: 96rpx;
  height: 96rpx;
  overflow: hidden;
  border-radius: 48rpx;
  background: rgba(35, 123, 255, 0.12);
}

.avatar-img {
  width: 96rpx;
  height: 96rpx;
}

.avatar-text {
  display: block;
  width: 96rpx;
  height: 96rpx;
  color: #237bff;
  font-size: 34rpx;
  font-weight: 800;
  line-height: 96rpx;
  text-align: center;
}

.identity-copy {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
}

.identity-name {
  color: #12253f;
  font-size: 34rpx;
  font-weight: 800;
}

.identity-sub {
  color: #6e839e;
  font-size: 22rpx;
  line-height: 1.5;
  word-break: break-all;
}

.panel {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-top: 24rpx;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20rpx;
}

.panel-title-block {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  min-width: 0;
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  min-height: 72rpx;
  padding: 0 20rpx;
  border-radius: 18rpx;
  background: rgba(35, 123, 255, 0.06);
}

.info-label {
  color: #6e839e;
  font-size: 24rpx;
}

.info-value {
  max-width: 390rpx;
  color: #12253f;
  font-size: 24rpx;
  font-weight: 700;
  text-align: right;
  word-break: break-all;
}

.compact-btn,
.ghost-btn {
  margin: 0;
  border-radius: 18rpx;
  background: rgba(35, 123, 255, 0.08);
  color: #237bff;
  font-size: 26rpx;
}

.compact-btn {
  flex: 0 0 128rpx;
  line-height: 72rpx;
}

.ghost-btn {
  line-height: 84rpx;
}

.ghost-btn.small {
  flex: 0 0 124rpx;
  line-height: 68rpx;
}

.menu-list {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  margin-top: 24rpx;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  min-height: 104rpx;
}

.menu-icon {
  flex: 0 0 64rpx;
  width: 64rpx;
  height: 64rpx;
  border-radius: 18rpx;
  background: rgba(35, 123, 255, 0.08);
  color: #237bff;
  font-size: 24rpx;
  font-weight: 800;
  line-height: 64rpx;
  text-align: center;
}

.menu-copy {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  min-width: 0;
}

.menu-title {
  color: #12253f;
  font-size: 28rpx;
  font-weight: 700;
}

.menu-desc {
  color: #6e839e;
  font-size: 22rpx;
  line-height: 1.5;
}

.menu-arrow {
  color: #8aa0b8;
  font-size: 38rpx;
}
</style>
