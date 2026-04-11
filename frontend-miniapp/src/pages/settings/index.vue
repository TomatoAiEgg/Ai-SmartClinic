<script setup lang="ts">
import { ref } from 'vue'
import AppTabBar from '@/components/AppTabBar.vue'
import { useAppointmentsStore } from '@/stores/appointments'
import { useChatStore } from '@/stores/chat'
import { useConfigStore } from '@/stores/config'

const configStore = useConfigStore()
const chatStore = useChatStore()
const appointmentsStore = useAppointmentsStore()

const baseUrl = ref(configStore.baseUrl)
const userId = ref(configStore.userId)

function updateBaseUrl(event: { detail: { value: string } }) {
  baseUrl.value = event.detail.value
}

function updateUserId(event: { detail: { value: string } }) {
  userId.value = event.detail.value
}

function saveSettings() {
  configStore.save({
    baseUrl: baseUrl.value,
    userId: userId.value,
  })
  baseUrl.value = configStore.baseUrl
  userId.value = configStore.userId
  uni.showToast({
    title: '已保存',
    icon: 'success',
  })
}

function resetChat() {
  configStore.rotateChatId()
  chatStore.clearHistory()
  uni.showToast({
    title: '会话已重置',
    icon: 'success',
  })
}

function clearHistory() {
  chatStore.clearHistory()
  uni.showToast({
    title: '已清空对话',
    icon: 'success',
  })
}

function clearAppointments() {
  appointmentsStore.clear()
  uni.showToast({
    title: '已清空预约',
    icon: 'success',
  })
}
</script>

<template>
  <view class="page-shell">
    <view class="panel glass-card">
      <text class="section-title">连接设置</text>
      <text class="section-subtitle">默认指向本地 Java 网关。真机调试时，把地址改成你电脑在局域网里的 IP。</text>

      <view class="field">
        <text class="field-label">网关地址</text>
        <input class="field-input" :value="baseUrl" @input="updateBaseUrl" />
      </view>

      <view class="field">
        <text class="field-label">用户 ID</text>
        <input class="field-input" :value="userId" @input="updateUserId" />
      </view>

      <view class="field">
        <text class="field-label">当前会话 ID</text>
        <text class="readonly">{{ configStore.chatId }}</text>
      </view>

      <button class="primary-btn" @tap="saveSettings">保存设置</button>
    </view>

    <view class="panel glass-card">
      <text class="section-title">数据维护</text>
      <text class="section-subtitle">这里处理本地缓存，不会影响后端账本。</text>

      <button class="ghost-btn" @tap="resetChat">重置会话</button>
      <button class="ghost-btn" @tap="clearHistory">清空对话记录</button>
      <button class="danger-btn" @tap="clearAppointments">清空预约记录</button>
    </view>

    <AppTabBar current="settings" />
  </view>
</template>

<style scoped lang="scss">
.panel {
  display: flex;
  flex-direction: column;
  gap: 22rpx;
  padding: 30rpx;
}

.panel + .panel {
  margin-top: 24rpx;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.field-label {
  font-size: 24rpx;
  color: #6f839d;
}

.field-input,
.readonly {
  min-height: 88rpx;
  padding: 0 20rpx;
  border-radius: 22rpx;
  background: rgba(35, 123, 255, 0.06);
  font-size: 26rpx;
  line-height: 88rpx;
  color: #12253f;
}

.primary-btn,
.ghost-btn,
.danger-btn {
  margin: 0;
  border-radius: 22rpx;
  font-size: 26rpx;
  line-height: 84rpx;
}

.primary-btn {
  background: linear-gradient(135deg, #237bff, #60a7ff);
  color: #fff;
}

.ghost-btn {
  background: rgba(35, 123, 255, 0.08);
  color: #237bff;
}

.danger-btn {
  background: rgba(219, 77, 99, 0.12);
  color: #db4d63;
}
</style>
