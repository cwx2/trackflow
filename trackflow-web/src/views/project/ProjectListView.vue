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
          <span class="project-name">{{ project.name }}</span>
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
          <a-dropdown trigger="click" @click.stop>
            <a-button type="text" size="small" class="btn-more">
              <icon-more />
            </a-button>
            <template #content>
              <a-doption @click="editProject(project)">编辑</a-doption>
              <a-doption @click="manageMembers(project)">成员管理</a-doption>
              <a-doption class="danger-option" @click="archiveProject(project)">归档</a-doption>
            </template>
          </a-dropdown>
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
      ok-text="保存"
      cancel-text="取消"
      :ok-loading="editSaving"
      :ok-button-props="{ disabled: !editForm.name }"
      @ok="submitEdit"
    >
      <a-form :model="editForm" layout="vertical">
        <a-form-item label="项目名称" required>
          <a-input v-model="editForm.name" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model="editForm.description" :auto-size="{ minRows: 2, maxRows: 5 }" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 成员管理弹窗 -->
    <a-modal
      v-model:visible="showMembersDialog"
      :title="'成员管理 — ' + (currentProject?.name || '')"
      :width="640"
      :footer="false"
    >
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
        <a-select v-model="addMemberForm.roleId" style="width: 130px">
          <a-option value="2">项目管理员</a-option>
          <a-option value="3">开发人员</a-option>
          <a-option value="4">测试人员</a-option>
          <a-option value="5">观察者</a-option>
        </a-select>
        <a-button type="primary" size="small" @click="addMember" :disabled="!addMemberForm.userId">
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
          <a-table-column title="角色" :width="140">
            <template #cell="{ record }">
              <a-select
                :model-value="record.roleId"
                size="mini"
                @change="(val: any) => changeMemberRole(record.userId, val)"
              >
                <a-option value="2">项目管理员</a-option>
                <a-option value="3">开发人员</a-option>
                <a-option value="4">测试人员</a-option>
                <a-option value="5">观察者</a-option>
              </a-select>
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="80">
            <template #cell="{ record }">
              <a-popconfirm content="确定移除该成员？" @ok="removeMember(record.userId)">
                <a-button type="text" size="mini" status="danger">移除</a-button>
              </a-popconfirm>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import {
  IconPlus,
  IconMore,
  IconFolder
} from '@arco-design/web-vue/es/icon'
import { projectApi, userApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const canCreateProject = computed(() => authStore.hasGlobalPermission('project:create'))

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
  leadId: ''
})

// 成员管理
const showMembersDialog = ref(false)
const currentProject = ref<any>(null)
const projectMembers = ref<any[]>([])
const allUsers = ref<any[]>([])
const addMemberForm = reactive({ userId: undefined as string | undefined, roleId: '3' })
const showAddMember = ref(false)

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
  router.push({ path: '/', query: { project: project.id } })
}

function editProject(project: any) {
  editForm.id = project.id
  editForm.name = project.name
  editForm.description = project.description || ''
  editForm.leadId = project.leadId
  showEditDialog.value = true
}

function manageMembers(project: any) {
  currentProject.value = project
  loadProjectMembers(project.id)
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

async function submitCreate() {
  if (!createForm.name || !createForm.key) return
  creating.value = true
  try {
    await projectApi.create({
      name: createForm.name,
      key: createForm.key,
      description: createForm.description || undefined
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
      description: editForm.description || undefined
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

async function addMember() {
  if (!addMemberForm.userId || !currentProject.value) return
  try {
    await projectApi.addMember(currentProject.value.id, {
      userId: addMemberForm.userId,
      roleId: Number(addMemberForm.roleId)
    })
    Message.success('成员添加成功')
    addMemberForm.userId = undefined
    showAddMember.value = false
    loadProjectMembers(currentProject.value.id)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '添加失败')
  }
}

async function removeMember(userId: string) {
  if (!currentProject.value) return
  try {
    await projectApi.removeMember(currentProject.value.id, userId)
    Message.success('成员已移除')
    loadProjectMembers(currentProject.value.id)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '移除失败')
  }
}

async function changeMemberRole(userId: string, roleId: string) {
  if (!currentProject.value) return
  try {
    await projectApi.updateMemberRole(currentProject.value.id, userId, Number(roleId))
    Message.success('角色已更新')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '更新失败')
  }
}

onMounted(() => {
  loadProjects()
})
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
.project-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
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
</style>
