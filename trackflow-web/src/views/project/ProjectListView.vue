<template>
  <div class="project-page">
    <!-- 顶部标题栏 -->
    <div class="page-toolbar">
      <h1 class="page-title">项目</h1>
      <div class="toolbar-right">
        <a-input-search
          v-model="searchKeyword"
          placeholder="搜索项目名称..."
          style="width: 200px"
          allow-clear
        />
        <a-button v-if="canCreateProject" type="primary" @click="showCreateDialog = true">
          <template #icon><icon-plus /></template>
          新建项目
        </a-button>
      </div>
    </div>

    <!-- 项目列表 -->
    <div class="project-list">
      <div
        v-for="project in filteredProjects"
        :key="project.id"
        class="project-row"
        @click="goToProject(project)"
      >
        <!-- 项目图标 -->
        <div class="project-icon" :style="{ background: getProjectColor(project) }">
          <span class="icon-text">{{ getProjectAbbr(project) }}</span>
        </div>

        <!-- 项目信息 -->
        <div class="project-info">
          <div class="project-name-row">
            <span class="project-name">{{ project.name }}</span>
            <span v-if="project.visibility && project.visibility !== 'private'" class="visibility-tag" :class="'vis-' + project.visibility">
              {{ project.visibility === 'internal' ? '内部' : '公开' }}
            </span>
          </div>
          <span class="project-desc" v-if="project.description">{{ project.description }}</span>
        </div>

        <!-- 右侧：成员 + 操作 -->
        <div class="project-right">
          <a-avatar-group :size="24" :max-count="3">
            <a-avatar
              v-for="(member, idx) in (project._members || []).slice(0, 4)"
              :key="idx"
              :style="{ backgroundColor: getMemberColor(idx) }"
            >
              {{ member.charAt(0) }}
            </a-avatar>
          </a-avatar-group>
          <!-- 项目操作菜单（按项目级权限 project:edit / project:manage_members 控制显隐） -->
          <span v-if="canManageProject(project)" class="dropdown-wrapper">
            <a-dropdown trigger="click" @click.stop>
              <a-button type="text" size="small" class="btn-more">
                <icon-more />
              </a-button>
              <template #content>
                <a-doption @click="editProject(project)">编辑</a-doption>
                <a-doption @click="manageMembers(project)">成员管理</a-doption>
                <a-doption class="danger-option" @click="archiveProject(project)">归档</a-doption>
                <a-doption v-if="canDeleteProject(project)" class="danger-option" @click="confirmDeleteProject(project)">删除项目</a-doption>
              </template>
            </a-dropdown>
          </span>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="filteredProjects.length === 0 && !loading" class="empty-state">
        <div class="empty-icon">
          <icon-folder />
        </div>
        <template v-if="searchKeyword">
          <h3 class="empty-title">未找到匹配的项目</h3>
          <p class="empty-desc">尝试更换搜索关键词</p>
        </template>
        <template v-else-if="canCreateProject">
          <h3 class="empty-title">还没有项目</h3>
          <p class="empty-desc">创建您的第一个项目，开始管理团队工作</p>
          <a-button type="primary" @click="showCreateDialog = true">创建第一个项目</a-button>
        </template>
        <template v-else>
          <h3 class="empty-title">您还未被分配到任何项目</h3>
          <p class="empty-desc">请联系项目管理员将您添加到相关项目中，或联系系统管理员分配权限</p>
        </template>
      </div>

      <!-- 加载更多 -->
      <div v-if="hasMore" class="load-more" @click="loadMore">
        <a-link>显示更多项目</a-link>
      </div>
    </div>

    <!-- 创建项目弹窗 -->
    <a-modal
      v-model:visible="showCreateDialog"
      title="新建项目"
      :width="520"
      :ok-text="'创建项目'"
      :cancel-text="'取消'"
      :ok-loading="creating"
      :ok-button-props="{ disabled: !createForm.name || !createForm.key }"
      @ok="submitCreate"
      @cancel="showCreateDialog = false"
    >
      <a-form :model="createForm" layout="vertical">
        <a-form-item label="项目名称" required>
          <a-input v-model="createForm.name" placeholder="例如：后端开发" />
        </a-form-item>
        <a-form-item label="项目标识" required extra="用于生成工单编号，如 BE-1, BE-2...">
          <a-input
            v-model="createForm.key"
            placeholder="例如：BE（大写英文缩写）"
            :max-length="10"
            @input="createForm.key = createForm.key.toUpperCase()"
          />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea
            v-model="createForm.description"
            placeholder="可选，简要描述项目用途"
            :auto-size="{ minRows: 2, maxRows: 5 }"
          />
        </a-form-item>
        <a-form-item label="项目模板">
          <a-radio-group v-model="createForm.template" direction="vertical">
            <a-radio value="default">
              <template #radio="{ checked }">
                <div class="template-option" :class="{ selected: checked }">
                  <div class="template-info">
                    <span class="template-name">默认</span>
                    <span class="template-desc">标准软件开发流程，包含常用状态和优先级</span>
                  </div>
                </div>
              </template>
            </a-radio>
            <a-radio value="scrum">
              <template #radio="{ checked }">
                <div class="template-option" :class="{ selected: checked }">
                  <div class="template-info">
                    <span class="template-name">Scrum</span>
                    <span class="template-desc">敏捷开发模式，配合迭代和看板使用</span>
                  </div>
                </div>
              </template>
            </a-radio>
            <a-radio value="kanban">
              <template #radio="{ checked }">
                <div class="template-option" :class="{ selected: checked }">
                  <div class="template-info">
                    <span class="template-name">看板</span>
                    <span class="template-desc">精益管理模式，按阶段流转任务</span>
                  </div>
                </div>
              </template>
            </a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 编辑项目弹窗 -->
    <a-modal
      v-model:visible="showEditDialog"
      title="编辑项目"
      :width="480"
      ok-text="保存修改"
      cancel-text="取消"
      :ok-loading="editSaving"
      :ok-button-props="{ disabled: !editForm.name }"
      @ok="submitEdit"
      @close="editMembers = []"
    >
      <a-form :model="editForm" layout="vertical">
        <a-form-item label="项目名称" required>
          <a-input v-model="editForm.name" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model="editForm.description" :auto-size="{ minRows: 2, maxRows: 5 }" />
        </a-form-item>
        <a-form-item label="项目负责人">
          <a-select
            v-model="editForm.leadId"
            placeholder="选择项目负责人..."
            allow-search
            :loading="editMembersLoading"
            @focus="loadEditMembers"
          >
            <a-option v-for="m in editMembers" :key="m.userId" :value="m.userId">
              {{ m.displayName || m.username }}
              <span v-if="m.email" style="color: var(--tf-text-tertiary); margin-left: 4px; font-size: 11px">{{ m.email }}</span>
            </a-option>
          </a-select>
          <template #extra>
            <span style="font-size: 11px; color: var(--tf-text-tertiary)">
              只能选择当前项目成员。变更后新负责人将自动升级为项目管理员。
            </span>
          </template>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 成员管理弹窗 -->
    <a-modal
      v-model:visible="showMembersDialog"
      :title="'成员管理 — ' + (currentProject?.name || '')"
      :width="700"
      :footer="false"
    >
      <a-tabs default-active-key="members" @change="onMemberTabChange">
        <a-tab-pane key="members" title="成员列表">
          <!-- 添加成员 -->
          <div style="margin-bottom: 16px; display: flex; gap: 8px; align-items: center">
            <a-select
              v-model="addMemberForm.userId"
              placeholder="选择用户..."
              allow-search
              style="flex: 1"
              @focus="loadAllUsers"
            >
              <a-option v-for="u in allUsers" :key="u.id" :value="u.id">
                {{ u.displayName || u.username }} ({{ u.email || '' }})
              </a-option>
            </a-select>
            <a-select
              v-model="addMemberForm.roleIds"
              placeholder="选择角色..."
              multiple
              style="width: 200px"
              @focus="loadProjectRoles"
            >
              <a-option v-for="r in projectRoles" :key="r.id" :value="r.id">
                {{ r.name }}
              </a-option>
            </a-select>
            <a-button type="primary" size="small" @click="addMember" :disabled="!addMemberForm.userId || addMemberForm.roleIds.length === 0">
              添加
            </a-button>
          </div>

          <!-- 成员列表 -->
          <a-table :data="projectMembers" :pagination="false" size="small">
            <template #columns>
              <a-table-column title="用户" data-index="displayName">
                <template #cell="{ record }">
                  {{ record.displayName || record.username }}
                  <span style="color: var(--tf-text-tertiary); margin-left: 4px">{{ record.email }}</span>
                </template>
              </a-table-column>
              <a-table-column title="角色" :width="200">
                <template #cell="{ record }">
                  <a-select
                    :model-value="record.roleIds || [record.roleId]"
                    size="mini"
                    multiple
                    :max-tag-count="2"
                    @change="(val: any) => changeMemberRole(record.userId, val)"
                    @focus="loadProjectRoles"
                  >
                    <a-option v-for="r in projectRoles" :key="r.id" :value="r.id">
                      {{ r.name }}
                    </a-option>
                  </a-select>
                </template>
              </a-table-column>
              <a-table-column title="操作" :width="80">
                <template #cell="{ record }">
                  <a-button type="text" size="mini" status="danger" @click="confirmRemoveMember(record)">移除</a-button>
                </template>
              </a-table-column>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="activity" title="活动日志">
          <div v-if="activityLoading" style="text-align: center; padding: 32px">
            <a-spin />
          </div>
          <div v-else-if="activities.length === 0" class="activity-empty">
            <span style="font-size: 32px">📋</span>
            <p style="color: var(--tf-text-tertiary); margin-top: 8px">暂无活动记录</p>
            <p style="color: var(--tf-text-tertiary); font-size: 12px">成员变动操作将记录在此处</p>
          </div>
          <div v-else class="activity-list">
            <div v-for="act in activities" :key="act.id" class="activity-item">
              <div class="activity-icon">
                <span v-if="act.action === 'add_member'">➕</span>
                <span v-else-if="act.action === 'remove_member'">➖</span>
                <span v-else-if="act.action === 'change_role'">🔄</span>
                <span v-else-if="act.action === 'change_lead'">⭐</span>
                <span v-else>📝</span>
              </div>
              <div class="activity-content">
                <span class="activity-text">{{ formatActivityText(act) }}</span>
                <span class="activity-time">{{ formatRelativeTime(act.createdAt) }}</span>
              </div>
            </div>
            <div v-if="activityHasMore" style="text-align: center; margin-top: 12px">
              <a-button type="text" size="small" @click="loadMoreActivities">加载更多</a-button>
            </div>
          </div>
        </a-tab-pane>
      </a-tabs>
    </a-modal>

    <!-- 删除项目确认弹窗 -->
    <a-modal
      v-model:visible="showDeleteDialog"
      title="删除项目"
      :ok-text="'永久删除'"
      :cancel-text="'取消'"
      :ok-loading="deleting"
      :ok-button-props="{ disabled: deleteConfirmKey !== deleteTarget?.projectKey, status: 'danger' }"
      @ok="submitDelete"
    >
      <div class="delete-confirm-content">
        <div class="delete-warning">
          <icon-exclamation-circle-fill class="warning-icon" />
          <span>此操作不可撤销！项目及其所有数据将被永久删除。</span>
        </div>
        <div v-if="deleteTarget" class="delete-impact">
          <p class="impact-title">即将删除的数据：</p>
          <ul class="impact-list">
            <li>📋 {{ deleteTarget.issueCount }} 个工单<span v-if="deleteTarget.openIssueCount > 0" class="impact-warn">（其中 {{ deleteTarget.openIssueCount }} 个未关闭）</span></li>
            <li>🏃 {{ deleteTarget.sprintCount }} 个 Sprint</li>
            <li>👥 {{ deleteTarget.memberCount }} 名成员</li>
          </ul>
        </div>
        <div class="delete-confirm-input">
          <p>请输入项目标识 <strong>{{ deleteTarget?.projectKey }}</strong> 确认删除：</p>
          <a-input v-model="deleteConfirmKey" placeholder="输入项目标识确认" />
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch, h } from 'vue'
import { useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import {
  IconPlus,
  IconMore,
  IconFolder,
  IconExclamationCircleFill
} from '@arco-design/web-vue/es/icon'
import { projectApi, userApi, workflowApi } from '@/api'
import type { ProjectActivityVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { loadProjectPermissions } from '@/composables/usePermission'

const router = useRouter()
const authStore = useAuthStore()

const canCreateProject = computed(() => authStore.hasGlobalPermission('project:create'))

// ========== 项目级权限（逐项目判断） ==========
const projectPermCache = ref<Record<string, Set<string>>>({})

/**
 * 批量加载所有已显示项目的权限
 * system:admin 跳过（自动有所有权限）
 */
async function loadProjectPermissionsForList() {
  if (authStore.hasGlobalPermission('system:admin')) return

  const projectIds = [...new Set(projects.value.map((p: any) => p.id as string))]
  const uncached = projectIds.filter(pid => !projectPermCache.value[pid])
  if (uncached.length === 0) return

  await Promise.all(uncached.map(async (pid) => {
    const perms = await loadProjectPermissions(pid)
    projectPermCache.value[pid] = perms
  }))
}

/**
 * 判断当前用户是否能管理指定项目（编辑/成员管理/归档）
 * system:admin 直接返回 true
 * 未加载权限时返回 true（乐观策略，后端兜底）
 */
function canManageProject(project: any): boolean {
  if (authStore.hasGlobalPermission('system:admin')) return true
  const perms = projectPermCache.value[project.id]
  if (!perms) return true // 未加载时默认允许，后端兜底
  return perms.has('project:edit') || perms.has('project:manage_members')
}

/**
 * 判断当前用户是否能删除指定项目（需要 project:delete 权限）
 */
function canDeleteProject(project: any): boolean {
  if (authStore.hasGlobalPermission('system:admin')) return true
  const perms = projectPermCache.value[project.id]
  if (!perms) return false // 删除是高危操作，未加载权限时默认不允许
  return perms.has('project:delete')
}

const projects = ref<any[]>([])
const loading = ref(false)
const creating = ref(false)
const searchKeyword = ref('')
const showCreateDialog = ref(false)
const hasMore = ref(false)
const page = ref(1)
const pageSize = 20

const createForm = reactive({
  name: '',
  key: '',
  description: '',
  template: 'default'
})

// 编辑项目
const showEditDialog = ref(false)
const editSaving = ref(false)
const editForm = reactive({
  id: '',
  name: '',
  description: '',
  leadId: '' as string | undefined
})
const editMembers = ref<any[]>([])
const editMembersLoading = ref(false)

// 成员管理
const showMembersDialog = ref(false)
const currentProject = ref<any>(null)

// 删除项目
const showDeleteDialog = ref(false)
const deleting = ref(false)
const deleteConfirmKey = ref('')
const deleteTarget = ref<{
  id: string
  projectName: string
  projectKey: string
  issueCount: number
  sprintCount: number
  memberCount: number
  openIssueCount: number
} | null>(null)
const projectMembers = ref<any[]>([])
const allUsers = ref<any[]>([])
const projectRoles = ref<{ id: string; name: string }[]>([])
const addMemberForm = reactive({ userId: undefined as string | undefined, roleIds: [] as string[] })
const showAddMember = ref(false)

// 活动日志
const activities = ref<ProjectActivityVO[]>([])
const activityLoading = ref(false)
const activityPage = ref(1)
const activityHasMore = ref(false)

// 项目颜色池
const colorPool = [
  '#e91e63', '#9c27b0', '#673ab7', '#3f51b5', '#2196f3',
  '#00bcd4', '#009688', '#4caf50', '#ff9800', '#ff5722',
  '#795548', '#607d8b'
]

const memberColors = ['#6366f1', '#ec4899', '#14b8a6', '#f59e0b', '#ef4444']

function getProjectColor(project: any) {
  const idx = (project.id || 0) % colorPool.length
  return colorPool[idx]
}

function getProjectAbbr(project: any) {
  return project.key?.substring(0, 3) || project.name?.charAt(0) || '?'
}

function getMemberColor(idx: number) {
  return memberColors[idx % memberColors.length]
}

const filteredProjects = computed(() => {
  if (!searchKeyword.value) return projects.value
  const kw = searchKeyword.value.toLowerCase()
  return projects.value.filter(p =>
    p.name.toLowerCase().includes(kw) ||
    p.key.toLowerCase().includes(kw)
  )
})

async function loadProjects() {
  loading.value = true
  try {
    const res = await projectApi.list({ page: page.value, pageSize })
    const list = res.data?.list || []
    // 模拟成员数据
    list.forEach((p: any) => {
      p._members = ['管', '张', '李'].slice(0, Math.min(3, (p.id % 3) + 1))
    })
    if (page.value === 1) {
      projects.value = list
    } else {
      projects.value.push(...list)
    }
    const total = res.data?.pagination?.total || list.length
    hasMore.value = projects.value.length < total
  } catch (e: any) {
    if (e.response?.status !== 401) {
      Message.error(e.response?.data?.message || '加载项目列表失败')
    }
    projects.value = []
  } finally {
    loading.value = false
  }
}

function loadMore() {
  page.value++
  loadProjects()
}

function goToProject(project: any) {
  router.push({ path: `/projects/${project.id}` })
}

function editProject(project: any) {
  editForm.id = project.id
  editForm.name = project.name
  editForm.description = project.description || ''
  editForm.leadId = project.leadId || undefined
  editMembers.value = []
  showEditDialog.value = true
  // 自动加载成员列表
  loadEditMembers()
}

async function loadEditMembers() {
  if (editMembers.value.length > 0 || !editForm.id) return
  editMembersLoading.value = true
  try {
    const res = await projectApi.listMembers(editForm.id)
    editMembers.value = res.data || []
  } catch {
    editMembers.value = []
  } finally {
    editMembersLoading.value = false
  }
}

function manageMembers(project: any) {
  currentProject.value = project
  activities.value = []
  activityPage.value = 1
  activityHasMore.value = false
  loadProjectMembers(project.id)
  loadProjectRoles()
  showMembersDialog.value = true
}

function archiveProject(project: any) {
  Modal.warning({
    title: '归档项目',
    content: `确定归档项目「${project.name}」？归档后可在管理中恢复。`,
    okText: '确定归档',
    cancelText: '取消',
    hideCancel: false,
    onOk: async () => {
      try {
        await projectApi.archive(project.id)
        projects.value = projects.value.filter(p => p.id !== project.id)
        Message.success('项目已归档')
      } catch (e: any) {
        Message.error(e.response?.data?.message || '归档失败')
      }
    }
  })
}

async function confirmDeleteProject(project: any) {
  // 调用预检查接口获取受影响数据
  try {
    const res = await projectApi.deletePreCheck(project.id)
    if (res.code === 0 && res.data) {
      deleteTarget.value = { id: project.id, ...res.data }
      deleteConfirmKey.value = ''
      showDeleteDialog.value = true
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '无法获取项目信息')
  }
}

async function submitDelete() {
  if (!deleteTarget.value) return
  if (deleteConfirmKey.value !== deleteTarget.value.projectKey) return
  deleting.value = true
  try {
    await projectApi.delete(deleteTarget.value.id, deleteConfirmKey.value)
    showDeleteDialog.value = false
    projects.value = projects.value.filter(p => p.id !== deleteTarget.value!.id)
    Message.success('项目已永久删除')
    deleteTarget.value = null
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
  } finally {
    deleting.value = false
  }
}

async function submitCreate() {
  if (!createForm.name || !createForm.key) return
  creating.value = true
  try {
    await projectApi.create({
      name: createForm.name,
      key: createForm.key,
      description: createForm.description || undefined,
      template: createForm.template || 'default'
    })
    showCreateDialog.value = false
    createForm.name = ''
    createForm.key = ''
    createForm.description = ''
    createForm.template = 'default'
    Message.success('项目创建成功')
    page.value = 1
    loadProjects()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

// ========== 编辑项目 ==========
async function submitEdit() {
  if (!editForm.name) return
  editSaving.value = true
  try {
    await projectApi.update(editForm.id, {
      name: editForm.name,
      description: editForm.description || undefined,
      leadId: editForm.leadId || undefined
    })
    showEditDialog.value = false
    Message.success('项目更新成功')
    page.value = 1
    loadProjects()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '更新失败')
  } finally {
    editSaving.value = false
  }
}

// ========== 成员管理 ==========
async function loadProjectMembers(projectId: string) {
  try {
    const res = await projectApi.listMembers(projectId)
    projectMembers.value = res.data || []
  } catch (e) {
    projectMembers.value = []
  }
}

async function loadAllUsers() {
  if (allUsers.value.length > 0) return
  try {
    const res = await userApi.list({ pageSize: 200 })
    allUsers.value = res.data?.list || []
  } catch (e) {
    allUsers.value = []
  }
}

async function loadProjectRoles() {
  if (projectRoles.value.length > 0) return
  try {
    const res = await workflowApi.listProjectRoles()
    projectRoles.value = (res.data || []).map((r: any) => ({ id: String(r.id), name: r.name }))
  } catch (e) {
    projectRoles.value = []
  }
}

async function addMember() {
  if (!addMemberForm.userId || !currentProject.value || addMemberForm.roleIds.length === 0) return
  try {
    await projectApi.addMember(currentProject.value.id, {
      userId: addMemberForm.userId,
      roleIds: addMemberForm.roleIds.map(Number)
    })
    Message.success('成员添加成功')
    addMemberForm.userId = undefined
    addMemberForm.roleIds = []
    showAddMember.value = false
    loadProjectMembers(currentProject.value.id)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '添加失败')
  }
}

async function confirmRemoveMember(record: any) {
  if (!currentProject.value) return
  try {
    // 预检：查询该成员被分配的工单数量
    const res = await projectApi.getAssignedIssueCount(currentProject.value.id, record.userId)
    const count = res.data?.count || 0
    const memberName = record.displayName || record.username

    const content = count > 0
      ? () => h('div', [
          h('p', { style: 'margin: 0 0 12px 0' }, `该成员当前负责 ${count} 个工单，移除后将自动取消这些工单的负责人分配。`),
          h('p', { style: 'margin: 0' }, `确定移除成员「${memberName}」？`)
        ])
      : `确定移除成员「${memberName}」？`

    Modal.warning({
      title: '移除项目成员',
      content,
      okText: '确定移除',
      cancelText: '取消',
      hideCancel: false,
      onOk: () => removeMember(record.userId)
    })
  } catch (e: any) {
    // 预检失败时退回简单确认
    Modal.warning({
      title: '移除项目成员',
      content: `确定移除成员「${record.displayName || record.username}」？`,
      okText: '确定移除',
      cancelText: '取消',
      hideCancel: false,
      onOk: () => removeMember(record.userId)
    })
  }
}

async function removeMember(userId: string) {
  if (!currentProject.value) return
  try {
    const res = await projectApi.removeMember(currentProject.value.id, userId)
    const affectedCount = res.data?.affectedIssueCount || 0
    if (affectedCount > 0) {
      Message.success(`成员已移除，${affectedCount} 个工单的负责人已自动取消分配`)
    } else {
      Message.success('成员已移除')
    }
    loadProjectMembers(currentProject.value.id)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '移除失败')
  }
}

async function changeMemberRole(userId: string, roleIds: string[]) {
  if (!currentProject.value) return
  try {
    await projectApi.updateMemberRole(currentProject.value.id, userId, roleIds.map(Number))
    Message.success('角色已更新')
    loadProjectMembers(currentProject.value.id)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '更新失败')
    // 刷新列表恢复正确状态
    loadProjectMembers(currentProject.value.id)
  }
}

