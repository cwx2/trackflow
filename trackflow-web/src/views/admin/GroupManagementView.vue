<template>
  <AdminPageLayout title="用户组管理" subtitle="通过用户组批量管理团队权限">
    <template #actions>
      <a-button type="primary" size="small" @click="openCreateDialog">
        <template #icon><icon-plus /></template>
        新建用户组
      </a-button>
    </template>

    <!-- 统计卡片 -->
    <template #stats>
      <AdminStatsBar :stats="statsItems" :loading="statsLoading" />
    </template>

    <!-- 用户组列表 -->
    <AdminDataTable
      v-model:search-keyword="keyword"
      search-placeholder="搜索用户组..."
      :search-width="320"
      :data="groups"
      :loading="loading"
      :total="groups.length"
      :current="1"
      :page-size="groups.length || 20"
      empty-title="暂无用户组"
      empty-description="创建用户组来批量管理团队权限"
      :columns="tableColumns"
      @search="loadGroups"
      @row-click="openDetail"
    >

    <!-- 创建/编辑弹窗 -->
    <a-modal
      v-model:visible="showFormDialog"
      :title="editingGroup ? '编辑用户组' : '创建用户组'"
      :width="440"
      :ok-text="editingGroup ? '保存修改' : '创建用户组'"
      cancel-text="取消"
      :ok-button-props="{ disabled: !formData.name?.trim() }"
      @ok="submitForm"
      @cancel="showFormDialog = false"
    >
      <a-form :model="formData" layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model="formData.name" placeholder="例如：前端团队" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model="formData.description" placeholder="组的用途说明..." :max-length="500" :auto-size="{ minRows: 3, maxRows: 6 }" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 详情面板（抽屉） -->
    <a-drawer
      v-model:visible="showDetailPanel"
      :title="detailData?.name"
      :width="640"
      :footer="false"
    >
      <div class="detail-body" v-if="detailData">
        <!-- 基本信息 -->
        <div class="detail-section" v-if="detailData.description">
          <p class="detail-desc">{{ detailData.description }}</p>
        </div>

        <!-- 成员管理 -->
        <div class="detail-section">
          <div class="section-header">
            <h4>成员 ({{ detailData.members.length }})</h4>
            <a-button type="outline" size="small" @click="showAddMemberDialog = true">
              <template #icon><icon-plus /></template>
              添加成员
            </a-button>
          </div>
          <div class="member-list" v-if="detailData.members.length > 0">
            <div v-for="member in detailData.members" :key="member.userId" class="member-item">
              <div class="member-info">
                <UserAvatar :name="member.displayName || member.username" :size="32" />
                <div>
                  <span class="member-name">{{ member.displayName || member.username }}</span>
                  <span class="member-email">{{ member.email }}</span>
                </div>
              </div>
              <a-button type="text" size="mini" status="danger" @click="removeMember(member)">移除</a-button>
            </div>
          </div>
          <a-empty v-else description="暂无成员" />
        </div>

        <!-- 角色分配 -->
        <div class="detail-section">
          <div class="section-header">
            <h4>角色分配 ({{ detailData.roles.length }})</h4>
            <a-button type="outline" size="small" @click="showAddRoleDialog = true">
              <template #icon><icon-plus /></template>
              分配角色
            </a-button>
          </div>
          <div class="role-list" v-if="detailData.roles.length > 0">
            <div v-for="role in detailData.roles" :key="role.id" class="role-item">
              <div class="role-info">
                <a-tag size="small">{{ role.roleName }}</a-tag>
                <a-tag v-if="role.scope === 'global'" size="small" color="arcoblue">全局</a-tag>
                <a-tag v-else-if="role.scope === 'all_projects'" size="small" color="green">所有项目</a-tag>
                <span class="role-scope" v-else-if="role.projectId">
                  {{ role.projectName }} ({{ role.projectKey }})
                </span>
              </div>
              <a-button type="text" size="mini" status="danger" @click="removeRole(role)">移除</a-button>
            </div>
          </div>
          <a-empty v-else description="暂无角色分配" />
        </div>
      </div>
    </a-drawer>

    <!-- 添加成员弹窗 -->
    <a-modal
      v-model:visible="showAddMemberDialog"
      title="添加成员"
      :width="440"
      ok-text="添加成员"
      cancel-text="取消"
      :ok-button-props="{ disabled: selectedUserIds.length === 0 }"
      @ok="submitAddMembers"
      @cancel="showAddMemberDialog = false"
    >
      <a-form :model="{}" layout="vertical">
        <a-form-item label="搜索用户">
          <a-input v-model="memberSearchKeyword" placeholder="输入用户名或姓名..." @input="searchUsers" allow-clear />
        </a-form-item>
      </a-form>
      <div class="user-search-results" v-if="searchedUsers.length > 0">
        <div
          v-for="user in searchedUsers"
          :key="user.id"
          class="user-option"
          :class="{ selected: selectedUserIds.includes(user.id) }"
          @click="toggleUserSelection(user)"
        >
          <UserAvatar :name="user.displayName || user.username" :size="24" />
          <span class="user-label">{{ user.displayName || user.username }} ({{ user.username }})</span>
          <icon-check v-if="selectedUserIds.includes(user.id)" class="check-mark" />
        </div>
      </div>
      <div class="selected-count" v-if="selectedUserIds.length > 0">
        已选择 {{ selectedUserIds.length }} 名用户
      </div>
    </a-modal>

    <!-- 分配角色弹窗 -->
    <a-modal
      v-model:visible="showAddRoleDialog"
      title="分配角色"
      :width="480"
      ok-text="分配角色"
      cancel-text="取消"
      :ok-button-props="{ disabled: !canSubmitRole }"
      @ok="submitAssignRole"
      @cancel="showAddRoleDialog = false"
    >
      <a-form :model="roleFormData" layout="vertical">
        <a-form-item label="角色" required>
          <a-select v-model="roleFormData.roleId" placeholder="请选择角色" @change="onRoleChange">
            <a-option v-for="role in availableRoles" :key="role.id" :value="role.id">
              {{ role.name }} ({{ role.roleType }})
            </a-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="selectedRoleType === 'project'" label="作用域" required>
          <a-radio-group v-model="roleFormData.scopeMode" direction="vertical">
            <a-radio value="global">
              <div class="scope-radio-content">
                <span class="scope-label">全局（所有项目）</span>
                <span class="scope-desc">角色对所有现有及未来新建的项目生效</span>
              </div>
            </a-radio>
            <a-radio value="projects">
              <div class="scope-radio-content">
                <span class="scope-label">指定项目</span>
                <span class="scope-desc">选择一个或多个项目</span>
              </div>
            </a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="selectedRoleType === 'project' && roleFormData.scopeMode === 'projects'" label="选择项目" required>
          <a-checkbox-group v-model="roleFormData.selectedProjectIds" direction="vertical">
            <a-checkbox v-for="project in availableProjects" :key="project.id" :value="project.id">
              {{ project.name }} ({{ project.key }})
            </a-checkbox>
          </a-checkbox-group>
          <div class="selected-count" v-if="roleFormData.selectedProjectIds.length > 0">
            已选择 {{ roleFormData.selectedProjectIds.length }} 个项目
          </div>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 删除确认弹窗 -->
    <a-modal
      v-model:visible="showDeleteDialog"
      title="确认删除"
      :width="400"
      ok-text="删除用户组"
      cancel-text="取消"
      :ok-button-props="{ status: 'danger' }"
      @ok="executeDelete"
      @cancel="showDeleteDialog = false"
    >
      <p>确定要删除用户组 <strong>{{ deletingGroup?.name }}</strong> 吗？</p>
      <a-alert type="warning" :show-icon="false" style="margin-top: 8px">
        此操作不可撤销。组内成员将失去通过该组继承的所有权限。
      </a-alert>
    </a-modal>
  </AdminPageLayout>
