import request from './request'
import type { R } from './types'

/**
 * 工作流定义 VO
 */
export interface WorkflowDefinitionVO {
  id: string
  name: string
  description: string | null
  isDefault: boolean
  transitionCount: number
  projectCount: number
  projects: BoundProject[]
  createdBy: string | null
  createdByName: string | null
  createdAt: string
  updatedAt: string
}

export interface BoundProject {
  id: string
  name: string
  key: string
}

export interface CreateWorkflowDefinitionDTO {
  name: string
  description?: string
  isDefault?: boolean
}

export interface UpdateWorkflowDefinitionDTO {
  name?: string
  description?: string
  isDefault?: boolean
}

/**
 * 工作流定义管理 API
 */
export const workflowDefinitionApi = {
  /** 获取所有工作流定义列表 */
  list() {
    return request.get<any, R<WorkflowDefinitionVO[]>>('/workflow-definitions')
  },

  /** 获取单个工作流定义详情 */
  get(id: string) {
    return request.get<any, R<WorkflowDefinitionVO>>(`/workflow-definitions/${id}`)
  },

  /** 创建工作流定义 */
  create(data: CreateWorkflowDefinitionDTO) {
    return request.post<any, R<WorkflowDefinitionVO>>('/workflow-definitions', data)
  },

  /** 更新工作流定义 */
  update(id: string, data: UpdateWorkflowDefinitionDTO) {
    return request.put<any, R<void>>(`/workflow-definitions/${id}`, data)
  },

  /** 删除工作流定义 */
  delete(id: string) {
    return request.delete<any, R<void>>(`/workflow-definitions/${id}`)
  },

  /** 克隆工作流定义 */
  clone(id: string, name: string) {
    return request.post<any, R<WorkflowDefinitionVO>>(`/workflow-definitions/${id}/clone`, { name })
  },

  /** 将工作流附加到项目 */
  attachToProject(projectId: string, workflowDefinitionId: string) {
    return request.post<any, R<void>>(`/workflow-definitions/projects/${projectId}/attach`, {
      workflowDefinitionId: Number(workflowDefinitionId)
    })
  },

  /** 从项目分离工作流 */
  detachFromProject(projectId: string, workflowDefinitionId: string) {
    return request.delete<any, R<void>>(`/workflow-definitions/projects/${projectId}/detach`, {
      data: { workflowDefinitionId: Number(workflowDefinitionId) }
    })
  },

  /** 获取项目绑定的工作流定义列表 */
  getProjectWorkflows(projectId: string) {
    return request.get<any, R<WorkflowDefinitionVO[]>>(`/workflow-definitions/projects/${projectId}`)
  }
}