// ========== 活动日志 ==========

function onMemberTabChange(key: string | number) {
  if (key === 'activity' && activities.value.length === 0) {
    loadActivities()
  }
}

async function loadActivities() {
  if (!currentProject.value) return
  activityLoading.value = true
  activityPage.value = 1
  try {
    const res = await projectApi.listActivities(currentProject.value.id, { page: 1, pageSize: 20 })
    if (res.code === 0 && res.data) {
      activities.value = res.data.list || []
      activityHasMore.value = (res.data.pagination?.page ?? 1) < (res.data.pagination?.totalPages ?? 1)
    }
  } catch {
    activities.value = []
  } finally {
    activityLoading.value = false
  }
}

async function loadMoreActivities() {
  if (!currentProject.value) return
  activityPage.value++
  try {
    const res = await projectApi.listActivities(currentProject.value.id, { page: activityPage.value, pageSize: 20 })
    if (res.code === 0 && res.data) {
      activities.value.push(...(res.data.list || []))
      activityHasMore.value = (res.data.pagination?.page ?? 1) < (res.data.pagination?.totalPages ?? 1)
    }
  } catch {
    // 静默失败
  }
}

function formatActivityText(act: ProjectActivityVO): string {
  const operator = act.userName || '未知用户'
  const target = act.targetUserName || '未知用户'
  let detail: any = {}
  try { detail = act.detail ? JSON.parse(act.detail) : {} } catch { /* ignore */ }

  switch (act.action) {
    case 'add_member':
      return `${operator} 添加了成员 ${target}（角色：${detail.role_name || ''}）`
    case 'remove_member':
      return `${operator} 移除了成员 ${target}`
    case 'change_role':
      return `${operator} 将 ${target} 的角色从「${detail.old_role_name || ''}」变更为「${detail.new_role_name || ''}」`
    case 'change_lead':
      return `${operator} 将项目负责人从「${detail.old_lead_name || '未设置'}」变更为「${detail.new_lead_name || ''}」`
    default:
      return `${operator} 执行了操作 ${act.action}`
  }
}