</template>

<script setup lang="ts">
import { formatDate } from '@/utils/date'
import { ref, computed, onMounted, reactive } from 'vue'
import { groupApi, userApi, projectApi } from '@/api'
import type { UserGroupVO, UserGroupDetailVO } from '@/api/group'
import type { UserVO, ProjectVO } from '@/api/types'
import { Message } from '@arco-design/web-vue'
import { useRequest } from '@/composables/useRequest'
import { UserAvatar } from '@/components/base'
import { AdminPageLayout, AdminDataTable, AdminStatsBar } from '@/components/admin'
import type { ColumnDef } from '@/components/admin'
import type { StatItem } from '@/components/admin'
import {
  IconUserGroup, IconUser, IconSafe
} from '@arco-design/web-vue/es/icon'

// ===== 列表数据 =====
// ===== 统计卡片 =====
const statsLoading = ref(false)
const statsItems = ref<StatItem[]>([
  { label: '用户组总数', value: '—', icon: IconUserGroup, color: 'blue' },
  { label: '总成员数', value: '—', icon: IconUser, color: 'green' },
  { label: '已分配角色', value: '—', icon: IconSafe, color: 'purple' },
])

async function loadStats() {
  statsLoading.value = true
  try {
    const res = await groupApi.stats()
    if (res.code === 0 && res.data) {
      const d = res.data
      statsItems.value = [
        { label: '用户组总数', value: d.total, icon: IconUserGroup, color: 'blue' },
        { label: '总成员数', value: d.totalMembers, icon: IconUser, color: 'green' },
        { label: '已分配角色', value: d.groupsWithRoles, icon: IconSafe, color: 'purple' },
      ]
    }
  } finally {
    statsLoading.value = false
  }
}

