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
