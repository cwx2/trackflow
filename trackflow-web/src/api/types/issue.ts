/**
 * Issue 模块类型定义
 */
import type { CustomFieldValueVO } from './customField'

/** 相似工单 VO — 创建工单时用于重复检测的轻量结构 */
export interface SimilarIssueVO {
  id: string
  issueKey: string
  title: string
  statusName?: string
  statusColor?: string
  assigneeName?: string
}

export interface IssueVO {
  id: string
  projectId: string
  projectKey?: string
  projectName?: string
  issueKey: string
  title: string
  issueType: string
  statusId: string
  statusName?: string
  statusColor?: string
  priority: string
  /** 优先级颜色（HEX 格式，来自自定义字段配置，null 表示未配置） */
  priorityColor?: string
  /** 工单类型颜色（HEX 格式，来自自定义字段配置，null 表示未配置） */
  issueTypeColor?: string
  assigneeId?: string
  assigneeName?: string
  assigneeAvatarUrl?: string
  reporterId: string
  reporterName?: string
  sprintId?: string
  sprintName?: string
  /** Sprint 状态（planned/active/completed/archived），用于前端视觉区分已完成迭代 */
  sprintStatus?: string
  /** 多 Sprint 模式：工单关联的所有 Sprint ID */
  sprintIds?: string[]
  /** 多 Sprint 模式：工单关联的所有 Sprint 名称（与 sprintIds 对应） */
  sprintNames?: string[]
  dueDate?: string
  createdAt: string
  updatedAt: string
  resolvedAt?: string
  version: number
  /** 直接子工单总数 */
  childCount?: number
  /** 已关闭的直接子工单数 */
  childClosedCount?: number
  /** 预估工时 */
  estimatedHours?: number
  /** 已花时间 */
  spentHours?: number
  /** 派生字段：自身 + 所有后代 spent_hours 总和 */
  derivedSpentHours?: number
  /** 派生字段：自身 + 所有后代 estimated_hours 总和 */
  derivedEstimatedHours?: number
  /** 自定义字段结构化详情，每个字段独立表达 value/values、displayValue/displayValues、color/colors */
  customFieldDetails?: CustomFieldValueVO[]
  /** 投票数（来自 issue.vote_count 冗余字段） */
  voteCount?: number
  /** 工单可见性：public（项目所有成员可见）或 restricted（仅限指定用户） */
  visibility?: string
  /** 工单关联的标签列表（列表查询时批量填充） */
  tags?: IssueTagVO[]
  /** 搜索匹配上下文片段（仅关键词搜索时由后端填充） */
  matchContext?: string
}

export interface IssueDetailVO extends IssueVO {
  description?: string
  projectName?: string
  projectStatus?: string
  reporterName?: string
  sprintName?: string
  parentId?: string
  parentKey?: string
  status?: IssueStatusVO
  tags?: IssueTagVO[]
  resolvedAt?: string
  /** 创建者ID */
  createdById?: string
  /** 创建者名称 */
  createdByName?: string
  /** 更新者ID */
  updatedById?: string
  /** 更新者名称 */
  updatedByName?: string
  children?: ChildIssueVO[]
  childProgress?: ChildProgressVO
  /** 类型变更导致状态自动重置时为 true（仅 update 响应中出现） */
  statusAutoReset?: boolean
  /** 受限工单的可见用户 ID 列表（visibility=restricted 时非空） */
  visibilityUserIds?: string[]
  /** 受限工单的可见用户显示名列表 */
  visibilityUserNames?: string[]
}

/** 子任务简要信息 */
export interface ChildIssueVO {
  id: string
  issueKey: string
  title: string
  issueType: string
  priority: string
  statusName: string
  statusColor: string
  statusCategory: string
  assigneeName?: string
}

/** 子任务进度汇总 */
export interface ChildProgressVO {
  total: number
  closed: number
  percent: number
  aggregatedEstimate?: number
  aggregatedSpent?: number
}

