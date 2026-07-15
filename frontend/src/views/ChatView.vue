<template>
  <div class="chat-page">
    <el-row :gutter="0" style="height: 100%">
      <el-col :span="6" class="conversation-sidebar">
        <div class="sidebar-header">
          <el-button type="primary" class="new-chat-btn" @click="handleNewConversation">
            <el-icon><Plus /></el-icon>
            新建对话
          </el-button>
        </div>
        <el-scrollbar class="conversation-list">
          <div 
            v-for="conv in chatStore.conversations" 
            :key="conv.id"
            class="conversation-item"
            :class="{ active: chatStore.currentConversation?.id === conv.id }"
            @click="handleSelectConversation(conv.id)"
          >
            <el-icon class="conv-icon"><ChatDotRound /></el-icon>
            <span class="conv-title">{{ conv.title || '新对话' }}</span>
            <el-button 
              type="danger" 
              link 
              class="delete-btn" 
              @click.stop="handleDeleteConversation(conv.id)"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          
          <el-empty v-if="chatStore.conversations.length === 0" description="暂无对话记录" />
        </el-scrollbar>
      </el-col>

      <el-col :span="18" class="chat-main">
        <div class="chat-header">
          <h2 class="chat-title">
            {{ chatStore.currentConversation?.title || '基础聊天' }}
          </h2>
          <el-tag v-if="chatStore.isStreaming" type="primary" effect="light">
            <el-icon class="is-loading"><Loading /></el-icon>
            AI 正在回复...
          </el-tag>
        </div>

        <el-scrollbar ref="messagesRef" class="messages-container">
          <el-empty v-if="chatStore.messages.length === 0" description="开始对话，与AI进行智能对话">
            <template #image>
              <el-icon :size="64" color="#409EFF"><ChatDotRound /></el-icon>
            </template>
          </el-empty>
          
          <ChatMessage 
            v-for="message in chatStore.messages" 
            :key="message.id" 
            :message="message" 
          />
        </el-scrollbar>

        <ChatInput @send="handleSendMessage" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch, onUnmounted } from 'vue'
import { Plus, ChatDotRound, Delete, Loading } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ChatMessage from '@/components/ChatMessage.vue'
import ChatInput from '@/components/ChatInput.vue'
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()
const messagesRef = ref(null)

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTo({ top: messagesRef.value.wrapRef.scrollHeight, behavior: 'smooth' })
    }
  })
}

const handleNewConversation = async () => {
  try {
    await chatStore.createConversation()
  } catch (error) {
    ElMessage.error('创建对话失败')
    console.error('创建对话失败:', error)
  }
}

const handleSelectConversation = async (conversationId) => {
  try {
    await chatStore.selectConversation(conversationId)
    scrollToBottom()
  } catch (error) {
    ElMessage.error('加载对话失败')
    console.error('加载对话失败:', error)
  }
}

const handleDeleteConversation = async (conversationId) => {
  try {
    await ElMessageBox.confirm('确定要删除这个对话吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await chatStore.deleteConversation(conversationId)
    if (chatStore.conversations.length > 0) {
      await handleSelectConversation(chatStore.conversations[0].id)
    }
    ElMessage.success('删除成功')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除对话失败:', error)
    }
  }
}

const handleSendMessage = async (content) => {
  try {
    await chatStore.sendMessage(content)
    scrollToBottom()
  } catch (error) {
    ElMessage.error('发送消息失败')
    console.error('发送消息失败:', error)
  }
}

watch(() => chatStore.messages.length, () => {
  scrollToBottom()
})

watch(() => chatStore.isStreaming, () => {
  scrollToBottom()
})

onMounted(async () => {
  try {
    await chatStore.fetchConversations()
    if (chatStore.conversations.length > 0) {
      await handleSelectConversation(chatStore.conversations[0].id)
    }
  } catch (error) {
    console.error('初始化失败:', error)
  }
})

onUnmounted(() => {
  chatStore.closeSSE()
})
</script>

<style scoped>
.chat-page {
  height: calc(100vh - 108px);
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

.conversation-sidebar {
  background: #f5f7fa;
  border-right: 1px solid #e4e7ed;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.new-chat-btn {
  width: 100%;
}

.conversation-list {
  flex: 1;
  padding: 8px;
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  margin-bottom: 4px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
}

.conversation-item:hover {
  background: #ecf5ff;
}

.conversation-item.active {
  background: #409EFF;
  color: white;
}

.conv-icon {
  flex-shrink: 0;
}

.conv-title {
  flex: 1;
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conversation-item .delete-btn {
  opacity: 0;
  padding: 4px;
}

.conversation-item:hover .delete-btn {
  opacity: 1;
}

.conversation-item.active .delete-btn {
  color: white;
}

.chat-main {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chat-header {
  padding: 16px 24px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chat-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.messages-container {
  flex: 1;
  padding: 24px;
}
</style>
