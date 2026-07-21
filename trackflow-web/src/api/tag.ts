import request from './request'
import type { R, IssueTagVO } from './types'
import type { AxiosRequestConfig } from 'axios'

/** 可选请求配置（支持 _silent403 静默 403） */
type RequestOptions = AxiosRequestConfig & { _silent403?: boolean }

/**
 * 标签模块 API
 */
export const tagApi = {
  /** 获取项目下所有标签 */
  listProjectTags(projectId: string, options?: RequestOptions) {
    return request.get<any, R<IssueTagVO[]>>(`/projects/${projectId}/tags`, options)
  },

  /** 创建项目标签 */
  createProjectTag(projectId: string, data: { name: string; color?: string }) {
    return request.post<any, R<IssueTagVO>>(`/projects/${projectId}/tags`, data)
  }
}
