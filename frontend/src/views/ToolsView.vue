<template>
  <CyberLayout>
    <div class="tools-view">
      <div class="page-header">
        <div class="header-decoration left"></div>
        <h1 class="page-title blue-glow">FUNCTION CALLING 工具调用</h1>
        <div class="header-decoration right"></div>
      </div>

      <div class="tools-demo">
        <div class="demo-tabs">
          <button 
            class="demo-tab" 
            :class="{ active: activeDemo === 'weather' }"
            @click="activeDemo = 'weather'"
          >
            <span class="tab-icon">🌤</span>
            <span>天气查询</span>
          </button>
          <button 
            class="demo-tab" 
            :class="{ active: activeDemo === 'multi' }"
            @click="activeDemo = 'multi'"
          >
            <span class="tab-icon">🔧</span>
            <span>多工具协作</span>
          </button>
        </div>

        <div class="chat-container cyber-card">
          <div class="chat-header">
            <div class="header-status">
              <span class="status-dot blue"></span>
              <span class="status-label">{{ activeDemo === 'weather' ? '天气查询工具' : '多工具协作模式' }}</span>
            </div>
            <button class="clear-btn" @click="handleClear" :disabled="messages.length === 0">
              <span>清空对话</span>
            </button>
          </div>

          <div class="messages-area cyber-scrollbar" ref="messagesRef">
            <div v-if="messages.length === 0" class="welcome-state">
              <div class="welcome-icon">⚡</div>
              <p class="welcome-title">Function Calling 演示</p>
              <p class="welcome-desc">
                {{ activeDemo === 'weather' 
                  ? '输入问题查询天气信息，例如："北京天气如何"' 
                  : '输入复杂问题，AI将自动调用多个工具协作完成，例如："北京天气怎么样？顺便计算123乘以456等于多少"' 
                }}
              </p>
              <div class="example-questions">
                <button 
                  v-for="(example, i) in currentExamples" 
                  :key="i" 
                  class="example-btn"
                  @click="sendMessage(example)"
                >
                  {{ example }}
                </button>
              </div>
            </div>

            <div v-for="(msg, index) in messages" :key="index" class="message-item">
              <div v-if="msg.role === 'user'" class="user-message">
                <div class="message-avatar user-avatar">U</div>
                <div class="message-content user-content">
                  <p>{{ msg.content }}</p>
                </div>
              </div>
              
              <div v-else class="ai-message">
                <div class="message-avatar ai-avatar">AI</div>
                <div class="message-content ai-content">
                  <div v-if="msg.loading" class="loading-bubble">
                    <div class="tool-calls">
                      <div v-if="msg.toolCalls && msg.toolCalls.length > 0" class="tool-call-list">
                        <div v-for="(tool, ti) in msg.toolCalls" :key="ti" class="tool-call-item">
                          <span class="tool-icon">🔧</span>
                          <span class="tool-name">{{ tool.name }}</span>
                          <span v-if="tool.status === 'pending'" class="tool-status pending">
                            <span class="typing-dot blue"></span>
                            <span class="typing-dot blue"></span>
                            <span class="typing-dot blue"></span>
                          </span>
                          <span v-else-if="tool.status === 'done'" class="tool-status done">✓</span>
                        </div>
                      </div>
                    </div>
                    <div v-if="!msg.content" class="thinking-text">AI 正在思考并调用工具...</div>
                  </div>
                  <div v-else class="response-text" v-html="formatResponse(msg.content)"></div>
                </div>
              </div>
            </div>
          </div>

          <div class="input-section">
            <div class="input-wrapper">
              <input 
                v-model="inputMessage" 
                type="text" 
                class="cyber-input blue-input" 
                :placeholder="activeDemo === 'weather' ? '询问天气，例如：北京天气如何...' : '输入复杂问题，可同时问天气和计算...'"
                @keyup.enter="handleSend"
                :disabled="loading"
              />
              <button 
                class="cyber-btn blue-btn send-btn" 
                @click="handleSend"
                :disabled="loading || !inputMessage.trim()"
              >
                <span v-if="loading" class="loading-spinner small"></span>
                <span v-else>发送</span>
              </button>
            </div>
            <div v-if="error" class="error-msg">
              <span class="error-icon">✕</span>
              {{ error }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </CyberLayout>
</template>

<script setup>
import { ref, computed, nextTick, watch } from 'vue'
import CyberLayout from '@/components/CyberLayout.vue'
import { get } from '@/utils/request'

const activeDemo = ref('weather')
const inputMessage = ref('')
const loading = ref(false)
const error = ref('')
const messages = ref([])
const messagesRef = ref(null)

const weatherExamples = ['北京天气如何？', '上海今天天气怎么样？', '查询深圳的天气']
const multiExamples = ['北京天气如何？顺便计算256乘以128等于多少', '广州天气怎么样？计算1024加2048等于几', '杭州天气？再计算999除以3等于多少']

const currentExamples = computed(() => {
  return activeDemo.value === 'weather' ? weatherExamples : multiExamples
})

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

watch(activeDemo, () => {
  messages.value = []
  error.value = ''
})

watch(() => messages.value.length, () => {
  scrollToBottom()
})

const handleClear = () => {
  messages.value = []
  error.value = ''
}

const sendMessage = (text) => {
  inputMessage.value = text
  handleSend()
}

const handleSend = async () => {
  if (!inputMessage.value.trim() || loading.value) return
  
  const content = inputMessage.value.trim()
  inputMessage.value = ''
  error.value = ''
  
  messages.value.push({
    role: 'user',
    content
  })
  
  const aiMsgIndex = messages.value.length
  messages.value.push({
    role: 'assistant',
    content: '',
    loading: true,
    toolCalls: []
  })
  
  loading.value = true
  scrollToBottom()
  
  try {
    const endpoint = activeDemo.value === 'weather' ? '/api/tools/weather' : '/api/tools/multi'
    const result = await get(endpoint, { message: content })
    
    let responseText = ''
    let toolCalls = []
    
    if (typeof result === 'string') {
      responseText = result
    } else if (result) {
      responseText = result.response || result.result || result.content || JSON.stringify(result, null, 2)
      if (result.toolCalls || result.tools) {
        toolCalls = (result.toolCalls || result.tools).map(t => ({
          name: t.name || t.function || t,
          status: 'done'
        }))
      }
    }
    
    messages.value[aiMsgIndex] = {
      role: 'assistant',
      content: responseText,
      loading: false,
      toolCalls
    }
  } catch (err) {
    messages.value[aiMsgIndex] = {
      role: 'assistant',
      content: '请求失败: ' + (err.message || '未知错误'),
      loading: false
    }
    error.value = err.message || '请求失败'
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

const formatResponse = (text) => {
  if (!text) return ''
  return text
    .replace(/\n/g, '<br>')
    .replace(/\*\*(.*?)\*\*/g, '<strong class="blue-highlight">$1</strong>')
    .replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')
}
</script>

<style scoped>
.tools-view {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  margin-bottom: 28px;
  padding: 16px 0;
}

.header-decoration {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--cyber-blue), transparent);
  position: relative;
}

.header-decoration::after {
  content: '';
  position: absolute;
  top: -4px;
  width: 9px;
  height: 9px;
  border: 1px solid var(--cyber-blue);
  transform: rotate(45deg);
  box-shadow: 0 0 10px var(--cyber-blue);
}

.header-decoration.left::after {
  right: 0;
}

.header-decoration.right::after {
  left: 0;
}

.page-title {
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 3px;
  margin: 0;
  white-space: nowrap;
}

.blue-glow {
  color: var(--cyber-blue);
  text-shadow: 0 0 10px var(--cyber-blue), 0 0 20px rgba(0, 102, 255, 0.5), 0 0 40px rgba(0, 102, 255, 0.3);
}

.tools-demo {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.demo-tabs {
  display: flex;
  gap: 12px;
}

.demo-tab {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 28px;
  background: rgba(0, 102, 255, 0.08);
  border: 1px solid rgba(0, 102, 255, 0.2);
  color: var(--cyber-text-secondary);
  font-family: inherit;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  clip-path: polygon(0 0, calc(100% - 10px) 0, 100% 10px, 100% 100%, 10px 100%, 0 calc(100% - 10px));
  letter-spacing: 1px;
}

.demo-tab:hover {
  background: rgba(0, 102, 255, 0.15);
  border-color: rgba(0, 102, 255, 0.4);
}

.demo-tab.active {
  background: linear-gradient(135deg, rgba(0, 102, 255, 0.25), rgba(0, 255, 255, 0.15));
  border-color: var(--cyber-blue);
  color: var(--cyber-cyan);
  box-shadow: 0 0 25px rgba(0, 102, 255, 0.3);
}

.tab-icon {
  font-size: 18px;
}

.chat-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 260px);
  min-height: 500px;
  clip-path: polygon(0 0, calc(100% - 15px) 0, 100% 15px, 100% 100%, 15px 100%, 0 calc(100% - 15px));
}

