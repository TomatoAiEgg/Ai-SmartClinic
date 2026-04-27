<script setup lang="ts">
import type { ChatApiResponse } from '@/types/app'
import { onShow } from '@dcloudio/uni-app'
import AppTabBar from '@/components/AppTabBar.vue'
import { requireAuthForPage } from '@/router/guard'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { ensureLogin } from '@/utils/auth'

interface GuideTopic {
  title: string
  question: string
  description: string
}

const chatStore = useChatStore()
const authStore = useAuthStore()

const topics: GuideTopic[] = [
  {
    title: '到院地址',
    question: '医院地址和到院时间怎么安排',
    description: '查询院区地址、建议提前多久到院。',
  },
  {
    title: '医保材料',
    question: '医保相关材料需要准备什么',
    description: '了解证件、医保卡、签到前检查项。',
  },
  {
    title: '退号规则',
    question: '取消挂号和退费规则是什么',
    description: '先看规则，再决定是否发起取消。',
  },
]

onShow(() => {
  void requireAuthForPage('/pages/guide/index')
})

async function loginNow() {
  await ensureLogin()
}

async function askTopic(topic: GuideTopic) {
  if (!await ensureLogin()) {
    return
  }
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
  <view class="page-shell guide-page">
    <view class="intro glass-card">
      <text class="section-title">就诊指引</text>
      <text class="section-subtitle">这里先承接低风险 FAQ。需要真实写操作时，仍然回到聊天页走确认流程。</text>
    </view>

    <view v-if="!authStore.loggedIn" class="login-required glass-card">
      <text class="login-title">请先登录</text>
      <text class="login-desc">登录后才能使用就诊指引。</text>
      <button class="login-btn" @tap="loginNow">微信登录</button>
    </view>

    <view v-else class="topic-list">
      <view v-for="topic in topics" :key="topic.title" class="topic-card glass-card" @tap="askTopic(topic)">
        <text class="topic-title">{{ topic.title }}</text>
        <text class="topic-desc">{{ topic.description }}</text>
      </view>
    </view>

    <AppTabBar current="guide" />
  </view>
</template>

<style scoped lang="scss">
.guide-page {
  padding-bottom: 260rpx;
}

.intro {
  padding: 30rpx;
}

.topic-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-top: 24rpx;
}

.login-required {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
  align-items: flex-start;
  margin-top: 24rpx;
  padding: 30rpx;
}

.login-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #12253f;
}

.login-desc {
  font-size: 24rpx;
  line-height: 1.6;
  color: #6b8099;
}

.login-btn {
  margin: 0;
  padding: 0 28rpx;
  border-radius: 999rpx;
  background: #237bff;
  color: #fff;
  font-size: 24rpx;
  line-height: 72rpx;
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
