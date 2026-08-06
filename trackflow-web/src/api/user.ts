import request from './request'
import type { R, PageResult, UserVO } from './types'

/** 用户摘要 VO — 悬停卡片等轻量级展示 */
export interface UserSummaryVO {
  id: string
  username: string
  displayName: string
  email?: string
  avatarUrl?: string
}

/**
 * 用户模块 API
 */
export const userApi = {
  /** 用户列表 */
  list(params?: {
    keyword?: string; username?: string; displayName?: string; email?: string
    orgId?: string; status?: string; banStatus?: string; roleId?: string
    page?: number; pageSize?: number; sort?: string
  }) {
    return request.get<any, R<PageResult<UserVO>>>('/users', { params })
  },

  /** 创建用户 */
  create(data: { username: string; email: string; displayName: string; password: string }) {
    return request.post<any, R<UserVO>>('/users', data)
  },

  /** 用户详情（含角色ID列表） */
  getById(id: string) {
    return request.get<any, R<{ user: UserVO; roleIds: string[] }>>(`/users/${id}`)
  },

  /** 用户完整档案（含全局角色、项目角色、最近活动） */
  getProfile(id: string) {
    return request.get<any, R<UserProfileVO>>(`/users/${id}/profile`)
  },

  /** 用户公开资料（权限分级，普通成员/管理员看到的内容不同） */
  getPublicProfile(id: string) {
    return request.get<any, R<UserPublicProfileVO>>(`/users/${id}/public-profile`)
  },

  /** 禁用用户 */
  disable(id: string, data: { banStatus: string; banReason?: string }) {
    return request.put<any, R<void>>(`/users/${id}/disable`, data)
  },

  /** 启用用户 */
  enable(id: string) {
    return request.put<any, R<void>>(`/users/${id}/enable`)
  },

  /** 分配全局角色（单个） */
  assignRole(userId: string, roleId: string) {
    return request.post<any, R<void>>(`/users/${userId}/roles`, { roleId })
  },

  /** 移除全局角色（单个） */
  removeRole(userId: string, roleId: string) {
    return request.delete<any, R<void>>(`/users/${userId}/roles/${roleId}`)
  },

  /**
   * 批量替换用户的全局角色集合
   * 语义：传入期望的完整角色 ID 列表，服务端计算差异后执行增删
   * @param userId 用户 ID
   * @param roleIds 期望的角色 ID 列表（空数组表示清空所有全局角色）
   */
  replaceRoles(userId: string, roleIds: string[]) {
    return request.put<any, R<void>>(`/users/${userId}/roles`, {
      roleIds: roleIds.map(id => Number(id))
    })
  },

  /** 导出用户数据（GDPR 数据可携权） */
  exportData(userId: string) {
    return request.get<any, R<UserDataExportVO>>(`/users/${userId}/export`)
  },

  /** 用户摘要信息（悬停卡片用，所有认证用户可访问） */
  getSummary(userId: string) {
    return request.get<any, R<UserSummaryVO>>(`/users/${userId}/summary`)
  }
}

/** 用户档案 VO */
export interface UserProfileVO {
  id: string
  keycloakId?: string
  username: string
  displayName: string
  email?: string
  avatarUrl?: string
  status: string
  banStatus?: string
  banReason?: string
  bannedAt?: string
  bannedByName?: string
  lastLoginAt?: string
  createdAt: string
  timezone?: string
  language?: string
  dateFormat?: string
  firstDayOfWeek?: string
  globalRoles: UserProfileRoleInfo[]
  projectRoles: UserProfileProjectRoleInfo[]
  recentActivities: UserProfileActivityInfo[]
}

export interface UserProfileRoleInfo {
  id: string
  name: string
  code: string
}

export interface UserProfileProjectRoleInfo {
  projectId: string
  projectName: string
  projectKey: string
  roleName: string
  roleCode: string
  roleId?: string
  joinedAt?: string
  /** 角色来源: "direct" = 直接分配, "group" = 通过用户组继承 */
  source?: string
  /** 当 source=group 时，来源组名称 */
  groupName?: string
}

export interface UserProfileActivityInfo {
  id: string
  issueId: string
  issueKey?: string
  issueTitle?: string
  action: string
  fieldName?: string
  oldValue?: string
  newValue?: string
  createdAt: string
}


/** 用户公开资料 VO（权限分级） */
export interface UserPublicProfileVO {
  id: string
  username: string
  displayName: string
  avatarUrl?: string
  /** 与访问者共同参与的项目列表 */
  commonProjects: UserPublicProfileCommonProject[]
  /** 管理员额外字段（adminView=true 时有值） */
  email?: string
  createdAt?: string
  status?: string
  banStatus?: string
  globalRoles?: UserPublicProfileRoleInfo[]
  allProjectRoles?: UserPublicProfileAllProjectRole[]
  /** 是否为管理员视角 */
  adminView: boolean
}

export interface UserPublicProfileCommonProject {
  projectId: string
  projectName: string
  projectKey: string
  roleName?: string
  roleCode?: string
}

export interface UserPublicProfileRoleInfo {
  id: string
  name: string
  code: string
}

export interface UserPublicProfileAllProjectRole {
  projectId: string
  projectName: string
  projectKey: string
  roleName?: string
  roleCode?: string
}

/** 用户数据导出 VO */
export interface UserDataExportVO {
  exportDate: string
  exportedBy: string
  userInfo: {
    id: string
    username: string
    displayName: string
    email: string
    phone?: string
    avatarUrl?: string
    status: string
    banStatus?: string
    banReason?: string
    bannedAt?: string
    lastLoginAt?: string
    createdAt: string
    updatedAt: string
  }
  globalRoles: string[]
  projectMemberships: Array<{
    projectId: string
    projectName: string
    projectKey: string
    roleName: string
    joinedAt: string
  }>
  createdIssues: Array<{
    id: string
    issueKey: string
    title: string
    issueType: string
    priority: string
    status?: string
    createdAt: string
    updatedAt: string
  }>
  assignedIssues: Array<{
    id: string
    issueKey: string
    title: string
    issueType: string
    priority: string
    status?: string
    createdAt: string
    updatedAt: string
  }>
  comments: Array<{
    id: string
    issueId: string
    content: string
    source?: string
    createdAt: string
  }>
  activities: Array<{
    id: string
    issueId: string
    action: string
    fieldName?: string
    oldValue?: string
    newValue?: string
    createdAt: string
  }>
  attachments: Array<{
    id: string
    issueId: string
    fileName: string
    fileSize: number
    contentType: string
    createdAt: string
  }>
}
