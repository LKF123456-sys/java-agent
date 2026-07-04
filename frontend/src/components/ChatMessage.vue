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
        <div class="message-content">{{ message.content }}</div>
      </div>
      
      <div v-if="message.role === 'user'" class="avatar user-avatar">
        <span class="avatar-icon">U</span>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  message: {
    type: Object,
    required: true,
    validator: (value) => {
      return value.role && ['user', 'assistant'].includes(value.role) && typeof value.content === 'string'
    }
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
  max-width: 80%;
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
  white-space: pre-wrap;
  word-wrap: break-word;
  letter-spacing: 0.5px;
}

.assistant-message .message-content {
  color: var(--cyber-text-primary);
}

.user-message .message-content {
  color: white;
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
</style>
