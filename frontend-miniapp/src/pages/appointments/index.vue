<script setup lang="ts">
import type { ChatApiResponse } from '@/types/app'
import { computed } from 'vue'
import AppTabBar from '@/components/AppTabBar.vue'
import AppointmentCard from '@/components/AppointmentCard.vue'
import { useAppointmentsStore } from '@/stores/appointments'
import { useChatStore } from '@/stores/chat'

const appointmentsStore = useAppointmentsStore()
const chatStore = useChatStore()

const records = computed(() => appointmentsStore.items)

async function queryRegistration(registrationId: string) {
  const response = await chatStore.sendMessage(`查询挂号 ${registrationId}`, {
    action: 'query',
    registrationId,
  })
  showResponse(response)
}

async function cancelRegistration(registrationId: string) {
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
    action: 'cancel',
    registrationId,
    confirmed: 'true',
    reason: 'miniapp_cancel',
  })
  showResponse(finalResponse)
}

function goToChat() {
  uni.redirectTo({ url: '/pages/index' })
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
</script>

<template>
  <view class="page-shell">
    <view class="header glass-card">
      <text class="section-title">我的预约</text>
      <text class="section-subtitle">这里展示挂号、查询、取消后的结构化结果。卡片动作会继续复用聊天链路。</text>
    </view>

    <view v-if="records.length" class="list">
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
.header {
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
</style>
