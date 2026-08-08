<template>
  <div class="org-detail-page">
    <!-- 顶部导航 -->
    <div class="page-nav">
      <router-link to="/admin/organizations" class="back-link">
        <span class="back-icon">←</span>
        <span>组织管理</span>
      </router-link>
    </div>

    <DataContainer
      :loading="loading"
      :error="error"
      :is-empty="false"
      :retry="loadOrg"
    >
    <!-- 组织详情 -->
    <template v-if="org">
      <div class="org-header">
        <div class="org-info">
          <h1 class="org-title">{{ org.name }}</h1>
          <code class="org-code">{{ org.code }}</code>
        </div>
      </div>

      <a-tabs v-model:active-key="activeTab" class="org-tabs" size="small">
        <a-tab-pane key="general" title="基本信息">
          <div class="tab-content">
            <div class="info-grid">
              <div class="info-row">
                <span class="info-label">名称</span>
                <div class="info-value editable" @click="startEditName">
                  <span v-if="!editingName">{{ org.name }}</span>
                  <a-input
                    v-else
                    ref="nameInputRef"
                    v-model="editNameValue"
                    size="small"
                    @blur="saveEditName"
                    @keydown.enter="saveEditName"
                    @keydown.escape="cancelEditName"
                  />
                </div>
              </div>
              <div class="info-row">
                <span class="info-label">编码</span>
                <div class="info-value"><code>{{ org.code }}</code></div>
              </div>
              <div class="info-row">
                <span class="info-label">描述</span>
                <div class="info-value editable" @click="startEditDesc">
                  <span v-if="!editingDesc">{{ org.description || '无描述' }}</span>
                  <a-textarea
                    v-else
                    ref="descInputRef"
                    v-model="editDescValue"
                    :auto-size="{ minRows: 2, maxRows: 5 }"
                    size="small"
                    @blur="saveEditDesc"
                    @keydown.escape="cancelEditDesc"
                  />
                </div>
              </div>
              <div class="info-row">
                <span class="info-label">创建时间</span>
                <div class="info-value">{{ formatDateTime(org.createdAt) }}</div>
              </div>
              <div class="info-row">
                <span class="info-label">项目数量</span>
                <div class="info-value">{{ org.projectCount ?? 0 }}</div>
              </div>
            </div>
          </div>
        </a-tab-pane>

        <a-tab-pane key="projects" title="项目">
          <div class="tab-content">
            <div class="tab-toolbar">
              <a-button type="primary" size="small" @click="openAddProjectDialog">
                <template #icon><icon-plus /></template>
                添加项目
              </a-button>
            </div>
            <a-table
              :columns="projectColumns"
              :data="projects"
              :pagination="false"
              :bordered="false"
              row-key="id"
              size="small"
            >
              <template #key="{ record }">
                <code class="code-tag">{{ record.key }}</code>
              </template>
              <template #status="{ record }">
                <span class="status-badge" :class="record.status?.toLowerCase()">
                  {{ record.status || '—' }}
                </span>
              </template>
              <template #actions="{ record }">
                <a-button type="text" size="mini" status="danger" @click="removeProject(record)">
                  移除
                </a-button>
              </template>
              <template #empty>
                <a-empty description="该组织下暂无项目">
                  <template #extra>
                    <a-button type="primary" size="small" @click="openAddProjectDialog">添加项目</a-button>
                  </template>
                </a-empty>
              </template>
            </a-table>
          </div>
        </a-tab-pane>

        <a-tab-pane key="access" title="访问控制">
          <div class="tab-content">
            <div class="access-hint">
              此处授予的权限将自动应用到该组织下的所有项目。
            </div>
            <div class="tab-toolbar">
              <a-button type="primary" size="small" @click="openGrantAccessDialog">
                <template #icon><icon-plus /></template>
                授予访问权限
              </a-button>
            </div>
            <a-table
              :columns="accessColumns"
              :data="accessList"
              :pagination="false"
              :bordered="false"
              row-key="id"
              size="small"
            >
              <template #user="{ record }">
                <span class="user-cell">
                  <span class="user-display-name">{{ record.userDisplayName }}</span>
                  <span class="user-name">@{{ record.userName }}</span>
                </span>
              </template>
              <template #role="{ record }">
                <span class="role-tag">{{ record.roleName }}</span>
              </template>
              <template #createdAt="{ record }">
                <span class="time-text">{{ formatDateTime(record.createdAt) }}</span>
              </template>
              <template #actions="{ record }">
                <a-button type="text" size="mini" status="danger" @click="revokeAccess(record)">
                  撤销
                </a-button>
              </template>
              <template #empty>
                <a-empty description="暂无组织级授权">
                  <template #extra>
                    <p class="empty-state-hint">向组织授予角色后，用户将自动获得组织下所有项目的对应权限</p>
                    <a-button type="primary" size="small" @click="openGrantAccessDialog">授予访问权限</a-button>
                  </template>
                </a-empty>
              </template>
            </a-table>
          </div>
        </a-tab-pane>
      </a-tabs>
    </template>
    </DataContainer>

    <!-- 添加项目弹窗 -->
    <a-modal
      v-model:visible="showAddProjectDialog"
      title="添加项目到组织"
      :width="480"
      @before-ok="submitAddProjects"
      ok-text="添加"
      :ok-button-props="{ disabled: selectedProjectIds.length === 0 }"
    >
      <p class="dialog-hint">选择要加入此组织的项目（仅显示未归属其他组织的项目）</p>
      <a-select
        v-model="selectedProjectIds"
        :options="unassignedProjectOptions"
        placeholder="选择项目"
        multiple
        allow-search
        :max-tag-count="5"
        style="width: 100%"
      />
    </a-modal>

    <!-- 授予访问权限弹窗 -->
    <a-modal
      v-model:visible="showGrantAccessDialog"
      title="授予组织级访问权限"
      :width="480"
      @before-ok="submitGrantAccess"
      ok-text="授予"
      :ok-button-props="{ disabled: !grantForm.userId || !grantForm.roleId }"
    >
      <a-form :model="grantForm" layout="vertical" size="small">
        <a-form-item label="用户" required>
          <a-select
            v-model="grantForm.userId"
            :options="userOptions"
            placeholder="选择用户"
            allow-search
            :filter-option="filterUserOption"
          />
        </a-form-item>
        <a-form-item label="角色" required>
          <a-select
            v-model="grantForm.roleId"
            :options="roleOptions"
            placeholder="选择角色"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import type { TableColumnData } from '@arco-design/web-vue'
