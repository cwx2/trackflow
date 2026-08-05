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
  cronExpression: string | null
  actionCommand: string | null
  lastExecutedAt: string | null
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
  ruleType?: string
  triggerEvent: string
  triggerField?: string | null
  conditionJson: string
  actionJson: string
  enabled?: boolean
  sortOrder?: number
  cronExpression?: string | null
  actionCommand?: string | null
}

/**
 * 规则执行日志 VO
 */
export interface WorkflowRuleExecutionLogVO {
  id: string
  ruleId: string
  executedAt: string
  matchedCount: number
  successCount: number
  failureCount: number
  errorMessage: string | null
  durationMs: number
  issueKey: string | null
}

/**
 * 规则有效性校验错误
 */
export interface WorkflowRuleValidationError {
  location: string
  message: string
  resourceType: string | null
  resourceRef: string | null
}

/**
 * 规则有效性校验结果 VO
 */
export interface WorkflowRuleValidationVO {
  valid: boolean
  errors: WorkflowRuleValidationError[]
}

/**
 * 规则导出 DTO
 */
export interface WorkflowRuleExportDTO {
  version: string
  exportedAt: string
  rules: WorkflowRuleExportItem[]
}

export interface WorkflowRuleExportItem {
  name: string
  description: string | null
  ruleType: string
  triggerEvent: string | null
  triggerField: string | null
  conditionJson: string
  actionJson: string
  sortOrder: number
  cronExpression: string | null
  actionCommand: string | null
}

/**
 * 规则导入请求 DTO
 */
export interface WorkflowRuleImportDTO {
  conflictStrategy: 'skip' | 'overwrite'
  rules: WorkflowRuleExportItem[]
}

/**
 * 规则导入结果 VO
 */
export interface WorkflowRuleImportResultVO {
  importedCount: number
  skippedCount: number
  overwrittenCount: number
  importedRules: string[]
  skippedRules: string[]
  errors: string[]
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
  },

  /** 校验规则引用资源有效性 */
  validate(id: string) {
    return request.get<any, R<WorkflowRuleValidationVO>>(`/workflow-rules/${id}/validate`)
  },

  /** 手动触发执行（on_schedule 类型） */
  execute(id: string) {
    return request.post<any, R<WorkflowRuleExecutionLogVO>>(`/workflow-rules/${id}/execute`)
  },

  /** 获取执行日志 */
  getExecutionLogs(id: string, limit = 50) {
    return request.get<any, R<WorkflowRuleExecutionLogVO[]>>(`/workflow-rules/${id}/execution-logs`, {
      params: { limit }
    })
  },

  /** 清空执行日志 */
  clearExecutionLogs(id: string) {
    return request.delete<any, R<void>>(`/workflow-rules/${id}/execution-logs`)
  },

  /** 获取指定工单可用的 Action Rule 列表 */
  getAvailableActionRules(issueId: string) {
    return request.get<any, R<WorkflowRuleVO[]>>(`/issues/${issueId}/action-rules`)
  },

  /** 执行 Action Rule */
  executeActionRule(issueId: string, command: string) {
    return request.post<any, R<void>>(`/issues/${issueId}/action-rules/${command}/execute`)
  },

  /** 导出单条规则 */
  exportRule(id: string) {
    return request.get<any, R<WorkflowRuleExportDTO>>(`/workflow-rules/${id}/export`)
  },

  /** 批量导出项目规则 */
  exportProjectRules(projectId: string) {
    return request.get<any, R<WorkflowRuleExportDTO>>(`/projects/${projectId}/workflow-rules/export`)
  },

  /** 导入规则到项目 */
  importRules(projectId: string, data: WorkflowRuleImportDTO) {
    return request.post<any, R<WorkflowRuleImportResultVO>>(`/projects/${projectId}/workflow-rules/import`, data)
  }
}
