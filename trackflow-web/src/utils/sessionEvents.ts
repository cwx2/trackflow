/**
 * 会话生命周期事件管理
 *
 * 用于在会话即将过期时通知组件保存数据，避免用户正在编辑的内容丢失。
 *
 * 事件类型：
 * - session:expiring — 会话即将过期且无法自动续期，组件应立即保存未持久化的数据
 */

type SessionEventHandler = () => void

const listeners = new Map<string, Set<SessionEventHandler>>()

/**
 * 监听会话事件
 * @returns 取消监听的函数
 */
export function onSessionEvent(event: string, handler: SessionEventHandler): () => void {
  if (!listeners.has(event)) {
    listeners.set(event, new Set())
  }
  listeners.get(event)!.add(handler)

  return () => {
    listeners.get(event)?.delete(handler)
  }
}

/**
 * 触发会话事件（同步执行所有 handler）
 */
export function emitSessionEvent(event: string): void {
  const handlers = listeners.get(event)
  if (handlers) {
    handlers.forEach(handler => {
      try {
        handler()
      } catch (e) {
        console.error(`[sessionEvents] Handler error for event "${event}":`, e)
      }
    })
  }
}

/**
 * 会话恢复草稿 — 用 sessionStorage 保存，登录后自动恢复
 *
 * 与普通草稿（localStorage）不同：
 * - 会话恢复草稿存在 sessionStorage 中，关闭浏览器标签页后自动清除
 * - 仅用于 token 过期场景的数据保护，不是用户主动保存的草稿
 */
const SESSION_RECOVERY_KEY = 'tf_session_recovery_draft'

export interface SessionRecoveryDraft {
  /** 来源页面路径 */
  fromPath: string
  /** 表单数据 */
  formData: Record<string, any>
  /** 保存时间戳 */
  savedAt: number
}

/**
 * 保存会话恢复草稿
 */
export function saveSessionRecoveryDraft(draft: SessionRecoveryDraft): void {
  try {
    sessionStorage.setItem(SESSION_RECOVERY_KEY, JSON.stringify(draft))
  } catch {
    // sessionStorage 满或不可用，忽略
  }
}

/**
 * 获取并清除会话恢复草稿
 * @returns 草稿数据，若不存在或已过期（> 30 分钟）返回 null
 */
export function consumeSessionRecoveryDraft(): SessionRecoveryDraft | null {
  try {
    const raw = sessionStorage.getItem(SESSION_RECOVERY_KEY)
    if (!raw) return null

    sessionStorage.removeItem(SESSION_RECOVERY_KEY)
    const draft: SessionRecoveryDraft = JSON.parse(raw)

    // 超过 30 分钟的恢复草稿视为过期
    const MAX_AGE = 30 * 60 * 1000
    if (Date.now() - draft.savedAt > MAX_AGE) {
      return null
    }

    return draft
  } catch {
    sessionStorage.removeItem(SESSION_RECOVERY_KEY)
    return null
  }
}
