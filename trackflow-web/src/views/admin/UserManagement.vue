<template>
  <div class="admin-page">
    <div class="page-header">
      <h2 class="page-title">用户管理</h2>
      <div class="header-actions">
        <div class="header-filters">
          <input v-model="filters.keyword" class="filter-input" placeholder="搜索用户名/姓名/邮箱..." @input="debounceLoad" />
          <select v-model="filters.roleId" class="filter-select" @change="loadUsers">
            <option value="">全部角色</option>
            <option v-for="role in globalRoles" :key="role.id" :value="role.id">{{ role.name }}</option>
          </select>
          <select v-model="filters.status" class="filter-select" @change="loadUsers">
            <option value="">全部状态</option>
            <option value="active">启用</option>
            <option value="disabled">禁用</option>
          </select>
          <select v-model="filters.banStatus" class="filter-select" @change="loadUsers">
            <option value="">全部禁用类型</option>
            <option value="banned">封禁</option>
            <option value="suspended">暂停</option>
            <option value="inactive">不活跃</option>
            <option value="deactivated">注销</option>
            <option value="locked">锁定</option>
          </select>
        </div>
        <button class="btn-primary" @click="showCreateDialog = true">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor" style="margin-right: 4px">
            <path d="M8 1a.75.75 0 01.75.75v5.5h5.5a.75.75 0 010 1.5h-5.5v5.5a.75.75 0 01-1.5 0v-5.5h-5.5a.75.75 0 010-1.5h5.5v-5.5A.75.75 0 018 1z"/>
          </svg>
          新建用户
        </button>
      </div>
    </div>

    <!-- 用户列表 -->
    <div class="data-table">
      <div class="table-header">
        <div class="col" style="width:240px">
          <span class="col-sortable" :class="{ active: sortField === 'displayName' }" @click="toggleSort('displayName')">
            用户
            <svg v-if="sortField === 'displayName'" class="sort-icon" :class="{ desc: sortDesc }" width="10" height="10" viewBox="0 0 10 10"><path d="M5 2L8 6H2L5 2Z" fill="currentColor"/></svg>
          </span>
        </div>
        <div class="col" style="flex:1">邮箱</div>
        <div class="col" style="width:140px">系统角色</div>
        <div class="col" style="width:80px">状态</div>
        <div class="col" style="width:150px">
          <span class="col-sortable" :class="{ active: sortField === 'lastLoginAt' }" @click="toggleSort('lastLoginAt')">
            最近登录
            <svg v-if="sortField === 'lastLoginAt'" class="sort-icon" :class="{ desc: sortDesc }" width="10" height="10" viewBox="0 0 10 10"><path d="M5 2L8 6H2L5 2Z" fill="currentColor"/></svg>
          </span>
        </div>
        <div class="col" style="width:120px">操作</div>
      </div>
      <div class="table-body">
        <div v-for="user in users" :key="user.id" class="table-row clickable-row" @click="navigateToUser(user, $event)">
          <div class="col user-col" style="width:240px">
            <span class="user-avatar" :style="{ background: getAvatarColor(user.displayName || user.username) }">
              {{ getInitial(user.displayName || user.username) }}
            </span>
            <div class="user-info">
              <router-link :to="`/admin/users/${user.id}`" class="username-link">{{ user.displayName || user.username }}</router-link>
              <span class="user-login">{{ user.username }}</span>
            </div>
          </div>
          <div class="col" style="flex:1">{{ user.email || '—' }}</div>
          <div class="col" style="width:140px">
            <template v-if="user.globalRoles && user.globalRoles.length > 0">
              <span v-for="role in user.globalRoles" :key="role.id" class="role-badge">{{ role.name }}</span>
            </template>
            <span v-else class="text-muted">—</span>
          </div>
          <div class="col" style="width:80px">
            <span class="status-tag" :class="user.status">{{ user.status === 'active' ? '启用' : getBanStatusLabel(user.banStatus) }}</span>
          </div>
          <div class="col" style="width:150px">
            <span class="time-text">{{ formatDate(user.lastLoginAt) }}</span>
          </div>
          <div class="col" style="width:120px">
            <button v-if="user.status === 'active' && user.id !== currentUserId" class="btn-sm danger" @click="disableUser(user)">禁用</button>
            <button v-else-if="user.status !== 'active'" class="btn-sm" @click="enableUser(user)">启用</button>
            <button class="btn-sm" @click="openRoleDialog(user)">角色</button>
          </div>
        </div>
        <div v-if="users.length === 0 && !loading" class="empty-state">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4-4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <path d="M22 21v-2a4 4 0 00-3-3.87"/>
            <path d="M16 3.13a4 4 0 010 7.75"/>
          </svg>
          <h3>暂无用户</h3>
          <p>点击"新建用户"按钮添加第一个用户</p>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div class="pagination" v-if="total > 0">
      <span>共 {{ total }} 条</span>
      <div class="page-btns">
        <button class="btn-page" :disabled="page <= 1" @click="page--; loadUsers()">‹</button>
        <span>{{ page }} / {{ totalPages }}</span>
        <button class="btn-page" :disabled="page >= totalPages" @click="page++; loadUsers()">›</button>
      </div>
    </div>

    <!-- 新建用户弹窗 -->
    <div class="modal-overlay" v-if="showCreateDialog" @click.self="showCreateDialog = false">
      <div class="modal-md">
        <div class="modal-header">
          <h3>新建用户</h3>
          <button class="btn-close" @click="closeCreateDialog">×</button>
        </div>
        <div class="modal-body">
          <form @submit.prevent="handleCreateUser">
            <div class="form-group">
              <label class="form-label">显示名称 <span class="required">*</span></label>
              <input
                v-model="createForm.displayName"
                class="form-input"
                placeholder="例如：张伟、John Smith"
                autocomplete="off"
              />
              <span class="form-hint">用户的全名，将显示在系统各处</span>
            </div>
            <div class="form-group">
              <label class="form-label">用户名 <span class="required">*</span></label>
              <input
                v-model="createForm.username"
                class="form-input"
                placeholder="字母开头，只含字母/数字/下划线/连字符"
                autocomplete="off"
              />
              <span class="form-hint">登录凭据，创建后不可修改</span>
            </div>
            <div class="form-group">
              <label class="form-label">邮箱 <span class="required">*</span></label>
              <input
                v-model="createForm.email"
                class="form-input"
                type="email"
                placeholder="user@company.com"
                autocomplete="off"
              />
            </div>
            <div class="form-group">
              <label class="form-label">临时密码 <span class="required">*</span></label>
              <input
                v-model="createForm.password"
                class="form-input"
                type="password"
                placeholder="至少 6 个字符，首次登录需修改"
                autocomplete="new-password"
              />
              <span class="form-hint">用户首次登录时将被要求修改密码</span>
            </div>
            <div v-if="createError" class="form-error">
              <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor">
                <path d="M8 1.5a6.5 6.5 0 100 13 6.5 6.5 0 000-13zM0 8a8 8 0 1116 0A8 8 0 010 8zm9-3a1 1 0 11-2 0 1 1 0 012 0zM7 7.75a.75.75 0 011.5 0v3.5a.75.75 0 01-1.5 0v-3.5z"/>
              </svg>
              {{ createError }}
            </div>
            <div class="modal-footer">
              <button type="button" class="btn-secondary" @click="closeCreateDialog">取消</button>
              <button type="submit" class="btn-primary" :disabled="creating">
                {{ creating ? '创建中...' : '创建用户' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>

    <!-- 角色管理弹窗 -->
    <div class="modal-overlay" v-if="showRoleDialog" @click.self="showRoleDialog = false">
      <div class="modal-role-panel">
        <div class="modal-header">
          <h3>管理角色 — {{ selectedUser?.displayName }}</h3>
          <button class="btn-close" @click="showRoleDialog = false">×</button>
        </div>
        <div class="modal-body">
          <!-- 加载中 -->
          <div v-if="roleLoading" class="role-loading">
            <div class="loading-spinner"></div>
            <span>加载角色信息...</span>
          </div>

          <template v-else>
            <!-- 系统角色区域 -->
            <div class="role-section">
              <div class="role-section-header">
                <h4 class="role-section-title">系统角色</h4>
                <span class="role-section-hint">赋予用户系统级管理权限</span>
              </div>
              <div class="role-list">
                <div v-for="role in globalRoles" :key="role.id" class="role-item">
                  <label class="role-check">
                    <input 
                      type="checkbox" 
                      :checked="userRoleIds.includes(String(role.id))" 
                      :disabled="savingRoles"
                      @change="toggleRole(role.id)" 
                    />
                    <span class="role-name">{{ role.name }}</span>
                    <span class="role-code">{{ role.code }}</span>
                  </label>
                </div>
              </div>
              <!-- 批量保存按钮 -->
              <div v-if="hasRoleChanges" class="role-save-bar">
                <span class="role-save-hint">系统角色已修改，请保存</span>
                <button class="btn-sm-action primary" :disabled="savingRoles" @click="saveRoles">
                  {{ savingRoles ? '保存中...' : '保存角色' }}
                </button>
              </div>
            </div>

            <!-- 自动分配的项目角色区域 -->
            <div class="role-section">
              <div class="role-section-header">
                <h4 class="role-section-title">自动分配的项目角色</h4>
                <span class="role-section-hint">用户将以此角色自动加入所有项目（包括新建项目）</span>
              </div>

              <div class="global-project-role-list">
                <div v-for="gm in userGlobalMembers" :key="gm.id" class="global-project-role-item">
                  <div class="global-project-role-info">
                    <span class="global-tag">自动</span>
                    <span class="global-project-role-name">{{ gm.roleName }}</span>
                    <span class="global-project-role-code">{{ gm.roleCode }}</span>
                  </div>
                  <button class="btn-icon-sm danger" title="撤销自动分配" @click="revokeGlobalMember(gm)">
                    <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor">
                      <path d="M3.72 3.72a.75.75 0 011.06 0L8 6.94l3.22-3.22a.75.75 0 111.06 1.06L9.06 8l3.22 3.22a.75.75 0 11-1.06 1.06L8 9.06l-3.22 3.22a.75.75 0 01-1.06-1.06L6.94 8 3.72 4.78a.75.75 0 010-1.06z"/>
                    </svg>
                  </button>
                </div>
                <div v-if="userGlobalMembers.length === 0" class="role-empty-inline">
                  未设置自动分配角色
                </div>
              </div>

              <!-- 添加自动分配的项目角色 -->
              <div class="add-global-member-section">
                <button v-if="!showAddGlobalMember" class="btn-text-sm" @click="showAddGlobalMember = true">
                  <svg width="12" height="12" viewBox="0 0 16 16" fill="currentColor" style="margin-right: 4px">
                    <path d="M8 1a.75.75 0 01.75.75v5.5h5.5a.75.75 0 010 1.5h-5.5v5.5a.75.75 0 01-1.5 0v-5.5h-5.5a.75.75 0 010-1.5h5.5v-5.5A.75.75 0 018 1z"/>
                  </svg>
                  添加自动分配角色
                </button>
                <div v-else class="add-global-member-form">
                  <select v-model="addGlobalRoleId" class="add-project-select">
                    <option value="">选择角色...</option>
                    <option v-for="r in availableGlobalProjectRoles" :key="r.id" :value="r.id">{{ r.name }}</option>
                  </select>
                  <button class="btn-sm-action" :disabled="!addGlobalRoleId" @click="assignGlobalMember">确认</button>
                  <button class="btn-sm-action secondary" @click="showAddGlobalMember = false; addGlobalRoleId = ''">取消</button>
                </div>
              </div>
            </div>

            <!-- 已加入的项目区域 -->
            <div class="role-section">
              <div class="role-section-header">
                <h4 class="role-section-title">已加入的项目</h4>
                <span class="role-section-count">{{ userProjectRoles.length }} 个项目</span>
              </div>
              <div class="role-section-desc">用户在各个具体项目中的成员角色</div>

              <div v-if="userProjectRoles.length === 0" class="role-empty">
                <span class="role-empty-icon">📁</span>
                <span>未加入任何项目</span>
              </div>

              <div v-else class="project-role-list">
                <div v-for="pr in userProjectRoles" :key="`${pr.projectId}-${pr.roleCode}`" class="project-role-item">
                  <div class="project-role-info">
                    <span class="project-role-key">{{ pr.projectKey }}</span>
                    <span class="project-role-name">{{ pr.projectName }}</span>
                    <span v-if="pr.source === 'group'" class="source-tag group" :title="pr.groupName ? `来源: ${pr.groupName}` : '通过用户组继承'">
                      组继承
                    </span>
                  </div>
                  <div class="project-role-actions">
                    <select
                      class="project-role-select"
                      :value="pr.roleCode"
                      :disabled="pr.source === 'group'"
                      :title="pr.source === 'group' ? '通过用户组继承的角色不可直接修改' : ''"
                      @change="changeProjectRole(pr, ($event.target as HTMLSelectElement).value)"
                    >
                      <option v-for="r in projectRoles" :key="r.id" :value="r.code">{{ r.name }}</option>
                    </select>
                    <button
                      v-if="pr.source !== 'group'"
                      class="btn-icon-sm danger"
                      title="移除成员"
                      @click="removeFromProject(pr)"
                    >
                      <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor">
                        <path d="M3.72 3.72a.75.75 0 011.06 0L8 6.94l3.22-3.22a.75.75 0 111.06 1.06L9.06 8l3.22 3.22a.75.75 0 11-1.06 1.06L8 9.06l-3.22 3.22a.75.75 0 01-1.06-1.06L6.94 8 3.72 4.78a.75.75 0 010-1.06z"/>
                      </svg>
                    </button>
                    <span v-else class="btn-icon-sm disabled" title="通过用户组继承的角色不可直接移除">
                      <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor" style="opacity: 0.3">
                        <path d="M8 0a8 8 0 100 16A8 8 0 008 0zm0 14.5a6.5 6.5 0 110-13 6.5 6.5 0 010 13z"/>
                      </svg>
                    </span>
                  </div>
                </div>
              </div>

              <!-- 添加到项目 -->
              <div class="add-project-section">
                <button v-if="!showAddProject" class="btn-text-sm" @click="showAddProject = true">
                  <svg width="12" height="12" viewBox="0 0 16 16" fill="currentColor" style="margin-right: 4px">
                    <path d="M8 1a.75.75 0 01.75.75v5.5h5.5a.75.75 0 010 1.5h-5.5v5.5a.75.75 0 01-1.5 0v-5.5h-5.5a.75.75 0 010-1.5h5.5v-5.5A.75.75 0 018 1z"/>
                  </svg>
                  添加到项目
                </button>
                <div v-else class="add-project-form">
                  <select v-model="addProjectId" class="add-project-select">
                    <option value="">选择项目...</option>
                    <option v-for="p in availableProjects" :key="p.id" :value="p.id">
                      {{ p.key }} — {{ p.name }}
                    </option>
                  </select>
                  <select v-model="addProjectRoleId" class="add-project-select">
                    <option value="">选择角色...</option>
                    <option v-for="r in projectRoles" :key="r.id" :value="r.id">{{ r.name }}</option>
                  </select>
                  <button class="btn-sm-action" :disabled="!addProjectId || !addProjectRoleId" @click="addToProject">确认</button>
                  <button class="btn-sm-action secondary" @click="cancelAddProject">取消</button>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import { Modal, Message } from '@arco-design/web-vue'
import { userApi, projectApi, globalMemberApi, roleApi } from '@/api'
import type { UserProfileProjectRoleInfo } from '@/api/user'
import type { GlobalMemberVO } from '@/api/globalMember'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()
const currentUserId = computed(() => authStore.user?.userId)

const users = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const totalPages = computed(() => Math.ceil(total.value / pageSize))
const loading = ref(false)

const filters = reactive({ keyword: '', roleId: '', status: '', banStatus: '' })

// 排序状态
const sortField = ref('createdAt')
const sortDesc = ref(true)

function toggleSort(field: string) {
  if (sortField.value === field) {
    sortDesc.value = !sortDesc.value
  } else {
    sortField.value = field
    sortDesc.value = true
  }
  page.value = 1
  loadUsers()
}

/** 点击行跳转用户详情（排除按钮和链接的点击） */
function navigateToUser(user: any, event: MouseEvent) {
  const target = event.target as HTMLElement
  // 排除点击按钮、链接、下拉框的情况，这些元素有自己的交互行为
  if (target.closest('button') || target.closest('a') || target.closest('select')) {
    return
  }
  router.push(`/admin/users/${user.id}`)
}

// 创建用户
const showCreateDialog = ref(false)
const creating = ref(false)
const createError = ref('')
const createForm = reactive({
  username: '',
  email: '',
  displayName: '',
  password: ''
})

// 角色管理
const showRoleDialog = ref(false)
const roleLoading = ref(false)
const selectedUser = ref<any>(null)
const userRoleIds = ref<string[]>([])
const originalRoleIds = ref<string[]>([])  // 记录初始角色状态，用于批量保存
const userProjectRoles = ref<UserProfileProjectRoleInfo[]>([])
const globalRoles = ref<any[]>([])
const projectRoles = ref<any[]>([])
const allProjects = ref<any[]>([])

// 角色操作 loading 状态（存储正在处理的角色 ID）
const roleToggleLoading = ref<Set<string>>(new Set())

// 批量保存角色 loading 状态
const savingRoles = ref(false)

// 检查系统角色是否有变更
const hasRoleChanges = computed(() => {
  if (originalRoleIds.value.length !== userRoleIds.value.length) return true
  const sortedOriginal = [...originalRoleIds.value].sort()
  const sortedCurrent = [...userRoleIds.value].sort()
  return sortedOriginal.some((id, i) => id !== sortedCurrent[i])
})

// 添加到项目
const showAddProject = ref(false)
const addProjectId = ref('')
const addProjectRoleId = ref('')

// 全局项目角色
const userGlobalMembers = ref<GlobalMemberVO[]>([])
const showAddGlobalMember = ref(false)
const addGlobalRoleId = ref('')

/** 可添加的全局项目角色（排除已分配的） */
const availableGlobalProjectRoles = computed(() => {
  const assignedRoleIds = new Set(userGlobalMembers.value.map(gm => gm.roleId))
  return projectRoles.value.filter((r: any) => !assignedRoleIds.has(String(r.id)))
})

/** 可添加的项目（排除用户已加入的） */
const availableProjects = computed(() => {
  const joinedProjectIds = new Set(userProjectRoles.value.map(pr => pr.projectId))
  return allProjects.value.filter(p => !joinedProjectIds.has(String(p.id)) && p.status === 'active')
})

let debounceTimer: any = null
function debounceLoad() {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => { page.value = 1; loadUsers() }, 300)
}

async function loadUsers() {
  loading.value = true
  try {
    const params: any = { page: page.value, pageSize }
    if (filters.keyword) params.keyword = filters.keyword
    if (filters.roleId) params.roleId = filters.roleId
    if (filters.status) params.status = filters.status
    if (filters.banStatus) params.banStatus = filters.banStatus
    // 排序参数：-fieldName 降序，fieldName 升序
    if (sortField.value) {
      params.sort = (sortDesc.value ? '-' : '') + sortField.value
    }
    const res = await userApi.list(params)
    users.value = res.data?.list || []
    total.value = res.data?.pagination?.total || 0
  } catch (e) { users.value = []; total.value = 0 }
  finally { loading.value = false }
}

async function disableUser(user: any) {
  // 使用响应式状态来收集表单数据
  const banStatus = ref('banned')
  const banReason = ref('')

  Modal.confirm({
    title: '禁用用户',
    width: 480,
    content: () => h('div', { style: 'padding: 4px 0' }, [
      h('p', { style: 'margin-bottom: 16px; color: var(--tf-text-secondary)' },
        `确定要禁用用户 "${user.displayName}" (${user.username}) 吗？禁用后该用户将无法登录系统。`),
      h('div', { class: 'form-group', style: 'margin-bottom: 16px' }, [
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
      h('div', { class: 'form-group' }, [
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
        await userApi.disable(user.id, {
          banStatus: banStatus.value,
          banReason: banReason.value || undefined
        })
        Message.success('用户已禁用')
        loadUsers()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '禁用失败')
      }
    }
  })
}

async function enableUser(user: any) {
  Modal.confirm({
    title: '确认启用用户',
    content: `确定要启用用户 "${user.displayName}" (${user.username}) 吗？`,
    okText: '启用用户',
    cancelText: '取消',
    async onOk() {
      try {
        await userApi.enable(user.id)
        Message.success('用户已启用')
        loadUsers()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '启用失败')
      }
    }
  })
}

function closeCreateDialog() {
  showCreateDialog.value = false
  createError.value = ''
  createForm.username = ''
  createForm.email = ''
  createForm.displayName = ''
  createForm.password = ''
}

async function handleCreateUser() {
  createError.value = ''

  // 前端校验
  if (!createForm.displayName.trim()) {
    createError.value = '请输入显示名称'
    return
  }
  if (!createForm.username.trim()) {
    createError.value = '请输入用户名'
    return
  }
  if (!/^[a-zA-Z][a-zA-Z0-9_-]*$/.test(createForm.username)) {
    createError.value = '用户名必须以字母开头，只能包含字母、数字、下划线和连字符'
    return
  }
  if (createForm.username.length < 3) {
    createError.value = '用户名长度至少 3 个字符'
    return
  }
  if (!createForm.email.trim()) {
    createError.value = '请输入邮箱'
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(createForm.email)) {
    createError.value = '邮箱格式无效'
    return
  }
  if (!createForm.password) {
    createError.value = '请输入临时密码'
    return
  }
  if (createForm.password.length < 6) {
    createError.value = '密码长度至少 6 个字符'
    return
  }

  creating.value = true
  try {
    await userApi.create({
      username: createForm.username.trim(),
      email: createForm.email.trim(),
      displayName: createForm.displayName.trim(),
      password: createForm.password
    })
    closeCreateDialog()
    loadUsers()
  } catch (e: any) {
    createError.value = e.response?.data?.message || '创建用户失败，请重试'
  } finally {
    creating.value = false
  }
}

async function openRoleDialog(user: any) {
  selectedUser.value = user
  showRoleDialog.value = true
  roleLoading.value = true
  showAddProject.value = false
  addProjectId.value = ''
  addProjectRoleId.value = ''
  showAddGlobalMember.value = false
  addGlobalRoleId.value = ''
  // 清理角色操作 loading 状态
  roleToggleLoading.value.clear()
  savingRoles.value = false

  try {
    const [profileRes, globalMembersRes] = await Promise.all([
      userApi.getProfile(user.id),
      globalMemberApi.listByUser(user.id)
    ])
    const profile = profileRes.data
    const roleIds = profile?.globalRoles?.map((r: any) => r.id) || []
    userRoleIds.value = [...roleIds]
    originalRoleIds.value = [...roleIds]  // 记录初始状态
    userProjectRoles.value = profile?.projectRoles || []
    userGlobalMembers.value = globalMembersRes.data || []
  } catch (e) {
    userRoleIds.value = []
    originalRoleIds.value = []
    userProjectRoles.value = []
    userGlobalMembers.value = []
  } finally {
    roleLoading.value = false
  }
}

async function toggleRole(roleId: number) {
  const roleIdStr = String(roleId)
  
  // 本地切换角色选中状态（不调用 API）
  if (userRoleIds.value.includes(roleIdStr)) {
    userRoleIds.value = userRoleIds.value.filter(id => id !== roleIdStr)
  } else {
    userRoleIds.value.push(roleIdStr)
  }
}

/** 批量保存系统角色 */
async function saveRoles() {
  const userId = selectedUser.value?.id
  if (!userId) return
  
  savingRoles.value = true
  try {
    await userApi.replaceRoles(userId, userRoleIds.value)
    
    // 更新初始状态为当前状态
    originalRoleIds.value = [...userRoleIds.value]
    
    // 同步更新用户列表显示
    syncUserListGlobalRoles(userId)
    
    Message.success('系统角色已保存')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存失败')
  } finally {
    savingRoles.value = false
  }
}

/** 将面板中的 userRoleIds 同步到用户列表中对应用户的 globalRoles 字段 */
function syncUserListGlobalRoles(userId: string) {
  const userInList = users.value.find((u: any) => String(u.id) === String(userId))
  if (!userInList) return
  // 根据当前 userRoleIds 构建完整的 globalRoles 数组
  userInList.globalRoles = userRoleIds.value
    .map(rid => globalRoles.value.find((r: any) => String(r.id) === rid))
    .filter(Boolean)
    .map((r: any) => ({ id: r.id, name: r.name, code: r.code }))
}

async function changeProjectRole(pr: UserProfileProjectRoleInfo, newRoleCode: string) {
  const userId = selectedUser.value?.id
  if (!userId) return
  const newRole = projectRoles.value.find(r => r.code === newRoleCode)
  if (!newRole) return

  try {
    const res = await projectApi.updateMemberRole(pr.projectId, userId, [Number(newRole.id)])
    const affectedCount = res.data?.affectedIssueCount || 0
    // 刷新项目角色数据
    pr.roleName = newRole.name
    pr.roleCode = newRole.code
    const userName = selectedUser.value?.displayName || selectedUser.value?.username
    if (affectedCount > 0) {
      Message.success(`角色已更新：${userName} 在 ${pr.projectName} 的角色已变更为「${newRole.name}」，已清空 ${affectedCount} 个工单的负责人`)
    } else {
      Message.success(`角色已更新：${userName} 在 ${pr.projectName} 的角色已变更为「${newRole.name}」`)
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '角色更新失败')
    // 重新加载以回滚UI
    await refreshProjectRoles()
  }
}

async function removeFromProject(pr: UserProfileProjectRoleInfo) {
  const userId = selectedUser.value?.id
  if (!userId) return

  Modal.confirm({
    title: '确认移除成员',
    content: `确定将该用户从项目"${pr.projectName}"中移除？`,
    okText: '移除',
    cancelText: '取消',
    okButtonProps: { status: 'danger' },
    async onOk() {
      try {
        await projectApi.removeMember(pr.projectId, userId)
        userProjectRoles.value = userProjectRoles.value.filter(
          r => !(r.projectId === pr.projectId && r.roleCode === pr.roleCode)
        )
        Message.success('已从项目中移除')
      } catch (e: any) {
        Message.error(e.response?.data?.message || '移除失败')
      }
    }
  })
}

async function addToProject() {
  const userId = selectedUser.value?.id
  if (!userId || !addProjectId.value || !addProjectRoleId.value) return

  try {
    await projectApi.addMember(addProjectId.value, {
      userId,
      roleIds: [Number(addProjectRoleId.value)]
    })
    const project = allProjects.value.find(p => String(p.id) === addProjectId.value)
    const role = projectRoles.value.find((r: any) => String(r.id) === addProjectRoleId.value)
    Message.success(`已添加到项目「${project?.name || ''}」，角色：${role?.name || ''}`)
    // 刷新项目角色
    await refreshProjectRoles()
    cancelAddProject()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '添加到项目失败')
  }
}

function cancelAddProject() {
  showAddProject.value = false
  addProjectId.value = ''
  addProjectRoleId.value = ''
}

async function assignGlobalMember() {
  const userId = selectedUser.value?.id
  if (!userId || !addGlobalRoleId.value) return

  try {
    await globalMemberApi.assign({ userId, roleId: addGlobalRoleId.value })
    Message.success('全局项目角色已分配')
    // 刷新全局成员列表
    const res = await globalMemberApi.listByUser(userId)
    userGlobalMembers.value = res.data || []
    showAddGlobalMember.value = false
    addGlobalRoleId.value = ''
    // 同时刷新项目角色（因为已同步到所有项目）
    await refreshProjectRoles()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '分配失败')
  }
}

async function revokeGlobalMember(gm: GlobalMemberVO) {
  const userId = selectedUser.value?.id
  if (!userId) return

  Modal.confirm({
    title: '撤销全局项目角色',
    content: `确定撤销该用户的全局「${gm.roleName}」角色吗？该用户将从所有项目中移除此角色。`,
    okText: '撤销',
    cancelText: '取消',
    okButtonProps: { status: 'danger' },
    async onOk() {
      try {
        await globalMemberApi.revoke(userId, gm.roleId)
        Message.success('全局项目角色已撤销')
        userGlobalMembers.value = userGlobalMembers.value.filter(m => m.id !== gm.id)
        // 同时刷新项目角色
        await refreshProjectRoles()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '撤销失败')
      }
    }
  })
}

