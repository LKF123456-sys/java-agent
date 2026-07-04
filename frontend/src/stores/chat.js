import { defineStore } from 'pinia'
import { ref } from 'vue'
import { get, post, del, createSSEConnection } from '../utils/request'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref([])
  const currentConversation = ref(null)
  const messages = ref([])
  const isStreaming = ref(false)
  let sseConnection = null

  const fetchConversations = async () => {
    try {
      const data = await get('/api/chat/conversations')
      conversations.value = data || []
      return data
    } catch (error) {
      console.error('获取会话列表失败:', error)
      throw error
    }
  }

  const createConversation = async (title = '新对话') => {
    try {
      const data = await post('/api/chat/conversations', { title })
      conversations.value.unshift(data)
      currentConversation.value = data
      messages.value = []
      return data
    } catch (error) {
      console.error('创建会话失败:', error)
      throw error
    }
  }

  const selectConversation = async (conversationId) => {
    try {
      const conversation = conversations.value.find(c => c.id === conversationId)
      if (conversation) {
        currentConversation.value = conversation
      }
      const data = await get(`/api/chat/conversations/${conversationId}/messages`)
      messages.value = (data || []).map(m => ({
        id: m.id,
        role: m.role,
        content: m.content
      }))
      return data
    } catch (error) {
      console.error('加载会话消息失败:', error)
      throw error
    }
  }

  const deleteConversation = async (conversationId) => {
    try {
      await del(`/api/chat/conversations/${conversationId}`)
      const index = conversations.value.findIndex(c => c.id === conversationId)
      if (index > -1) {
        conversations.value.splice(index, 1)
      }
      if (currentConversation.value?.id === conversationId) {
        currentConversation.value = null
        messages.value = []
      }
    } catch (error) {
      console.error('删除会话失败:', error)
      throw error
    }
  }

  const closeSSE = () => {
    if (sseConnection) {
      sseConnection.close()
      sseConnection = null
    }
  }

  const sendMessage = async (content) => {
    closeSSE()

    let convId = currentConversation.value?.id
    if (!convId) {
      const conv = await createConversation(content.substring(0, Math.min(content.length, 20)))
      convId = conv.id
    }

    const userMessage = {
      id: Date.now().toString(),
      role: 'user',
      content
    }
    messages.value.push(userMessage)

    const assistantMessage = {
      id: (Date.now() + 1).toString(),
      role: 'assistant',
      content: ''
    }
    messages.value.push(assistantMessage)

    try {
      await streamMessage(content, convId, assistantMessage.id)
    } catch (error) {
      const msg = messages.value.find(m => m.id === assistantMessage.id)
      if (msg) {
        msg.content = '抱歉，发生了错误，请稍后重试。'
      }
      console.error('发送消息失败:', error)
      throw error
    }
  }

  const streamMessage = async (content, conversationId, messageId) => {
    isStreaming.value = true

    return new Promise((resolve, reject) => {
      const url = `/api/chat/stream?message=${encodeURIComponent(content)}&conversationId=${conversationId}`
      const message = messages.value.find(m => m.id === messageId)
      let hasContent = false

      sseConnection = createSSEConnection(url, {
        onMessage: (data) => {
          if (data === '[DONE]') {
            closeSSE()
            isStreaming.value = false
            resolve()
            return
          }
          if (message) {
            message.content += data
            hasContent = true
          }
        },
        onError: (error) => {
          closeSSE()
          isStreaming.value = false
          if (message && !hasContent) {
            reject(error)
          } else {
            resolve()
          }
        }
      })
    })
  }

  return {
    conversations,
    currentConversation,
    messages,
    isStreaming,
    fetchConversations,
    createConversation,
    selectConversation,
    deleteConversation,
    sendMessage
  }
})
