<template>
  <CyberLayout>
    <div class="memory-page">
      <div class="conversation-sidebar">
        <div class="sidebar-header">
          <button class="cyber-btn new-chat-btn memory-btn" @click="handleNewConversation">
            <span class="btn-icon">+</span>
            <span>新建对话</span>
          </button>
        </div>
        
        <div class="conversation-list cyber-scrollbar">
          <div 
            v-for="conv in conversations" 
            :key="conv.id"
            class="conversation-item memory-item"
            :class="{ active: currentConversation?.id === conv.id }"
            @click="handleSelectConversation(conv.id)"
          >
            <span class="conv-icon">◆</span>
            <span class="conv-title">{{ conv.title || '新对话' }}</span>
            <button class="delete-btn" @click.stop="handleDeleteConversation(conv.id)">
              <span>×</span>
            </button>
          </div>
          
          <div v-if="conversations.length === 0" class="empty-conversations">
            <p>暂无对话记录</p>
            <p class="hint">点击上方按钮新建对话</p>
          </div>
        </div>
      </div>

      <div class="chat-main">
        <div class="chat-header memory-header">
          <h2 class="chat-title cyber-glow-text-magenta">
            {{ currentConversation?.title || '记忆对话' }}
          </h2>
          <div v-if="isStreaming" class="streaming-indicator">
            <span class="typing-dot magenta-dot"></span>
            <span class="typing-dot magenta-dot"></span>
            <span class="typing-dot magenta-dot"></span>
            <span class="streaming-text">AI 正在思考...</span>
          </div>
        </div>

        <div class="messages-container cyber-scrollbar" ref="messagesRef">
          <div v-if="messages.length === 0" class="empty-messages">
            <div class="empty-icon">🧠</div>
            <p class="empty-title memory-empty-title">记忆对话</p>
            <p class="empty-desc">多轮对话，AI能记住上下文内容</p>
          </div>
          
          <div 
            v-for="message in messages" 
            :key="message.id"
            class="chat-message"
            :class="{ 'user-message': message.role === 'user', 'assistant-message': message.role === 'assistant' }"
          >
            <div class="message-wrapper">
              <div v-if="message.role === 'assistant'" class="avatar assistant-avatar-memory">
                <span class="avatar-icon">AI</span>
              </div>
              
              <div class="message-bubble memory-bubble-assistant">
                <div class="corner corner-tl magenta-corner"></div>
                <div class="corner corner-tr magenta-corner"></div>
                <div class="corner corner-bl magenta-corner"></div>
                <div class="corner corner-br magenta-corner"></div>
                <div class="message-content">{{ message.content }}</div>
              </div>
              
              <div v-if="message.role === 'user'" class="avatar user-avatar-memory">
                <span class="avatar-icon">U</span>
              </div>
            </div>
          </div>
        </div>

        <div class="chat-input-container memory-input-container">
          <div class="input-wrapper">
            <div class="input-frame memory-input-frame">
              <div class="input-corner input-corner-tl"></div>
              <div class="input-corner input-corner-tr"></div>
              <div class="input-corner input-corner-bl"></div>
              <div class="input-corner input-corner-br"></div>
              <textarea
                ref="inputRef"
                v-model="inputMessage"
                class="cyber-input textarea-input"
                placeholder="输入你的消息..."
                rows="1"
                @keydown="handleKeydown"
                @input="autoResize"
                :disabled="isStreaming"
              ></textarea>
            </div>
            <button 
              class="cyber-btn send-btn memory-send-btn" 
              @click="handleSendMessage"
              :disabled="isStreaming || !inputMessage.trim()"
            >
              <span v-if="!isStreaming" class="btn-text">发送</span>
              <span v-else class="btn-loading">
                <span class="typing-dot magenta-dot"></span>
                <span class="typing-dot magenta-dot"></span>
                <span class="typing-dot magenta-dot"></span>
              </span>
            </button>
          </div>
          <div class="input-hint">Shift + Enter 换行 | 支持上下文记忆</div>
        </div>
      </div>
    </div>
  </CyberLayout>
</template>

<script setup>
import { ref, onMounted, nextTick, watch, onUnmounted } from 'vue'
import CyberLayout from '@/components/CyberLayout.vue'
import { get, post, del, createSSEConnection } from '@/utils/request'

