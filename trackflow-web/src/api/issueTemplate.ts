import request from './request'
import type { R, IssueTemplateVO } from './types'

/**
 * 工单模板 API
 */
export const issueTemplateApi = {
  /** 获取项目下所有模板 */
  list(projectId: string) {
    return request.get<any, R<IssueTemplateVO[]>>(`/projects/${projectId}/issue-templates`)
  },

  /** 获取单个模板详情 */
  getById(projectId: string, id: string) {
    return request.get<any, R<IssueTemplateVO>>(`/projects/${projectId}/issue-templates/${id}`)
  },

  /** 创建模板 */
  create(projectId: string, data: { name: string; description?: string; issueType?: string; priority?: string; defaultTags?: string; sortOrder?: number }) {
    return request.post<any, R<IssueTemplateVO>>(`/projects/${projectId}/issue-templates`, data)
  },

  /** 更新模板 */
  update(projectId: string, id: string, data: { name?: string; description?: string; issueType?: string; priority?: string; defaultTags?: string; sortOrder?: number }) {
    return request.put<any, R<IssueTemplateVO>>(`/projects/${projectId}/issue-templates/${id}`, data)
  },

  /** 删除模板 */
  delete(projectId: string, id: string) {
    return request.delete<any, R<void>>(`/projects/${projectId}/issue-templates/${id}`)
  }
}
