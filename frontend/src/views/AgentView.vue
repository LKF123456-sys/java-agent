<template>
  <div class="agent-page">
    <el-row :gutter="0" style="height: 100%">
      <el-col :span="6" class="config-sidebar">
        <div class="config-section">
          <h3 class="section-title">选择 Agent 类型</h3>
          <el-select v-model="selectedAgent" placeholder="请选择Agent类型" style="width: 100%">
            <el-option
              v-for="agent in agentTypes"
              :key="agent.value"
              :label="agent.label"
              :value="agent.value"
            >
              <div style="display: flex; align-items: center; gap: 8px;">
                <el-icon><component :is="agent.icon" /></el-icon>
                <span>{{ agent.label }}</span>
              </div>
            </el-option>
          </el-select>
        </div>

        <div class="config-section">
          <h3 class="section-title">Agent 说明</h3>
          <el-card shadow="never" class="agent-info-card">
            <p v-if="currentAgentInfo">{{ currentAgentInfo.description }}</p>
            <el-empty v-else description="请选择一个Agent类型" :image-size="80" />
          </el-card>
        </div>
      </el-col>

      <el-col :span="18" class="chat-main">
        <div class="chat-header">
          <h2 class="chat-title">
            <el-icon><MagicStick /></el-icon>
            {{ currentAgentInfo?.label || '智能 Agent' }}
          </h2>
          <el-tag v-if="isLoading" type="primary" effect="light">
            <el-icon class="is-loading"><Loading /></el-icon>
            Agent 正在思考...
          </el-tag>
        </div>

        <el-alert
          title="此页面为智能Agent对话，Agent具备工具调用能力"
          type="info"
          :closable="false"
          style="margin: 16px 24px 0"
        />

        <el-scrollbar ref="messagesRef" class="messages-container">
          <el-empty v-if="messages.length === 0" description="开始与专业Agent对话">
            <template #image>
              <el-icon :size="64" color="#409EFF"><MagicStick /></el-icon>
            </template>
          </el-empty>
          
          <ChatMessage 
            v-for="message in messages" 
            :key="message.id" 
            :message="message" 
          />
        </el-scrollbar>

        <ChatInput @send="handleSendMessage" :disabled="isLoading || !selectedAgent" placeholder="请选择Agent类型后输入消息..." />
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, watch, onUnmounted } from 'vue'
import { MagicStick, EditPen, Document, Reading, DataAnalysis, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ChatMessage from '@/components/ChatMessage.vue'
import ChatInput from '@/components/ChatInput.vue'
import { agentChat } from '@/api'

const selectedAgent = ref('')
const messages = ref([])
const isLoading = ref(false)
const messagesRef = ref(null)
let currentSSE = null

const agentTypes = [
  { value: 'code', label: '代码助手', description: '专业代码助手，解答编程问题、代码审查、优化建议', icon: EditPen },
  { value: 'writer', label: '写作助手', description: '内容创作助手，文章、文案、创意写作', icon: Document },
  { value: 'translator', label: '翻译助手', description: '多语言翻译支持', icon: Reading },
  { value: 'analyst', label: '数据分析助手', description: '数据分析、统计洞察、报告生成', icon: DataAnalysis }
]

const currentAgentInfo = computed(() => {
  return agentTypes.find(a => a.value === selectedAgent.value)
})

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

const handleSendMessage = async (content) => {
  if (!selectedAgent.value) {
    ElMessage.warning('请先选择Agent类型')
    return
  }

  closeSSE()
  
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

  isLoading.value = true
  scrollToBottom()

  try {
    currentSSE = agentChat(content, (data) => {
      if (data.content) {
        assistantMessage.content += data.content
        scrollToBottom()
      }
    })
    await currentSSE
  } catch (error) {
    console.error('Agent chat error:', error)
    assistantMessage.content = '发生错误，请重试'
    ElMessage.error('发送消息失败')
  } finally {
    isLoading.value = false
    closeSSE()
  }
}

watch(selectedAgent, () => {
  messages.value = []
})

onUnmounted(() => {
  closeSSE()
})
</script>

<style scoped>
.agent-page {
  height: calc(100vh - 108px);
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

.config-sidebar {
  background: #f5f7fa;
  border-right: 1px solid #e4e7ed;
  height: 100%;
  padding: 24px;
  overflow-y: auto;
}

.config-section {
  margin-bottom: 24px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 12px 0;
}

.agent-info-card {
  font-size: 13px;
  color: #606266;
}

:deep(.agent-info-card .el-card__body) {
  padding: 16px;
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
</style>
