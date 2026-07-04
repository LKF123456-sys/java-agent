<template>
  <CyberLayout>
    <div class="multi-agent-view">
      <div class="page-header">
        <div class="header-decoration left"></div>
        <h1 class="page-title purple-glow">MULTI AGENT 多Agent协作</h1>
        <div class="header-decoration right"></div>
      </div>

      <div class="intro-panel cyber-card purple-card">
        <div class="card-corner tl"></div>
        <div class="card-corner tr"></div>
        <div class="card-corner bl"></div>
        <div class="card-corner br"></div>
        
        <div class="intro-content">
          <p class="intro-text">多智能体协作系统，由四位专业Agent协同工作，共同完成复杂任务：</p>
          <div class="agents-grid">
            <div class="agent-badge planner">
              <span class="agent-icon">📋</span>
              <span class="agent-name">Planner</span>
              <span class="agent-role">规划师</span>
            </div>
            <div class="agent-badge researcher">
              <span class="agent-icon">🔍</span>
              <span class="agent-name">Researcher</span>
              <span class="agent-role">研究员</span>
            </div>
            <div class="agent-badge coder">
              <span class="agent-icon">💻</span>
              <span class="agent-name">Coder</span>
              <span class="agent-role">程序员</span>
            </div>
            <div class="agent-badge executor">
              <span class="agent-icon">⚡</span>
              <span class="agent-name">Executor</span>
              <span class="agent-role">执行者</span>
            </div>
          </div>
        </div>
      </div>

      <div class="input-panel cyber-card purple-card">
        <div class="card-corner tl"></div>
        <div class="card-corner tr"></div>
        <div class="card-corner bl"></div>
        <div class="card-corner br"></div>
        
        <div class="panel-header">
          <span class="panel-icon">◆</span>
          <h2 class="panel-title">任务输入</h2>
          <span class="panel-line"></span>
        </div>
        
        <div class="input-body">
          <textarea 
            v-model="taskGoal" 
            class="cyber-input purple-input textarea" 
            rows="4"
            placeholder="描述您想要完成的复杂任务，例如：开发一个在线购物网站的完整技术方案..."
            :disabled="collaborating"
          ></textarea>
          
          <button 
            class="cyber-btn purple-btn start-btn" 
            @click="handleCollaborate"
            :disabled="collaborating || !taskGoal.trim()"
          >
            <span v-if="collaborating" class="loading-spinner"></span>
            <span v-else class="btn-icon">▶</span>
            <span>{{ collaborating ? '协作中...' : '开始协作' }}</span>
          </button>

          <div v-if="errorMsg" class="error-msg">
            <span class="error-icon">✕</span>
            {{ errorMsg }}
          </div>
        </div>
      </div>

      <div v-if="messages.length > 0 || collaborating" class="chat-panel cyber-card purple-card">
        <div class="card-corner tl"></div>
        <div class="card-corner tr"></div>
        <div class="card-corner bl"></div>
        <div class="card-corner br"></div>
        
        <div class="panel-header">
          <span class="panel-icon">◆</span>
          <h2 class="panel-title">协作过程</h2>
          <span class="panel-line"></span>
          <button v-if="messages.length > 0" class="clear-btn" @click="handleClear">清空</button>
        </div>
        
        <div class="chat-body cyber-scrollbar" ref="chatBodyRef">
          <div v-for="(msg, index) in messages" :key="index" class="message-item" :class="getAgentClass(msg.agent)">
            <div class="message-avatar" :class="getAvatarClass(msg.agent)">
              {{ getAgentIcon(msg.agent) }}
            </div>
            <div class="message-content">
              <div class="message-header">
                <span class="agent-name-label">{{ getAgentName(msg.agent) }}</span>
                <span class="message-status" v-if="msg.done">✓ 完成</span>
                <span class="message-status active" v-else-if="msg.isActive">● 思考中</span>
              </div>
              <div class="message-text" v-html="formatMessage(msg.content)"></div>
            </div>
          </div>
          
          <div v-if="isCompleted" class="completed-banner">
            <span class="completed-icon">✓</span>
            <span>协作任务已完成</span>
          </div>
        </div>
      </div>

      <div v-if="finalResult" class="result-panel cyber-card purple-card">
        <div class="card-corner tl"></div>
        <div class="card-corner tr"></div>
        <div class="card-corner bl"></div>
        <div class="card-corner br"></div>
        
        <div class="panel-header">
          <span class="panel-icon">◆</span>
          <h2 class="panel-title final-title">最终结果</h2>
          <span class="panel-line"></span>
        </div>
        
        <div class="result-body cyber-scrollbar">
          <div class="result-content" v-html="formatMessage(finalResult)"></div>
        </div>
      </div>
    </div>
  </CyberLayout>
