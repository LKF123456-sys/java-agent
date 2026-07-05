<template>
  <div class="rag-view">
    <div class="page-header">
      <div class="header-decoration left"></div>
      <h1 class="page-title cyan-glow">RAG 知识库检索</h1>
      <div class="header-decoration right"></div>
    </div>

    <div class="content-grid">
      <div class="panel doc-panel cyber-card">
        <div class="panel-header">
          <span class="panel-icon">◆</span>
          <h2 class="panel-title">文档管理</h2>
          <span class="panel-line"></span>
          <button class="refresh-btn" @click="loadDocuments" title="刷新列表">
            <span :class="['refresh-icon', { spinning: loadingDocs }]">↻</span>
          </button>
        </div>
        
        <div class="panel-body">
          <div class="upload-section">
            <label class="form-label">上传文档</label>
            <div class="upload-area" @click="triggerFileInput" @dragover.prevent @drop.prevent="handleFileDrop">
              <input 
                type="file" 
                ref="fileInputRef"
                accept=".pdf,.doc,.docx,.xls,.xlsx,.html,.htm,.md,.txt,.ppt,.pptx"
                @change="handleFileSelect"
                style="display: none;"
              />
              <div v-if="uploading" class="upload-progress">
                <span class="upload-spinner"></span>
                <span class="upload-text">上传中... {{ uploadProgress }}%</span>
              </div>
              <div v-else class="upload-placeholder">
                <span class="upload-icon">📄</span>
                <span class="upload-hint">点击或拖拽上传文档</span>
              </div>
              <div v-if="selectedFileName" class="selected-file">
                <span class="file-icon">✓</span>
                <span class="file-name">{{ selectedFileName }}</span>
              </div>
            </div>
            <div class="upload-tips">
              <span class="tip-icon">ℹ</span>
              <span class="tip-text">支持格式：PDF、Word(.doc/.docx)、Excel(.xls/.xlsx)、HTML、Markdown(.md)、TXT、PPT(.ppt/.pptx)</span>
            </div>
          </div>

          <div v-if="uploadSuccess" class="success-msg">
            <span class="success-icon">✓</span>
            文件上传成功，已生成 {{ documentCount }} 个文档块
          </div>
          <div v-if="uploadError" class="error-msg">
            <span class="error-icon">✕</span>
            {{ uploadError }}
          </div>

          <div class="divider">
            <span class="divider-text">文档列表</span>
          </div>

          <div v-if="loadingDocs" class="loading-docs">
            <span class="loading-spinner small"></span>
            <span>加载文档列表...</span>
          </div>

          <div v-else-if="documents.length === 0" class="empty-docs">
            <span class="empty-docs-icon">📚</span>
            <p>暂无文档，请上传文件</p>
          </div>

          <div v-else class="doc-list cyber-scrollbar">
            <div v-for="doc in documents" :key="doc.docId" class="doc-item">
              <div class="doc-info">
                <div class="doc-name">
                  <span class="doc-type-icon">{{ getFileIcon(doc.fileType) }}</span>
                  <span :title="doc.fileName">{{ doc.fileName }}</span>
                </div>
                <div class="doc-meta">
                  <span class="meta-item">{{ formatFileSize(doc.fileSize) }}</span>
                  <span class="meta-sep">·</span>
                  <span class="meta-item">{{ doc.chunkCount }} 块</span>
                  <span class="meta-sep">·</span>
                  <span class="meta-item">{{ formatDate(doc.createdAt) }}</span>
                </div>
              </div>
              <button 
                class="delete-btn" 
                @click="handleDeleteDocument(doc)"
                :disabled="deletingDocId === doc.docId"
                title="删除文档"
              >
                <span v-if="deletingDocId === doc.docId" class="loading-spinner small"></span>
                <span v-else>🗑</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      <div class="panel qa-panel cyber-card">
        <div class="panel-header">
          <span class="panel-icon">◆</span>
          <h2 class="panel-title">智能问答</h2>
          <span class="panel-line"></span>
          <span v-if="streaming" class="streaming-badge">
            <span class="pulse-dot"></span>
            AI思考中
          </span>
        </div>
        
        <div class="chat-history cyber-scrollbar" ref="chatHistoryRef">
          <div v-if="qaHistory.length === 0" class="empty-state">
            <div class="empty-icon">◈</div>
            <p class="empty-text">知识库已就绪</p>
            <p class="empty-hint">上传文档后，在下方输入问题，基于知识库内容进行问答</p>
            <div v-if="documents.length > 0" class="quick-questions">
              <p class="quick-title">试试这些问题：</p>
              <button v-for="q in quickQuestions" :key="q" class="quick-btn" @click="sendQuickQuestion(q)">
                {{ q }}
              </button>
            </div>
          </div>
          
          <div v-for="(item, index) in qaHistory" :key="index" class="qa-item">
            <div class="question-bubble">
              <span class="bubble-icon">Q</span>
              <p class="bubble-text">{{ item.question }}</p>
            </div>
            <div class="answer-bubble">
              <span class="bubble-icon ai">A</span>
              <div class="bubble-text">
                <div v-if="item.thinking" class="thinking-indicator">
                  <span class="thinking-dot"></span>
                  <span class="thinking-dot"></span>
                  <span class="thinking-dot"></span>
                  <span class="loading-text">AI 正在检索知识库...</span>
                </div>
                <div v-else class="answer-content" v-html="renderContent(item.answer)"></div>
              </div>
            </div>
          </div>
        </div>

        <div class="input-area">
          <div class="input-wrapper">
            <input 
              v-model="question" 
              type="text" 
              class="cyber-input question-input" 
              placeholder="输入您的问题..."
              @keyup.enter="handleAsk"
              :disabled="streaming"
            />
            <button 
              class="cyber-btn cyber-btn-cyan ask-btn" 
              @click="handleAsk"
              :disabled="streaming || !question.trim()"
            >
              <span v-if="streaming" class="loading-spinner small"></span>
              <span v-else>发送</span>
            </button>
          </div>
          <div v-if="askError" class="error-msg input-error">
            <span class="error-icon">✕</span>
            {{ askError }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, watch } from 'vue'