const groups = ref<UserGroupVO[]>([])
const keyword = ref('')

const { loading, execute: loadGroups } = useRequest(
  () => groupApi.list({ keyword: keyword.value || undefined, page: 1, pageSize: 100 }),
  {
    immediate: false,
    onSuccess: (data: any) => {
      groups.value = data?.list ?? []
    }
  }
)

// ===== 创建/编辑 =====
const showFormDialog = ref(false)
const editingGroup = ref<UserGroupVO | null>(null)
const formData = reactive({ name: '', description: '' })

// ===== 详情面板 =====
const showDetailPanel = ref(false)
const detailData = ref<UserGroupDetailVO | null>(null)
const currentGroupId = ref('')

// ===== 添加成员 =====
const showAddMemberDialog = ref(false)
const memberSearchKeyword = ref('')
const searchedUsers = ref<UserVO[]>([])
const selectedUserIds = ref<string[]>([])

// ===== 分配角色 =====
const showAddRoleDialog = ref(false)
const availableRoles = ref<any[]>([])
const availableProjects = ref<ProjectVO[]>([])
const roleFormData = reactive({ roleId: '', scopeMode: 'global' as 'global' | 'projects', selectedProjectIds: [] as string[] })
const selectedRoleType = ref('')

// ===== 删除确认 =====
const showDeleteDialog = ref(false)
const deletingGroup = ref<UserGroupVO | null>(null)

const canSubmitRole = computed(() => {
  if (!roleFormData.roleId) return false
  if (selectedRoleType.value === 'project') {
    if (roleFormData.scopeMode === 'projects' && roleFormData.selectedProjectIds.length === 0) {
      return false
    }
  }
  return true
})

function openCreateDialog() {
  editingGroup.value = null
  formData.name = ''
  formData.description = ''
  showFormDialog.value = true
}

function openEditDialog(group: UserGroupVO) {
  editingGroup.value = group
  formData.name = group.name
  formData.description = group.description || ''
  showFormDialog.value = true
}

