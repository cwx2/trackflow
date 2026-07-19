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
              <span class="status-tag" :class="profile.status">{{ statusLabel(profile.status, profile.banStatus) }}</span>
            </span>
          </div>
          <!-- 禁用信息展示 -->
          <div v-if="profile.status === 'disabled' && profile.banStatus" class="ban-info-section">
            <div class="ban-info-card">
              <div class="ban-info-header">
                <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor" style="color: var(--color-danger-light)">
                  <path d="M8 1.5a6.5 6.5 0 100 13 6.5 6.5 0 000-13zM0 8a8 8 0 1116 0A8 8 0 010 8z"/>
                  <path d="M4.646 4.646a.5.5 0 01.708 0L8 7.293l2.646-2.647a.5.5 0 01.708.708L8.707 8l2.647 2.646a.5.5 0 01-.708.708L8 8.707l-2.646 2.647a.5.5 0 01-.708-.708L7.293 8 4.646 5.354a.5.5 0 010-.708z"/>
                </svg>
                <span class="ban-info-title">账号已{{ getBanStatusLabel(profile.banStatus) }}</span>
              </div>
              <div class="ban-info-details">
                <span v-if="profile.banReason" class="ban-reason">{{ profile.banReason }}</span>
                <span class="ban-meta">
                  <span v-if="profile.bannedByName">由 {{ profile.bannedByName }} 操作</span>
                  <span v-if="profile.bannedAt">{{ formatDate(profile.bannedAt) }}</span>
                </span>
              </div>
            </div>
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
            <div class="section-header-right">
              <span class="section-count">{{ profile.projectRoles.length }} 个项目</span>
              <button class="btn-text" @click="openAssignRoleDialog">赋予角色</button>
            </div>
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
                <div class="project-role-actions">
                  <span class="project-role-name">{{ pr.roleName }}</span>
                  <span v-if="pr.source === 'group'" class="role-source-tag" :title="`通过组「${pr.groupName}」继承`">
                    组继承
                  </span>
                  <button
                    v-if="pr.source !== 'group'"
                    class="btn-revoke"
                    title="撤销此角色"
                    @click="revokeProjectRole(pr)"
                  >×</button>
                </div>
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

    <!-- 赋予项目角色弹窗 -->
    <div class="modal-overlay" v-if="showAssignProjectRoleDialog" @click.self="closeAssignDialog">
      <div class="modal-sm">
        <div class="modal-header">
          <h3>赋予项目角色 — {{ profile?.displayName }}</h3>
          <button class="btn-close" @click="closeAssignDialog">×</button>
        </div>
        <div class="modal-body assign-role-form">
          <div class="form-field">
            <label class="form-label">选择项目</label>
            <select v-model="assignProjectId" class="form-select">
              <option value="">请选择项目</option>
              <option v-for="p in allProjects" :key="p.id" :value="p.id">
                {{ p.key }} — {{ p.name }}
              </option>
            </select>
          </div>
          <div class="form-field">
            <label class="form-label">选择角色</label>
            <select v-model="assignRoleId" class="form-select">
              <option value="">请选择角色</option>
              <option v-for="r in projectRoles" :key="r.id" :value="r.id">
                {{ r.name }}
              </option>
            </select>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-secondary" @click="closeAssignDialog">取消</button>
          <button
            class="btn-primary"
            :disabled="!assignProjectId || !assignRoleId || assignLoading"
            @click="confirmAssignProjectRole"
          >
            {{ assignLoading ? '赋予中...' : '赋予角色' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Modal, Message } from '@arco-design/web-vue'
import { userApi, projectApi } from '@/api'
import type { UserProfileVO, UserProfileProjectRoleInfo } from '@/api/user'
import request from '@/api/request'
import { localizeActionShort, fieldLabelMap } from '@/utils/fieldLabels'

const route = useRoute()
const router = useRouter()

const profile = ref<UserProfileVO | null>(null)
const loading = ref(true)
const error = ref('')
const showRoleDialog = ref(false)
const globalRoles = ref<any[]>([])

// 赋予项目角色相关
const showAssignProjectRoleDialog = ref(false)
const assignProjectId = ref('')
const assignRoleId = ref('')
const assignLoading = ref(false)
const allProjects = ref<any[]>([])
const projectRoles = ref<any[]>([])

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
    Message.error(e.response?.data?.message || '操作失败')
  }
}

