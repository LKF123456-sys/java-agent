<template>
  <div v-if="visible" class="modal-overlay" @click.self="handleClose">
    <div class="modal-container cyber-card">
      <div class="modal-corner tl"></div>
      <div class="modal-corner tr"></div>
      <div class="modal-corner bl"></div>
      <div class="modal-corner br"></div>
      
      <div class="modal-header">
        <span class="header-icon">◆</span>
        <h2 class="modal-title cyan-glow">SYSTEM AUTH</h2>
        <button class="close-btn" @click="handleClose">✕</button>
      </div>

      <div class="tab-bar">
        <button 
          class="tab-btn" 
          :class="{ active: activeTab === 'login' }"
          @click="activeTab = 'login'"
        >
          <span class="tab-icon">▶</span>
          <span>登录</span>
        </button>
        <button 
          class="tab-btn" 
          :class="{ active: activeTab === 'register' }"
          @click="activeTab = 'register'"
        >
          <span class="tab-icon">+</span>
          <span>注册</span>
        </button>
        <div class="tab-indicator" :class="{ 'register': activeTab === 'register' }"></div>
      </div>
      
      <div class="modal-body">
        <div class="form-group">
          <label class="form-label">用户名 *</label>
          <input 
            v-model="username" 
            type="text" 
            class="cyber-input" 
            placeholder="请输入用户名"
            @keyup.enter="handleSubmit"
          />
        </div>
        
        <div class="form-group">
          <label class="form-label">密码 *</label>
          <input 
            v-model="password" 
            type="password" 
            class="cyber-input" 
            :placeholder="activeTab === 'login' ? '请输入密码' : '请输入密码（至少6位）'"
            @keyup.enter="handleSubmit"
          />
        </div>

        <div v-if="activeTab === 'register'" class="form-group">
          <label class="form-label">昵称（可选）</label>
          <input 
            v-model="nickname" 
            type="text" 
            class="cyber-input" 
            placeholder="请输入昵称"
            @keyup.enter="handleSubmit"
          />
        </div>

        <div v-if="errorMsg" class="error-msg">
          <span class="error-icon">✕</span>
          {{ errorMsg }}
        </div>
        <div v-if="successMsg" class="success-msg">
          <span class="success-icon">✓</span>
          {{ successMsg }}
        </div>
      </div>
      
      <div class="modal-footer">
        <button class="cyber-btn cyber-btn-outline" @click="handleClose">取消</button>
        <button 
          class="cyber-btn cyber-btn-cyan" 
          @click="handleSubmit"
          :disabled="loading"
        >
          <span v-if="loading" class="loading-spinner"></span>
          <span v-else class="btn-icon">▶</span>
          <span>{{ loading ? (activeTab === 'login' ? '登录中...' : '注册中...') : (activeTab === 'login' ? '登录系统' : '注册账号') }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['close', 'success'])

const userStore = useUserStore()
const activeTab = ref('login')
const username = ref('')
const password = ref('')
const nickname = ref('')
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

watch(() => props.visible, (val) => {
  if (val) {
    activeTab.value = 'login'
    username.value = ''
    password.value = ''
    nickname.value = ''
    errorMsg.value = ''
    successMsg.value = ''
  }
})

const handleClose = () => {
  if (!loading.value) {
    emit('close')
  }
}