import { get, del } from '@/utils/request'
import { createSSEConnection } from '@/utils/request'

const allowedExtensions = ['.pdf', '.doc', '.docx', '.xls', '.xlsx', '.html', '.htm', '.md', '.txt', '.ppt', '.pptx']

const quickQuestions = [
  '总结一下知识库的主要内容',
  '文档中提到了哪些关键概念？',
  '列出文档中的重要观点'
]

const fileInputRef = ref(null)
const selectedFile = ref(null)
const selectedFileName = ref('')
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadSuccess = ref(false)
const uploadError = ref('')
const documentCount = ref(0)

const documents = ref([])
const loadingDocs = ref(false)
const deletingDocId = ref(null)

const question = ref('')
const streaming = ref(false)
const askError = ref('')
const qaHistory = ref([])
const chatHistoryRef = ref(null)
let sseConnection = null

const scrollToBottom = () => {
  nextTick(() => {
    if (chatHistoryRef.value) {
      chatHistoryRef.value.scrollTop = chatHistoryRef.value.scrollHeight
    }
  })
}

const triggerFileInput = () => {
  if (!uploading.value) {
    fileInputRef.value?.click()
  }
}

const handleFileSelect = (event) => {
  const file = event.target.files?.[0]
  if (file) {
    processFile(file)
  }
}

const handleFileDrop = (event) => {
  const file = event.dataTransfer?.files?.[0]
  if (file) {
    processFile(file)
  }
}

const processFile = (file) => {
  const ext = '.' + file.name.split('.').pop()?.toLowerCase()
  if (!allowedExtensions.includes(ext)) {
    uploadError.value = '不支持的文件格式，请上传 PDF/Word/Excel/HTML/Markdown/TXT/PPT 文件'
    setTimeout(() => { uploadError.value = '' }, 3000)
    return
  }
  selectedFile.value = file
  selectedFileName.value = file.name
  uploadError.value = ''
}

