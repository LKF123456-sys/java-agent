<template>
  <div class="chat-message" :class="{ 'user-message': message.role === 'user', 'assistant-message': message.role === 'assistant' }">
    <div class="message-wrapper">
      <div v-if="message.role === 'assistant'" class="avatar assistant-avatar">
        <span class="avatar-icon">AI</span>
      </div>
      
      <div class="message-bubble">
        <div class="corner corner-tl"></div>
        <div class="corner corner-tr"></div>
        <div class="corner corner-bl"></div>
        <div class="corner corner-br"></div>
        <div v-if="message.role === 'user'" class="message-content plain-text">{{ message.content }}</div>
        <div v-else class="message-content markdown-body" v-html="renderedContent"></div>
      </div>
      
      <div v-if="message.role === 'user'" class="avatar user-avatar">
        <span class="avatar-icon">U</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'

const props = defineProps({
  message: {
    type: Object,
    required: true,
    validator: (value) => {
      return value.role && ['user', 'assistant'].includes(value.role) && typeof value.content === 'string'
    }
  }
})

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
const originalCode = renderer.code.bind(renderer)
renderer.code = function({ text, lang, escaped }) {
  const highlighted = lang && hljs.getLanguage(lang)
    ? hljs.highlight(text, { language: lang }).value
    : hljs.highlightAuto(text).value
  return `<div class="code-block-wrapper">
    <div class="code-header">
      <span class="code-lang">${lang || 'text'}</span>
      <button class="copy-btn" onclick="navigator.clipboard.writeText(this.closest('.code-block-wrapper').querySelector('code').textContent)">复制</button>
    </div>
    <pre><code class="hljs ${lang ? 'language-' + lang : ''}">${highlighted}</code></pre>
  </div>`
}

marked.use({ renderer })

const renderedContent = computed(() => {
  if (!props.message.content) return ''
  try {
    return DOMPurify.sanitize(marked.parse(props.message.content))
  } catch (e) {
    console.error('Markdown parse error:', e)
    return props.message.content
  }
})
</script>

<style scoped>
.chat-message {
  margin-bottom: 24px;
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
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  clip-path: polygon(8px 0, 100% 0, calc(100% - 8px) 100%, 0 100%);
}

.assistant-avatar {
  background: linear-gradient(135deg, var(--cyber-cyan), var(--cyber-blue));
  box-shadow: var(--cyber-shadow-cyan);
}

.user-avatar {
  background: linear-gradient(135deg, var(--cyber-magenta), var(--cyber-pink));
  box-shadow: var(--cyber-shadow-magenta);
}

.avatar-icon {
  font-size: 12px;
  font-weight: 700;
  color: white;
  letter-spacing: 1px;
}

.message-bubble {
  position: relative;
  padding: 16px 20px;
  min-width: 60px;
}

.assistant-message .message-bubble {
  background: linear-gradient(135deg, rgba(107, 0, 255, 0.2), rgba(107, 0, 255, 0.1));
  border: 1px solid var(--cyber-cyan);
  box-shadow: var(--cyber-shadow-cyan);
  clip-path: polygon(0 0, calc(100% - 12px) 0, 100% 12px, 100% 100%, 12px 100%, 0 calc(100% - 12px));
}

.user-message .message-bubble {
  background: linear-gradient(135deg, var(--cyber-magenta), var(--cyber-pink));
  border: 1px solid var(--cyber-magenta);
  box-shadow: var(--cyber-shadow-magenta);
  clip-path: polygon(12px 0, 100% 0, 100% calc(100% - 12px), calc(100% - 12px) 100%, 0 100%, 0 12px);
}

.corner {
  position: absolute;
  width: 8px;
  height: 8px;
  background: var(--cyber-cyan);
}

.user-message .corner {
  background: var(--cyber-magenta);
}

.corner-tl {
  top: 0;
  left: 0;
  clip-path: polygon(0 0, 100% 0, 0 100%);
}

.corner-tr {
  top: 0;
  right: 0;
  clip-path: polygon(0 0, 100% 0, 100% 100%);
}

.corner-bl {
  bottom: 0;
  left: 0;
  clip-path: polygon(0 0, 0 100%, 100% 100%);
}

.corner-br {
  bottom: 0;
  right: 0;
  clip-path: polygon(100% 0, 0 100%, 100% 100%);
}

.message-content {
  font-size: 14px;
  line-height: 1.8;
  color: var(--cyber-text-primary);
  word-wrap: break-word;
  letter-spacing: 0.5px;
}

.plain-text {
  white-space: pre-wrap;
  color: white;
}

.assistant-message .message-content {
  color: var(--cyber-text-primary);
}

.message-bubble::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--cyber-cyan), transparent);
  opacity: 0.5;
}

.user-message .message-bubble::before {
  background: linear-gradient(90deg, transparent, var(--cyber-magenta), transparent);
}

:deep(.markdown-body) {
  color: #e0e0e0;
}

:deep(.markdown-body p) {
  margin: 0 0 12px 0;
}

:deep(.markdown-body p:last-child) {
  margin-bottom: 0;
}