async function handleDisable() {
  if (!profile.value) return
  const user = profile.value
  const banStatus = ref('banned')
  const banReason = ref('')

  Modal.confirm({
    title: '禁用用户',
    width: 480,
    content: () => h('div', { style: 'padding: 4px 0' }, [
      h('p', { style: 'margin-bottom: 16px; color: var(--tf-text-secondary)' },
        `确定要禁用用户 "${user.displayName}" (${user.username}) 吗？禁用后该用户将无法登录系统。`),
      h('div', { style: 'margin-bottom: 16px' }, [
        h('label', { style: 'display: block; font-size: 13px; font-weight: 500; margin-bottom: 6px; color: var(--tf-text-primary)' }, '禁用状态'),
        h('select', {
          value: banStatus.value,
          style: 'width: 100%; height: 32px; padding: 0 8px; border: 1px solid var(--tf-border); border-radius: 6px; background: var(--tf-bg-surface); color: var(--tf-text-primary); font-size: 13px',
          onChange: (e: Event) => { banStatus.value = (e.target as HTMLSelectElement).value }
        }, [
          h('option', { value: 'banned' }, '封禁 — 违规行为或安全问题'),
          h('option', { value: 'suspended' }, '暂停 — 临时停用（如休假）'),
          h('option', { value: 'inactive' }, '不活跃 — 长期未使用'),
          h('option', { value: 'deactivated' }, '注销 — 员工离职'),
          h('option', { value: 'locked' }, '锁定 — 安全审计锁定')
        ])
      ]),
      h('div', [
        h('label', { style: 'display: block; font-size: 13px; font-weight: 500; margin-bottom: 6px; color: var(--tf-text-primary)' }, '原因说明（可选）'),
        h('textarea', {
          value: banReason.value,
          placeholder: '例如：2026年7月离职、安全审计发现异常登录...',
          style: 'width: 100%; min-height: 72px; padding: 8px; border: 1px solid var(--tf-border); border-radius: 6px; background: var(--tf-bg-surface); color: var(--tf-text-primary); font-size: 13px; resize: vertical; font-family: inherit',
          onInput: (e: Event) => { banReason.value = (e.target as HTMLTextAreaElement).value }
        })
      ])
    ]),
    okText: '禁用用户',
    cancelText: '取消',
    okButtonProps: { status: 'danger' },
    async onOk() {
      try {
        await userApi.disable(userId.value, {
          banStatus: banStatus.value,
          banReason: banReason.value || undefined
        })
        Message.success('用户已禁用')
        await loadProfile()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '禁用失败')
      }
    }
  })
}

async function handleEnable() {
  if (!profile.value) return
  const user = profile.value
  Modal.confirm({
    title: '确认启用用户',
    content: `确定要启用用户 "${user.displayName}" (${user.username}) 吗？`,
    okText: '启用用户',
    cancelText: '取消',
    async onOk() {
      try {
        await userApi.enable(userId.value)
        Message.success('用户已启用')
        await loadProfile()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '启用失败')
      }
    }
  })
}

const BAN_STATUS_LABELS: Record<string, string> = {
  banned: '封禁',
  suspended: '暂停',
  inactive: '不活跃',
  deactivated: '注销',
  locked: '锁定'
}

function getBanStatusLabel(banStatus?: string): string {
  if (!banStatus) return '禁用'
  return BAN_STATUS_LABELS[banStatus] || '禁用'
}

function statusLabel(status: string, banStatus?: string) {
  if (status === 'active') return '启用'
  if (status === 'disabled' && banStatus) return getBanStatusLabel(banStatus)
  return '禁用'
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
  // 特殊 action：移动到项目
  if (activity.action === 'moved_to_project') {
    return activity.newValue ? `移动到项目 ${activity.newValue}` : '移动到其他项目'
  }
  // 特殊 action：关联操作
  if (activity.action === 'link_added') {
    return activity.newValue ? `添加了关联 ${activity.newValue}` : '添加了关联'
  }
  if (activity.action === 'link_removed') {
    return activity.oldValue ? `移除了关联 ${activity.oldValue}` : '移除了关联'
  }
  // 通用 update：拼接字段中文名
  if ((activity.action === 'update' || activity.action === 'updated' || activity.action === 'field_changed') && activity.fieldName) {
    return `修改了${fieldLabelMap[activity.fieldName] || activity.fieldName}`
  }
  return localizeActionShort(activity.action)
}

// ===== 项目角色管理 =====

async function loadProjectRoles() {
  try {
    const res: any = await request.get('/roles', { params: { roleType: 'project', pageSize: 50 } })
    projectRoles.value = res.data?.list || []
  } catch (e) { projectRoles.value = [] }
}

