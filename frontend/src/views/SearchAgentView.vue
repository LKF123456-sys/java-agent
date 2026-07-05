<template>
  <div class="search-page">
    <div class="search-layout">
      <div class="chat-area">
        <div class="chat-header">
          <div class="header-info">
            <h2 class="chat-title">🔍 赛博搜索官</h2>
            <span class="header-badge">联网实时搜索</span>
            <div v-if="streaming" class="streaming-indicator">
              <span class="search-pulse"></span>
              <span class="streaming-text">正在搜索互联网...</span>
            </div>
          </div>
        </div>

        <div class="messages-container cyber-scrollbar" ref="messagesRef">
          <div v-if="messages.length === 0" class="empty-messages">
            <div class="empty-icon">🌐</div>
            <p class="empty-title">赛博搜索官</p>
            <p class="empty-desc">我可以联网搜索互联网，为您获取最新、最准确的信息</p>
            <div class="capabilities">
              <span class="capability-tag">📰 实时新闻</span>
              <span class="capability-tag">🔬 技术资料</span>
              <span class="capability-tag">📈 数据查询</span>
              <span class="capability-tag">🌦️ 天气查询</span>
              <span class="capability-tag">🧮 数学计算</span>
              <span class="capability-tag">🔗 来源标注</span>
            </div>
            <div class="quick-questions">
              <p class="quick-title">试试这些问题：</p>
              <button v-for="q in quickQuestions" :key="q" class="quick-btn" @click="sendQuickQuestion(q)">
                {{ q }}
              </button>
            </div>
          </div>

          <div v-for="(message, index) in messages" :key="index" class="message-wrapper" :class="message.role">
            <div class="message-avatar">
              <span v-if="message.role === 'user'">👤</span>
              <span v-else>🔍</span>
            </div>
            <div class="message-bubble">
              <div v-if="message.role === 'assistant' && message.thinking" class="thinking-indicator">
                <span class="thinking-dot"></span>
                <span class="thinking-dot"></span>
                <span class="thinking-dot"></span>
                <span class="thinking-text">正在思考...</span>
              </div>
              <div v-else-if="message.role === 'assistant' && message.searching" class="searching-indicator">
                <span class="search-icon-spin">🔎</span>
                <span class="searching-text">正在联网搜索: {{ message.searchQuery }}</span>
              </div>
              <div v-else class="message-content" v-html="renderContent(message.content)"></div>
            </div>
          </div>
        </div>

        <ChatInput @send="sendMessage" :disabled="streaming" placeholder="输入您想搜索的问题，我会联网查找最新信息..." />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import ChatInput from '@/components/ChatInput.vue'
import request, { createSSEConnection } from '@/utils/request'

const quickQuestions = [
  '2025年最新的AI大模型有哪些？',
  '今天有什么重要新闻？',
  'Spring AI 1.0有什么新特性？',
  '现在比特币价格是多少？'
]

const messages = ref([])
const streaming = ref(false)
const messagesRef = ref(null)
const conversationId = ref(null)
let sseConnection = null

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

const closeSSE = () => {
  if (sseConnection) {
    sseConnection.close()
    sseConnection = null
  }
}

const renderContent = (content) => {
  if (!content) return ''
  let html = escapeHtml(content)
  html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\*(.*?)\*/g, '<em>$1</em>')
  html = html.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')
  html = html.replace(/(https?:\/\/[^\s<>\)]+)/g, '<a href="$1" target="_blank" class="source-link">$1</a>')
  html = html.replace(/\n/g, '<br>')
  return html
}

const escapeHtml = (text) => {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

const startSSE = (message) => {
  let url = `/api/search-agent/stream?task=${encodeURIComponent(message)}`
  if (conversationId.value) {
    url += `&conversationId=${conversationId.value}`
  }

  let hasError = false

  sseConnection = createSSEConnection(url, {
    onMessage: (data) => {
      if (!data) return

      if (data.startsWith('[ERROR]')) {
        hasError = true
        const lastMsg = messages.value[messages.value.length - 1]
        if (lastMsg && lastMsg.role === 'assistant') {
          lastMsg.thinking = false
          lastMsg.searching = false
          lastMsg.content = '❌ 搜索失败: ' + data.substring(7)
        }
        streaming.value = false
        closeSSE()
        scrollToBottom()
        return
      }

      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg && lastMsg.role === 'assistant') {
        lastMsg.thinking = false
        lastMsg.searching = false
        lastMsg.content += data
        scrollToBottom()
      }
    },
    onError: () => {
      if (!hasError) {
        const lastMsg = messages.value[messages.value.length - 1]
        if (lastMsg && lastMsg.role === 'assistant' && !lastMsg.content) {
          lastMsg.thinking = false
          lastMsg.searching = false
          lastMsg.content = '❌ 连接失败，请检查后端服务是否启动'
        }
      }
      streaming.value = false
      closeSSE()
    },
    onClose: () => {
      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg && lastMsg.role === 'assistant') {
        lastMsg.thinking = false
        lastMsg.searching = false
      }
      streaming.value = false
      scrollToBottom()
    }
  })
}

const sendMessage = (content) => {
  if (!content.trim() || streaming.value) return

  closeSSE()
  streaming.value = true

  messages.value.push({
    role: 'user',
    content: content.trim()
  })

  messages.value.push({
    role: 'assistant',
    content: '',
    thinking: true,
    searching: false
  })

  scrollToBottom()
  startSSE(content.trim())
}