</template>

<script setup>
import { ref, nextTick, onUnmounted } from 'vue'
import CyberLayout from '@/components/CyberLayout.vue'
import { createSSEConnection } from '@/utils/request'

const taskGoal = ref('')
const collaborating = ref(false)
const errorMsg = ref('')
const messages = ref([])
const finalResult = ref('')
const isCompleted = ref(false)
let sseConnection = null
const chatBodyRef = ref(null)
let currentMessageIndex = -1

const agentConfig = {
  planner: { name: 'Planner 规划师', icon: '📋', color: 'var(--cyber-cyan)' },
  researcher: { name: 'Researcher 研究员', icon: '🔍', color: 'var(--cyber-blue)' },
  coder: { name: 'Coder 程序员', icon: '💻', color: 'var(--cyber-green)' },
  executor: { name: 'Executor 执行者', icon: '⚡', color: 'var(--cyber-magenta)' }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatBodyRef.value) {
      chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
    }
  })
}

const getAgentName = (agent) => {
  return agentConfig[agent]?.name || agent
}

const getAgentIcon = (agent) => {
  return agentConfig[agent]?.icon || '◆'
}

const getAgentClass = (agent) => {
  return `agent-${agent}`
}

const getAvatarClass = (agent) => {
  return `avatar-${agent}`
}

const formatMessage = (text) => {
  if (!text) return ''
  return text
    .replace(/\n/g, '<br>')
    .replace(/\*\*(.*?)\*\*/g, '<strong class="highlight">$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/^# (.*$)/gm, '<h3 class="msg-h3">$1</h3>')
    .replace(/^## (.*$)/gm, '<h4 class="msg-h4">$1</h4>')
    .replace(/^- (.*$)/gm, '<li class="msg-li">$1</li>')
}

const closeSSE = () => {
  if (sseConnection) {
    sseConnection.close()
    sseConnection = null
  }
}

const handleCollaborate = async () => {
  if (!taskGoal.value.trim() || collaborating.value) return
  
  closeSSE()
  
  collaborating.value = true
  errorMsg.value = ''
  messages.value = []
  finalResult.value = ''
  isCompleted.value = false
  currentMessageIndex = -1
  
  const url = `/api/multi-agent/stream?task=${encodeURIComponent(taskGoal.value.trim())}`

  sseConnection = createSSEConnection(url, {
    onMessage: (data) => {
      try {
        const parsed = JSON.parse(data)
        
        if (parsed.type === 'agent_start') {
          messages.value.forEach(m => m.isActive = false)
          messages.value.push({
            agent: parsed.agent,
            content: '',
            isActive: true,
            done: false,
            timestamp: Date.now()
          })
          currentMessageIndex = messages.value.length - 1
          scrollToBottom()
          return
        }
        
        if (parsed.type === 'token') {
          if (currentMessageIndex >= 0 && parsed.content) {
            messages.value[currentMessageIndex].content += parsed.content
            scrollToBottom()
          }
          return
        }
        
        if (parsed.type === 'agent_end') {
          if (currentMessageIndex >= 0) {
            messages.value[currentMessageIndex].done = true
            messages.value[currentMessageIndex].isActive = false
          }
          scrollToBottom()
          return
        }
        
        if (parsed.type === 'done') {
          if (parsed.result) {
            finalResult.value = parsed.result
          }
          if (parsed.content) {
            finalResult.value = parsed.content
          }
          isCompleted.value = true
          collaborating.value = false
          closeSSE()
          scrollToBottom()
          return
        }
      } catch (e) {
        console.error('解析SSE消息失败:', e, data)
      }
    },
    onError: (error) => {
      closeSSE()
      collaborating.value = false
      isCompleted.value = true
      if (messages.value.length === 0) {
        errorMsg.value = '协作失败，请重试'
      }
    }
  })
}

onUnmounted(() => {
  closeSSE()
})

const handleClear = () => {
  messages.value = []
  finalResult.value = ''
  isCompleted.value = false
  currentMessageIndex = -1
}
</script>

<style scoped>
.multi-agent-view {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  margin-bottom: 24px;
  padding: 20px 0;
}

.header-decoration {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--cyber-purple), transparent);
  position: relative;
}

