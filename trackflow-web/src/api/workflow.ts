import request from './request'
import type { R, PageResult, RoleVO, WorkflowTransitionVO, WorkflowMatrixVO, WorkflowActivityVO, UpdateWorkflowDTO, WorkflowImpactAnalysisVO } from './types'

/** 守卫条件项类型 */
export interface TransitionConditionItem {
  field?: string
  operator?: string
  value?: string
  /** 高级条件类型（links_resolved, children_resolved） */
  conditionType?: string
  /** links_resolved 条件的关联类型 */
  linkType?: string
}

/** 更新守卫条件 DTO */
export interface UpdateTransitionConditionsDTO {
  conditions: TransitionConditionItem[]
}

/**
 * 工作流模块 API
 */
export const workflowApi = {
  /** 获取工作流转换矩阵（含版本号，用于乐观锁） */
  getTransitionMatrix(projectId: string, params?: {
    issueType?: string
    roleId?: string
    author?: boolean
    assignee?: boolean
  }) {
    return request.get<any, R<WorkflowMatrixVO>>(`/projects/${projectId}/workflows`, { params })
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
  },

  /** 获取工作流变更历史（审计日志） */
  listActivities(projectId: string, params?: {
    userId?: string
    startDate?: string
    endDate?: string
    page?: number
    pageSize?: number
  }) {
    return request.get<any, R<PageResult<WorkflowActivityVO>>>(`/projects/${projectId}/workflow-activities`, { params })
  },

  /** 工作流影响分析：统计被删除转换的源状态下有多少工单 */
  analyzeImpact(data: {
    statusIds: number[]
    projectId?: number
    issueType?: string
  }) {
    return request.post<any, R<WorkflowImpactAnalysisVO>>('/workflows/impact-analysis', data)
  },

  /**
   * 更新指定工作流转换规则的守卫条件。
   * 空条件列表 = 清除守卫条件（无前置限制）。
   *
   * @param transitionId 转换规则 ID（来自 WorkflowTransitionVO.id）
   * @param data 守卫条件配置
   */
  updateTransitionConditions(transitionId: string, data: UpdateTransitionConditionsDTO) {
    return request.patch<any, R<void>>(`/workflows/transitions/${transitionId}/conditions`, data)
  }
}