const handleFileUpload = async () => {
  if (!selectedFile.value) {
    uploadError.value = '请选择要上传的文件'
    return
  }

  uploading.value = true
  uploadProgress.value = 0
  uploadSuccess.value = false
  uploadError.value = ''

  const formData = new FormData()
  formData.append('file', selectedFile.value)

  try {
    const data = await new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      
      const token = localStorage.getItem('accessToken')
      if (token) {
        xhr.setRequestHeader('Authorization', `Bearer ${token}`)
      }
      
      xhr.upload.addEventListener('progress', (event) => {
        if (event.lengthComputable) {
          uploadProgress.value = Math.round((event.loaded / event.total) * 100)
        }
      })

      xhr.addEventListener('load', () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          try {
            const response = JSON.parse(xhr.responseText)
            if (response.code === 200) {
              resolve(response.data)
            } else {
              reject(new Error(response.message || '上传失败'))
            }
          } catch (e) {
            resolve({})
          }
        } else if (xhr.status === 401) {
          reject(new Error('未授权，请重新登录'))
        } else {
          reject(new Error('上传失败'))
        }
      })

      xhr.addEventListener('error', () => reject(new Error('网络错误')))

      xhr.open('POST', '/api/rag/upload/file')
      xhr.send(formData)
    })

    documentCount.value = data?.documentCount || 0
    uploadSuccess.value = true
    selectedFile.value = null
    selectedFileName.value = ''
    if (fileInputRef.value) {
      fileInputRef.value.value = ''
    }
    setTimeout(() => { uploadSuccess.value = false }, 5000)
    await loadDocuments()
  } catch (error) {
    uploadError.value = error.message || '文件上传失败'
  } finally {
    uploading.value = false
  }
}

const loadDocuments = async () => {
  loadingDocs.value = true
  try {
    const docs = await get('/api/rag/documents')
    documents.value = docs || []
  } catch (error) {
    console.error('加载文档列表失败:', error)
  } finally {
    loadingDocs.value = false
  }
}

const handleDeleteDocument = async (doc) => {
  if (!confirm(`确定要删除文档「${doc.fileName}」吗？`)) {
    return
  }

  deletingDocId.value = doc.docId
  try {
    await del(`/api/rag/documents/${doc.docId}`)
    documents.value = documents.value.filter(d => d.docId !== doc.docId)
  } catch (error) {
    uploadError.value = error.message || '删除失败'
    setTimeout(() => { uploadError.value = '' }, 3000)
  } finally {
    deletingDocId.value = null
  }
}

const closeSSE = () => {
  if (sseConnection) {
    sseConnection.close()
    sseConnection = null
  }
}

const handleAsk = () => {
  if (!question.value.trim() || streaming.value) return
  
  const q = question.value.trim()
  question.value = ''
  askError.value = ''
  
  qaHistory.value.push({
    question: q,
    answer: '',
    thinking: true
  })
  scrollToBottom()

  streaming.value = true

  const url = `/api/rag/ask/stream?question=${encodeURIComponent(q)}`
  let hasError = false

  sseConnection = createSSEConnection(url, {
    onOpen: () => {
      const lastMsg = qaHistory.value[qaHistory.value.length - 1]
      if (lastMsg) {
        lastMsg.thinking = false
      }
      scrollToBottom()
    },
    onMessage: (data) => {
      if (!data) return

      if (data.startsWith('[ERROR]')) {
        hasError = true
        const lastMsg = qaHistory.value[qaHistory.value.length - 1]
        if (lastMsg) {
          lastMsg.thinking = false
          lastMsg.answer = '❌ 请求失败: ' + data.substring(7)
        }
        streaming.value = false
        closeSSE()
        scrollToBottom()
        return
      }

      const lastMsg = qaHistory.value[qaHistory.value.length - 1]
      if (lastMsg) {
        lastMsg.thinking = false
        lastMsg.answer += data
        scrollToBottom()
      }
    },
    onError: (err) => {
      if (!hasError) {
        const lastMsg = qaHistory.value[qaHistory.value.length - 1]
        if (lastMsg) {
          lastMsg.thinking = false
          lastMsg.answer = '❌ 连接失败: ' + (err.message || '网络错误')
        }
        askError.value = err.message || '连接失败'
      }
      streaming.value = false
      closeSSE()
      scrollToBottom()
    },
    onClose: () => {
      const lastMsg = qaHistory.value[qaHistory.value.length - 1]
      if (lastMsg && lastMsg.thinking) {
        lastMsg.thinking = false
      }
      streaming.value = false
      sseConnection = null
    }
  })
}