function formatRelativeTime(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  return date.toLocaleDateString('zh-CN')
}

onMounted(() => {
  loadProjects()
})

// 项目列表变化时，加载对应项目的权限
watch(projects, () => {
  loadProjectPermissionsForList()
}, { flush: 'post' })
</script>

<style scoped>
.project-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.page-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 24px 16px;
  flex-shrink: 0;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--tf-text-primary);
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 项目列表 */
.project-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 24px 24px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.project-row {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: var(--tf-bg-surface);
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, box-shadow 0.15s;
}
.project-row:hover {
  background: var(--tf-bg-hover);
  border-color: var(--tf-border);
  box-shadow: var(--tf-shadow);
}

.project-icon {
  width: 36px;
  height: 36px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-right: 16px;
}
.icon-text {
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  text-transform: uppercase;
}

.project-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.project-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.project-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
}
.visibility-tag {
  font-size: 10px;
  font-weight: 500;
  padding: 1px 6px;
  border-radius: 3px;
  white-space: nowrap;
}
.visibility-tag.vis-internal {
  background: rgba(88, 166, 255, 0.1);
  color: var(--tf-accent);
}
.visibility-tag.vis-public {
  background: rgba(63, 185, 80, 0.1);
  color: #3fb950;
}
.project-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.btn-more {
  color: var(--tf-text-tertiary);
  transition: color 0.15s;
}
.btn-more:hover {
  color: var(--tf-text-primary);
}

