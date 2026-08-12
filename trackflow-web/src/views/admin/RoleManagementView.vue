<template>
  <AdminPageLayout title="角色管理" subtitle="管理系统中所有角色及其权限配置">
    <template #actions>
      <a-button type="primary" size="small" @click="openCreateDialog">
        <template #icon><icon-plus /></template>
        新建角色
      </a-button>
    </template>

    <!-- 统计卡片 -->
    <template #stats>
      <AdminStatsBar :stats="statsItems" :loading="statsLoading" />
    </template>

    <!-- 角色列表 -->
    <AdminDataTable
      :show-toolbar="true"
      :data="filteredRoles"
      :total="filteredRoles.length"
      :current="1"
      :page-size="filteredRoles.length || 20"
      :selectable="false"
      :column-resizable="true"
      :row-class="(record: any) => record.enabled === false ? 'row-disabled' : ''"
      search-placeholder="搜索角色名称、编码或描述..."
      :show-reset="true"
      v-model:search-keyword="searchKeyword"
      @search="applyFilter"
      @reset="resetFilter"
      empty-title="暂无角色"
      empty-description="创建角色来管理团队权限"
    >
      <!-- 筛选器 -->
      <template #toolbar-filters>
        <FilterSelect label="角色类型" v-model="filters.roleType" :width="90" @change="applyFilter">
          <a-option value="global">全局</a-option>
          <a-option value="project">项目级</a-option>
        </FilterSelect>
        <FilterSelect label="用户数" v-model="filters.hasUsers" :width="90" @change="applyFilter">
          <a-option value="yes">有用户</a-option>
          <a-option value="no">无用户</a-option>
        </FilterSelect>
        <FilterSelect label="系统角色" v-model="filters.builtin" :width="90" @change="applyFilter">
          <a-option value="true">是</a-option>
          <a-option value="false">否</a-option>
        </FilterSelect>
        <FilterSelect label="状态" v-model="filters.enabled" :width="80" @change="applyFilter">
          <a-option value="true">启用</a-option>
          <a-option value="false">禁用</a-option>
        </FilterSelect>
      </template>

      <template #columns>
        <!-- ID 列 -->
        <a-table-column title="ID" :width="56" align="center">
          <template #cell="{ rowIndex }">
            <span class="id-cell">{{ rowIndex + 1 }}</span>
          </template>
        </a-table-column>

        <!-- 角色名称：图标 + 名称 + 内置tag -->
        <a-table-column title="角色名称" :width="160">
          <template #cell="{ record }">
            <div class="role-name-cell">
              <span class="role-icon" :class="`role-icon--${getRoleIconColor(record)}`">
                <component :is="getRoleIcon(record)" />
              </span>
              <span class="role-name">{{ record.name }}</span>
              <a-tag v-if="record.builtin" size="small" color="arcoblue" class="builtin-tag-inline">内置</a-tag>
            </div>
          </template>
        </a-table-column>

        <!-- 编码 -->
        <a-table-column title="编码" :width="130">
          <template #cell="{ record }">
            <code class="code-tag">{{ record.code }}</code>
          </template>
        </a-table-column>

        <!-- 类型 -->
        <a-table-column title="类型" :width="80">
          <template #cell="{ record }">
            <span class="type-badge" :class="record.roleType">{{ record.roleType === 'global' ? '全局' : '项目级' }}</span>
          </template>
        </a-table-column>

        <!-- 用户数 -->
        <a-table-column title="用户数" :width="72" align="center">
          <template #cell="{ record }">
            <span
              class="user-count-badge"
              :class="{ clickable: record.userCount > 0 }"
              @click="record.userCount > 0 && openUsersDialog(record)"
            >{{ record.userCount ?? 0 }} 人</span>
          </template>
        </a-table-column>

        <!-- 描述：设最大宽度，超出 ellipsis，不独占所有空间 -->
        <a-table-column title="描述" data-index="description" :width="300" ellipsis />

        <!-- 系统角色、状态、操作 -->
        <a-table-column title="系统角色" :width="90" align="center">
          <template #cell="{ record }">
            <span :class="record.builtin ? 'flag-yes' : 'flag-no'">{{ record.builtin ? '是' : '否' }}</span>
          </template>
        </a-table-column>

        <a-table-column title="状态" :width="100" align="center">
          <template #cell="{ record }">
            <span class="status-dot" :class="record.enabled !== false ? 'enabled' : 'disabled'">
              <i class="dot" />{{ record.enabled !== false ? '启用' : '禁用' }}
            </span>
          </template>
        </a-table-column>

        <!-- 操作 -->
        <a-table-column title="操作" :width="220" align="right">
          <template #cell="{ record }">
            <div class="action-col">
              <a-button type="text" size="mini" @click="openUsersDialog(record)">用户</a-button>
              <a-button type="text" size="mini" @click="openPermDialog(record)">权限</a-button>
              <a-button type="text" size="mini" @click="openCloneDialog(record)">克隆</a-button>
              <a-button v-if="!record.builtin" type="text" size="mini" @click="editRole(record)">编辑</a-button>
              <a-button v-if="!record.builtin" type="text" size="mini" status="danger" @click="deleteRole(record)">删除</a-button>
              <!-- 内置角色只显示 ⋮ 更多菜单 -->
              <a-dropdown v-if="record.builtin" trigger="click">
                <a-button type="text" size="mini" class="more-btn">⋮</a-button>
                <template #content>
                  <a-doption @click="editRole(record)">编辑名称/描述</a-doption>
                  <a-doption @click="toggleEnabled(record)">
                    {{ record.enabled !== false ? '禁用角色' : '启用角色' }}
                  </a-doption>
                </template>
              </a-dropdown>
              <a-dropdown v-else trigger="click">
                <a-button type="text" size="mini" class="more-btn">⋮</a-button>
                <template #content>
                  <a-doption @click="toggleEnabled(record)">
                    {{ record.enabled !== false ? '禁用角色' : '启用角色' }}
                  </a-doption>
                </template>
              </a-dropdown>
            </div>
          </template>
        </a-table-column>
      </template>

      <!-- 右键菜单：与操作列功能对应的快捷入口 -->
      <template #context-menu="{ record, close }">
        <div class="ctx-menu-item" @click="openUsersDialog(record); close()">👥 查看用户</div>
        <div class="ctx-menu-item" @click="openPermDialog(record); close()">🔑 配置权限</div>
        <div class="ctx-menu-item" @click="openCloneDialog(record); close()">📋 克隆角色</div>
        <div v-if="!record.builtin" class="ctx-menu-item" @click="editRole(record); close()">✏️ 编辑</div>
        <div class="ctx-menu-item" @click="toggleEnabled(record); close()">
          {{ record.enabled !== false ? '🚫 禁用' : '✅ 启用' }}
        </div>
        <div v-if="!record.builtin" class="ctx-menu-item ctx-menu-item--danger" @click="deleteRole(record); close()">🗑️ 删除</div>
      </template>
    </AdminDataTable>

    <!-- 创建/编辑角色弹窗 -->
    <a-modal
      v-model:visible="showCreateDialog"
      :title="editingRole ? '编辑角色' : '创建角色'"
      :ok-text="editingRole ? '更新' : '创建角色'"
      cancel-text="取消"
      :width="420"
      @ok="submitRole"
      @cancel="showCreateDialog = false"
    >
      <a-form :model="roleForm" layout="vertical" size="medium">
        <a-form-item label="名称" required>
          <a-input v-model="roleForm.name" placeholder="角色名称" />
        </a-form-item>
        <a-form-item v-if="!editingRole" label="编码" required>
          <a-input v-model="roleForm.code" placeholder="例如 qa_lead" />
        </a-form-item>
        <a-form-item v-if="!editingRole" label="类型" required>
          <a-radio-group v-model="roleForm.roleType">
            <a-radio value="project">项目级</a-radio>
            <a-radio value="global">全局</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="描述">
          <a-input v-model="roleForm.description" placeholder="角色描述（可选）" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 权限配置抽屉 -->
    <a-drawer
      v-model:visible="showPermDialog"
      :title="`权限配置 — ${permRole?.name || ''}`"
      :width="560"
      :footer="!permRole?.builtin"
      @cancel="showPermDialog = false"
    >
      <template #title>
        <div class="drawer-title-row">
          <span>权限配置 — {{ permRole?.name }}</span>
          <span v-if="permRole?.builtin" class="builtin-badge">内置角色</span>
        </div>
      </template>

      <!-- 内置角色只读提示 -->
      <a-alert v-if="permRole?.builtin" type="warning" class="builtin-alert">
        <template #icon><icon-lock /></template>
        内置角色的权限不可修改。如需定制权限，请使用"克隆"功能创建副本后修改。
      </a-alert>

      <div v-for="group in filteredPermissionGroups" :key="group.category" class="perm-group">
        <h4 class="perm-category">{{ CATEGORY_LABELS[group.category] || group.category }}</h4>
        <div class="perm-list">
          <label
            v-for="perm in group.permissions"
            :key="perm.code"
            class="perm-item"
            :class="{ readonly: permRole?.builtin, ungrantable: !permRole?.builtin && !rolePerms.includes(perm.code) && !canGrantPermission(perm.code) }"
            :title="!permRole?.builtin && !canGrantPermission(perm.code) && !rolePerms.includes(perm.code) ? getUngrantableTooltip(perm.code) : ''"
          >
            <a-checkbox
              :model-value="rolePerms.includes(perm.code)"
              :disabled="permRole?.builtin || (!rolePerms.includes(perm.code) && !canGrantPermission(perm.code))"
              @change="togglePerm(perm.code)"
            />
            <span class="perm-name">{{ perm.name }}</span>
            <span class="perm-code">{{ perm.code }}</span>
            <span v-if="!permRole?.builtin && !canGrantPermission(perm.code) && !rolePerms.includes(perm.code)" class="perm-lock">🔒</span>
          </label>
        </div>
      </div>

      <template #footer>
        <div class="drawer-footer">
          <a-button @click="showPermDialog = false">取消</a-button>
          <a-button type="primary" @click="savePermissions">保存权限</a-button>
        </div>
      </template>
    </a-drawer>

    <!-- 克隆角色弹窗 -->
    <a-modal
      v-model:visible="showCloneDialog"
      :title="`克隆角色 — ${cloneSource?.name || ''}`"
      ok-text="克隆角色"
      cancel-text="取消"
      :width="420"
      :ok-loading="cloneLoading"
      @ok="submitClone"
      @cancel="showCloneDialog = false"
    >
      <a-alert type="info" style="margin-bottom: 16px;">
        将创建一个新角色，自动继承原角色的全部权限配置。
      </a-alert>
      <a-form :model="cloneForm" layout="vertical" size="medium">
        <a-form-item label="新角色名称" required>
          <a-input v-model="cloneForm.name" placeholder="角色名称" />
        </a-form-item>
        <a-form-item label="新角色编码" required>
          <a-input v-model="cloneForm.code" placeholder="英文小写+下划线" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 已分配用户弹窗 -->
    <a-modal
      v-model:visible="showUsersDialog"
      :title="`已分配用户 — ${usersData?.roleName || ''}`"
      :footer="false"
      :width="600"
      @cancel="showUsersDialog = false"
    >
      <template #title>
        <div class="modal-title-row">
          <span>已分配用户 — {{ usersData?.roleName }}</span>
          <span class="users-total">共 {{ usersData?.totalUserCount ?? 0 }} 人</span>
        </div>
      </template>

      <a-spin :loading="usersLoading" style="width: 100%;">
        <!-- 全局角色：直接显示用户列表 -->
        <template v-if="usersData?.roleType === 'global'">
          <div v-if="usersData.globalUsers.length === 0" class="users-empty">
            <a-empty description="该角色尚未分配给任何用户">
              <template #image><icon-user style="font-size: 48px; color: var(--color-text-3);" /></template>
            </a-empty>
          </div>
          <div v-else class="users-list">
            <div v-for="user in usersData.globalUsers" :key="user.id" class="user-item">
              <UserAvatar :name="user.displayName || user.username || ''" :size="28" />
              <div class="user-info">
                <div class="user-name">{{ user.displayName }}</div>
                <div class="user-meta">{{ user.username }} · {{ user.email || '—' }}</div>
              </div>
            </div>
          </div>
        </template>

        <!-- 项目角色：按项目分组显示 -->
        <template v-else-if="usersData?.roleType === 'project'">
          <div v-if="usersData.projectGroups.length === 0" class="users-empty">
            <a-empty description="该角色尚未在任何项目中分配给用户">
              <template #image><icon-user style="font-size: 48px; color: var(--color-text-3);" /></template>
            </a-empty>
          </div>
          <div v-else class="project-groups">
            <div v-for="group in usersData.projectGroups" :key="group.projectId" class="project-group">
              <div class="project-group-header">
                <span class="project-key-tag">{{ group.projectKey }}</span>
                <span class="project-group-name">{{ group.projectName }}</span>
                <span class="project-group-count">{{ group.users.length }} 人</span>
              </div>
              <div class="users-list">
                <div v-for="user in group.users" :key="user.id" class="user-item">
                  <UserAvatar :name="user.displayName || user.username || ''" :size="28" />
                  <div class="user-info">
                    <div class="user-name">{{ user.displayName }}</div>
                    <div class="user-meta">{{ user.username }} · {{ user.email || '—' }}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 加载中但无数据 -->
        <template v-else-if="!usersLoading">
          <a-empty description="暂无数据" />
        </template>
      </a-spin>
    </a-modal>
  </AdminPageLayout>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { Message } from '@arco-design/web-vue'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { roleApi } from '@/api'
