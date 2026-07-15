/**
 * 后端统一响应结构 R<T>
 */
export interface R<T = any> {
  code: number
  message: string
  data: T
  timestamp: number
  traceId?: string
}

/**
 * /api/v1/auth/me 返回的当前用户信息 VO
 * 对应后端 UserInfoVO，字段来自 Keycloak JWT
 */
export interface UserInfoVO {
  /** Keycloak subject ID */
  keycloakId: string
  /** 登录用户名 */
  username: string
  /** 显示名称 */
  displayName: string
  /** 邮箱 */
  email: string
}

/**
 * 从 JWT Token 解析出的当前认证用户信息
 * 存储在 auth store 的 user 字段中，持久化到 localStorage
 */
export interface AuthUser {
  /** Keycloak subject ID */
  id: string
  /** 登录用户名 */
  username: string
  /** 显示名称（CJK 姓+名，西方名+姓） */
  displayName: string
  /** 邮箱 */
  email: string
  /** Keycloak realm_access.roles（如 tf_admin, tf_user） */
  roles: string[]
}

/**
 * 分页结果
 */
export interface PageResult<T = any> {
  list: T[]
  pagination: {
    page: number
    pageSize: number
    total: number
    totalPages: number
  }
}

/**
 * 分页查询参数
 */
export interface PageQuery {
  page?: number
  pageSize?: number
  sort?: string
}

// ========== 项目 ==========
export interface ProjectVO {
  id: string
  name: string
  key: string
  description?: string
  orgId?: string
  leadId?: string
  status: string
  visibility: 'private' | 'internal' | 'public'
  issueSequence: number
  createdAt: string
  updatedAt: string
}

export interface ProjectDetailVO extends ProjectVO {
  /** 当前登录用户在该项目中的角色名称 */
  myRoleName?: string
  /** 当前登录用户在该项目中的角色代码 */
  myRoleCode?: string
  /** 项目成员总数 */
  memberCount?: number
  /** 项目负责人显示名称 */
  leadName?: string
}

export interface ProjectMemberVO {
  id: string
  projectId: string
  userId: string
  /** 主角色ID（向后兼容） */
  roleId: string
  /** 所有角色ID列表 */
  roleIds: string[]
  /** 所有角色名称列表 */
  roleNames: string[]
  username: string
  displayName: string
  email?: string
  joinedAt: string
}

// ========== 项目活动日志 ==========
export interface ProjectActivityVO {
  id: string
  projectId: string
  userId: string
  action: string
  targetUserId?: string
  detail?: string
  createdAt: string
  userName: string
  targetUserName?: string
}

// ========== Issue ==========
export interface IssueVO {
  id: string
  projectId: string
  issueKey: string
  title: string
  issueType: string
  statusId: string
  priority: string
  assigneeId?: string
  assigneeName?: string
  reporterId: string
  sprintId?: string
  dueDate?: string
  createdAt: string
  updatedAt: string
  version: number
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
  estimatedHours?: number
  spentHours?: number
  customFields?: string
  tags?: IssueTagVO[]
  resolvedAt?: string
  children?: ChildIssueVO[]
  childProgress?: ChildProgressVO
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
  code: string
  color: string
  category: string
  isDefault: boolean
  isClosed: boolean
  sortOrder: number
  /** 是否被阻塞（当前 issue 有未解决的 blocker 且此状态为关闭状态时为 true） */
  blocked?: boolean
  /** 阻塞方的 issueKey 列表 */
  blockedBy?: string[]
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
  createdAt: string
  updatedAt: string
}

export interface IssueActivityVO {
  id: string
  issueId: string
  userId: string
  userName?: string
  action: string
  fieldName?: string
  oldValue?: string
  newValue?: string
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
}

