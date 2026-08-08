/**
 * Issue 语义颜色常量 — 全系统单一来源
 *
 * 集中管理优先级、类型、工作类型对应的颜色。
 * 这些颜色由业务定义（对应后端字段值），不随 UI 主题变化。
 *
 * ⚠️ 其他文件禁止重复定义这些颜色，应通过 import 引用。
 */

// ─── 优先级颜色 ─────────────────────────────────────────────────

export const PRIORITY_COLORS: Record<string, string> = {
  '阻塞': '#b91c1c',
  '紧急': '#ef4444',
  '高': '#f59e0b',
  '普通': '#6366f1',
  '低': '#64748b',
  // 兼容历史英文值
  'Show-stopper': '#b91c1c',
  'Critical': '#ef4444',
  'High': '#f59e0b',
  'Medium': '#6366f1',
  'Normal': '#6366f1',
  'Low': '#64748b',
}

/** 获取优先级颜色，支持中英文名称 */
export function getPriorityColor(priority: string): string {
  return PRIORITY_COLORS[priority] || '#6366f1'
}

/** 优先级选项列表（用于过滤器和批量操作下拉） */
export const PRIORITY_OPTIONS = [
  { id: '阻塞', label: '阻塞', value: '阻塞', color: PRIORITY_COLORS['阻塞'] },
  { id: '紧急', label: '紧急', value: '紧急', color: PRIORITY_COLORS['紧急'] },
  { id: '高', label: '高', value: '高', color: PRIORITY_COLORS['高'] },
  { id: '普通', label: '普通', value: '普通', color: PRIORITY_COLORS['普通'] },
  { id: '低', label: '低', value: '低', color: PRIORITY_COLORS['低'] },
]

// ─── 工单类型颜色 ───────────────────────────────────────────────

export const ISSUE_TYPE_COLORS: Record<string, string> = {
  'Bug': '#ef4444',
  '缺陷': '#ef4444',
  'Task': '#6366f1',
  '任务': '#6366f1',
  'Feature': '#22c55e',
  '需求': '#22c55e',
  'Epic': '#a855f7',
  '史诗': '#a855f7',
  'Story': '#3b82f6',
  '故事': '#3b82f6',
}

/** 获取 Issue 类型颜色 */
export function getIssueTypeColor(type: string): string {
  return ISSUE_TYPE_COLORS[type] || '#6366f1'
}

// ─── 工作类型颜色（Time Tracking） ──────────────────────────────

export const WORK_TYPE_COLORS: Record<string, string> = {
  'Development': '#58a6ff',
  '开发': '#58a6ff',
  'Testing': '#3fb950',
  '测试': '#3fb950',
  'Documentation': '#d29922',
  '文档': '#d29922',
  'Design': '#a371f7',
  '设计': '#a371f7',
  'Review': '#f0883e',
  '代码审查': '#f0883e',
  'Meeting': '#8b949e',
  '会议': '#8b949e',
  'Other': '#6e7681',
  '其他': '#6e7681',
}

/** 默认工作类型选项（后端数据不可用时的回退） */
export const WORK_TYPE_OPTIONS = [
  { id: 'Development', name: '开发', color: WORK_TYPE_COLORS['Development'] },
  { id: 'Testing', name: '测试', color: WORK_TYPE_COLORS['Testing'] },
  { id: 'Documentation', name: '文档', color: WORK_TYPE_COLORS['Documentation'] },
  { id: 'Design', name: '设计', color: WORK_TYPE_COLORS['Design'] },
  { id: 'Review', name: '代码审查', color: WORK_TYPE_COLORS['Review'] },
  { id: 'Meeting', name: '会议', color: WORK_TYPE_COLORS['Meeting'] },
  { id: 'Other', name: '其他', color: WORK_TYPE_COLORS['Other'] },
]

// ─── 默认回退色 ─────────────────────────────────────────────────

/** 默认 badge 颜色（当后端数据没有指定颜色时的回退色） */
export const DEFAULT_BADGE_COLOR = '#3b82f6'
export const DEFAULT_STATUS_COLOR = '#6b7280'
export const DEFAULT_TAG_COLOR = '#6b7280'
export const DEFAULT_NODE_COLOR = '#6366f1'