import type { RoleVO, RoleUsersVO } from '@/api/types'
import type { PermissionGroup } from '@/api/role'
import { AdminPageLayout, AdminDataTable, AdminStatsBar, FilterSelect } from '@/components/admin'
import { UserAvatar } from '@/components/base'
import type { StatItem } from '@/components/admin'
import {
  IconSafe, IconUserGroup, IconUser, IconSettings,
  IconIdcard, IconCode, IconEye, IconLock, IconTag, IconTrophy
} from '@arco-design/web-vue/es/icon'

const CATEGORY_LABELS: Record<string, string> = {
  system: '系统权限',
  project: '项目权限',
  issue: '工单权限',
  sprint: '迭代权限',
  query: '查询权限',
  report: '报表权限',
  integration: '集成权限',
  time_tracking: '时间追踪权限',
  rule: '规则权限',
}

// ===== 统计卡片 =====
const statsLoading = ref(false)
const statsItems = ref<StatItem[]>([
  { label: '总角色数', value: '—', icon: IconSafe, color: 'blue' },
  { label: '系统角色', value: '—', icon: IconUserGroup, color: 'purple' },
  { label: '项目角色', value: '—', icon: IconUser, color: 'green' },
  { label: '使用中', value: '—', icon: IconSettings, color: 'orange' },
])

