import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { authApi } from '@/api'
import type { AuthUser } from '@/api/types'
import { decodeBase64Url, buildDisplayName } from '@/utils/jwt'
import { usePermissionStore } from './permission'

/**
 * 认证 Store — 管理用户会话和 Token 生命周期
 *
 * 职责：
 * - 用户信息存储（user、accessToken、refreshToken）
 * - Keycloak PKCE 认证流程（login、handleCallback）
 * - Token 主动刷新和定期检查
 * - 登出流程
 *
 * 全局权限管理已迁移至 stores/permission.ts
 */

const KEYCLOAK_CONFIG = {
  authority: 'http://localhost:8080/realms/trackflow',
  clientId: 'trackflow-frontend',
  redirectUri: window.location.origin + '/auth/callback',
  logoutUri: window.location.origin + '/login'
}

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
    if (val) {
      localStorage.setItem(STORAGE_KEYS.refreshToken, val)
    } else {
      localStorage.removeItem(STORAGE_KEYS.refreshToken)
    }
  })

  watch(user, (val) => {
    if (val) {
      localStorage.setItem(STORAGE_KEYS.user, JSON.stringify(val))
    } else {
      localStorage.removeItem(STORAGE_KEYS.user)
    }
  }, { deep: true, immediate: true })

  // 初始化时，如果有 token 则启动定时刷新
  if (accessToken.value) {
    scheduleTokenRefresh(accessToken.value)
    startTokenCheckInterval()
  }

  // 认证状态变化时启动/停止 token 检查
  watch(isAuthenticated, (authenticated) => {
    if (authenticated) {
      startTokenCheckInterval()
    } else {
      stopTokenCheckInterval()
    }
  })

  function getTokenRemainingTime(token: string): number {
    try {
      const parts = token.split('.')
      if (parts.length !== 3) return -1
      const payload = JSON.parse(decodeBase64Url(parts[1]))
      const exp = payload.exp
      if (!exp) return -1
      return exp - Math.floor(Date.now() / 1000)
    } catch {
      return -1
    }
  }

  function scheduleTokenRefresh(token: string) {
    clearRefreshTimer()
    try {
      const parts = token.split('.')
      if (parts.length !== 3) return
      const payload = JSON.parse(decodeBase64Url(parts[1]))
      const exp = payload.exp
      if (!exp) return

      const now = Math.floor(Date.now() / 1000)
      const refreshAt = (exp - TOKEN_REFRESH_BUFFER) - now
      if (refreshAt <= 0) {
        performProactiveRefresh()
        return
      }

      refreshTimer = setTimeout(() => {
        performProactiveRefresh()
      }, refreshAt * 1000)
    } catch {
      // 解析失败，不设置定时器
    }
  }

  async function performProactiveRefresh(retries = 0): Promise<void> {
    if (isProactiveRefreshing) return
    isProactiveRefreshing = true

    try {
      const success = await refresh()
      if (success) {
        isProactiveRefreshing = false
        return
      }

      isProactiveRefreshing = false
      if (retries < TOKEN_REFRESH_MAX_RETRIES) {
        console.warn(`[auth] Proactive token refresh failed, retry ${retries + 1}/${TOKEN_REFRESH_MAX_RETRIES} in ${TOKEN_REFRESH_RETRY_DELAY / 1000}s`)
        refreshTimer = setTimeout(() => {
          performProactiveRefresh(retries + 1)
        }, TOKEN_REFRESH_RETRY_DELAY)
      } else {
        console.error('[auth] Token refresh failed after all retries')
        showSessionExpiredNotification()
      }
    } catch {
      isProactiveRefreshing = false
      if (retries < TOKEN_REFRESH_MAX_RETRIES) {
        refreshTimer = setTimeout(() => {
          performProactiveRefresh(retries + 1)
        }, TOKEN_REFRESH_RETRY_DELAY)
      } else {
        showSessionExpiredNotification()
      }
    }
  }

  let sessionExpiredNotified = false
  function showSessionExpiredNotification() {
    if (sessionExpiredNotified) return
    sessionExpiredNotified = true

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

    setTimeout(() => {
      logout('会话已过期，请重新登录')
    }, 2000)
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
          if (remaining < TOKEN_REFRESH_BUFFER) {
            performProactiveRefresh()
          }
        }
      }
    })
  }

  async function login() {
    const codeVerifier = generateCodeVerifier()
    sessionStorage.setItem('pkce_code_verifier', codeVerifier)

    const codeChallenge = await generateCodeChallenge(codeVerifier)
    const state = generateRandomString(16)
    sessionStorage.setItem('oauth_state', state)

    const params = new URLSearchParams({
      response_type: 'code',
      client_id: KEYCLOAK_CONFIG.clientId,
      redirect_uri: KEYCLOAK_CONFIG.redirectUri,
      scope: 'openid profile email',
      state,
      code_challenge: codeChallenge,
      code_challenge_method: 'S256'
    })

    window.location.href = `${KEYCLOAK_CONFIG.authority}/protocol/openid-connect/auth?${params}`
  }

  async function handleCallback(code: string, state: string) {
    const savedState = sessionStorage.getItem('oauth_state')
    if (!savedState || savedState !== state) {
      sessionStorage.removeItem('pkce_code_verifier')
      sessionStorage.removeItem('oauth_state')
      throw new Error('OAuth state mismatch - possible CSRF attack')
    }

    const codeVerifier = sessionStorage.getItem('pkce_code_verifier') || ''

    const params = new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: KEYCLOAK_CONFIG.clientId,
      redirect_uri: KEYCLOAK_CONFIG.redirectUri,
      code,
      code_verifier: codeVerifier
    })

    const response = await fetch(
      `${KEYCLOAK_CONFIG.authority}/protocol/openid-connect/token`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params
      }
    )

    if (!response.ok) {
      throw new Error('Token exchange failed')
    }

    const data = await response.json()
    accessToken.value = data.access_token
    refreshToken.value = data.refresh_token
    user.value = parseJwtPayload(data.access_token)

    sessionStorage.removeItem('pkce_code_verifier')
    sessionStorage.removeItem('oauth_state')
  }

  async function refresh(): Promise<boolean> {
    if (!refreshToken.value) return false

    const params = new URLSearchParams({
      grant_type: 'refresh_token',
      client_id: KEYCLOAK_CONFIG.clientId,
      refresh_token: refreshToken.value
    })

    try {
      const response = await fetch(
        `${KEYCLOAK_CONFIG.authority}/protocol/openid-connect/token`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: params
        }
      )

      if (!response.ok) {
        const errBody = await response.json().catch(() => ({}))
        console.warn('[auth] Token refresh failed:', response.status, errBody.error, errBody.error_description)
        return false
      }

      const data = await response.json()
      accessToken.value = data.access_token
      refreshToken.value = data.refresh_token
      const existingUserId = user.value?.userId
      user.value = parseJwtPayload(data.access_token)
      if (user.value && existingUserId) {
        user.value.userId = existingUserId
      }
      return true
    } catch (e) {
      console.warn('[auth] Token refresh exception:', e)
      return false
    }
  }

  function logout(reason?: string) {
    // 主动登出时通知后端（best-effort）
    if (!reason && accessToken.value) {
      authApi.notifyLogout().catch(() => { /* ignore */ })
    }

    // 被动登出：保存当前页面路径用于登录后跳回
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

    // 清除权限状态（permission store 自身通过 watch isAuthenticated 也会清理，但显式调用更可靠）
    import('./permission').then(({ usePermissionStore }) => {
      const permStore = usePermissionStore()
      permStore.clearPermissions()
    }).catch(() => { /* ignore */ })

    // 清除项目权限缓存
    import('@/composables/usePermission').then(({ invalidateProjectPermissions }) => {
      invalidateProjectPermissions()
    }).catch(() => { /* ignore */ })

    // 清除项目选择偏好的内存状态
    import('./project').then(({ useProjectStore }) => {
      const projectStore = useProjectStore()
      projectStore.selectProject(undefined)
    }).catch(() => { /* ignore */ })

    if (reason) {
      sessionStorage.setItem('tf_logout_reason', reason)
      window.location.href = KEYCLOAK_CONFIG.logoutUri
      return
    }

    sessionStorage.removeItem('tf_return_url')
    const params = new URLSearchParams({
      client_id: KEYCLOAK_CONFIG.clientId,
      post_logout_redirect_uri: KEYCLOAK_CONFIG.logoutUri
    })

    window.location.href = `${KEYCLOAK_CONFIG.authority}/protocol/openid-connect/logout?${params}`
  }

  function clearStorage() {
    localStorage.removeItem(STORAGE_KEYS.accessToken)
    localStorage.removeItem(STORAGE_KEYS.refreshToken)
    localStorage.removeItem(STORAGE_KEYS.user)
  }

  // ===== Backward-compatible delegations to permission store =====
  // Consumers that still use authStore.hasGlobalPermission() etc. continue to work.
  // New code should import usePermissionStore directly.
  //
  // Note: Circular import (auth ↔ permission) is safe here because:
  // - Both modules use defineStore() which only registers factories at module level
  // - Actual store instances are created lazily when first accessed
  // - By the time any delegation function runs, both modules are fully loaded

  function _permStore() {
    // Lazy access to avoid instantiation during auth store setup
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
    // Auth methods
    login,
    handleCallback,
    refresh,
    logout,
    // Permission delegations (backward compat — prefer usePermissionStore for new code)
    permissionsLoaded,
    globalPermissions,
    canCreateIssue,
    hasGlobalPermission,
    loadGlobalPermissions,
    refreshGlobalPermissions
  }
})

// ===== Helper functions (module-level, not exported from store) =====

function restoreUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEYS.user)
    return raw ? JSON.parse(raw) : null
  } catch {
    localStorage.removeItem(STORAGE_KEYS.user)
    return null
  }
}

function generateCodeVerifier(): string {
  return generateRandomString(43)
}

async function generateCodeChallenge(codeVerifier: string): Promise<string> {
  const encoder = new TextEncoder()
  const data = encoder.encode(codeVerifier)
  const digest = await crypto.subtle.digest('SHA-256', data)
  return btoa(String.fromCharCode(...new Uint8Array(digest)))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, '')
}

function generateRandomString(length: number): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~'
  let result = ''
  const array = new Uint8Array(length)
  crypto.getRandomValues(array)
  for (let i = 0; i < length; i++) {
    result += chars[array[i] % chars.length]
  }
  return result
}

function parseJwtPayload(token: string): AuthUser | null {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    const payload = JSON.parse(decodeBase64Url(parts[1]))
    return {
      id: payload.sub,
      username: payload.preferred_username,
      displayName: buildDisplayName(payload),
      email: payload.email || '',
      roles: payload.realm_access?.roles || []
    }
  } catch {
    return null
  }
}
