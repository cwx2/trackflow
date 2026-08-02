import request from './request'
import type { R, PageResult, IssueVO, SavedQueryVO } from './types'

/**
 * 保存查询模块 API
 */
export const queryApi = {
  /** 获取查询面板数据（仅返回自己创建的 + 收藏的） */
  getPanel(projectId?: string, hideResolved?: boolean) {
    const params: Record<string, string> = {}
    if (projectId) params.projectId = projectId
    if (hideResolved) params.hideResolved = 'true'
    return request.get<any, R<{ pinned: any[]; queries: any[] }>>('/queries/panel', {
      params: Object.keys(params).length > 0 ? params : undefined
    })
  },

  /** 获取所有可用的共享查询（供管理面板使用） */
  getAvailableQueries(projectId?: string) {
    return request.get<any, R<any[]>>('/queries/available', {
      params: projectId ? { projectId } : undefined
    })
  },

  /** 收藏查询（添加到面板） */
  addFavorite(queryId: string) {
    return request.post<any, R<void>>(`/queries/${queryId}/favorite`)
  },

  /** 取消收藏查询（从面板移除） */
  removeFavorite(queryId: string) {
    return request.delete<any, R<void>>(`/queries/${queryId}/favorite`)
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
  executeById(id: string, params?: { page?: number; pageSize?: number; hideResolved?: string }, signal?: AbortSignal) {
    return request.get<any, R<PageResult<IssueVO>>>(`/queries/${id}/results`, { params, signal })
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