async function loadStats() {
  statsLoading.value = true
  try {
    const res = await roleApi.stats()
    if (res.code === 0 && res.data) {
      const d = res.data
      statsItems.value = [
        { label: '总角色数', value: d.total, icon: IconSafe, color: 'blue' },
        { label: '系统角色', value: d.globalRoles, icon: IconUserGroup, color: 'purple' },
        { label: '项目角色', value: d.projectRoles, icon: IconUser, color: 'green' },
        { label: '使用中', value: d.rolesInUse, icon: IconSettings, color: 'orange' },
      ]
    }
  } finally {
    statsLoading.value = false
  }
}

const roles = ref<RoleVO[]>([])

// ===== 筛选逻辑 =====
const searchKeyword = ref('')
const filters = reactive({
  roleType: '',
  hasUsers: '',
  builtin: '',
  enabled: '',
})

const filteredRoles = computed(() => {
  return roles.value.filter(r => {
    if (searchKeyword.value) {
      const kw = searchKeyword.value.toLowerCase()
      if (!r.name.toLowerCase().includes(kw) && !r.code.toLowerCase().includes(kw) && !(r.description || '').toLowerCase().includes(kw)) return false
    }
    if (filters.roleType && r.roleType !== filters.roleType) return false
    if (filters.hasUsers === 'yes' && (r.userCount ?? 0) === 0) return false
    if (filters.hasUsers === 'no' && (r.userCount ?? 0) > 0) return false
    if (filters.builtin === 'true' && !r.builtin) return false
    if (filters.builtin === 'false' && r.builtin) return false
    if (filters.enabled === 'true' && r.enabled === false) return false
    if (filters.enabled === 'false' && r.enabled !== false) return false
    return true
  })
})