const conversations = ref([])
const currentConversation = ref(null)
const messages = ref([])
const isStreaming = ref(false)
const inputMessage = ref('')
const messagesRef = ref(null)
const inputRef = ref(null)
let sseConnection = null

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

const autoResize = () => {
  const textarea = inputRef.value
  if (textarea) {
    textarea.style.height = 'auto'
    textarea.style.height = Math.min(textarea.scrollHeight, 200) + 'px'
  }
}

const handleKeydown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSendMessage()
  }
}

const fetchConversations = async () => {
  try {
    const data = await get('/api/memory/conversations')
    conversations.value = data || []
    return data
  } catch (error) {
    console.error('获取记忆会话列表失败:', error)
    return []
  }
}

const handleNewConversation = async () => {
  try {
    const data = await post('/api/memory/conversations', { title: '新对话' })
    conversations.value.unshift(data)
    currentConversation.value = data
    messages.value = []
  } catch (error) {
    console.error('创建记忆对话失败:', error)
  }
}

const handleSelectConversation = async (conversationId) => {
  try {
    const conversation = conversations.value.find(c => c.id === conversationId)
    if (conversation) {
      currentConversation.value = conversation
    }
    const data = await get(`/api/memory/conversations/${conversationId}/messages`)
    messages.value = (data || []).map(m => ({
      id: m.id,
      role: m.role,
      content: m.content
    }))
    scrollToBottom()
  } catch (error) {
    console.error('加载记忆对话消息失败:', error)
  }
}

const handleDeleteConversation = async (conversationId) => {
  try {
    await del(`/api/memory/conversations/${conversationId}`)
    const index = conversations.value.findIndex(c => c.id === conversationId)
    if (index > -1) {
      conversations.value.splice(index, 1)
    }
    if (currentConversation.value?.id === conversationId) {
      currentConversation.value = null
      messages.value = []
      if (conversations.value.length > 0) {
        await handleSelectConversation(conversations.value[0].id)
      }
    }
  } catch (error) {
    console.error('删除记忆对话失败:', error)
  }
}

const closeSSE = () => {
  if (sseConnection) {
    sseConnection.close()
    sseConnection = null
  }
}

const streamMessage = async (content) => {
  closeSSE()
  isStreaming.value = true

  const userMessage = {
    id: Date.now().toString(),
    role: 'user',
    content
  }
  messages.value.push(userMessage)

  const assistantMessage = {
    id: (Date.now() + 1).toString(),
    role: 'assistant',
    content: ''
  }
  messages.value.push(assistantMessage)
  inputMessage.value = ''
  scrollToBottom()

  return new Promise((resolve, reject) => {
    const url = `/api/memory/stream?conversationId=${currentConversation.value?.id}&message=${encodeURIComponent(content)}`
    const message = messages.value.find(m => m.id === assistantMessage.id)
    let hasContent = false

    sseConnection = createSSEConnection(url, {
      onMessage: (data) => {
        if (data === '[DONE]') {
          closeSSE()
          isStreaming.value = false
          resolve()
          return
        }
        if (message) {
          message.content += data
          hasContent = true
          scrollToBottom()
        }
      },
      onError: (error) => {
        closeSSE()
        isStreaming.value = false
        if (message) {
          if (!hasContent) {
            message.content = '抱歉，发生了错误，请稍后重试。'
          }
          reject(error)
        } else {
          resolve()
        }
      }
    })
  })
}

onUnmounted(() => {
  closeSSE()
})

const handleSendMessage = async () => {
  const trimmedMessage = inputMessage.value.trim()
  if (!trimmedMessage || isStreaming.value) return

  if (!currentConversation.value) {
    await handleNewConversation()
  }

  await streamMessage(trimmedMessage)
  
  nextTick(() => {
    if (inputRef.value) {
      inputRef.value.style.height = 'auto'
    }
  })
}

watch(() => messages.length, () => {
  scrollToBottom()
})

onMounted(async () => {
  try {
    await fetchConversations()
    if (conversations.value.length > 0) {
      await handleSelectConversation(conversations.value[0].id)
    }
  } catch (error) {
    console.error('初始化失败:', error)
  }
})
</script>