import { organizationApi, userApi, roleApi } from '@/api'
import type { OrgDetailVO, OrgProjectVO, OrgAccessVO } from '@/api/organization'
import DataContainer from '@/components/base/DataContainer.vue'

const route = useRoute()
const router = useRouter()

// Basic state
const loading = ref(true)
const error = ref('')
const org = ref<OrgDetailVO | null>(null)
const activeTab = ref('general')

// Projects tab
const projects = ref<OrgProjectVO[]>([])
const showAddProjectDialog = ref(false)
const selectedProjectIds = ref<string[]>([])
const unassignedProjects = ref<OrgProjectVO[]>([])

// Access tab
const accessList = ref<OrgAccessVO[]>([])
const showGrantAccessDialog = ref(false)
const grantForm = reactive({ userId: '', roleId: '' })
const users = ref<any[]>([])
const roles = ref<any[]>([])

// Inline editing
const editingName = ref(false)
const editNameValue = ref('')
const editingDesc = ref(false)
const editDescValue = ref('')
const nameInputRef = ref<any>(null)
const descInputRef = ref<any>(null)

const orgId = computed(() => route.params.id as string)

const projectColumns: TableColumnData[] = [
  { title: 'Key', slotName: 'key', width: 120 },
  { title: '名称', dataIndex: 'name' },
  { title: '状态', slotName: 'status', width: 100 },
  { title: '操作', slotName: 'actions', width: 80 },
]

const accessColumns: TableColumnData[] = [
  { title: '用户', slotName: 'user' },
  { title: '角色', slotName: 'role', width: 180 },
  { title: '授予时间', slotName: 'createdAt', width: 160 },
  { title: '操作', slotName: 'actions', width: 80 },
]