function applyFilter() { /* 响应式自动更新 */ }

function resetFilter() {
  searchKeyword.value = ''
  filters.roleType = ''
  filters.hasUsers = ''
  filters.builtin = ''
  filters.enabled = ''
}

// ===== 角色图标和颜色映射 =====
// 根据角色 code 返回合适的图标和颜色
function getRoleIcon(role: RoleVO) {
  const code = role.code
  if (code === 'system_admin') return IconTrophy
  if (code === 'project_admin') return IconIdcard
  if (code === 'developer') return IconCode
  if (code === 'tester') return IconSafe
  if (code === 'observer') return IconEye
  if (code === 'product_manager') return IconTag
  if (code === 'tech_lead') return IconUserGroup
  if (code === 'user_manager') return IconUser
  if (code === 'project_creator') return IconSettings
  if (role.roleType === 'global') return IconLock
  return IconUser
}

function getRoleIconColor(role: RoleVO) {
  const code = role.code
  if (code === 'system_admin') return 'orange'
  if (code === 'project_admin') return 'blue'
  if (code === 'developer') return 'purple'
  if (code === 'tester') return 'green'
  if (code === 'observer') return 'gray'
  if (code === 'product_manager') return 'pink'
  if (code === 'tech_lead') return 'cyan'
  return role.roleType === 'global' ? 'orange' : 'blue'
}
const showCreateDialog = ref(false)
const editingRole = ref<RoleVO | null>(null)
const roleForm = reactive({ name: '', code: '', roleType: 'project', description: '' })