async function loadAllProjects() {
  try {
    const res = await projectApi.list({ pageSize: 100 })
    allProjects.value = res.data?.list || []
  } catch (e) { allProjects.value = [] }
}

function openAssignRoleDialog() {
  assignProjectId.value = ''
  assignRoleId.value = ''
  showAssignProjectRoleDialog.value = true
}

function closeAssignDialog() {
  showAssignProjectRoleDialog.value = false
  assignProjectId.value = ''
  assignRoleId.value = ''
}

async function confirmAssignProjectRole() {
  if (!assignProjectId.value || !assignRoleId.value || !profile.value) return
  assignLoading.value = true
  try {
    await projectApi.addMember(assignProjectId.value, {
      userId: userId.value,
      roleIds: [Number(assignRoleId.value)]
    })
    Message.success('角色赋予成功')
    closeAssignDialog()
    await loadProfile()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '赋予角色失败')
  } finally {
    assignLoading.value = false
  }
}

function revokeProjectRole(pr: UserProfileProjectRoleInfo) {
  if (!profile.value) return

  // 计算该用户在此项目中的所有直接角色
  const directRolesInProject = profile.value.projectRoles.filter(
    r => r.projectId === pr.projectId && r.source !== 'group'
  )

  if (directRolesInProject.length <= 1) {
    // 只有一个直接角色：撤销意味着从项目中完全移除
    Modal.confirm({
      title: '确认撤销角色',
      content: () => h('div', [
        h('p', `确定撤销 "${profile.value!.displayName}" 在项目「${pr.projectName}」中的「${pr.roleName}」角色吗？`),
        h('p', { style: 'color: var(--tf-text-tertiary); font-size: 12px; margin-top: 8px' },
          '这是该用户在此项目中的唯一直接角色，撤销后将从项目中完全移除。')
      ]),
      okText: '撤销角色',
      cancelText: '取消',
      okButtonProps: { status: 'danger' },
      async onOk() {
        try {
          await projectApi.removeMember(pr.projectId, userId.value)
          Message.success('角色已撤销')
          await loadProfile()
        } catch (e: any) {
          Message.error(e.response?.data?.message || '撤销失败')
        }
      }
    })
  } else {
    // 多个角色：只移除选中的角色
    Modal.confirm({
      title: '确认撤销角色',
      content: `确定撤销 "${profile.value.displayName}" 在项目「${pr.projectName}」中的「${pr.roleName}」角色吗？`,
      okText: '撤销角色',
      cancelText: '取消',
      okButtonProps: { status: 'danger' },
      async onOk() {
        try {
          // 保留其余角色
          const remainingRoleIds = directRolesInProject
            .filter(r => r.roleId !== pr.roleId)
            .map(r => Number(r.roleId))
          await projectApi.updateMemberRole(pr.projectId, userId.value, remainingRoleIds)
          Message.success('角色已撤销')
          await loadProfile()
        } catch (e: any) {
          Message.error(e.response?.data?.message || '撤销失败')
        }
      }
    })
  }
}

onMounted(() => {
  loadProfile()
  loadGlobalRoles()
  loadProjectRoles()
  loadAllProjects()
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
.btn-primary:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

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
.section-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
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
.project-role-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.role-source-tag {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: var(--radius-sm);
  background: rgba(210,153,34,0.12);
  color: var(--accent-orange, #d29922);
  white-space: nowrap;
}
.btn-revoke {
  display: none;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-muted);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  transition: background 150ms, color 150ms;
  align-items: center;
  justify-content: center;
}
.project-role-item:hover .btn-revoke {
  display: flex;
}
.btn-revoke:hover {
  background: rgba(244,67,54,0.1);
  color: var(--accent-red);
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
.modal-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 18px;
  border-top: 1px solid var(--border-color);
}
.assign-role-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.form-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-label {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  font-weight: 500;
}
.form-select {
  height: 32px;
  padding: 0 10px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  appearance: auto;
  cursor: pointer;
}
.form-select:focus {
  outline: none;
  border-color: var(--accent-blue);
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

/* Ban info section */
.ban-info-section {
  margin-top: 12px;
}
.ban-info-card {
  background: rgba(244, 67, 54, 0.06);
  border: 1px solid rgba(244, 67, 54, 0.2);
  border-radius: var(--radius-md);
  padding: 10px 12px;
}
.ban-info-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.ban-info-title {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--accent-red);
}
.ban-info-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ban-reason {
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  line-height: 1.4;
}
.ban-meta {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  display: flex;
  gap: 8px;
}
</style>
