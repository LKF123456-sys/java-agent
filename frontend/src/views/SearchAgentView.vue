<template>
  <div class="search-agent-page">
    <div class="chat-main">
      <div class="chat-header">
        <h2 class="chat-title">
          <el-icon><Search /></el-icon>
          联网搜索 Agent
        </h2>
        <el-tag v-if="isLoading" type="primary" effect="light">
          <el-icon class="is-loading"><Loading /></el-icon>
          正在搜索并分析...
        </el-tag>
      </div>

      <el-alert
        title="此Agent具备联网搜索能力，可获取最新信息"
        type="info"
        :closable="false"
        style="margin: 16px 24px 0"
      />

      <el-scrollbar ref="messagesRef" class="messages-container">
        <el-empty v-if="messages.length === 0" description="输入问题，AI将联网搜索最新信息">
          <template #image>
            <el-icon :size="64" color="#409EFF"><Search /></el-icon>
          </template>
        </el-empty>
        
        <ChatMessage 
          v-for="message in messages" 
          :key="message.id" 
          :message="message" 
        />
      </el-scrollbar>

      <ChatInput @send="handleSendMessage" :disabled="isLoading" placeholder="输入需要搜索的问题..." />
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onUnmounted } from 'vue'
import { Search, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ChatMessage from '@/components/ChatMessage.vue'
import ChatInput from '@/components/ChatInput.vue'
import { searchAgentChat } from '@/api'

const messages = ref([])
const isLoading = ref(false)
const messagesRef = ref(null)
let currentSSE = null

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
    currentSSE = searchAgentChat(content, (data) => {
      if (data.content) {
        assistantMessage.content += data.content
        scrollToBottom()
      }
    })
    await currentSSE
  } catch (error) {
    console.error('Search agent chat error:', error)
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
.search-agent-page {
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
</style>
