<template>
  <div class="admin-page">
    <div class="page-header">
      <h2 class="page-title">用户组管理</h2>
      <button class="btn-create" @click="openCreateDialog">+ 新建用户组</button>
    </div>

    <!-- 搜索 -->
    <div class="search-bar">
      <input v-model="keyword" class="form-input search-input" placeholder="搜索用户组..." @input="debouncedSearch" />
    </div>

    <!-- 用户组列表 -->
    <div class="data-table">
      <div class="table-header">
        <div class="col" style="width:200px">名称</div>
        <div class="col" style="flex:1">描述</div>
        <div class="col" style="width:90px">成员数</div>
        <div class="col" style="width:90px">角色数</div>
        <div class="col" style="width:160px">创建时间</div>
        <div class="col" style="width:200px">操作</div>
      </div>
      <div class="table-body">
        <div v-if="loading" class="table-empty">加载中...</div>
        <div v-else-if="groups.length === 0" class="table-empty">
          <div class="empty-icon">👥</div>
          <p class="empty-title">暂无用户组</p>
          <p class="empty-desc">创建用户组来批量管理团队权限</p>
          <button class="btn-create-sm" @click="openCreateDialog">创建用户组</button>
        </div>
        <div v-for="group in groups" :key="group.id" class="table-row" @click="openDetail(group)">
          <div class="col" style="width:200px">
            <span class="group-name">{{ group.name }}</span>
          </div>
          <div class="col" style="flex:1">{{ group.description || '—' }}</div>
          <div class="col" style="width:90px">
            <span class="count-badge">{{ group.memberCount }} 人</span>
          </div>
          <div class="col" style="width:90px">
            <span class="count-badge">{{ group.roleCount }} 个</span>
          </div>
          <div class="col" style="width:160px">{{ formatDate(group.createdAt) }}</div>
          <div class="col" style="width:200px" @click.stop>
            <button class="btn-sm" @click="openDetail(group)">详情</button>
            <button class="btn-sm" @click="openEditDialog(group)">编辑</button>
            <button class="btn-sm danger" @click="confirmDelete(group)">删除</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建/编辑弹窗 -->
    <div class="modal-overlay" v-if="showFormDialog" @click.self="showFormDialog = false">
      <div class="modal-sm">
        <div class="modal-header">
          <h3>{{ editingGroup ? '编辑用户组' : '创建用户组' }}</h3>
          <button class="btn-close" @click="showFormDialog = false">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-row">
            <label class="form-label">名称 *</label>
            <input v-model="formData.name" class="form-input" placeholder="例如：前端团队" />
          </div>
          <div class="form-row">
            <label class="form-label">描述</label>
            <textarea v-model="formData.description" class="form-input form-textarea" placeholder="组的用途说明..." rows="3"></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" @click="showFormDialog = false">取消</button>
          <button class="btn-submit" @click="submitForm" :disabled="!formData.name?.trim()">
            {{ editingGroup ? '保存修改' : '创建用户组' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 详情面板 -->
    <div class="modal-overlay" v-if="showDetailPanel" @click.self="showDetailPanel = false">
      <div class="modal-lg">
        <div class="modal-header">
          <h3>{{ detailData?.name }}</h3>
          <button class="btn-close" @click="showDetailPanel = false">✕</button>
        </div>
        <div class="modal-body detail-body" v-if="detailData">
          <!-- 基本信息 -->
          <div class="detail-section">
            <p class="detail-desc" v-if="detailData.description">{{ detailData.description }}</p>
          </div>

          <!-- 成员管理 -->
          <div class="detail-section">
            <div class="section-header">
              <h4>成员 ({{ detailData.members.length }})</h4>
              <button class="btn-sm accent" @click="showAddMemberDialog = true">+ 添加成员</button>
            </div>
            <div class="member-list" v-if="detailData.members.length > 0">
              <div v-for="member in detailData.members" :key="member.userId" class="member-item">
                <div class="member-info">
                  <span class="member-avatar">{{ (member.displayName || member.username).charAt(0) }}</span>
                  <div>
                    <span class="member-name">{{ member.displayName || member.username }}</span>
                    <span class="member-email">{{ member.email }}</span>
                  </div>
                </div>
                <button class="btn-sm danger" @click="removeMember(member)">移除</button>
              </div>
            </div>
            <div v-else class="empty-section">暂无成员</div>
          </div>

          <!-- 角色分配 -->
          <div class="detail-section">
            <div class="section-header">
              <h4>角色分配 ({{ detailData.roles.length }})</h4>
              <button class="btn-sm accent" @click="showAddRoleDialog = true">+ 分配角色</button>
            </div>
            <div class="role-list" v-if="detailData.roles.length > 0">
              <div v-for="role in detailData.roles" :key="role.id" class="role-item">
                <div class="role-info">
                  <span class="role-name-tag">{{ role.roleName }}</span>
                  <span class="role-scope" v-if="role.projectId">
                    项目: {{ role.projectName }} ({{ role.projectKey }})
                  </span>
                  <span class="role-scope global" v-else>全局</span>
                </div>
                <button class="btn-sm danger" @click="removeRole(role)">移除</button>
              </div>
            </div>
            <div v-else class="empty-section">暂无角色分配</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 添加成员弹窗 -->
    <div class="modal-overlay" v-if="showAddMemberDialog" @click.self="showAddMemberDialog = false">
      <div class="modal-sm">
        <div class="modal-header">
          <h3>添加成员</h3>
          <button class="btn-close" @click="showAddMemberDialog = false">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-row">
            <label class="form-label">搜索用户</label>
            <input v-model="memberSearchKeyword" class="form-input" placeholder="输入用户名或姓名..." @input="searchUsers" />
          </div>
          <div class="user-search-results" v-if="searchedUsers.length > 0">
            <div
              v-for="user in searchedUsers"
              :key="user.id"
              class="user-option"
              :class="{ selected: selectedUserIds.includes(user.id) }"
              @click="toggleUserSelection(user)"
            >
              <span class="user-avatar-sm">{{ (user.displayName || user.username).charAt(0) }}</span>
              <span class="user-label">{{ user.displayName || user.username }} ({{ user.username }})</span>
              <span class="check-mark" v-if="selectedUserIds.includes(user.id)">✓</span>
            </div>
          </div>
          <div class="selected-count" v-if="selectedUserIds.length > 0">
            已选择 {{ selectedUserIds.length }} 名用户
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" @click="showAddMemberDialog = false">取消</button>
          <button class="btn-submit" @click="submitAddMembers" :disabled="selectedUserIds.length === 0">
            添加 {{ selectedUserIds.length }} 名成员
          </button>
        </div>
      </div>
    </div>

    <!-- 分配角色弹窗 -->
    <div class="modal-overlay" v-if="showAddRoleDialog" @click.self="showAddRoleDialog = false">
      <div class="modal-sm">
        <div class="modal-header">
          <h3>分配角色</h3>
          <button class="btn-close" @click="showAddRoleDialog = false">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-row">
            <label class="form-label">角色 *</label>
            <select v-model="roleFormData.roleId" class="form-input" @change="onRoleChange">
              <option value="">请选择角色</option>
              <option v-for="role in availableRoles" :key="role.id" :value="role.id">
                {{ role.name }} ({{ role.roleType }})
              </option>
            </select>
          </div>
          <div class="form-row" v-if="selectedRoleType === 'project'">
            <label class="form-label">项目 *</label>
            <select v-model="roleFormData.projectId" class="form-input">
              <option value="">请选择项目</option>
              <option v-for="project in availableProjects" :key="project.id" :value="project.id">
                {{ project.name }} ({{ project.key }})
              </option>
            </select>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" @click="showAddRoleDialog = false">取消</button>
          <button class="btn-submit" @click="submitAssignRole" :disabled="!canSubmitRole">
            分配角色
          </button>
        </div>
      </div>
    </div>

    <!-- 删除确认弹窗 -->
    <div class="modal-overlay" v-if="showDeleteDialog" @click.self="showDeleteDialog = false">
      <div class="modal-sm">
        <div class="modal-header">
          <h3>确认删除</h3>
          <button class="btn-close" @click="showDeleteDialog = false">✕</button>
        </div>
        <div class="modal-body">
          <p>确定要删除用户组 <strong>{{ deletingGroup?.name }}</strong> 吗？</p>
          <p class="warning-text">此操作不可撤销。组内成员将失去通过该组继承的所有权限。</p>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" @click="showDeleteDialog = false">取消</button>
          <button class="btn-submit danger" @click="executeDelete">删除用户组</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { groupApi, userApi, projectApi } from '@/api'
import type { UserGroupVO, UserGroupDetailVO } from '@/api/group'
import type { UserVO, ProjectVO } from '@/api/types'
import { Message } from '@arco-design/web-vue'

// ===== 列表数据 =====
const groups = ref<UserGroupVO[]>([])
const loading = ref(false)
const keyword = ref('')

// ===== 创建/编辑 =====
const showFormDialog = ref(false)
const editingGroup = ref<UserGroupVO | null>(null)
const formData = ref({ name: '', description: '' })

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
const roleFormData = ref({ roleId: '', projectId: '' })
const selectedRoleType = ref('')

// ===== 删除确认 =====
const showDeleteDialog = ref(false)
const deletingGroup = ref<UserGroupVO | null>(null)

const canSubmitRole = computed(() => {
  if (!roleFormData.value.roleId) return false
  if (selectedRoleType.value === 'project' && !roleFormData.value.projectId) return false
  return true
})

let searchTimer: ReturnType<typeof setTimeout> | null = null
function debouncedSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => loadGroups(), 300)
}