const showPermDialog = ref(false)
const permRole = ref<RoleVO | null>(null)
const rolePerms = ref<string[]>([])
const permissionGroups = ref<PermissionGroup[]>([])
const grantablePermissions = ref<Set<string>>(new Set())
const isFullAdmin = ref(false)

/**
 * 根据角色类型过滤可见的权限组：
 * - global 角色：显示 global scope 权限（系统管理权限）
 * - project 角色：显示 project scope 权限（项目/工单/迭代等）
 * 过滤掉空分组
 */
const filteredPermissionGroups = computed(() => {
  if (!permRole.value) return permissionGroups.value
  const roleType = permRole.value.roleType
  return permissionGroups.value
    .map(group => ({
      ...group,
      permissions: group.permissions.filter(p => {
        if (roleType === 'global') return p.scope === 'global'
        return p.scope === 'project' || p.code === 'project:create'
      })
    }))
    .filter(group => group.permissions.length > 0)
})

const showCloneDialog = ref(false)
const cloneSource = ref<RoleVO | null>(null)
const cloneForm = reactive({ name: '', code: '' })
const cloneLoading = ref(false)

const showUsersDialog = ref(false)
const usersData = ref<RoleUsersVO | null>(null)
const usersLoading = ref(false)

async function loadRoles() {
  try {
    const res = await roleApi.list({ pageSize: 100 })
    roles.value = res.data?.list || []
  } catch (e) { roles.value = [] }
}

async function loadPermissionDefinitions() {
  try {
    const res = await roleApi.listPermissionDefinitions()
    permissionGroups.value = res.data || []
  } catch (e) { permissionGroups.value = [] }
}

async function loadGrantablePermissions() {
  try {
    const res = await roleApi.listGrantablePermissions()
    const perms: string[] = res.data || []
    if (perms.includes('*')) {
      isFullAdmin.value = true
      grantablePermissions.value = new Set()
    } else {
      isFullAdmin.value = false
      grantablePermissions.value = new Set(perms)
    }
  } catch (e) {
    isFullAdmin.value = false
    grantablePermissions.value = new Set()
  }
}

function canGrantPermission(permCode: string): boolean {
  if (isFullAdmin.value) return true
  if (grantablePermissions.value.has(permCode)) return true
  return false
}

function getUngrantableTooltip(permCode: string): string {
  if (canGrantPermission(permCode)) return ''
  return '您不能授予自己不持有的权限'
}

function openCreateDialog() {
  editingRole.value = null
  roleForm.name = ''; roleForm.code = ''; roleForm.roleType = 'project'; roleForm.description = ''
  showCreateDialog.value = true
}

function editRole(role: RoleVO) {
  editingRole.value = role
  roleForm.name = role.name; roleForm.code = role.code; roleForm.roleType = role.roleType; roleForm.description = role.description || ''
  showCreateDialog.value = true
}

async function submitRole() {
  if (editingRole.value) {
    await roleApi.update(editingRole.value.id, { name: roleForm.name, description: roleForm.description })
  } else {
    await roleApi.create({ name: roleForm.name, code: roleForm.code, roleType: roleForm.roleType, description: roleForm.description })
  }
  showCreateDialog.value = false
  loadRoles()
}

