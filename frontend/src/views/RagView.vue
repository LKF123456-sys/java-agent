<template>
  <div class="rag-container">
    <div class="sidebar">
      <el-button type="primary" @click="refreshDocuments" :loading="loading" :icon="Refresh" class="refresh-btn">
        刷新文档列表
      </el-button>
      
      <el-upload
        drag
        :action="uploadUrl"
        :headers="uploadHeaders"
        :on-success="handleUploadSuccess"
        :on-error="handleUploadError"
        :before-upload="beforeUpload"
        accept=".pdf,.doc,.docx,.xls,.xlsx,.html,.txt,.md"
        class="upload-area"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">
          将文件拖到此处，或<em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持 PDF、Word、Excel、HTML、TXT、MD 格式文件
          </div>
        </template>
      </el-upload>
      
      <el-divider>已上传文档</el-divider>
      
      <el-table :data="documents" v-loading="loading" stripe style="width: 100%" max-height="400">
        <el-table-column prop="fileName" label="文件名" min-width="150" show-overflow-tooltip />
        <el-table-column prop="fileSize" label="大小" width="100" :formatter="formatFileSize" />
        <el-table-column prop="chunkCount" label="分块数" width="80" />
        <el-table-column prop="uploadTime" label="上传时间" width="160" :formatter="formatTime" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="scope">
            <el-button type="danger" size="small" :icon="Delete" @click="deleteDocument(scope.row.docId)" circle />
          </template>
        </el-table-column>
      </el-table>
    </div>
    
    <div class="chat-area">
      <div class="chat-header">
        <h2><el-icon><Reading /></el-icon> RAG 知识库问答</h2>
        <p>基于上传文档的智能问答系统</p>
      </div>
      
      <div class="messages-container" ref="messagesContainer">
        <div v-if="messages.length === 0" class="empty-state">
          <el-empty description="上传文档后即可开始知识库问答" />
        </div>
        <ChatMessage v-for="(msg, index) in messages" :key="index" :message="msg" />
        <div v-if="loading" class="loading-indicator">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>AI 正在思考...</span>
        </div>
      </div>
      
      <div class="input-area">
        <div class="input-wrapper">
          <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="2"
            placeholder="请输入您的问题，AI将基于知识库内容回答..."
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
            :disabled="!inputMessage.trim()"
            class="send-btn"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Reading, Position, Refresh, Delete, UploadFilled, Loading } from '@element-plus/icons-vue'
import ChatMessage from '@/components/ChatMessage.vue'
import { ragChat, getDocuments, deleteDocument as deleteDocApi, createSSEConnection } from '@/api'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

// 状态变量
const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const messagesContainer = ref(null)
const documents = ref([])
const currentSSE = ref(null)

// 上传配置
const uploadUrl = ref('/api/rag/upload/file')
const uploadHeaders = ref({
  'Authorization': `Bearer ${userStore.token}`
})

// 格式化文件大小
const formatFileSize = (row) => {
  const size = row.fileSize
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / (1024 * 1024)).toFixed(1) + ' MB'
}

// 格式化时间
const formatTime = (row) => {
  if (!row.uploadTime) return '-'
  return new Date(row.uploadTime).toLocaleString('zh-CN')
}

// 上传前验证
const beforeUpload = (file) => {
  const allowedTypes = [
    'application/pdf',
    'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'application/vnd.ms-excel',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'text/html',
    'text/plain',
    'text/markdown'
  ]
  const maxSize = 50 * 1024 * 1024
  if (!allowedTypes.includes(file.type) && !file.name.match(/\.(pdf|doc|docx|xls|xlsx|html|txt|md)$/i)) {
    ElMessage.error('不支持的文件格式！')
    return false
  }
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过 50MB！')
    return false
  }
  return true
}

// 上传成功
const handleUploadSuccess = (response) => {
  if (response.code === 200) {
    ElMessage.success('文档上传成功！')
    refreshDocuments()
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

// 上传失败
const handleUploadError = () => {
  ElMessage.error('文档上传失败！')
}

// 删除文档
const deleteDocument = async (id) => {
  try {
    await deleteDocApi(id)
    ElMessage.success('文档删除成功！')
    refreshDocuments()
  } catch (error) {
    ElMessage.error('删除失败：' + error.message)
  }
}

// 刷新文档列表
const refreshDocuments = async () => {
  loading.value = true
  try {
    const res = await getDocuments()
    documents.value = res || []
  } catch (error) {
    ElMessage.error('获取文档列表失败：' + error.message)
  } finally {
    loading.value = false
  }
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

// 发送消息
const sendMessage = async () => {
  if (!inputMessage.value.trim() || loading.value) return
  
  const userMessage = {
    role: 'user',
    content: inputMessage.value
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
    let fullContent = ''
    let hasError = false
    
    currentSSE.value = createSSEConnection(`/api/rag/ask/stream?question=${encodeURIComponent(currentInput)}`, {
      onMessage: (data) => {
        if (data.startsWith('[ERROR]')) {
          hasError = true
          const errorMsg = data.substring(7)
          messages.value[messages.value.length - 1].content = '抱歉，发生了错误：' + errorMsg
          ElMessage.error(errorMsg)
          return
        }
        fullContent += data
        messages.value[messages.value.length - 1].content = fullContent
        scrollToBottom()
      },
      onError: (error) => {
        if (!hasError) {
          messages.value[messages.value.length - 1].content = '抱歉，连接出错了：' + error.message
          ElMessage.error('RAG问答失败')
        }
      },
      onClose: () => {
        loading.value = false
        if (!hasError) {
          scrollToBottom()
        }
      }
    })
  } catch (error) {
    messages.value[messages.value.length - 1].content = '抱歉，发生了错误：' + error.message
    loading.value = false
    ElMessage.error('RAG问答失败')
  }
}

// 组件卸载时关闭SSE连接
onUnmounted(() => {
  if (currentSSE.value) {
    currentSSE.value.close()
  }
})

onMounted(() => {
  refreshDocuments()
})
</script>

<style scoped>
.rag-container {
  display: flex;
  height: calc(100vh - 120px);
  gap: 20px;
}

.sidebar {
  width: 400px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.upload-area {
  width: 100%;
}

.refresh-btn {
  width: 100%;
}

.chat-area {
  flex: 1;
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
  margin: 5px 0 0 0;
  color: #909399;
  font-size: 14px;
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