const sendQuickQuestion = (q) => {
  question.value = q
  handleAsk()
}

const getFileIcon = (fileType) => {
  const type = (fileType || '').toLowerCase()
  if (type.includes('pdf')) return '📕'
  if (type.includes('doc') || type.includes('word')) return '📘'
  if (type.includes('xls') || type.includes('excel')) return '📗'
  if (type.includes('ppt') || type.includes('powerpoint')) return '📙'
  if (type.includes('md')) return '📝'
  if (type.includes('txt')) return '📄'
  if (type.includes('html') || type.includes('htm')) return '🌐'
  return '📄'
}

const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  try {
    const date = new Date(dateStr)
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    const hours = String(date.getHours()).padStart(2, '0')
    const minutes = String(date.getMinutes()).padStart(2, '0')
    return `${month}-${day} ${hours}:${minutes}`
  } catch (e) {
    return dateStr
  }
}

const escapeHtml = (text) => {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

const renderContent = (content) => {
  if (!content) return ''
  let html = escapeHtml(content)
  html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\*(.*?)\*/g, '<em>$1</em>')
  html = html.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')
  html = html.replace(/\n/g, '<br>')
  return html
}

watch(
  () => selectedFile.value,
  (newVal) => {
    if (newVal) {
      handleFileUpload()
    }
  }
)

onMounted(() => {
  loadDocuments()
})
</script>

<style scoped>
.rag-view {
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
  background: linear-gradient(90deg, transparent, var(--cyber-cyan), transparent);
  position: relative;
}

.header-decoration.left::after,
.header-decoration.right::after {
  content: '';
  position: absolute;
  top: -4px;
  width: 9px;
  height: 9px;
  border: 1px solid var(--cyber-cyan);
  transform: rotate(45deg);
  box-shadow: 0 0 10px var(--cyber-cyan);
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

.cyan-glow {
  color: var(--cyber-cyan);
  text-shadow: 0 0 10px var(--cyber-cyan), 0 0 20px var(--cyber-cyan), 0 0 40px var(--cyber-cyan);
}

.content-grid {
  display: grid;
  grid-template-columns: 400px 1fr;
  gap: 24px;
  height: calc(100vh - 200px);
}

.panel {
  display: flex;
  flex-direction: column;
  clip-path: polygon(0 0, calc(100% - 15px) 0, 100% 15px, 100% 100%, 15px 100%, 0 calc(100% - 15px));
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 24px;
  border-bottom: 1px solid rgba(0, 255, 255, 0.2);
  background: rgba(0, 255, 255, 0.05);
}

.panel-icon {
  color: var(--cyber-cyan);
  font-size: 10px;
  text-shadow: 0 0 10px var(--cyber-cyan);
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--cyber-cyan);
  letter-spacing: 2px;
  margin: 0;
}

.panel-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, var(--cyber-cyan), transparent);
}

.refresh-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 255, 255, 0.1);
  border: 1px solid rgba(0, 255, 255, 0.3);
  color: var(--cyber-cyan);
  cursor: pointer;
  transition: all 0.3s ease;
  clip-path: polygon(0 0, calc(100% - 4px) 0, 100% 4px, 100% 100%, 4px 100%, 0 calc(100% - 4px));
}

.refresh-btn:hover {
  background: rgba(0, 255, 255, 0.2);
  box-shadow: 0 0 15px rgba(0, 255, 255, 0.3);
}

.refresh-icon {
  font-size: 16px;
  transition: transform 0.3s ease;
}

.refresh-icon.spinning {
  animation: spin 1s linear infinite;
}

