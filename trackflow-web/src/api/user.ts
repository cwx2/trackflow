import request from './request'
import type { R, PageResult, UserVO } from './types'

/**
 * 用户模块 API
 */
export const userApi = {
  /** 用户列表 */
  list(params?: {
    username?: string; displayName?: string; email?: string
    orgId?: string; status?: string; page?: number; pageSize?: number
  }) {
    return request.get<any, R<PageResult<UserVO>>>('/users', { params })
  },

  /** 用户详情（含角色ID列表） */
  getById(id: string) {
    return request.get<any, R<{ user: UserVO; roleIds: string[] }>>(`/users/${id}`)
  },

  /** 禁用用户 */
  disable(id: string) {
    return request.put<any, R<void>>(`/users/${id}/disable`)
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
