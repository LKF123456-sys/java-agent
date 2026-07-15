import request, { createSSEConnection } from '@/utils/request'

const createStreamChat = (url, onMessage) => {
  let sseConnection = null
  let fullContent = ''
  let hasError = false

  const promise = new Promise((resolve, reject) => {
    sseConnection = createSSEConnection(url, {
      onMessage: (data) => {
        if (!data) return
        
        if (data.startsWith('[ERROR]')) {
          hasError = true
          reject(new Error(data.substring(7)))
          return
        }
        
        fullContent += data
        onMessage({ content: data })
      },
      onError: (error) => {
        if (!hasError) {
          reject(error)
        }
      },
      onClose: () => {
        if (!hasError) {
          resolve({ ok: true, content: fullContent })
        }
      }
    })
  })

  promise.close = () => {
    if (sseConnection) {
      sseConnection.close()
    }
  }

  return promise
}

const createMultiAgentStream = (url, onMessage) => {
  let sseConnection = null
  let fullContent = ''
  let hasError = false

  const promise = new Promise((resolve, reject) => {
    sseConnection = createSSEConnection(url, {
      onMessage: (data) => {
        if (!data) return
        
        if (data.startsWith('[ERROR]')) {
          hasError = true
          reject(new Error(data.substring(7)))
          return
        }
        
        try {
          const parsed = JSON.parse(data)
          if (parsed.step && parsed.agent) {
            onMessage(parsed)
          }
          if (parsed.content) {
            fullContent += parsed.content
            onMessage({ content: parsed.content })
          }
        } catch (e) {
          fullContent += data
          onMessage({ content: data })
        }
      },
      onError: (error) => {
        if (!hasError) {
          reject(error)
        }
      },
      onClose: () => {
        if (!hasError) {
          resolve({ ok: true, content: fullContent })
        }
      }
    })
  })

  promise.close = () => {
    if (sseConnection) {
      sseConnection.close()
    }
  }

  return promise
}

export { createSSEConnection }

// 基础聊天API
export const streamChat = (message, conversationId, onMessage) => {
  let url = `/api/chat/stream?message=${encodeURIComponent(message)}`
  if (conversationId) {
    url += `&conversationId=${conversationId}`
  }
  return createStreamChat(url, onMessage)
}

// Agent聊天API
export const agentChat = (message, onMessage) => {
  const url = `/api/agent/stream?task=${encodeURIComponent(message)}`
  return createStreamChat(url, onMessage)
}

// 搜索Agent聊天API
export const searchAgentChat = (message, onMessage) => {
  const url = `/api/search-agent/stream?task=${encodeURIComponent(message)}`
  return createStreamChat(url, onMessage)
}

// 多Agent聊天API
export const multiAgentChat = (message, onMessage) => {
  const url = `/api/multi-agent/stream?task=${encodeURIComponent(message)}`
  return createMultiAgentStream(url, onMessage)
}

// 记忆聊天API
export const memoryChat = (message, onMessage) => {
  const url = `/api/memory/stream?message=${encodeURIComponent(message)}`
  return createStreamChat(url, onMessage)
}

// RAG聊天API
export const ragChat = (question, onMessage) => {
  const url = `/api/rag/ask/stream?question=${encodeURIComponent(question)}`
  return createStreamChat(url, onMessage)
}

// 会话API
export const getConversations = (type = 'chat') => {
  return request.get('/api/conversations', { params: { type } })
}

export const createConversation = (title) => {
  return request.post('/api/conversations', { title })
}

export const deleteConversation = (id) => {
  return request.delete(`/api/conversations/${id}`)
}

export const getConversationMessages = (conversationId) => {
  return request.get(`/api/conversations/${conversationId}/messages`)
}

// RAG文档API
export const getDocuments = () => {
  return request.get('/api/rag/documents')
}

export const deleteDocument = (id) => {
  return request.delete(`/api/rag/documents/${id}`)
}

// MCP工具API
export const getMcpTools = () => {
  return request.get('/api/mcp/tools')
}

export const callMcpTool = (name, args) => {
  return request.post('/api/mcp/tools/call', { name, args })
}

export const toggleMcpTool = (name, enabled) => {
  return request.post(`/api/mcp/tools/${name}/toggle`, { enabled })
}

// 内置工具API
export const callBuiltinTool = (toolName, params) => {
  return request.post('/api/tools/call', { toolName, params })
}
