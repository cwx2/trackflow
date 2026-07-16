/**
 * 字段英文标识 → 中文显示名映射
 *
 * 用于活动记录、仪表盘等场景中，将后端存储的英文字段标识转换为界面一致的中文标签。
 * 同时覆盖 camelCase 和 snake_case 形式，防御不同来源的字段命名风格。
 */
export const fieldLabelMap: Record<string, string> = {
  status: '状态',
  status_id: '状态',
  assignee: '负责人',
  assignee_id: '负责人',
  priority: '优先级',
  title: '标题',
  description: '描述',
  issueType: '类型',
  issue_type: '类型',
  sprint: '迭代',
  sprintId: '迭代',
  sprint_id: '迭代',
  dueDate: '截止日期',
  due_date: '截止日期',
  estimatedHours: '预估工时',
  estimated_hours: '预估工时',
  spentHours: '已花时间',
  spent_hours: '已花时间',
  spent_time: '花费时间',
  attachment: '附件',
  parent: '父工单',
  parentId: '父工单',
  parent_id: '父工单',
  tags: '标签',
  reporter: '报告人',
  reporterId: '报告人',
  reporter_id: '报告人',
}

/**
 * 将英文字段标识转换为中文显示名
 * @param name 英文字段标识（如 "status"、"assignee"）
 * @returns 中文显示名（如 "状态"、"负责人"），未匹配时 fallback 返回原始字段名
 */
export function localizeFieldName(name?: string | null): string | undefined {
  if (!name) return undefined
  return fieldLabelMap[name] || name
}

/**
 * 优先级英文值 → 中文映射
 */
export const priorityLabelMap: Record<string, string> = {
  Critical: '紧急',
  High: '高',
  Normal: '普通',
  Low: '低',
  critical: '紧急',
  high: '高',
  normal: '普通',
  low: '低',
}

/**
 * 状态英文名 → 中文映射
 * 覆盖 issue_status 表中的所有预置状态
 */
export const statusLabelMap: Record<string, string> = {
  'Open': '待处理',
  'In Progress': '进行中',
  'Code Review': '代码审查',
  'Testing': '测试中',
  'Done': '已完成',
  'Cancelled': '已取消',
  'Reopened': '重新打开',
  'Todo': '待办',
  'UI Todo': 'UI 待办',
  'Done (Local Env)': '本地完成',
  'No Test': '无需测试',
  'Pending Code Review': '等待审查',
  'Pending Publish': '等待发布',
  'Online': '已上线',
  'Solved': '已解决',
  'Closed': '已关闭',
  'Pending Cancel': '待取消',
  'Pending Extension': '待延期',
}

/**
 * 本地化状态名称
 * 统一用于系统中所有展示状态名称的位置（列表、详情、下拉、看板、筛选器等）
 * @param name 英文状态名（如 "Open"、"In Progress"）
 * @returns 中文状态名（如 "待处理"、"进行中"），未匹配时返回原值
 */
export function localizeStatusName(name?: string | null): string {
  if (!name) return '未知'
  return statusLabelMap[name] || name
}

/**
 * 根据字段名，本地化字段值
 * 适用于活动记录中展示 old_value / new_value 的场景
 * @param fieldName 字段标识（如 "priority"、"status"）
 * @param value 英文原值
 * @returns 中文值或原值
 */
export function localizeFieldValue(fieldName?: string | null, value?: string | null): string | undefined {
  if (!value) return undefined
  if (!fieldName) return value

  // 优先级字段
  if (fieldName === 'priority') {
    return priorityLabelMap[value] || value
  }

  // 状态字段
  if (fieldName === 'status' || fieldName === 'status_id') {
    return statusLabelMap[value] || value
  }

  return value
}

/**
 * 操作类型 action 中文映射（统一维护）
 *
 * - short: 用于 Dashboard 等拼接场景（"{user} {short} {issueKey}"）
 * - full: 用于 ActivityStream 独立句子场景
 *
 * 同时覆盖后端可能返回的长形式（created）和短形式（create）。
 */
export const actionLabelMap: Record<string, { short: string; full: string }> = {
  create: { short: '创建了', full: '创建了此工单' },
  created: { short: '创建了', full: '创建了此工单' },
  update: { short: '更新了', full: '修改了工单' },
  updated: { short: '更新了', full: '修改了工单' },
  status_change: { short: '变更了状态', full: '修改了状态' },
  status_changed: { short: '变更了状态', full: '修改了状态' },
  assign: { short: '分配了', full: '修改了负责人' },
  assigned: { short: '分配了', full: '修改了负责人' },
  comment: { short: '评论了', full: '添加了评论' },
  commented: { short: '评论了', full: '添加了评论' },
  comment_updated: { short: '编辑了评论', full: '编辑了评论' },
  comment_deleted: { short: '删除了评论', full: '删除了评论' },
  attach: { short: '添加了附件', full: '添加了附件' },
  attached: { short: '添加了附件', full: '添加了附件' },
  attachment_added: { short: '添加了附件', full: '添加了附件' },
  attachment_removed: { short: '删除了附件', full: '删除了附件' },
  time_logged: { short: '记录了工时', full: '记录了工时' },
  time_removed: { short: '删除了工时', full: '删除了工时' },
  deleted: { short: '删除了', full: '删除了此工单' },
  restored: { short: '恢复了', full: '恢复了此工单' },
  reopened: { short: '重新打开了', full: '重新打开了此工单' },
  resolved: { short: '解决了', full: '解决了此工单' },
  auto_assigned: { short: '自动分配了', full: '自动分配了负责人' },
  auto_assign_skipped: { short: '跳过了自动分配', full: '跳过了自动分配' },
}

/**
 * 本地化 action（完整句子模式，用于 ActivityStream）
 */
export function localizeAction(action?: string | null): string {
  if (!action) return '未知操作'
  return actionLabelMap[action]?.full || action
}

/**
 * 本地化 action（简短动词模式，用于 Dashboard 拼接）
 */
export function localizeActionShort(action?: string | null): string {
  if (!action) return '操作了'
  return actionLabelMap[action]?.short || action
}