<style scoped>
.memory-page {
  display: flex;
  height: calc(100vh - 48px);
  margin: -24px;
}

.conversation-sidebar {
  width: 280px;
  min-width: 280px;
  background: var(--cyber-bg-secondary);
  border-right: 1px solid var(--cyber-border);
  display: flex;
  flex-direction: column;
  position: relative;
}

.conversation-sidebar::before {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 1px;
  height: 100%;
  background: linear-gradient(180deg, var(--cyber-magenta), var(--cyber-purple), var(--cyber-magenta));
  opacity: 0.5;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid var(--cyber-border);
}

.new-chat-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px;
}

.memory-btn {
  background: linear-gradient(135deg, var(--cyber-magenta) 0%, var(--cyber-pink) 100%);
}

.memory-btn:hover {
  box-shadow: 0 0 30px rgba(255, 0, 255, 0.5);
}

.btn-icon {
  font-size: 18px;
  font-weight: 700;
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

.memory-item:hover {
  background: rgba(255, 0, 255, 0.05);
  color: var(--cyber-magenta);
}

.memory-item.active {
  background: rgba(255, 0, 255, 0.1);
  border-color: var(--cyber-magenta);
  color: var(--cyber-magenta);
  box-shadow: var(--cyber-shadow-magenta);
}

.conv-icon {
  font-size: 8px;
  color: var(--cyber-text-muted);
  transition: all 0.3s ease;
  flex-shrink: 0;
}

.memory-item.active .conv-icon,
.memory-item:hover .conv-icon {
  color: var(--cyber-magenta);
  text-shadow: 0 0 10px var(--cyber-magenta);
}

.conv-title {
  flex: 1;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  letter-spacing: 0.5px;
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
  color: var(--cyber-text-muted);
  opacity: 0.7;
}

.chat-main {
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

.memory-header .chat-title {
  color: var(--cyber-magenta);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 2px;
  margin: 0;
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

.magenta-dot {
  background: var(--cyber-magenta);
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
  margin-bottom: 8px;
  letter-spacing: 2px;
}

.memory-empty-title {
  color: var(--cyber-magenta);
}

.empty-desc {
  font-size: 13px;
  letter-spacing: 1px;
}

.chat-message {
  margin-bottom: 24px;
  display: flex;
}

.user-message {
  justify-content: flex-end;
}

.assistant-message {
  justify-content: flex-start;
}

.message-wrapper {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  max-width: 80%;
}

.user-message .message-wrapper {
  flex-direction: row-reverse;
}

.avatar {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  clip-path: polygon(8px 0, 100% 0, calc(100% - 8px) 100%, 0 100%);
}

.assistant-avatar-memory {
  background: linear-gradient(135deg, var(--cyber-magenta), var(--cyber-purple));
  box-shadow: var(--cyber-shadow-magenta);
}

.user-avatar-memory {
  background: linear-gradient(135deg, var(--cyber-cyan), var(--cyber-blue));
  box-shadow: var(--cyber-shadow-cyan);
}

.avatar-icon {
  font-size: 12px;
  font-weight: 700;
  color: white;
  letter-spacing: 1px;
}

.message-bubble {
  position: relative;
  padding: 16px 20px;
  min-width: 60px;
}

.memory-bubble-assistant {
  background: linear-gradient(135deg, rgba(255, 0, 255, 0.2), rgba(107, 0, 255, 0.1));
  border: 1px solid var(--cyber-magenta);
  box-shadow: var(--cyber-shadow-magenta);
  clip-path: polygon(0 0, calc(100% - 12px) 0, 100% 12px, 100% 100%, 12px 100%, 0 calc(100% - 12px));
}

.user-message .message-bubble {
  background: linear-gradient(135deg, var(--cyber-cyan), var(--cyber-blue));
  border: 1px solid var(--cyber-cyan);
  box-shadow: var(--cyber-shadow-cyan);
  clip-path: polygon(12px 0, 100% 0, 100% calc(100% - 12px), calc(100% - 12px) 100%, 0 100%, 0 12px);
}

.corner {
  position: absolute;
  width: 8px;
  height: 8px;
}

.magenta-corner {
  background: var(--cyber-magenta);
}

.user-message .corner {
  background: var(--cyber-cyan);
}

.corner-tl {
  top: 0;
  left: 0;
  clip-path: polygon(0 0, 100% 0, 0 100%);
}

.corner-tr {
  top: 0;
  right: 0;
  clip-path: polygon(0 0, 100% 0, 100% 100%);
}

.corner-bl {
  bottom: 0;
  left: 0;
  clip-path: polygon(0 0, 0 100%, 100% 100%);
}

.corner-br {
  bottom: 0;
  right: 0;
  clip-path: polygon(100% 0, 0 100%, 100% 100%);
}

.message-content {
  font-size: 14px;
  line-height: 1.8;
  color: var(--cyber-text-primary);
  white-space: pre-wrap;
  word-wrap: break-word;
  letter-spacing: 0.5px;
}

.user-message .message-content {
  color: white;
}

.memory-bubble-assistant::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--cyber-magenta), transparent);
  opacity: 0.5;
}

