<template>
  <AdminPageLayout title="审计日志">
    <template #actions>
      <a-button size="small" @click="exportJson" :loading="exporting">
        <template #icon><icon-download /></template>
        导出 JSON
      </a-button>
    </template>

    <!-- 搜索与过滤 -->
    <AdminTableToolbar
      v-model="filters.search"
      search-placeholder="搜索审计日志... (支持 author:xxx / target:xxx)"
      :search-width="480"
      @search="resetAndLoad"
      @clear="resetAndLoad"
    >
      <a-select v-model="filters.action" placeholder="全部操作" size="small" allow-clear style="width: 160px" @change="resetAndLoad">
        <a-option-group label="认证">
          <a-option value="login">用户登录</a-option>
          <a-option value="first_login">首次登录</a-option>
          <a-option value="login_failed">登录失败</a-option>
          <a-option value="api_key_used">使用 API Key</a-option>
          <a-option value="api_key_failed">API Key 失败</a-option>
        </a-option-group>
        <a-option-group label="用户管理">
          <a-option value="create_user">创建用户</a-option>
          <a-option value="disable_user">禁用用户</a-option>
          <a-option value="enable_user">启用用户</a-option>
          <a-option value="assign_global_role">分配系统角色</a-option>
          <a-option value="remove_global_role">移除系统角色</a-option>
        </a-option-group>
        <a-option-group label="用户组">
          <a-option value="create_group">创建用户组</a-option>
          <a-option value="update_group">编辑用户组</a-option>
          <a-option value="delete_group">删除用户组</a-option>
          <a-option value="add_group_members">添加组成员</a-option>
          <a-option value="remove_group_members">移除组成员</a-option>
          <a-option value="assign_group_role">分配组角色</a-option>
        </a-option-group>
        <a-option-group label="角色与权限">
          <a-option value="clone_role">克隆角色</a-option>
          <a-option value="update_role_permissions">修改角色权限</a-option>
        </a-option-group>
        <a-option-group label="API Key">
          <a-option value="create_api_key">创建 API Key</a-option>
          <a-option value="revoke_api_key">吊销 API Key</a-option>
          <a-option value="revoke_all_api_keys">吊销所有 API Key</a-option>
        </a-option-group>
        <a-option-group label="系统设置">
          <a-option value="time_tracking_settings_update">修改工时设置</a-option>
        </a-option-group>
        <a-option-group label="项目管理">
          <a-option value="create_project">创建项目</a-option>
          <a-option value="update_project">修改项目</a-option>
          <a-option value="archive_project">归档项目</a-option>
          <a-option value="delete_project">删除项目</a-option>
          <a-option value="update_project_member_role">修改成员角色</a-option>
          <a-option value="remove_project_member">移除项目成员</a-option>
        </a-option-group>
      </a-select>
      <a-select v-model="filters.targetType" placeholder="全部目标" size="small" allow-clear style="width: 120px" @change="resetAndLoad">
        <a-option value="auth">认证</a-option>
        <a-option value="user">用户</a-option>
        <a-option value="user_group">用户组</a-option>
        <a-option value="role">角色</a-option>
        <a-option value="project">项目</a-option>
        <a-option value="api_key">API Key</a-option>
        <a-option value="system_setting">系统设置</a-option>
      </a-select>
      <a-range-picker
        size="small"
        style="width: 240px"
        :model-value="dateRange"
        @change="onDateRangeChange"
      />
    </AdminTableToolbar>

    <!-- 审计日志列表 -->
    <div class="data-table">
      <div class="table-header">
        <div class="col" style="width: 160px">时间</div>
        <div class="col" style="width: 120px">操作者</div>
        <div class="col" style="width: 160px">操作</div>
        <div class="col" style="width: 80px">目标类型</div>
        <div class="col" style="width: 120px">目标</div>
        <div class="col" style="flex: 1">变更详情</div>
      </div>
      <div class="table-body">
        <div v-if="loading" class="loading-row">
          <span class="loading-text">加载中...</span>
        </div>
        <template v-else-if="logs.length > 0">
          <div v-for="log in logs" :key="log.id" class="table-row">
            <div class="col" style="width: 160px">
              <span class="time-text">{{ formatDateTime(log.createdAt) }}</span>
            </div>
            <div class="col" style="width: 120px">
              <span class="operator-name">{{ log.operatorName }}</span>
            </div>
            <div class="col" style="width: 160px">
              <span class="action-tag" :class="getActionClass(log.action)">
                {{ getActionLabel(log.action) }}
              </span>
            </div>
            <div class="col" style="width: 80px">
              <span class="target-type">{{ getTargetTypeLabel(log.targetType) }}</span>
            </div>
            <div class="col" style="width: 120px">
              <span class="target-name">{{ log.targetName || '—' }}</span>
            </div>
            <div class="col detail-col" style="flex: 1">
              <span class="detail-text">{{ formatDetails(log) }}</span>
            </div>
          </div>
        </template>
        <div v-else class="empty-state">
          <div class="empty-icon">📋</div>
          <h3 class="empty-title">暂无审计日志</h3>
          <p class="empty-desc">权限变更操作将自动记录在此</p>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <AdminPagination
      v-model:current="page"
      v-model:page-size="pageSize"
      :total="total"
      @change="loadLogs"
      @page-size-change="loadLogs"
    />
  </AdminPageLayout>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { auditLogApi } from '@/api'
