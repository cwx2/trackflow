/**
 * utils/timesheet.ts — 工时场景专属工具函数
 *
 * 职责：
 * - 日历视图数据生成（周视图/月视图的格子数组）
 * - 工作日计算（排除周末，支持自定义工作日配置）
 * - 工时文本解析（"2h30m" / "1.5h" / "30m" → 分钟数）
 * - 时长格式化（紧凑格式 "2h30m"，区别于通用的 "2h 30m"）
 *
 * 依赖：utils/date.ts 的基础日期函数
 */

import { toDate, toDateKey, addDays } from './date'
import type { DateInput } from './date'

// ─── 日历视图数据 ─────────────────────────────────────────────────────────────

/** 周视图中单天的信息 */
export interface WeekDayInfo {
  /** "YYYY-MM-DD" */
  date: string
  /** 日期数字（1-31） */
  dateNum: number
  /** 中文星期名（周一 - 周日） */
  dayName: string
  /** 是否周末 */
  isWeekend: boolean
}

/** 月视图中单天的信息 */
export interface MonthDayInfo {
  /** "YYYY-MM-DD" */
  date: string
  /** 日期数字（1-31） */
  dateNum: number
  /** 是否周末 */
  isWeekend: boolean
  /** 是否属于当前月（false = 补位天） */
  currentMonth: boolean
}

/**
 * 生成周视图的 7 天数组（周一 → 周日）。
 *
 * @param weekStart     周一的日期（Date 或 "YYYY-MM-DD"）
 * @param isWorkingDay  判断某个 ISO weekday（1=周一…7=周日）是否工作日的函数
 *                      不传时默认周一到周五为工作日
 * @returns             WeekDayInfo[7]
 */
export function getWeekDays(
  weekStart: DateInput,
  isWorkingDay: (isoWeekday: number) => boolean = (d) => d >= 1 && d <= 5
): WeekDayInfo[] {
  const result: WeekDayInfo[] = []
  const DAY_NAMES = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  for (let i = 0; i < 7; i++) {
    const dateStr = addDays(weekStart, i)
    const d = toDate(dateStr)!
    const jsDow = d.getDay()
    const isoDow = jsDow === 0 ? 7 : jsDow
    result.push({
      date:      dateStr,
      dateNum:   d.getDate(),
      dayName:   DAY_NAMES[jsDow],
      isWeekend: !isWorkingDay(isoDow),
    })
  }
  return result
}

/**
 * 生成月视图的日历格子数组（固定 6 行 × 7 列 = 42 格，补位显示上下月的天）。
 *
 * @param year          年（如 2026）
 * @param month         月，1-indexed（如 8 = 8月）
 * @param isWorkingDay  判断某个 ISO weekday 是否工作日的函数，默认周一到周五
 * @returns             MonthDayInfo[42]
 */
export function getMonthDays(
  year: number,
  month: number,
  isWorkingDay: (isoWeekday: number) => boolean = (d) => d >= 1 && d <= 5
): MonthDayInfo[] {
  const result: MonthDayInfo[] = []
  const firstDay = new Date(year, month - 1, 1)
  const firstDayOfWeek = (firstDay.getDay() + 6) % 7  // 0=周一

  for (let i = 0; i < 42; i++) {
    const dayOffset = i - firstDayOfWeek
    const date = new Date(year, month - 1, 1 + dayOffset)
    const jsDow = date.getDay()
    const isoDow = jsDow === 0 ? 7 : jsDow
    const currentMonth = date.getMonth() === month - 1

    result.push({
      date:         toDateKey(date),
      dateNum:      date.getDate(),
      isWeekend:    !isWorkingDay(isoDow),
      currentMonth,
    })
  }

  return result
}

// ─── 工作日计算 ───────────────────────────────────────────────────────────────

/**
 * 获取日期范围内的工作日列表（默认排除周六周日）。
 *
 * @param start        起始日期（含）
 * @param end          结束日期（含）
 * @param workingDays  哪些星期几算工作日，ISO weekday：1=周一...7=周日
 *                     不传时默认 [1,2,3,4,5]（周一到周五）
 * @returns            工作日的 "YYYY-MM-DD" 数组
 */
export function getWorkingDaysInRange(
  start: DateInput,
  end: DateInput,
  workingDays: number[] = [1, 2, 3, 4, 5]
): string[] {
  const result: string[] = []
  const startDate = toDate(start)
  const endDate = toDate(end)
  if (!startDate || !endDate) return result

  const current = new Date(startDate)
  while (current <= endDate) {
    // ISO weekday: 1=周一...7=周日
    const isoWeekday = current.getDay() === 0 ? 7 : current.getDay()
    if (workingDays.includes(isoWeekday)) {
      result.push(toDateKey(current))
    }
    current.setDate(current.getDate() + 1)
  }
  return result
}

// ─── 工时文本解析 ─────────────────────────────────────────────────────────────

