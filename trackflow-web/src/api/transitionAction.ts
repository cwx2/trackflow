import request from './request'
import type { R } from './types'

export interface TransitionActionVO {
  id: string
  projectId: string
  issueType: string
  oldStatusId: string
  newStatusId: string
  oldStatusName?: string
  newStatusName?: string
  actionType: string
  actionConfig: {
    strategy: string
    user_id?: number
    role_id?: number
    mode?: string
    fallback_strategy?: string
  }
  sortOrder: number
  enabled: boolean
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface CreateTransitionActionDTO {
  projectId: number
  issueType: string
  oldStatusId: number
  newStatusId: number
  actionType: string
  actionConfig: Record<string, any>
  sortOrder?: number
  enabled?: boolean
}

export interface UpdateTransitionActionDTO {
  actionType?: string
  actionConfig?: Record<string, any>
  sortOrder?: number
  enabled?: boolean
}

export const transitionActionApi = {
  /** 查询转换动作列表 */
  list(projectId: number | string, params?: { issueType?: string; oldStatusId?: number; newStatusId?: number }) {
    return request.get<any, R<TransitionActionVO[]>>('/transition-actions', { params: { projectId, ...params } })
  },

  /** 创建转换动作 */
  create(data: CreateTransitionActionDTO) {
    return request.post<any, R<TransitionActionVO>>('/transition-actions', data)
  },

  /** 更新转换动作 */
  update(id: string, data: UpdateTransitionActionDTO) {
    return request.put<any, R<TransitionActionVO>>(`/transition-actions/${id}`, data)
  },

  /** 删除转换动作 */
  delete(id: string) {
    return request.delete<any, R<void>>(`/transition-actions/${id}`)
  },

  /** 切换启用/禁用 */
  toggleEnabled(id: string) {
    return request.patch<any, R<void>>(`/transition-actions/${id}/toggle`)
  }
}
