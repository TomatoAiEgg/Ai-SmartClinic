<script setup lang="ts">
import type { ChatApiResponse } from '@/types/app'
import { onShow } from '@dcloudio/uni-app'
import { computed } from 'vue'
import AppTabBar from '@/components/AppTabBar.vue'
import AppointmentCard from '@/components/AppointmentCard.vue'
import { runProtectedPageTask } from '@/router/guard'
import { replacePage } from '@/router/navigation'
import { useAppointmentsStore } from '@/stores/appointments'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { ensureLogin } from '@/utils/auth'

const appointmentsStore = useAppointmentsStore()
const chatStore = useChatStore()
const authStore = useAuthStore()

const records = computed(() => appointmentsStore.items)
const loading = computed(() => appointmentsStore.loading)
const loadError = computed(() => appointmentsStore.error)

onShow(() => {
  void runProtectedPageTask('/pages/appointments/index', loadAppointments)
})

async function loginNow() {
  if (await ensureLogin()) {
    await loadAppointments()
  }
}

async function loadAppointments(showSuccess = false) {
  if (!authStore.loggedIn) {
    return
  }
  try {
    await appointmentsStore.load(authStore.toRequestContext())
    if (showSuccess) {
      uni.showToast({
        title: '已刷新',
        icon: 'success',
      })
    }
  }
  catch (error) {
    uni.showModal({
      title: '加载失败',
      content: error instanceof Error ? error.message : '预约记录加载失败。',
      showCancel: false,
    })
  }
}

async function queryRegistration(registrationId: string) {
  if (!await ensureLogin()) {
    return
  }
  try {
    const record = await appointmentsStore.refreshOne(registrationId, authStore.toRequestContext())
    uni.showModal({
      title: '预约状态',
      content: `${record.registrationId}：${record.status}`,
      showCancel: false,
    })
  }
  catch (error) {
    uni.showModal({
      title: '查询失败',
      content: error instanceof Error ? error.message : '预约状态查询失败。',
      showCancel: false,
    })
  }
}

async function cancelRegistration(registrationId: string) {
  if (!await ensureLogin()) {
    return
  }
  const preview = await chatStore.sendMessage(`取消挂号 ${registrationId}`, {
    action: 'cancel',
    registrationId,
  })
  if (!preview) {
    return
  }

  if (!preview.requiresConfirmation) {
    showResponse(preview)
    return
  }

  const confirmed = await confirmAction('确认取消', preview.message)
  if (!confirmed) {
    return
  }

  const finalResponse = await chatStore.sendMessage(`确认取消挂号 ${registrationId}`, {
    ...toStringMetadata(preview.data),
    action: 'cancel',
    registrationId,
    confirmed: 'true',
    reason: 'miniapp_cancel',
  })
  showResponse(finalResponse)
  if (finalResponse) {
    await loadAppointments()
  }
}

function goToChat() {
  void replacePage('/pages/index')
}

function showResponse(response?: ChatApiResponse) {
  if (!response) {
    return
  }
  uni.showModal({
    title: '处理结果',
    content: response.message,
    showCancel: false,
  })
}

function confirmAction(title: string, content: string) {
  return new Promise<boolean>((resolve) => {
    uni.showModal({
      title,
      content,
      success: result => resolve(result.confirm),
      fail: () => resolve(false),
    })
  })
}

function toStringMetadata(data?: Record<string, unknown>) {
  return Object.entries(data || {}).reduce<Record<string, string>>((result, [key, value]) => {
    if (typeof value === 'string' && value.trim()) {
      result[key] = value.trim()
    }
    else if (typeof value === 'number' || typeof value === 'boolean') {
      result[key] = String(value)
    }
    return result
  }, {})
}
</script>

<template>
  <view class="page-shell appointments-page">
    <view class="header glass-card">
      <text class="section-title">我的预约</text>
      <text class="section-subtitle">这里展示后端挂号记录。取消操作会继续走确认链路。</text>
      <button v-if="authStore.loggedIn" class="refresh-btn" :disabled="loading" @tap="loadAppointments(true)">
        {{ loading ? '刷新中' : '刷新' }}
      </button>
    </view>

    <view v-if="!authStore.loggedIn" class="empty glass-card">
      <text class="empty-title">请先登录</text>
      <text class="empty-desc">登录后才能查看和操作你的预约记录。</text>
      <button class="empty-btn" @tap="loginNow">微信登录</button>
    </view>

    <view v-else-if="loading && !records.length" class="empty glass-card">
      <text class="empty-title">正在加载预约记录</text>
      <text class="empty-desc">正在从后端同步当前账号的挂号记录。</text>
    </view>

    <view v-else-if="loadError && !records.length" class="empty glass-card">
      <text class="empty-title">加载失败</text>
      <text class="empty-desc">{{ loadError }}</text>
      <button class="empty-btn" @tap="loadAppointments(true)">重新加载</button>
    </view>

    <view v-else-if="records.length" class="list">
      <AppointmentCard
        v-for="record in records"
        :key="record.registrationId"
        :record="record"
        @query="queryRegistration"
        @cancel="cancelRegistration"
      />
    </view>

    <view v-else class="empty glass-card">
      <text class="empty-title">还没有预约记录</text>
      <text class="empty-desc">先去聊天页发起挂号，结果会自动同步到这里。</text>
      <button class="empty-btn" @tap="goToChat">去聊天页</button>
    </view>

    <AppTabBar current="appointments" />
  </view>
</template>

<style scoped lang="scss">
.appointments-page {
  padding-bottom: 260rpx;
}

.header {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 16rpx;
  padding: 30rpx;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-top: 24rpx;
}

.empty {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
  align-items: flex-start;
  margin-top: 28rpx;
  padding: 32rpx;
}

.empty-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #12253f;
}

.empty-desc {
  font-size: 24rpx;
  line-height: 1.6;
  color: #6e839e;
}

.empty-btn {
  margin: 0;
  padding: 0 28rpx;
  border-radius: 999rpx;
  background: #237bff;
  color: #fff;
  font-size: 24rpx;
  line-height: 72rpx;
}

.refresh-btn {
  margin: 0;
  padding: 0 28rpx;
  border-radius: 999rpx;
  background: rgba(35, 123, 255, 0.08);
  color: #237bff;
  font-size: 24rpx;
  line-height: 72rpx;
}

.refresh-btn[disabled] {
  opacity: 0.55;
}
</style>
