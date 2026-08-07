<template>
  <AdminPageLayout title="角色管理">
    <template #actions>
      <a-button type="primary" size="small" @click="openCreateDialog">
        <template #icon><icon-plus /></template>
        新建角色
      </a-button>
    </template>

    <!-- 角色列表 -->
    <a-table
      :data="roles"
      :columns="tableColumns"
      :pagination="false"
      :bordered="false"
      row-key="id"
      size="medium"
      class="role-table"
      :scroll="{ y: '100%' }"
    >
      <template #name="{ record }">
        <span class="role-name">{{ record.name }}</span>
      </template>
      <template #code="{ record }">
        <code class="code-tag">{{ record.code }}</code>
      </template>
      <template #roleType="{ record }">
        <span class="type-badge" :class="record.roleType">{{ record.roleType === 'global' ? '全局' : '项目级' }}</span>
      </template>
      <template #userCount="{ record }">
        <span
          class="user-count-badge"
          :class="{ clickable: record.userCount > 0 }"
          @click="record.userCount > 0 && openUsersDialog(record)"
        >
          {{ record.userCount ?? 0 }} 人
        </span>
      </template>
      <template #builtin="{ record }">
        <span v-if="record.builtin" class="builtin-tag">是</span>
        <span v-else>—</span>
      </template>
      <template #actions="{ record }">
        <a-button type="text" size="mini" @click="openUsersDialog(record)">用户</a-button>
        <a-button type="text" size="mini" @click="openPermDialog(record)">权限</a-button>
        <a-button type="text" size="mini" @click="openCloneDialog(record)">克隆</a-button>
        <a-button type="text" size="mini" @click="editRole(record)" :disabled="record.builtin">编辑</a-button>
        <a-button type="text" size="mini" status="danger" @click="deleteRole(record)" :disabled="record.builtin">删除</a-button>
      </template>
    </a-table>

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
              <div class="user-avatar">{{ user.displayName?.charAt(0) || '?' }}</div>
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
                  <div class="user-avatar">{{ user.displayName?.charAt(0) || '?' }}</div>
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
import { Modal, Message } from '@arco-design/web-vue'
import { roleApi } from '@/api'
import type { RoleVO, RoleUsersVO } from '@/api/types'
import type { PermissionItem, PermissionGroup } from '@/api/role'
import AdminPageLayout from '@/components/admin/AdminPageLayout.vue'

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

const tableColumns = [
  { title: 'ID', dataIndex: 'id', width: 60 },
  { title: '名称', slotName: 'name', width: 150 },
  { title: '编码', slotName: 'code', width: 120 },
  { title: '类型', slotName: 'roleType', width: 100 },
  { title: '用户数', slotName: 'userCount', width: 80 },
  { title: '描述', dataIndex: 'description', ellipsis: true },
  { title: '内置', slotName: 'builtin', width: 80 },
  { title: '操作', slotName: 'actions', width: 260 },
]

const roles = ref<RoleVO[]>([])
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
  Modal.confirm({
    title: '确认删除角色',
    content: `确定要删除角色"${role.name}"吗？此操作不可撤销。`,
    okText: '删除角色',
    cancelText: '取消',
    okButtonProps: { status: 'danger' },
    async onOk() {
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
  loadRoles()
  loadPermissionDefinitions()
  loadGrantablePermissions()
})
</script>

<style scoped>
/* Table fills remaining space with fixed header */
.role-table { flex: 1; min-height: 0; }
.role-table :deep(.arco-table) { height: 100%; }
.role-table :deep(.arco-table-container) { height: 100%; display: flex; flex-direction: column; }
.role-table :deep(.arco-table-content-scroll) { flex: 1; min-height: 0; overflow: hidden; }
.role-table :deep(.arco-table-body) { flex: 1; max-height: none !important; overflow-y: auto !important; }

/* Table custom cell styles */
.role-table :deep(.arco-table-th) { font-size: var(--font-size-xs); color: var(--text-secondary); text-transform: uppercase; background: var(--bg-tertiary); }
.role-table :deep(.arco-table-td) { font-size: var(--font-size-sm); }
.role-table :deep(.arco-table-tr:hover .arco-table-td) { background: var(--bg-hover); }

.role-name { color: var(--text-bright); font-weight: 500; }
.code-tag { font-size: var(--font-size-xs); background: var(--bg-tertiary); padding: 2px 6px; border-radius: var(--radius-sm); color: var(--accent-blue); }
.type-badge { font-size: var(--font-size-xs); padding: 2px 6px; border-radius: var(--radius-sm); }
.type-badge.global { background: rgba(156,39,176,0.15); color: var(--accent-purple); }
.type-badge.project { background: rgba(33,150,243,0.15); color: var(--accent-blue); }
.builtin-tag { font-size: var(--font-size-xs); color: var(--accent-orange); }

.user-count-badge { font-size: var(--font-size-xs); padding: 2px 8px; border-radius: var(--radius-sm); background: var(--bg-tertiary); color: var(--text-secondary); }
.user-count-badge.clickable { cursor: pointer; color: var(--accent-blue); }
.user-count-badge.clickable:hover { background: rgba(33,150,243,0.15); }

/* Drawer title */
.drawer-title-row { display: flex; align-items: center; gap: 12px; }
.builtin-badge { font-size: var(--font-size-xs); background: rgba(255,152,0,0.15); color: var(--accent-orange); padding: 2px 8px; border-radius: var(--radius-sm); font-weight: 500; }
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
.user-avatar { width: 28px; height: 28px; border-radius: 50%; background: var(--accent-blue); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 500; flex-shrink: 0; }
.user-info { min-width: 0; }
.user-name { font-size: var(--font-size-sm); color: var(--text-bright); font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-meta { font-size: var(--font-size-xs); color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.project-groups { display: flex; flex-direction: column; gap: 16px; }
.project-group { border: 1px solid var(--border-light); border-radius: var(--radius-md); overflow: hidden; }
.project-group-header { display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: var(--bg-tertiary); border-bottom: 1px solid var(--border-light); }
.project-key-tag { font-size: var(--font-size-xs); background: rgba(33,150,243,0.15); color: var(--accent-blue); padding: 1px 6px; border-radius: var(--radius-sm); font-weight: 500; }
.project-group-name { font-size: var(--font-size-sm); color: var(--text-bright); font-weight: 500; }
.project-group-count { font-size: var(--font-size-xs); color: var(--text-secondary); margin-left: auto; }
</style>
