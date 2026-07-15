<template>
  <div class="home-container">
    <el-card shadow="never" class="hero-card">
      <div class="hero-content">
        <h1 class="hero-title">Cyber AI Platform</h1>
        <p class="hero-subtitle">赛博AI平台 - 基于Spring AI的企业级AI应用平台</p>
      </div>
    </el-card>

    <el-row :gutter="20" class="features-grid">
      <el-col :xs="24" :sm="12" :md="8" v-for="feature in features" :key="feature.title">
        <el-card shadow="hover" class="feature-card">
          <div class="card-icon">
            <el-icon :size="48" color="#409EFF"><component :is="feature.icon" /></el-icon>
          </div>
          <h3 class="card-title">{{ feature.title }}</h3>
          <p class="card-desc">{{ feature.description }}</p>
        </el-card>
      </el-col>
    </el-row>

    <el-card v-if="!userStore.isLoggedIn" shadow="hover" class="auth-card">
      <el-tabs v-model="activeTab" class="auth-tabs">
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" label-width="80px" @keyup.enter="handleLogin">
            <el-form-item label="用户名">
              <el-input v-model="loginForm.username" placeholder="请输入用户名" prefix-icon="User" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleLogin" :loading="loginLoading" style="width: 100%">登录</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-form :model="registerForm" label-width="80px" @keyup.enter="handleRegister">
            <el-form-item label="用户名">
              <el-input v-model="registerForm.username" placeholder="请输入用户名" prefix-icon="User" />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="registerForm.nickname" placeholder="请输入昵称（可选）" prefix-icon="Avatar" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="registerForm.password" type="password" placeholder="请输入密码（至少6位）" prefix-icon="Lock" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleRegister" :loading="registerLoading" style="width: 100%">注册</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ChatDotRound,
  MagicStick,
  UserFilled,
  Reading,
  Setting,
  Document
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('login')
const loginLoading = ref(false)
const registerLoading = ref(false)

const loginForm = ref({
  username: '',
  password: ''
})

const registerForm = ref({
  username: '',
  password: '',
  nickname: ''
})

const features = [
  {
    title: '智能对话',
    description: 'SSE流式响应、多轮对话记忆',
    icon: ChatDotRound
  },
  {
    title: '智能Agent',
    description: '工具调用、联网搜索、天气查询',
    icon: MagicStick
  },
  {
    title: '多Agent协作',
    description: 'Planner/Researcher/Coder/Critic团队协作',
    icon: UserFilled
  },
  {
    title: 'RAG知识库',
    description: '文档上传、向量检索、智能问答',
    icon: Reading
  },
  {
    title: 'MCP协议',
    description: '模型上下文协议、工具标准化管理',
    icon: Setting
  },
  {
    title: '结构化输出',
    description: 'JSON格式、结构化数据提取',
    icon: Document
  }
]

const handleLogin = async () => {
  if (!loginForm.value.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!loginForm.value.password) {
    ElMessage.warning('请输入密码')
    return
  }

  try {
    loginLoading.value = true
    await userStore.login(loginForm.value.username.trim(), loginForm.value.password)
    ElMessage.success('登录成功')
    router.push('/chat')
  } catch (error) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    loginLoading.value = false
  }
}

const handleRegister = async () => {
  if (!registerForm.value.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!registerForm.value.password || registerForm.value.password.length < 6) {
    ElMessage.warning('密码长度至少为6位')
    return
  }

  try {
    registerLoading.value = true
    await userStore.register(
      registerForm.value.username.trim(),
      registerForm.value.password,
      registerForm.value.nickname.trim()
    )
    await userStore.login(registerForm.value.username.trim(), registerForm.value.password)
    ElMessage.success('注册成功并已登录')
    router.push('/chat')
  } catch (error) {
    ElMessage.error(error.message || '注册失败')
  } finally {
    registerLoading.value = false
  }
}
</script>

<style scoped>
.home-container {
  max-width: 1200px;
  margin: 0 auto;
}

.hero-card {
  margin-bottom: 32px;
  background: linear-gradient(135deg, #409EFF 0%, #66b1ff 100%);
  border: none;
}

:deep(.hero-card .el-card__body) {
  padding: 60px 40px;
  text-align: center;
}

.hero-content {
  color: white;
}

.hero-title {
  font-size: 42px;
  font-weight: 700;
  margin: 0 0 16px 0;
  letter-spacing: 2px;
}

.hero-subtitle {
  font-size: 18px;
  margin: 0;
  opacity: 0.9;
}

.features-grid {
  margin: 0 0 32px 0 !important;
}

.feature-card {
  margin-bottom: 20px;
  text-align: center;
  transition: all 0.3s ease;
}

.feature-card:hover {
  transform: translateY(-4px);
}

:deep(.feature-card .el-card__body) {
  padding: 32px 24px;
}

.card-icon {
  margin-bottom: 20px;
  display: flex;
  justify-content: center;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 12px 0;
}

.card-desc {
  font-size: 14px;
  color: #909399;
  margin: 0;
  line-height: 1.6;
}

.auth-card {
  max-width: 500px;
  margin: 0 auto;
}

:deep(.auth-card .el-card__body) {
  padding: 30px;
}

.auth-tabs {
  margin-top: -10px;
}
</style>
