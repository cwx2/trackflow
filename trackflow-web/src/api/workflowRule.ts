import request from './request'
import type { R } from './types'

/**
 * 工作流规则 VO（前端类型定义）
 */
export interface WorkflowRuleVO {
  id: string
  projectId: string | null
  name: string
  description: string | null
  ruleType: string
  triggerEvent: string
  triggerField: string | null
  conditionJson: string
  actionJson: string
  enabled: boolean
  sortOrder: number
  createdBy: string | null
  createdAt: string
  updatedAt: string
}

/**
 * 创建/更新工作流规则 DTO
 */
export interface WorkflowRuleDTO {
  name: string
  description?: string
  triggerEvent: string
  triggerField?: string | null
  conditionJson: string
  actionJson: string
  enabled?: boolean
  sortOrder?: number
}

/**
 * 工作流规则 API
 */
export const workflowRuleApi = {
  /** 获取项目规则列表（含全局规则） */
  list(projectId: string) {
    return request.get<any, R<WorkflowRuleVO[]>>(`/projects/${projectId}/workflow-rules`)
  },

  /** 获取单个规则 */
  get(id: string) {
    return request.get<any, R<WorkflowRuleVO>>(`/workflow-rules/${id}`)
  },

  /** 创建规则 */
  create(projectId: string, data: WorkflowRuleDTO) {
    return request.post<any, R<WorkflowRuleVO>>(`/projects/${projectId}/workflow-rules`, data)
  },

  /** 更新规则 */
  update(id: string, data: WorkflowRuleDTO) {
    return request.put<any, R<WorkflowRuleVO>>(`/workflow-rules/${id}`, data)
  },

  /** 删除规则 */
  delete(id: string) {
    return request.delete<any, R<void>>(`/workflow-rules/${id}`)
  },

  /** 切换启用/禁用 */
  toggle(id: string) {
    return request.patch<any, R<WorkflowRuleVO>>(`/workflow-rules/${id}/toggle`)
  }
}
