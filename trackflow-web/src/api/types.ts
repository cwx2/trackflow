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
  issueSequence: number
  createdAt: string
  updatedAt: string
}

export interface ProjectMemberVO {
  id: string
  projectId: string
  userId: string
  roleId: string
  username: string
  displayName: string
  email?: string
  joinedAt: string
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
}

export interface IssueDetailVO extends IssueVO {
  description?: string
  projectName?: string
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