.dropdown-wrapper {
  display: inline-flex;
}

.load-more {
  text-align: center;
  padding: 16px 0 32px;
}

/* 模板选项样式 */
.template-option {
  padding: 8px 12px;
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: border-color 0.15s, background 0.15s;
}
.template-option:hover {
  border-color: var(--tf-accent);
}
.template-option.selected {
  border-color: var(--tf-accent);
  background: var(--tf-accent-bg);
}
.template-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.template-name {
  font-size: 13px;
  color: var(--tf-text-primary);
  font-weight: 500;
}
.template-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

/* Arco Design 暗色主题适配 */
:deep(.arco-dropdown-option.danger-option) {
  color: var(--tf-danger);
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
  margin-bottom: 16px;
  opacity: 0.6;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 8px;
}

.empty-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0 0 24px;
  max-width: 360px;
  line-height: 1.5;
}

/* ===== 活动日志 ===== */
.activity-empty {
  text-align: center;
  padding: 48px 16px;
}

.activity-list {
  max-height: 400px;
  overflow-y: auto;
}

.activity-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border-1);
}

.activity-item:last-child {
  border-bottom: none;
}

.activity-icon {
  font-size: 16px;
  flex-shrink: 0;
  width: 24px;
  text-align: center;
}

.activity-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.activity-text {
  font-size: 13px;
  color: var(--tf-text-primary);
  line-height: 1.5;
}

.activity-time {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

/* 删除项目确认弹窗 */
.delete-confirm-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.delete-warning {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: rgba(248, 81, 73, 0.08);
  border: 1px solid rgba(248, 81, 73, 0.2);
  border-radius: var(--tf-radius-md);
  font-size: 13px;
  color: var(--tf-danger);
}

.delete-warning .warning-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.delete-impact {
  font-size: 13px;
  color: var(--tf-text-secondary);
}

.impact-title {
  margin: 0 0 8px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.impact-list {
  margin: 0;
  padding-left: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.impact-list li {
  font-size: 13px;
}

.impact-warn {
  color: var(--tf-danger);
  font-weight: 500;
}

.delete-confirm-input {
  font-size: 13px;
  color: var(--tf-text-secondary);
}

.delete-confirm-input p {
  margin: 0 0 8px;
}

.delete-confirm-input strong {
  color: var(--tf-text-primary);
  font-weight: 600;
}
</style>
