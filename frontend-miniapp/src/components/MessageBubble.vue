<script setup lang="ts">
import type { ChatMessage, MessageAction } from '@/types/app'

defineProps<{
  message: ChatMessage
}>()

const emit = defineEmits<{
  action: [payload: MessageAction]
}>()

function routeLabel(route?: ChatMessage['route']) {
  switch (route) {
    case 'TRIAGE':
      return '分诊'
    case 'REGISTRATION':
      return '挂号'
    case 'GUIDE':
      return '指引'
    case 'HUMAN_REVIEW':
      return '人工复核'
    default:
      return ''
  }
}

function tapAction(action: MessageAction) {
  emit('action', action)
}
</script>

<template>
  <view :id="`msg-${message.id}`" class="bubble-row" :class="message.role">
    <view class="meta">
      <text v-if="message.route" class="route-tag">{{ routeLabel(message.route) }}</text>
      <text class="time">{{ message.createdAt }}</text>
    </view>
    <view class="bubble">
      <text class="content">{{ message.content }}</text>
    </view>
    <view v-if="message.actions?.length" class="actions">
      <button
        v-for="action in message.actions"
        :key="`${message.id}-${action.label}`"
        class="action-btn"
        :class="action.tone || 'default'"
        size="mini"
        @tap="tapAction(action)"
      >
        {{ action.label }}
      </button>
    </view>
  </view>
</template>

<style scoped lang="scss">
.bubble-row {
  display: flex;
  flex-direction: column;
  margin-bottom: 24rpx;
}

.bubble-row.user {
  align-items: flex-end;
}

.bubble-row.assistant,
.bubble-row.system {
  align-items: flex-start;
}

.meta {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 10rpx;
}

.route-tag {
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(35, 123, 255, 0.08);
  color: #237bff;
  font-size: 20rpx;
}

.time {
  font-size: 20rpx;
  color: #7c90aa;
}

.bubble {
  max-width: 88%;
  padding: 22rpx 24rpx;
  border-radius: 26rpx;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 12rpx 28rpx rgba(35, 58, 99, 0.08);
}

.bubble-row.user .bubble {
  background: linear-gradient(135deg, #237bff, #4f96ff);
}

.bubble-row.user .content {
  color: #fff;
}

.bubble-row.system .bubble {
  border: 1rpx solid rgba(219, 77, 99, 0.2);
  background: rgba(255, 246, 247, 0.94);
}

.content {
  font-size: 28rpx;
  line-height: 1.7;
  color: #12253f;
  white-space: pre-wrap;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
  margin-top: 14rpx;
}

.action-btn {
  margin: 0;
  padding: 0 24rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  line-height: 60rpx;
}

.action-btn.primary {
  background: #237bff;
  color: #fff;
}

.action-btn.default {
  background: rgba(35, 123, 255, 0.08);
  color: #237bff;
}

.action-btn.danger {
  background: rgba(219, 77, 99, 0.12);
  color: #db4d63;
}
</style>
