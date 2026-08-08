/**
 * 优先级颜色常量
 *
 * 集中管理 Issue 优先级对应的颜色，保证全系统一致。
 * 这些颜色由业务定义（对应后端 priority 字段值），不随主题变化。
 */

export const PRIORITY_COLORS: Record<string, string> = {
  '阻塞': '#b91c1c',
  '紧急': '#ef4444',
  '高': '#f59e0b',
  '普通': '#6366f1',
  '低': '#64748b',
  // English names (for backward compatibility)
  'Critical': '#ef4444',
  'High': '#f59e0b',
  'Normal': '#3b82f6',
  'Low': '#9ca3af'
}

/**
 * 获取优先级颜色，支持中英文名称
 */
export function getPriorityColor(priority: string): string {
  return PRIORITY_COLORS[priority] || '#6366f1'
}

/**
 * 优先级选项列表（用于过滤器和批量操作下拉）
 */
export const PRIORITY_OPTIONS = [
  { id: '阻塞', label: '阻塞', value: '阻塞', color: PRIORITY_COLORS['阻塞'] },
  { id: '紧急', label: '紧急', value: '紧急', color: PRIORITY_COLORS['紧急'] },
  { id: '高', label: '高', value: '高', color: PRIORITY_COLORS['高'] },
  { id: '普通', label: '普通', value: '普通', color: PRIORITY_COLORS['普通'] },
  { id: '低', label: '低', value: '低', color: PRIORITY_COLORS['低'] }
]

/**
 * Issue 类型对应颜色
 */
export const ISSUE_TYPE_COLORS: Record<string, string> = {
  'Bug': '#d32f2f',
  'Task': '#1976d2',
  'Feature': '#388e3c',
  'Epic': '#7b1fa2',
  'Story': '#f57c00'
}

/**
 * 获取 Issue 类型颜色
 */
export function getIssueTypeColor(type: string): string {
  return ISSUE_TYPE_COLORS[type] || '#6366f1'
}

/**
 * 默认 badge 颜色（当后端数据没有指定颜色时的回退色）
 */
export const DEFAULT_BADGE_COLOR = '#3b82f6'
export const DEFAULT_STATUS_COLOR = '#6b7280'
export const DEFAULT_TAG_COLOR = '#6b7280'
