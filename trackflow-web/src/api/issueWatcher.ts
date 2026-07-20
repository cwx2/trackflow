import request from './request'
import type { R } from './types'

export interface IssueWatcherStatusVO {
  watching: boolean
  watcherCount: number
}

export interface IssueWatcherVO {
  userId: string
  displayName: string
  username: string
  avatarUrl: string | null
}

export const issueWatcherApi = {
  /** 关注工单 */
  watch(issueId: string) {
    return request.post<any, R<IssueWatcherStatusVO>>(`/issues/${issueId}/watchers`)
  },

  /** 取消关注工单 */
  unwatch(issueId: string) {
    return request.delete<any, R<IssueWatcherStatusVO>>(`/issues/${issueId}/watchers`)
  },

  /** 获取关注状态（当前用户是否关注 + 关注者数量） */
  getStatus(issueId: string) {
    return request.get<any, R<IssueWatcherStatusVO>>(`/issues/${issueId}/watchers/status`)
  },

  /** 获取关注者列表 */
  listWatchers(issueId: string) {
    return request.get<any, R<IssueWatcherVO[]>>(`/issues/${issueId}/watchers`)
  }
}
