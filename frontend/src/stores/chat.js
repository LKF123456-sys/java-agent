import { defineStore } from 'pinia'
import { ref } from 'vue'
import request, { createSSEConnection } from '@/utils/request'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref([])
  const currentConversation = ref(null)
  const messages = ref([])
  const isStreaming = ref(false)
  let sseConnection = null

  const closeSSE = () => {
    if (sseConnection) {
      sseConnection.close()
      sseConnection = null
    }
  }

  const fetchConversations = async () => {
    const data = await request.get('/api/conversations', {
      params: { type: 'chat' }
    })
    conversations.value = data || []
    return data
  }

  const createConversation = async (skipClear = false) => {
    const newConv = await request.post('/api/conversations', {
      title: '新对话',
      type: 'chat'
    })
    conversations.value.unshift(newConv)
    currentConversation.value = newConv
    if (!skipClear) {
      messages.value = []
    }
    return newConv
  }

  const selectConversation = async (conversationId) => {
    closeSSE()
    const conv = conversations.value.find(c => c.id === conversationId)
    if (conv) {
      currentConversation.value = conv
      const data = await request.get(`/api/conversations/${conversationId}/messages`)
      messages.value = (data || []).map(m => ({
        id: m.id,
        role: m.role,
        content: m.content
      }))
    }
  }

  const deleteConversation = async (conversationId) => {
    await request.delete(`/api/conversations/${conversationId}`)
    conversations.value = conversations.value.filter(c => c.id !== conversationId)
    if (currentConversation.value?.id === conversationId) {
      currentConversation.value = null
      messages.value = []
    }
  }

  const sendMessage = (content) => {
    return new Promise((resolve, reject) => {
      if (!content.trim()) {
        reject(new Error('消息内容不能为空'))
        return
      }

      closeSSE()
      isStreaming.value = true

      const startSSE = (conversationId) => {
        let url = `/api/chat/stream?message=${encodeURIComponent(content.trim())}`
        if (conversationId) {
          url += `&conversationId=${conversationId}`
        }

        let hasError = false

        sseConnection = createSSEConnection(url, {
          onMessage: (data) => {
            if (!data) return
            
            if (data.startsWith('[ERROR]')) {
              hasError = true
              const errMsg = '错误: ' + data.substring(7)
              const lastMsg = messages.value[messages.value.length - 1]
              if (lastMsg && lastMsg.role === 'assistant') {
                lastMsg.content = errMsg
              }
              isStreaming.value = false
              closeSSE()
              reject(new Error(data.substring(7)))
              return
            }

            const lastMsg = messages.value[messages.value.length - 1]
            if (lastMsg && lastMsg.role === 'assistant') {
              lastMsg.content += data
            }
          },
          onError: (error) => {
            if (!hasError) {
              const lastMsg = messages.value[messages.value.length - 1]
              if (lastMsg && lastMsg.role === 'assistant' && !lastMsg.content) {
                lastMsg.content = '连接失败，请检查后端服务是否启动'
              }
            }
            isStreaming.value = false
            closeSSE()
            reject(error)
          },
          onClose: () => {
            isStreaming.value = false
            fetchConversations()
            const lastMsg = messages.value[messages.value.length - 1]
            resolve(lastMsg ? lastMsg.content : '')
          }
        })
      }

      const userMessage = {
        id: Date.now(),
        role: 'user',
        content: content.trim()
      }
      messages.value.push(userMessage)

      const assistantMessage = {
        id: Date.now() + 1,
        role: 'assistant',
        content: ''
      }
      messages.value.push(assistantMessage)

      if (currentConversation.value?.id) {
        startSSE(currentConversation.value.id)
      } else {
        createConversation(true)
          .then(newConv => {
            startSSE(newConv.id)
          })
          .catch(err => {
            isStreaming.value = false
            reject(err)
          })
      }
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
    sendMessage,
    closeSSE
  }
})
