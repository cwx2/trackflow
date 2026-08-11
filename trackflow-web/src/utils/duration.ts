/**
 * utils/duration.ts — 时长格式化工具
 *
 * 统一替代 TimesheetView / WeekGrid / MonthGrid / SpentTimePopover /
 * WorkTimeForm / TimeReportTab 等 11 处各自定义的 formatDuration。
 *
 * 单位：分钟（与后端 spent_time 字段保持一致）。
 */

/**
 * 将分钟数格式化为紧凑时长字符串。
 * - 0         → "0m"
 * - 45        → "45m"
 * - 90        → "1h 30m"
 * - 120       → "2h"
 * - null/负数 → "0m"
 *
 * @param minutes 分钟数（整数），允许 null / undefined
 */
export function formatDuration(minutes: number | null | undefined): string {
  if (!minutes || minutes <= 0) return '0m'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m}m`
  if (m === 0) return `${h}h`
  return `${h}h ${m}m`
}

/**
 * 将毫秒数格式化为执行时长字符串（自动化执行历史场景）。
 * - < 1s      → "NNNms"
 * - < 1min    → "N.Ns"
 * - ≥ 1min    → "Nm Ns"
 *
 * @param ms 毫秒数，允许 null / undefined
 */
export function formatDurationMs(ms: number | null | undefined): string {
  if (!ms || ms <= 0) return '-'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  return `${Math.floor(ms / 60000)}m ${Math.floor((ms % 60000) / 1000)}s`
}
