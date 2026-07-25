import request from './request'
import type { R } from './types'

/**
 * 工单投票状态 VO
 */
export interface IssueVoteStatusVO {
  /** 当前用户是否已投票 */
  voted: boolean
  /** 投票者总数 */
  voteCount: number
}

/**
 * 投票者信息 VO
 */
export interface IssueVoterVO {
  userId: string
  displayName: string
  username: string
  avatarUrl: string | null
}

/**
 * 工单投票 API
 */
export const issueVoteApi = {
  /**
   * 对工单投票
   */
  vote(issueId: string) {
    return request.post<any, R<IssueVoteStatusVO>>(`/issues/${issueId}/voters`)
  },

  /**
   * 取消对工单的投票
   */
  unvote(issueId: string) {
    return request.delete<any, R<IssueVoteStatusVO>>(`/issues/${issueId}/voters`)
  },

  /**
   * 获取当前用户的投票状态
   */
  getStatus(issueId: string) {
    return request.get<any, R<IssueVoteStatusVO>>(`/issues/${issueId}/voters/status`)
  },

  /**
   * 获取工单的投票者列表
   */
  listVoters(issueId: string) {
    return request.get<any, R<IssueVoterVO[]>>(`/issues/${issueId}/voters`)
  }
}
