<template>
  <AdminPageLayout title="用户管理" subtitle="管理系统所有用户的账号、角色和权限">
    <template #actions>
      <a-button type="primary" size="small" @click="showCreateDialog = true">
        <template #icon><icon-plus /></template>
        新建用户
      </a-button>
    </template>

    <!-- 统计卡片 -->
    <template #stats>
      <AdminStatsBar :stats="statsItems" :loading="statsLoading" />
    </template>

    <!-- 用户列表 -->
    <AdminDataTable
      v-model:search-keyword="filters.keyword"
      search-placeholder="搜索用户名/姓名/邮箱..."
      :search-width="260"
      :data="users"
      :loading="loading"
      :total="total"
      v-model:current="pagination.page"
      v-model:page-size="pagination.pageSize"
      empty-title="暂无用户"
      empty-description="点击「新建用户」按钮添加第一个用户"
      @search="() => { pagination.page = 1; loadUsers() }"
      @page-change="loadUsers"
      @page-size-change="loadUsers"
      @row-click="navigateToUser"
    >
      <!-- 角色/状态筛选器 -->
      <template #toolbar-filters>
        <FilterSelect
          label="角色"
          v-model="filters.roleId"
          :width="100"
          @change="() => { pagination.page = 1; loadUsers() }"
        >
          <a-option v-for="role in globalRoles" :key="role.id" :value="role.id">{{ role.name }}</a-option>
        </FilterSelect>
        <FilterSelect
          label="状态"
          v-model="filters.status"
          :width="80"
          @change="() => { pagination.page = 1; loadUsers() }"
        >
          <a-option value="active">启用</a-option>
          <a-option value="disabled">禁用</a-option>
        </FilterSelect>
        <FilterSelect
          label="禁用类型"
          v-model="filters.banStatus"
          :width="100"
          @change="() => { pagination.page = 1; loadUsers() }"
        >
          <a-option value="banned">封禁</a-option>
          <a-option value="suspended">暂停</a-option>
          <a-option value="inactive">不活跃</a-option>
          <a-option value="deactivated">注销</a-option>
          <a-option value="locked">锁定</a-option>
        </FilterSelect>
      </template>

      <template #columns>
        <a-table-column title="用户" :width="260" data-index="displayName">
          <template #title>
            <span class="col-sortable" :class="{ active: sortField === 'displayName' }" @click="toggleSort('displayName')">
              用户
              <icon-caret-up v-if="sortField === 'displayName'" class="sort-icon" :class="{ desc: sortDesc }" :size="10" />
            </span>
          </template>
          <template #cell="{ record }">
            <div class="user-col">
              <UserAvatar :name="record.displayName || record.username" :size="32" />
              <div class="user-info">
                <router-link :to="`/admin/users/${record.id}`" class="username-link" @click.stop>{{ record.displayName || record.username }}</router-link>
                <span class="user-login">@{{ record.username }}</span>
              </div>
            </div>
          </template>
        </a-table-column>
        <a-table-column title="邮箱" data-index="email" ellipsis>
          <template #cell="{ record }">
            <span class="email-text">{{ record.email || '—' }}</span>
          </template>
        </a-table-column>
        <a-table-column title="系统角色" :width="160">
          <template #cell="{ record }">
            <template v-if="record.globalRoles && record.globalRoles.length > 0">
              <span v-for="role in record.globalRoles" :key="role.id" class="role-badge">{{ role.name }}</span>
            </template>
            <span v-else class="text-muted">—</span>
          </template>
        </a-table-column>
        <a-table-column title="状态" :width="90" align="center">
          <template #cell="{ record }">
            <a-tag
              v-if="record.status === 'active'"
              color="green"
              size="small"
              class="status-tag"
            >
              <template #icon><span class="status-dot status-dot--active" /></template>
              启用
            </a-tag>
            <a-tag
              v-else
              color="red"
              size="small"
              class="status-tag"
            >
              <template #icon><span class="status-dot status-dot--disabled" /></template>
              {{ getBanStatusLabel(record.banStatus) }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="操作" :width="160" align="right" fixed="right">
          <template #cell="{ record }">
            <div class="action-col">
              <a-button
                v-if="record.status === 'active' && record.id !== currentUserId"
                type="text" size="mini" status="danger"
                @click.stop="disableUser(record)"
              >禁用</a-button>
              <a-button
                v-else-if="record.status !== 'active'"
                type="text" size="mini"
                @click.stop="enableUser(record)"
              >启用</a-button>
              <a-button type="text" size="mini" @click.stop="openRoleDialog(record)">角色</a-button>
              <a-button type="text" size="mini" @click.stop="navigateToUser(record)">详情</a-button>
            </div>
          </template>
        </a-table-column>
      </template>
    </AdminDataTable>

    <!-- 新建用户弹窗 -->
    <a-modal
      v-model:visible="showCreateDialog"
      title="新建用户"
      :width="480"
      :footer="false"
      @cancel="closeCreateDialog"
    >
      <form @submit.prevent="handleCreateUser">
        <a-form :model="createForm" layout="vertical" size="small">
          <a-form-item label="显示名称" required>
            <a-input
              v-model="createForm.displayName"
              placeholder="例如：张伟、John Smith"
              :max-length="50"
            />
            <template #extra>用户的全名，将显示在系统各处</template>
          </a-form-item>
          <a-form-item label="用户名" required>
            <a-input
              v-model="createForm.username"
              placeholder="字母开头，只含字母/数字/下划线/连字符"
            />
            <template #extra>登录凭据，创建后不可修改</template>
          </a-form-item>
          <a-form-item label="邮箱" required>
            <a-input
              v-model="createForm.email"
              placeholder="user@company.com"
            />
          </a-form-item>
          <a-form-item label="临时密码" required>
            <a-input-password
              v-model="createForm.password"
              placeholder="至少 6 个字符，首次登录需修改"
              autocomplete="new-password"
            />
            <template #extra>用户首次登录时将被要求修改密码</template>
          </a-form-item>
        </a-form>
        <div v-if="createError" class="form-error">
          <icon-info-circle :size="14" />
          {{ createError }}
        </div>
        <div class="modal-footer">
          <a-button @click="closeCreateDialog">取消</a-button>
          <a-button type="primary" html-type="submit" :loading="creating">
            创建用户
          </a-button>
        </div>
      </form>
    </a-modal>

    <!-- 角色管理弹窗 -->
    <a-modal
      v-model:visible="showRoleDialog"
      :title="`管理角色 — ${selectedUser?.displayName || ''}`"
      :width="520"
      :footer="false"
      unmount-on-close
      class="role-modal"
      @cancel="showRoleDialog = false"
    >
      <!-- 加载中 -->
      <div v-if="roleLoading" class="role-loading">
        <a-spin />
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
              <a-checkbox
                :model-value="userRoleIds.includes(String(role.id))"
                :disabled="savingRoles"
                @change="() => toggleRole(role.id)"
              >
                <span class="role-name">{{ role.name }}</span>
                <span class="role-code">{{ role.code }}</span>
              </a-checkbox>
            </div>
          </div>
          <!-- 批量保存按钮 -->
          <div v-if="hasRoleChanges" class="role-save-bar">
            <span class="role-save-hint">系统角色已修改，请保存</span>
            <a-button size="mini" type="primary" :loading="savingRoles" @click="saveRoles">
              保存角色
            </a-button>
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
              <a-button type="text" size="mini" status="danger" title="撤销自动分配" @click="revokeGlobalMember(gm)">
                <template #icon><icon-close :size="14" /></template>
              </a-button>
            </div>
            <div v-if="userGlobalMembers.length === 0" class="role-empty-inline">
              未设置自动分配角色
            </div>
          </div>

          <!-- 添加自动分配的项目角色 -->
          <div class="add-global-member-section">
            <a-button v-if="!showAddGlobalMember" type="text" size="mini" @click="showAddGlobalMember = true">
              <template #icon><icon-plus :size="12" /></template>
              添加自动分配角色
            </a-button>
            <div v-else class="add-global-member-form">
              <a-select v-model="addGlobalRoleId" placeholder="选择角色..." size="mini" style="flex: 1; min-width: 120px">
                <a-option v-for="r in availableGlobalProjectRoles" :key="r.id" :value="r.id">{{ r.name }}</a-option>
              </a-select>
              <a-button size="mini" type="primary" :disabled="!addGlobalRoleId" @click="assignGlobalMember">确认</a-button>
              <a-button size="mini" @click="showAddGlobalMember = false; addGlobalRoleId = ''">取消</a-button>
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

          <EmptyState
            v-if="userProjectRoles.length === 0"
            icon="folder"
            title="未加入任何项目"
            :compact="true"
          />

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
                <a-select
                  :model-value="pr.roleCode"
                  size="mini"
                  style="width: 110px"
                  :disabled="pr.source === 'group'"
                  :title="pr.source === 'group' ? '通过用户组继承的角色不可直接修改' : ''"
                  @change="(val: any) => changeProjectRole(pr, val as string)"
                >
                  <a-option v-for="r in projectRoles" :key="r.id" :value="r.code">{{ r.name }}</a-option>
                </a-select>
                <a-button
                  v-if="pr.source !== 'group'"
                  type="text"
                  size="mini"
                  status="danger"
                  title="移除成员"
                  @click="removeFromProject(pr)"
                >
                  <template #icon><icon-close :size="14" /></template>
                </a-button>
                <span v-else class="btn-icon-sm disabled" title="通过用户组继承的角色不可直接移除">
                  <icon-close-circle :size="14" style="opacity: 0.3" />
                </span>
              </div>
            </div>
          </div>

          <!-- 添加到项目 -->
          <div class="add-project-section">
            <a-button v-if="!showAddProject" type="text" size="mini" @click="showAddProject = true">
              <template #icon><icon-plus :size="12" /></template>
              添加到项目
            </a-button>
            <div v-else class="add-project-form">
              <a-select v-model="addProjectId" placeholder="选择项目..." size="mini" style="flex: 1; min-width: 120px">
                <a-option v-for="p in availableProjects" :key="p.id" :value="p.id">
                  {{ p.key }} — {{ p.name }}
                </a-option>
              </a-select>
              <a-select v-model="addProjectRoleId" placeholder="选择角色..." size="mini" style="flex: 1; min-width: 120px">
                <a-option v-for="r in projectRoles" :key="r.id" :value="r.id">{{ r.name }}</a-option>
              </a-select>
              <a-button size="mini" type="primary" :disabled="!addProjectId || !addProjectRoleId" @click="addToProject">确认</a-button>
              <a-button size="mini" @click="cancelAddProject">取消</a-button>
            </div>
          </div>
        </div>
      </template>
    </a-modal>
  </AdminPageLayout>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import { Modal, Message } from '@arco-design/web-vue'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { userApi, projectApi, globalMemberApi, roleApi } from '@/api'
import type { UserProfileProjectRoleInfo } from '@/api/user'
import type { GlobalMemberVO } from '@/api/globalMember'
import { useAuthStore } from '@/stores/auth'
import { AdminPageLayout, AdminDataTable, AdminStatsBar, FilterSelect } from '@/components/admin'
import { UserAvatar, EmptyState } from '@/components/base'
import { usePagedList } from '@/composables/usePagedList'
import type { StatItem } from '@/components/admin'
import {
  IconUser, IconUserGroup, IconClose, IconCloseCircle,
  IconSafe, IconSettings, IconCaretUp
} from '@arco-design/web-vue/es/icon'

const authStore = useAuthStore()
const router = useRouter()
const currentUserId = computed(() => authStore.user?.userId)

// ===== 统计卡片 =====
const statsLoading = ref(false)
const statsItems = ref<StatItem[]>([
  { label: '总用户数', value: '—', icon: IconUser, color: 'blue' },
  { label: '启用中', value: '—', icon: IconUserGroup, color: 'green' },
  { label: '禁用中', value: '—', icon: IconSettings, color: 'red' },
  { label: '今日新增', value: '—', icon: IconSafe, color: 'orange' },
])

async function loadStats() {
  statsLoading.value = true
  try {
    const res = await userApi.stats()
    if (res.code === 0 && res.data) {
      const d = res.data
      statsItems.value = [
        { label: '总用户数', value: d.total, icon: IconUser, color: 'blue' },
        { label: '启用中', value: d.active, icon: IconUserGroup, color: 'green' },
        { label: '禁用中', value: d.disabled, icon: IconSettings, color: 'red' },
        { label: '今日新增', value: d.todayNew, icon: IconSafe, color: 'orange' },
      ]
    }
  } finally {
    statsLoading.value = false
  }
}

// 排序状态
const sortField = ref('createdAt')
const sortDesc = ref(true)

interface UserFilters {
  keyword: string
  roleId: string
  status: string
  banStatus: string
}

const { list: users, total, loading, pagination, filters, refresh: loadUsers } = usePagedList<any, UserFilters>(
  (params) => {
    const requestParams: Record<string, any> = { page: params.page, pageSize: params.pageSize }
    if (params.keyword) requestParams.keyword = params.keyword
    if (params.roleId) requestParams.roleId = params.roleId
    if (params.status) requestParams.status = params.status
    if (params.banStatus) requestParams.banStatus = params.banStatus
    if (sortField.value) {
      requestParams.sort = (sortDesc.value ? '-' : '') + sortField.value
    }
    return userApi.list(requestParams)
  },
  { pageSize: 20, initialFilters: { keyword: '', roleId: '', status: '', banStatus: '' } }
)

function toggleSort(field: string) {
  if (sortField.value === field) {
    sortDesc.value = !sortDesc.value
  } else {
    sortField.value = field
    sortDesc.value = true
  }
  pagination.page = 1
  loadUsers()
}

/** 点击行跳转用户详情（排除按钮和链接的点击） */
function navigateToUser(record: any) {
  router.push(`/admin/users/${record.id}`)
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

  const { confirmDelete } = useConfirmDelete()
  confirmDelete({
    itemName: `该用户在项目「${pr.projectName}」中的成员资格`,
    confirmText: '移除',
    onConfirm: async () => {
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

  const { confirmDelete } = useConfirmDelete()
  confirmDelete({
    itemName: `该用户的全局「${gm.roleName}」角色`,
    confirmText: '撤销',
    onConfirm: async () => {
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
  } catch (e) {
    console.error('[UserManagement] 刷新项目角色失败:', e)
  }
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

onMounted(() => {
  loadStats()
  loadGlobalRoles()
  loadProjectRoles()
  loadAllProjects()
})
</script>

<style scoped>
/* User column with avatar */
.user-col { display: flex; align-items: center; gap: 10px; }
.user-info { display: flex; flex-direction: column; min-width: 0; gap: 2px; }
.username-link { font-size: 13px; font-weight: 500; color: var(--accent-blue); text-decoration: none; line-height: 1.3; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.username-link:hover { text-decoration: underline; }
.user-login { font-size: 11px; color: var(--text-muted); line-height: 1.2; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.email-text { font-size: 13px; color: var(--tf-text-secondary); }

/* 状态标签 */
.status-tag { font-size: 12px; }
.status-dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; margin-right: 4px; }
.status-dot--active { background: var(--accent-green); }
.status-dot--disabled { background: var(--accent-red); }

/* 操作列 */
.action-col { display: flex; align-items: center; justify-content: flex-start; gap: 2px; flex-wrap: nowrap; white-space: nowrap; }

/* Modal */
.modal-footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--border-light); }

/* Form */
.form-error { display: flex; align-items: center; gap: 6px; padding: 8px 12px; background: var(--tf-danger-bg); border: 1px solid var(--tf-danger-strong); border-radius: var(--radius-md); color: var(--accent-red); font-size: var(--font-size-xs); margin-top: 12px; }

.role-item { margin-bottom: 8px; }
.role-item .role-code { font-size: var(--font-size-xs); color: var(--text-muted); margin-left: 8px; }
.role-item .role-name { font-size: var(--font-size-sm); color: var(--text-primary); }

/* Role Panel (inside a-modal) */
.role-modal :deep(.arco-modal-body) { padding: 0; max-height: 60vh; overflow-y: auto; }

.role-loading { display: flex; align-items: center; gap: 10px; padding: 32px; justify-content: center; color: var(--text-secondary); font-size: var(--font-size-sm); }

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
.project-role-key { font-size: var(--font-size-xs); font-weight: 500; color: var(--accent-blue); background: var(--tf-accent-light); padding: 2px 6px; border-radius: var(--radius-sm); flex-shrink: 0; }
.project-role-name { font-size: var(--font-size-sm); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.source-tag { font-size: 10px; font-weight: 500; padding: 2px 6px; border-radius: 3px; flex-shrink: 0; }
.source-tag.group { color: var(--tf-purple); background: var(--tf-purple-bg); }
.project-role-actions { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }

.btn-icon-sm.disabled { width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; cursor: not-allowed; opacity: 0.4; }

/* Add to Project */
.add-project-section { margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--border-light); }
.add-project-form { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }

/* Role badges */
.role-badge { display: inline-flex; align-items: center; height: 20px; padding: 0 7px; background: var(--tf-accent-bg-light); color: var(--accent-blue); font-size: 11px; font-weight: 500; border-radius: 3px; white-space: nowrap; margin-right: 4px; }
.text-muted { color: var(--text-muted); font-size: var(--font-size-sm); }

/* Global Project Role Section */
.role-section-hint { font-size: 11px; color: var(--text-muted); font-style: italic; }
.role-section-desc { font-size: 11px; color: var(--text-muted); margin-top: -8px; margin-bottom: 8px; }

/* Role Save Bar */
.role-save-bar { display: flex; align-items: center; justify-content: space-between; margin-top: 12px; padding: 10px 12px; background: var(--tf-accent-light); border-radius: var(--radius-sm); }
.role-save-hint { font-size: 12px; color: var(--accent-blue); }

.global-project-role-list { display: flex; flex-direction: column; gap: 4px; }
.global-project-role-item { display: flex; align-items: center; justify-content: space-between; padding: 6px 8px; border-radius: var(--radius-sm); transition: background 150ms; }
.global-project-role-item:hover { background: var(--bg-hover); }
.global-project-role-info { display: flex; align-items: center; gap: 8px; }
.global-tag { font-size: 10px; font-weight: 600; color: var(--tf-success); background: var(--tf-success-bg); padding: 2px 6px; border-radius: 3px; text-transform: uppercase; letter-spacing: 0.3px; }
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
