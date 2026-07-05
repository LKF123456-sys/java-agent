import axios from 'axios'
import router from '@/router'

const ACCESS_TOKEN_KEY = 'accessToken'
const REFRESH_TOKEN_KEY = 'refreshToken'
const USER_KEY = 'user'

let isRefreshing = false
let refreshSubscribers = []

const subscribeTokenRefresh = (cb) => {
  refreshSubscribers.push(cb)
}

const onTokenRefreshed = (newToken) => {
  refreshSubscribers.forEach(cb => cb(newToken))
  refreshSubscribers = []
}

const getAccessToken = () => localStorage.getItem(ACCESS_TOKEN_KEY)
const getRefreshToken = () => localStorage.getItem(REFRESH_TOKEN_KEY)

export const setTokens = (accessToken, refreshToken) => {
  if (accessToken) {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  }
  if (refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  }
}

export const clearTokens = () => {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

const service = axios.create({
  baseURL: '',
  timeout: 60000
})

service.interceptors.request.use(
  config => {
    const token = getAccessToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

const refreshTokenRequest = async () => {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new Error('No refresh token available')
  }
  try {
    const response = await axios.post('/api/auth/refresh', {
      refreshToken: refreshToken
    })
    const res = response.data
    if (res.code === 200 && res.data) {
      const { accessToken: newAccessToken, refreshToken: newRefreshToken } = res.data
      setTokens(newAccessToken, newRefreshToken)
      return newAccessToken
    }
    throw new Error(res.message || 'Token refresh failed')
  } catch (error) {
    clearTokens()
    router.push('/')
    window.location.reload()
    throw error
  }
}

service.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    } else {
      const error = new Error(res.message || '请求失败')
      error.code = res.code
      console.error('响应错误:', res.message)
      return Promise.reject(error)
    }
  },
  async error => {
    const originalRequest = error.config

    if (error.response) {
      const { status } = error.response

      if (status === 401 && !originalRequest._retry) {
        if (isRefreshing) {
          return new Promise(resolve => {
            subscribeTokenRefresh(newToken => {
              originalRequest.headers.Authorization = `Bearer ${newToken}`
              resolve(service(originalRequest))
            })
          })
        }

        originalRequest._retry = true
        isRefreshing = true

        try {
          const newToken = await refreshTokenRequest()
          isRefreshing = false
          onTokenRefreshed(newToken)
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          return service(originalRequest)
        } catch (refreshError) {
          isRefreshing = false
          refreshSubscribers = []
          return Promise.reject(refreshError)
        }
      }

      const data = error.response.data
      if (data && data.message) {
        error.message = data.message
      }
    }

    console.error('网络错误:', error)
    return Promise.reject(error)
  }
)

export const createSSEConnection = (url, handlers = {}) => {
  const { onMessage, onError, onOpen, onClose } = handlers

  const accessToken = getAccessToken()
  const finalHeaders = {
    Accept: 'text/event-stream'
  }
  if (accessToken) {
    finalHeaders.Authorization = `Bearer ${accessToken}`
  }

  const controller = new AbortController()
  let isClosed = false

  const close = () => {
    if (isClosed) return
    isClosed = true
    controller.abort()
    if (onClose) {
      onClose()
    }
  }

  const connect = async () => {
    try {
      const response = await fetch(url, {
        method: 'GET',
        headers: finalHeaders,
        signal: controller.signal
      })

      if (!response.ok) {
        if (response.status === 401) {
          try {
            const refreshToken = getRefreshToken()
            if (refreshToken) {
              const newToken = await refreshTokenRequest()
              if (!isClosed) {
                finalHeaders.Authorization = `Bearer ${newToken}`
                connect()
                return
              }
            }
          } catch (e) {
            if (onError) onError(new Error('Unauthorized'))
            close()
            return
          }
        }
        if (onError) onError(new Error(`HTTP error! status: ${response.status}`))
        close()
        return
      }

      if (onOpen) onOpen()

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (!isClosed) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        let data = ''

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const lineData = line.slice(5)
            data += lineData.startsWith(' ') ? lineData.slice(1) : lineData
          } else if (line === '') {
            if (data && onMessage) {
              onMessage(data)
            }
            data = ''
          }
        }
      }

      if (buffer) {
        const lines = buffer.split('\n')
        let data = ''
        for (const line of lines) {
          if (line.startsWith('data:')) {
            const lineData = line.slice(5)
            data += lineData.startsWith(' ') ? lineData.slice(1) : lineData
          }
        }
        if (data && onMessage) {
          onMessage(data)
        }
      }

      if (!isClosed && onClose) {
        onClose()
      }
    } catch (err) {
      if (!isClosed && err.name !== 'AbortError') {
        if (onError) onError(err)
      }
      if (!isClosed && onClose) {
        onClose()
      }
    }
  }

  connect()

  return { close }
}

export const getAuthHeader = () => {
  const token = getAccessToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export const getAuthEventSourceUrl = (baseUrl) => {
  const token = getAccessToken()
  if (!token) return baseUrl
  const separator = baseUrl.includes('?') ? '&' : '?'
  return `${baseUrl}${separator}accessToken=${encodeURIComponent(token)}`
}

export const get = (url, params) => {
  return service.get(url, { params })
}

export const post = (url, data, config) => {
  return service.post(url, data, config)
}

export const put = (url, data) => {
  return service.put(url, data)
}

export const del = (url, params) => {
  return service.delete(url, { params })
}

export default service
