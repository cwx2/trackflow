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
  action_rule: '自动化规则',
  issue_key: '工单编号',
  issueKey: '工单编号',
  project: '项目',
  project_id: '项目',
  projectId: '项目',
  link: '关联',
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
 * Issue 类型英文值 → 中文映射
 * 统一用于系统中所有展示 Issue 类型名称的位置（列表、详情、筛选器、看板、自定义字段等）
 */
export const issueTypeLabelMap: Record<string, string> = {
  Bug: '缺陷',
  Task: '任务',
  Feature: '需求',
  Epic: '史诗',
  Story: '故事',
}

/**
 * 本地化 Issue 类型名称
 * @param type 英文类型值（如 "Bug"、"Task"）
 * @returns 中文类型名（如 "缺陷"、"任务"），未匹配时返回原值
 */
export function localizeIssueType(type?: string | null): string {
  if (!type) return '未知'
  return issueTypeLabelMap[type] || type
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
 * 本地化优先级名称
 * @param priority 英文优先级值（如 "Normal"、"High"）
 * @returns 中文优先级名（如 "普通"、"高"），未匹配时返回原值
 */
export function localizePriority(priority?: string | null): string {
  if (!priority) return '普通'
  return priorityLabelMap[priority] || priority
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
  'Needs Fix': '待修复',
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
 * 状态分类英文 → 中文映射
 */
export const categoryLabelMap: Record<string, string> = {
  open: '待处理',
  in_progress: '进行中',
  done: '已完成',
  cancelled: '已取消',
}

/**
 * 本地化状态分类名称
 * @param category 英文分类名（如 "open"、"in_progress"）
 * @returns 中文分类名（如 "待处理"、"进行中"），未匹配时返回原值
 */
export function localizeCategoryName(category?: string | null): string {
  if (!category) return '未知'
  return categoryLabelMap[category] || category
}

/**
 * 判断值是否看起来像内部标识符（应该被过滤的技术名称）
 * 例如：TestOnlyStatus、TEST_STATUS、InternalState 等
 * @param value 字符串值
 * @returns 是否为内部标识符
 */
function looksLikeInternalIdentifier(value: string): boolean {
  // 1. 包含 Test、Internal、Debug、Mock、Dummy、Temp 等测试/内部关键词（不区分大小写）
  const testKeywords = /test|internal|debug|mock|dummy|temp|placeholder|dev\b/i
  if (testKeywords.test(value)) return true

  // 2. 全大写带下划线的枚举风格（如 IN_PROGRESS、TODO_STATUS）
  if (/^[A-Z][A-Z_0-9]+$/.test(value)) return true

  // 3. PascalCase 且超过一个单词但不含中文（如 TestOnlyStatus、InProgressState）
  // 但排除已知的合法英文状态名（如 "In Progress"、"Code Review"）
  const knownEnglishStatuses = [
    'Open', 'In Progress', 'Code Review', 'Testing', 'Done', 'Cancelled',
    'Reopened', 'Todo', 'UI Todo', 'Online', 'Solved', 'Closed', 'Pending'
  ]
  if (!knownEnglishStatuses.some(s => value.includes(s))) {
    // 检测 PascalCase 多词形式（如 TestOnlyStatus）
    if (/^[A-Z][a-z]+(?:[A-Z][a-z]+)+$/.test(value)) return true
  }

  return false
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

  // 状态字段：额外处理无法识别的历史状态值
  if (fieldName === 'status' || fieldName === 'status_id') {
    const localized = statusLabelMap[value]
    if (localized) return localized
    // 如果值看起来像内部标识符（如 TestOnlyStatus），显示为"(未知状态)"
    // 防止历史脏数据（测试状态、已删除状态）泄漏到用户界面
    if (looksLikeInternalIdentifier(value)) {
      return '(未知状态)'
    }
    return value
  }

  // Issue 类型字段
  if (fieldName === 'issueType' || fieldName === 'issue_type') {
    return issueTypeLabelMap[value] || value
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
  // 操作类型 action 中文映射（统一维护）
  action_rule_executed: { short: '执行了规则', full: '自动执行了规则' },
  create: { short: '创建了', full: '创建了此工单' },
  created: { short: '创建了', full: '创建了此工单' },
  update: { short: '更新了', full: '修改了工单' },
  updated: { short: '更新了', full: '修改了工单' },
  status_change: { short: '变更了状态', full: '修改了状态' },
  status_changed: { short: '变更了状态', full: '修改了状态' },
  status_reset: { short: '重置了状态', full: '重置了状态' },
  assign: { short: '分配了', full: '修改了负责人' },
  assigned: { short: '分配了', full: '修改了负责人' },
  comment: { short: '评论了', full: '添加了评论' },
  commented: { short: '评论了', full: '添加了评论' },
  comment_updated: { short: '编辑了评论', full: '编辑了评论' },
  comment_deleted: { short: '删除了评论', full: '删除了评论' },
  comment_restored: { short: '还原了评论', full: '还原了评论' },
  comment_permanently_deleted: { short: '永久删除了评论', full: '永久删除了评论' },
  attach: { short: '添加了附件', full: '添加了附件' },
  attached: { short: '添加了附件', full: '添加了附件' },
  attachment_added: { short: '添加了附件', full: '添加了附件' },
  attachment_removed: { short: '删除了附件', full: '删除了附件' },
  time_logged: { short: '记录了工时', full: '记录了工时' },
  time_removed: { short: '删除了工时', full: '删除了工时' },
  time_updated: { short: '修改了工时', full: '修改了工时' },
  deleted: { short: '删除了', full: '删除了此工单' },
  restored: { short: '恢复了', full: '恢复了此工单' },
  reopened: { short: '重新打开了', full: '重新打开了此工单' },
  resolved: { short: '解决了', full: '解决了此工单' },
  auto_assigned: { short: '自动分配了', full: '自动分配了负责人' },
  auto_assign_skipped: { short: '跳过了自动分配', full: '跳过了自动分配' },
  moved_to_project: { short: '移动到项目', full: '移动了工单到其他项目' },
  link_added: { short: '添加了关联', full: '添加了工单关联' },
  link_removed: { short: '移除了关联', full: '移除了工单关联' },
}

/**
 * 将 snake_case 的 action 标识转换为基本可读的中文兜底描述
 * 仅用于 actionLabelMap 中未覆盖的未知 action 类型
 */
function fallbackActionLabel(action: string): string {
  // 将 snake_case 转为空格分隔，首字母保持
  return action.replace(/_/g, ' ')
}

/**
 * 本地化 action（完整句子模式，用于 ActivityStream）
 */
export function localizeAction(action?: string | null): string {
  if (!action) return '未知操作'
  return actionLabelMap[action]?.full || `执行了 ${fallbackActionLabel(action)}`
}

/**
 * 本地化 action（简短动词模式，用于 Dashboard 拼接）
 */
export function localizeActionShort(action?: string | null): string {
  if (!action) return '操作了'
  return actionLabelMap[action]?.short || '操作了'
}

/**
 * 关联类型英文值 → 中文映射
 * 覆盖 issue_link_type 表中的所有预置类型及其反向关系
 * 
 * 关联类型分为正向（outward）和反向（inward）两种视角：
 * - blocks / blocked_by: 阻塞关系
 * - duplicates / duplicated_by: 重复关系
 * - relates_to: 相关（双向对称）
 * - parent_of / child_of: 父子关系
 */
export const linkTypeLabelMap: Record<string, string> = {
  // 阻塞关系
  blocks: '阻塞',
  blocked_by: '被阻塞',
  is_blocked_by: '被阻塞', // 兼容可能的 snake_case 变体
  
  // 重复关系
  duplicates: '重复',
  duplicated_by: '被重复',
  is_duplicated_by: '被重复', // 兼容可能的变体
  
  // 相关关系（双向对称）
  relates_to: '相关',
  
  // 父子关系
  parent_of: '父工单',
  child_of: '子工单',
  subtask_of: '子工单', // 兼容别名
}

/**
 * 本地化关联类型名称
 * @param linkType 英文关联类型值（如 "relates_to"、"blocks"、"blocked_by"）
 * @returns 中文关联类型名（如 "相关"、"阻塞"、"被阻塞"），未匹配时返回原值
 */
export function localizeLinkType(linkType?: string | null): string {
  if (!linkType) return '未知'
  return linkTypeLabelMap[linkType] || linkType
}

/**
 * 查询字段的中文→英文反向映射（用于 queryTextToFilters 解析）
 * 从 fieldLabelMap 中提取筛选相关字段的反向映射
 */
export const queryFieldLabelToKey: Record<string, string> = {
  '状态': 'status', '优先级': 'priority', '负责人': 'assignee',
  '类型': 'type', '迭代': 'sprint', 'Sprint': 'sprint', 'sprint': 'sprint',
  '项目': 'project', '报告人': 'reporter',
  '截止日期': 'dueDate', '创建日期': 'createdAt', '更新日期': 'updatedAt', '解决日期': 'resolvedAt',
  '关键词': 'keyword',
}

/**
 * 查询字段的英文→中文映射（用于 filtersToQueryText 展示）
 */
export const queryFieldKeyToLabel: Record<string, string> = {
  status: '状态', priority: '优先级', assignee: '负责人',
  type: '类型', sprint: 'Sprint', project: '项目', reporter: '报告人',
  dueDate: '截止日期', createdAt: '创建日期', updatedAt: '更新日期', resolvedAt: '解决日期',
  keyword: '关键词',
}

/**
 * 优先级中文→英文反向映射
 */
export const priorityReverseLabelMap: Record<string, string> = Object.fromEntries(
  Object.entries(priorityLabelMap)
    .filter(([k]) => k[0] === k[0].toUpperCase()) // 只取 Pascal case 的键
    .map(([k, v]) => [v, k])
)