import type { AuditLogVO } from '@/api/auditLog'
import { Message } from '@arco-design/web-vue'
import { AdminPageLayout, AdminPagination, AdminTableToolbar } from '@/components/admin'

const logs = ref<AuditLogVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const exporting = ref(false)

const filters = reactive({
  action: '',
  targetType: '',
  startDate: '',
  endDate: '',
  search: ''
})

/** Date range for a-range-picker (array of two strings or undefined) */
const dateRange = computed(() => {
  if (filters.startDate && filters.endDate) return [filters.startDate, filters.endDate]
  if (filters.startDate) return [filters.startDate, '']
  if (filters.endDate) return ['', filters.endDate]
  return undefined
})

function onDateRangeChange(val: (string | undefined)[] | undefined) {
  if (val && val.length === 2) {
    filters.startDate = val[0] || ''
    filters.endDate = val[1] || ''
  } else {
    filters.startDate = ''
    filters.endDate = ''
  }
  resetAndLoad()
}

function resetAndLoad() {
  page.value = 1
  loadLogs()
}

async function loadLogs() {
  loading.value = true
  try {
    const params: any = { page: page.value, pageSize: pageSize.value }
    if (filters.action) params.action = filters.action
    if (filters.targetType) params.targetType = filters.targetType
    if (filters.startDate) params.startDate = filters.startDate
    if (filters.endDate) params.endDate = filters.endDate
    if (filters.search) params.search = filters.search

    const res = await auditLogApi.list(params)
    logs.value = res.data?.list || []
    total.value = res.data?.pagination?.total || 0
  } catch (e) {
    logs.value = []
    total.value = 0
    Message.error('加载审计日志失败')
  } finally {
    loading.value = false
  }
}

async function exportJson() {
  exporting.value = true
  try {
    const params: any = {}
    if (filters.action) params.action = filters.action
    if (filters.targetType) params.targetType = filters.targetType
    if (filters.startDate) params.startDate = filters.startDate
    if (filters.endDate) params.endDate = filters.endDate
    if (filters.search) params.search = filters.search

    const res = await auditLogApi.exportJson(params)
    // 触发浏览器下载
    const blob = new Blob([res as any], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    const today = new Date().toISOString().slice(0, 10)
    link.href = url
    link.download = `audit-logs-${today}.json`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    Message.success('导出成功')
  } catch (e) {
    Message.error('导出失败')
  } finally {
    exporting.value = false
  }
}

function formatDateTime(dt: string) {
  if (!dt) return '—'
  return new Date(dt).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  })
}

// === 操作类型完整映射（覆盖数据库中所有 action） ===
const ACTION_LABELS: Record<string, string> = {
  // 认证
  login: '用户登录',
  first_login: '首次登录',
  login_failed: '登录失败',
  api_key_used: '使用 API Key',
  api_key_failed: 'API Key 失败',
  // 用户管理
  create_user: '创建用户',
  disable_user: '禁用用户',
  enable_user: '启用用户',
  assign_global_role: '分配系统角色',
  remove_global_role: '移除系统角色',
  // 用户组
  create_group: '创建用户组',
  update_group: '编辑用户组',
  delete_group: '删除用户组',
  add_group_members: '添加组成员',
  remove_group_members: '移除组成员',
  assign_group_role: '分配组角色',
  // 角色与权限
  clone_role: '克隆角色',
  update_role_permissions: '修改角色权限',
  // API Key
  create_api_key: '创建 API Key',
  revoke_api_key: '吊销 API Key',
  revoke_all_api_keys: '吊销所有 API Key',
  // 系统设置
  time_tracking_settings_update: '修改工时设置',
  // 项目管理
  create_project: '创建项目',
  update_project: '修改项目',
  archive_project: '归档项目',
  delete_project: '删除项目',
  update_project_member_role: '修改成员角色',
  remove_project_member: '移除项目成员'
}

