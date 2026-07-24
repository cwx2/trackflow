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
  },

  // ===== 标签收藏（侧边栏 Tags 面板） =====

  /** 获取当前用户收藏的标签面板数据（含匹配工单数量） */
  getFavoritePanel(projectId?: string) {
    return request.get<any, R<TagPanelItemVO[]>>('/tag-favorites', {
      params: projectId ? { projectId } : undefined
    })
  },

  /** 添加标签到收藏 */
  addFavorite(tagId: string) {
    return request.post<any, R<void>>(`/tag-favorites/${tagId}`)
  },

  /** 从收藏中移除标签 */
  removeFavorite(tagId: string) {
    return request.delete<any, R<void>>(`/tag-favorites/${tagId}`)
  },

  /** 重新排序收藏标签（拖拽排序） */
  reorderFavorites(tagIds: string[]) {
    return request.put<any, R<void>>('/tag-favorites/reorder', { tagIds: tagIds.map(Number) })
  },

  /** 获取所有可收藏的标签列表（管理面板用） */
  listAvailableTags(projectId?: string) {
    return request.get<any, R<AvailableTagVO[]>>('/tag-favorites/available', {
      params: projectId ? { projectId } : undefined
    })
  }
}

/** 标签面板项 */
export interface TagPanelItemVO {
  id: string
  name: string
  color: string
  projectId: string | null
  count: number
}

/** 可收藏标签项 */
export interface AvailableTagVO {
  id: string
  name: string
  color: string
  projectId: string
  favorited: boolean
}
