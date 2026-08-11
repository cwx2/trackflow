/**
 * utils/date.ts — 日期/时间格式化工具
 *
 * 设计原则（参考 date-fns）：
 *   - 纯函数，不修改入参，永远返回新值
 *   - 所有函数接受 DateInput（Date | string | null | undefined），内部做 toDate() 转换
 *   - 函数可组合：复杂操作由简单函数叠加实现
 *
 * 函数分组：
 *   [类型]    DateInput
 *   [转换]    toDate / toDateKey
 *   [判断]    isToday / isWeekend / isSameDay
 *   [导航]    startOfWeek / addDays / addMonths / addWeeks
 *   [格式]    formatRelativeTime / formatDateTime / formatDate / formatDateDisplay
 */

// ─── 类型 ────────────────────────────────────────────────────────────────────

/** 所有日期函数都接受此类型的入参，内部统一用 toDate() 转换 */
export type DateInput = Date | string | null | undefined

// ─── 转换 ────────────────────────────────────────────────────────────────────

/**
 * 将任意日期输入转换为 Date 对象。无效输入返回 null。
 */
export function toDate(input: DateInput): Date | null {
  if (!input) return null
  const d = input instanceof Date ? input : new Date(input)
  return isNaN(d.getTime()) ? null : d
}

/**
 * 将日期格式化为标准键值 "YYYY-MM-DD"。
 * 接受 Date 对象或字符串，统一出口，无效输入返回 ''。
 */
export function toDateKey(input: DateInput): string {
  const d = toDate(input)
  if (!d) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

// ─── 判断 ────────────────────────────────────────────────────────────────────

/**
 * 判断某个日期是否是今天。接受 Date 对象或 "YYYY-MM-DD" 字符串。
 */
export function isToday(input: DateInput): boolean {
  const key = toDateKey(input)
  return !!key && key === toDateKey(new Date())
}

/**
 * 判断某个日期是否是周末（周六或周日）。
 */
export function isWeekend(input: DateInput): boolean {
  const d = toDate(input)
  if (!d) return false
  const day = d.getDay()
  return day === 0 || day === 6
}

/**
 * 判断两个日期是否是同一天（忽略时间部分）。
 */
export function isSameDay(a: DateInput, b: DateInput): boolean {
  const ka = toDateKey(a)
  return !!ka && ka === toDateKey(b)
}

// ─── 导航 ────────────────────────────────────────────────────────────────────

/**
 * 返回某日期所在周的周一（ISO 周：周一为第一天）。返回 "YYYY-MM-DD"。
 */
export function startOfWeek(input: DateInput): string {
  const d = toDate(input)
  if (!d) return ''
  const date = new Date(d)
  const day = date.getDay()
  const diff = date.getDate() - day + (day === 0 ? -6 : 1)
  date.setDate(diff)
  return toDateKey(date)
}

/**
 * 在某个日期基础上加减若干天，返回新的 "YYYY-MM-DD" 字符串。
 * @param days   正数向未来，负数向过去
 */
export function addDays(input: DateInput, days: number): string {
  const d = toDate(input)
  if (!d) return ''
  const result = new Date(d)
  result.setDate(result.getDate() + days)
  return toDateKey(result)
}

/**
 * 在某个月份基础上加减若干月，返回新的 "YYYY-MM" 字符串。
 * @param yearMonth  格式 "YYYY-MM"
 * @param months     正数向未来，负数向过去
 */
export function addMonths(yearMonth: string, months: number): string {
  const [year, month] = yearMonth.split('-').map(Number)
  const d = new Date(year, month - 1 + months, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

/**
 * 在某个周开始日期基础上加减若干周，返回新的 "YYYY-MM-DD" 字符串。
 * @param weekStart  周一的 "YYYY-MM-DD"
 */
export function addWeeks(weekStart: string, weeks: number): string {
  return addDays(weekStart, weeks * 7)
}

// ─── 格式化（输出给用户看的字符串） ─────────────────────────────────────────

/**
 * 相对时间格式化。
 * - < 1 分钟  → "刚刚"
 * - < 1 小时  → "N 分钟前"
 * - < 24 小时 → "N 小时前"
 * - < 30 天   → "N 天前"
 * - 更早      → "2026-08-11" 绝对日期
 */
export function formatRelativeTime(input: DateInput): string {
  const d = toDate(input)
  if (!d) return ''
  const diffSec = Math.floor((Date.now() - d.getTime()) / 1000)
  if (diffSec < 60) return '刚刚'
  const diffMin = Math.floor(diffSec / 60)
  if (diffMin < 60) return `${diffMin} 分钟前`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour} 小时前`
  const diffDay = Math.floor(diffHour / 24)
  if (diffDay < 30) return `${diffDay} 天前`
  return toDateKey(d)
}

/**
 * 日期+时间格式化，精确到分钟。输出示例：2026-08-11 14:30
 */
export function formatDateTime(input: DateInput): string {
  const d = toDate(input)
  if (!d) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/**
 * 仅日期格式化，输出 "YYYY-MM-DD"。接受字符串或 Date 对象。
 */
export function formatDate(input: DateInput): string {
  return toDateKey(input)
}

/**
 * 人类友好的日期显示格式，输出 "YYYY/M/D"（不补零）。
 * 适合在 UI 日历标题、tooltip 中使用。
 */
export function formatDateDisplay(input: DateInput): string {
  const d = toDate(input)
  if (!d) return ''
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}`
}

// ─── 截止日期相关（Sprint 规划卡片等场景） ───────────────────────────────────

export type DueDateStatus = 'overdue' | 'due-soon' | 'normal' | 'none'

/**
 * 判断截止日期状态。
 * - overdue：已过期
 * - due-soon：3 天内到期（含今天）
 * - normal：超过 3 天
 * - none：无截止日期
 */
export function getDueDateStatus(input: DateInput): DueDateStatus {
  const d = toDate(input)
  if (!d) return 'none'
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const due = new Date(d)
  due.setHours(0, 0, 0, 0)
  const diffMs = due.getTime() - today.getTime()
  const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24))
  if (diffDays < 0) return 'overdue'
  if (diffDays <= 3) return 'due-soon'
  return 'normal'
}

/**
 * 格式化截止日期的紧凑显示文本。
 * - 已过期：返回 "逾期 N天"
 * - 今天到期：返回 "今天到期"
 * - 3 天内：返回 "N天后到期"
 * - 更远：返回 "M/D"（不含年）
 */
export function formatDueDate(input: DateInput): string {
  const d = toDate(input)
  if (!d) return ''
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const due = new Date(d)
  due.setHours(0, 0, 0, 0)
  const diffMs = due.getTime() - today.getTime()
  const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24))
  if (diffDays < 0) return `逾期 ${Math.abs(diffDays)}天`
  if (diffDays === 0) return '今天到期'
  if (diffDays <= 3) return `${diffDays}天后到期`
  return `${due.getMonth() + 1}/${due.getDate()}`
}