const unassignedProjectOptions = computed(() =>
  unassignedProjects.value.map(p => ({ value: p.id, label: `${p.key} - ${p.name}` }))
)
const userOptions = computed(() =>
  users.value.map(u => ({ value: String(u.id), label: `${u.displayName} (@${u.username})` }))
)
const roleOptions = computed(() =>
  roles.value.map(r => ({ value: String(r.id), label: r.name }))
)

function filterUserOption(inputValue: string, option: any) {
  return option.label.toLowerCase().includes(inputValue.toLowerCase())
}

// Load data
async function loadOrg() {
  loading.value = true
  error.value = ''
  try {
    const res = await organizationApi.getById(orgId.value)
    org.value = res.data
    await loadProjects()
    await loadAccessList()
  } catch (e: any) {
    error.value = e.response?.data?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadProjects() {
  try {
    const res = await organizationApi.getProjects(orgId.value)
    projects.value = res.data || []
  } catch (e) { projects.value = [] }
}

async function loadAccessList() {
  try {
    const res = await organizationApi.getAccessList(orgId.value)
    accessList.value = res.data || []
  } catch (e) { accessList.value = [] }
}

async function loadUnassignedProjects() {
  try {
    const res = await organizationApi.getUnassignedProjects()
    unassignedProjects.value = res.data || []
  } catch (e) { unassignedProjects.value = [] }
}

async function loadUsersAndRoles() {
  try {
    const [usersRes, rolesRes] = await Promise.all([
      userApi.list({ pageSize: 200 }),
      roleApi.list({ pageSize: 100 })
    ])
    users.value = usersRes.data?.list || []
    roles.value = rolesRes.data?.list || []
  } catch (e) {
    users.value = []
    roles.value = []
  }
}

// Inline editing
function startEditName() {
  editNameValue.value = org.value?.name || ''
  editingName.value = true
  nextTick(() => nameInputRef.value?.focus())
}
function cancelEditName() { editingName.value = false }
async function saveEditName() {
  const newName = editNameValue.value.trim()
  if (!newName || newName === org.value?.name) {
    editingName.value = false
    return
  }
  try {
    await organizationApi.update(orgId.value, { name: newName })
    org.value!.name = newName
    Message.success('名称已更新')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '更新失败')
  }
  editingName.value = false
}

function startEditDesc() {
  editDescValue.value = org.value?.description || ''
  editingDesc.value = true
  nextTick(() => descInputRef.value?.focus())
}
function cancelEditDesc() { editingDesc.value = false }
async function saveEditDesc() {
  const newDesc = editDescValue.value.trim()
  if (newDesc === (org.value?.description || '')) {
    editingDesc.value = false
    return
  }
  try {
    await organizationApi.update(orgId.value, { description: newDesc })
    org.value!.description = newDesc
    Message.success('描述已更新')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '更新失败')
  }
  editingDesc.value = false
}

// Projects management
function openAddProjectDialog() {
  selectedProjectIds.value = []
  loadUnassignedProjects()
  showAddProjectDialog.value = true
}

async function submitAddProjects(done?: (closed: boolean) => void) {
  try {
    await organizationApi.addProjects(orgId.value, selectedProjectIds.value)
    Message.success('项目已添加')
    if (done) done(true)
    loadProjects()
    // Update project count
    if (org.value) org.value.projectCount = (org.value.projectCount || 0) + selectedProjectIds.value.length
  } catch (e: any) {
    Message.error(e.response?.data?.message || '添加失败')
    if (done) done(false)
  }
}

function removeProject(project: OrgProjectVO) {
  const { confirmDelete } = useConfirmDelete()
  confirmDelete({
    itemName: `项目「${project.name}」`,
    confirmText: '移除',
    onConfirm: async () => {
      try {
        await organizationApi.removeProject(orgId.value, project.id)
        Message.success('项目已移除')
        loadProjects()
        if (org.value && org.value.projectCount) org.value.projectCount--
      } catch (e: any) {
        Message.error(e.response?.data?.message || '移除失败')
      }
    }
  })
}

// Access management
function openGrantAccessDialog() {
  grantForm.userId = ''
  grantForm.roleId = ''
  loadUsersAndRoles()
  showGrantAccessDialog.value = true
}

