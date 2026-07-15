<template>
  <div class="admin-page">
    <div class="page-header">
      <h2 class="page-title">审计日志</h2>
      <div class="header-filters">
        <select v-model="filters.action" class="filter-select" @change="resetAndLoad">
          <option value="">全部操作</option>
          <option value="assign_global_role">分配全局角色</option>
          <option value="remove_global_role">移除全局角色</option>
          <option value="update_role_permissions">修改角色权限</option>
          <option value="disable_user">禁用用户</option>
          <option value="enable_user">启用用户</option>
        </select>
        <select v-model="filters.targetType" class="filter-select" @change="resetAndLoad">
          <option value="">全部目标</option>
          <option value="user">用户</option>
          <option value="role">角色</option>
        </select>
        <input
          v-model="filters.startDate"
          type="date"
          class="filter-input date-input"
          @change="resetAndLoad"
        />
        <span class="date-sep">至</span>
        <input
          v-model="filters.endDate"
          type="date"
          class="filter-input date-input"
          @change="resetAndLoad"
        />
      </div>
    </div>

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
    <div class="pagination" v-if="total > 0">
      <span class="total-text">共 {{ total }} 条记录</span>
      <div class="page-btns">
        <button class="btn-page" :disabled="page <= 1" @click="page--; loadLogs()">‹</button>
        <span class="page-info">{{ page }} / {{ totalPages }}</span>
        <button class="btn-page" :disabled="page >= totalPages" @click="page++; loadLogs()">›</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { auditLogApi } from '@/api'
import type { AuditLogVO } from '@/api/auditLog'
import { Message } from '@arco-design/web-vue'

const logs = ref<AuditLogVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(false)
const totalPages = computed(() => Math.ceil(total.value / pageSize))

const filters = reactive({
  action: '',
  targetType: '',
  startDate: '',
  endDate: ''
})

function resetAndLoad() {
  page.value = 1
  loadLogs()
}

async function loadLogs() {
  loading.value = true
  try {
    const params: any = { page: page.value, pageSize }
    if (filters.action) params.action = filters.action
    if (filters.targetType) params.targetType = filters.targetType
    if (filters.startDate) params.startDate = filters.startDate
    if (filters.endDate) params.endDate = filters.endDate

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

function formatDateTime(dt: string) {
  if (!dt) return '—'
  return new Date(dt).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  })
}

const ACTION_LABELS: Record<string, string> = {
  assign_global_role: '分配全局角色',
  remove_global_role: '移除全局角色',
  update_role_permissions: '修改角色权限',
  disable_user: '禁用用户',
  enable_user: '启用用户'
}

function getActionLabel(action: string) {
  return ACTION_LABELS[action] || action
}

function getActionClass(action: string) {
  if (action.includes('assign') || action === 'enable_user') return 'action-success'
  if (action.includes('remove') || action === 'disable_user') return 'action-danger'
  return 'action-info'
}

function getTargetTypeLabel(type: string) {
  if (type === 'user') return '用户'
  if (type === 'role') return '角色'
  return type
}

function formatDetails(log: AuditLogVO) {
  if (!log.details) return '—'
  try {
    const d = JSON.parse(log.details)

    if (log.action === 'assign_global_role') {
      return `分配角色: ${d.roleName || ''}`
    }
    if (log.action === 'remove_global_role') {
      return `移除角色: ${d.roleName || ''}`
    }
    if (log.action === 'update_role_permissions') {
      const oldCount = d.oldPermissions?.length || 0
      const newCount = d.newPermissions?.length || 0
      return `权限数: ${oldCount} → ${newCount}`
    }
    if (log.action === 'disable_user') {
      return `禁用用户: ${d.displayName || d.username || ''}`
    }
    if (log.action === 'enable_user') {
      return `启用用户: ${d.displayName || d.username || ''}`
    }
    return JSON.stringify(d)
  } catch {
    return log.details
  }
}

onMounted(() => {
  loadLogs()
})
</script>

<style scoped>
.admin-page {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-bright);
}

.header-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-select {
  height: 32px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 0 10px;
  color: var(--text-primary);
  font-size: var(--font-size-sm);
}

.filter-input {
  height: 32px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 0 10px;
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  outline: none;
}

.filter-input:focus {
  border-color: var(--accent-blue);
}

.date-input {
  width: 140px;
}

.date-sep {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
}

.data-table {
  border: 1px solid var(--border-color);
  border-radius: 6px;
  overflow: hidden;
}

.table-header {
  display: flex;
  padding: 8px 12px;
  background: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-color);
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  text-transform: uppercase;
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

.action-info {
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

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
}

.page-btns {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-info {
  font-size: var(--font-size-xs);
}

.btn-page {
  width: 28px;
  height: 28px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-page:hover:not(:disabled) {
  background: var(--bg-hover);
}

.btn-page:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
</style>
