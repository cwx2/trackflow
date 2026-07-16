<template>
  <div class="user-detail-page">
    <!-- 顶部导航 -->
    <div class="page-nav">
      <router-link to="/admin/users" class="back-link">
        <span class="back-icon">←</span>
        <span>用户管理</span>
      </router-link>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
      <span>加载用户信息...</span>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="error-state">
      <div class="error-icon">⚠️</div>
      <h3>加载失败</h3>
      <p>{{ error }}</p>
      <button class="btn-primary" @click="loadProfile">重试</button>
    </div>

    <!-- 用户档案内容 -->
    <template v-else-if="profile">
      <!-- 用户基本信息卡片 -->
      <div class="profile-header">
        <div class="avatar-section">
          <div class="user-avatar">
            {{ profile.displayName?.charAt(0) || profile.username?.charAt(0) || '?' }}
          </div>
        </div>
        <div class="info-section">
          <h1 class="user-display-name">{{ profile.displayName || profile.username }}</h1>
          <div class="user-meta">
            <span class="meta-item">
              <span class="meta-label">用户名</span>
              <span class="meta-value">{{ profile.username }}</span>
            </span>
            <span class="meta-item" v-if="profile.email">
              <span class="meta-label">邮箱</span>
              <span class="meta-value">{{ profile.email }}</span>
            </span>
            <span class="meta-item">
              <span class="meta-label">状态</span>
              <span class="status-tag" :class="profile.status">{{ statusLabel(profile.status) }}</span>
            </span>
          </div>
          <div class="user-timestamps">
            <span v-if="profile.createdAt">注册于 {{ formatDate(profile.createdAt) }}</span>
            <span v-if="profile.lastLoginAt" class="separator">·</span>
            <span v-if="profile.lastLoginAt">最近登录 {{ formatDate(profile.lastLoginAt) }}</span>
          </div>
        </div>
        <div class="actions-section">
          <button
            v-if="profile.status === 'active'"
            class="btn-secondary danger"
            @click="handleDisable"
          >禁用用户</button>
          <button
            v-else
            class="btn-secondary"
            @click="handleEnable"
          >启用用户</button>
        </div>
      </div>

      <!-- 三栏信息区 -->
      <div class="profile-sections">
        <!-- 全局角色 -->
        <div class="section-card">
          <div class="section-header">
            <h2 class="section-title">全局角色</h2>
            <button class="btn-text" @click="showRoleDialog = true">管理</button>
          </div>
          <div class="section-body">
            <div v-if="profile.globalRoles.length === 0" class="empty-hint">
              <span class="empty-icon">👤</span>
              <span>未分配全局角色</span>
            </div>
            <div v-else class="role-tags">
              <span v-for="role in profile.globalRoles" :key="role.id" class="role-tag">
                {{ role.name }}
              </span>
            </div>
          </div>
        </div>

        <!-- 项目角色分布 -->
        <div class="section-card">
          <div class="section-header">
            <h2 class="section-title">项目角色</h2>
            <span class="section-count">{{ profile.projectRoles.length }} 个项目</span>
          </div>
          <div class="section-body">
            <div v-if="profile.projectRoles.length === 0" class="empty-hint">
              <span class="empty-icon">📁</span>
              <span>未加入任何项目</span>
            </div>
            <div v-else class="project-role-list">
              <div v-for="pr in profile.projectRoles" :key="`${pr.projectId}-${pr.roleCode}`" class="project-role-item">
                <div class="project-info">
                  <span class="project-key">{{ pr.projectKey }}</span>
                  <span class="project-name">{{ pr.projectName }}</span>
                </div>
                <span class="project-role-name">{{ pr.roleName }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 最近活动 -->
        <div class="section-card section-activity">
          <div class="section-header">
            <h2 class="section-title">最近活动</h2>
          </div>
          <div class="section-body">
            <div v-if="profile.recentActivities.length === 0" class="empty-hint">
              <span class="empty-icon">📝</span>
              <span>暂无操作记录</span>
            </div>
            <div v-else class="activity-list">
              <div v-for="activity in profile.recentActivities" :key="activity.id" class="activity-item">
                <div class="activity-main">
                  <span class="activity-action">{{ formatAction(activity) }}</span>
                  <router-link
                    v-if="activity.issueKey"
                    :to="`/issues/${activity.issueKey}`"
                    class="activity-issue"
                  >{{ activity.issueKey }}</router-link>
                  <span v-if="activity.issueTitle" class="activity-issue-title">{{ activity.issueTitle }}</span>
                </div>
                <span class="activity-time">{{ formatRelativeTime(activity.createdAt) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 角色管理弹窗 -->
    <div class="modal-overlay" v-if="showRoleDialog" @click.self="showRoleDialog = false">
      <div class="modal-sm">
        <div class="modal-header">
          <h3>管理全局角色 — {{ profile?.displayName }}</h3>
          <button class="btn-close" @click="showRoleDialog = false">×</button>
        </div>
        <div class="modal-body">
          <div class="role-list">
            <div v-for="role in globalRoles" :key="role.id" class="role-item">
              <label class="role-check">
                <input
                  type="checkbox"
                  :checked="currentRoleIds.includes(String(role.id))"
                  @change="toggleRole(role.id)"
                />
                <span class="role-name">{{ role.name }}</span>
                <span class="role-code">{{ role.code }}</span>
              </label>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { userApi } from '@/api'
import type { UserProfileVO } from '@/api/user'
import request from '@/api/request'

const route = useRoute()
const router = useRouter()

const profile = ref<UserProfileVO | null>(null)
const loading = ref(true)
const error = ref('')
const showRoleDialog = ref(false)
const globalRoles = ref<any[]>([])

const userId = computed(() => route.params.id as string)
const currentRoleIds = computed(() => profile.value?.globalRoles.map(r => r.id) || [])

async function loadProfile() {
  loading.value = true
  error.value = ''
  try {
    const res = await userApi.getProfile(userId.value)
    profile.value = res.data
  } catch (e: any) {
    error.value = e.response?.data?.message || '无法加载用户信息'
  } finally {
    loading.value = false
  }
}

async function loadGlobalRoles() {
  try {
    const res: any = await request.get('/roles', { params: { roleType: 'global', pageSize: 50 } })
    globalRoles.value = res.data?.list || []
  } catch (e) { globalRoles.value = [] }
}

async function toggleRole(roleId: number) {
  if (!profile.value) return
  const roleIdStr = String(roleId)
  try {
    if (currentRoleIds.value.includes(roleIdStr)) {
      await userApi.removeRole(userId.value, roleIdStr)
    } else {
      await userApi.assignRole(userId.value, roleIdStr)
    }
    // 重新加载档案以刷新角色列表
    await loadProfile()
  } catch (e: any) {
    alert(e.response?.data?.message || '操作失败')
  }
}

async function handleDisable() {
  if (!confirm('确定禁用该用户？禁用后用户将无法登录系统。')) return
  try {
    await userApi.disable(userId.value)
    await loadProfile()
  } catch (e: any) {
    alert(e.response?.data?.message || '操作失败')
  }
}

async function handleEnable() {
  try {
    await userApi.enable(userId.value)
    await loadProfile()
  } catch (e: any) {
    alert(e.response?.data?.message || '操作失败')
  }
}

function statusLabel(status: string) {
  return status === 'active' ? '启用' : status === 'disabled' ? '禁用' : status
}

function formatDate(dt: string) {
  if (!dt) return ''
  return new Date(dt).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

function formatRelativeTime(dt: string) {
  if (!dt) return ''
  const now = Date.now()
  const time = new Date(dt).getTime()
  const diff = now - time
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  return formatDate(dt)
}

function formatAction(activity: { action: string; fieldName?: string; oldValue?: string; newValue?: string }) {
  const actionMap: Record<string, string> = {
    'create': '创建了',
    'created': '创建了',
    'update': '更新了',
    'updated': '更新了',
    'comment': '评论了',
    'commented': '评论了',
    'delete': '删除了',
    'deleted': '删除了',
    'status_change': '修改了状态',
    'status_changed': '修改了状态',
    'assign': '分配了',
    'assigned': '分配了',
    'field_changed': '修改了字段',
  }

  let label = actionMap[activity.action] || activity.action
  if ((activity.action === 'update' || activity.action === 'updated' || activity.action === 'field_changed') && activity.fieldName) {
    const fieldMap: Record<string, string> = {
      'title': '标题',
      'description': '描述',
      'status': '状态',
      'assignee_id': '负责人',
      'priority': '优先级',
      'sprint_id': 'Sprint',
      'due_date': '截止日期',
      'issue_type': '类型',
      'parent_id': '父工单',
      'estimated_hours': '预估工时',
      'spent_hours': '已用工时',
    }
    label = `修改了${fieldMap[activity.fieldName] || activity.fieldName}`
  }
  return label
}

onMounted(() => {
  loadProfile()
  loadGlobalRoles()
})
</script>

<style scoped>
.user-detail-page {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
  max-width: 960px;
}

/* 顶部导航 */
.page-nav {
  margin-bottom: 20px;
}
.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  text-decoration: none;
  font-size: var(--font-size-sm);
  transition: color 150ms;
}
.back-link:hover {
  color: var(--accent-blue);
}
.back-icon {
  font-size: 14px;
}

/* 加载/错误状态 */
.loading-state {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 48px 0;
  justify-content: center;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}
.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid var(--border-color);
  border-top-color: var(--accent-blue);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.error-state {
  text-align: center;
  padding: 48px 0;
  color: var(--text-secondary);
}
.error-icon { font-size: 32px; margin-bottom: 12px; }
.error-state h3 { color: var(--text-primary); margin-bottom: 8px; font-size: 16px; }
.error-state p { margin-bottom: 16px; font-size: var(--font-size-sm); }

/* 用户头部卡片 */
.profile-header {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  padding: 24px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  margin-bottom: 24px;
}
.user-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--accent-blue);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 600;
  flex-shrink: 0;
}
.info-section {
  flex: 1;
  min-width: 0;
}
.user-display-name {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-bright);
  margin-bottom: 8px;
  letter-spacing: -0.3px;
}
.user-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 8px;
}
.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-sm);
}
.meta-label {
  color: var(--text-muted);
}
.meta-value {
  color: var(--text-primary);
}
.status-tag {
  font-size: var(--font-size-xs);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
}
.status-tag.active {
  background: rgba(76,175,80,0.15);
  color: var(--accent-green);
}
.status-tag.disabled {
  background: rgba(244,67,54,0.15);
  color: var(--accent-red);
}
.user-timestamps {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}
.separator { margin: 0 6px; }

