/**
 * Keycloak PKCE 认证工具 — 处理 OAuth2 Authorization Code + PKCE 流程
 *
 * 职责：
 * - PKCE code_verifier / code_challenge 生成
 * - 构造 Keycloak 登录跳转 URL
 * - Token exchange（code → token）
 * - Token refresh（refresh_token → new token）
 * - Keycloak logout URL 构造
 * - JWT payload 解析
 */

import { decodeBase64Url, buildDisplayName } from '@/utils/jwt'
import type { AuthUser } from '@/api/types'

export const KEYCLOAK_CONFIG = {
  authority: 'http://localhost:8080/realms/trackflow',
  clientId: 'trackflow-frontend',
  redirectUri: window.location.origin + '/auth/callback',
  logoutUri: window.location.origin + '/login'
}

/**
 * 生成 PKCE code_verifier（RFC 7636 推荐 43-128 字符）
 */
export function generateCodeVerifier(): string {
  return generateRandomString(43)
}

/**
 * 从 code_verifier 生成 code_challenge（S256 方式）
 */
export async function generateCodeChallenge(codeVerifier: string): Promise<string> {
  const encoder = new TextEncoder()
  const data = encoder.encode(codeVerifier)
  const digest = await crypto.subtle.digest('SHA-256', data)
  return btoa(String.fromCharCode(...new Uint8Array(digest)))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, '')
}

/**
 * 生成密码学安全的随机字符串
 */
export function generateRandomString(length: number): string {
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
 * 解析 JWT access_token payload 为 AuthUser 对象
 */
export function parseJwtPayload(token: string): AuthUser | null {
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

/**
 * 从 JWT token 获取剩余有效秒数
 * @returns 剩余秒数，解析失败返回 -1
 */
export function getTokenRemainingTime(token: string): number {
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

export interface TokenExchangeResult {
  access_token: string
  refresh_token: string
}

/**
 * 使用 authorization_code 交换 token
 */
export async function exchangeCodeForToken(code: string, codeVerifier: string): Promise<TokenExchangeResult> {
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

  return response.json()
}

/**
 * 使用 refresh_token 刷新 token
 * @returns 新的 token 对，失败返回 null
 */
export async function refreshTokenRequest(currentRefreshToken: string): Promise<TokenExchangeResult | null> {
  const params = new URLSearchParams({
    grant_type: 'refresh_token',
    client_id: KEYCLOAK_CONFIG.clientId,
    refresh_token: currentRefreshToken
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
      return null
    }

    return response.json()
  } catch (e) {
    console.warn('[auth] Token refresh exception:', e)
    return null
  }
}

/**
 * 构造 Keycloak 登出 URL
 */
export function buildLogoutUrl(): string {
  const params = new URLSearchParams({
    client_id: KEYCLOAK_CONFIG.clientId,
    post_logout_redirect_uri: KEYCLOAK_CONFIG.logoutUri
  })
  return `${KEYCLOAK_CONFIG.authority}/protocol/openid-connect/logout?${params}`
}

/**
 * 构造 Keycloak 登录授权 URL
 */
export async function buildLoginUrl(): Promise<{ url: string; codeVerifier: string; state: string }> {
  const codeVerifier = generateCodeVerifier()
  const codeChallenge = await generateCodeChallenge(codeVerifier)
  const state = generateRandomString(16)

  const params = new URLSearchParams({
    response_type: 'code',
    client_id: KEYCLOAK_CONFIG.clientId,
    redirect_uri: KEYCLOAK_CONFIG.redirectUri,
    scope: 'openid profile email',
    state,
    code_challenge: codeChallenge,
    code_challenge_method: 'S256'
  })

  return {
    url: `${KEYCLOAK_CONFIG.authority}/protocol/openid-connect/auth?${params}`,
    codeVerifier,
    state
  }
}