.streaming-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: rgba(255, 0, 255, 0.15);
  border: 1px solid rgba(255, 0, 255, 0.4);
  color: var(--cyber-magenta);
  font-size: 11px;
  letter-spacing: 1px;
}

.pulse-dot {
  width: 6px;
  height: 6px;
  background: var(--cyber-magenta);
  border-radius: 50%;
  animation: pulse 1s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.8); }
}

.panel-body {
  padding: 20px;
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.upload-section {
  margin-bottom: 12px;
}

.form-label {
  display: block;
  font-size: 12px;
  color: var(--cyber-cyan);
  letter-spacing: 1px;
  margin-bottom: 8px;
  text-transform: uppercase;
}

.upload-area {
  border: 2px dashed rgba(0, 255, 255, 0.3);
  padding: 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  background: rgba(0, 255, 255, 0.02);
}

.upload-area:hover {
  border-color: var(--cyber-cyan);
  background: rgba(0, 255, 255, 0.05);
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.upload-icon {
  font-size: 28px;
}

.upload-hint {
  font-size: 12px;
  color: var(--cyber-text-muted);
}

.upload-tips {
  margin-top: 8px;
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 8px 10px;
  background: rgba(0, 255, 255, 0.05);
  border: 1px solid rgba(0, 255, 255, 0.15);
}

.tip-icon {
  color: var(--cyber-cyan);
  font-size: 11px;
  margin-top: 1px;
}

.tip-text {
  font-size: 10px;
  color: var(--cyber-text-muted);
  line-height: 1.5;
}

.upload-progress {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.upload-spinner {
  width: 28px;
  height: 28px;
  border: 3px solid rgba(0, 255, 255, 0.2);
  border-top-color: var(--cyber-cyan);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.upload-text {
  font-size: 12px;
  color: var(--cyber-cyan);
}

.selected-file {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 10px;
  padding: 6px 10px;
  background: rgba(0, 255, 136, 0.1);
  border: 1px solid rgba(0, 255, 136, 0.3);
}

.file-icon {
  color: var(--cyber-green);
  font-weight: 700;
}

.file-name {
  font-size: 11px;
  color: var(--cyber-green);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 16px 0;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: rgba(0, 255, 255, 0.2);
}

.divider-text {
  font-size: 11px;
  color: var(--cyber-text-muted);
  letter-spacing: 1px;
  text-transform: uppercase;
}

.success-msg {
  padding: 10px 14px;
  background: rgba(0, 255, 136, 0.1);
  border: 1px solid rgba(0, 255, 136, 0.3);
  color: var(--cyber-green);
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.success-icon {
  font-weight: 700;
}

.error-msg {
  padding: 10px 14px;
  background: rgba(255, 0, 128, 0.1);
  border: 1px solid rgba(255, 0, 128, 0.3);
  color: var(--cyber-pink);
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.error-icon {
  font-weight: 700;
}

.loading-docs,
.empty-docs {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--cyber-text-muted);
  font-size: 13px;
}

.empty-docs-icon {
  font-size: 40px;
  opacity: 0.5;
}

.doc-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.doc-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  background: rgba(0, 255, 255, 0.03);
  border: 1px solid rgba(0, 255, 255, 0.15);
  transition: all 0.3s ease;
}

.doc-item:hover {
  background: rgba(0, 255, 255, 0.08);
  border-color: rgba(0, 255, 255, 0.3);
}

.doc-info {
  flex: 1;
  min-width: 0;
}

.doc-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--cyber-text-primary);
  margin-bottom: 4px;
}

.doc-name span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-type-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.doc-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--cyber-text-muted);
}

.meta-sep {
  opacity: 0.5;
}

.delete-btn {
  width: 32px;
  height: 32px;
  min-width: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 0, 128, 0.1);
  border: 1px solid rgba(255, 0, 128, 0.3);
  color: var(--cyber-pink);
  cursor: pointer;
  transition: all 0.3s ease;
}

.delete-btn:hover:not(:disabled) {
  background: rgba(255, 0, 128, 0.2);
  box-shadow: 0 0 15px rgba(255, 0, 128, 0.3);
}

