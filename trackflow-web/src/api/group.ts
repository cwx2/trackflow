import request from './request'
import type { R, PageResult } from './types'

// ========== 用户组相关类型 ==========

export interface UserGroupVO {
  id: string
  name: string
  description?: string
  memberCount: number
  roleCount: number
  createdAt: string
  updatedAt: string
}

export interface UserGroupDetailVO {
  id: string
  name: string
  description?: string
  createdAt: string
  updatedAt: string
  members: GroupMemberInfo[]
  roles: GroupRoleAssignment[]
}

export interface GroupMemberInfo {
  userId: string
  username: string
  displayName: string
  email?: string
  avatarUrl?: string
  joinedAt: string
}

export interface GroupRoleAssignment {
  id: string
  roleId: string
  roleName: string
  roleCode: string
  roleType: string
  /** null 表示全局角色 */
  projectId?: string
  projectName?: string
  projectKey?: string
  /** 作用域：global / all_projects / project */
  scope: string
  createdAt: string
}

// ========== API ==========

export const groupApi = {
  /** 用户组列表 */
  list(params?: { keyword?: string; page?: number; pageSize?: number }) {
    return request.get<any, R<PageResult<UserGroupVO>>>('/groups', { params })
  },

  /** 用户组详情 */
  getDetail(id: string) {
    return request.get<any, R<UserGroupDetailVO>>(`/groups/${id}`)
  },

  /** 创建用户组 */
  create(data: { name: string; description?: string }) {
    return request.post<any, R<UserGroupVO>>('/groups', data)
  },

  /** 更新用户组 */
  update(id: string, data: { name: string; description?: string }) {
    return request.put<any, R<void>>(`/groups/${id}`, data)
  },

  /** 删除用户组 */
  delete(id: string) {
    return request.delete<any, R<void>>(`/groups/${id}`)
  },

  /** 添加成员到组 */
  addMembers(groupId: string, userIds: string[]) {
    return request.post<any, R<void>>(`/groups/${groupId}/members`, { userIds })
  },

  /** 从组中移除成员 */
  removeMembers(groupId: string, userIds: string[]) {
    return request.delete<any, R<void>>(`/groups/${groupId}/members`, {
      data: { userIds }
    })
  },

  /** 为组分配角色 */
  assignRole(groupId: string, data: { roleId: string; projectIds?: string[]; globalScope?: boolean; projectId?: string }) {
    return request.post<any, R<void>>(`/groups/${groupId}/roles`, {
      roleId: data.roleId,
      projectIds: data.projectIds?.length ? data.projectIds : undefined,
      globalScope: data.globalScope || undefined,
      projectId: data.projectId || undefined
    })
  },

  /** 移除组的角色分配 */
  removeRole(groupId: string, roleAssignmentId: string) {
    return request.delete<any, R<void>>(`/groups/${groupId}/roles/${roleAssignmentId}`)
  }
}
