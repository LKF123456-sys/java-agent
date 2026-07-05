<template>
    <div class="rag-view">
      <div class="page-header">
        <div class="header-decoration left"></div>
        <h1 class="page-title cyan-glow">RAG 检索增强生成</h1>
        <div class="header-decoration right"></div>
      </div>

      <div class="content-grid">
        <div class="panel knowledge-panel cyber-card">
          <div class="panel-header">
            <span class="panel-icon">◆</span>
            <h2 class="panel-title">知识管理</h2>
            <span class="panel-line"></span>
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

            <div class="divider">
              <span class="divider-text">或手动输入</span>
            </div>
            
            <div class="form-group">
              <label class="form-label">Source 名称</label>
              <input 
                v-model="docSource" 
                type="text" 
                class="cyber-input" 
                placeholder="请输入文档来源标识"
              />
            </div>
            
            <div class="form-group">
              <label class="form-label">文档内容</label>
              <textarea 
                v-model="docContent" 
                class="cyber-input textarea" 
                rows="8"
                placeholder="请输入要添加到知识库的文本内容..."
              ></textarea>
            </div>
            
            <button 
              class="cyber-btn cyber-btn-cyan action-btn" 
              @click="handleAddDocument"
              :disabled="addingDoc"
            >
              <span v-if="addingDoc" class="loading-spinner"></span>
              <span v-else class="btn-icon">+</span>
              <span>{{ addingDoc ? '添加中...' : '添加到知识库' }}</span>
            </button>

            <button 
              class="cyber-btn clear-btn" 
              @click="handleClearKnowledge"
              :disabled="clearing"
            >
              <span v-if="clearing" class="loading-spinner"></span>
              <span v-else class="btn-icon">🗑</span>
              <span>{{ clearing ? '清空中...' : '清空知识库' }}</span>
            </button>

            <div v-if="uploadSuccess" class="success-msg">
              <span class="success-icon">✓</span>
              文件上传成功，已生成 {{ documentCount }} 个文档块
            </div>
            <div v-if="addSuccess" class="success-msg">
              <span class="success-icon">✓</span>
              文档已成功添加到知识库
            </div>
            <div v-if="clearSuccess" class="success-msg">
              <span class="success-icon">✓</span>
              知识库已清空
            </div>
            <div v-if="uploadError" class="error-msg">
              <span class="error-icon">✕</span>
              {{ uploadError }}
            </div>
            <div v-if="addError" class="error-msg">
              <span class="error-icon">✕</span>
              {{ addError }}
            </div>
            <div v-if="clearError" class="error-msg">
              <span class="error-icon">✕</span>
              {{ clearError }}
            </div>
          </div>
        </div>

        <div class="panel qa-panel cyber-card">
          <div class="panel-header">
            <span class="panel-icon">◆</span>
            <h2 class="panel-title">智能问答</h2>
            <span class="panel-line"></span>
          </div>
          
          <div class="chat-history cyber-scrollbar" ref="chatHistoryRef">
            <div v-if="qaHistory.length === 0" class="empty-state">
              <div class="empty-icon">◈</div>
              <p class="empty-text">知识库已就绪</p>
              <p class="empty-hint">在下方输入问题，基于知识库内容进行问答</p>
            </div>
            
            <div v-for="(item, index) in qaHistory" :key="index" class="qa-item">
              <div class="question-bubble">
                <span class="bubble-icon">Q</span>
                <p class="bubble-text">{{ item.question }}</p>
              </div>
              <div class="answer-bubble">
                <span class="bubble-icon ai">A</span>
                <div class="bubble-text" v-html="formatAnswer(item.answer)"></div>
              </div>
            </div>

            <div v-if="asking" class="qa-item">
              <div class="answer-bubble loading">
                <span class="bubble-icon ai">A</span>
                <div class="typing-indicator">
                  <span class="typing-dot"></span>
                  <span class="typing-dot"></span>
                  <span class="typing-dot"></span>
                  <span class="loading-text">AI 正在检索知识库...</span>
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
                :disabled="asking"
              />
              <button 
                class="cyber-btn cyber-btn-cyan ask-btn" 
                @click="handleAsk"
                :disabled="asking || !question.trim()"
              >
                <span v-if="asking" class="loading-spinner small"></span>
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
import { ref, nextTick, watch } from 'vue'
import { get, post, del } from '@/utils/request'

const allowedExtensions = ['.pdf', '.doc', '.docx', '.xls', '.xlsx', '.html', '.htm', '.md', '.txt', '.ppt', '.pptx']

const docSource = ref('')
const docContent = ref('')
const addingDoc = ref(false)
const addSuccess = ref(false)
const addError = ref('')

const fileInputRef = ref(null)
const selectedFile = ref(null)
const selectedFileName = ref('')
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadSuccess = ref(false)
const uploadError = ref('')
const documentCount = ref(0)

const clearing = ref(false)
const clearSuccess = ref(false)
const clearError = ref('')

