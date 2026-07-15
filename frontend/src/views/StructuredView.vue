<template>
  <div class="structured-container">
    <div class="chat-area">
      <div class="chat-header">
        <h2><el-icon><Document /></el-icon> 结构化输出</h2>
        <p>AI 以 JSON 格式返回结构化数据</p>
        
        <div class="output-type-selector">
          <span class="selector-label">输出类型：</span>
          <el-select v-model="outputType" placeholder="请选择输出类型" style="width: 200px">
            <el-option label="书籍信息" value="book" />
            <el-option label="电影信息" value="movie" />
            <el-option label="人物信息" value="person" />
          </el-select>
        </div>
      </div>
      
      <div class="messages-container" ref="messagesContainer">
        <div v-if="messages.length === 0" class="empty-state">
          <el-empty description="请输入描述，AI 将返回结构化 JSON 数据" />
        </div>
        <ChatMessage v-for="(msg, index) in messages" :key="index" :message="msg" />
        <div v-if="loading" class="loading-indicator">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>AI 正在生成结构化数据...</span>
        </div>
      </div>
      
      <div class="input-area">
        <div class="input-wrapper">
          <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="2"
            :placeholder="placeholderText"
            @keydown.enter.exact.prevent="sendMessage"
            :disabled="loading"
            resize="none"
          />
          <el-button 
            type="primary" 
            :icon="Position" 
            circle 
            @click="sendMessage"
            :loading="loading"
            :disabled="!inputMessage.trim() || !outputType"
            class="send-btn"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Position, Loading } from '@element-plus/icons-vue'
import ChatMessage from '@/components/ChatMessage.vue'
import request from '@/utils/request'

const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const messagesContainer = ref(null)
const outputType = ref('book')

const placeholderText = computed(() => {
  switch (outputType.value) {
    case 'book':
      return '例如：请介绍《三体》这本书的信息'
    case 'movie':
      return '例如：请介绍《流浪地球》这部电影'
    case 'person':
      return '例如：请介绍爱因斯坦的生平'
    default:
      return '请输入描述...'
  }
})

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

const sendMessage = async () => {
  if (!inputMessage.value.trim() || loading.value || !outputType.value) return
  
  const userMessage = {
    role: 'user',
    content: `[${outputType.value}] ${inputMessage.value}`
  }
  messages.value.push(userMessage)
  const currentInput = inputMessage.value
  inputMessage.value = ''
  loading.value = true
  scrollToBottom()
  
  const aiMessage = {
    role: 'assistant',
    content: '',
    timestamp: new Date()
  }
  messages.value.push(aiMessage)
  
  try {
    const type = outputType.value === 'person' ? 'book' : outputType.value
    const response = await request.post(`/api/structured/extract/${type}`, {
      content: currentInput,
      type: outputType.value
    })
    if (response.code === 200) {
      aiMessage.content = JSON.stringify(response.data, null, 2)
    } else {
      aiMessage.content = '抱歉，结构化提取失败：' + (response.message || '未知错误')
    }
    scrollToBottom()
  } catch (error) {
    aiMessage.content = '抱歉，发生了错误：' + (error.message || '请求失败')
    ElMessage.error('结构化输出请求失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.structured-container {
  height: calc(100vh - 120px);
}

.chat-area {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.chat-header {
  padding: 20px;
  border-bottom: 1px solid #ebeef5;
}

.chat-header h2 {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
}

.chat-header p {
  margin: 5px 0 15px 0;
  color: #909399;
  font-size: 14px;
}

.output-type-selector {
  display: flex;
  align-items: center;
  gap: 10px;
}

.selector-label {
  font-size: 14px;
  color: #606266;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  color: #409EFF;
}

.loading-indicator .el-icon {
  font-size: 20px;
}

.input-area {
  padding: 20px;
  border-top: 1px solid #ebeef5;
  background: #fafafa;
}

.input-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.send-btn {
  flex-shrink: 0;
}
</style>
