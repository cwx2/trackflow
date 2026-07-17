import { ref, readonly } from 'vue'
import { systemSettingApi, type TimeTrackingSettingsVO } from '@/api/systemSetting'

/**
 * 时间追踪设置 composable
 *
 * 提供系统级时间追踪配置的读取和缓存。
 * 配置加载一次后缓存，避免重复请求。
 */

// 模块级缓存（跨组件共享）
const settings = ref<TimeTrackingSettingsVO>({
  hoursPerDay: 8,
  workingDays: [1, 2, 3, 4, 5]
})
const loaded = ref(false)
const loading = ref(false)

export function useTimeTrackingSettings() {
  /**
   * 加载时间追踪设置（带缓存，仅首次加载时请求）
   */
  async function loadSettings(force = false) {
    if (loaded.value && !force) return settings.value
    if (loading.value) return settings.value

    loading.value = true
    try {
      const res = await systemSettingApi.getTimeTrackingSettings()
      if (res.code === 0 && res.data) {
        settings.value = res.data
        loaded.value = true
      }
    } catch (e) {
      console.warn('加载时间追踪设置失败，使用默认值', e)
    } finally {
      loading.value = false
    }
    return settings.value
  }

  /**
   * 每日工作分钟数（hoursPerDay * 60）
   */
  function minutesPerDay(): number {
    return settings.value.hoursPerDay * 60
  }

  /**
   * 每周工作分钟数（hoursPerDay * workingDays.length * 60）
   */
  function minutesPerWeek(): number {
    return settings.value.hoursPerDay * settings.value.workingDays.length * 60
  }

  /**
   * 判断某个星期几是否为工作日（1=周一, 7=周日）
   */
  function isWorkingDay(dayOfWeek: number): boolean {
    return settings.value.workingDays.includes(dayOfWeek)
  }

  /**
   * 格式化配额文本（如 "8h"）
   */
  function quotaText(): string {
    return `${settings.value.hoursPerDay}h`
  }

  /**
   * 强制刷新设置缓存
   */
  async function refreshSettings() {
    return loadSettings(true)
  }

  return {
    settings: readonly(settings),
    loaded: readonly(loaded),
    loading: readonly(loading),
    loadSettings,
    refreshSettings,
    minutesPerDay,
    minutesPerWeek,
    isWorkingDay,
    quotaText
  }
}
