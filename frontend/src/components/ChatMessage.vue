<template>
  <div class="chat-message" :class="{ 'user-message': message.role === 'user', 'assistant-message': message.role === 'assistant' }">
    <div class="message-wrapper">
      <div v-if="message.role === 'assistant'" class="avatar assistant-avatar">
        <el-avatar :size="40" style="background: #409EFF">
          <el-icon size="20"><MagicStick /></el-icon>
        </el-avatar>
      </div>
      
      <el-card 
        class="message-card" 
        :type="message.role === 'user' ? 'primary' : ''"
        shadow="hover"
      >
        <div v-if="message.role === 'user'" class="message-content plain-text">{{ message.content }}</div>
        <div v-else ref="markdownRef" class="message-content markdown-body" v-html="renderedContent"></div>
      </el-card>
      
      <div v-if="message.role === 'user'" class="avatar user-avatar">
        <el-avatar :size="40" style="background: #67c23a">
          <el-icon size="20"><User /></el-icon>
        </el-avatar>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'
import { ElMessage } from 'element-plus'
import { MagicStick, User, CopyDocument } from '@element-plus/icons-vue'

const props = defineProps({
  message: {
    type: Object,
    required: true
  }
})

const markdownRef = ref(null)

marked.setOptions({
  highlight: function(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang }).value
      } catch (e) {
        console.error('Highlight error:', e)
      }
    }
    return hljs.highlightAuto(code).value
  },
  breaks: true,
  gfm: true
})

const renderer = new marked.Renderer()
renderer.code = function({ text, lang, escaped }) {
  const highlighted = lang && hljs.getLanguage(lang)
    ? hljs.highlight(text, { language: lang }).value
    : hljs.highlightAuto(text).value
  return `<div class="code-block-wrapper">
    <div class="code-header">
      <span class="code-lang">${lang || 'text'}</span>
      <button class="copy-btn" type="button">
        <span class="el-button el-button--small el-button--default copy-btn-el">
          <span class="el-icon"><svg viewBox="0 0 1024 1024" width="1em" height="1em" fill="currentColor"><path d="M768 832H256V192h384l128 128v512z m-64-480H544V256H320v512h384V352z"></path><path d="M640 128H192v640h64V192h384v-64z"></path></svg></span>
          <span>复制</span>
        </span>
      </button>
    </div>
    <pre><code class="hljs ${lang ? 'language-' + lang : ''}">${highlighted}</code></pre>
  </div>`
}

marked.use({ renderer })

const addCopyListeners = () => {
  nextTick(() => {
    if (!markdownRef.value) return
    const buttons = markdownRef.value.querySelectorAll('.copy-btn')
    buttons.forEach(btn => {
      btn.addEventListener('click', () => {
        const code = btn.closest('.code-block-wrapper').querySelector('code')
        if (code) {
          navigator.clipboard.writeText(code.textContent).then(() => {
            ElMessage.success('代码已复制')
            const btnEl = btn.querySelector('.copy-btn-el span:last-child')
            if (btnEl) {
              const originalText = btnEl.textContent
              btnEl.textContent = '已复制'
              setTimeout(() => {
                btnEl.textContent = originalText
              }, 2000)
            }
          }).catch(() => {
            ElMessage.error('复制失败')
          })
        }
      })
    })
  })
}

const renderedContent = computed(() => {
  if (!props.message.content) return ''
  try {
    return DOMPurify.sanitize(marked.parse(props.message.content))
  } catch (e) {
    console.error('Markdown parse error:', e)
    return props.message.content
  }
})

watch(() => props.message.content, () => {
  addCopyListeners()
})

onMounted(() => {
  addCopyListeners()
})
</script>

<style scoped>
.chat-message {
  margin-bottom: 20px;
  display: flex;
}

.user-message {
  justify-content: flex-end;
}

.assistant-message {
  justify-content: flex-start;
}

.message-wrapper {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  max-width: 85%;
}

.user-message .message-wrapper {
  flex-direction: row-reverse;
}

.avatar {
  flex-shrink: 0;
}

.message-card {
  flex: 1;
}

:deep(.message-card .el-card__body) {
  padding: 12px 16px;
}

.user-message :deep(.el-card) {
  background: #409EFF;
  border-color: #409EFF;
}

.user-message :deep(.el-card .el-card__body) {
  color: white;
}

.message-content {
  font-size: 14px;
  line-height: 1.8;
  word-wrap: break-word;
}

.plain-text {
  white-space: pre-wrap;
  margin: 0;
}

:deep(.markdown-body) {
  color: #303133;
}

:deep(.markdown-body p) {
  margin: 0 0 12px 0;
}

:deep(.markdown-body p:last-child) {
  margin-bottom: 0;
}

:deep(.copy-btn) {
  border: none;
  background: none;
  padding: 0;
  cursor: pointer;
}

:deep(.copy-btn-el) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 5px 11px;
  font-size: 12px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
  background: #fff;
  color: #606266;
  transition: all 0.3s;
}

:deep(.copy-btn-el:hover) {
  color: #409EFF;
  border-color: #c6e2ff;
  background: #ecf5ff;
}

:deep(.copy-btn-el .el-icon) {
  display: inline-flex;
  align-items: center;
}
</style>
