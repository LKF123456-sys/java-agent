<template>
  <div class="memory-page">
    <div class="memory-layout">
      <div class="memory-sidebar">
        <div class="sidebar-header">
          <h2 class="sidebar-title cyber-glow-text">🧠 记忆对话</h2>
          <button class="cyber-btn new-chat-btn" @click="handleNewChat">
            <span>+ 新建记忆对话</span>
          </button>
        </div>
        
        <div class="conversation-list cyber-scrollbar">
          <div 
            v-for="conv in conversations" 
            :key="conv.id"
            class="conversation-item"
            :class="{ active: currentConversationId === conv.id }"
            @click="loadConversation(conv.id)"
          >
            <span class="conv-icon">💭</span>
            <span class="conv-title">{{ conv.title || '记忆对话' }}</span>
            <button class="delete-btn" @click.stop="deleteConversation(conv.id)">
              <span>×</span>
            </button>
          </div>
          
          <div v-if="conversations.length === 0" class="empty-conversations">
            <p>暂无记忆对话</p>
            <p class="hint">AI会记住对话内容</p>
          </div>
        </div>
      </div>

      <div class="chat-area">
        <div class="chat-header">
          <div class="header-info">
            <h2 class="chat-title">
              {{ currentConversationTitle || '记忆对话' }}
            </h2>
            <div class="memory-badge">
              <span class="badge-icon">💾</span>
              <span>长期记忆已启用</span>
            </div>
          </div>
          <div v-if="streaming" class="streaming-indicator">
            <span class="typing-dot"></span>
            <span class="typing-dot"></span>
            <span class="typing-dot"></span>
            <span class="streaming-text">AI 正在思考...</span>
          </div>
        </div>

        <div class="messages-container cyber-scrollbar" ref="messagesRef">
          <div v-if="messages.length === 0" class="empty-messages">
            <div class="empty-icon">🧠</div>
            <p class="empty-title">记忆增强对话</p>
            <p class="empty-desc">AI会记住您的对话历史，提供更连贯的交流体验</p>
            <div class="feature-tags">
              <span class="feature-tag">📝 上下文记忆</span>
              <span class="feature-tag">🔄 连贯对话</span>
              <span class="feature-tag">💡 个性化回复</span>
            </div>
          </div>

          <ChatMessage 
            v-for="(message, index) in messages" 
            :key="index" 
            :message="message" 
          />
        </div>

        <ChatInput @send="sendMessage" :disabled="streaming" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, computed, onUnmounted } from 'vue'
import ChatMessage from '@/components/ChatMessage.vue'
import ChatInput from '@/components/ChatInput.vue'
import request, { createSSEConnection } from '@/utils/request'

const messages = ref([])
const conversations = ref([])
const currentConversationId = ref(null)
const streaming = ref(false)
const messagesRef = ref(null)
let sseConnection = null

const currentConversationTitle = computed(() => {
  const conv = conversations.value.find(c => c.id === currentConversationId.value)
  return conv?.title || ''
})

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

const fetchConversations = async () => {
  try {
    const data = await request.get('/api/conversations', {
      params: { type: 'memory' }
    })
    conversations.value = data || []
  } catch (error) {
    console.error('获取会话列表失败:', error)
  }
}

const loadConversation = async (conversationId) => {
  closeSSE()
  currentConversationId.value = conversationId
  try {
    const data = await request.get(`/api/conversations/${conversationId}/messages`)
    messages.value = (data || []).map(m => ({
      role: m.role,
      content: m.content
    }))
    scrollToBottom()
  } catch (error) {
    console.error('加载消息失败:', error)
  }
}

const handleNewChat = () => {
  closeSSE()
  currentConversationId.value = null
  messages.value = []
}

const deleteConversation = async (conversationId) => {
  try {
    await request.delete(`/api/conversations/${conversationId}`)
    conversations.value = conversations.value.filter(c => c.id !== conversationId)
    if (currentConversationId.value === conversationId) {
      handleNewChat()
    }
  } catch (error) {
    console.error('删除会话失败:', error)
  }
}

const createAndStartSSE = async (message) => {
  try {
    const newConv = await request.post('/api/conversations', {
      title: message.substring(0, 20) + (message.length > 20 ? '...' : ''),
      type: 'memory'
    })
    currentConversationId.value = newConv.id
    conversations.value.unshift(newConv)
    startSSE(message, newConv.id)
  } catch (error) {
    console.error('创建会话失败:', error)
    streaming.value = false
    messages.value.push({
      role: 'assistant',
      content: '创建会话失败，请重试'
    })
  }
}

