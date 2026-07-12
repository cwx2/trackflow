import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器：附加 Token
request.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    if (authStore.accessToken) {
      config.headers.Authorization = `Bearer ${authStore.accessToken}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：处理 401 刷新 Token
request.interceptors.response.use(
  (response) => response.data,
  async (error) => {
    const authStore = useAuthStore()
    const originalRequest = error.config

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      const refreshed = await authStore.refresh()
      if (refreshed) {
        originalRequest.headers.Authorization = `Bearer ${authStore.accessToken}`
        return request(originalRequest)
      } else {
        // Token 过期且无法刷新 — 清除存储并跳转登录
        authStore.logout()
        return Promise.reject(error)
      }
    }

    return Promise.reject(error)
  }
)

export default request
