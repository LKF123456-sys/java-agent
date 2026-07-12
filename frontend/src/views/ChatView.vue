<template>
    <div class="chat-page">
      <div class="conversation-sidebar">
        <div class="sidebar-header">
          <button class="cyber-btn new-chat-btn" @click="handleNewConversation">
            <span class="btn-icon">+</span>
            <span>新建对话</span>
          </button>
        </div>
        
        <div class="conversation-list cyber-scrollbar">
          <div 
            v-for="conv in chatStore.conversations" 
            :key="conv.id"
            class="conversation-item"
            :class="{ active: chatStore.currentConversation?.id === conv.id }"
            @click="handleSelectConversation(conv.id)"
          >
            <span class="conv-icon">◆</span>
            <span class="conv-title">{{ conv.title || '新对话' }}</span>
            <button class="delete-btn" @click.stop="handleDeleteConversation(conv.id)">
              <span>×</span>
            </button>
          </div>
          
          <div v-if="chatStore.conversations.length === 0" class="empty-conversations">
            <p>暂无对话记录</p>
            <p class="hint">点击上方按钮新建对话</p>
          </div>
        </div>
      </div>

      <div class="chat-main">
        <div class="chat-header">
          <h2 class="chat-title cyber-glow-text">
            {{ chatStore.currentConversation?.title || '基础聊天' }}
          </h2>
          <div v-if="chatStore.isStreaming" class="streaming-indicator">
            <span class="typing-dot"></span>
            <span class="typing-dot"></span>
            <span class="typing-dot"></span>
            <span class="streaming-text">AI 正在回复...</span>
          </div>
        </div>

        <div class="messages-container cyber-scrollbar" ref="messagesRef">
          <div v-if="chatStore.messages.length === 0" class="empty-messages">
            <div class="empty-icon">💬</div>
            <p class="empty-title">开始对话</p>
            <p class="empty-desc">在下方输入消息，与AI进行智能对话</p>
          </div>
          
          <ChatMessage 
            v-for="message in chatStore.messages" 
            :key="message.id" 
            :message="message" 
          />
        </div>

        <ChatInput @send="handleSendMessage" />
      </div>
    </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch, onUnmounted } from 'vue'
import ChatMessage from '@/components/ChatMessage.vue'
import ChatInput from '@/components/ChatInput.vue'
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()
const messagesRef = ref(null)

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

const handleNewConversation = async () => {
  try {
    await chatStore.createConversation()
  } catch (error) {
    console.error('创建对话失败:', error)
  }
}

const handleSelectConversation = async (conversationId) => {
  try {
    await chatStore.selectConversation(conversationId)
    scrollToBottom()
  } catch (error) {
    console.error('加载对话失败:', error)
  }
}

const handleDeleteConversation = async (conversationId) => {
  try {
    await chatStore.deleteConversation(conversationId)
    if (chatStore.conversations.length > 0) {
      await handleSelectConversation(chatStore.conversations[0].id)
    }
  } catch (error) {
    console.error('删除对话失败:', error)
  }
}

const handleSendMessage = async (content) => {
  try {
    await chatStore.sendMessage(content)
    scrollToBottom()
  } catch (error) {
    console.error('发送消息失败:', error)
  }
}

watch(() => chatStore.messages.length, () => {
  scrollToBottom()
})

watch(() => chatStore.isStreaming, () => {
  scrollToBottom()
})

onMounted(async () => {
  try {
    await chatStore.fetchConversations()
    if (chatStore.conversations.length > 0) {
      await handleSelectConversation(chatStore.conversations[0].id)
    }
  } catch (error) {
    console.error('初始化失败:', error)
  }
})

onUnmounted(() => {
  chatStore.closeSSE()
})
</script>

<style scoped>
.chat-page {
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
  background: linear-gradient(180deg, var(--cyber-cyan), var(--cyber-magenta), var(--cyber-cyan));
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
  font-size: 8px;
  color: var(--cyber-text-muted);
  transition: all 0.3s ease;
  flex-shrink: 0;
}

.conversation-item.active .conv-icon,
.conversation-item:hover .conv-icon {
  color: var(--cyber-cyan);
  text-shadow: 0 0 10px var(--cyber-cyan);
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

.chat-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--cyber-cyan);
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
}
</style>