const startSSE = (message, conversationId) => {
  let url = `/api/memory/stream?message=${encodeURIComponent(message)}`
  if (conversationId) {
    url += `&conversationId=${conversationId}`
  }

  let hasError = false

  sseConnection = createSSEConnection(url, {
    onMessage: (data) => {
      if (!data) return
      
      if (data.startsWith('[ERROR]')) {
        hasError = true
        const lastMsg = messages.value[messages.value.length - 1]
        if (lastMsg && lastMsg.role === 'assistant') {
          lastMsg.content = '错误: ' + data.substring(7)
        }
        streaming.value = false
        closeSSE()
        return
      }

      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg && lastMsg.role === 'assistant') {
        lastMsg.content += data
        scrollToBottom()
      }
    },
    onError: () => {
      if (!hasError) {
        const lastMsg = messages.value[messages.value.length - 1]
        if (lastMsg && lastMsg.role === 'assistant' && !lastMsg.content) {
          lastMsg.content = '连接失败，请检查后端服务是否启动'
        }
      }
      streaming.value = false
      closeSSE()
    },
    onClose: () => {
      streaming.value = false
      fetchConversations()
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
    content: ''
  })

  scrollToBottom()

  if (currentConversationId.value) {
    startSSE(content.trim(), currentConversationId.value)
  } else {
    createAndStartSSE(content.trim())
  }
}

onMounted(async () => {
  await fetchConversations()
  if (conversations.value.length > 0) {
    await loadConversation(conversations.value[0].id)
  }
})

onUnmounted(() => {
  closeSSE()
})
</script>

<style scoped>
.memory-page {
  height: calc(100vh - 48px);
  margin: -24px;
}

.memory-layout {
  display: flex;
  height: 100%;
}

.memory-sidebar {
  width: 280px;
  min-width: 280px;
  background: var(--cyber-bg-secondary);
  border-right: 1px solid var(--cyber-border);
  display: flex;
  flex-direction: column;
  position: relative;
}

.memory-sidebar::before {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 1px;
  height: 100%;
  background: linear-gradient(180deg, var(--cyber-cyan), var(--cyber-magenta), var(--cyber-cyan));
  opacity: 0.5;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid var(--cyber-border);
}

.sidebar-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--cyber-cyan);
  margin: 0 0 16px 0;
  letter-spacing: 1px;
}

.new-chat-btn {
  width: 100%;
  justify-content: center;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  margin-bottom: 6px;
  color: var(--cyber-text-secondary);
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.3s ease;
  position: relative;
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.conversation-item:hover {
  background: rgba(0, 255, 255, 0.05);
  color: var(--cyber-cyan);
}

.conversation-item.active {
  background: rgba(0, 255, 255, 0.1);
  border-color: var(--cyber-cyan);
  color: var(--cyber-cyan);
  box-shadow: var(--cyber-shadow-cyan);
}

.conv-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.conv-title {
  flex: 1;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.delete-btn {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  color: var(--cyber-text-muted);
  font-size: 18px;
  cursor: pointer;
  opacity: 0;
  transition: all 0.3s ease;
  border-radius: 4px;
  flex-shrink: 0;
}

.conversation-item:hover .delete-btn {
  opacity: 1;
}

.delete-btn:hover {
  background: rgba(255, 0, 128, 0.2);
  color: var(--cyber-pink);
}

.empty-conversations {
  text-align: center;
  padding: 40px 20px;
  color: var(--cyber-text-muted);
}

.empty-conversations p {
  font-size: 13px;
  margin-bottom: 8px;
}

.empty-conversations .hint {
  font-size: 11px;
  opacity: 0.7;
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
  justify-content: space-between;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.chat-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--cyber-cyan);
  margin: 0;
  letter-spacing: 2px;
}

.memory-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: rgba(107, 0, 255, 0.2);
  border: 1px solid var(--cyber-purple);
  border-radius: 4px;
  font-size: 12px;
  color: var(--cyber-purple);
  clip-path: polygon(6px 0, 100% 0, 100% calc(100% - 6px), calc(100% - 6px) 100%, 0 100%, 0 6px);
}

.badge-icon {
  font-size: 14px;
}

.streaming-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
}

.streaming-text {
  font-size: 12px;
  color: var(--cyber-text-muted);
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
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 20px;
  opacity: 0.5;
}

.empty-title {
  font-size: 18px;
  color: var(--cyber-cyan);
  margin-bottom: 8px;
  letter-spacing: 2px;
}

.empty-desc {
  font-size: 13px;
  letter-spacing: 1px;
  margin-bottom: 24px;
}

.feature-tags {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
}

.feature-tag {
  padding: 8px 16px;
  background: rgba(0, 255, 255, 0.1);
  border: 1px solid var(--cyber-cyan);
  border-radius: 4px;
  font-size: 12px;
  color: var(--cyber-cyan);
  clip-path: polygon(6px 0, 100% 0, 100% calc(100% - 6px), calc(100% - 6px) 100%, 0 100%, 0 6px);
}
</style>
