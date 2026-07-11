import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * Keycloak OIDC 配置
 */
const KEYCLOAK_CONFIG = {
  authority: 'http://localhost:8080/realms/trackflow',
  clientId: 'trackflow-frontend',
  redirectUri: window.location.origin + '/auth/callback',
  logoutUri: window.location.origin + '/login'
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)
  const refreshToken = ref<string | null>(null)
  const user = ref<any>(null)

  const isAuthenticated = computed(() => !!accessToken.value)

  /**
   * 发起 Keycloak PKCE 登录
   */
  function login() {
    const codeVerifier = generateCodeVerifier()
    sessionStorage.setItem('pkce_code_verifier', codeVerifier)

    const codeChallenge = codeVerifier // 简化：实际应做 SHA256 + base64url
    const state = generateRandomString(16)
    sessionStorage.setItem('oauth_state', state)

    const params = new URLSearchParams({
      response_type: 'code',
      client_id: KEYCLOAK_CONFIG.clientId,
      redirect_uri: KEYCLOAK_CONFIG.redirectUri,
      scope: 'openid profile email',
      state,
      code_challenge: codeChallenge,
      code_challenge_method: 'plain' // 简化，生产环境用 S256
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
   * 登出
   */
  function logout() {
    accessToken.value = null
    refreshToken.value = null
    user.value = null

    const params = new URLSearchParams({
      client_id: KEYCLOAK_CONFIG.clientId,
      post_logout_redirect_uri: KEYCLOAK_CONFIG.logoutUri
    })

    window.location.href = `${KEYCLOAK_CONFIG.authority}/protocol/openid-connect/logout?${params}`
  }

  return {
    accessToken,
    refreshToken,
    user,
    isAuthenticated,
    login,
    handleCallback,
    refresh,
    logout
  }
})

function generateCodeVerifier(): string {
  return generateRandomString(43)
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