:deep(.markdown-body h1),
:deep(.markdown-body h2),
:deep(.markdown-body h3),
:deep(.markdown-body h4),
:deep(.markdown-body h5),
:deep(.markdown-body h6) {
  color: var(--cyber-cyan);
  margin: 16px 0 12px 0;
  font-weight: 600;
  text-shadow: 0 0 10px rgba(0, 255, 255, 0.3);
}

:deep(.markdown-body h1) { font-size: 1.5em; border-bottom: 1px solid rgba(0, 255, 255, 0.3); padding-bottom: 8px; }
:deep(.markdown-body h2) { font-size: 1.3em; border-bottom: 1px solid rgba(0, 255, 255, 0.2); padding-bottom: 6px; }
:deep(.markdown-body h3) { font-size: 1.15em; }
:deep(.markdown-body h4) { font-size: 1.05em; }

:deep(.markdown-body strong) {
  color: var(--cyber-magenta);
  font-weight: 600;
  text-shadow: 0 0 8px rgba(255, 0, 255, 0.3);
}

:deep(.markdown-body em) {
  color: var(--cyber-cyan);
  font-style: italic;
}

:deep(.markdown-body code:not(pre code)) {
  background: rgba(255, 0, 255, 0.15);
  color: var(--cyber-magenta);
  padding: 2px 8px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 0.9em;
  border: 1px solid rgba(255, 0, 255, 0.3);
}

:deep(.markdown-body ul),
:deep(.markdown-body ol) {
  margin: 8px 0;
  padding-left: 24px;
}

:deep(.markdown-body li) {
  margin: 4px 0;
}

:deep(.markdown-body li::marker) {
  color: var(--cyber-cyan);
}

:deep(.markdown-body blockquote) {
  margin: 12px 0;
  padding: 12px 16px;
  border-left: 4px solid var(--cyber-purple);
  background: rgba(107, 0, 255, 0.1);
  border-radius: 0 8px 8px 0;
  color: #b0b0d0;
}

:deep(.markdown-body a) {
  color: var(--cyber-cyan);
  text-decoration: none;
  border-bottom: 1px dashed var(--cyber-cyan);
  transition: all 0.3s ease;
}

:deep(.markdown-body a:hover) {
  color: var(--cyber-magenta);
  border-bottom-color: var(--cyber-magenta);
  text-shadow: 0 0 8px rgba(255, 0, 255, 0.5);
}

:deep(.markdown-body hr) {
  border: none;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--cyber-cyan), var(--cyber-magenta), transparent);
  margin: 20px 0;
}

:deep(.markdown-body table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  border: 1px solid rgba(0, 255, 255, 0.3);
}

:deep(.markdown-body th),
:deep(.markdown-body td) {
  border: 1px solid rgba(0, 255, 255, 0.2);
  padding: 8px 12px;
  text-align: left;
}

:deep(.markdown-body th) {
  background: rgba(0, 255, 255, 0.1);
  color: var(--cyber-cyan);
  font-weight: 600;
}

:deep(.code-block-wrapper) {
  margin: 12px 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(0, 255, 255, 0.4);
  box-shadow: 0 0 20px rgba(0, 255, 255, 0.1);
}

:deep(.code-header) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: linear-gradient(90deg, rgba(0, 255, 255, 0.15), rgba(107, 0, 255, 0.15));
  border-bottom: 1px solid rgba(0, 255, 255, 0.3);
}

:deep(.code-lang) {
  color: var(--cyber-cyan);
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 1px;
}

:deep(.copy-btn) {
  background: transparent;
  border: 1px solid var(--cyber-cyan);
  color: var(--cyber-cyan);
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
}

:deep(.copy-btn:hover) {
  background: var(--cyber-cyan);
  color: #000;
  box-shadow: 0 0 10px var(--cyber-cyan);
}

:deep(.code-block-wrapper pre) {
  margin: 0;
  padding: 16px;
  background: #0a0a1a;
  overflow-x: auto;
}

:deep(.code-block-wrapper code) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  background: transparent !important;
  border: none !important;
  padding: 0 !important;
  color: #e0e0e0;
}

:deep(.hljs-keyword),
:deep(.hljs-selector-tag),
:deep(.hljs-built_in),
:deep(.hljs-name),
:deep(.hljs-tag) {
  color: #ff79c6;
  font-weight: 600;
}

:deep(.hljs-string),
:deep(.hljs-title),
:deep(.hljs-section),
:deep(.hljs-attribute),
:deep(.hljs-literal),
:deep(.hljs-template-tag),
:deep(.hljs-template-variable),
:deep(.hljs-type),
:deep(.hljs-addition) {
  color: #f1fa8c;
}

:deep(.hljs-comment),
:deep(.hljs-deletion),
:deep(.hljs-meta) {
  color: #6272a4;
  font-style: italic;
}

:deep(.hljs-number),
:deep(.hljs-regexp),
:deep(.hljs-symbol),
:deep(.hljs-variable),
:deep(.hljs-bullet),
:deep(.hljs-link) {
  color: #bd93f9;
}

:deep(.hljs-function),
:deep(.hljs-title.function_) {
  color: #50fa7b;
}

:deep(.hljs-params),
:deep(.hljs-attr) {
  color: #ffb86c;
}

:deep(.hljs-built_in),
:deep(.hljs-class .hljs-title) {
  color: #8be9fd;
}
</style>
