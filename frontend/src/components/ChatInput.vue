<template>
  <div class="chat-input-container">
    <div class="input-wrapper">
      <div class="input-frame">
        <div class="corner corner-tl"></div>
        <div class="corner corner-tr"></div>
        <div class="corner corner-bl"></div>
        <div class="corner corner-br"></div>
        <textarea
          ref="inputRef"
          v-model="message"
          class="cyber-input textarea-input"
          placeholder="输入你的消息..."
          rows="1"
          @keydown="handleKeydown"
          @input="autoResize"
        ></textarea>
      </div>
      <button 
        class="cyber-btn send-btn" 
        :class="{ 'cyber-btn-cyan': true, 'loading': isLoading }"
        @click="sendMessage"
        :disabled="isLoading || !message.trim()"
      >
        <span v-if="!isLoading" class="btn-text">发送</span>
        <span v-else class="btn-loading">
          <span class="typing-dot"></span>
          <span class="typing-dot"></span>
          <span class="typing-dot"></span>
        </span>
      </button>
    </div>
    <div class="input-hint">Shift + Enter 换行</div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const emit = defineEmits(['send'])

const message = ref('')
const inputRef = ref(null)
const isLoading = ref(false)

const autoResize = () => {
  const textarea = inputRef.value
  if (textarea) {
    textarea.style.height = 'auto'
    textarea.style.height = Math.min(textarea.scrollHeight, 200) + 'px'
  }
}

const handleKeydown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

const sendMessage = () => {
  const trimmedMessage = message.value.trim()
  if (!trimmedMessage || isLoading.value) return
  
  isLoading.value = true
  emit('send', trimmedMessage)
  message.value = ''
  
  setTimeout(() => {
    if (inputRef.value) {
      inputRef.value.style.height = 'auto'
    }
    isLoading.value = false
  }, 500)
}

watch(message, () => {
  autoResize()
})
</script>

<style scoped>
.chat-input-container {
  padding: 16px 24px;
  background: var(--cyber-bg-secondary);
  border-top: 1px solid var(--cyber-border);
  position: relative;
}

.chat-input-container::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--cyber-cyan), var(--cyber-magenta), var(--cyber-cyan), transparent);
}

.input-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.input-frame {
  flex: 1;
  position: relative;
}

.textarea-input {
  resize: none;
  min-height: 52px;
  max-height: 200px;
  line-height: 1.6;
  font-family: var(--cyber-font-family);
  padding-right: 40px;
}

.corner {
  position: absolute;
  width: 10px;
  height: 10px;
  z-index: 1;
  pointer-events: none;
}

.corner-tl {
  top: -1px;
  left: -1px;
  border-top: 2px solid var(--cyber-cyan);
  border-left: 2px solid var(--cyber-cyan);
  box-shadow: -3px -3px 10px rgba(0, 255, 255, 0.3);
}

.corner-tr {
  top: -1px;
  right: -1px;
  border-top: 2px solid var(--cyber-cyan);
  border-right: 2px solid var(--cyber-cyan);
  box-shadow: 3px -3px 10px rgba(0, 255, 255, 0.3);
}

.corner-bl {
  bottom: -1px;
  left: -1px;
  border-bottom: 2px solid var(--cyber-magenta);
  border-left: 2px solid var(--cyber-magenta);
  box-shadow: -3px 3px 10px rgba(255, 0, 255, 0.3);
}

.corner-br {
  bottom: -1px;
  right: -1px;
  border-bottom: 2px solid var(--cyber-magenta);
  border-right: 2px solid var(--cyber-magenta);
  box-shadow: 3px 3px 10px rgba(255, 0, 255, 0.3);
}

.send-btn {
  height: 52px;
  padding: 0 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 90px;
  flex-shrink: 0;
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

.send-btn:disabled:hover {
  box-shadow: none;
  transform: none;
}

.btn-text {
  letter-spacing: 2px;
}

.btn-loading {
  display: flex;
  align-items: center;
  gap: 4px;
}

.input-hint {
  margin-top: 10px;
  text-align: center;
  font-size: 11px;
  color: var(--cyber-text-muted);
  letter-spacing: 1px;
}
</style>