function getActionLabel(action: string) {
  return ACTION_LABELS[action] || action
}

// === 操作颜色语义分类 ===
type ActionColorType = 'action-auth' | 'action-success' | 'action-warning' | 'action-danger'

function getActionClass(action: string): ActionColorType {
  // 登录/认证 → 灰蓝
  if (['login', 'first_login', 'api_key_used'].includes(action)) return 'action-auth'
  // 创建/分配/启用 → 绿色
  if (['create_user', 'create_group', 'create_api_key', 'assign_global_role',
       'assign_group_role', 'add_group_members', 'enable_user',
       'create_project'].includes(action)) return 'action-success'
  // 修改/编辑/克隆 → 橙色
  if (['update_group', 'update_role_permissions', 'clone_role',
       'time_tracking_settings_update', 'update_project',
       'update_project_member_role'].includes(action)) return 'action-warning'
  // 删除/禁用/吊销/失败/归档/移除 → 红色
  if (['delete_group', 'disable_user', 'remove_global_role', 'remove_group_members',
       'revoke_api_key', 'revoke_all_api_keys', 'login_failed', 'api_key_failed',
       'archive_project', 'delete_project', 'remove_project_member'].includes(action)) return 'action-danger'
  return 'action-auth'
}

// === 目标类型完整映射 ===
const TARGET_TYPE_LABELS: Record<string, string> = {
  auth: '认证',
  user: '用户',
  user_group: '用户组',
  role: '角色',
  project: '项目',
  api_key: 'API Key',
  system_setting: '系统设置'
}

function getTargetTypeLabel(type: string) {
  return TARGET_TYPE_LABELS[type] || type
}

// === 变更详情人类可读化 ===
function formatDetails(log: AuditLogVO): string {
  if (!log.details) return '—'
  try {
    const d = JSON.parse(log.details)
    return formatDetailsByAction(log.action, d)
  } catch {
    return log.details
  }
}

function formatDetailsByAction(action: string, d: Record<string, any>): string {
  switch (action) {
    // 认证事件
    case 'login':
    case 'first_login':
      return `通过 ${formatAuthMethod(d.method)} 登录，用户名：${d.username || '—'}`
    case 'login_failed':
      return `${formatAuthMethod(d.method)} 认证失败，原因：${formatFailReason(d.reason)}${d.path ? `，路径：${d.path}` : ''}`
    case 'api_key_used':
      return `密钥：${d.key_name || d.key_prefix || '—'}`
    case 'api_key_failed':
      return `密钥前缀：${d.key_prefix || '—'}`

    // 用户管理
    case 'create_user':
      return `用户名：${d.username || '—'}，显示名：${d.displayName || '—'}`
    case 'disable_user':
      return `禁用用户：${d.displayName || d.username || '—'}`
    case 'enable_user':
      return `启用用户：${d.displayName || d.username || '—'}`
    case 'assign_global_role':
      return `角色：${d.roleName || '—'}，用户：${d.username || '—'}`
    case 'remove_global_role':
      return `角色：${d.roleName || '—'}，用户：${d.username || '—'}`

    // 用户组
    case 'create_group':
      return `名称：${d.name || '—'}`
    case 'update_group':
      return `名称：${d.name || d.newName || '—'}${d.oldName ? `（原：${d.oldName}）` : ''}`
    case 'delete_group':
      return `名称：${d.name || '—'}，成员数：${d.memberCount ?? '—'}`
    case 'add_group_members':
      return `组：${d.groupName || '—'}，新增 ${d.addedCount ?? '—'} 人`
    case 'remove_group_members':
      return `组：${d.groupName || '—'}，移除 ${d.removedCount ?? '—'} 人`
    case 'assign_group_role':
      return `组：${d.groupName || '—'}，角色：${d.roleName || '—'}`

    // 角色与权限
    case 'clone_role':
      return `源角色：${d.sourceName || '—'} → 新角色：${d.newName || '—'}（${d.permissionCount ?? 0} 个权限）`
    case 'update_role_permissions': {
      const oldCount = d.oldPermissions?.length ?? 0
      const newCount = d.newPermissions?.length ?? 0
      return `角色：${d.roleName || '—'}，权限数：${oldCount} → ${newCount}`
    }

    // API Key
    case 'create_api_key':
      return `名称：${d.key_name || '—'}，过期：${d.expires_at === 'never' ? '永不' : (d.expires_at || '—')}`
    case 'revoke_api_key':
      return `名称：${d.key_name || '—'}，前缀：${d.key_prefix || '—'}`
    case 'revoke_all_api_keys':
      return `原因：${formatRevokeReason(d.reason)}，吊销 ${d.revoked_count ?? '—'} 个`

    // 系统设置
    case 'time_tracking_settings_update':
      return `策略：${d.strategy || '—'}，每日工时：${d.oldHoursPerDay ?? '—'} → ${d.newHoursPerDay ?? '—'}`

    // 项目管理
    case 'create_project':
      return `项目：${d.name || '—'}（${d.key || '—'}）`
    case 'update_project':
      return `项目：${d.project_name || '—'}，修改字段：${Array.isArray(d.changed_fields) ? d.changed_fields.join(', ') : '—'}`
    case 'archive_project':
      return `项目：${d.project_name || '—'}（${d.project_key || '—'}）${d.suspended_sprint_count ? `，暂停 ${d.suspended_sprint_count} 个 Sprint` : ''}`
    case 'delete_project':
      return `项目：${d.project_name || '—'}（${d.project_key || '—'}），成员数：${d.member_count ?? '—'}`
    case 'update_project_member_role':
      return `用户：${d.user_name || '—'}，角色：${d.old_roles || '—'} → ${d.new_roles || '—'}`
    case 'remove_project_member':
      return `用户：${d.user_name || '—'}${d.affected_issue_count ? `，清空 ${d.affected_issue_count} 个工单负责人` : ''}`

    default:
      return formatGenericDetails(d)
  }
}

