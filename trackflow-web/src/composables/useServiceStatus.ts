import { ref, computed } from 'vue'
import { markServiceDown as throttleMarkServiceDown, markServiceRecovered as throttleMarkServiceRecovered } from '@/utils/messageThrottle'

/**
 * 全局服务状态追踪器
 *
 * 追踪连续 API 失败次数，当超过阈值时判定后端服务不可用。
 * 供 AppLayout 显示全局错误横幅，供列表组件区分"数据为空"和"加载失败"。
 */

// 模块级单例状态（所有组件共享同一份数据）
const consecutiveFailures = ref(0)
const lastErrorTime = ref<number | null>(null)
const lastErrorMessage = ref('')

/** 连续失败多少次后判定服务不可用 */
const FAILURE_THRESHOLD = 3

/**
 * 判定是否为"服务级"错误（非业务错误）
 * - 500+ 表示服务端内部错误
 * - 0 / undefined 表示网络不可达（ERR_CONNECTION_REFUSED）
 */
function isServiceError(status: number | undefined): boolean {
  if (!status || status === 0) return true // 网络不可达
  return status >= 500
}

/**
 * 记录一次 API 失败
 */
export function reportApiFailure(status: number | undefined, message?: string) {
  if (!isServiceError(status)) return // 400/401/403/404 等不算服务不可用

  consecutiveFailures.value++
  lastErrorTime.value = Date.now()
  lastErrorMessage.value = message || getDefaultMessage(status)

  // 当连续失败超过阈值时，通知消息节流器进入服务不可用模式
  if (consecutiveFailures.value >= FAILURE_THRESHOLD) {
    throttleMarkServiceDown(lastErrorMessage.value)
  }
}

/**
 * 记录一次 API 成功（重置连续失败计数）
 */
export function reportApiSuccess() {
  if (consecutiveFailures.value > 0) {
    const wasDown = consecutiveFailures.value >= FAILURE_THRESHOLD
    consecutiveFailures.value = 0
    lastErrorMessage.value = ''
    // 如果之前处于服务不可用状态，通知消息节流器恢复
    if (wasDown) {
      throttleMarkServiceRecovered()
    }
  }
}

/**
 * 手动清除错误状态（用户点击"重试"后）
 */
export function clearServiceError() {
  consecutiveFailures.value = 0
  lastErrorMessage.value = ''
  lastErrorTime.value = null
}

function getDefaultMessage(status: number | undefined): string {
  if (!status || status === 0) return '无法连接到服务器，请检查网络连接'
  if (status >= 500) return '服务暂时不可用，请稍后重试'
  return '服务异常'
}

/**
 * 组合式函数：在组件中使用服务状态
 */
export function useServiceStatus() {
  const isServiceDown = computed(() => consecutiveFailures.value >= FAILURE_THRESHOLD)

  const errorMessage = computed(() => {
    if (!isServiceDown.value) return ''
    return lastErrorMessage.value || '服务暂时不可用，请稍后重试'
  })

  const failureCount = computed(() => consecutiveFailures.value)

  return {
    isServiceDown,
    errorMessage,
    failureCount,
    clearServiceError
  }
}