async function submitGrantAccess(done?: (closed: boolean) => void) {
  try {
    await organizationApi.grantAccess(orgId.value, {
      userId: grantForm.userId,
      roleId: grantForm.roleId
    })
    Message.success('权限已授予')
    if (done) done(true)
    loadAccessList()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '授权失败')
    if (done) done(false)
  }
}

function revokeAccess(access: OrgAccessVO) {
  const { confirmDelete } = useConfirmDelete()
  confirmDelete({
    itemName: `${access.userDisplayName} 的「${access.roleName}」角色`,
    confirmText: '撤销',
    onConfirm: async () => {
      try {
        await organizationApi.revokeAccess(orgId.value, access.id)
        Message.success('授权已撤销')
        loadAccessList()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '撤销失败')
      }
    }
  })
}

function formatDateTime(dt: string | undefined) {
  if (!dt) return '—'
  return new Date(dt).toLocaleString('zh-CN')
}

onMounted(loadOrg)
</script>

<style scoped>
.org-detail-page { padding: 24px; height: 100%; display: flex; flex-direction: column; overflow-y: auto; }
.page-nav { margin-bottom: 16px; flex-shrink: 0; }
.back-link { display: inline-flex; align-items: center; gap: 6px; color: var(--text-secondary); font-size: 13px; text-decoration: none; transition: color 150ms; }
.back-link:hover { color: var(--accent-blue); }
.back-icon { font-size: 16px; }

.loading-state, .error-state { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; padding: 48px; color: var(--text-secondary); }
.error-icon { font-size: 36px; }
.error-state h3 { margin: 0; color: var(--text-primary); }
.error-state p { margin: 0; color: var(--text-secondary); }
.error-actions { display: flex; gap: 8px; margin-top: 8px; }

.org-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.org-info { display: flex; align-items: baseline; gap: 12px; }
.org-title { font-size: 20px; font-weight: 600; color: var(--text-bright); margin: 0; }
.org-code { font-size: 12px; padding: 2px 8px; background: var(--bg-tertiary); border-radius: var(--radius-sm); color: var(--accent-blue); }

.org-tabs { flex: 1; }
.org-tabs :deep(.arco-tabs-content) { padding-top: 16px; }

.tab-content { }
.tab-toolbar { margin-bottom: 16px; }
.dialog-hint { color: var(--text-secondary); font-size: 13px; margin-bottom: 12px; }
.access-hint { color: var(--text-tertiary); font-size: 12px; margin-bottom: 12px; padding: 8px 12px; background: var(--bg-tertiary); border-radius: var(--radius-sm); }

/* Info grid */
.info-grid { max-width: 600px; }
.info-row { display: flex; align-items: flex-start; padding: 10px 0; border-bottom: 1px solid var(--border-light); }
.info-row:last-child { border-bottom: none; }
.info-label { flex-shrink: 0; width: 100px; font-size: 13px; color: var(--text-tertiary); font-weight: 500; }
.info-value { flex: 1; font-size: 13px; color: var(--text-primary); }
.info-value.editable { cursor: pointer; padding: 2px 4px; margin: -2px -4px; border-radius: var(--radius-sm); transition: background 150ms; }
.info-value.editable:hover { background: var(--bg-hover); }
.info-value code { font-size: 12px; padding: 2px 6px; background: var(--bg-tertiary); border-radius: var(--radius-sm); }

/* Table cells */
.code-tag { font-size: 11px; background: var(--bg-tertiary); padding: 2px 6px; border-radius: var(--radius-sm); color: var(--accent-blue); }
.status-badge { font-size: 11px; padding: 2px 8px; border-radius: 10px; background: var(--bg-tertiary); color: var(--text-secondary); text-transform: capitalize; }
.status-badge.active { background: var(--tf-success-bg); color: var(--color-success-light); }

.user-cell { display: flex; flex-direction: column; gap: 2px; }
.user-display-name { font-size: 13px; font-weight: 500; color: var(--text-primary); }
.user-name { font-size: 11px; color: var(--text-tertiary); }
.role-tag { font-size: 12px; padding: 2px 8px; background: var(--bg-tertiary); border-radius: var(--radius-sm); color: var(--text-secondary); }
.time-text { font-size: 12px; color: var(--text-secondary); }
.empty-state-hint { color: var(--text-tertiary); margin-bottom: 12px; font-size: 13px; }
</style>