async function submitForm() {
  const data = { name: formData.name.trim(), description: formData.description?.trim() || undefined }
  try {
    if (editingGroup.value) {
      await groupApi.update(editingGroup.value.id, data)
      Message.success('用户组已更新')
    } else {
      await groupApi.create(data)
      Message.success('用户组已创建')
    }
    showFormDialog.value = false
    await loadGroups()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  }
}

async function openDetail(record: any) {
  const group = record as UserGroupVO
  currentGroupId.value = group.id
  try {
    const res = await groupApi.getDetail(record.id)
    if (res.code === 0) {
      detailData.value = res.data
      showDetailPanel.value = true
    }
  } catch (e) {
    Message.error('获取详情失败')
  }
}

async function reloadDetail() {
  if (!currentGroupId.value) return
  const res = await groupApi.getDetail(currentGroupId.value)
  if (res.code === 0) {
    detailData.value = res.data
  }
}

// ===== 成员管理 =====
async function searchUsers() {
  if (!memberSearchKeyword.value.trim()) {
    searchedUsers.value = []
    return
  }
  try {
    const res = await userApi.list({ keyword: memberSearchKeyword.value, page: 1, pageSize: 20 })
    if (res.code === 0) {
      const existingIds = detailData.value?.members.map(m => m.userId) || []
      searchedUsers.value = res.data.list.filter(u => !existingIds.includes(u.id))
    }
  } catch (e) {
    console.error('Search users failed', e)
  }
}

function toggleUserSelection(user: UserVO) {
  const idx = selectedUserIds.value.indexOf(user.id)
  if (idx >= 0) {
    selectedUserIds.value.splice(idx, 1)
  } else {
    selectedUserIds.value.push(user.id)
  }
}

async function submitAddMembers() {
  try {
    await groupApi.addMembers(currentGroupId.value, selectedUserIds.value)
    Message.success(`已添加 ${selectedUserIds.value.length} 名成员`)
    showAddMemberDialog.value = false
    selectedUserIds.value = []
    memberSearchKeyword.value = ''
    searchedUsers.value = []
    await reloadDetail()
    await loadGroups()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '添加失败')
  }
}

async function removeMember(member: { userId: string; displayName: string }) {
  try {
    await groupApi.removeMembers(currentGroupId.value, [member.userId])
    Message.success(`已移除 ${member.displayName}`)
    await reloadDetail()
    await loadGroups()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '移除失败')
  }
}

// ===== 角色管理 =====
async function loadRolesAndProjects() {
  try {
    const [rolesRes, projectsRes] = await Promise.all([
      (await import('@/api/request')).default.get<any, any>('/roles'),
      projectApi.list({ page: 1, pageSize: 100 })
    ])
    if (rolesRes.code === 0) {
      availableRoles.value = rolesRes.data.list || rolesRes.data || []
    }
    if (projectsRes.code === 0) {
      availableProjects.value = projectsRes.data.list
    }
  } catch (e) {
    console.error('Load roles/projects failed', e)
  }
}

function onRoleChange() {
  const role = availableRoles.value.find((r: any) => String(r.id) === roleFormData.roleId)
  selectedRoleType.value = role?.roleType || ''
  roleFormData.scopeMode = 'global'
  roleFormData.selectedProjectIds = []
}

async function submitAssignRole() {
  try {
    const isProjectRole = selectedRoleType.value === 'project'
    const isGlobalScope = isProjectRole && roleFormData.scopeMode === 'global'
    const projectIds = isProjectRole && roleFormData.scopeMode === 'projects'
      ? roleFormData.selectedProjectIds
      : undefined

    await groupApi.assignRole(currentGroupId.value, {
      roleId: roleFormData.roleId,
      globalScope: isGlobalScope || undefined,
      projectIds: projectIds
    })

    const scopeLabel = isGlobalScope ? '（所有项目）' :
      projectIds ? `（${projectIds.length} 个项目）` : ''
    Message.success(`角色已分配${scopeLabel}`)
    showAddRoleDialog.value = false
    roleFormData.roleId = ''
    roleFormData.scopeMode = 'global'
    roleFormData.selectedProjectIds = []
    selectedRoleType.value = ''
    await reloadDetail()
    await loadGroups()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '分配失败')
  }
}

