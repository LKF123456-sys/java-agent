<template>
  <CyberLayout>
    <div class="agent-view">
      <div class="page-header">
        <div class="header-decoration left"></div>
        <h1 class="page-title purple-glow">AI AGENT 智能代理</h1>
        <div class="header-decoration right"></div>
      </div>

      <div class="cards-grid">
        <div class="feature-card cyber-card purple-card">
          <div class="card-corner tl"></div>
          <div class="card-corner tr"></div>
          <div class="card-corner bl"></div>
          <div class="card-corner br"></div>
          
          <div class="card-header">
            <div class="card-icon travel-icon">✈</div>
            <div class="card-title-wrap">
              <h2 class="card-title">旅游规划</h2>
              <p class="card-subtitle">智能生成个性化旅行计划</p>
            </div>
          </div>
          
          <div class="card-body">
            <div class="form-group">
              <label class="form-label purple">目的地</label>
              <input 
                v-model="travelDestination" 
                type="text" 
                class="cyber-input purple-input" 
                placeholder="例如：北京、东京、巴黎"
              />
            </div>
            
            <div class="form-group">
              <label class="form-label purple">旅行天数</label>
              <div class="number-input-wrap">
                <button class="num-btn" @click="travelDays > 1 && travelDays--">-</button>
                <input 
                  v-model.number="travelDays" 
                  type="number" 
                  class="cyber-input purple-input number-input" 
                  min="1"
                  max="30"
                />
                <button class="num-btn" @click="travelDays < 30 && travelDays++">+</button>
              </div>
            </div>
            
            <button 
              class="cyber-btn purple-btn action-btn" 
              @click="handleTravelPlan"
              :disabled="travelLoading"
            >
              <span v-if="travelLoading" class="loading-spinner"></span>
              <span v-else class="btn-icon">◈</span>
              <span>{{ travelLoading ? '生成中...' : '生成计划' }}</span>
            </button>
          </div>
        </div>

        <div class="feature-card cyber-card purple-card">
          <div class="card-corner tl"></div>
          <div class="card-corner tr"></div>
          <div class="card-corner bl"></div>
          <div class="card-corner br"></div>
          
          <div class="card-header">
            <div class="card-icon task-icon">⚙</div>
            <div class="card-title-wrap">
              <h2 class="card-title">通用任务</h2>
              <p class="card-subtitle">流式执行任意复杂任务指令</p>
            </div>
          </div>
          
          <div class="card-body">
            <div class="form-group">
              <label class="form-label purple">任务目标</label>
              <textarea 
                v-model="taskGoal" 
                class="cyber-input purple-input textarea" 
                rows="4"
                placeholder="描述您想要完成的任务，例如：分析最近的市场趋势并给出投资建议"
              ></textarea>
            </div>
            
            <button 
              class="cyber-btn purple-btn action-btn" 
              @click="handleTask"
              :disabled="taskLoading"
            >
              <span v-if="taskLoading" class="loading-spinner"></span>
              <span v-else class="btn-icon">▶</span>
              <span>{{ taskLoading ? '执行中...' : '流式执行' }}</span>
            </button>
          </div>
        </div>
      </div>

      <div v-if="travelResult || taskResult || travelLoading || taskLoading" class="result-panel cyber-card">
        <div class="result-header">
          <span class="result-icon">◆</span>
          <h2 class="result-title purple-glow">执行结果</h2>
          <div class="result-tabs">
            <button 
              class="tab-btn" 
              :class="{ active: activeTab === 'travel' }"
              @click="activeTab = 'travel'"
              v-if="travelResult || travelLoading"
            >
              旅游规划
            </button>
            <button 
              class="tab-btn" 
              :class="{ active: activeTab === 'task' }"
              @click="activeTab = 'task'"
              v-if="taskResult || taskLoading"
            >
              通用任务
            </button>
          </div>
          <span class="panel-line"></span>
        </div>
        
        <div class="result-body cyber-scrollbar">
          <div v-if="activeTab === 'travel'">
            <div v-if="travelLoading" class="loading-state">
              <div class="loading-animation">
                <span class="typing-dot purple"></span>
                <span class="typing-dot purple"></span>
                <span class="typing-dot purple"></span>
              </div>
              <p class="loading-text-purple">Agent 正在规划您的旅程...</p>
              <p class="loading-hint">正在检索目的地信息、规划行程路线</p>
            </div>
            <div v-else-if="travelError" class="error-state">
              <span class="error-big-icon">✕</span>
              <p class="error-text">{{ travelError }}</p>
            </div>
            <div v-else-if="travelResult" class="result-content" v-html="formatResult(travelResult)"></div>
          </div>
          
          <div v-if="activeTab === 'task'">
            <div v-if="taskLoading" class="streaming-state">
              <div class="stream-indicator">
                <span class="stream-dot"></span>
                <span class="stream-text">实时流式输出中...</span>
              </div>
              <div v-if="taskResult" class="result-content" v-html="formatResult(taskResult)"></div>
              <div class="typing-indicator-wrapper">
                <span class="typing-cursor">▊</span>
              </div>
            </div>
            <div v-else-if="taskError" class="error-state">
              <span class="error-big-icon">✕</span>
              <p class="error-text">{{ taskError }}</p>
            </div>
            <div v-else-if="taskResult" class="result-content" v-html="formatResult(taskResult)"></div>
          </div>
        </div>
      </div>
    </div>
  </CyberLayout>
