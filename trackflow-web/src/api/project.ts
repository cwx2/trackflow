import request from './request'
import type { R, PageResult, ProjectVO, ProjectMemberVO } from './types'

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

  /** 创建项目 */
  create(data: { name: string; key: string; description?: string }) {
    return request.post<any, R<ProjectVO>>('/projects', data)
  },

  /** 更新项目 */
  update(id: string, data: { name?: string; description?: string; leadId?: string }) {
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

  /** 获取项目成员列表 */
  listMembers(projectId: string) {
    return request.get<any, R<ProjectMemberVO[]>>(`/projects/${projectId}/members`)
  },

  /** 添加项目成员 */
  addMember(projectId: string, data: { userId: string; roleId: number }) {
    return request.post<any, R<void>>(`/projects/${projectId}/members`, data)
  },

  /** 更新成员角色 */
  updateMemberRole(projectId: string, userId: string, roleId: number) {
    return request.put<any, R<void>>(`/projects/${projectId}/members/${userId}`, { roleId })
  },

  /** 移除成员 */
  removeMember(projectId: string, userId: string) {
    return request.delete<any, R<void>>(`/projects/${projectId}/members/${userId}`)
  }
}
