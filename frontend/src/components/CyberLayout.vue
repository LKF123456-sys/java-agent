<template>
  <div class="cyber-layout">
    <aside class="cyber-sidebar">
      <div class="sidebar-header">
        <h1 class="cyber-title cyber-glow-text">CYBER AI</h1>
      </div>
      
      <nav class="sidebar-nav">
        <router-link to="/" class="nav-item" :class="{ active: $route.path === '/' }">
          <span class="nav-icon">◆</span>
          <span class="nav-text">首页</span>
        </router-link>
        <router-link to="/chat" class="nav-item" :class="{ active: $route.path === '/chat' }">
          <span class="nav-icon">◆</span>
          <span class="nav-text">基础聊天</span>
        </router-link>
        <router-link to="/memory" class="nav-item" :class="{ active: $route.path === '/memory' }">
          <span class="nav-icon">◆</span>
          <span class="nav-text">记忆对话</span>
        </router-link>
        <router-link to="/rag" class="nav-item" :class="{ active: $route.path === '/rag' }">
          <span class="nav-icon">◆</span>
          <span class="nav-text">RAG检索</span>
        </router-link>
        <router-link to="/agent" class="nav-item" :class="{ active: $route.path === '/agent' }">
          <span class="nav-icon">◆</span>
          <span class="nav-text">Agent智能体</span>
        </router-link>
        <router-link to="/multi-agent" class="nav-item" :class="{ active: $route.path === '/multi-agent' }">
          <span class="nav-icon purple-icon">◆</span>
          <span class="nav-text">多Agent协作</span>
        </router-link>
        <router-link to="/mcp" class="nav-item" :class="{ active: $route.path === '/mcp' }">
          <span class="nav-icon blue-icon">◆</span>
          <span class="nav-text">MCP工具</span>
        </router-link>
        <router-link to="/structured" class="nav-item" :class="{ active: $route.path === '/structured' }">
          <span class="nav-icon">◆</span>
          <span class="nav-text">结构化输出</span>
        </router-link>
        <router-link to="/tools" class="nav-item" :class="{ active: $route.path === '/tools' }">
          <span class="nav-icon">◆</span>
          <span class="nav-text">工具调用</span>
        </router-link>
      </nav>
      
      <div class="sidebar-footer">
        <div class="user-section">
          <div v-if="userStore.isLoggedIn" class="user-info">
            <div class="user-avatar">{{ userInitial }}</div>
            <div class="user-details">
              <span class="user-name">{{ userStore.user?.nickname || userStore.user?.username }}</span>
              <span class="user-role" :class="roleClass">{{ roleLabel }}</span>
            </div>
            <button class="logout-btn" @click="handleLogout" title="登出">⏻</button>
          </div>
          <button v-else class="cyber-btn cyber-btn-cyan login-btn" @click="showLoginModal = true">
            <span class="btn-icon">▶</span>
            <span>登录</span>
          </button>
        </div>
        
        <div class="status-indicator">
          <span class="status-dot"></span>
          <span class="status-text">Spring AI v1.0</span>
        </div>
      </div>
    </aside>
    
    <main class="cyber-main">
      <div class="gradient-bar"></div>
      <div class="main-content cyber-scrollbar">
        <slot></slot>
      </div>
    </main>

    <LoginModal 
      :visible="showLoginModal" 
      @close="showLoginModal = false"
      @success="handleLoginSuccess"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import LoginModal from './LoginModal.vue'

const userStore = useUserStore()
const showLoginModal = ref(false)

onMounted(async () => {
  if (userStore.token) {
    try {
      await userStore.getInfo()
    } catch (error) {
      console.error('Token无效，已自动登出')
    }
  }
})

const userInitial = computed(() => {
  const name = userStore.user?.nickname || userStore.user?.username || 'G'
  return name.charAt(0).toUpperCase()
})

const roleClass = computed(() => {
  return {
    'admin-badge': userStore.isAdmin,
    'user-badge': !userStore.isAdmin
  }
})

const roleLabel = computed(() => {
  return userStore.isAdmin ? 'ADMIN' : (userStore.user?.role?.toUpperCase() || 'USER')
})

const handleLogout = () => {
  userStore.logout()
}

const handleLoginSuccess = () => {
  console.log('登录成功')
}
</script>