.chat-container::before {
  background: linear-gradient(90deg, var(--cyber-blue), var(--cyber-cyan), var(--cyber-blue));
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid rgba(0, 102, 255, 0.2);
  background: rgba(0, 102, 255, 0.05);
}

.header-status {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  animation: pulse-glow 2s ease-in-out infinite;
}

.status-dot.blue {
  background: var(--cyber-blue);
  box-shadow: 0 0 12px var(--cyber-blue);
}

.status-label {
  font-size: 13px;
  color: var(--cyber-cyan);
  letter-spacing: 1px;
}

.clear-btn {
  padding: 8px 16px;
  background: transparent;
  border: 1px solid rgba(0, 102, 255, 0.3);
  color: var(--cyber-text-muted);
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  clip-path: polygon(0 0, calc(100% - 6px) 0, 100% 6px, 100% 100%, 6px 100%, 0 calc(100% - 6px));
}

.clear-btn:hover:not(:disabled) {
  border-color: var(--cyber-pink);
  color: var(--cyber-pink);
}

.clear-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.welcome-state {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 40px 20px;
}

.welcome-icon {
  font-size: 56px;
  margin-bottom: 20px;
  filter: drop-shadow(0 0 20px var(--cyber-blue));
}

.welcome-title {
  font-size: 20px;
  color: var(--cyber-cyan);
  margin-bottom: 12px;
  letter-spacing: 2px;
  text-shadow: 0 0 10px var(--cyber-cyan);
}

