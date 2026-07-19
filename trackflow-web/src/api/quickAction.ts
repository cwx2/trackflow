import request from './request'
import type { R } from './types'

// ==================== 类型定义 ====================

export interface QuickActionDefinitionVO {
  id: string
  projectId: string | null
  actionKey: string
  label: string
  icon: string | null
  sortOrder: number
  formSchema: string  // JSON string of form field definitions
  actions: string     // JSON string of executable actions
  visibility: string  // JSON string of visibility rules
  executionActions: string  // JSON string of automated actions for rule type
  actionType: string  // 'form' or 'rule'
  statusTransitionTo: string | null
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface MailTemplateVO {
  id: string
  projectId: string | null
  actionKey: string
  name: string
  subjectTemplate: string
  bodyTemplate: string
  recipientsRule: string
  sortOrder: number
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface QuickActionExecutionResultVO {
  success: boolean
  commentId: string
  mailSent: boolean
  mailError: string | null
  statusBefore: string | null
  statusAfter: string | null
  logId: string
}

/** 解析后的表单字段配置 */
export interface QuickActionFormField {
  key: string
  label: string
  type: 'radio' | 'checkbox' | 'select' | 'input' | 'textarea'
  required: boolean
  options?: string[]
  optionsSource?: string  // 'mail_templates' | 'sprints'
  placeholder?: string
  defaultValue?: string
}

/** 解析后的操作按钮配置 */
export interface QuickActionButton {
  key: string
  label: string
  type: 'primary' | 'secondary'
}

// ==================== API ====================

export const quickActionApi = {
  /** 获取当前 Issue 可用的快捷动作列表 */
  getAvailableActions(issueId: string) {
    return request.get<any, R<QuickActionDefinitionVO[]>>(`/issues/${issueId}/quick-actions`)
  },

  /** 执行快捷动作（form 类型） */
  execute(issueId: string, actionKey: string, data: {
    formData: string
    resultType: string
    mailTemplateId?: string
  }) {
    return request.post<any, R<QuickActionExecutionResultVO>>(
      `/issues/${issueId}/quick-actions/${actionKey}/execute`,
      data
    )
  },

  /** 执行 rule 类型快捷动作（点击即执行） */
  executeRule(issueId: string, actionKey: string) {
    return request.post<any, R<QuickActionExecutionResultVO>>(
      `/issues/${issueId}/quick-actions/${actionKey}/execute-rule`
    )
  },

  /** 获取指定动作的邮件模板列表 */
  getMailTemplates(issueId: string, actionKey: string, projectId?: string) {
    return request.get<any, R<MailTemplateVO[]>>(
      `/issues/${issueId}/quick-actions/${actionKey}/mail-templates`,
      { params: { projectId } }
    )
  },

  // ==================== 管理端 API ====================

  /** 获取所有动作定义 */
  listDefinitions(projectId?: string) {
    return request.get<any, R<QuickActionDefinitionVO[]>>('/quick-actions/definitions', { params: { projectId } })
  },

  /** 创建动作定义 */
  createDefinition(data: any) {
    return request.post<any, R<QuickActionDefinitionVO>>('/quick-actions/definitions', data)
  },

  /** 更新动作定义 */
  updateDefinition(id: string, data: any) {
    return request.put<any, R<QuickActionDefinitionVO>>(`/quick-actions/definitions/${id}`, data)
  },

  /** 删除动作定义 */
  deleteDefinition(id: string) {
    return request.delete<any, R<void>>(`/quick-actions/definitions/${id}`)
  },

  /** 获取所有邮件模板 */
  listMailTemplates(projectId?: string) {
    return request.get<any, R<MailTemplateVO[]>>('/mail-templates', { params: { projectId } })
  },

  /** 创建邮件模板 */
  createMailTemplate(data: any) {
    return request.post<any, R<MailTemplateVO>>('/mail-templates', data)
  },

  /** 更新邮件模板 */
  updateMailTemplate(id: string, data: any) {
    return request.put<any, R<MailTemplateVO>>(`/mail-templates/${id}`, data)
  },

  /** 删除邮件模板 */
  deleteMailTemplate(id: string) {
    return request.delete<any, R<void>>(`/mail-templates/${id}`)
  },
}
