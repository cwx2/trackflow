import request from './request'
import type { R, PageResult, ProjectVO, ProjectDetailVO, ProjectMemberVO, ProjectActivityVO, ProjectStatisticsVO } from './types'
import type { AxiosRequestConfig } from 'axios'

/** 可选请求配置（支持 _silent403 静默 403） */
type RequestOptions = AxiosRequestConfig & { _silent403?: boolean }

/**
 * 项目模块 API
 */
export const projectApi = {
  /** 项目列表 */
  list(params?: { keyword?: string; status?: string; page?: number; pageSize?: number }) {
    return request.get<any, R<PageResult<ProjectVO>>>('/projects', { params })
  },

  /** 项目详情 */
  getById(id: string) {
    return request.get<any, R<ProjectVO>>(`/projects/${id}`)
  },

  /** 项目详情（含当前用户角色和成员统计） */
  getDetail(id: string) {
    return request.get<any, R<ProjectDetailVO>>(`/projects/${id}/detail`)
  },

  /** 创建项目 */
  create(data: { name: string; key: string; description?: string; template?: string }) {
    return request.post<any, R<ProjectVO>>('/projects', data)
  },

  /** 更新项目 */
  update(id: string, data: { name?: string; description?: string; leadId?: string; visibility?: string }) {
    return request.put<any, R<ProjectVO>>(`/projects/${id}`, data)
  },

  /** 归档项目 */
  archive(id: string) {
    return request.put<any, R<void>>(`/projects/${id}/archive`)
  },

  /** 恢复项目 */
  restore(id: string) {
    return request.put<any, R<void>>(`/projects/${id}/restore`)
  },

  /** 删除项目预检查（返回受影响数据量） */
  deletePreCheck(id: string) {
    return request.get<any, R<{
      projectName: string
      projectKey: string
      issueCount: number
      sprintCount: number
      memberCount: number
      openIssueCount: number
      deletable: boolean
      reason: string | null
    }>>(`/projects/${id}/delete-precheck`)
  },

  /** 删除项目（需输入 confirmKey 确认） */
  delete(id: string, confirmKey: string) {
    return request.delete<any, R<void>>(`/projects/${id}`, { params: { confirmKey } })
  },

  /** 获取项目成员列表 */
  listMembers(projectId: string, config?: RequestOptions) {
    return request.get<any, R<ProjectMemberVO[]>>(`/projects/${projectId}/members`, config)
  },

  /** 添加项目成员（支持多角色） */
  addMember(projectId: string, data: { userId: string; roleIds: number[] }) {
    return request.post<any, R<void>>(`/projects/${projectId}/members`, data)
  },

  /** 更新成员角色（全量替换） */
  updateMemberRole(projectId: string, userId: string, roleIds: number[]) {
    return request.put<any, R<void>>(`/projects/${projectId}/members/${userId}`, { roleIds })
  },

  /** 查询成员被分配的工单数量（移除前预检） */
  getAssignedIssueCount(projectId: string, userId: string) {
    return request.get<any, R<{ count: number }>>(`/projects/${projectId}/members/${userId}/assigned-issue-count`)
  },

  /** 移除成员（级联清空该成员被分配的工单负责人） */
  removeMember(projectId: string, userId: string) {
    return request.delete<any, R<{ affectedIssueCount: number }>>(`/projects/${projectId}/members/${userId}`)
  },

  /** 获取项目活动日志 */
  listActivities(projectId: string, params?: { page?: number; pageSize?: number }) {
    return request.get<any, R<PageResult<ProjectActivityVO>>>(`/projects/${projectId}/activities`, { params })
  },

  /** 获取项目回收站保留策略 */
  getTrashSettings(projectId: string) {
    return request.get<any, R<{ trashRetentionDays: number }>>(`/projects/${projectId}/trash-settings`)
  },

  /** 更新项目回收站保留策略 */
  updateTrashSettings(projectId: string, data: { trashRetentionDays: number }) {
    return request.put<any, R<void>>(`/projects/${projectId}/trash-settings`, data)
  },

  /** 获取项目概览统计数据 */
  getStatistics(projectId: string) {
    return request.get<any, R<ProjectStatisticsVO>>(`/projects/${projectId}/statistics`)
  },

  /** 获取项目时间追踪设置 */
  getTimeTrackingSettings(projectId: string) {
    return request.get<any, R<{ enabled: boolean }>>(`/projects/${projectId}/time-tracking-settings`)
  },

  /** 更新项目时间追踪设置 */
  updateTimeTrackingSettings(projectId: string, data: { enabled: boolean }) {
    return request.put<any, R<{ enabled: boolean }>>(`/projects/${projectId}/time-tracking-settings`, data)
  }
}