</template>

<script setup>
import { ref, watch, nextTick, onUnmounted } from 'vue'
import CyberLayout from '@/components/CyberLayout.vue'
import { get, createSSEConnection } from '@/utils/request'

const travelDestination = ref('')
const travelDays = ref(3)
const travelLoading = ref(false)
const travelError = ref('')
const travelResult = ref('')

const taskGoal = ref('')
const taskLoading = ref(false)
const taskError = ref('')
const taskResult = ref('')
let taskSSEConnection = null
const resultBodyRef = ref(null)

const activeTab = ref('travel')

const scrollToBottom = () => {
  nextTick(() => {
    const resultBody = document.querySelector('.result-body')
    if (resultBody) {
      resultBody.scrollTop = resultBody.scrollHeight
    }
  })
}

const handleTravelPlan = async () => {
  if (!travelDestination.value.trim()) {
    travelError.value = '请输入目的地'
    return
  }
  
  travelLoading.value = true
  travelError.value = ''
  travelResult.value = ''
  activeTab.value = 'travel'
  
  try {
    const result = await get('/api/agent/travel-plan', {
      destination: travelDestination.value.trim(),
      days: travelDays.value
    })
    travelResult.value = typeof result === 'string' ? result : JSON.stringify(result, null, 2)
  } catch (error) {
    travelError.value = error.message || '生成计划失败'
  } finally {
    travelLoading.value = false
  }
}

const closeTaskSSE = () => {
  if (taskSSEConnection) {
    taskSSEConnection.close()
    taskSSEConnection = null
  }
}

const handleTask = async () => {
  if (!taskGoal.value.trim()) {
    taskError.value = '请输入任务目标'
    return
  }
  
  closeTaskSSE()
  
  taskLoading.value = true
  taskError.value = ''
  taskResult.value = ''
  activeTab.value = 'task'
  
  const url = `/api/agent/stream?goal=${encodeURIComponent(taskGoal.value.trim())}`
  let hasContent = false

  taskSSEConnection = createSSEConnection(url, {
    onMessage: (data) => {
      if (data === '[DONE]') {
        closeTaskSSE()
        taskLoading.value = false
        return
      }
      taskResult.value += data
      hasContent = true
      scrollToBottom()
    },
    onError: (error) => {
      closeTaskSSE()
      taskLoading.value = false
      if (!hasContent) {
        taskError.value = '执行任务失败，请重试'
      }
    }
  })
}

onUnmounted(() => {
  closeTaskSSE()
})

