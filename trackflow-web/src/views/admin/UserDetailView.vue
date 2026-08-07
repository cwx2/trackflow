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
      <a-spin :size="20" />
      <span>加载用户信息...</span>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="error-state">
      <div class="error-icon">⚠️</div>
      <h3>加载失败</h3>
      <p>{{ error }}</p>
      <div class="error-actions">
        <a-button @click="router.push('/admin/users')">返回用户列表</a-button>
        <a-button type="primary" @click="loadProfile">重试</a-button>
      </div>
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
                <icon-close-circle :size="14" style="color: var(--color-danger-light)" />
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
          <!-- Keycloak 编辑提示 -->
          <div class="keycloak-hint">
            <div class="keycloak-hint-icon">
              <icon-info-circle :size="14" />
            </div>
            <span class="keycloak-hint-text">用户信息由身份系统管理</span>
            <a
              v-if="profile.keycloakId"
              :href="keycloakUserUrl"
              target="_blank"
              class="keycloak-link"
              title="在 Keycloak 中编辑用户"
            >
              在 Keycloak 中编辑
              <icon-launch :size="12" class="external-icon" />
            </a>
          </div>
          <a-button
            size="small"
            @click="handleExportUserData"
            :disabled="exporting"
            :loading="exporting"
          >{{ exporting ? '导出中...' : '导出用户数据' }}</a-button>
          <a-button
            v-if="profile.status === 'active'"
            size="small"
            status="danger"
            @click="handleDisable"
          >禁用用户</a-button>
          <a-button
            v-else
            size="small"
            @click="handleEnable"
          >启用用户</a-button>
        </div>
      </div>

      <!-- 三栏信息区 -->
      <div class="profile-sections">
        <!-- 系统角色 -->
        <div class="section-card">
          <div class="section-header">
            <h2 class="section-title">系统角色</h2>
            <a-button type="text" size="small" @click="showRoleDialog = true">管理</a-button>
          </div>
          <div class="section-body">
            <div v-if="profile.globalRoles.length === 0" class="empty-hint">
              <span class="empty-icon">👤</span>
              <span>未分配系统角色</span>
            </div>
            <div v-else class="role-tags">
              <span v-for="role in profile.globalRoles" :key="role.id" class="role-tag">
                {{ role.name }}
              </span>
            </div>
          </div>
        </div>

        <!-- 已加入的项目 -->
        <div class="section-card">
          <div class="section-header">
            <h2 class="section-title">已加入的项目</h2>
            <div class="section-header-right">
              <span class="section-count">{{ profile.projectRoles.length }} 个项目</span>
              <a-button type="text" size="small" @click="openAssignRoleDialog">赋予角色</a-button>
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
                  <a-button
                    v-if="pr.source !== 'group'"
                    type="text"
                    size="mini"
                    class="btn-revoke"
                    title="撤销此角色"
                    @click="revokeProjectRole(pr)"
                  >
                    <template #icon><icon-close :size="12" /></template>
                  </a-button>
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
    <a-modal
      v-model:visible="showRoleDialog"
      :width="420"
      :footer="false"
      @cancel="showRoleDialog = false"
    >
      <template #title>管理全局角色 — {{ profile?.displayName }}</template>
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
    </a-modal>

    <!-- 赋予项目角色弹窗 -->
    <a-modal
      v-model:visible="showAssignProjectRoleDialog"
      title=""
      :width="420"
      :footer="false"
      @cancel="closeAssignDialog"
    >
      <template #title>赋予项目角色 — {{ profile?.displayName }}</template>
      <a-form layout="vertical" size="small">
        <a-form-item label="选择项目">
          <a-select v-model="assignProjectId" placeholder="请选择项目" allow-clear>
            <a-option v-for="p in allProjects" :key="p.id" :value="p.id">
              {{ p.key }} — {{ p.name }}
            </a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="选择角色">
          <a-select v-model="assignRoleId" placeholder="请选择角色" allow-clear>
            <a-option v-for="r in projectRoles" :key="r.id" :value="r.id">
              {{ r.name }}
            </a-option>
          </a-select>
        </a-form-item>
      </a-form>
      <div class="modal-footer">
        <a-button @click="closeAssignDialog">取消</a-button>
        <a-button
          type="primary"
          :disabled="!assignProjectId || !assignRoleId"
          :loading="assignLoading"
          @click="confirmAssignProjectRole"
        >
          赋予角色
        </a-button>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Modal, Message } from '@arco-design/web-vue'
import { userApi, projectApi, roleApi } from '@/api'
import type { UserProfileVO, UserProfileProjectRoleInfo } from '@/api/user'
import { localizeActionShort, fieldLabelMap, localizeLinkType } from '@/utils/fieldLabels'

const route = useRoute()
const router = useRouter()

const profile = ref<UserProfileVO | null>(null)
const loading = ref(true)
const error = ref('')
const exporting = ref(false)
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

