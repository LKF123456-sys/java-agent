<template>
  <div class="memory-page">
    <div class="chat-main">
      <div class="chat-header">
        <h2 class="chat-title">
          <el-icon><Memo /></el-icon>
          记忆对话
        </h2>
        <el-tag type="success" effect="light">持久化记忆已启用</el-tag>
      </div>

      <el-scrollbar ref="messagesRef" class="messages-container">
        <el-empty v-if="messages.length === 0" description="AI会记住你们的对话历史">
          <template #image>
            <el-icon :size="64" color="#67c23a"><Memo /></el-icon>
          </template>
        </el-empty>
        
        <ChatMessage 
          v-for="message in messages" 
          :key="message.id" 
          :message="message" 
        />
      </el-scrollbar>

      <ChatInput @send="handleSendMessage" :disabled="isLoading" placeholder="开始有记忆的对话..." />
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onUnmounted } from 'vue'
import { Memo, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ChatMessage from '@/components/ChatMessage.vue'
import ChatInput from '@/components/ChatInput.vue'
import { memoryChat } from '@/api'

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
    const history = messages.value.slice(0, -2).map(m => ({
      role: m.role,
      content: m.content
    }))
    
    currentSSE = chatAPI.memoryChat(content, history, (data) => {
      if (data.content) {
        assistantMessage.content += data.content
        scrollToBottom()
      }
    })
    await currentSSE
  } catch (error) {
    console.error('Memory chat error:', error)
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
.memory-page {
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