const formatResult = (text) => {
  if (!text) return ''
  return text
    .replace(/\n/g, '<br>')
    .replace(/\*\*(.*?)\*\*/g, '<strong class="purple-highlight">$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/^# (.*$)/gm, '<h3 class="result-h3">$1</h3>')
    .replace(/^## (.*$)/gm, '<h4 class="result-h4">$1</h4>')
    .replace(/^- (.*$)/gm, '<li class="result-li">$1</li>')
    .replace(/(\d+)\. (.*$)/gm, '<li class="result-li ordered">$1. $2</li>')
}
</script>

<style scoped>
.agent-view {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  margin-bottom: 32px;
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

.header-decoration.left::after {
  right: 0;
}

.header-decoration.right::after {
  left: 0;
}

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

.cards-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
  margin-bottom: 24px;
}

.feature-card {
  padding: 0;
  position: relative;
}

.purple-card::before {
  background: linear-gradient(90deg, var(--cyber-magenta), var(--cyber-purple), var(--cyber-magenta));
}

.card-corner {
  position: absolute;
  width: 20px;
  height: 20px;
  border: 2px solid var(--cyber-magenta);
}

.card-corner.tl {
  top: 10px;
  left: 10px;
  border-right: none;
  border-bottom: none;
}

.card-corner.tr {
  top: 10px;
  right: 10px;
  border-left: none;
  border-bottom: none;
}

.card-corner.bl {
  bottom: 10px;
  left: 10px;
  border-right: none;
  border-top: none;
}

.card-corner.br {
  bottom: 10px;
  right: 10px;
  border-left: none;
  border-top: none;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px 28px 20px;
  border-bottom: 1px solid rgba(255, 0, 255, 0.2);
}

.card-icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
}

.travel-icon {
  background: linear-gradient(135deg, var(--cyber-magenta), var(--cyber-purple));
  box-shadow: 0 0 20px rgba(255, 0, 255, 0.4);
}

.task-icon {
  background: linear-gradient(135deg, var(--cyber-purple), var(--cyber-blue));
  box-shadow: 0 0 20px rgba(107, 0, 255, 0.4);
}

.card-title-wrap {
  flex: 1;
}

.card-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--cyber-magenta);
  letter-spacing: 2px;
  margin: 0 0 6px 0;
}

.card-subtitle {
  font-size: 12px;
  color: var(--cyber-text-muted);
  letter-spacing: 1px;
  margin: 0;
}

.card-body {
  padding: 24px 28px;
}

.form-group {
  margin-bottom: 20px;
}

.form-label.purple {
  display: block;
  font-size: 12px;
  color: var(--cyber-magenta);
  letter-spacing: 1px;
  margin-bottom: 8px;
  text-transform: uppercase;
}

.purple-input:focus {
  border-color: var(--cyber-magenta);
  box-shadow: 0 0 20px rgba(255, 0, 255, 0.3);
}

.textarea {
  resize: none;
  font-family: inherit;
  line-height: 1.6;
}

.number-input-wrap {
  display: flex;
  gap: 8px;
  align-items: center;
}

.num-btn {
  width: 48px;
  height: 48px;
  background: rgba(255, 0, 255, 0.1);
  border: 1px solid rgba(255, 0, 255, 0.3);
  color: var(--cyber-magenta);
  font-size: 20px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.3s ease;
  clip-path: polygon(0 0, calc(100% - 6px) 0, 100% 6px, 100% 100%, 6px 100%, 0 calc(100% - 6px));
}

.num-btn:hover {
  background: rgba(255, 0, 255, 0.2);
  box-shadow: 0 0 15px rgba(255, 0, 255, 0.3);
}

.number-input {
  width: 100px;
  text-align: center;
}

.purple-btn {
  background: linear-gradient(135deg, var(--cyber-magenta) 0%, var(--cyber-purple) 100%);
}

.purple-btn:hover {
  box-shadow: 0 0 30px rgba(255, 0, 255, 0.5);
}

.action-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 14px;
  margin-top: 8px;
}

.btn-icon {
  font-size: 16px;
}

