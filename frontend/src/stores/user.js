import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { get, post, setTokens, clearTokens } from '../utils/request'

const ACCESS_TOKEN_KEY = 'accessToken'
const USER_KEY = 'user'

export const useUserStore = defineStore('user', () => {
  const user = ref(null)
  const accessToken = ref(localStorage.getItem(ACCESS_TOKEN_KEY) || '')

  const isLoggedIn = computed(() => !!accessToken.value)
  const isAdmin = computed(() => user.value?.role === 'admin')
  const isGuest = computed(() => !user.value || user.value?.role === 'guest')
  const token = computed(() => accessToken.value)

  const login = async (username, password) => {
    try {
      const data = await post('/api/auth/login', { username, password })
      const { accessToken: newAccessToken, refreshToken, user: userData } = data
      accessToken.value = newAccessToken || ''
      user.value = userData || data.user || null
      setTokens(newAccessToken, refreshToken)
      if (user.value) {
        localStorage.setItem(USER_KEY, JSON.stringify(user.value))
      }
      return data
    } catch (error) {
      console.error('登录失败:', error)
      throw error
    }
  }

  const register = async (username, password, nickname) => {
    try {
      const payload = { username, password }
      if (nickname) {
        payload.nickname = nickname
      }
      const data = await post('/api/auth/register', payload)
      return data
    } catch (error) {
      console.error('注册失败:', error)
      throw error
    }
  }

  const logout = () => {
    accessToken.value = ''
    user.value = null
    clearTokens()
  }

  const getInfo = async () => {
    if (!accessToken.value) return null
    try {
      const data = await get('/api/auth/me')
      user.value = data
      if (data) {
        localStorage.setItem(USER_KEY, JSON.stringify(data))
      }
      return data
    } catch (error) {
      console.warn('获取用户信息失败（Token可能已过期，等待401刷新）:', error.message)
      return null
    }
  }

  const initFromStorage = () => {
    const savedUser = localStorage.getItem(USER_KEY)
    if (savedUser && accessToken.value) {
      try {
        user.value = JSON.parse(savedUser)
      } catch (e) {
        console.error('解析本地用户信息失败:', e)
      }
    }
  }

  initFromStorage()

  if (typeof window !== 'undefined') {
    window.addEventListener('auth:logout', () => {
      accessToken.value = ''
      user.value = null
    })
  }

  return {
    user,
    accessToken,
    token,
    isLoggedIn,
    isAdmin,
    isGuest,
    login,
    register,
    logout,
    getInfo
  }
})