async function removeRole(role: { id: string; roleName: string }) {
  try {
    await groupApi.removeRole(currentGroupId.value, role.id)
    Message.success(`已移除角色 ${role.roleName}`)
    await reloadDetail()
    await loadGroups()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '移除失败')
  }
}

// ===== 删除 =====
function confirmDelete(group: UserGroupVO) {
  deletingGroup.value = group
  showDeleteDialog.value = true
}

async function executeDelete() {
  if (!deletingGroup.value) return
  try {
    await groupApi.delete(deletingGroup.value.id)
    Message.success('用户组已删除')
    showDeleteDialog.value = false
    deletingGroup.value = null
    await loadGroups()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
  }
}



// ===== 表格列配置 =====
const tableColumns: ColumnDef[] = [
  { type: 'text', title: '名称', key: 'name', width: 200 },
  { type: 'text', title: '描述', key: 'description', ellipsis: true },
  { type: 'count', title: '成员数', key: 'memberCount', width: 90, unit: '人' },
  { type: 'count', title: '角色数', key: 'roleCount', width: 90, unit: '个' },
  { type: 'date', title: '创建时间', key: 'createdAt', width: 160, format: 'datetime' },
  {
    type: 'actions',
    width: 180,
    actions: (record: any) => [
      { label: '详情', onClick: (r) => openDetail(r) },
      { label: '编辑', onClick: (r) => openEditDialog(r) },
      { label: '删除', danger: true, onClick: (r) => confirmDelete(r) },
    ],
  },
]

onMounted(async () => {
  loadStats()
  await loadGroups()
  await loadRolesAndProjects()
})


</script>

<style scoped>
/* Detail panel */
.detail-body { padding: 0; }
.detail-section { padding: 16px 0; border-bottom: 1px solid var(--tf-border-light); }
.detail-section:last-child { border-bottom: none; }
.detail-desc { font-size: 13px; color: var(--tf-text-secondary); margin: 0; }
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.section-header h4 { font-size: 14px; font-weight: 600; color: var(--tf-text-primary); margin: 0; }

/* Member list */
.member-list { display: flex; flex-direction: column; gap: 8px; }
.member-item { display: flex; align-items: center; justify-content: space-between; padding: 8px 12px; background: var(--tf-bg-body); border-radius: 6px; }
.member-info { display: flex; align-items: center; gap: 10px; }
.member-name { font-size: 13px; font-weight: 500; color: var(--tf-text-primary); display: block; }
.member-email { font-size: 11px; color: var(--tf-text-tertiary); }

/* Role list */
.role-list { display: flex; flex-direction: column; gap: 8px; }
.role-item { display: flex; align-items: center; justify-content: space-between; padding: 8px 12px; background: var(--tf-bg-body); border-radius: 6px; }
.role-info { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.role-scope { font-size: 11px; color: var(--tf-text-tertiary); }

/* Scope radio content */
.scope-radio-content { display: flex; flex-direction: column; gap: 2px; }
.scope-label { font-size: 13px; font-weight: 500; color: var(--tf-text-primary); }
.scope-desc { font-size: 11px; color: var(--tf-text-tertiary); }

/* Add member dialog */
.user-search-results { max-height: 240px; overflow-y: auto; margin-top: 8px; border: 1px solid var(--tf-border-light); border-radius: 6px; }
.user-option { display: flex; align-items: center; gap: 8px; padding: 8px 12px; cursor: pointer; transition: background .1s; }
.user-option:hover { background: var(--tf-bg-hover); }
.user-option.selected { background: var(--tf-bg-elevated); }
.user-label { font-size: 13px; color: var(--tf-text-primary); flex: 1; }
.check-mark { color: var(--tf-accent); font-size: 14px; }
.selected-count { margin-top: 8px; font-size: 12px; color: var(--tf-accent); }
</style>
