import { Message } from '@arco-design/web-vue'
import type { MessageConfig, MessageReturn } from '@arco-design/web-vue/es/message/interface'
import type { AppContext } from 'vue'

/**
 * 全局消息节流器
 *
 * 解决多个 API 同时报错时前端弹出大量重复错误提示的问题。
 * 策略：
 * 1. 全局最大同时显示消息数（maxVisible = 3）
 * 2. 相同内容的错误在去重窗口内（2s）只显示一次
 * 3. 短时间内（2s）超过阈值（3条）自动聚合为一条汇总消息
 * 4. 当服务不可用时，抑制组件层的独立错误提示
 *
 * 安装方式：在 main.ts 中调用 installMessageThrottle()
 * 安装后所有 Message.error() / Message.warning() 调用自动经过节流。
 */

/** 去重窗口期（毫秒） */
const DEDUP_WINDOW_MS = 2000

/** 聚合阈值：窗口期内超过此数量则聚合 */
const AGGREGATE_THRESHOLD = 3

/** 聚合消息的 ID（固定值，保证只显示一条） */
const AGGREGATE_MSG_ID = '__tf_error_aggregate'

/** 服务不可用消息的 ID */
const SERVICE_DOWN_MSG_ID = '__tf_service_down'

/** 最大同时可见消息数 */
const MAX_VISIBLE = 3

// --- 原始 Message 方法引用（在 install 之前保存） ---
const originalError = Message.error
const originalWarning = Message.warning

// --- 内部状态 ---

/** 记录最近窗口期内的错误消息（时间戳列表） */
const recentErrors: Array<{ content: string; time: number }> = []

/** 当前可见的消息计数 */
let visibleCount = 0

/** 是否已显示聚合消息 */
let aggregateShown = false

/** 是否处于服务不可用状态 */
let serviceDownActive = false

/**
 * 清理过期的错误记录
 */
function cleanupRecent() {
  const now = Date.now()
  while (recentErrors.length > 0 && now - recentErrors[0].time > DEDUP_WINDOW_MS) {
    recentErrors.shift()
  }
}

/**
 * 检查是否是重复消息（同内容在窗口期内已展示过）
 */
function isDuplicate(content: string): boolean {
  cleanupRecent()
  return recentErrors.some(item => item.content === content)
}

/**
 * 记录一条错误消息
 */
function recordError(content: string) {
  recentErrors.push({ content, time: Date.now() })
}

/**
 * 检查是否应该聚合（窗口期内错误数超过阈值）
 */
function shouldAggregate(): boolean {
  cleanupRecent()
  return recentErrors.length >= AGGREGATE_THRESHOLD
}

/**
 * 消息关闭后减少计数
 */
function onMessageClose() {
  visibleCount = Math.max(0, visibleCount - 1)
}

/**
 * 重置聚合状态（聚合消息关闭后）
 */
function onAggregateClose() {
  aggregateShown = false
  onMessageClose()
}

/** 空返回值 */
const NOOP_RETURN: MessageReturn = { close: () => {} }

/**
 * 显示错误消息（带去重、聚合、节流能力）
 *
 * 内部使用 originalError 直接调用 Arco 原生方法，避免递归。
 *
 * @param content 错误消息文本
 * @param options 可选配置
 * @returns 是否实际显示了消息
 */
export function showError(
  content: string,
  options?: { id?: string; duration?: number; closable?: boolean }
): boolean {
  // 服务不可用时，抑制所有独立错误消息（已有全局横幅）
  if (serviceDownActive) {
    return false
  }

  // 带固定 id 的消息走 Arco 原生去重（如 session-expired），直接透传
  if (options?.id) {
    originalError({
      content,
      id: options.id,
      duration: options.duration ?? 3000,
      closable: options.closable,
      onClose: onMessageClose
    })
    return true
  }

  // 去重检查：相同内容在窗口期内不重复展示
  if (isDuplicate(content)) {
    return false
  }

  // 记录本条错误
  recordError(content)

  // 聚合检查：短时间内多个错误 → 只显示一条汇总
  if (shouldAggregate()) {
    if (!aggregateShown) {
      aggregateShown = true
      visibleCount++
      originalError({
        content: '多个请求失败，请刷新页面或稍后重试',
        id: AGGREGATE_MSG_ID,
        duration: 5000,
        closable: true,
        onClose: onAggregateClose
      })
    }
    return false
  }

  // 最大可见数限制
  if (visibleCount >= MAX_VISIBLE) {
    return false
  }

  // 正常展示
  visibleCount++
  originalError({
    content,
    duration: options?.duration ?? 3000,
    closable: options?.closable,
    onClose: onMessageClose
  })
  return true
}

