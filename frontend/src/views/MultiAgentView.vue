<template>
  <div class="multi-agent-page">
    <div class="chat-main">
      <div class="chat-header">
        <h2 class="chat-title">
          <el-icon><UserFilled /></el-icon>
          多 Agent 协作
        </h2>
        <el-tag v-if="isLoading" type="primary" effect="light">
          <el-icon class="is-loading"><Loading /></el-icon>
          Agent 团队协作中...
        </el-tag>
      </div>

      <el-scrollbar ref="messagesRef" class="messages-container">
        <el-empty v-if="messages.length === 0 && executionSteps.length === 0" description="输入复杂任务，多Agent团队协同解决">
          <template #image>
            <el-icon :size="64" color="#409EFF"><UserFilled /></el-icon>
          </template>
        </el-empty>

        <el-timeline v-if="executionSteps.length > 0" class="execution-timeline">
          <el-timeline-item
            v-for="(step, index) in executionSteps"
            :key="index"
            :timestamp="step.timestamp"
            :type="step.status === 'completed' ? 'success' : step.status === 'active' ? 'primary' : 'info'"
            :hollow="step.status === 'pending'"
          >
            <el-card shadow="hover" class="step-card">
              <template #header>
                <div class="step-header">
                  <el-icon :size="20"><component :is="getAgentIcon(step.agent)" /></el-icon>
                  <span class="agent-name">{{ step.agentName }}</span>
                  <el-tag size="small" :type="step.status === 'completed' ? 'success' : step.status === 'active' ? 'primary' : 'info'">
                    {{ step.statusText }}
                  </el-tag>
                </div>
              </template>
              <div class="step-content">{{ step.content }}</div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
        
        <ChatMessage 
          v-for="message in messages" 
          :key="message.id" 
          :message="message" 
        />
      </el-scrollbar>

      <ChatInput @send="handleSendMessage" :disabled="isLoading" placeholder="描述你的复杂任务，例如：分析今天的股市行情并生成投资建议报告..." />
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onUnmounted } from 'vue'
import { UserFilled, Loading, MagicStick, Search, EditPen, Setting } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ChatMessage from '@/components/ChatMessage.vue'
import ChatInput from '@/components/ChatInput.vue'
import { multiAgentChat } from '@/api'

const messages = ref([])
const executionSteps = ref([])
const isLoading = ref(false)
const messagesRef = ref(null)
const currentConversationId = ref(null)
let currentSSE = null

const agentMap = {
  planner: { name: '规划师', icon: MagicStick },
  researcher: { name: '研究员', icon: Search },
  coder: { name: '编码员', icon: EditPen },
  critic: { name: '评审员', icon: Setting }
}

const getAgentIcon = (agent) => {
  return agentMap[agent]?.icon || MagicStick
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTo({ top: messagesRef.value.wrapRef.scrollHeight, behavior: 'smooth' })
    }
  })
}

const closeSSE = () => {
  if (currentSSE) {
    currentSSE.close()
    currentSSE = null
  }
  isLoading.value = false
}

const addExecutionStep = (agent, content, status = 'active') => {
  const agentInfo = agentMap[agent] || { name: agent, icon: MagicStick }
  const statusText = {
    pending: '等待中',
    active: '执行中',
    completed: '已完成'
  }[status]
  
  executionSteps.value.push({
    agent,
    agentName: agentInfo.name,
    content,
    status,
    statusText,
    timestamp: new Date().toLocaleTimeString()
  })
  scrollToBottom()
}

const handleSendMessage = async (content) => {
  closeSSE()
  
  const userMessage = {
    id: Date.now().toString(),
    role: 'user',
    content
  }
  messages.value.push(userMessage)
  executionSteps.value = []

  const assistantMessage = {
    id: (Date.now() + 1).toString(),
    role: 'assistant',
    content: ''
  }
  messages.value.push(assistantMessage)

  isLoading.value = true
  scrollToBottom()

  const onMessage = (data) => {
    if (data.step && data.agent) {
      addExecutionStep(data.agent, data.step, data.status || 'completed')
    }
    if (data.content) {
      assistantMessage.content += data.content
      scrollToBottom()
    }
  }
  onMessage._conversationIdCallback = (convId) => {
    currentConversationId.value = convId
  }

  try {
    currentSSE = multiAgentChat(content, currentConversationId.value, onMessage)
    await currentSSE
  } catch (error) {
    console.error('Multi agent chat error:', error)
    assistantMessage.content = '发生错误，请重试'
    ElMessage.error('发送消息失败')
  } finally {
    isLoading.value = false
    closeSSE()
  }
}

onUnmounted(() => {
  closeSSE()
})
</script>

<style scoped>
.multi-agent-page {
  height: calc(100vh - 108px);
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

.chat-main {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chat-header {
  padding: 16px 24px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chat-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.messages-container {
  flex: 1;
  padding: 24px;
}

.execution-timeline {
  margin-bottom: 24px;
  padding-left: 8px;
}

.step-card {
  margin-bottom: 8px;
}

:deep(.step-card .el-card__header) {
  padding: 12px 16px;
}

:deep(.step-card .el-card__body) {
  padding: 12px 16px;
  font-size: 14px;
  color: #606266;
}

.step-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.agent-name {
  flex: 1;
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}
</style>
