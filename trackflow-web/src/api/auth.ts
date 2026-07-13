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
  }
}