/**
 * 显示警告消息（带去重能力）
 */
export function showWarning(
  content: string,
  options?: { id?: string; duration?: number }
): boolean {
  if (options?.id) {
    originalWarning({
      content,
      id: options.id,
      duration: options.duration ?? 3000,
      onClose: onMessageClose
    })
    return true
  }

  if (isDuplicate(content)) {
    return false
  }
  recordError(content)

  if (visibleCount >= MAX_VISIBLE) {
    return false
  }

  visibleCount++
  originalWarning({
    content,
    duration: options?.duration ?? 3000,
    onClose: onMessageClose
  })
  return true
}

/**
 * 标记服务不可用状态
 * 当检测到后端服务不可用时调用，会抑制后续所有组件层的独立错误消息
 */
export function markServiceDown(message?: string) {
  if (serviceDownActive) return
  serviceDownActive = true

  // 清除所有当前消息
  Message.clear()
  visibleCount = 0
  aggregateShown = false

  // 显示一条全局服务不可用提示
  originalError({
    content: message || '服务暂时不可用，请稍后重试',
    id: SERVICE_DOWN_MSG_ID,
    duration: 0, // 不自动消失
    closable: true,
    onClose: () => {
      serviceDownActive = false
      onMessageClose()
    }
  })
  visibleCount++
}

/**
 * 标记服务恢复
 * 当检测到后端服务恢复时调用
 */
export function markServiceRecovered() {
  if (!serviceDownActive) return
  serviceDownActive = false
  Message.clear()
  visibleCount = 0
  aggregateShown = false
}

/**
 * 检查当前是否处于服务不可用状态
 */
export function isServiceDownActive(): boolean {
  return serviceDownActive
}

/**
 * 手动清除所有消息并重置状态
 */
export function clearAllMessages() {
  Message.clear()
  visibleCount = 0
  aggregateShown = false
  recentErrors.length = 0
}

/**
 * 安装全局消息节流拦截器
 *
 * 覆盖 Arco Design 的 Message.error / Message.warning，
 * 使得所有组件调用 Message.error() 时自动经过节流/去重/聚合逻辑。
 * 无需修改任何现有组件代码。
 *
 * 使用方式（main.ts 中）：
 * ```ts
 * import { installMessageThrottle } from '@/utils/messageThrottle'
 * installMessageThrottle()
 * ```
 */
export function installMessageThrottle() {
  // 覆盖 Message.error —— 所有 Message.error('xxx') 和 Message.error({...}) 自动走节流
  ;(Message as any).error = (config: string | MessageConfig, _appContext?: AppContext): MessageReturn => {
    // 字符串形式调用：Message.error('xxx')
    if (typeof config === 'string') {
      const shown = showError(config)
      return shown ? NOOP_RETURN : NOOP_RETURN
    }

    // 对象形式调用：Message.error({ content: 'xxx', id: 'xxx' })
    const content = typeof config.content === 'string' ? config.content : ''
    const id = config.id as string | undefined

    // 带 id 的消息直接透传给原始方法（Arco 原生去重）
    if (id) {
      return originalError(config)
    }

    // 无 id 且有文本内容的消息走节流逻辑
    if (content) {
      showError(content, { duration: config.duration, closable: config.closable })
      return NOOP_RETURN
    }

    // 其他情况（RenderFunction content 等）直接透传
    return originalError(config)
  }

  // 覆盖 Message.warning —— 同样的节流逻辑
  ;(Message as any).warning = (config: string | MessageConfig, _appContext?: AppContext): MessageReturn => {
    if (typeof config === 'string') {
      showWarning(config)
      return NOOP_RETURN
    }

    const content = typeof config.content === 'string' ? config.content : ''
    const id = config.id as string | undefined

    if (id) {
      return originalWarning(config)
    }

    if (content) {
      showWarning(content, { duration: config.duration })
      return NOOP_RETURN
    }

    return originalWarning(config)
  }
}