.actions-section {
  flex-shrink: 0;
}

/* 按钮 */
.btn-primary {
  height: 32px;
  padding: 0 14px;
  background: var(--accent-blue);
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: opacity 150ms;
}
.btn-primary:hover { opacity: 0.9; }

.btn-secondary {
  height: 32px;
  padding: 0 14px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: background 150ms;
}
.btn-secondary:hover { background: var(--bg-hover); }
.btn-secondary.danger { color: var(--accent-red); border-color: var(--accent-red); }
.btn-secondary.danger:hover { background: rgba(244,67,54,0.08); }

.btn-text {
  background: none;
  border: none;
  color: var(--accent-blue);
  font-size: var(--font-size-sm);
  cursor: pointer;
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  transition: background 150ms;
}
.btn-text:hover { background: var(--bg-hover); }

/* 信息区块 */
.profile-sections {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.section-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  overflow: hidden;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border-light);
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-bright);
}
.section-count {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}
.section-body {
  padding: 16px 18px;
}

/* 空状态 */
.empty-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  font-size: var(--font-size-sm);
  padding: 8px 0;
}
.empty-icon { font-size: 16px; }

/* 角色标签 */
.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.role-tag {
  padding: 4px 10px;
  background: rgba(88,166,255,0.12);
  color: var(--accent-blue);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
  font-weight: 500;
}

