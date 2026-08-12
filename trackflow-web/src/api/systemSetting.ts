import request from './request'
import type { R } from './types'

export interface TimeTrackingSettingsVO {
  /** 每日工作小时数（1-24） */
  hoursPerDay: number
  /** 每周工作日列表（1=周一, 7=周日） */
  workingDays: number[]
}

export interface UpdateTimeTrackingSettingsDTO {
  hoursPerDay: number
  workingDays: number[]
  /** 工时重新计算策略（hoursPerDay 变更时必填） */
  recalculationStrategy?: 'PRESERVE_MINUTES' | 'PRESERVE_DAYS'
}

export interface TimeTrackingRecalculationResultVO {
  settings: TimeTrackingSettingsVO
  recalculated: boolean
  strategy: string | null
  affectedTimeEntries: number
  affectedEstimations: number
  oldHoursPerDay: number
  newHoursPerDay: number
}

export const systemSettingApi = {
  /** 获取时间追踪设置 */
  getTimeTrackingSettings() {
    return request.get<any, R<TimeTrackingSettingsVO>>('/system/settings/time-tracking')
  },

  /** 更新时间追踪设置（需要管理员权限） */
  updateTimeTrackingSettings(data: UpdateTimeTrackingSettingsDTO) {
    return request.put<any, R<TimeTrackingRecalculationResultVO>>('/system/settings/time-tracking', data)
  },

  /** 获取系统调色板颜色列表 */
  getColorPalette() {
    return request.get<any, R<string[]>>('/system/settings/color-palette')
  },

  /** 更新系统调色板颜色列表（需要管理员权限） */
  updateColorPalette(colors: string[]) {
    return request.put<any, R<string[]>>('/system/settings/color-palette', { colors })
  }
}
