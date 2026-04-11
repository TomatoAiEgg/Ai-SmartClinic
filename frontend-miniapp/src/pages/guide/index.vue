<script setup lang="ts">
import type { ChatApiResponse } from '@/types/app'
import AppTabBar from '@/components/AppTabBar.vue'
import { useChatStore } from '@/stores/chat'

interface GuideTopic {
  title: string
  question: string
  description: string
}

const chatStore = useChatStore()

const topics: GuideTopic[] = [
  {
    title: '到院地址',
    question: '医院 address 和到院时间怎么安排',
    description: '查询院区地址、建议提前多久到院。',
  },
  {
    title: '医保材料',
    question: 'insurance 相关材料需要准备什么',
    description: '了解证件、医保卡、签到前检查项。',
  },
  {
    title: '退号规则',
    question: 'cancel and refund policy 是什么',
    description: '先看规则，再决定是否发起取消。',
  },
]

async function askTopic(topic: GuideTopic) {
  const response = await chatStore.sendMessage(topic.question)
  showResponse(response)
}

function showResponse(response?: ChatApiResponse) {
  if (!response) {
    return
  }
  uni.showModal({
    title: '就诊指引',
    content: response.message,
    showCancel: false,
  })
}
</script>

<template>
  <view class="page-shell">
    <view class="intro glass-card">
      <text class="section-title">就诊指引</text>
      <text class="section-subtitle">这页先承接低风险 FAQ。需要真实写操作时，还是回到聊天页走确认流。</text>
    </view>

    <view class="topic-list">
      <view v-for="topic in topics" :key="topic.title" class="topic-card glass-card" @tap="askTopic(topic)">
        <text class="topic-title">{{ topic.title }}</text>
        <text class="topic-desc">{{ topic.description }}</text>
      </view>
    </view>

    <AppTabBar current="guide" />
  </view>
</template>

<style scoped lang="scss">
.intro {
  padding: 30rpx;
}

.topic-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-top: 24rpx;
}

.topic-card {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding: 28rpx;
}

.topic-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #12253f;
}

.topic-desc {
  font-size: 24rpx;
  line-height: 1.6;
  color: #6b8099;
}
</style>
