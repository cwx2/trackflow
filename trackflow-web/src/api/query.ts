import request from './request'
import type { R, PageResult, IssueVO, SavedQueryVO } from './types'

/**
 * 保存查询模块 API
 */
export const queryApi = {
  /** 获取查询面板数据 */
  getPanel(projectId?: string) {
    return request.get<any, R<{ pinned: any[]; queries: any[] }>>('/queries/panel', {
      params: projectId ? { projectId } : undefined
    })
  },

  /** 创建保存查询 */
  create(data: { name: string; filters: any[]; pinned?: boolean; folder?: string; shared?: boolean; icon?: string }) {
    return request.post<any, R<SavedQueryVO>>('/queries', data)
  },

  /** 更新保存查询 */
  update(id: string, data: Record<string, any>) {
    return request.put<any, R<SavedQueryVO>>(`/queries/${id}`, data)
  },

  /** 删除保存查询 */
  delete(id: string) {
    return request.delete<any, R<void>>(`/queries/${id}`)
  },

  /** 执行保存查询 */
  executeById(id: string, params?: { page?: number; pageSize?: number; hideResolved?: string }) {
    return request.get<any, R<PageResult<IssueVO>>>(`/queries/${id}/results`, { params })
  },

  /** 即时执行查询 */
  executeAdhoc(data: { filters: any[]; page?: number; pageSize?: number }) {
    return request.post<any, R<PageResult<IssueVO>>>('/queries/execute', data)
  },

  /** 批量获取计数 */
  batchCount(queryIds: string[]) {
    return request.post<any, R<Record<string, number>>>('/queries/counts', { queryIds })
  }
}
