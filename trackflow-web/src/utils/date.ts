/**
 * utils/date.ts — 日期/时间格式化工具
 *
 * 统一替代项目中散落在 40+ 个组件里各自定义的
 * formatTime / formatDate / formatDateTime 函数。
 *
 * 三个函数覆盖项目中出现的全部格式需求：
 *   - formatRelativeTime  → "刚刚 / 5 分钟前 / 2 小时前 / 3 天前 / 2026-08-11"
 *   - formatDateTime      → "2026-08-11 14:30"（精确到分，列表/卡片首选）
 *   - formatDate          → "2026-08-11"（仅日期）
 *
 * 所有函数对空值/无效值安全，返回空字符串。
 */

/**
 * 相对时间格式化。
 * - < 1 分钟  → "刚刚"
 * - < 1 小时  → "N 分钟前"
 * - < 24 小时 → "N 小时前"
 * - < 30 天   → "N 天前"
 * - 更早      → "2026-08-11" 绝对日期
 */
export function formatRelativeTime(dateStr: string | null | undefined): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  if (isNaN(d.getTime())) return ''
  const diffMs = Date.now() - d.getTime()
  const diffSec = Math.floor(diffMs / 1000)
  if (diffSec < 60) return '刚刚'
  const diffMin = Math.floor(diffSec / 60)
  if (diffMin < 60) return `${diffMin} 分钟前`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour} 小时前`
  const diffDay = Math.floor(diffHour / 24)
  if (diffDay < 30) return `${diffDay} 天前`
  return formatDate(dateStr)
}

/**
 * 日期+时间格式化，精确到分钟。
 * 输出示例：2026-08-11 14:30
 */
export function formatDateTime(dateStr: string | null | undefined): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  if (isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/**
 * 仅日期格式化。
 * 输出示例：2026-08-11
 */
export function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  if (isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
