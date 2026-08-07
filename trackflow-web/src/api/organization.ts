import request from './request'
import type { R, PageResult } from './types'

/** 组织 VO 类型 */
export interface OrgVO {
  id: string
  name: string
  code: string
  description?: string
  createdAt: string
  projectCount?: number
}

/** 组织详情 VO */
export interface OrgDetailVO {
  id: string
  name: string
  code: string
  description?: string
  createdAt: string
  updatedAt?: string
  projectCount?: number
}

/** 组织下项目 VO */
export interface OrgProjectVO {
  id: string
  name: string
  key: string
  status?: string
  issueCount?: number
}

/** 组织级访问控制 VO */
export interface OrgAccessVO {
  id: string
  orgId: string
  userId: string
  userName: string
  userDisplayName: string
  roleId: string
  roleName: string
  createdAt: string
}

/**
 * 组织管理模块 API
 */
export const organizationApi = {
  /** 分页查询组织列表 */
  list(params?: { keyword?: string; page?: number; pageSize?: number; sort?: string }) {
    return request.get<any, R<PageResult<OrgVO>>>('/organizations', { params })
  },

  /** 获取组织详情 */
  getById(id: string) {
    return request.get<any, R<OrgDetailVO>>(`/organizations/${id}`)
  },

  /** 创建组织 */
  create(data: { name: string; code: string; description?: string; projectIds?: number[] }) {
    return request.post<any, R<OrgVO>>('/organizations', data)
  },

  /** 更新组织 */
  update(id: string, data: { name?: string; description?: string }) {
    return request.put<any, R<OrgVO>>(`/organizations/${id}`, data)
  },

  /** 删除组织 */
  delete(id: string) {
    return request.delete<any, R<void>>(`/organizations/${id}`)
  },

  // ===== 项目归属管理 =====

  /** 获取组织下的项目列表 */
  getProjects(orgId: string) {
    return request.get<any, R<OrgProjectVO[]>>(`/organizations/${orgId}/projects`)
  },

  /** 添加项目到组织 */
  addProjects(orgId: string, projectIds: number[]) {
    return request.post<any, R<void>>(`/organizations/${orgId}/projects`, { projectIds })
  },

  /** 从组织中移除项目 */
  removeProject(orgId: string, projectId: string) {
    return request.delete<any, R<void>>(`/organizations/${orgId}/projects/${projectId}`)
  },

  /** 获取未归属任何组织的项目 */
  getUnassignedProjects() {
    return request.get<any, R<OrgProjectVO[]>>('/organizations/unassigned-projects')
  },

  // ===== 访问控制 =====

  /** 获取组织级访问授权列表 */
  getAccessList(orgId: string) {
    return request.get<any, R<OrgAccessVO[]>>(`/organizations/${orgId}/access`)
  },

  /** 授予组织级访问权限 */
  grantAccess(orgId: string, data: { userId: number; roleId: number }) {
    return request.post<any, R<OrgAccessVO>>(`/organizations/${orgId}/access`, data)
  },

  /** 撤销组织级访问授权 */
  revokeAccess(orgId: string, accessId: string) {
    return request.delete<any, R<void>>(`/organizations/${orgId}/access/${accessId}`)
  }
}
