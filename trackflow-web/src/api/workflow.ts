import request from './request'
import type { R } from './types'

export interface WorkflowTransitionVO {
  id: string
  projectId: string
  issueType: string
  roleId: string
  oldStatusId: string
  newStatusId: string
}

export interface UpdateWorkflowDTO {
  issueType: string
  roleId: number
  transitions: { from: number; to: number; allowed: boolean }[]
}

/**
 * 工作流模块 API
 */
export const workflowApi = {
  /** 获取工作流转换矩阵 */
  getTransitionMatrix(projectId: string, params?: { issueType?: string; roleId?: string }) {
    return request.get<any, R<WorkflowTransitionVO[]>>(`/projects/${projectId}/workflows`, { params })
  },

  /** 更新工作流转换矩阵 */
  updateTransitionMatrix(projectId: string, data: UpdateWorkflowDTO) {
    return request.put<any, R<void>>(`/projects/${projectId}/workflows`, data)
  }
}
