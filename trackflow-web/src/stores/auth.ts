import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { authApi } from '@/api'
import type { AuthUser } from '@/api/types'
import { usePermissionStore } from './permission'
import {
  KEYCLOAK_CONFIG,
  parseJwtPayload,
  getTokenRemainingTime,
  exchangeCodeForToken,
  refreshTokenRequest,
  buildLogoutUrl,
  buildLoginUrl
} from '@/utils/keycloak'

/**
 * 认证 Store — 管理用户会话和 Token 生命周期
 *
 * 职责：
 * - 用户信息存储（user、accessToken、refreshToken）
 * - Keycloak PKCE 认证流程编排（login、handleCallback）
 * - Token 主动刷新和定期检查
 * - 登出流程
 *
 * PKCE 协议细节和 Token 解析已迁移至 utils/keycloak.ts
 * 全局权限管理已迁移至 stores/permission.ts
 */

const STORAGE_KEYS = {
  accessToken: 'tf_access_token',
  refreshToken: 'tf_refresh_token',
  user: 'tf_user'
} as const

/** 在 token 过期前这么多秒时主动刷新 */
const TOKEN_REFRESH_BUFFER = 60

/** Token 有效性检查间隔（毫秒），防止浏览器后台暂停定时器 */
const TOKEN_CHECK_INTERVAL = 30 * 1000

/** Token 刷新失败后重试延迟（毫秒） */
const TOKEN_REFRESH_RETRY_DELAY = 5 * 1000

/** Token 刷新最大重试次数 */
const TOKEN_REFRESH_MAX_RETRIES = 3

