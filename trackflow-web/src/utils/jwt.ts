/**
 * JWT 解码工具函数
 *
 * 提供 Base64url 解码和 CJK 姓名处理等通用能力，
 * 被 auth store 和 request 拦截器共同使用。
 */

/**
 * 解码 Base64url 编码的字符串，正确处理 UTF-8 多字节字符（如中文）。
 * 浏览器原生 atob() 只支持 Latin-1，直接用于包含 UTF-8 的 JWT payload 会产生乱码。
 */
export function decodeBase64Url(base64url: string): string {
  const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/')
  const binaryStr = atob(base64)
  const bytes = Uint8Array.from(binaryStr, (c) => c.charCodeAt(0))
  return new TextDecoder('utf-8').decode(bytes)
}

/**
 * 判断字符串是否包含 CJK（中日韩）字符。
 * 覆盖 BMP 内的主要 CJK 区块：基本区 + 扩展 A + 兼容表意。
 */
export function containsCjk(text: string): boolean {
  return /[\u4e00-\u9fff\u3400-\u4dbf\uf900-\ufaff]/.test(text)
}

/**
 * 从 JWT payload 中的 given_name / family_name 构建正确格式的显示名称。
 * - CJK 姓名：姓+名（无空格），如"张伟"
 * - 西方姓名：名+空格+姓，如"Test User"
 *
 * 与后端 UserSyncService.buildDisplayName 逻辑保持一致。
 */
export function buildDisplayName(payload: Record<string, any>): string {
  const givenName = payload.given_name as string | undefined
  const familyName = payload.family_name as string | undefined

  if (givenName && familyName) {
    if (containsCjk(givenName) || containsCjk(familyName)) {
      return familyName + givenName
    } else {
      return givenName + ' ' + familyName
    }
  }
  if (givenName) return givenName
  if (familyName) return familyName
  return payload.name || payload.preferred_username || ''
}
