<template>
  <div class="chat-input-container">
    <div class="input-wrapper">
      <el-input
        ref="inputRef"
        v-model="message"
        type="textarea"
        :rows="2"
        :placeholder="placeholder || '输入你的消息...'"
        :disabled="isLoading || disabled"
        @keydown="handleKeydown"
        resize="none"
      />
      <el-button 
        type="primary" 
        class="send-btn" 
        @click="sendMessage"
        :disabled="isLoading || !message.trim() || disabled"
        :loading="isLoading"
      >
        <el-icon><Position /></el-icon>
      </el-button>
    </div>
    <div class="input-hint">Shift + Enter 换行</div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Position } from '@element-plus/icons-vue'

const props = defineProps({
  placeholder: {
    type: String,
    default: '输入你的消息...'
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['send'])

const message = ref('')
const inputRef = ref(null)
const isLoading = ref(false)

const handleKeydown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

const sendMessage = () => {
  const trimmedMessage = message.value.trim()
  if (!trimmedMessage || isLoading.value || props.disabled) return
  
  isLoading.value = true
  emit('send', trimmedMessage)
  message.value = ''
  
  setTimeout(() => {
    isLoading.value = false
  }, 500)
}
</script>

<style scoped>
.chat-input-container {
  padding: 16px 24px;
  background: #fff;
  border-top: 1px solid #e4e7ed;
}

.input-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.send-btn {
  height: 74px;
  min-width: 80px;
}

.input-hint {
  margin-top: 8px;
  text-align: center;
  font-size: 12px;
  color: #909399;
}
</style>