const sendQuickQuestion = (q) => {
  sendMessage(q)
}

onMounted(() => {
})

onUnmounted(() => {
  closeSSE()
})
</script>

<style scoped>
.search-page {
  height: calc(100vh - 48px);
  margin: -24px;
}

.search-layout {
  display: flex;
  height: 100%;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--cyber-bg-primary);
}

.chat-header {
  padding: 20px 24px;
  border-bottom: 1px solid var(--cyber-border);
  background: var(--cyber-bg-secondary);
  display: flex;
  align-items: center;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chat-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--cyber-cyan);
  margin: 0;
  letter-spacing: 2px;
}

.header-badge {
  padding: 4px 10px;
  background: rgba(0, 255, 255, 0.15);
  border: 1px solid var(--cyber-cyan);
  border-radius: 4px;
  font-size: 11px;
  color: var(--cyber-cyan);
  letter-spacing: 1px;
}

.streaming-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 8px;
}

.search-pulse {
  width: 8px;
  height: 8px;
  background: var(--cyber-magenta);
  border-radius: 50%;
  animation: pulse 1s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.7); }
}

.streaming-text {
  font-size: 12px;
  color: var(--cyber-magenta);
  letter-spacing: 1px;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.empty-messages {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--cyber-text-muted);
  text-align: center;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 20px;
  opacity: 0.6;
  animation: float 3s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}

.empty-title {
  font-size: 22px;
  color: var(--cyber-cyan);
  margin-bottom: 8px;
  letter-spacing: 2px;
}

.empty-desc {
  font-size: 13px;
  letter-spacing: 1px;
  margin-bottom: 24px;
}

.capabilities {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
  margin-bottom: 32px;
}

.capability-tag {
  padding: 8px 14px;
  background: rgba(0, 255, 255, 0.08);
  border: 1px solid var(--cyber-cyan);
  border-radius: 4px;
  font-size: 12px;
  color: var(--cyber-cyan);
  clip-path: polygon(6px 0, 100% 0, 100% calc(100% - 6px), calc(100% - 6px) 100%, 0 100%, 0 6px);
}

.quick-questions {
  margin-top: 16px;
}

.quick-title {
  font-size: 12px;
  color: var(--cyber-text-muted);
  margin-bottom: 12px;
  letter-spacing: 1px;
}

.quick-btn {
  display: block;
  width: 100%;
  max-width: 480px;
  margin: 8px auto;
  padding: 12px 20px;
  background: rgba(255, 0, 255, 0.05);
  border: 1px solid var(--cyber-border);
  color: var(--cyber-text-secondary);
  cursor: pointer;
  font-size: 13px;
  text-align: left;
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
  transition: all 0.3s ease;
}

.quick-btn:hover {
  background: rgba(0, 255, 255, 0.1);
  border-color: var(--cyber-cyan);
  color: var(--cyber-cyan);
  transform: translateX(4px);
}

.message-wrapper {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.message-wrapper.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.message-wrapper.assistant .message-avatar {
  background: rgba(0, 255, 255, 0.15);
  border: 1px solid var(--cyber-cyan);
}

.message-wrapper.user .message-avatar {
  background: rgba(255, 0, 255, 0.15);
  border: 1px solid var(--cyber-magenta);
}

.message-bubble {
  max-width: 75%;
  padding: 14px 18px;
  font-size: 14px;
  line-height: 1.7;
}

.message-wrapper.assistant .message-bubble {
  background: var(--cyber-bg-secondary);
  border: 1px solid var(--cyber-border);
  color: var(--cyber-text-primary);
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.message-wrapper.user .message-bubble {
  background: rgba(0, 255, 255, 0.1);
  border: 1px solid rgba(0, 255, 255, 0.3);
  color: var(--cyber-cyan);
  clip-path: polygon(8px 0, 100% 0, 100% calc(100% - 8px), calc(100% - 8px) 100%, 0 100%, 0 8px);
}

.thinking-indicator, .searching-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--cyber-text-muted);
}

.thinking-dot {
  width: 6px;
  height: 6px;
  background: var(--cyber-cyan);
  border-radius: 50%;
  animation: bounce 1.4s ease-in-out infinite;
}

.thinking-dot:nth-child(2) { animation-delay: 0.2s; }
.thinking-dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.4; }
  40% { transform: translateY(-6px); opacity: 1; }
}

.thinking-text, .searching-text {
  font-size: 13px;
  letter-spacing: 1px;
}

.search-icon-spin {
  display: inline-block;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.searching-indicator {
  color: var(--cyber-magenta);
}

.message-content :deep(strong) {
  color: var(--cyber-cyan);
  font-weight: 600;
}

.message-content :deep(em) {
  color: var(--cyber-magenta);
  font-style: italic;
}

.message-content :deep(.inline-code) {
  background: rgba(0, 0, 0, 0.3);
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Consolas', monospace;
  font-size: 13px;
  color: var(--cyber-yellow);
}

.message-content :deep(.source-link) {
  color: var(--cyber-cyan);
  text-decoration: none;
  border-bottom: 1px dashed var(--cyber-cyan);
  word-break: break-all;
}

.message-content :deep(.source-link:hover) {
  color: var(--cyber-magenta);
  border-bottom-color: var(--cyber-magenta);
}
</style>