function formatAuthMethod(method: string | undefined): string {
  if (method === 'jwt') return 'JWT'
  if (method === 'api_key') return 'API Key'
  return method || '未知'
}

function formatFailReason(reason: string | undefined): string {
  if (!reason) return '未知'
  const reasonMap: Record<string, string> = {
    InvalidBearerTokenException: 'Token 无效',
    ExpiredJwtException: 'Token 已过期',
    user_disabled: '用户已禁用',
    invalid_key: '密钥无效',
    key_expired: '密钥已过期',
    key_revoked: '密钥已吊销'
  }
  return reasonMap[reason] || reason
}

function formatRevokeReason(reason: string | undefined): string {
  if (!reason) return '未知'
  const reasonMap: Record<string, string> = {
    user_disabled: '用户被禁用',
    manual: '手动吊销'
  }
  return reasonMap[reason] || reason
}

/** 对未知 action 的通用 details 格式化（key-value 列表） */
function formatGenericDetails(d: Record<string, any>): string {
  const entries = Object.entries(d)
  if (entries.length === 0) return '—'
  return entries
    .map(([key, value]) => {
      const label = key.replace(/_/g, ' ')
      const val = Array.isArray(value) ? value.join(', ') : String(value)
      return `${label}：${val}`
    })
    .join('；')
}

onMounted(() => {
  loadLogs()
})
</script>

<style scoped>
.data-table {
  width: 100%;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  overflow: hidden;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.table-header {
  display: flex;
  padding: 8px 12px;
  background: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-color);
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  text-transform: uppercase;
  flex-shrink: 0;
}

.table-body {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.table-row {
  display: flex;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-light);
  align-items: center;
  transition: background 0.1s;
}

.table-row:last-child {
  border-bottom: none;
}

.table-row:hover {
  background: var(--bg-hover);
}

.col {
  padding: 0 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-size-sm);
}

.detail-col {
  white-space: normal;
  word-break: break-all;
  min-width: 0;
  flex: 1;
}

.time-text {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
}

.operator-name {
  font-weight: 500;
  color: var(--text-primary);
}

.action-tag {
  font-size: var(--font-size-xs);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  white-space: nowrap;
}

.action-success {
  background: rgba(76, 175, 80, 0.12);
  color: var(--accent-green);
}

.action-danger {
  background: rgba(244, 67, 54, 0.12);
  color: var(--accent-red);
}

.action-warning {
  background: rgba(255, 152, 0, 0.12);
  color: var(--accent-orange, #f0a020);
}

.action-auth {
  background: rgba(88, 166, 255, 0.12);
  color: var(--accent-blue);
}

.target-type {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
}

.target-name {
  color: var(--text-primary);
}

.detail-text {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  line-height: 1.4;
}

.loading-row {
  padding: 40px;
  text-align: center;
}

.loading-text {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
}

.empty-state {
  padding: 48px 24px;
  text-align: center;
}

.empty-icon {
  font-size: 36px;
  margin-bottom: 12px;
}

.empty-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary);
  margin: 0 0 8px 0;
}

.empty-desc {
  font-size: 13px;
  color: var(--text-tertiary);
  margin: 0;
}
</style>
