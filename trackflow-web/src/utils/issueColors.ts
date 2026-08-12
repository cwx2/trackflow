/**
 * Issue 语义颜色常量 — 全系统单一来源
 *
 * 优先级颜色和工单类型颜色已迁移到后端自定义字段系统动态管理，
 * 前端通过 API 返回的 priorityColor / issueTypeColor 字段获取。
 *
 * 本文件仅保留：
 * - 工作类型颜色（time tracking，暂未迁移到动态系统）
 * - 默认回退色（当 API 未返回颜色时使用）
 */

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
