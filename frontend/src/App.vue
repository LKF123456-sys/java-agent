<template>
  <el-container style="height: 100vh">
    <el-aside :width="isCollapse ? '64px' : '240px'" style="transition: width 0.3s; background: #fff; border-right: 1px solid #e4e7ed; overflow: hidden">
      <div class="logo-container" style="height: 60px; display: flex; align-items: center; justify-content: center; border-bottom: 1px solid #e4e7ed; padding: 0 16px">
        <el-icon v-if="isCollapse" size="24" color="#409EFF"><Cpu /></el-icon>
        <span v-else style="font-size: 18px; font-weight: 700; color: #409EFF; letter-spacing: 1px">Cyber AI</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
        style="border-right: none"
      >
        <el-menu-item index="/">
          <el-icon><House /></el-icon>
          <template #title>首页</template>
        </el-menu-item>
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>基础聊天</template>
        </el-menu-item>
        <el-menu-item index="/agent">
          <el-icon><MagicStick /></el-icon>
          <template #title>智能Agent</template>
        </el-menu-item>
        <el-menu-item index="/search-agent">
          <el-icon><Search /></el-icon>
          <template #title>联网搜索</template>
        </el-menu-item>
        <el-menu-item index="/multi-agent">
          <el-icon><UserFilled /></el-icon>
          <template #title>多Agent协作</template>
        </el-menu-item>
        <el-menu-item index="/memory">
          <el-icon><Memo /></el-icon>
          <template #title>记忆对话</template>
        </el-menu-item>
        <el-menu-item index="/rag">
          <el-icon><Reading /></el-icon>
          <template #title>RAG知识库</template>
        </el-menu-item>
        <el-menu-item index="/structured">
          <el-icon><Document /></el-icon>
          <template #title>结构化输出</template>
        </el-menu-item>
        <el-menu-item index="/mcp">
          <el-icon><Setting /></el-icon>
          <template #title>MCP工具</template>
        </el-menu-item>
        <el-menu-item index="/tools">
          <el-icon><Tools /></el-icon>
          <template #title>工具演示</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header style="background: #fff; border-bottom: 1px solid #e4e7ed; display: flex; align-items: center; justify-content: space-between; padding: 0 24px; height: 60px">
        <div style="display: flex; align-items: center; gap: 16px">
          <el-button text @click="toggleCollapse">
            <el-icon size="20"><Fold v-if="!isCollapse" /><Expand v-else /></el-icon>
          </el-button>
          <h1 style="font-size: 20px; font-weight: 600; margin: 0; color: #303133">Cyber AI Platform</h1>
        </div>
        <div style="display: flex; align-items: center; gap: 16px">
          <template v-if="userStore.isLoggedIn">
            <el-dropdown>
              <span style="display: flex; align-items: center; gap: 8px; cursor: pointer">
                <el-avatar :size="32" style="background: #409EFF">
                  <el-icon><User /></el-icon>
                </el-avatar>
                <span>{{ userStore.user?.nickname || userStore.user?.username || '用户' }}</span>
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="handleLogout">
                    <el-icon><SwitchButton /></el-icon>
                    退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <el-button type="primary" @click="$router.push('/')">登录</el-button>
          </template>
        </div>
      </el-header>

      <el-main style="padding: 24px; background: #f5f7fa; overflow-y: auto">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'highlight.js/styles/atom-one-dark.css'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapse = ref(false)

const activeMenu = computed(() => route.path)

onMounted(async () => {
  if (userStore.isLoggedIn) {
    try {
      await userStore.getInfo()
    } catch (e) {
      // getInfo失败会在request拦截器中处理401并跳转到首页
    }
  }
})

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/')
  } catch {
  }
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

#app {
  width: 100%;
  height: 100vh;
}

.markdown-body {
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
}

.markdown-body p {
  margin: 0 0 12px 0;
}

.markdown-body p:last-child {
  margin-bottom: 0;
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4,
.markdown-body h5,
.markdown-body h6 {
  margin: 16px 0 12px 0;
  font-weight: 600;
  color: #303133;
}

.markdown-body h1 { font-size: 1.5em; border-bottom: 1px solid #e4e7ed; padding-bottom: 8px; }
.markdown-body h2 { font-size: 1.3em; border-bottom: 1px solid #ebeef5; padding-bottom: 6px; }
.markdown-body h3 { font-size: 1.15em; }
.markdown-body h4 { font-size: 1.05em; }

.markdown-body strong {
  font-weight: 600;
  color: #409EFF;
}

.markdown-body em {
  color: #67c23a;
  font-style: italic;
}

.markdown-body code:not(pre code) {
  background: #f5f7fa;
  color: #e6a23c;
  padding: 2px 8px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 0.9em;
  border: 1px solid #e4e7ed;
}

.markdown-body ul,
.markdown-body ol {
  margin: 8px 0;
  padding-left: 24px;
}

.markdown-body li {
  margin: 4px 0;
}

.markdown-body blockquote {
  margin: 12px 0;
  padding: 12px 16px;
  border-left: 4px solid #409EFF;
  background: #ecf5ff;
  border-radius: 0 4px 4px 0;
  color: #606266;
}

.markdown-body a {
  color: #409EFF;
  text-decoration: none;
}

.markdown-body a:hover {
  color: #66b1ff;
  text-decoration: underline;
}

.markdown-body hr {
  border: none;
  height: 1px;
  background: #e4e7ed;
  margin: 20px 0;
}

.markdown-body table {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  border: 1px solid #e4e7ed;
}

.markdown-body th,
.markdown-body td {
  border: 1px solid #e4e7ed;
  padding: 8px 12px;
  text-align: left;
}

.markdown-body th {
  background: #f5f7fa;
  font-weight: 600;
}

.code-block-wrapper {
  margin: 12px 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e4e7ed;
}

.code-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}

.code-lang {
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.code-block-wrapper pre {
  margin: 0;
  padding: 16px;
  background: #282c34;
  overflow-x: auto;
}

.code-block-wrapper code {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
}
</style>