.result-panel {
  clip-path: polygon(0 0, calc(100% - 15px) 0, 100% 15px, 100% 100%, 15px 100%, 0 calc(100% - 15px));
  display: flex;
  flex-direction: column;
  max-height: 600px;
}

.result-panel::before {
  background: linear-gradient(90deg, var(--cyber-magenta), var(--cyber-purple), var(--cyber-magenta));
}

.result-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 24px;
  border-bottom: 1px solid rgba(255, 0, 255, 0.2);
  background: rgba(255, 0, 255, 0.05);
  flex-wrap: wrap;
}

.result-icon {
  color: var(--cyber-magenta);
  font-size: 10px;
  text-shadow: 0 0 10px var(--cyber-magenta);
}

.result-title {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 2px;
  margin: 0;
}

.result-tabs {
  display: flex;
  gap: 8px;
  margin-left: 16px;
}

.tab-btn {
  padding: 6px 16px;
  background: transparent;
  border: 1px solid rgba(255, 0, 255, 0.3);
  color: var(--cyber-text-secondary);
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  clip-path: polygon(0 0, calc(100% - 6px) 0, 100% 6px, 100% 100%, 6px 100%, 0 calc(100% - 6px));
}

.tab-btn:hover {
  border-color: var(--cyber-magenta);
  color: var(--cyber-magenta);
}

.tab-btn.active {
  background: rgba(255, 0, 255, 0.2);
  border-color: var(--cyber-magenta);
  color: var(--cyber-magenta);
  box-shadow: 0 0 15px rgba(255, 0, 255, 0.3);
}

.panel-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, var(--cyber-magenta), transparent);
}

.result-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  min-height: 200px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
}

.streaming-state {
  position: relative;
}

.stream-indicator {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
  padding: 10px 16px;
  background: rgba(255, 0, 255, 0.1);
  border: 1px solid rgba(255, 0, 255, 0.3);
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.stream-dot {
  width: 10px;
  height: 10px;
  background: var(--cyber-magenta);
  border-radius: 50%;
  animation: stream-pulse 1s ease-in-out infinite;
  box-shadow: 0 0 10px var(--cyber-magenta);
}

@keyframes stream-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.8); }
}

.stream-text {
  font-size: 13px;
  color: var(--cyber-magenta);
  letter-spacing: 1px;
}

.typing-indicator-wrapper {
  display: inline;
}

.typing-cursor {
  color: var(--cyber-magenta);
  animation: blink 1s step-end infinite;
  font-weight: 700;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.loading-animation {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}

.typing-dot.purple {
  background: var(--cyber-magenta);
  box-shadow: 0 0 10px var(--cyber-magenta);
}

.loading-text-purple {
  font-size: 16px;
  color: var(--cyber-magenta);
  margin-bottom: 8px;
  letter-spacing: 1px;
}

.loading-hint {
  font-size: 13px;
  color: var(--cyber-text-muted);
}

.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
}

.error-big-icon {
  font-size: 48px;
  color: var(--cyber-pink);
  margin-bottom: 16px;
  text-shadow: 0 0 20px var(--cyber-pink);
}

.error-text {
  color: var(--cyber-pink);
  font-size: 14px;
}

.result-content {
  line-height: 1.8;
  font-size: 14px;
  color: var(--cyber-text-primary);
}

.result-content :deep(.purple-highlight) {
  color: var(--cyber-magenta);
  text-shadow: 0 0 8px rgba(255, 0, 255, 0.5);
}

.result-content :deep(.result-h3) {
  color: var(--cyber-magenta);
  font-size: 18px;
  margin: 20px 0 12px;
  letter-spacing: 1px;
}

.result-content :deep(.result-h4) {
  color: var(--cyber-purple);
  font-size: 15px;
  margin: 16px 0 8px;
}

.result-content :deep(.result-li) {
  margin: 6px 0;
  padding-left: 20px;
  position: relative;
  list-style: none;
}

.result-content :deep(.result-li::before) {
  content: '◆';
  position: absolute;
  left: 0;
  color: var(--cyber-magenta);
  font-size: 8px;
  top: 6px;
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
