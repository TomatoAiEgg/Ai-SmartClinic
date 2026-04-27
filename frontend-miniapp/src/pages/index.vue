<script setup lang="ts">
import type { MessageAction, QuickAction } from '@/types/app'
import { onShow } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import { computed, ref } from 'vue'
import AppTabBar from '@/components/AppTabBar.vue'
import MessageBubble from '@/components/MessageBubble.vue'
import QuickActions from '@/components/QuickActions.vue'
import { requireAuthForPage } from '@/router/guard'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { ensureLogin } from '@/utils/auth'

const chatStore = useChatStore()
const authStore = useAuthStore()
chatStore.ensureWelcome()

const { messages, sending } = storeToRefs(chatStore)

const draft = ref('')

const quickActions: QuickAction[] = [
  {
    title: '立即挂号',
    description: '按症状和时间快速生成挂号预览。',
    message: '帮我挂明天下午呼吸内科的号',
    metadata: { action: 'create' },
  },
  {
    title: '查询挂号',
    description: '输入挂号单号，快速查看当前状态。',
    message: '查询我的挂号结果',
    metadata: { action: 'query' },
  },
  {
    title: '导诊分流',
    description: '先说症状，再由系统建议应该挂哪个科室。',
    message: '我咳嗽发热三天了，应该挂什么科室',
  },
  {
    title: '就诊准备',
    description: '地址、医保、到院时间这些问题可以直接问。',
    message: '就诊前需要带什么材料',
  },
]

const scrollTarget = computed(() => {
  const last = messages.value.at(-1)
  return last ? `msg-${last.id}` : ''
})

onShow(() => {
  void requireAuthForPage('/pages/index')
})

async function loginNow() {
  await ensureLogin()
}

async function sendDraft() {
  if (!await ensureLogin()) {
    return
  }
  if (!draft.value.trim()) {
    return
  }
  const content = draft.value
  draft.value = ''
  await chatStore.sendMessage(content)
}

async function sendQuickAction(action: QuickAction) {
  if (!await ensureLogin()) {
    return
  }
  await chatStore.sendMessage(action.message, action.metadata ?? {})
}

async function sendMessageAction(action: MessageAction) {
  if (!await ensureLogin()) {
    return
  }
  await chatStore.sendMessage(action.message, action.metadata ?? {})
}

function updateDraft(event: { detail: { value: string } }) {
  draft.value = event.detail.value
}
</script>

<template>
  <view class="page-shell chat-page">
    <view class="hero glass-card">
      <text class="hero-tag">AI 挂号台</text>
      <text class="hero-title">先说清症状和时间，再确认挂号</text>
      <text class="hero-desc">
        面向真实号源、就诊人和预约记录的智能挂号助手。
      </text>
    </view>

    <view v-if="!authStore.loggedIn" class="login-tip glass-card">
      <view class="login-copy">
        <text class="login-title">请先登录</text>
        <text class="login-desc">需要完成微信授权登录后才能使用智能挂号。拒绝授权时，聊天、预约和指引功能会保持不可用。</text>
      </view>
      <button class="login-btn" @tap="loginNow">
        微信登录
      </button>
    </view>

    <view v-if="authStore.loggedIn" class="section">
      <text class="section-title">快捷入口</text>
      <text class="section-subtitle">直接发送结构化意图，避免一开始手工输入太多信息。</text>
      <QuickActions :items="quickActions" @select="sendQuickAction" />
    </view>

    <view v-if="authStore.loggedIn" class="section chat-section">
      <text class="section-title">对话记录</text>
      <text class="section-subtitle">挂号预览、确认动作和服务端返回结果都会落到这里。</text>
      <scroll-view
        class="chat-panel glass-card"
        scroll-y
        scroll-with-animation
        :scroll-into-view="scrollTarget"
      >
        <MessageBubble
          v-for="message in messages"
          :key="message.id"
          :message="message"
          @action="sendMessageAction"
        />
        <view class="chat-bottom-spacer" />
      </scroll-view>
    </view>

    <view v-if="authStore.loggedIn" class="composer glass-card">
      <textarea
        class="composer-input"
        auto-height
        :maxlength="280"
        placeholder="例如：帮我挂明天下午呼吸内科"
        :value="draft"
        confirm-type="send"
        @input="updateDraft"
        @confirm="sendDraft"
      />
      <button class="send-btn" :disabled="sending || !draft.trim()" @tap="sendDraft">
        {{ sending ? '发送中' : '发送' }}
      </button>
    </view>

    <AppTabBar current="chat" />
  </view>
</template>

<style scoped lang="scss">
.chat-page {
  padding-bottom: 280rpx;
}

.hero {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  padding: 32rpx;
}

.hero-tag {
  width: fit-content;
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(35, 123, 255, 0.1);
  color: #237bff;
  font-size: 22rpx;
  font-weight: 700;
}

.hero-title {
  font-size: 42rpx;
  line-height: 1.35;
  font-weight: 700;
  color: #12253f;
}

.hero-desc {
  font-size: 22rpx;
  line-height: 1.6;
  color: #6e839e;
}

.login-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  margin-top: 24rpx;
  padding: 26rpx;
}

.login-copy {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.login-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #12253f;
}

.login-desc {
  font-size: 22rpx;
  line-height: 1.5;
  color: #6e839e;
}

.login-btn {
  flex-shrink: 0;
  width: 168rpx;
  margin: 0;
  border-radius: 22rpx;
  background: #237bff;
  color: #fff;
  font-size: 24rpx;
  line-height: 72rpx;
}

.section {
  margin-top: 28rpx;
}

.chat-panel {
  height: 730rpx;
  margin-top: 18rpx;
  padding: 28rpx 28rpx 0;
}

.chat-bottom-spacer {
  height: 32rpx;
}

.composer {
  position: fixed;
  right: 24rpx;
  bottom: calc(132rpx + env(safe-area-inset-bottom));
  left: 24rpx;
  display: flex;
  gap: 18rpx;
  align-items: flex-end;
  padding: 20rpx;
  z-index: 25;
}

.composer-input {
  flex: 1;
  min-height: 84rpx;
  max-height: 190rpx;
  padding: 18rpx 20rpx;
  border-radius: 22rpx;
  background: rgba(35, 123, 255, 0.06);
  font-size: 28rpx;
  line-height: 1.6;
}

.send-btn {
  width: 144rpx;
  margin: 0;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #237bff, #5ea6ff);
  color: #fff;
  font-size: 28rpx;
  line-height: 84rpx;
}

.send-btn[disabled] {
  opacity: 0.55;
}
</style>
