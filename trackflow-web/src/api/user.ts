import request from './request'
import type { R, PageResult, UserVO } from './types'

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

  /** 禁用用户 */
  disable(id: string, data: { banStatus: string; banReason?: string }) {
    return request.put<any, R<void>>(`/users/${id}/disable`, data)
  },

  /** 启用用户 */
  enable(id: string) {
    return request.put<any, R<void>>(`/users/${id}/enable`)
  },

  /** 分配全局角色 */
  assignRole(userId: string, roleId: string) {
    return request.post<any, R<void>>(`/users/${userId}/roles`, { roleId })
  },

  /** 移除全局角色 */
  removeRole(userId: string, roleId: string) {
    return request.delete<any, R<void>>(`/users/${userId}/roles/${roleId}`)
  }
}

/** 用户档案 VO */
export interface UserProfileVO {
  id: string
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