.header-decoration::after {
  content: '';
  position: absolute;
  top: -4px;
  width: 9px;
  height: 9px;
  border: 1px solid var(--cyber-purple);
  transform: rotate(45deg);
  box-shadow: 0 0 10px var(--cyber-purple);
}

.header-decoration.left::after { right: 0; }
.header-decoration.right::after { left: 0; }

.page-title {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 4px;
  margin: 0;
  white-space: nowrap;
}

.purple-glow {
  color: var(--cyber-magenta);
  text-shadow: 0 0 10px var(--cyber-magenta), 0 0 20px var(--cyber-magenta), 0 0 40px var(--cyber-purple);
}

.intro-panel, .input-panel, .chat-panel, .result-panel {
  position: relative;
  padding: 0;
  margin-bottom: 24px;
  clip-path: polygon(0 0, calc(100% - 15px) 0, 100% 15px, 100% 100%, 15px 100%, 0 calc(100% - 15px));
}

.purple-card::before {
  background: linear-gradient(90deg, var(--cyber-magenta), var(--cyber-purple), var(--cyber-magenta));
}

.card-corner {
  position: absolute;
  width: 20px;
  height: 20px;
  border: 2px solid var(--cyber-magenta);
  z-index: 10;
}

.card-corner.tl { top: 10px; left: 10px; border-right: none; border-bottom: none; }
.card-corner.tr { top: 10px; right: 10px; border-left: none; border-bottom: none; }
.card-corner.bl { bottom: 10px; left: 10px; border-right: none; border-top: none; }
.card-corner.br { bottom: 10px; right: 10px; border-left: none; border-top: none; }

.intro-content {
  padding: 24px 28px;
}

.intro-text {
  font-size: 14px;
  color: var(--cyber-text-secondary);
  margin-bottom: 20px;
  line-height: 1.7;
}

.agents-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.agent-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 12px;
  border: 1px solid rgba(255, 0, 255, 0.2);
  background: rgba(255, 0, 255, 0.05);
  clip-path: polygon(0 0, calc(100% - 10px) 0, 100% 10px, 100% 100%, 10px 100%, 0 calc(100% - 10px));
  transition: all 0.3s ease;
}

.agent-badge:hover {
  transform: translateY(-2px);
}

.agent-badge.planner { border-color: var(--cyber-cyan); }
.agent-badge.researcher { border-color: var(--cyber-blue); }
.agent-badge.coder { border-color: var(--cyber-green); }
.agent-badge.executor { border-color: var(--cyber-magenta); }

.agent-icon {
  font-size: 28px;
}

.agent-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--cyber-text-primary);
  letter-spacing: 1px;
}

.agent-role {
  font-size: 11px;
  color: var(--cyber-text-muted);
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 24px;
  border-bottom: 1px solid rgba(255, 0, 255, 0.2);
  background: rgba(255, 0, 255, 0.05);
}

.panel-icon {
  color: var(--cyber-magenta);
  font-size: 10px;
  text-shadow: 0 0 10px var(--cyber-magenta);
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--cyber-magenta);
  letter-spacing: 2px;
  margin: 0;
}

.final-title {
  color: var(--cyber-green);
  text-shadow: 0 0 10px var(--cyber-green);
}

.panel-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, var(--cyber-magenta), transparent);
}

.clear-btn {
  padding: 6px 16px;
  background: transparent;
  border: 1px solid rgba(255, 0, 128, 0.3);
  color: var(--cyber-pink);
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  clip-path: polygon(0 0, calc(100% - 6px) 0, 100% 6px, 100% 100%, 6px 100%, 0 calc(100% - 6px));
}

.clear-btn:hover {
  background: rgba(255, 0, 128, 0.1);
  border-color: var(--cyber-pink);
}

.input-body {
  padding: 24px;
}

.purple-input:focus {
  border-color: var(--cyber-magenta);
  box-shadow: 0 0 20px rgba(255, 0, 255, 0.3);
}

.textarea {
  resize: none;
  font-family: inherit;
  line-height: 1.6;
  margin-bottom: 16px;
}

.start-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 14px;
}

.btn-icon { font-size: 16px; }

