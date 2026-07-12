<template>
  <div class="agent-page">
    <div class="agent-layout">
      <div class="agent-sidebar">
        <div class="sidebar-header">
          <h2 class="sidebar-title cyber-glow-text">🤖 智能体</h2>
        </div>
        
        <div class="agent-list cyber-scrollbar">
          <div 
            v-for="agent in agents" 
            :key="agent.id"
            class="agent-item"
            :class="{ active: selectedAgent === agent.id }"
            @click="selectAgent(agent.id)"
          >
            <div class="agent-icon">{{ agent.icon }}</div>
            <div class="agent-info">
              <h3 class="agent-name">{{ agent.name }}</h3>
              <p class="agent-desc">{{ agent.description }}</p>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-area">
        <div class="chat-header">
          <div class="header-info">
            <h2 class="chat-title" v-if="currentAgent">
              {{ currentAgent.icon }} {{ currentAgent.name }}
            </h2>
            <div v-if="streaming" class="streaming-indicator">
              <span class="typing-dot"></span>
              <span class="typing-dot"></span>
              <span class="typing-dot"></span>
              <span class="streaming-text">{{ currentAgent?.name || 'Agent' }} 正在工作...</span>
            </div>
          </div>
        </div>

        <div class="messages-container cyber-scrollbar" ref="messagesRef">
          <div v-if="messages.length === 0" class="empty-messages">
            <div class="empty-icon">{{ currentAgent?.icon || '🤖' }}</div>
            <p class="empty-title">{{ currentAgent?.name || '选择一个智能体' }}</p>
            <p class="empty-desc">{{ currentAgent?.description || '从左侧选择一个智能体开始对话' }}</p>
            <div v-if="currentAgent" class="capabilities">
              <span v-for="cap in currentAgent.capabilities" :key="cap" class="capability-tag">
                {{ cap }}
              </span>
            </div>
          </div>

          <ChatMessage 
            v-for="(message, index) in messages" 
            :key="index" 
            :message="message" 
          />
        </div>

        <ChatInput @send="sendMessage" :disabled="streaming || !selectedAgent" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, onUnmounted } from 'vue'
import ChatMessage from '@/components/ChatMessage.vue'
import ChatInput from '@/components/ChatInput.vue'
import request, { createSSEConnection } from '@/utils/request'

const agents = ref([
  {
    id: 'coder',
    name: '代码助手',
    icon: '💻',
    description: '专业的编程助手，帮助编写、调试和优化代码',
    capabilities: ['代码生成', 'Bug修复', '代码审查', '技术解答']
  },
  {
    id: 'writer',
    name: '写作助手',
    icon: '✍️',
    description: '帮助您进行文章写作、内容创作和文案润色',
    capabilities: ['文章写作', '文案创作', '内容润色', '风格调整']
  },
  {
    id: 'translator',
    name: '翻译专家',
    icon: '🌐',
    description: '多语言翻译助手，支持中英文互译及多语言翻译',
    capabilities: ['中英互译', '多语言支持', '专业术语', '语境理解']
  },
  {
    id: 'analyst',
    name: '数据分析师',
    icon: '📊',
    description: '数据分析和可视化专家，帮助解读数据和生成报告',
    capabilities: ['数据分析', '趋势预测', '报告生成', '洞察建议']
  }
])

const selectedAgent = ref(null)
const messages = ref([])
const streaming = ref(false)
const messagesRef = ref(null)
let sseConnection = null

const currentAgent = computed(() => {
  return agents.value.find(a => a.id === selectedAgent.value)
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

const selectAgent = (agentId) => {
  closeSSE()
  selectedAgent.value = agentId
  messages.value = []
}

const agentPrompts = {
  coder: '你是一位专业的编程助手，请帮助用户编写、调试和优化代码。请用中文回答。\n\n用户问题：',
  writer: '你是一位专业的写作助手，请帮助用户进行文章写作、内容创作和文案润色。请用中文回答。\n\n用户需求：',
  translator: '你是一位专业的翻译专家，请提供准确的多语言翻译服务，支持中英文互译。请用中文回答。\n\n翻译请求：',
  analyst: '你是一位专业的数据分析师，请帮助用户分析数据、解读趋势并提供洞察建议。请用中文回答。\n\n分析需求：'
}

const startSSE = (message) => {
  const agentId = selectedAgent.value
  const promptPrefix = agentPrompts[agentId] || ''
  const fullTask = promptPrefix + message
  let url = `/api/agent/stream?task=${encodeURIComponent(fullTask)}`

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
    }
  })
}

const sendMessage = (content) => {
  if (!content.trim() || streaming.value || !selectedAgent.value) return

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
  startSSE(content.trim())
}

onMounted(() => {
  if (agents.value.length > 0) {
    selectAgent(agents.value[0].id)
  }
})

onUnmounted(() => {
  closeSSE()
})
</script>

<style scoped>
.agent-page {
  height: calc(100vh - 48px);
  margin: -24px;
}

.agent-layout {
  display: flex;
  height: 100%;
}

.agent-sidebar {
  width: 280px;
  min-width: 280px;
  background: var(--cyber-bg-secondary);
  border-right: 1px solid var(--cyber-border);
  display: flex;
  flex-direction: column;
  position: relative;
}

.agent-sidebar::before {
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
  margin: 0;
  letter-spacing: 1px;
}

.agent-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.agent-item {
  display: flex;
  gap: 12px;
  padding: 16px;
  margin-bottom: 8px;
  color: var(--cyber-text-secondary);
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.3s ease;
  position: relative;
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.agent-item:hover {
  background: rgba(0, 255, 255, 0.05);
  color: var(--cyber-cyan);
}

.agent-item.active {
  background: rgba(0, 255, 255, 0.1);
  border-color: var(--cyber-cyan);
  color: var(--cyber-cyan);
  box-shadow: var(--cyber-shadow-cyan);
}

.agent-icon {
  font-size: 28px;
  flex-shrink: 0;
}

.agent-info {
  flex: 1;
  min-width: 0;
}

.agent-name {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 4px 0;
  letter-spacing: 0.5px;
}

.agent-desc {
  font-size: 11px;
  color: var(--cyber-text-muted);
  margin: 0;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
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
  text-align: center;
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

.capabilities {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
}

.capability-tag {
  padding: 8px 14px;
  background: rgba(255, 0, 255, 0.1);
  border: 1px solid var(--cyber-magenta);
  border-radius: 4px;
  font-size: 12px;
  color: var(--cyber-magenta);
  clip-path: polygon(6px 0, 100% 0, 100% calc(100% - 6px), calc(100% - 6px) 100%, 0 100%, 0 6px);
}
</style>