async function deleteRole(role: RoleVO) {
  const { confirmDelete } = useConfirmDelete()
  confirmDelete({
    itemName: `角色「${role.name}」`,
    confirmText: '删除角色',
    onConfirm: async () => {
      try {
        await roleApi.delete(role.id)
        Message.success('角色已删除')
        loadRoles()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '删除失败')
      }
    }
  })
}

function openCloneDialog(role: RoleVO) {
  cloneSource.value = role
  cloneForm.name = `${role.name} (副本)`
  cloneForm.code = `${role.code}_copy`
  showCloneDialog.value = true
}

async function submitClone() {
  if (!cloneForm.name.trim() || !cloneForm.code.trim()) {
    Message.warning('名称和编码不能为空')
    return
  }
  cloneLoading.value = true
  try {
    await roleApi.clone(cloneSource.value!.id, {
      name: cloneForm.name.trim(),
      code: cloneForm.code.trim()
    })
    showCloneDialog.value = false
    Message.success('角色克隆成功')
    loadRoles()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '克隆失败')
  } finally {
    cloneLoading.value = false
  }
}

async function openPermDialog(role: RoleVO) {
  permRole.value = role
  showPermDialog.value = true
  try {
    const res = await roleApi.getPermissions(role.id)
    rolePerms.value = res.data || []
  } catch (e) { rolePerms.value = [] }
}

function togglePerm(perm: string) {
  if (rolePerms.value.includes(perm)) {
    rolePerms.value = rolePerms.value.filter(p => p !== perm)
  } else {
    if (!canGrantPermission(perm)) return
    rolePerms.value.push(perm)
  }
}

async function savePermissions() {
  await roleApi.updatePermissions(permRole.value!.id, rolePerms.value)
  showPermDialog.value = false
  Message.success('权限保存成功')
}