const question = ref('')
const asking = ref(false)
const askError = ref('')
const qaHistory = ref([])
const chatHistoryRef = ref(null)

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

      xhr.open('POST', '/api/rag/upload')
      xhr.send(formData)
    })

    documentCount.value = data?.documentCount || data?.chunkCount || 0
    uploadSuccess.value = true
    selectedFile.value = null
    selectedFileName.value = ''
    if (fileInputRef.value) {
      fileInputRef.value.value = ''
    }
    setTimeout(() => { uploadSuccess.value = false }, 5000)
  } catch (error) {
    uploadError.value = error.message || '文件上传失败'
  } finally {
    uploading.value = false
  }
}

const handleAddDocument = async () => {
  if (!docContent.value.trim()) {
    addError.value = '请输入文档内容'
    return
  }
  if (!docSource.value.trim()) {
    addError.value = '请输入Source名称'
    return
  }
  
  addingDoc.value = true
  addSuccess.value = false
  addError.value = ''
  
  try {
    await post('/api/rag/documents', {
      content: docContent.value,
      source: docSource.value
    })
    addSuccess.value = true
    docContent.value = ''
    docSource.value = ''
    setTimeout(() => { addSuccess.value = false }, 3000)
  } catch (error) {
    addError.value = error.message || '添加文档失败'
  } finally {
    addingDoc.value = false
  }
}

const handleClearKnowledge = async () => {
  if (!confirm('确定要清空知识库吗？此操作不可恢复。')) {
    return
  }

  clearing.value = true
  clearSuccess.value = false
  clearError.value = ''
  
  try {
    await del('/api/rag/documents')
    clearSuccess.value = true
    qaHistory.value = []
    setTimeout(() => { clearSuccess.value = false }, 3000)
  } catch (error) {
    clearError.value = error.message || '清空知识库失败'
  } finally {
    clearing.value = false
  }
}

const handleAsk = async () => {
  if (!question.value.trim() || asking.value) return
  
  const q = question.value.trim()
  question.value = ''
  askError.value = ''
  asking.value = true
  
  qaHistory.value.push({
    question: q,
    answer: ''
  })
  scrollToBottom()
  
  try {
    const answer = await get('/api/rag/ask', { question: q })
    qaHistory.value[qaHistory.value.length - 1].answer = answer || '暂无回答'
  } catch (error) {
    qaHistory.value[qaHistory.value.length - 1].answer = '请求失败: ' + (error.message || '未知错误')
    askError.value = error.message || '请求失败'
  } finally {
    asking.value = false
    scrollToBottom()
  }
}

const formatAnswer = (text) => {
  if (!text) return ''
  return text
    .replace(/\n/g, '<br>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
}

watch(
  () => selectedFile.value,
  (newVal) => {
    if (newVal) {
      handleFileUpload()
    }
  }
)
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

.panel-body {
  padding: 24px;
  flex: 1;
  overflow-y: auto;
}

.upload-section {
  margin-bottom: 16px;
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
  padding: 24px;
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
  font-size: 32px;
}

.upload-hint {
  font-size: 13px;
  color: var(--cyber-text-muted);
}

.upload-tips {
  margin-top: 10px;
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 8px 12px;
  background: rgba(0, 255, 255, 0.05);
  border: 1px solid rgba(0, 255, 255, 0.15);
}

.tip-icon {
  color: var(--cyber-cyan);
  font-size: 12px;
  margin-top: 1px;
}

.tip-text {
  font-size: 11px;
  color: var(--cyber-text-muted);
  line-height: 1.5;
}

.upload-progress {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.upload-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid rgba(0, 255, 255, 0.2);
  border-top-color: var(--cyber-cyan);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.upload-text {
  font-size: 13px;
  color: var(--cyber-cyan);
}

.selected-file {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 12px;
  padding: 8px 12px;
  background: rgba(0, 255, 136, 0.1);
  border: 1px solid rgba(0, 255, 136, 0.3);
}

.file-icon {
  color: var(--cyber-green);
  font-weight: 700;
}

.file-name {
  font-size: 12px;
  color: var(--cyber-green);
}

.divider {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 20px 0;
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

.form-group {
  margin-bottom: 20px;
}

.textarea {
  resize: none;
  min-height: 180px;
  font-family: inherit;
  line-height: 1.6;
}

.action-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 14px;
  margin-bottom: 12px;
}

.clear-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 14px;
  background: linear-gradient(135deg, var(--cyber-pink) 0%, #ff4444 100%);
}

.clear-btn:hover {
  box-shadow: 0 0 30px rgba(255, 0, 128, 0.5);
}

.btn-icon {
  font-size: 18px;
  font-weight: 700;
}

.success-msg {
  margin-top: 16px;
  padding: 12px 16px;
  background: rgba(0, 255, 136, 0.1);
  border: 1px solid rgba(0, 255, 136, 0.3);
  color: var(--cyber-green);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 10px;
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.success-icon {
  font-weight: 700;
}

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

.error-icon {
  font-weight: 700;
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

.answer-bubble.loading .bubble-text {
  padding: 18px;
}

.typing-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
}

.loading-text {
  font-size: 13px;
  color: var(--cyber-text-muted);
  margin-left: 8px;
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
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
