import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { authApi } from '@/api'

/**
 * Keycloak OIDC 配置
 */
const KEYCLOAK_CONFIG = {
  authority: 'http://localhost:8080/realms/trackflow',
  clientId: 'trackflow-frontend',
  redirectUri: window.location.origin + '/auth/callback',
  logoutUri: window.location.origin + '/login'
}

/**
 * localStorage keys
 */
const STORAGE_KEYS = {
  accessToken: 'tf_access_token',
  refreshToken: 'tf_refresh_token',
  user: 'tf_user'
} as const

/**
 * Token 主动刷新缓冲时间（秒）
 * 在 token 过期前这么多秒时主动刷新
 */
const TOKEN_REFRESH_BUFFER = 60

export const useAuthStore = defineStore('auth', () => {
  // 从 localStorage 恢复状态
  const accessToken = ref<string | null>(localStorage.getItem(STORAGE_KEYS.accessToken))
  const refreshToken = ref<string | null>(localStorage.getItem(STORAGE_KEYS.refreshToken))
  const user = ref<any>(restoreUser())
  const globalPermissions = ref<Set<string>>(new Set())
  const permissionsLoaded = ref(false)

  // Token 刷新定时器
  let refreshTimer: ReturnType<typeof setTimeout> | null = null

  const isAuthenticated = computed(() => !!accessToken.value)

  // 监听 token 变化，同步到 localStorage
  watch(accessToken, (val) => {
    if (val) {
      localStorage.setItem(STORAGE_KEYS.accessToken, val)
      // Token 更新后重新设置刷新定时器
      scheduleTokenRefresh(val)
    } else {
      localStorage.removeItem(STORAGE_KEYS.accessToken)
      clearRefreshTimer()
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
  }, { deep: true })

  // 初始化时，如果有 token 则启动定时刷新
  if (accessToken.value) {
    scheduleTokenRefresh(accessToken.value)
  }

  /**
   * 计划在 token 即将过期前主动刷新
   */
  function scheduleTokenRefresh(token: string) {
    clearRefreshTimer()
    try {
      const parts = token.split('.')
      if (parts.length !== 3) return
      const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')))
      const exp = payload.exp
      if (!exp) return

      const now = Math.floor(Date.now() / 1000)
      // 在过期前 TOKEN_REFRESH_BUFFER 秒刷新
      const refreshAt = (exp - TOKEN_REFRESH_BUFFER) - now
      if (refreshAt <= 0) {
        // Token 已经即将过期或已过期，立即刷新
        refresh()
        return
      }

      refreshTimer = setTimeout(async () => {
        const success = await refresh()
        if (!success) {
          // 刷新失败，但不强制登出——让请求拦截器处理
          console.warn('[auth] Proactive token refresh failed')
        }
      }, refreshAt * 1000)
    } catch {
      // 解析失败，不设置定时器
    }
  }

  function clearRefreshTimer() {
    if (refreshTimer !== null) {
      clearTimeout(refreshTimer)
      refreshTimer = null
    }
  }

  /**
   * 发起 Keycloak PKCE 登录
   */
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

  /**
   * 处理 Keycloak 回调，用 code 换取 token
   */
  async function handleCallback(code: string) {
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

    // 解析 JWT payload 获取用户信息
    user.value = parseJwtPayload(data.access_token)

    // 清理
    sessionStorage.removeItem('pkce_code_verifier')
    sessionStorage.removeItem('oauth_state')
  }

  /**
   * 刷新 token
   * 使用 refresh_token 向 Keycloak 获取新的 access_token
   */
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

      if (!response.ok) return false

      const data = await response.json()
      accessToken.value = data.access_token
      refreshToken.value = data.refresh_token
      user.value = parseJwtPayload(data.access_token)
      return true
    } catch {
      return false
    }
  }

  /**
   * 登出 — 清除本地存储并跳转
   * @param reason 登出原因提示信息（可选，存在时说明是被动登出如 token 过期）
   */
  function logout(reason?: string) {
    clearRefreshTimer()
    clearStorage()
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    globalPermissions.value = new Set()
    permissionsLoaded.value = false

    // 清除项目权限缓存（避免用户切换时残留旧权限）
    import('@/composables/usePermission').then(({ invalidateProjectPermissions }) => {
      invalidateProjectPermissions()
    }).catch(() => { /* ignore if module not loaded */ })

    if (reason) {
      // 被动登出（token 过期）：直接跳本地登录页，不走 Keycloak logout
      // 因为 token 已过期，Keycloak session 大概率也已失效，走 logout 会显示多余的确认页
      sessionStorage.setItem('tf_logout_reason', reason)
      window.location.href = KEYCLOAK_CONFIG.logoutUri
      return
    }

    // 主动登出：走 Keycloak logout 端点，销毁 SSO session
    const params = new URLSearchParams({
      client_id: KEYCLOAK_CONFIG.clientId,
      post_logout_redirect_uri: KEYCLOAK_CONFIG.logoutUri
    })

    window.location.href = `${KEYCLOAK_CONFIG.authority}/protocol/openid-connect/logout?${params}`
  }

  /**
   * 清除所有持久化的认证数据
   */
  function clearStorage() {
    localStorage.removeItem(STORAGE_KEYS.accessToken)
    localStorage.removeItem(STORAGE_KEYS.refreshToken)
    localStorage.removeItem(STORAGE_KEYS.user)
  }

  /**
   * 加载当前用户的全局权限
   * 支持并发调用去重：多次调用只发起一次请求
   */
  let _permissionLoadPromise: Promise<void> | null = null

  async function loadGlobalPermissions(): Promise<void> {
    if (!isAuthenticated.value) return
    if (permissionsLoaded.value) return

    // 去重：如果已有正在进行的请求，复用同一个 Promise
    if (_permissionLoadPromise) return _permissionLoadPromise

    _permissionLoadPromise = (async () => {
      try {
        const res = await authApi.getMyGlobalPermissions()
        globalPermissions.value = new Set(res.data || [])
      } catch (e) {
        console.warn('[auth] Failed to load global permissions', e)
        globalPermissions.value = new Set()
      } finally {
        permissionsLoaded.value = true
        _permissionLoadPromise = null
      }
    })()

    return _permissionLoadPromise
  }

  /**
   * 强制刷新全局权限（忽略 permissionsLoaded 标志）
   * 用于：403 响应触发、定期轮询、页面可见性恢复
   */
  async function refreshGlobalPermissions(): Promise<void> {
    if (!isAuthenticated.value) return

    // 去重：复用正在进行的请求
    if (_permissionLoadPromise) return _permissionLoadPromise

    _permissionLoadPromise = (async () => {
      try {
        const res = await authApi.getMyGlobalPermissions()
        globalPermissions.value = new Set(res.data || [])
      } catch (e) {
        console.warn('[auth] Failed to refresh global permissions', e)
      } finally {
        permissionsLoaded.value = true
        _permissionLoadPromise = null
      }
    })()

    return _permissionLoadPromise
  }

  // ===== 定期轮询刷新全局权限（每 5 分钟） =====
  const PERMISSION_REFRESH_INTERVAL = 5 * 60 * 1000 // 5 分钟
  let permissionRefreshTimer: ReturnType<typeof setInterval> | null = null

  function startPermissionRefresh() {
    stopPermissionRefresh()
    permissionRefreshTimer = setInterval(() => {
      if (isAuthenticated.value && permissionsLoaded.value) {
        refreshGlobalPermissions()
      }
    }, PERMISSION_REFRESH_INTERVAL)
  }

  function stopPermissionRefresh() {
    if (permissionRefreshTimer !== null) {
      clearInterval(permissionRefreshTimer)
      permissionRefreshTimer = null
    }
  }

  // 页面可见性变化时刷新权限（用户从后台切回前台）
  // 节流：距上次刷新 < 30s 则跳过，避免频繁 alt-tab 产生不必要的请求
  let lastVisibilityRefresh = 0
  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible' && isAuthenticated.value && permissionsLoaded.value) {
        const now = Date.now()
        if (now - lastVisibilityRefresh < 30000) return
        lastVisibilityRefresh = now
        refreshGlobalPermissions()
        // 同时清除项目级权限缓存，下次访问时重新加载
        import('@/composables/usePermission').then(({ invalidateProjectPermissions }) => {
          invalidateProjectPermissions()
        }).catch(() => { /* ignore */ })
      }
    })
  }

  // 认证成功后启动定期刷新
  watch(isAuthenticated, (authenticated) => {
    if (authenticated) {
      startPermissionRefresh()
    } else {
      stopPermissionRefresh()
    }
  }, { immediate: true })

  /**
   * 检查用户是否拥有指定的全局权限
   */
  function hasGlobalPermission(permission: string): boolean {
    if (globalPermissions.value.has('system:admin')) return true
    return globalPermissions.value.has(permission)
  }

  return {
    accessToken,
    refreshToken,
    user,
    globalPermissions,
    permissionsLoaded,
    isAuthenticated,
    login,
    handleCallback,
    refresh,
    logout,
    loadGlobalPermissions,
    refreshGlobalPermissions,
    hasGlobalPermission
  }
})

/**
 * 从 localStorage 恢复 user 对象
 */
function restoreUser(): Record<string, any> | null {
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

/**
 * PKCE S256: code_challenge = BASE64URL(SHA256(code_verifier))
 */
async function generateCodeChallenge(codeVerifier: string): Promise<string> {
  const encoder = new TextEncoder()
  const data = encoder.encode(codeVerifier)
  const digest = await crypto.subtle.digest('SHA-256', data)
  // base64url 编码（无 padding）
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

/**
 * 解析 JWT payload，提取用户信息
 * Keycloak JWT payload 包含: preferred_username, name, email, sub 等字段
 */
function parseJwtPayload(token: string): Record<string, any> | null {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')))
    return {
      id: payload.sub,
      username: payload.preferred_username,
      displayName: payload.name || payload.preferred_username,
      email: payload.email || '',
      roles: payload.realm_access?.roles || []
    }
  } catch {
    return null
  }
}
