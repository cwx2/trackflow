import request from './request'
import type { R, UserInfoVO } from './types'

/**
 * Auth 模块 API
 */
export const authApi = {
  /** 获取当前用户信息 */
  me() {
    return request.get<any, R<UserInfoVO>>('/auth/me')
  },

  /** 获取当前用户在指定项目中的权限列表 */
  getMyPermissions(projectId: string) {
    return request.get<any, R<string[]>>('/auth/my-permissions', { params: { projectId } })
  },

  /** 获取当前用户的全局权限列表 */
  getMyGlobalPermissions() {
    return request.get<any, R<string[]>>('/auth/my-global-permissions')
  },

  /**
   * 通知后端记录 logout 审计事件（best-effort，fire-and-forget）。
   * 前端在跳转 Keycloak logout 之前调用。
   */
  notifyLogout() {
    return request.post<any, R<void>>('/auth/logout')
  }
}
