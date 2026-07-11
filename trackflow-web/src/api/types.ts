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
