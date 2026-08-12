import request from './request'
import type { R, PageResult, RoleVO, RoleUsersVO } from './types'

/** 权限条目 */
export interface PermissionItem {
  code: string
  name: string
  description: string
  scope: string
}

/** 权限分组 */
export interface PermissionGroup {
  category: string
  permissions: PermissionItem[]
}

/** 创建角色参数 */
export interface CreateRoleDTO {
  name: string
  code: string
  roleType: string
  description?: string
}

/** 更新角色参数 */
export interface UpdateRoleDTO {
  name?: string
  description?: string
}

/** 克隆角色参数 */
export interface CloneRoleDTO {
  name: string
  code: string
}

/**
 * 角色管理模块 API
 */
/** 角色管理统计 */
export interface RoleStatsVO {
  total: number
  globalRoles: number
  projectRoles: number
  rolesInUse: number
}

export const roleApi = {
  /** 角色管理统计 */
  stats() {
    return request.get<any, R<RoleStatsVO>>('/roles/stats')
  },

  /** 分页查询角色列表 */
  list(params?: { roleType?: string; pageSize?: number; page?: number }) {
    return request.get<any, R<PageResult<RoleVO>>>('/roles', { params })
  },

  /** 获取所有权限定义（按分类分组） */
  listPermissionDefinitions() {
    return request.get<any, R<PermissionGroup[]>>('/roles/permission-definitions')
  },

  /** 获取当前用户可授予的权限列表（'*' 表示全部） */
  listGrantablePermissions() {
    return request.get<any, R<string[]>>('/roles/my-grantable-permissions')
  },

  /** 创建角色 */
  create(data: CreateRoleDTO) {
    return request.post<any, R<RoleVO>>('/roles', data)
  },

  /** 更新角色 */
  update(id: string, data: UpdateRoleDTO) {
    return request.put<any, R<RoleVO>>(`/roles/${id}`, data)
  },

  /** 删除角色 */
  delete(id: string) {
    return request.delete<any, R<void>>(`/roles/${id}`)
  },

  /** 克隆角色 */
  clone(id: string, data: CloneRoleDTO) {
    return request.post<any, R<RoleVO>>(`/roles/${id}/clone`, data)
  },

  /** 获取角色的权限列表 */
  getPermissions(id: string) {
    return request.get<any, R<string[]>>(`/roles/${id}/permissions`)
  },

  /** 更新角色的权限列表 */
  updatePermissions(id: string, permissions: string[]) {
    return request.put<any, R<void>>(`/roles/${id}/permissions`, { permissions })
  },

  /** 获取角色下的用户（全局+项目分组） */
  getUsers(id: string) {
    return request.get<any, R<RoleUsersVO>>(`/roles/${id}/users`)
  }
}
