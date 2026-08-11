/**
 * utils/automation.ts — 自动化执行结果工具函数
 *
 * 统一替代 ExecutionDetailDrawer 和 ExecutionHistoryView
 * 各自重复定义的 statusColor / statusLabel / formatJson。
 */

/** 执行状态 → Arco Design 颜色标签（用于 <a-tag :color="...">） */
export function executionStatusColor(status: string): string {
  const map: Record<string, string> = {
    running:   'blue',
    success:   'green',
    failed:    'red',
    cancelled: 'gray',
    skipped:   'orange',
  }
  return map[status] ?? 'gray'
}

/** 执行状态 → 中文标签 */
export function executionStatusLabel(status: string): string {
  const map: Record<string, string> = {
    running:   '运行中',
    success:   '成功',
    failed:    '失败',
    cancelled: '已取消',
    skipped:   '已跳过',
    pending:   '等待中',
  }
  return map[status] ?? status
}

/**
 * 将任意值格式化为可读的 JSON 字符串（调试/日志展示）。
 * null/undefined → "(空)"，对象/数组 → 缩进 JSON，其他 → String()
 */
export function formatJson(val: unknown): string {
  if (val == null) return '(空)'
  try {
    return JSON.stringify(val, null, 2)
  } catch {
    return String(val)
  }
}