// ========== Sprint ==========
export interface SprintVO {
  id: string
  projectId: string
  name: string
  goal?: string
  status: string
  startDate?: string
  endDate?: string
  createdAt: string
  /** 状态提示信息：当 status 与日期矛盾时后端返回警告文案 */
  statusHint?: string
  /** 是否已超期（active 且 end_date < today） */
  overdue?: boolean
  /** 工单总数 */
  totalIssues: number
  /** 已完成工单数 */
  doneIssues: number
  /** 进行中工单数 */
  inProgressIssues: number
  /** 待办工单数 */
  todoIssues: number
  /** 逾期工单数 */
  overdueIssues: number
}

/** Sprint 燃尽图数据 */
export interface SprintBurndownVO {
  sprintId: string
  sprintName: string
  /** X 轴日期列表 (yyyy-MM-dd) */
  dates: string[]
  /** 理想线：每天的理想剩余工单数 */
  idealLine: number[]
  /** 实际线：每天的实际剩余工单数（仅到今天） */
  actualLine: number[]
  /** 今天在 dates 中的索引（-1 = 不在范围内） */
  todayIndex: number
  /** Sprint 总工单数（起始值） */
  totalIssues: number
  /** 日均完成速率 */
  velocity: number
  /** 预测完成日期 (yyyy-MM-dd)，null 表示速率为 0 */
  forecastDate: string | null
}

// ========== 用户/角色/组织 ==========
export interface UserVO {
  id: string
  username: string
  displayName: string
  email?: string
  orgId?: string
  status: string
  lastLoginAt?: string
  createdAt: string
}

export interface RoleVO {
  id: string
  name: string
  code: string
  description?: string
  roleType: string
  builtin: boolean
  sortOrder: number
}

export interface SavedQueryVO {
  id: string
  name: string
  projectId?: string
  userId: string
  shared: boolean
  pinned: boolean
  folder?: string
  filters?: string
  icon?: string
  sortOrder: number
  createdAt: string
}

// ========== 自定义字段 ==========
export interface CustomFieldDefinitionVO {
  id: string
  name: string
  fieldFormat: 'string' | 'int' | 'float' | 'date' | 'bool' | 'list' | 'user'
  isRequired: boolean
  isForAll: boolean
  defaultValue?: string
  minLength: number
  maxLength: number
  regexp?: string
  position: number
  options?: CustomFieldOptionVO[]
  projectIds?: string[]
  issueTypes?: string[]
  createdAt: string
  updatedAt: string
}

export interface CustomFieldOptionVO {
  id: string
  customFieldId: string
  value: string
  position: number
  isDefault: boolean
}

export interface CustomFieldValueVO {
  customFieldId: string
  fieldName: string
  fieldFormat: string
  value?: string
  displayValue?: string
}

export interface AvailableColumnVO {
  key: string
  label: string
  group: 'standard' | 'custom'
  fieldFormat?: string
  sortable: boolean
  removable: boolean
}

// ========== 工作流 ==========
export interface WorkflowTransitionVO {
  id: string
  projectId: string
  issueType: string
  roleId: string
  oldStatusId: string
  newStatusId: string
}

export interface UpdateWorkflowDTO {
  issueType: string
  roleId: number
  transitions: { from: number; to: number; allowed: boolean }[]
}

// ========== 看板 ==========
export interface BoardColumnVO {
  statusId: string
  statusName: string
  statusCode: string
  statusColor: string
  statusCategory: string
  visible: boolean
  sortOrder: number
  collapsed: boolean
}

export interface BoardColumnItem {
  statusId: number
  visible: boolean
  sortOrder: number
  collapsed?: boolean
}

// ========== Sprint 完成预览 ==========
export interface CompletionPreviewVO {
  openIssues: CompletionPreviewIssue[]
  targetSprints: CompletionPreviewTarget[]
}

export interface CompletionPreviewIssue {
  id: string
  issueKey: string
  title: string
  priority: string
  statusName: string
  statusColor: string
  assigneeName?: string
}

export interface CompletionPreviewTarget {
  id: string
  name: string
  status: string
}