async function loadGroups() {
  loading.value = true
  try {
    const res = await groupApi.list({ keyword: keyword.value || undefined, page: 1, pageSize: 100 })
    if (res.code === 0) {
      groups.value = res.data.list
    }
  } catch (e) {
    console.error('Failed to load groups', e)
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingGroup.value = null
  formData.value = { name: '', description: '' }
  showFormDialog.value = true
}

function openEditDialog(group: UserGroupVO) {
  editingGroup.value = group
  formData.value = { name: group.name, description: group.description || '' }
  showFormDialog.value = true
}

async function submitForm() {
  const data = { name: formData.value.name.trim(), description: formData.value.description?.trim() || undefined }
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

async function openDetail(group: UserGroupVO) {
  currentGroupId.value = group.id
  try {
    const res = await groupApi.getDetail(group.id)
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
    const res = await userApi.list({ displayName: memberSearchKeyword.value, page: 1, pageSize: 20 })
    if (res.code === 0) {
      // 过滤掉已是组成员的用户
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
  const role = availableRoles.value.find((r: any) => String(r.id) === roleFormData.value.roleId)
  selectedRoleType.value = role?.roleType || ''
  if (selectedRoleType.value !== 'project') {
    roleFormData.value.projectId = ''
  }
}

async function submitAssignRole() {
  try {
    await groupApi.assignRole(currentGroupId.value, {
      roleId: roleFormData.value.roleId,
      projectId: roleFormData.value.projectId || undefined
    })
    Message.success('角色已分配')
    showAddRoleDialog.value = false
    roleFormData.value = { roleId: '', projectId: '' }
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

// ===== Utils =====
function formatDate(dateStr: string) {
  if (!dateStr) return '—'
  return new Date(dateStr).toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

onMounted(async () => {
  await loadGroups()
  await loadRolesAndProjects()
})
</script>

<style scoped>
.admin-page { padding: 32px; height: 100%; overflow-y: auto; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
.page-title { font-size: 20px; font-weight: 600; color: var(--tf-text-primary); margin: 0; }
.btn-create { height: 36px; padding: 0 16px; background: var(--tf-accent); color: #fff; border: none; border-radius: 6px; font-size: 13px; font-weight: 500; cursor: pointer; transition: opacity .15s; }
.btn-create:hover { opacity: 0.9; }
.search-bar { margin-bottom: 16px; }
.search-input { max-width: 320px; }

/* Table */
.data-table { background: var(--tf-bg-surface); border: 1px solid var(--tf-border-light); border-radius: 8px; overflow: hidden; }
.table-header { display: flex; padding: 10px 16px; background: var(--tf-bg-elevated); border-bottom: 1px solid var(--tf-border-light); }
.table-header .col { font-size: 11px; font-weight: 600; color: var(--tf-text-tertiary); text-transform: uppercase; letter-spacing: 0.5px; }
.table-body { max-height: 600px; overflow-y: auto; }
.table-row { display: flex; align-items: center; padding: 10px 16px; border-bottom: 1px solid var(--tf-border-light); transition: background .1s; cursor: pointer; }
.table-row:hover { background: var(--tf-bg-hover); }
.table-row:last-child { border-bottom: none; }
.col { display: flex; align-items: center; font-size: 13px; color: var(--tf-text-secondary); padding-right: 8px; }
.table-empty { padding: 48px 16px; text-align: center; color: var(--tf-text-tertiary); }
.empty-icon { font-size: 32px; margin-bottom: 12px; }
.empty-title { font-size: 14px; font-weight: 500; color: var(--tf-text-secondary); margin: 0 0 4px; }
.empty-desc { font-size: 12px; color: var(--tf-text-tertiary); margin: 0 0 16px; }
.btn-create-sm { height: 32px; padding: 0 12px; background: var(--tf-accent); color: #fff; border: none; border-radius: 6px; font-size: 12px; cursor: pointer; }
.group-name { font-weight: 500; color: var(--tf-text-primary); }
.count-badge { font-size: 12px; color: var(--tf-text-tertiary); }

/* Buttons */
.btn-sm { height: 28px; padding: 0 10px; background: var(--tf-bg-elevated); border: 1px solid var(--tf-border-light); border-radius: 4px; font-size: 12px; color: var(--tf-text-secondary); cursor: pointer; margin-right: 4px; transition: all .1s; }
.btn-sm:hover { background: var(--tf-bg-hover); color: var(--tf-text-primary); }
.btn-sm.danger { color: var(--tf-text-tertiary); }
.btn-sm.danger:hover { color: var(--color-error); border-color: var(--color-error); }
.btn-sm.accent { background: var(--tf-accent); color: #fff; border-color: transparent; }
.btn-sm.accent:hover { opacity: 0.9; }

/* Modal */
.modal-overlay { position: fixed; inset: 0; z-index: 100; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; }
.modal-sm { width: 440px; max-height: 80vh; background: var(--tf-bg-surface); border-radius: 12px; overflow: hidden; display: flex; flex-direction: column; }
.modal-lg { width: 680px; max-height: 85vh; background: var(--tf-bg-surface); border-radius: 12px; overflow: hidden; display: flex; flex-direction: column; }
.modal-header { display: flex; align-items: center; gap: 12px; padding: 16px 20px; border-bottom: 1px solid var(--tf-border-light); }
.modal-header h3 { font-size: 16px; font-weight: 600; color: var(--tf-text-primary); margin: 0; flex: 1; }
.modal-body { padding: 20px; overflow-y: auto; flex: 1; }
.modal-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 12px 20px; border-top: 1px solid var(--tf-border-light); }
.btn-close { width: 28px; height: 28px; background: none; border: none; font-size: 16px; color: var(--tf-text-tertiary); cursor: pointer; border-radius: 4px; }
.btn-close:hover { background: var(--tf-bg-hover); color: var(--tf-text-primary); }
.btn-cancel { height: 36px; padding: 0 16px; background: var(--tf-bg-elevated); border: 1px solid var(--tf-border-light); border-radius: 6px; font-size: 13px; color: var(--tf-text-secondary); cursor: pointer; }
.btn-submit { height: 36px; padding: 0 16px; background: var(--tf-accent); color: #fff; border: none; border-radius: 6px; font-size: 13px; font-weight: 500; cursor: pointer; }
.btn-submit:disabled { opacity: 0.4; cursor: not-allowed; }
.btn-submit.danger { background: var(--color-error, #e53e3e); }

/* Form */
.form-row { margin-bottom: 16px; }
.form-label { display: block; font-size: 12px; font-weight: 500; color: var(--tf-text-secondary); margin-bottom: 6px; }
.form-input { width: 100%; height: 32px; padding: 0 12px; background: var(--tf-bg-body); border: 1px solid var(--tf-border-light); border-radius: 6px; font-size: 13px; color: var(--tf-text-primary); outline: none; }
.form-input:focus { border-color: var(--tf-accent); }
.form-textarea { height: auto; padding: 8px 12px; resize: vertical; }
select.form-input { cursor: pointer; }

/* Detail panel */
.detail-body { padding: 0; }
.detail-section { padding: 16px 20px; border-bottom: 1px solid var(--tf-border-light); }
.detail-section:last-child { border-bottom: none; }
.detail-desc { font-size: 13px; color: var(--tf-text-secondary); margin: 0; }
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.section-header h4 { font-size: 14px; font-weight: 600; color: var(--tf-text-primary); margin: 0; }
.empty-section { font-size: 13px; color: var(--tf-text-tertiary); padding: 12px 0; }

/* Member list */
.member-list { display: flex; flex-direction: column; gap: 8px; }
.member-item { display: flex; align-items: center; justify-content: space-between; padding: 8px 12px; background: var(--tf-bg-body); border-radius: 6px; }
.member-info { display: flex; align-items: center; gap: 10px; }
.member-avatar { width: 32px; height: 32px; border-radius: 50%; background: var(--tf-accent); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 500; flex-shrink: 0; }
.member-name { font-size: 13px; font-weight: 500; color: var(--tf-text-primary); display: block; }
.member-email { font-size: 11px; color: var(--tf-text-tertiary); }

/* Role list */
.role-list { display: flex; flex-direction: column; gap: 8px; }
.role-item { display: flex; align-items: center; justify-content: space-between; padding: 8px 12px; background: var(--tf-bg-body); border-radius: 6px; }
.role-info { display: flex; align-items: center; gap: 8px; }
.role-name-tag { font-size: 12px; font-weight: 500; padding: 2px 8px; background: var(--tf-bg-elevated); border-radius: 3px; color: var(--tf-text-primary); }
.role-scope { font-size: 11px; color: var(--tf-text-tertiary); }
.role-scope.global { color: var(--tf-accent); }

/* Add member dialog */
.user-search-results { max-height: 240px; overflow-y: auto; margin-top: 8px; border: 1px solid var(--tf-border-light); border-radius: 6px; }
.user-option { display: flex; align-items: center; gap: 8px; padding: 8px 12px; cursor: pointer; transition: background .1s; }
.user-option:hover { background: var(--tf-bg-hover); }
.user-option.selected { background: var(--tf-bg-elevated); }
.user-avatar-sm { width: 24px; height: 24px; border-radius: 50%; background: var(--tf-accent); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 11px; flex-shrink: 0; }
.user-label { font-size: 13px; color: var(--tf-text-primary); flex: 1; }
.check-mark { color: var(--tf-accent); font-weight: 600; }
.selected-count { margin-top: 8px; font-size: 12px; color: var(--tf-accent); }
.warning-text { font-size: 12px; color: var(--color-error, #e53e3e); margin-top: 8px; }
</style>