export interface IssueStatusVO {
  id: string
  name: string
  displayName?: string
  code: string
  color: string
  category: string
  isDefault: boolean
  isClosed: boolean
  sortOrder: number
  canvasX?: number | null
  canvasY?: number | null
  /** 是否被阻塞（当前 issue 有未解决的 blocker 且此状态为关闭状态时为 true） */
  blocked?: boolean
  /** 阻塞方的 issueKey 列表 */
  blockedBy?: string[]
  /** 此转换是否要求必须填写评论/理由 */
  requireComment?: boolean
  /** 转换显示名（如"开始处理"），为空时使用目标状态名 */
  transitionName?: string
}

/** 批量操作中每个状态的可达性信息 */
export interface BatchAvailableStatusVO {
  id: string
  name: string
  color: string
  category: string
  isClosed: boolean
  sortOrder: number
  /** 转换显示名称（优先于 name 展示） */
  transitionName?: string
  /** 可以转换到此状态的工单数量 */
  reachableCount: number
  /** 选中的工单总数 */
  totalCount: number
}

export interface IssueTrashVO {
  id: string
  projectId: string
  issueKey: string
  title: string
  issueType: string
  priority: string
  assigneeName?: string
  deletedAt: string
  deletedByName?: string
}

export interface IssueCommentVO {
  id: string
  issueId: string
  userId: string
  userName?: string
  userAvatar?: string
  content: string
  source: string
  isEdited?: boolean
  deletedAt?: string | null
  visibleToGroupIds?: string[]
  visibleToGroupNames?: string[]
  createdAt: string
  updatedAt: string
}

export interface IssueActivityVO {
  id: string
  issueId: string
  userId?: string
  userName?: string
  userAvatar?: string
  action: string
  fieldName?: string
  oldValue?: string
  newValue?: string
  /**
   * 操作元数据（JSON 字符串），存储变更来源等信息。
   * 示例：{"source":"action_rule"} | {"source":"automation","ruleName":"..."} | {"reason":"member_removed"}
   */
  detail?: string
  /**
   * 活动来源标识：manual（用户手动）/ automation（自动化规则）/ workflow_action（转换动作）/ system（系统）
   * null 等同于 manual。
   */
  source?: string
  createdAt: string
}

export interface IssueAttachmentVO {
  id: string
  issueId: string
  fileName: string
  filePath: string
  fileSize: number
  contentType?: string
  uploadedBy: string
  createdAt: string
  /** 是否为私有附件 */
  isPrivate?: boolean
  /** 可见性限制的组 ID 列表 */
  visibleToGroupIds?: string[]
  /** 可见性限制的组名称列表 */
  visibleToGroupNames?: string[]
}

export interface IssueTagVO {
  id: string
  name: string
  color: string
}

export interface IssueLinkVO {
  id: string
  linkType: string
  issueId: string
  issueKey: string
  issueTitle: string
  issueStatus?: IssueStatusVO
  /** 优先级值（如 "Normal", "Critical"） */
  priority?: string
  /** 优先级对应颜色（HEX 格式） */
  priorityColor?: string
  /** 优先级排序序号（方块中显示的数字，从 1 开始） */
  priorityOrder?: number
}

export interface IssueLinkTypeVO {
  id: string
  name: string
  outwardName: string
  inwardName: string
  direction: string
  isSystem: boolean
}

/**
 * Issue 部分更新请求数据（对应后端 UpdateIssueDTO）
 * 所有字段均可选，仅传递需要更新的字段
 */
export interface UpdateIssueData {
  title?: string
  description?: string
  issueType?: string
  priority?: string
  assigneeId?: string | null
  sprintId?: string | null
  parentId?: string | null
  dueDate?: string | null
  estimatedHours?: number | null
  customFields?: Record<string, string>
  clearDueDate?: boolean
  clearEstimatedHours?: boolean
  version?: number
  forceWip?: boolean
  addToSprintId?: string
  removeFromSprintId?: string
  visibility?: string
  visibilityUserIds?: string[]
}

/**
 * 更新评论请求体
 */
export interface UpdateCommentData {
  content: string
  visibleToGroupIds?: string[] | null
}
