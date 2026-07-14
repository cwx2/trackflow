import request from './request'
import type { R, RoleVO, WorkflowTransitionVO, UpdateWorkflowDTO } from './types'

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
  },

  /** 获取项目级角色列表（用于工作流编辑器筛选） */
  listProjectRoles() {
    return request.get<any, R<RoleVO[]>>('/workflows/project-roles')
  },

  /** 获取系统中已使用的工单类型列表 */
  listIssueTypes() {
    return request.get<any, R<string[]>>('/workflows/issue-types')
  },

  /** 获取当前用户在指定项目中可发起状态转换的源状态 ID 列表 */
  getTransitionableStatuses(projectId: string) {
    return request.get<any, R<string[]>>(`/projects/${projectId}/workflows/transitionable-statuses`)
  }
}
