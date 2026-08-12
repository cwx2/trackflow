/**
 * 用户、角色、组织、保存查询模块类型定义
 */

export interface UserVO {
  id: string
  username: string
  displayName: string
  email?: string
  orgId?: string
  status: string
  banStatus?: string
  banReason?: string
  bannedAt?: string
  bannedByName?: string
  lastLoginAt?: string
  createdAt: string
  globalRoles?: GlobalRoleInfo[]
}

export interface GlobalRoleInfo {
  id: string
  name: string
  code: string
}

export interface RoleVO {
  id: string
  name: string
  code: string
  description?: string
  roleType: string
  builtin: boolean
  enabled?: boolean
  sortOrder: number
  userCount?: number
}

export interface RoleUsersVO {
  roleId: string
  roleName: string
  roleType: string
  globalUsers: UserVO[]
  projectGroups: ProjectRoleGroup[]
  totalUserCount: number
}

export interface ProjectRoleGroup {
  projectId: string
  projectName: string
  projectKey: string
  users: UserVO[]
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
  sortCriteria?: string
  icon?: string
  sortOrder: number
  createdAt: string
}

/**
 * 查询面板单项 VO — 面板中每个查询的展示数据
 * 对应后端 QueryPanelItemVO
 */
export interface QueryPanelItemVO {
  id: string
  name: string
  folder?: string
  icon?: string
  pinned?: boolean
  shared?: boolean
  userId: string
  count: number
  filters?: string
  sortCriteria?: string
  favorited?: boolean
}

/**
 * 查询面板数据 VO — 面板整体数据结构
 * 对应后端 QueryPanelVO
 */
export interface QueryPanelVO {
  pinned: QueryPanelItemVO[]
  queries: QueryPanelItemVO[]
}

/**
 * 保存查询的筛选条件对象
 */
export interface SavedQueryFilter {
  field: string
  operator: string
  value: string | string[]
  displayName?: string
}

/**
 * 更新保存查询的请求数据
 */
export interface UpdateSavedQueryDTO {
  name?: string
  filters?: SavedQueryFilter[]
  pinned?: boolean
  shared?: boolean
  folder?: string
  icon?: string
  sortCriteria?: string
}

/**
 * 创建保存查询的请求数据
 */
export interface CreateSavedQueryDTO {
  name: string
  filters: SavedQueryFilter[]
  pinned?: boolean
  folder?: string
  shared?: boolean
  icon?: string
}