<style scoped>
.cyber-layout {
  display: flex;
  min-height: 100vh;
  background: var(--cyber-bg-primary);
}

.cyber-sidebar {
  width: 260px;
  min-width: 260px;
  background: var(--cyber-bg-secondary);
  border-right: 1px solid var(--cyber-border);
  display: flex;
  flex-direction: column;
  position: relative;
}

.cyber-sidebar::before {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 1px;
  height: 100%;
  background: linear-gradient(180deg, var(--cyber-cyan), var(--cyber-magenta), var(--cyber-cyan));
  opacity: 0.5;
}

.sidebar-header {
  padding: 30px 20px;
  border-bottom: 1px solid var(--cyber-border);
  text-align: center;
}

.cyber-title {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 3px;
  color: var(--cyber-cyan);
  margin: 0;
}

.sidebar-nav {
  flex: 1;
  padding: 20px 15px;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  margin-bottom: 8px;
  color: var(--cyber-text-secondary);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  border: 1px solid transparent;
  transition: all 0.3s ease;
  position: relative;
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.nav-icon {
  color: var(--cyber-text-muted);
  font-size: 8px;
  transition: all 0.3s ease;
}

.nav-icon.purple-icon {
  color: var(--cyber-magenta);
}

.nav-icon.blue-icon {
  color: var(--cyber-blue);
}

.nav-item:hover {
  background: rgba(0, 255, 255, 0.05);
  color: var(--cyber-cyan);
}

.nav-item:hover .nav-icon {
  color: var(--cyber-cyan);
}

.nav-item.active {
  background: rgba(0, 255, 255, 0.1);
  border-color: var(--cyber-cyan);
  color: var(--cyber-cyan);
  box-shadow: var(--cyber-shadow-cyan);
}

.nav-item.active .nav-icon {
  color: var(--cyber-cyan);
  text-shadow: 0 0 10px var(--cyber-cyan);
}

.sidebar-footer {
  padding: 20px;
  border-top: 1px solid var(--cyber-border);
}

.user-section {
  margin-bottom: 16px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: rgba(0, 255, 255, 0.05);
  border: 1px solid rgba(0, 255, 255, 0.2);
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.user-avatar {
  width: 40px;
  height: 40px;
  min-width: 40px;
  background: linear-gradient(135deg, var(--cyber-cyan), var(--cyber-blue));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  color: var(--cyber-bg-primary);
  clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
}

.user-details {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow: hidden;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--cyber-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-role {
  font-size: 10px;
  letter-spacing: 1px;
  padding: 2px 8px;
  align-self: flex-start;
}

.admin-badge {
  background: rgba(255, 0, 255, 0.2);
  color: var(--cyber-magenta);
  border: 1px solid var(--cyber-magenta);
}

.user-badge {
  background: rgba(0, 255, 136, 0.1);
  color: var(--cyber-green);
  border: 1px solid var(--cyber-green);
}

.logout-btn {
  width: 32px;
  height: 32px;
  background: transparent;
  border: 1px solid rgba(255, 0, 128, 0.3);
  color: var(--cyber-pink);
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
  clip-path: polygon(0 0, calc(100% - 6px) 0, 100% 6px, 100% 100%, 6px 100%, 0 calc(100% - 6px));
}

.logout-btn:hover {
  background: rgba(255, 0, 128, 0.1);
  border-color: var(--cyber-pink);
  box-shadow: 0 0 15px rgba(255, 0, 128, 0.3);
}

.login-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 14px;
}

.login-btn .btn-icon {
  font-size: 14px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: rgba(0, 255, 136, 0.05);
  border: 1px solid rgba(0, 255, 136, 0.3);
  border-radius: 4px;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--cyber-green);
  animation: pulse-glow 2s ease-in-out infinite;
  box-shadow: 0 0 10px var(--cyber-green);
}

.status-text {
  font-size: 12px;
  color: var(--cyber-green);
  letter-spacing: 1px;
}

.cyber-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.gradient-bar {
  height: 3px;
  background: linear-gradient(90deg, var(--cyber-cyan), var(--cyber-purple), var(--cyber-magenta), var(--cyber-cyan));
  background-size: 200% 100%;
  animation: gradient-flow 3s linear infinite;
}

@keyframes gradient-flow {
  0% { background-position: 0% 50%; }
  100% { background-position: 200% 50%; }
}

.main-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}
</style>