// Keycloak 管理后台用户编辑链接
// 格式: {keycloak-url}/admin/{realm}/console/#/{realm}/users/{keycloak-user-id}/settings
const KEYCLOAK_BASE_URL = 'http://localhost:8080'
const KEYCLOAK_REALM = 'trackflow'
const keycloakUserUrl = computed(() => {
  if (!profile.value?.keycloakId) return ''
  return `${KEYCLOAK_BASE_URL}/admin/${KEYCLOAK_REALM}/console/#/${KEYCLOAK_REALM}/users/${profile.value.keycloakId}/settings`
})

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
    const res = await roleApi.list({ roleType: 'global', pageSize: 50 })
    globalRoles.value = res.data?.list || []
  } catch (e) { globalRoles.value = [] }
}

async function toggleRole(roleId: number) {
  if (!profile.value) return
  const roleIdStr = String(roleId)
  const role = globalRoles.value.find((r: any) => String(r.id) === roleIdStr)
  const roleName = role?.name || '角色'
  try {
    if (currentRoleIds.value.includes(roleIdStr)) {
      await userApi.removeRole(userId.value, roleIdStr)
      Message.success(`已移除全局角色「${roleName}」`)
    } else {
      await userApi.assignRole(userId.value, roleIdStr)
      Message.success(`已分配全局角色「${roleName}」`)
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

async function handleExportUserData() {
  if (!profile.value) return
  exporting.value = true
  try {
    const res = await userApi.exportData(userId.value)
    const data = res.data
    // 触发浏览器下载 JSON 文件
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `user-data-${profile.value.username}-${new Date().toISOString().split('T')[0]}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    Message.success('用户数据导出成功')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '导出失败')
  } finally {
    exporting.value = false
  }
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

/**
 * 本地化关联活动的值（格式："{linkType} {issueKey}"）
 * 例如："relates_to DE4-1060" → "相关 DE4-1060"
 */
function localizeLinkValue(value: string): string {
  const parts = value.split(' ')
  if (parts.length >= 2) {
    const linkType = parts[0]
    const issueKey = parts.slice(1).join(' ')
    return `${localizeLinkType(linkType)} ${issueKey}`
  }
  return value
}

function formatAction(activity: { action: string; fieldName?: string; oldValue?: string; newValue?: string }) {
  // 特殊 action：移动到项目
  if (activity.action === 'moved_to_project') {
    return activity.newValue ? `移动到项目 ${activity.newValue}` : '移动到其他项目'
  }
  // 特殊 action：关联操作（本地化关联类型）
  if (activity.action === 'link_added') {
    const value = activity.newValue ? localizeLinkValue(activity.newValue) : ''
    return value ? `添加了关联 ${value}` : '添加了关联'
  }
  if (activity.action === 'link_removed') {
    const value = activity.oldValue ? localizeLinkValue(activity.oldValue) : ''
    return value ? `移除了关联 ${value}` : '移除了关联'
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
    const res = await roleApi.list({ roleType: 'project', pageSize: 50 })
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
          const res = await projectApi.updateMemberRole(pr.projectId, userId.value, remainingRoleIds)
          const affectedCount = res.data?.affectedIssueCount || 0
          if (affectedCount > 0) {
            Message.success(`角色已撤销，已清空 ${affectedCount} 个工单的负责人`)
          } else {
            Message.success('角色已撤销')
          }
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

.error-state {
  text-align: center;
  padding: 48px 0;
  color: var(--text-secondary);
}
.error-icon { font-size: 32px; margin-bottom: 12px; }
.error-state h3 { color: var(--text-primary); margin-bottom: 8px; font-size: 16px; }
.error-state p { margin-bottom: 16px; font-size: var(--font-size-sm); }
.error-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}

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

/* Actions section */
.actions-section {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

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
  display: none !important;
}
.project-role-item:hover .btn-revoke {
  display: inline-flex !important;
}
.btn-revoke:hover {
  color: var(--accent-red) !important;
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

/* 角色管理弹窗内容 */
.role-list {
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

/* 弹窗 footer（赋予项目角色） */
.modal-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 16px;
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

/* Keycloak hint section */
.keycloak-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: rgba(88, 166, 255, 0.06);
  border: 1px solid rgba(88, 166, 255, 0.15);
  border-radius: var(--radius-md);
  margin-bottom: 12px;
}
.keycloak-hint-icon {
  color: var(--accent-blue);
  flex-shrink: 0;
  display: flex;
  align-items: center;
}
.keycloak-hint-text {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  flex: 1;
}
.keycloak-link {
  font-size: var(--font-size-xs);
  color: var(--accent-blue);
  text-decoration: none;
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  transition: opacity 150ms;
}
.keycloak-link:hover {
  opacity: 0.8;
  text-decoration: underline;
}
.external-icon {
  flex-shrink: 0;
}
</style>
