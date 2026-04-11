<script setup lang="ts">
import type { AppointmentRecord } from '@/types/app'

defineProps<{
  record: AppointmentRecord
}>()

const emit = defineEmits<{
  query: [registrationId: string]
  cancel: [registrationId: string]
}>()

function statusLabel(status: string) {
  if (status === 'CONFIRMED') {
    return '已确认'
  }
  if (status === 'CANCELLED') {
    return '已取消'
  }
  if (status === 'RESCHEDULED') {
    return '已改约'
  }
  return status
}
</script>

<template>
  <view class="card glass-card">
    <view class="header">
      <view>
        <text class="title">{{ record.registrationId }}</text>
        <text class="subtitle">{{ record.message }}</text>
      </view>
      <text class="status" :class="record.status.toLowerCase()">{{ statusLabel(record.status) }}</text>
    </view>

    <view class="info-grid">
      <view class="info-item">
        <text class="label">科室</text>
        <text class="value">{{ record.departmentCode || '待确认' }}</text>
      </view>
      <view class="info-item">
        <text class="label">医生</text>
        <text class="value">{{ record.doctorId || '待分配' }}</text>
      </view>
      <view class="info-item">
        <text class="label">日期</text>
        <text class="value">{{ record.clinicDate || '--' }}</text>
      </view>
      <view class="info-item">
        <text class="label">时间</text>
        <text class="value">{{ record.startTime || '--' }}</text>
      </view>
    </view>

    <view class="footer">
      <text class="updated">更新于 {{ record.updatedAt }}</text>
      <view class="buttons">
        <button class="ghost-btn" size="mini" @tap="emit('query', record.registrationId)">查询状态</button>
        <button
          v-if="record.status !== 'CANCELLED'"
          class="danger-btn"
          size="mini"
          @tap="emit('cancel', record.registrationId)"
        >
          取消挂号
        </button>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.card {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx;
}

.header {
  display: flex;
  justify-content: space-between;
  gap: 20rpx;
}

.title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #12253f;
}

.subtitle {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  line-height: 1.5;
  color: #6d8098;
}

.status {
  height: fit-content;
  padding: 10rpx 16rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  background: rgba(19, 165, 107, 0.12);
  color: #13a56b;
}

.status.cancelled {
  background: rgba(219, 77, 99, 0.12);
  color: #db4d63;
}

.status.rescheduled {
  background: rgba(241, 168, 71, 0.14);
  color: #c07c1d;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18rpx;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  padding: 18rpx;
  border-radius: 22rpx;
  background: rgba(35, 123, 255, 0.05);
}

.label {
  font-size: 20rpx;
  color: #7d91aa;
}

.value {
  font-size: 24rpx;
  color: #12253f;
}

.footer {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.updated {
  font-size: 20rpx;
  color: #7d91aa;
}

.buttons {
  display: flex;
  gap: 16rpx;
}

.ghost-btn,
.danger-btn {
  margin: 0;
  padding: 0 24rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  line-height: 60rpx;
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