async function refreshProjectRoles() {
  const userId = selectedUser.value?.id
  if (!userId) return
  try {
    const res = await userApi.getProfile(userId)
    userProjectRoles.value = res.data?.projectRoles || []
  } catch (e) { /* keep current */ }
}

async function loadGlobalRoles() {
  try {
    const res = await roleApi.list({ roleType: 'global', pageSize: 50 })
    globalRoles.value = res.data?.list || []
  } catch (e) { globalRoles.value = [] }
}

async function loadProjectRoles() {
  try {
    const res = await roleApi.list({ roleType: 'project', pageSize: 50 })
    const roles = res.data?.list || []
    // 排除 non_member 和 anonymous 等不可分配的角色
    projectRoles.value = roles.filter((r: any) => !['non_member', 'anonymous'].includes(r.code))
  } catch (e) { projectRoles.value = [] }
}

async function loadAllProjects() {
  try {
    const res = await projectApi.list({ pageSize: 100 })
    allProjects.value = res.data?.list || []
  } catch (e) { allProjects.value = [] }
}

function formatDate(dt: string) {
  if (!dt) return '—'
  return new Date(dt).toLocaleString('zh-CN')
}

/** 禁用状态标签映射 */
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

/** 获取用户名首字母（支持中文取第一个字） */
function getInitial(name: string): string {
  if (!name) return '?'
  const first = name.trim().charAt(0)
  return first.toUpperCase()
}