.delete-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.qa-panel {
  display: flex;
  flex-direction: column;
}

.chat-history {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.empty-state {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--cyber-text-muted);
  padding: 20px;
}

.empty-icon {
  font-size: 48px;
  color: var(--cyber-cyan);
  opacity: 0.5;
  margin-bottom: 16px;
  text-shadow: 0 0 20px var(--cyber-cyan);
}

.empty-text {
  font-size: 16px;
  color: var(--cyber-cyan);
  margin-bottom: 8px;
  letter-spacing: 2px;
}

.empty-hint {
  font-size: 13px;
  letter-spacing: 1px;
  text-align: center;
  margin-bottom: 20px;
}

.quick-questions {
  width: 100%;
  max-width: 400px;
}

.quick-title {
  font-size: 12px;
  color: var(--cyber-cyan);
  margin-bottom: 12px;
  text-align: center;
}

.quick-btn {
  display: block;
  width: 100%;
  padding: 10px 16px;
  margin-bottom: 8px;
  background: rgba(0, 255, 255, 0.05);
  border: 1px solid rgba(0, 255, 255, 0.2);
  color: var(--cyber-text-primary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.3s ease;
  clip-path: polygon(0 0, calc(100% - 6px) 0, 100% 6px, 100% 100%, 6px 100%, 0 calc(100% - 6px));
  text-align: left;
}

.quick-btn:hover {
  background: rgba(0, 255, 255, 0.1);
  border-color: var(--cyber-cyan);
  color: var(--cyber-cyan);
}

.qa-item {
  margin-bottom: 24px;
}

.question-bubble,
.answer-bubble {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.bubble-icon {
  width: 32px;
  height: 32px;
  min-width: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--cyber-cyan), var(--cyber-blue));
  color: var(--cyber-bg-primary);
  font-weight: 700;
  font-size: 13px;
  clip-path: polygon(0 0, calc(100% - 6px) 0, 100% 6px, 100% 100%, 6px 100%, 0 calc(100% - 6px));
}

.bubble-icon.ai {
  background: linear-gradient(135deg, var(--cyber-magenta), var(--cyber-purple));
}

.bubble-text {
  flex: 1;
  padding: 14px 18px;
  background: rgba(0, 255, 255, 0.05);
  border: 1px solid rgba(0, 255, 255, 0.2);
  line-height: 1.7;
  font-size: 14px;
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.answer-bubble .bubble-text {
  background: rgba(107, 0, 255, 0.08);
  border-color: rgba(255, 0, 255, 0.2);
}

.thinking-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
}

.thinking-dot {
  width: 8px;
  height: 8px;
  background: var(--cyber-magenta);
  border-radius: 50%;
  animation: thinkingBounce 1.4s ease-in-out infinite both;
}

.thinking-dot:nth-child(1) { animation-delay: -0.32s; }
.thinking-dot:nth-child(2) { animation-delay: -0.16s; }

@keyframes thinkingBounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.loading-text {
  font-size: 13px;
  color: var(--cyber-text-muted);
  margin-left: 8px;
}

.answer-content {
  word-break: break-word;
}

.answer-content :deep(strong) {
  color: var(--cyber-cyan);
}

.answer-content :deep(em) {
  color: var(--cyber-magenta);
  font-style: italic;
}

.answer-content :deep(.inline-code) {
  background: rgba(0, 0, 0, 0.4);
  padding: 2px 6px;
  border: 1px solid rgba(0, 255, 255, 0.2);
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  color: var(--cyber-cyan);
}

.input-area {
  padding: 20px 24px;
  border-top: 1px solid rgba(0, 255, 255, 0.2);
  background: var(--cyber-bg-secondary);
}

.input-wrapper {
  display: flex;
  gap: 12px;
}

.question-input {
  flex: 1;
}

.ask-btn {
  min-width: 100px;
  padding: 14px 24px;
}

.input-error {
  margin-top: 12px;
  margin-bottom: 0;
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
  border-width: 2px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