const handleSubmit = async () => {
  if (!username.value.trim()) {
    errorMsg.value = '请输入用户名'
    return
  }
  if (!password.value) {
    errorMsg.value = '请输入密码'
    return
  }
  if (activeTab.value === 'register' && password.value.length < 6) {
    errorMsg.value = '密码长度至少为6位'
    return
  }
  
  loading.value = true
  errorMsg.value = ''
  successMsg.value = ''
  
  try {
    if (activeTab.value === 'login') {
      await userStore.login(username.value.trim(), password.value)
      emit('success')
      emit('close')
    } else {
      await userStore.register(
        username.value.trim(), 
        password.value, 
        nickname.value.trim()
      )
      successMsg.value = '注册成功，正在登录...'
      await userStore.login(username.value.trim(), password.value)
      emit('success')
      emit('close')
    }
  } catch (error) {
    errorMsg.value = error.message || (activeTab.value === 'login' ? '登录失败，请重试' : '注册失败，请重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(10, 10, 31, 0.9);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.modal-container {
  width: 450px;
  position: relative;
  padding: 0;
  clip-path: polygon(0 0, calc(100% - 20px) 0, 100% 20px, 100% 100%, 20px 100%, 0 calc(100% - 20px));
  animation: slideIn 0.3s ease;
}

@keyframes slideIn {
  from { 
    opacity: 0; 
    transform: translateY(-20px) scale(0.95); 
  }
  to { 
    opacity: 1; 
    transform: translateY(0) scale(1); 
  }
}

.modal-container::before {
  background: linear-gradient(90deg, var(--cyber-cyan), var(--cyber-blue), var(--cyber-cyan));
}

.modal-corner {
  position: absolute;
  width: 20px;
  height: 20px;
  border: 2px solid var(--cyber-cyan);
}

.modal-corner.tl {
  top: 10px;
  left: 10px;
  border-right: none;
  border-bottom: none;
}

.modal-corner.tr {
  top: 10px;
  right: 10px;
  border-left: none;
  border-bottom: none;
}

.modal-corner.bl {
  bottom: 10px;
  left: 10px;
  border-right: none;
  border-top: none;
}

.modal-corner.br {
  bottom: 10px;
  right: 10px;
  border-left: none;
  border-top: none;
}

.modal-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 24px 28px;
  border-bottom: 1px solid rgba(0, 255, 255, 0.2);
  background: rgba(0, 255, 255, 0.05);
}

.header-icon {
  color: var(--cyber-cyan);
  font-size: 12px;
  text-shadow: 0 0 10px var(--cyber-cyan);
}

.modal-title {
  flex: 1;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 3px;
  margin: 0;
}

.cyan-glow {
  color: var(--cyber-cyan);
  text-shadow: 0 0 10px var(--cyber-cyan), 0 0 20px var(--cyber-cyan);
}

.close-btn {
  width: 32px;
  height: 32px;
  background: transparent;
  border: 1px solid rgba(0, 255, 255, 0.3);
  color: var(--cyber-text-secondary);
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
  clip-path: polygon(0 0, calc(100% - 6px) 0, 100% 6px, 100% 100%, 6px 100%, 0 calc(100% - 6px));
}

.close-btn:hover {
  border-color: var(--cyber-pink);
  color: var(--cyber-pink);
  background: rgba(255, 0, 128, 0.1);
}

.tab-bar {
  display: flex;
  position: relative;
  padding: 0 28px;
  margin-top: 20px;
}

.tab-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  background: transparent;
  border: none;
  color: var(--cyber-text-muted);
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  letter-spacing: 1px;
  position: relative;
  z-index: 1;
}

.tab-btn.active {
  color: var(--cyber-cyan);
  text-shadow: 0 0 10px var(--cyber-cyan);
}

.tab-icon {
  font-size: 12px;
}

.tab-indicator {
  position: absolute;
  bottom: 0;
  left: 28px;
  width: calc(50% - 14px);
  height: 2px;
  background: var(--cyber-cyan);
  box-shadow: 0 0 10px var(--cyber-cyan);
  transition: all 0.3s ease;
}

.tab-indicator.register {
  left: calc(50% + 14px);
}

.modal-body {
  padding: 28px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group:last-child {
  margin-bottom: 0;
}

.form-label {
  display: block;
  font-size: 12px;
  color: var(--cyber-cyan);
  letter-spacing: 1px;
  margin-bottom: 8px;
  text-transform: uppercase;
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

.modal-footer {
  display: flex;
  gap: 12px;
  padding: 20px 28px;
  border-top: 1px solid rgba(0, 255, 255, 0.2);
  background: var(--cyber-bg-secondary);
}

.modal-footer .cyber-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 14px;
}

.btn-icon {
  font-size: 14px;
}

.loading-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
