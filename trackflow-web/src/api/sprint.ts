import request from './request'
import type { R, SprintVO } from './types'

/**
 * Sprint 模块 API
 */
export const sprintApi = {
  /** 项目的 Sprint 列表 */
  listByProject(projectId: string) {
    return request.get<any, R<SprintVO[]>>(`/projects/${projectId}/sprints`)
  },

  /** 创建 Sprint */
  create(projectId: string, data: { name: string; goal?: string; startDate?: string; endDate?: string }) {
    return request.post<any, R<SprintVO>>(`/projects/${projectId}/sprints`, data)
  },

  /** Sprint 详情 */
  getById(id: string) {
    return request.get<any, R<SprintVO>>(`/sprints/${id}`)
  },

  /** 激活 Sprint */
  activate(id: string) {
    return request.put<any, R<SprintVO>>(`/sprints/${id}/activate`)
  },

  /** 完成 Sprint */
  complete(id: string) {
    return request.put<any, R<SprintVO>>(`/sprints/${id}/complete`)
  },

  /** 删除 Sprint */
  delete(id: string) {
    return request.delete<any, R<void>>(`/sprints/${id}`)
  }
}
