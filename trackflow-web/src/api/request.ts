import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import { decodeBase64Url } from '@/utils/jwt'
import { emitSessionEvent } from '@/utils/sessionEvents'
import { reportApiSuccess, reportApiFailure } from '@/composables/useServiceStatus'

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
let refreshSubscribers: Array<{
  resolve: (token: string) => void
  reject: (error: Error) => void
}> = []

function subscribeTokenRefresh(resolve: (token: string) => void, reject: (error: Error) => void) {
  refreshSubscribers.push({ resolve, reject })
}

function onTokenRefreshed(newToken: string) {
  refreshSubscribers.forEach(({ resolve }) => resolve(newToken))
  refreshSubscribers = []
}

function onRefreshFailed() {
  const error = new Error('Token refresh failed')
  refreshSubscribers.forEach(({ reject }) => reject(error))
  refreshSubscribers = []
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

/**
 * 会话过期处理（去重 + 延迟跳转）
 * 解决：多个并发请求同时发现 token 过期时，只触发一次登出通知 + 跳转。
 * 给用户 2 秒的视觉反馈时间（显示"会话已过期"提示），而非突然跳转。
 */
let sessionExpiredHandled = false

function handleSessionExpired() {
  if (sessionExpiredHandled) return
  sessionExpiredHandled = true

  // 通知所有组件保存未持久化的数据（如创建工单表单）
  emitSessionEvent('session:expiring')

  // 动态导入避免循环依赖
  import('@arco-design/web-vue').then(({ Message }) => {
    Message.error({ content: '会话已过期，正在跳转到登录页...', id: 'session-expired', duration: 3000 })
  })

  // 延迟 1.5 秒后执行 logout 跳转，给用户视觉反馈
  setTimeout(() => {
    const authStore = useAuthStore()
    authStore.logout('会话已过期，请重新登录')
  }, 1500)
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
        return new Promise((resolve, reject) => {
          subscribeTokenRefresh(
            (newToken: string) => {
              config.headers.Authorization = `Bearer ${newToken}`
              resolve(config)
            },
            (error: Error) => {
              reject(new axios.Cancel(error.message))
            }
          )
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
          // refresh_token 也过期了 — 不立即 logout，而是优雅处理
          isRefreshing = false
          onRefreshFailed()
          handleSessionExpired()
          return Promise.reject(new axios.Cancel('会话已过期'))
        }
      } catch {
        isRefreshing = false
        onRefreshFailed()
        handleSessionExpired()
        return Promise.reject(new axios.Cancel('会话已过期'))
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
  (response) => {
    reportApiSuccess()
    return response.data
  },
  async (error) => {
    // 如果是主动取消的请求（token 刷新失败），静默处理
    // 不再向上抛出错误，避免多个并发请求同时 reject 导致级联异常
    if (axios.isCancel(error)) {
      // 返回一个空的响应结构，让调用方能安全地解构（如 res.data）
      // 调用方的 try-catch 或空值检查会处理这种情况
      return Promise.reject(error)
    }

    const authStore = useAuthStore()
    const originalRequest = error.config

    // 429 请求频率限制：显示友好提示，不触发重试
    if (error.response?.status === 429) {
      const retryAfter = error.response.headers?.['retry-after']
      const seconds = retryAfter ? parseInt(retryAfter, 10) : 60
      const message = error.response.data?.message || `请求过于频繁，请 ${seconds} 秒后重试`
      import('@arco-design/web-vue').then(({ Message }) => {
        Message.warning({ content: message, id: 'rate-limited', duration: 5000 })
      })
      return Promise.reject(error)
    }

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
          subscribeTokenRefresh(
            (newToken: string) => {
              originalRequest.headers.Authorization = `Bearer ${newToken}`
              resolve(request(originalRequest))
            },
            () => {
              reject(error)
            }
          )
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
          handleSessionExpired()
          return Promise.reject(error)
        }
      } catch {
        isRefreshing = false
        onRefreshFailed()
        handleSessionExpired()
        return Promise.reject(error)
      }
    }

    // 报告服务级错误（500+、网络不可达），用于全局服务状态感知
    reportApiFailure(error.response?.status, error.response?.data?.message)

    return Promise.reject(error)
  }
)

export default request