/**
 * 将工时文本解析为分钟数。
 *
 * 支持格式：
 *   - "2h30m" / "2h 30m" / "2H30M"
 *   - "1.5h" / "1.5"（纯数字视为小时）
 *   - "30m" / "30min"
 *   - "1w" / "1d"（需提供 minutesPerDay/minutesPerWeek）
 *   - "1:30"（时:分格式）
 *
 * @param text           用户输入的工时文本
 * @param minutesPerDay  每工作日分钟数（默认 480 = 8h），用于解析 "1d" 格式
 * @returns              分钟数，解析失败返回 null
 */
export function parseDuration(
  text: string,
  minutesPerDay = 480
): number | null {
  const s = text.trim().toLowerCase()
  if (!s) return null

  // 时:分格式 "1:30"
  const colonMatch = s.match(/^(\d+):(\d{2})$/)
  if (colonMatch) {
    return parseInt(colonMatch[1]) * 60 + parseInt(colonMatch[2])
  }

  const minutesPerWeek = minutesPerDay * 5

  let total = 0
  let matched = false

  const weekMatch = s.match(/(\d+(?:\.\d+)?)\s*w/)
  const dayMatch  = s.match(/(\d+(?:\.\d+)?)\s*d/)
  const hourMatch = s.match(/(\d+(?:\.\d+)?)\s*h/)
  const minMatch  = s.match(/(\d+(?:\.\d+)?)\s*m(?:in)?/)

  if (weekMatch) { total += parseFloat(weekMatch[1]) * minutesPerWeek; matched = true }
  if (dayMatch)  { total += parseFloat(dayMatch[1])  * minutesPerDay;  matched = true }
  if (hourMatch) { total += parseFloat(hourMatch[1]) * 60;             matched = true }
  if (minMatch)  { total += parseFloat(minMatch[1]);                   matched = true }

  // 纯数字：视为小时
  if (!matched) {
    const num = parseFloat(s)
    if (!isNaN(num) && num > 0) return Math.round(num * 60)
    return null
  }

  return total > 0 ? Math.round(total) : null
}

/**
 * 将 "HH:MM" 字符串解析为分钟数。
 * 用于时间选择器（如 "09:30" → 570）。
 * 解析失败返回 undefined。
 */
export function parseTimeToMinutes(timeStr: string): number | undefined {
  if (!timeStr) return undefined
  const parts = timeStr.split(':')
  if (parts.length !== 2) return undefined
  const hours = parseInt(parts[0], 10)
  const minutes = parseInt(parts[1], 10)
  if (isNaN(hours) || isNaN(minutes)) return undefined
  return hours * 60 + minutes
}

// ─── 时长格式化 ───────────────────────────────────────────────────────────────

/**
 * 将分钟数格式化为紧凑工时字符串（用于工时记录输入框）。
 *
 * 与 utils/duration.ts 的 formatDuration 的区别：
 *   - formatDuration(90)        → "1h 30m"（带空格，用于展示）
 *   - formatDurationCompact(90) → "1h30m" （无空格，用于输入框回显）
 *
 * @param minutes  分钟数
 */
export function formatDurationCompact(minutes: number): string {
  if (!minutes || minutes <= 0) return '0m'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m}m`
  if (m === 0) return `${h}h`
  return `${h}h${m}m`
}

// ─── 重新导出 date.ts 中 timesheet 常用的函数，方便单一导入 ──────────────────

export { toDateKey, isToday, isWeekend, addDays, addWeeks, addMonths, startOfWeek, formatDate, formatDateDisplay } from './date'
export type { DateInput }


// ─── 工时条目查询（WeekGrid/MonthGrid 共用） ─────────────────────────────────

import type { TimeEntryVO } from '@/api/timeEntry'

/**
 * 从条目列表中筛选出指定日期的所有条目。
 * @param entries  全部工时条目
 * @param dateKey  "YYYY-MM-DD" 格式的日期键
 */
export function getDayEntries(entries: TimeEntryVO[], dateKey: string): TimeEntryVO[] {
  return entries.filter(e => e.workDate === dateKey)
}

/**
 * 计算指定日期的总工时（分钟）。
 */
export function getDayTotal(entries: TimeEntryVO[], dateKey: string): number {
  return getDayEntries(entries, dateKey).reduce((sum, e) => sum + (e.duration || 0), 0)
}

// ─── 工时类型标签 ────────────────────────────────────────────────────────────

const WORK_TYPE_LABELS: Record<string, string> = {
  Development:   '开发',
  Testing:       '测试',
  Documentation: '文档',
  Design:        '设计',
  Review:        '代码审查',
  Meeting:       '会议',
  Other:         '其他',
}

/**
 * 将工时类型英文 key 转换为中文标签。
 * 未知类型直接返回原值。
 */
export function workTypeLabel(type: string): string {
  return WORK_TYPE_LABELS[type] ?? type
}