/* 项目角色列表 */
.project-role-list {
  display: flex;
  flex-direction: column;
}
.project-role-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-light);
}
.project-role-item:last-child { border-bottom: none; }
.project-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.project-key {
  font-size: var(--font-size-xs);
  font-weight: 500;
  color: var(--accent-blue);
  background: rgba(88,166,255,0.08);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}
.project-name {
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.project-role-name {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  flex-shrink: 0;
}

/* 活动列表 */
.activity-list {
  display: flex;
  flex-direction: column;
}
.activity-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-light);
  gap: 12px;
}
.activity-item:last-child { border-bottom: none; }
.activity-main {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex: 1;
}
.activity-action {
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  flex-shrink: 0;
}
.activity-issue {
  font-size: var(--font-size-xs);
  font-weight: 500;
  color: var(--accent-blue);
  text-decoration: none;
  flex-shrink: 0;
}
.activity-issue:hover { text-decoration: underline; }
.activity-issue-title {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.activity-time {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  flex-shrink: 0;
}

/* 角色管理弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.modal-sm {
  width: 420px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border-color);
}
.modal-header h3 {
  font-size: 15px;
  color: var(--text-bright);
  font-weight: 500;
}
.btn-close {
  background: none;
  border: none;
  color: var(--text-secondary);
  font-size: 16px;
  cursor: pointer;
}
.modal-body {
  padding: 16px 18px;
  max-height: 300px;
  overflow-y: auto;
}
.role-item { margin-bottom: 8px; }
.role-check {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: var(--font-size-sm);
  color: var(--text-primary);
}
.role-check input { accent-color: var(--accent-blue); }
.role-name { flex: 1; }
.role-code {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}
</style>
