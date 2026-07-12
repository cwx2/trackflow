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
