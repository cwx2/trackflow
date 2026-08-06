import request from './request'
import type { R, UserInfoVO } from './types'
import type { UserProfileVO } from './user'

/** 更新个人资料请求体 */
export interface UpdateMyProfileDTO {
  displayName: string
  timezone?: string
  language?: string
  dateFormat?: string
  firstDayOfWeek?: string
}

/**
 * Auth 模块 API
 */
export const authApi = {
  /** 获取当前用户信息 */
  me() {
    return request.get<any, R<UserInfoVO>>('/auth/me')
  },

  /** 获取当前登录用户的个人资料（含注册日期等） */
  getMyProfile() {
    return request.get<any, R<UserProfileVO>>('/auth/me/profile')
  },

  /** 更新当前登录用户的个人资料 */
  updateMyProfile(data: UpdateMyProfileDTO) {
    return request.patch<any, R<UserProfileVO>>('/auth/me/profile', data)
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
