import request from './request'
import type { R, IssueTagVO } from './types'

/**
 * 标签模块 API
 */
export const tagApi = {
  /** 获取项目下所有标签 */
  listProjectTags(projectId: string) {
    return request.get<any, R<IssueTagVO[]>>(`/projects/${projectId}/tags`)
  },

  /** 创建项目标签 */
  createProjectTag(projectId: string, data: { name: string; color?: string }) {
    return request.post<any, R<IssueTagVO>>(`/projects/${projectId}/tags`, data)
  }
}