/** 根据名称生成稳定的头像背景色 */
function getAvatarColor(name: string): string {
  const colors = [
    '#4a9af5', '#7c5cbf', '#e06c75', '#e5a64e',
    '#56b6c2', '#98c379', '#c678dd', '#61afef',
    '#d19a66', '#be5046'
  ]
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash)
  }
  return colors[Math.abs(hash) % colors.length]
}

onMounted(() => {
  loadUsers()
  loadGlobalRoles()
  loadProjectRoles()
  loadAllProjects()
})
</script>

<style scoped>
.admin-page { padding: 24px; height: 100%; overflow-y: auto; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.page-title { font-size: 18px; font-weight: 600; color: var(--text-bright); }
.header-actions { display: flex; align-items: center; gap: 12px; }
.header-filters { display: flex; gap: 8px; }
.filter-input { height: 32px; width: 260px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 0 10px; color: var(--text-primary); font-size: var(--font-size-sm); outline: none; }
.filter-input:focus { border-color: var(--accent-blue); }
.filter-select { height: 32px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 0 10px; color: var(--text-primary); font-size: var(--font-size-sm); }

.btn-primary { display: flex; align-items: center; height: 32px; padding: 0 14px; background: var(--accent-blue); border: none; border-radius: var(--radius-md); color: #fff; font-size: var(--font-size-sm); font-weight: 500; cursor: pointer; white-space: nowrap; transition: background 150ms; }
.btn-primary:hover { background: var(--accent-blue-hover, #4a9af5); }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-secondary { height: 32px; padding: 0 14px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-md); color: var(--text-primary); font-size: var(--font-size-sm); cursor: pointer; transition: background 150ms; }
.btn-secondary:hover { background: var(--bg-hover); }

.data-table { border: 1px solid var(--border-color); border-radius: 6px; overflow: hidden; }
.table-header { display: flex; padding: 8px 12px; background: var(--bg-tertiary); border-bottom: 1px solid var(--border-color); font-size: var(--font-size-xs); color: var(--text-secondary); text-transform: uppercase; }
.table-row { display: flex; padding: 10px 12px; border-bottom: 1px solid var(--border-light); align-items: center; }
.table-row:hover { background: var(--bg-hover); }
.table-row:last-child { border-bottom: none; }
.table-row.clickable-row { cursor: pointer; transition: background 150ms ease; }
.table-row.clickable-row:hover { background: var(--bg-hover); }
.table-row.clickable-row:active { background: var(--bg-active, var(--bg-hover)); }
.col { padding: 0 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: var(--font-size-sm); }

.username-link { color: var(--accent-blue); font-weight: 500; text-decoration: none; }
.username-link:hover { text-decoration: underline; }

/* User column with avatar */
.user-col { display: flex; align-items: center; gap: 10px; }
.user-avatar { width: 28px; height: 28px; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 12px; font-weight: 600; flex-shrink: 0; }
.user-info { display: flex; flex-direction: column; min-width: 0; }
.user-info .username-link { font-size: var(--font-size-sm); line-height: 1.3; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-login { font-size: 11px; color: var(--text-muted); line-height: 1.2; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.status-tag { font-size: var(--font-size-xs); padding: 2px 8px; border-radius: var(--radius-sm); }
.status-tag.active { background: rgba(76,175,80,0.15); color: var(--accent-green); }
.status-tag.disabled { background: rgba(244,67,54,0.15); color: var(--accent-red); }
.time-text { font-size: var(--font-size-xs); color: var(--text-secondary); }

.btn-sm { height: 24px; padding: 0 8px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-sm); color: var(--text-primary); font-size: var(--font-size-xs); cursor: pointer; margin-right: 4px; }
.btn-sm:hover { background: var(--bg-hover); }
.btn-sm.danger { color: var(--accent-red); border-color: var(--accent-red); }

.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 48px 24px; color: var(--text-muted); }
.empty-state svg { opacity: 0.4; margin-bottom: 12px; }
.empty-state h3 { font-size: 14px; font-weight: 500; color: var(--text-secondary); margin: 0 0 6px; }
.empty-state p { font-size: 13px; color: var(--text-muted); margin: 0; }

.pagination { display: flex; align-items: center; justify-content: space-between; margin-top: 12px; font-size: var(--font-size-sm); color: var(--text-secondary); }
.page-btns { display: flex; align-items: center; gap: 8px; }
.btn-page { width: 28px; height: 28px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-md); color: var(--text-primary); cursor: pointer; }
.btn-page:disabled { opacity: 0.3; cursor: not-allowed; }

/* Modal */
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.modal-sm { width: 420px; background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 8px; }
.modal-md { width: 480px; background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 8px; }
.modal-header { display: flex; align-items: center; justify-content: space-between; padding: 14px 18px; border-bottom: 1px solid var(--border-color); }
.modal-header h3 { font-size: 15px; color: var(--text-bright); font-weight: 500; }
.btn-close { background: none; border: none; color: var(--text-secondary); font-size: 18px; cursor: pointer; padding: 0 4px; }
.btn-close:hover { color: var(--text-primary); }
.modal-body { padding: 16px 18px; }
.modal-footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--border-light); }

/* Form */
.form-group { margin-bottom: 16px; }
.form-label { display: block; font-size: var(--font-size-xs); font-weight: 500; color: var(--text-secondary); margin-bottom: 6px; }
.required { color: var(--accent-red); }
.form-input { width: 100%; height: 36px; background: var(--bg-primary); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 0 10px; color: var(--text-primary); font-size: var(--font-size-sm); outline: none; box-sizing: border-box; transition: border-color 150ms; }
.form-input:focus { border-color: var(--accent-blue); }
.form-hint { display: block; font-size: 11px; color: var(--text-muted); margin-top: 4px; }
.form-error { display: flex; align-items: center; gap: 6px; padding: 8px 12px; background: rgba(244,67,54,0.1); border: 1px solid rgba(244,67,54,0.3); border-radius: var(--radius-md); color: var(--accent-red); font-size: var(--font-size-xs); margin-top: 12px; }

.role-item { margin-bottom: 8px; }
.role-check { display: flex; align-items: center; gap: 8px; cursor: pointer; font-size: var(--font-size-sm); color: var(--text-primary); }
.role-check.loading { cursor: wait; opacity: 0.7; }
.role-check input { accent-color: var(--accent-blue); }
.role-check input:disabled { cursor: wait; }
.checkbox-spinner { width: 14px; height: 14px; border: 2px solid var(--border-color); border-top-color: var(--accent-blue); border-radius: 50%; animation: spin 0.6s linear infinite; flex-shrink: 0; }
.role-code { font-size: var(--font-size-xs); color: var(--text-muted); margin-left: auto; }

/* Role Panel */
.modal-role-panel { width: 520px; background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 8px; max-height: 80vh; display: flex; flex-direction: column; }
.modal-role-panel .modal-body { overflow-y: auto; flex: 1; padding: 0; }

.role-loading { display: flex; align-items: center; gap: 10px; padding: 32px; justify-content: center; color: var(--text-secondary); font-size: var(--font-size-sm); }
.loading-spinner { width: 16px; height: 16px; border: 2px solid var(--border-color); border-top-color: var(--accent-blue); border-radius: 50%; animation: spin 0.6s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.role-section { padding: 16px 18px; }
.role-section + .role-section { border-top: 1px solid var(--border-light); }
.role-section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.role-section-title { font-size: 12px; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.5px; }
.role-section-count { font-size: var(--font-size-xs); color: var(--text-muted); }

.role-empty { display: flex; align-items: center; gap: 8px; color: var(--text-muted); font-size: var(--font-size-sm); padding: 8px 0; }
.role-empty-icon { font-size: 14px; }

/* Project Role List */
.project-role-list { display: flex; flex-direction: column; gap: 4px; }
.project-role-item { display: flex; align-items: center; justify-content: space-between; padding: 6px 8px; border-radius: var(--radius-sm); transition: background 150ms; }
.project-role-item:hover { background: var(--bg-hover); }
.project-role-info { display: flex; align-items: center; gap: 8px; min-width: 0; flex: 1; }
.project-role-key { font-size: var(--font-size-xs); font-weight: 500; color: var(--accent-blue); background: rgba(88,166,255,0.08); padding: 2px 6px; border-radius: var(--radius-sm); flex-shrink: 0; }
.project-role-name { font-size: var(--font-size-sm); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.source-tag { font-size: 10px; font-weight: 500; padding: 2px 6px; border-radius: 3px; flex-shrink: 0; }
.source-tag.group { color: #a371f7; background: rgba(163,113,247,0.12); }
.project-role-actions { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.project-role-select { height: 26px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-sm); padding: 0 6px; color: var(--text-primary); font-size: var(--font-size-xs); cursor: pointer; outline: none; }
.project-role-select:focus { border-color: var(--accent-blue); }
.project-role-select:disabled { opacity: 0.5; cursor: not-allowed; background: var(--bg-secondary); }

.btn-icon-sm { width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; background: none; border: none; border-radius: var(--radius-sm); color: var(--text-muted); cursor: pointer; transition: all 150ms; }
.btn-icon-sm:hover { background: var(--bg-hover); color: var(--text-primary); }
.btn-icon-sm.danger:hover { background: rgba(244,67,54,0.1); color: var(--accent-red); }
.btn-icon-sm.disabled { cursor: not-allowed; opacity: 0.4; }

/* Add to Project */
.add-project-section { margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--border-light); }
.btn-text-sm { display: flex; align-items: center; background: none; border: none; color: var(--accent-blue); font-size: var(--font-size-xs); cursor: pointer; padding: 4px 0; transition: opacity 150ms; }
.btn-text-sm:hover { opacity: 0.8; }
.add-project-form { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.add-project-select { height: 28px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-sm); padding: 0 8px; color: var(--text-primary); font-size: var(--font-size-xs); flex: 1; min-width: 120px; outline: none; }
.add-project-select:focus { border-color: var(--accent-blue); }
.btn-sm-action { height: 28px; padding: 0 10px; background: var(--accent-blue); border: none; border-radius: var(--radius-sm); color: #fff; font-size: var(--font-size-xs); cursor: pointer; transition: opacity 150ms; white-space: nowrap; }
.btn-sm-action:hover { opacity: 0.9; }
.btn-sm-action:disabled { opacity: 0.4; cursor: not-allowed; }
.btn-sm-action.secondary { background: var(--bg-tertiary); border: 1px solid var(--border-color); color: var(--text-primary); }
.btn-sm-action.secondary:hover { background: var(--bg-hover); }

/* Role badges */
.role-badge { display: inline-flex; align-items: center; height: 20px; padding: 0 7px; background: rgba(88,166,255,0.12); color: var(--accent-blue); font-size: 11px; font-weight: 500; border-radius: 3px; white-space: nowrap; margin-right: 4px; }
.text-muted { color: var(--text-muted); font-size: var(--font-size-sm); }

/* Global Project Role Section */
.role-section-hint { font-size: 11px; color: var(--text-muted); font-style: italic; }
.role-section-desc { font-size: 11px; color: var(--text-muted); margin-top: -8px; margin-bottom: 8px; }

/* Role Save Bar */
.role-save-bar { display: flex; align-items: center; justify-content: space-between; margin-top: 12px; padding: 10px 12px; background: rgba(88,166,255,0.08); border-radius: var(--radius-sm); }
.role-save-hint { font-size: 12px; color: var(--accent-blue); }
.btn-sm-action.primary { background: var(--accent-blue); color: #fff; font-weight: 500; }
.btn-sm-action.primary:hover { opacity: 0.9; }
.btn-sm-action.primary:disabled { opacity: 0.4; cursor: not-allowed; }

.global-project-role-list { display: flex; flex-direction: column; gap: 4px; }
.global-project-role-item { display: flex; align-items: center; justify-content: space-between; padding: 6px 8px; border-radius: var(--radius-sm); transition: background 150ms; }
.global-project-role-item:hover { background: var(--bg-hover); }
.global-project-role-info { display: flex; align-items: center; gap: 8px; }
.global-tag { font-size: 10px; font-weight: 600; color: #3fb950; background: rgba(63,185,80,0.1); padding: 2px 6px; border-radius: 3px; text-transform: uppercase; letter-spacing: 0.3px; }
.global-project-role-name { font-size: var(--font-size-sm); color: var(--text-primary); font-weight: 500; }
.global-project-role-code { font-size: var(--font-size-xs); color: var(--text-muted); }
.role-empty-inline { font-size: var(--font-size-xs); color: var(--text-muted); padding: 4px 0; }
.add-global-member-section { margin-top: 10px; padding-top: 10px; border-top: 1px solid var(--border-light); }
.add-global-member-form { display: flex; align-items: center; gap: 8px; }

/* Sortable columns */
.col-sortable { display: inline-flex; align-items: center; gap: 3px; cursor: pointer; user-select: none; transition: color 150ms; }
.col-sortable:hover { color: var(--text-primary); }
.col-sortable.active { color: var(--accent-blue); }
.sort-icon { transition: transform 150ms; }
.sort-icon.desc { transform: rotate(180deg); }
</style>