.welcome-desc {
  font-size: 14px;
  color: var(--cyber-text-muted);
  margin-bottom: 28px;
  max-width: 500px;
  line-height: 1.7;
}

.example-questions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  max-width: 450px;
}

.example-btn {
  padding: 12px 20px;
  background: rgba(0, 102, 255, 0.08);
  border: 1px solid rgba(0, 102, 255, 0.25);
  color: var(--cyber-text-secondary);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.3s ease;
  text-align: left;
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.example-btn:hover {
  background: rgba(0, 102, 255, 0.15);
  border-color: var(--cyber-blue);
  color: var(--cyber-cyan);
  box-shadow: 0 0 15px rgba(0, 102, 255, 0.2);
  transform: translateX(4px);
}

.message-item {
  margin-bottom: 24px;
}

.user-message,
.ai-message {
  display: flex;
  gap: 14px;
}

.message-avatar {
  width: 38px;
  height: 38px;
  min-width: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 13px;
  letter-spacing: 1px;
}

.user-avatar {
  background: linear-gradient(135deg, var(--cyber-cyan), var(--cyber-blue));
  color: var(--cyber-bg-primary);
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.ai-avatar {
  background: linear-gradient(135deg, var(--cyber-blue), var(--cyber-purple));
  color: white;
  clip-path: polygon(8px 0, 100% 0, 100% calc(100% - 8px), calc(100% - 8px) 100%, 0 100%, 0 8px);
}

.message-content {
  flex: 1;
  max-width: calc(100% - 60px);
}

.user-content {
  padding: 14px 18px;
  background: rgba(0, 255, 255, 0.08);
  border: 1px solid rgba(0, 255, 255, 0.2);
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.user-content p {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--cyber-text-primary);
}

.ai-content {
  padding: 14px 18px;
  background: rgba(0, 102, 255, 0.06);
  border: 1px solid rgba(0, 102, 255, 0.2);
  clip-path: polygon(8px 0, 100% 0, 100% calc(100% - 8px), calc(100% - 8px) 100%, 0 100%, 0 8px);
}

.loading-bubble {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tool-calls {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tool-call-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tool-call-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: rgba(0, 102, 255, 0.12);
  border: 1px solid rgba(0, 102, 255, 0.3);
  font-size: 12px;
  width: fit-content;
  clip-path: polygon(0 0, calc(100% - 6px) 0, 100% 6px, 100% 100%, 6px 100%, 0 calc(100% - 6px));
}

.tool-icon {
  font-size: 14px;
}

.tool-name {
  color: var(--cyber-cyan);
  font-family: 'Consolas', monospace;
  letter-spacing: 0.5px;
}

.tool-status {
  display: flex;
  align-items: center;
  gap: 3px;
  margin-left: 4px;
}

.tool-status.done {
  color: var(--cyber-green);
  font-weight: 700;
}

.tool-status.pending {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.typing-dot.blue {
  background: var(--cyber-blue);
  box-shadow: 0 0 8px var(--cyber-blue);
  width: 6px;
  height: 6px;
}

.thinking-text {
  font-size: 13px;
  color: var(--cyber-text-muted);
  display: flex;
  align-items: center;
  gap: 8px;
}

.response-text {
  font-size: 14px;
  line-height: 1.8;
  color: var(--cyber-text-primary);
}

.blue-highlight {
  color: var(--cyber-cyan);
  text-shadow: 0 0 6px rgba(0, 255, 255, 0.4);
}

.inline-code {
  padding: 2px 8px;
  background: rgba(0, 102, 255, 0.15);
  border: 1px solid rgba(0, 102, 255, 0.3);
  font-family: 'Consolas', monospace;
  font-size: 13px;
  color: var(--cyber-cyan);
  border-radius: 3px;
}

.input-section {
  padding: 18px 24px;
  border-top: 1px solid rgba(0, 102, 255, 0.2);
  background: var(--cyber-bg-secondary);
}

.input-wrapper {
  display: flex;
  gap: 12px;
}

.blue-input:focus {
  border-color: var(--cyber-blue);
  box-shadow: 0 0 20px rgba(0, 102, 255, 0.3);
}

.blue-btn {
  background: linear-gradient(135deg, var(--cyber-blue) 0%, var(--cyber-cyan) 100%);
}

.blue-btn:hover {
  box-shadow: 0 0 30px rgba(0, 102, 255, 0.5);
}

.send-btn {
  min-width: 90px;
  padding: 14px 24px;
}

.error-msg {
  margin-top: 12px;
  padding: 10px 14px;
  background: rgba(255, 0, 128, 0.1);
  border: 1px solid rgba(255, 0, 128, 0.3);
  color: var(--cyber-pink);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 10px;
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.error-icon {
  font-weight: 700;
}

.loading-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.loading-spinner.small {
  width: 14px;
  height: 14px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@keyframes pulse-glow {
  0%, 100% { opacity: 1; box-shadow: 0 0 12px var(--cyber-blue); }
  50% { opacity: 0.6; box-shadow: 0 0 6px var(--cyber-blue); }
}
</style>
