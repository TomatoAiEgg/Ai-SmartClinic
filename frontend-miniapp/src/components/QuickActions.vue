<script setup lang="ts">
import type { QuickAction } from '@/types/app'

defineProps<{
  items: QuickAction[]
}>()

const emit = defineEmits<{
  select: [payload: QuickAction]
}>()
</script>

<template>
  <view class="actions-grid">
    <view
      v-for="item in items"
      :key="item.title"
      class="action-card glass-card"
      @tap="emit('select', item)"
    >
      <text class="action-title">{{ item.title }}</text>
      <text class="action-desc">{{ item.description }}</text>
    </view>
  </view>
</template>

<style scoped lang="scss">
.actions-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20rpx;
}

.action-card {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  min-height: 164rpx;
  padding: 24rpx;
}

.action-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #12253f;
}

.action-desc {
  font-size: 22rpx;
  line-height: 1.6;
  color: #6a7f99;
}
</style>