export const useAuthStore = defineStore('auth', () => {
  // 从 localStorage 恢复状态
  const accessToken = ref<string | null>(localStorage.getItem(STORAGE_KEYS.accessToken))
  const refreshToken = ref<string | null>(localStorage.getItem(STORAGE_KEYS.refreshToken))
  const user = ref<AuthUser | null>((() => {
    if (!accessToken.value) return restoreUser()
    const jwtUser = parseJwtPayload(accessToken.value)
    if (!jwtUser) return restoreUser()
    const storedUser = restoreUser()
    if (storedUser?.userId) {
      jwtUser.userId = storedUser.userId
    }
    return jwtUser
  })())

  // Token 刷新定时器
  let refreshTimer: ReturnType<typeof setTimeout> | null = null
  // Token 有效性定期检查器
  let tokenCheckInterval: ReturnType<typeof setInterval> | null = null
  // 是否正在执行主动刷新
  let isProactiveRefreshing = false

  /**
   * 登出进行中标志。
   * 在 logout() 开始时设为 true，防止 clearPermissions() → permissionsLoaded=false
   * 导致路由守卫在 window.location.href 生效前错误地跳转到 /403。
   */
  const isLoggingOut = ref(false)

  const isAuthenticated = computed(() => !!accessToken.value)

  // 监听 token 变化，同步到 localStorage
  watch(accessToken, (val) => {
    if (val) {
      localStorage.setItem(STORAGE_KEYS.accessToken, val)
      scheduleTokenRefresh(val)
    } else {
      localStorage.removeItem(STORAGE_KEYS.accessToken)
      clearRefreshTimer()
      stopTokenCheckInterval()
    }
  })

  watch(refreshToken, (val) => {
    if (val) localStorage.setItem(STORAGE_KEYS.refreshToken, val)
    else localStorage.removeItem(STORAGE_KEYS.refreshToken)
  })

  watch(user, (val) => {
    if (val) localStorage.setItem(STORAGE_KEYS.user, JSON.stringify(val))
    else localStorage.removeItem(STORAGE_KEYS.user)
  }, { deep: true, immediate: true })

  // 初始化时，如果有 token 则启动定时刷新
  if (accessToken.value) {
    scheduleTokenRefresh(accessToken.value)
    startTokenCheckInterval()
  }

  // 认证状态变化时启动/停止 token 检查
  watch(isAuthenticated, (authenticated) => {
    if (authenticated) startTokenCheckInterval()
    else stopTokenCheckInterval()
  })

  // ===== Token 刷新调度 =====

  function scheduleTokenRefresh(token: string) {
    clearRefreshTimer()
    const remaining = getTokenRemainingTime(token)
    if (remaining < 0) return

    const refreshAt = remaining - TOKEN_REFRESH_BUFFER
    if (refreshAt <= 0) {
      performProactiveRefresh()
      return
    }
    refreshTimer = setTimeout(() => performProactiveRefresh(), refreshAt * 1000)
  }

  async function performProactiveRefresh(retries = 0): Promise<void> {
    if (isProactiveRefreshing) return
    isProactiveRefreshing = true

    try {
      const success = await refresh()
      isProactiveRefreshing = false
      if (success) return

      if (retries < TOKEN_REFRESH_MAX_RETRIES) {
        console.warn(`[auth] Proactive token refresh failed, retry ${retries + 1}/${TOKEN_REFRESH_MAX_RETRIES}`)
        refreshTimer = setTimeout(() => performProactiveRefresh(retries + 1), TOKEN_REFRESH_RETRY_DELAY)
      } else {
        console.error('[auth] Token refresh failed after all retries')
        showSessionExpiredNotification()
      }
    } catch {
      isProactiveRefreshing = false
      if (retries < TOKEN_REFRESH_MAX_RETRIES) {
        refreshTimer = setTimeout(() => performProactiveRefresh(retries + 1), TOKEN_REFRESH_RETRY_DELAY)
      } else {
        showSessionExpiredNotification()
      }
    }
  }

  let sessionExpiredNotified = false
  function showSessionExpiredNotification() {
    if (sessionExpiredNotified) return
    sessionExpiredNotified = true

    // 立即标记登出进行中，防止后续权限刷新触发路由守卫误跳 /403
    isLoggingOut.value = true

    import('@/utils/sessionEvents').then(({ emitSessionEvent }) => {
      emitSessionEvent('session:expiring')
    }).catch(() => { /* ignore */ })

    import('@arco-design/web-vue').then(({ Notification: ArcoNotification }) => {
      ArcoNotification.error({
        id: 'session-expired-global',
        title: '会话已过期',
        content: '您的登录状态已失效，即将跳转到登录页面...',
        duration: 3000,
        closable: false
      })
    }).catch(() => { /* ignore */ })

    setTimeout(() => logout('会话已过期，请重新登录'), 2000)
  }

  function startTokenCheckInterval() {
    stopTokenCheckInterval()
    tokenCheckInterval = setInterval(() => {
      if (!accessToken.value || !refreshToken.value) return
      const remaining = getTokenRemainingTime(accessToken.value)
      if (remaining < TOKEN_REFRESH_BUFFER && remaining > -1800 && !isProactiveRefreshing) {
        performProactiveRefresh()
      }
    }, TOKEN_CHECK_INTERVAL)
  }

  function stopTokenCheckInterval() {
    if (tokenCheckInterval !== null) {
      clearInterval(tokenCheckInterval)
      tokenCheckInterval = null
    }
  }

  function clearRefreshTimer() {
    if (refreshTimer !== null) {
      clearTimeout(refreshTimer)
      refreshTimer = null
    }
  }

  // 页面可见性变化时检查 token 刷新需求
  if (typeof document !== 'undefined') {
    let lastVisibilityCheck = 0
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible' && isAuthenticated.value) {
        const now = Date.now()
        if (now - lastVisibilityCheck < 30000) return
        lastVisibilityCheck = now
        if (accessToken.value) {
          const remaining = getTokenRemainingTime(accessToken.value)
          if (remaining < TOKEN_REFRESH_BUFFER) performProactiveRefresh()
        }
      }
    })
  }

  // ===== 认证流程 =====

  /**
   * 身份初始化 — 从后端获取数据库用户 ID，补全登录后的用户身份信息。
   *
   * JWT 只包含 Keycloak 信息（sub/username/email 等），不包含数据库主键。
   * 数据库 userId 是前端资源级权限判断（如 WebSocket 事件过滤）的必要字段，
   * 必须在用户进入应用前确保已获取。
   */
  async function fetchUserProfile(): Promise<void> {
    if (!user.value) return
    try {
      const res = await authApi.me()
      if (res.code === 0 && res.data?.userId && user.value) {
        user.value.userId = res.data.userId
      }
    } catch (e) {
      console.warn('[auth] Failed to fetch user profile:', e)
      throw e // 身份初始化失败应向上抛出，不能静默忽略
    }
  }

  /**
   * 确保 userId 已加载。
   * 如果已有 userId 则立即返回；否则调用 /me 接口获取。
   * 用于路由守卫保证看板"仅我的"等依赖 userId 的功能在页面刷新后正常工作。
   */
  async function ensureUserId(): Promise<void> {
    if (!user.value) return
    if (user.value.userId) return
    await fetchUserProfile()
  }

  async function login() {
    const { url, codeVerifier, state } = await buildLoginUrl()
    sessionStorage.setItem('pkce_code_verifier', codeVerifier)
    sessionStorage.setItem('oauth_state', state)
    window.location.href = url
  }

  async function handleCallback(code: string, state: string) {
    const savedState = sessionStorage.getItem('oauth_state')
    if (!savedState || savedState !== state) {
      sessionStorage.removeItem('pkce_code_verifier')
      sessionStorage.removeItem('oauth_state')
      throw new Error('OAuth state mismatch - possible CSRF attack')
    }

    const codeVerifier = sessionStorage.getItem('pkce_code_verifier') || ''
    const data = await exchangeCodeForToken(code, codeVerifier)

    // 第 1 步：认证 — 存 token，从 JWT 解析基础用户信息
    accessToken.value = data.access_token
    refreshToken.value = data.refresh_token
    user.value = parseJwtPayload(data.access_token)

    // 第 2 步：身份初始化 — 获取数据库用户 ID，完善用户身份信息
    // 必须在此处 await 完成，确保登录流程结束时 userId 已有值
    // 后续依赖 userId 的模块（WebSocket 事件过滤等）才能正确工作
    await fetchUserProfile()

    sessionStorage.removeItem('pkce_code_verifier')
    sessionStorage.removeItem('oauth_state')
  }

  async function refresh(): Promise<boolean> {
    if (!refreshToken.value) return false
    const data = await refreshTokenRequest(refreshToken.value)
    if (!data) return false

    accessToken.value = data.access_token
    refreshToken.value = data.refresh_token
    const existingUserId = user.value?.userId
    user.value = parseJwtPayload(data.access_token)
    if (user.value && existingUserId) {
      user.value.userId = existingUserId
    }
    return true
  }

  function logout(reason?: string) {
    // 标记登出进行中，防止 clearPermissions() 触发路由守卫误跳 /403
    isLoggingOut.value = true

    if (!reason && accessToken.value) {
      authApi.notifyLogout().catch(() => { /* ignore */ })
    }

    if (reason) {
      const currentPath = window.location.pathname + window.location.search
      if (currentPath && currentPath !== '/' && currentPath !== '/login' && !currentPath.startsWith('/auth/')) {
        sessionStorage.setItem('tf_return_url', currentPath)
      }
    }

    clearRefreshTimer()
    stopTokenCheckInterval()
    clearStorage()
    accessToken.value = null
    refreshToken.value = null
    user.value = null

    import('./permission').then(({ usePermissionStore: getPermStore }) => {
      getPermStore().clearPermissions()
    }).catch(() => { /* ignore */ })

    import('@/composables/usePermission').then(({ invalidateProjectPermissions }) => {
      invalidateProjectPermissions()
    }).catch(() => { /* ignore */ })

    import('./project').then(({ useProjectStore }) => {
      useProjectStore().selectProject(undefined)
    }).catch(() => { /* ignore */ })

    if (reason) {
      sessionStorage.setItem('tf_logout_reason', reason)
      window.location.href = KEYCLOAK_CONFIG.logoutUri
      return
    }

    sessionStorage.removeItem('tf_return_url')
    window.location.href = buildLogoutUrl()
  }

  function clearStorage() {
    localStorage.removeItem(STORAGE_KEYS.accessToken)
    localStorage.removeItem(STORAGE_KEYS.refreshToken)
    localStorage.removeItem(STORAGE_KEYS.user)
  }

  // ===== Backward-compatible delegations to permission store =====

  function _permStore() {
    return usePermissionStore()
  }

  const permissionsLoaded = computed(() => _permStore().permissionsLoaded)
  const globalPermissions = computed(() => _permStore().globalPermissions)
  const canCreateIssue = computed(() => _permStore().canCreateIssue)

  function hasGlobalPermission(permission: string): boolean {
    return _permStore().hasGlobalPermission(permission)
  }

  async function loadGlobalPermissions(): Promise<void> {
    return _permStore().loadGlobalPermissions()
  }

  async function refreshGlobalPermissions(): Promise<void> {
    return _permStore().refreshGlobalPermissions()
  }

  return {
    accessToken,
    refreshToken,
    user,
    isAuthenticated,
    isLoggingOut,
    login,
    handleCallback,
    refresh,
    logout,
    ensureUserId,
    permissionsLoaded,
    globalPermissions,
    canCreateIssue,
    hasGlobalPermission,
    loadGlobalPermissions,
    refreshGlobalPermissions
  }
})

// ===== Module-level helpers =====

function restoreUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem('tf_user')
    return raw ? JSON.parse(raw) : null
  } catch {
    localStorage.removeItem('tf_user')
    return null
  }
}
