import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { timeEntryApi } from '@/api/timeEntry'
import type { TimeEntryVO } from '@/api/timeEntry'

/**
 * 计时器状态管理（跨页面同步）
 * 管理当前用户的活跃计时器：启动/停止/实时经过时间
 */
export const useTimerStore = defineStore('timer', () => {
  // State
  const activeTimer = ref<TimeEntryVO | null>(null)
  const elapsedSeconds = ref(0)
  const loading = ref(false)
  const initialized = ref(false)

  let intervalId: ReturnType<typeof setInterval> | null = null

  // Getters
  const isRunning = computed(() => activeTimer.value != null)

  const elapsedDisplay = computed(() => {
    const total = elapsedSeconds.value
    const h = Math.floor(total / 3600)
    const m = Math.floor((total % 3600) / 60)
    const s = total % 60
    if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
    return `${m}:${String(s).padStart(2, '0')}`
  })

  const issueKey = computed(() => activeTimer.value?.issueKey || '')
  const issueTitle = computed(() => activeTimer.value?.issueTitle || '')
  const issueId = computed(() => activeTimer.value?.issueId || '')

  // Actions

  /** 初始化：页面加载时调用，获取活跃计时器 */
  async function init() {
    if (initialized.value) return
    initialized.value = true
    await refresh()
  }

  /** 刷新活跃计时器状态（从后端获取） */
  async function refresh() {
    try {
      const res = await timeEntryApi.getActiveTimer()
      if (res.code === 0 && res.data) {
        activeTimer.value = res.data
        startTicking()
      } else {
        activeTimer.value = null
        stopTicking()
      }
    } catch {
      // silently fail - timer badge is non-critical
    }
  }

  /** 启动计时器 */
  async function startTimer(issueId: string, description?: string) {
    loading.value = true
    try {
      const res = await timeEntryApi.startTimer({ issueId, description })
      if (res.code === 0 && res.data) {
        activeTimer.value = res.data
        startTicking()
        return { success: true }
      }
      return { success: false, message: res.message || '启动失败' }
    } catch (e: any) {
      const msg = e.response?.data?.message || '启动计时器失败'
      return { success: false, message: msg }
    } finally {
      loading.value = false
    }
  }

  /** 停止计时器 */
  async function stopTimer() {
    if (!activeTimer.value) return { success: false, message: '没有活跃计时器' }
    loading.value = true
    try {
      const res = await timeEntryApi.stopTimer(activeTimer.value.id)
      if (res.code === 0) {
        activeTimer.value = null
        stopTicking()
        return { success: true, entry: res.data }
      }
      return { success: false, message: res.message || '停止失败' }
    } catch (e: any) {
      const msg = e.response?.data?.message || '停止计时器失败'
      return { success: false, message: msg }
    } finally {
      loading.value = false
    }
  }

  /** 开始本地计时（每秒更新 elapsedSeconds） */
  function startTicking() {
    stopTicking()
    updateElapsed()
    intervalId = setInterval(updateElapsed, 1000)
  }

  /** 停止本地计时 */
  function stopTicking() {
    if (intervalId !== null) {
      clearInterval(intervalId)
      intervalId = null
    }
    elapsedSeconds.value = 0
  }

  /** 根据 createdAt 计算经过秒数 */
  function updateElapsed() {
    const timer = activeTimer.value
    if (!timer) {
      elapsedSeconds.value = 0
      return
    }
    // Use startedAt (same as createdAt) for timer start time
    const startStr = timer.startedAt || timer.createdAt
    if (!startStr) {
      elapsedSeconds.value = 0
      return
    }
    const startTime = new Date(startStr).getTime()
    const now = Date.now()
    elapsedSeconds.value = Math.max(0, Math.floor((now - startTime) / 1000))
  }

  /** 清理（退出登录时调用） */
  function reset() {
    activeTimer.value = null
    stopTicking()
    initialized.value = false
  }

  return {
    // State
    activeTimer,
    elapsedSeconds,
    loading,
    // Getters
    isRunning,
    elapsedDisplay,
    issueKey,
    issueTitle,
    issueId,
    // Actions
    init,
    refresh,
    startTimer,
    stopTimer,
    reset,
  }
})
