import request from './request'
import type { R } from './types'

/**
 * Auth 模块 API
 */
export const authApi = {
  /** 获取当前用户信息 */
  me() {
    return request.get<any, R<Record<string, any>>>('/auth/me')
  },

  /** 获取当前用户在指定项目中的权限列表 */
  getMyPermissions(projectId: string) {
    return request.get<any, R<string[]>>('/auth/my-permissions', { params: { projectId } })
  }
}