.error-msg {
  margin-top: 16px;
  padding: 12px 16px;
  background: rgba(255, 0, 128, 0.1);
  border: 1px solid rgba(255, 0, 128, 0.3);
  color: var(--cyber-pink);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 10px;
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.error-icon { font-weight: 700; }

.purple-btn {
  background: linear-gradient(135deg, var(--cyber-magenta) 0%, var(--cyber-purple) 100%);
}

.purple-btn:hover {
  box-shadow: 0 0 30px rgba(255, 0, 255, 0.5);
}

.chat-body {
  padding: 24px;
  max-height: 600px;
  overflow-y: auto;
}

.message-item {
  display: flex;
  gap: 14px;
  margin-bottom: 20px;
}

.message-avatar {
  width: 44px;
  height: 44px;
  min-width: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
}

.avatar-planner { background: linear-gradient(135deg, var(--cyber-cyan), var(--cyber-blue)); }
.avatar-researcher { background: linear-gradient(135deg, var(--cyber-blue), var(--cyber-purple)); }
.avatar-coder { background: linear-gradient(135deg, var(--cyber-green), var(--cyber-cyan)); }
.avatar-executor { background: linear-gradient(135deg, var(--cyber-magenta), var(--cyber-purple)); }

.message-content {
  flex: 1;
  background: rgba(255, 0, 255, 0.05);
  border: 1px solid rgba(255, 0, 255, 0.2);
  padding: 14px 18px;
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.agent-planner .message-content { border-color: rgba(0, 255, 255, 0.3); background: rgba(0, 255, 255, 0.05); }
.agent-researcher .message-content { border-color: rgba(0, 102, 255, 0.3); background: rgba(0, 102, 255, 0.05); }
.agent-coder .message-content { border-color: rgba(0, 255, 136, 0.3); background: rgba(0, 255, 136, 0.05); }
.agent-executor .message-content { border-color: rgba(255, 0, 255, 0.3); background: rgba(255, 0, 255, 0.05); }

.message-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.agent-name-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--cyber-magenta);
  letter-spacing: 1px;
}

.agent-planner .agent-name-label { color: var(--cyber-cyan); }
.agent-researcher .agent-name-label { color: var(--cyber-blue); }
.agent-coder .agent-name-label { color: var(--cyber-green); }
.agent-executor .agent-name-label { color: var(--cyber-magenta); }

.message-status {
  font-size: 11px;
  color: var(--cyber-green);
  padding: 2px 8px;
  background: rgba(0, 255, 136, 0.1);
  border: 1px solid rgba(0, 255, 136, 0.3);
}

.message-status.active {
  color: var(--cyber-magenta);
  background: rgba(255, 0, 255, 0.1);
  border-color: rgba(255, 0, 255, 0.3);
  animation: pulse-glow 1.5s ease-in-out infinite;
}

.completed-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 16px;
  margin-top: 20px;
  background: rgba(0, 255, 136, 0.1);
  border: 1px solid rgba(0, 255, 136, 0.3);
  color: var(--cyber-green);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 2px;
  clip-path: polygon(0 0, calc(100% - 10px) 0, 100% 10px, 100% 100%, 10px 100%, 0 calc(100% - 10px));
}

.completed-icon {
  font-size: 18px;
}

.message-text {
  font-size: 14px;
  line-height: 1.7;
  color: var(--cyber-text-primary);
}

.result-body {
  padding: 24px;
  max-height: 400px;
  overflow-y: auto;
}

.result-content {
  font-size: 14px;
  line-height: 1.8;
  color: var(--cyber-text-primary);
}

.result-content :deep(.highlight) {
  color: var(--cyber-green);
  text-shadow: 0 0 8px rgba(0, 255, 136, 0.5);
}

.result-content :deep(.msg-h3) {
  color: var(--cyber-green);
  font-size: 18px;
  margin: 20px 0 12px;
  letter-spacing: 1px;
}

.result-content :deep(.msg-h4) {
  color: var(--cyber-cyan);
  font-size: 15px;
  margin: 16px 0 8px;
}

.result-content :deep(.msg-li) {
  margin: 6px 0;
  padding-left: 20px;
  position: relative;
  list-style: none;
}

.result-content :deep(.msg-li::before) {
  content: '◆';
  position: absolute;
  left: 0;
  color: var(--cyber-green);
  font-size: 8px;
  top: 6px;
}

.message-text :deep(.highlight) {
  color: var(--cyber-magenta);
  text-shadow: 0 0 8px rgba(255, 0, 255, 0.5);
}

.message-text :deep(.msg-h3) {
  color: var(--cyber-magenta);
  font-size: 16px;
  margin: 12px 0 8px;
}

.message-text :deep(.msg-h4) {
  color: var(--cyber-purple);
  font-size: 14px;
  margin: 10px 0 6px;
}

.loading-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
