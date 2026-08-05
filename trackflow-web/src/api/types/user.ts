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