async function toggleEnabled(role: RoleVO) {
  const newEnabled = role.enabled === false ? true : false
  try {
    const res = await roleApi.setEnabled(role.id, newEnabled)
    if (res.code === 0) {
      role.enabled = newEnabled
      Message.success(newEnabled ? '角色已启用' : '角色已禁用')
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  }
}

async function openUsersDialog(role: RoleVO) {
  showUsersDialog.value = true
  usersLoading.value = true
  usersData.value = null
  try {
    const res = await roleApi.getUsers(role.id)
    usersData.value = res.data || null
  } catch (e: any) {
    Message.error(e.response?.data?.message || '获取用户列表失败')
  } finally {
    usersLoading.value = false
  }
}

onMounted(() => {
  loadStats()
  loadRoles()
  loadPermissionDefinitions()
  loadGrantablePermissions()
})
</script>

<style scoped>
/* Table */
.id-cell { font-size: 12px; color: var(--tf-text-tertiary); }

.role-name { color: var(--text-bright); font-weight: 500; font-size: 13px; }
.role-name-cell { display: flex; align-items: center; gap: 6px; }
.builtin-tag-inline { flex-shrink: 0; }

/* 角色图标 */
.role-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 22px; height: 22px; border-radius: 4px; flex-shrink: 0; font-size: 13px;
}
.role-icon--blue     { background: rgba(79,140,255,0.15); color: #4f8cff; }
.role-icon--purple   { background: rgba(148,100,255,0.15); color: #9464ff; }
.role-icon--green    { background: rgba(56,197,120,0.15); color: #38c578; }
.role-icon--orange   { background: rgba(255,163,60,0.15); color: #ffa33c; }
.role-icon--gray     { background: rgba(150,160,180,0.15); color: #96a0b4; }
.role-icon--cyan     { background: rgba(34,211,238,0.15); color: #22d3ee; }
.role-icon--pink     { background: rgba(236,72,153,0.15); color: #ec4899; }
.code-tag { font-size: var(--font-size-xs); background: var(--bg-tertiary); padding: 2px 6px; border-radius: var(--radius-sm); color: var(--accent-blue); font-family: monospace; }
.type-badge { font-size: var(--font-size-xs); padding: 2px 6px; border-radius: var(--radius-sm); }
.type-badge.global { background: var(--tf-purple-medium); color: var(--accent-purple); }
.type-badge.project { background: var(--tf-accent-medium); color: var(--accent-blue); }
.user-count-badge { font-size: var(--font-size-xs); padding: 2px 8px; border-radius: var(--radius-sm); background: var(--bg-tertiary); color: var(--text-secondary); }
.user-count-badge.clickable { cursor: pointer; color: var(--accent-blue); }
.user-count-badge.clickable:hover { background: var(--tf-accent-medium); }

/* 系统角色列 */
.flag-yes { font-size: 12px; color: #38c578; font-weight: 500; }
.flag-no  { font-size: 12px; color: var(--tf-text-tertiary); }

/* 状态列 */
.status-dot { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; font-weight: 500; }
.status-dot .dot { width: 7px; height: 7px; border-radius: 50%; }
.status-dot.enabled  { color: #38c578; }
.status-dot.enabled .dot { background: #38c578; }
.status-dot.disabled { color: #f87171; }
.status-dot.disabled .dot { background: #f87171; }

.action-col { display: flex; align-items: center; justify-content: flex-end; gap: 2px; flex-wrap: nowrap; white-space: nowrap; }
.more-btn { font-size: 16px; letter-spacing: 1px; }

/* 禁用行样式：整行变半透明灰色 */
:deep(.row-disabled) { opacity: 0.45; }
:deep(.row-disabled):hover { opacity: 0.6; }

/* 右键菜单条目 */
.ctx-menu-item {
  padding: 7px 14px;
  font-size: 13px;
  color: var(--tf-text-primary);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: background 0.1s;
  white-space: nowrap;
}
.ctx-menu-item:hover { background: var(--tf-bg-hover); }
.ctx-menu-item--danger { color: var(--tf-error); }
.ctx-menu-item--danger:hover { background: rgba(248, 81, 73, 0.1); }

/* Drawer title */
.drawer-title-row { display: flex; align-items: center; gap: 12px; }
.builtin-badge { font-size: var(--font-size-xs); background: var(--tf-warning-medium); color: var(--accent-orange); padding: 2px 8px; border-radius: var(--radius-sm); font-weight: 500; }
.builtin-alert { margin-bottom: 16px; }

/* Permission groups */
.perm-group { margin-bottom: 20px; }
.perm-category { font-size: var(--font-size-sm); color: var(--accent-blue); text-transform: capitalize; margin-bottom: 10px; font-weight: 500; }
.perm-list { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.perm-item { display: flex; align-items: center; gap: 6px; font-size: var(--font-size-sm); color: var(--text-primary); cursor: pointer; padding: 4px 0; }
.perm-item.readonly { opacity: 0.6; cursor: not-allowed; }
.perm-item.ungrantable { opacity: 0.5; cursor: not-allowed; }
.perm-name { color: var(--text-primary); }
.perm-code { font-size: var(--font-size-xs); color: var(--text-tertiary); margin-left: 2px; }
.perm-lock { font-size: 11px; margin-left: 4px; opacity: 0.7; }

/* Drawer footer */
.drawer-footer { display: flex; justify-content: flex-end; gap: 8px; }

/* Modal title row */
.modal-title-row { display: flex; align-items: center; gap: 12px; }
.users-total { font-size: var(--font-size-sm); color: var(--text-secondary); }

/* Users */
.users-empty { padding: 24px 0; }
.users-list { display: flex; flex-direction: column; gap: 2px; }
.user-item { display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-radius: var(--radius-md); transition: background 100ms; }
.user-item:hover { background: var(--bg-hover); }
.user-info { min-width: 0; }
.user-name { font-size: var(--font-size-sm); color: var(--text-bright); font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-meta { font-size: var(--font-size-xs); color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.project-groups { display: flex; flex-direction: column; gap: 16px; }
.project-group { border: 1px solid var(--border-light); border-radius: var(--radius-md); overflow: hidden; }
.project-group-header { display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: var(--bg-tertiary); border-bottom: 1px solid var(--border-light); }
.project-key-tag { font-size: var(--font-size-xs); background: var(--tf-accent-medium); color: var(--accent-blue); padding: 1px 6px; border-radius: var(--radius-sm); font-weight: 500; }
.project-group-name { font-size: var(--font-size-sm); color: var(--text-bright); font-weight: 500; }
.project-group-count { font-size: var(--font-size-xs); color: var(--text-secondary); margin-left: auto; }
</style>
