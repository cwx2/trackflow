import request from './request'
import type { R, PageResult, ProjectVO, ProjectDetailVO, ProjectMemberVO, ProjectActivityVO, ProjectStatisticsVO, ProjectModulesVO, MemberOperationResultVO, FavoriteToggleVO, ProjectCopySummaryVO, ProjectTrashSettingsVO, AssignedIssueCountVO } from './types'
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
      timeEntryCount: number
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

  /** 获取可分配工单的成员列表（排除观察者等不具备 issue:edit 权限的角色） */
  listAssignableMembers(projectId: string, config?: RequestOptions) {
    return request.get<any, R<ProjectMemberVO[]>>(`/projects/${projectId}/assignable-members`, config)
  },

  /** 添加项目成员（支持多角色） */
  addMember(projectId: string, data: { userId: string; roleIds: number[] }) {
    return request.post<any, R<void>>(`/projects/${projectId}/members`, data)
  },

  /** 更新成员角色（全量替换） */
  updateMemberRole(projectId: string, userId: string, roleIds: number[]) {
    return request.put<any, R<MemberOperationResultVO>>(`/projects/${projectId}/members/${userId}`, { roleIds })
  },

  /** 查询成员被分配的工单数量（移除前预检） */
  getAssignedIssueCount(projectId: string, userId: string) {
    return request.get<any, R<AssignedIssueCountVO>>(`/projects/${projectId}/members/${userId}/assigned-issue-count`)
  },

  /** 移除成员（级联清空该成员被分配的工单负责人） */
  removeMember(projectId: string, userId: string) {
    return request.delete<any, R<MemberOperationResultVO>>(`/projects/${projectId}/members/${userId}`)
  },

  /** 获取项目活动日志 */
  listActivities(projectId: string, params?: { page?: number; pageSize?: number }) {
    return request.get<any, R<PageResult<ProjectActivityVO>>>(`/projects/${projectId}/activities`, { params })
  },

  /** 获取项目回收站保留策略 */
  getTrashSettings(projectId: string) {
    return request.get<any, R<ProjectTrashSettingsVO>>(`/projects/${projectId}/trash-settings`)
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
  },

  /** 获取禁用时间追踪的影响评估 */
  getTimeTrackingDisableImpact(projectId: string) {
    return request.get<any, R<{ totalTimeEntries: number; affectedUsers: number; activeTimers: number }>>(`/projects/${projectId}/time-tracking-settings/disable-impact`)
  },

  /** 获取项目可复制模块的概要统计 */
  getCopySummary(projectId: string) {
    return request.get<any, R<ProjectCopySummaryVO>>(`/projects/${projectId}/copy-summary`)
  },

  /** 复制项目 */
  copy(data: { sourceProjectId: string; name: string; key: string; description?: string; copyOptions?: string[] }) {
    return request.post<any, R<ProjectVO>>('/projects/copy', data)
  },

  /** 切换项目收藏状态 */
  toggleFavorite(projectId: string) {
    return request.post<any, R<FavoriteToggleVO>>(`/projects/${projectId}/favorite`)
  },

  /** 获取项目启用模块列表 */
  getEnabledModules(projectId: string) {
    return request.get<any, R<ProjectModulesVO>>(`/projects/${projectId}/modules`)
  },

  /** 更新项目启用模块 */
  updateEnabledModules(projectId: string, data: { enabledModules: string[] }) {
    return request.put<any, R<ProjectModulesVO>>(`/projects/${projectId}/modules`, data)
  }
}
