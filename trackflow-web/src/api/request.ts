import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

/**
 * Token 刷新控制器
 * - 防止并发请求同时触发多次 refresh
 * - 在 refresh 进行中的请求排队等待
 */
let isRefreshing = false
let refreshSubscribers: Array<(token: string) => void> = []

function subscribeTokenRefresh(cb: (token: string) => void) {
  refreshSubscribers.push(cb)
}

function onTokenRefreshed(newToken: string) {
  refreshSubscribers.forEach((cb) => cb(newToken))
  refreshSubscribers = []
}

function onRefreshFailed() {
  refreshSubscribers = []
}

/**
 * 解码 Base64url 编码的字符串，正确处理 UTF-8 多字节字符
 */
function decodeBase64Url(base64url: string): string {
  const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/')
  const binaryStr = atob(base64)
  const bytes = Uint8Array.from(binaryStr, (c) => c.charCodeAt(0))
  return new TextDecoder('utf-8').decode(bytes)
}

/**
 * 判断 JWT token 是否即将过期（剩余时间 < bufferSeconds）
 */
function isTokenExpiringSoon(token: string, bufferSeconds = 30): boolean {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return true
    const payload = JSON.parse(decodeBase64Url(parts[1]))
    const exp = payload.exp
    if (!exp) return true
    const now = Math.floor(Date.now() / 1000)
    return exp - now < bufferSeconds
  } catch {
    return true
  }
}

// 请求拦截器：附加 Token + 主动刷新即将过期的 Token
request.interceptors.request.use(
  async (config) => {
    const authStore = useAuthStore()

    if (!authStore.accessToken) {
      return config
    }

    // 检查 token 是否即将过期（剩余 < 30 秒）
    if (isTokenExpiringSoon(authStore.accessToken, 30)) {
      // 如果已有刷新进行中，等待其完成
      if (isRefreshing) {
        return new Promise((resolve) => {
          subscribeTokenRefresh((newToken: string) => {
            config.headers.Authorization = `Bearer ${newToken}`
            resolve(config)
          })
        })
      }

      // 发起刷新
      isRefreshing = true
      try {
        const refreshed = await authStore.refresh()
        if (refreshed) {
          isRefreshing = false
          onTokenRefreshed(authStore.accessToken!)
          config.headers.Authorization = `Bearer ${authStore.accessToken}`
        } else {
          // refresh_token 也过期了，跳转登录
          isRefreshing = false
          onRefreshFailed()
          authStore.logout('登录已过期，请重新登录')
          return Promise.reject(new axios.Cancel('Token refresh failed'))
        }
      } catch {
        isRefreshing = false
        onRefreshFailed()
        authStore.logout('登录已过期，请重新登录')
        return Promise.reject(new axios.Cancel('Token refresh failed'))
      }
    } else {
      config.headers.Authorization = `Bearer ${authStore.accessToken}`
    }

    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：处理 401 刷新 Token + 403 权限不足提示
request.interceptors.response.use(
  (response) => response.data,
  async (error) => {
    // 如果是主动取消的请求（token 刷新失败），不再处理
    if (axios.isCancel(error)) {
      return Promise.reject(error)
    }

    const authStore = useAuthStore()
    const originalRequest = error.config

    // 403 权限不足：刷新本地权限缓存并提示
    if (error.response?.status === 403) {
      if (!originalRequest._silent403) {
        const message = error.response?.data?.message || '权限不足，无法执行此操作'
        // 使用动态导入避免循环依赖
        import('@arco-design/web-vue').then(({ Message }) => {
          Message.warning({ content: message, id: 'permission-denied', duration: 3000 })
        })
      }

      // 403 表示权限已变更，自动刷新本地权限缓存
      if (!originalRequest._permissionRefreshed) {
        originalRequest._permissionRefreshed = true
        // 刷新全局权限
        authStore.refreshGlobalPermissions()
        // 清除项目级权限缓存
        import('@/composables/usePermission').then(({ invalidateProjectPermissions }) => {
          invalidateProjectPermissions()
        }).catch(() => { /* ignore */ })
      }

      return Promise.reject(error)
    }

    // 只处理 401 且非重试请求
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      // 如果已有刷新进行中，排队等待
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          subscribeTokenRefresh((newToken: string) => {
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            resolve(request(originalRequest))
          })
          // 如果等待超时（5秒），reject
          setTimeout(() => reject(error), 5000)
        })
      }

      isRefreshing = true
      try {
        const refreshed = await authStore.refresh()
        if (refreshed) {
          isRefreshing = false
          onTokenRefreshed(authStore.accessToken!)
          originalRequest.headers.Authorization = `Bearer ${authStore.accessToken}`
          return request(originalRequest)
        } else {
          isRefreshing = false
          onRefreshFailed()
          authStore.logout('登录已过期，请重新登录')
          return Promise.reject(error)
        }
      } catch {
        isRefreshing = false
        onRefreshFailed()
        authStore.logout('登录已过期，请重新登录')
        return Promise.reject(error)
      }
    }

    return Promise.reject(error)
  }
)

export default request