.user-message .message-bubble::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--cyber-cyan), transparent);
  opacity: 0.5;
}

.chat-input-container {
  padding: 16px 24px;
  background: var(--cyber-bg-secondary);
  border-top: 1px solid var(--cyber-border);
  position: relative;
}

.memory-input-container::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--cyber-magenta), var(--cyber-purple), var(--cyber-magenta), transparent);
}

.input-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.input-frame {
  flex: 1;
  position: relative;
}

.memory-input-frame .input-corner {
  position: absolute;
  width: 10px;
  height: 10px;
  z-index: 1;
  pointer-events: none;
}

.memory-input-frame .input-corner-tl {
  top: -1px;
  left: -1px;
  border-top: 2px solid var(--cyber-magenta);
  border-left: 2px solid var(--cyber-magenta);
  box-shadow: -3px -3px 10px rgba(255, 0, 255, 0.3);
}

.memory-input-frame .input-corner-tr {
  top: -1px;
  right: -1px;
  border-top: 2px solid var(--cyber-magenta);
  border-right: 2px solid var(--cyber-magenta);
  box-shadow: 3px -3px 10px rgba(255, 0, 255, 0.3);
}

.memory-input-frame .input-corner-bl {
  bottom: -1px;
  left: -1px;
  border-bottom: 2px solid var(--cyber-purple);
  border-left: 2px solid var(--cyber-purple);
  box-shadow: -3px 3px 10px rgba(107, 0, 255, 0.3);
}

.memory-input-frame .input-corner-br {
  bottom: -1px;
  right: -1px;
  border-bottom: 2px solid var(--cyber-purple);
  border-right: 2px solid var(--cyber-purple);
  box-shadow: 3px 3px 10px rgba(107, 0, 255, 0.3);
}

.textarea-input {
  resize: none;
  min-height: 52px;
  max-height: 200px;
  line-height: 1.6;
  font-family: var(--cyber-font-family);
  padding-right: 40px;
}

.memory-input-frame .corner {
  position: absolute;
  width: 10px;
  height: 10px;
  z-index: 1;
  pointer-events: none;
}

.memory-input-frame .corner-tl {
  top: -1px;
  left: -1px;
}

.memory-input-frame .corner-tr {
  top: -1px;
  right: -1px;
}

.memory-input-frame .corner-bl {
  bottom: -1px;
  left: -1px;
}

.memory-input-frame .corner-br {
  bottom: -1px;
  right: -1px;
}

.send-btn {
  height: 52px;
  padding: 0 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 90px;
  flex-shrink: 0;
}

.memory-send-btn {
  background: linear-gradient(135deg, var(--cyber-magenta) 0%, var(--cyber-purple) 100%);
}

.memory-send-btn:hover {
  box-shadow: 0 0 30px rgba(255, 0, 255, 0.5);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

.send-btn:disabled:hover {
  box-shadow: none;
  transform: none;
}

.btn-text {
  letter-spacing: 2px;
}

.btn-loading {
  display: flex;
  align-items: center;
  gap: 4px;
}

.input-hint {
  margin-top: 10px;
  text-align: center;
  font-size: 11px;
  color: var(--cyber-text-muted);
  letter-spacing: 1px;
}
</style>
