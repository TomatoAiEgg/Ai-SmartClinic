<script setup lang="ts">
import type { MessageAction, QuickAction } from '@/types/app'
import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import AppTabBar from '@/components/AppTabBar.vue'
import MessageBubble from '@/components/MessageBubble.vue'
import QuickActions from '@/components/QuickActions.vue'
import { useChatStore } from '@/stores/chat'
import { useConfigStore } from '@/stores/config'

const chatStore = useChatStore()
const configStore = useConfigStore()
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
    message: '查询挂号 REG-1234ABCD',
    metadata: { action: 'query', registrationId: 'REG-1234ABCD' },
  },
  {
    title: '导诊分流',
    description: '先问症状，再建议应该挂哪个科室。',
    message: '我咳嗽发热三天了，应该挂什么科室',
  },
  {
    title: '就诊准备',
    description: '地址、医保、到院时间这些问题直接问。',
    message: '就诊前需要带什么材料',
  },
]

const scrollTarget = computed(() => {
  const last = messages.value[messages.value.length - 1]
  return last ? `msg-${last.id}` : ''
})

async function sendDraft() {
  if (!draft.value.trim()) {
    return
  }
  const content = draft.value
  draft.value = ''
  await chatStore.sendMessage(content)
}

async function sendQuickAction(action: QuickAction) {
  await chatStore.sendMessage(action.message, action.metadata ?? {})
}

async function sendMessageAction(action: MessageAction) {
  await chatStore.sendMessage(action.message, action.metadata ?? {})
}

function updateDraft(event: { detail: { value: string } }) {
  draft.value = event.detail.value
}
</script>

<template>
  <view class="page-shell">
    <view class="hero glass-card">
      <text class="hero-tag">AI 挂号台</text>
      <text class="hero-title">先聊天，再完成挂号、查询和取消</text>
      <text class="hero-desc">
        当前网关：{{ configStore.baseUrl }}
      </text>
    </view>

    <view class="section">
      <text class="section-title">快捷入口</text>
      <text class="section-subtitle">直接发送结构化意图，避免一上来就手工输入。</text>
      <QuickActions :items="quickActions" @select="sendQuickAction" />
    </view>

    <view class="section">
      <text class="section-title">对话记录</text>
      <text class="section-subtitle">后端已接入当前 Java 网关，确认流和挂号结果会直接落到这里。</text>
      <scroll-view class="chat-panel glass-card" scroll-y :scroll-into-view="scrollTarget">
        <MessageBubble
          v-for="message in messages"
          :key="message.id"
          :message="message"
          @action="sendMessageAction"
        />
      </scroll-view>
    </view>

    <view class="composer glass-card">
      <textarea
        class="composer-input"
        auto-height
        :maxlength="280"
        placeholder="例如：帮我挂明天下午呼吸内科"
        :value="draft"
        @input="updateDraft"
      />
      <button class="send-btn" :disabled="sending" @tap="sendDraft">
        {{ sending ? '发送中' : '发送' }}
      </button>
    </view>

    <AppTabBar current="chat" />
  </view>
</template>

<style scoped lang="scss">
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

.section {
  margin-top: 28rpx;
}

.chat-panel {
  height: 760rpx;
  margin-top: 18rpx;
  padding: 28rpx;
}

.composer {
  position: fixed;
  right: 24rpx;
  bottom: 172rpx;
  left: 24rpx;
  display: flex;
  gap: 18rpx;
  align-items: flex-end;
  padding: 22rpx;
  z-index: 25;
}

.composer-input {
  flex: 1;
  min-height: 88rpx;
  max-height: 200rpx;
  padding: 18rpx 20rpx;
  border-radius: 22rpx;
  background: rgba(35, 123, 255, 0.06);
  font-size: 28rpx;
  line-height: 1.6;
}

.send-btn {
  width: 148rpx;
  margin: 0;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #237bff, #5ea6ff);
  color: #fff;
  font-size: 28rpx;
  line-height: 88rpx;
}

.send-btn[disabled] {
  opacity: 0.6;
}
</style>
