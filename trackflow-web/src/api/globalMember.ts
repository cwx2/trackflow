import request from './request'
import type { R } from './types'

/**
 * 全局项目角色分配 VO
 */
export interface GlobalMemberVO {
  id: string
  userId: string
  username?: string
  displayName?: string
  email?: string
  roleId: string
  roleName?: string
  roleCode?: string
  createdAt?: string
  createdByName?: string
}

/**
 * 全局项目角色分配 API
 */
export const globalMemberApi = {
  /** 分配全局项目角色 */
  assign(data: { userId: number | string; roleId: number | string }) {
    return request.post<any, R<GlobalMemberVO>>('/global-members', data)
  },

  /** 撤销全局项目角色 */
  revoke(userId: string | number, roleId: string | number) {
    return request.delete<any, R<void>>('/global-members', { params: { userId, roleId } })
  },

  /** 列出所有全局分配记录 */
  listAll() {
    return request.get<any, R<GlobalMemberVO[]>>('/global-members')
  },

  /** 列出指定用户的全局角色分配 */
  listByUser(userId: string) {
    return request.get<any, R<GlobalMemberVO[]>>(`/global-members/user/${userId}`)
  }
}
