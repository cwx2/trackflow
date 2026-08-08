<template>
  <div class="sprint-page">
    <div class="page-header">
      <h2 class="page-title">迭代管理</h2>
      <div class="header-actions">
        <a-select
          v-model="selectedProject"
          placeholder="选择项目"
          style="width: 200px"
          size="small"
          allow-search
          allow-clear
          :loading="projectLoadState === 'loading'"
          @change="handleProjectChange"
        >
          <template v-if="projectLoadState === 'error'" #empty>
            <div class="select-error-state">
              <span>加载失败</span>
              <a-link @click.stop="loadProjects">重试</a-link>
            </div>
          </template>
          <a-option v-for="p in projects" :key="p.id" :value="p.id">
            {{ p.key }} - {{ p.name }}
          </a-option>
        </a-select>
        <a-button v-if="canCreateSprint" type="primary" size="small" :disabled="!selectedProject" @click="openCreateModal">
          + 新建迭代
        </a-button>
        <a-button v-if="canEditSprint" size="small" :disabled="!selectedProject" @click="router.push('/sprint-planning')">
          📋 规划
        </a-button>
      </div>
    </div>

    <!-- Sprint 列表 -->
    <div class="sprint-list" v-if="loadingState === 'success' && sprints.length > 0">

      <!-- Sprint 导引横幅 -->
      <SprintGuidanceBanner
        :show="plannedSprints.length > 0"
        :has-active-sprint="hasActiveSprint"
        :message="sprintGuidanceMessage"
        :activate-tooltip="warningBarActivateTooltip"
        :can-activate="!!nextStartableSprint && canEditSprintItem(nextStartableSprint)"
        @activate="nextStartableSprint && handleActivateSprint(nextStartableSprint.id)"
      />

      <!-- Active Sprints -->
      <SprintCard
        v-for="sprint in activeSprints"
        :key="sprint.id"
        :sprint="sprint"
        :show-project-badge="!selectedProject"
        :project-key="selectedProjectKey"
        :can-edit="canEditSprintItem(sprint)"
        :can-delete="false"
        :has-active-sprint="hasActiveSprint"
        :inline-editable="true"
        @view-issues="viewSprintIssues"
        @view-on-board="viewSprintOnBoard"
        @view-category="viewIssuesByCategory"
        @view-total="openIssueDrawer"
        @view-overdue="viewOverdueIssues"
        @view-unassigned="viewUnassignedIssues"
        @view-issues-filtered="(s, f) => openIssueDrawer(s, f)"
        @edit="openEditModal"
        @edit-dates="openEditModal"
        @complete="handleCompleteSprint"
        @archive="handleArchiveActiveSprint"
        @revert-to-planned="handleRevertToPlanned"
        @inline-rename="inlineRenameSprint"
      />

      <!-- Planned Sprints -->
      <SprintCard
        v-for="sprint in plannedSprints"
        :key="sprint.id"
        :sprint="sprint"
        :show-project-badge="!selectedProject"
        :project-key="selectedProjectKey"
        :can-edit="canEditSprintItem(sprint)"
        :can-delete="canDeleteSprintItem(sprint)"
        :is-next="!hasActiveSprint && sprint.id === nextPlannedSprintId"
        :has-active-sprint="hasActiveSprint"
        :is-startable="!isSprintNotStartable(sprint)"
        :activate-tooltip="getActivateTooltip(sprint)"
        :inline-editable="true"
        @view-issues="viewSprintIssues"
        @view-on-board="viewSprintOnBoard"
        @view-category="viewIssuesByCategory"
        @view-total="openIssueDrawer"
        @view-overdue="viewOverdueIssues"
        @view-unassigned="viewUnassignedIssues"
        @view-issues-filtered="(s, f) => openIssueDrawer(s, f)"
        @edit="openEditModal"
        @activate="handleActivateSprint"
        @archive-planned="archiveSprint"
        @delete="handleDeleteSprint"
        @inline-rename="inlineRenameSprint"
      />

      <!-- Completed Sprints Section (collapsible) -->
      <div v-if="completedSprints.length > 0" class="completed-section">
        <div class="completed-section-header" @click="showCompletedSprints = !showCompletedSprints">
          <span class="completed-toggle-icon">{{ showCompletedSprints ? '▾' : '▸' }}</span>
          <span class="completed-section-title">已完成</span>
          <span class="completed-section-count">{{ completedSprints.length }}</span>
        </div>
        <template v-if="showCompletedSprints">
          <SprintCard
            v-for="sprint in completedSprints"
            :key="sprint.id"
            :sprint="sprint"
            :show-project-badge="!selectedProject"
            :project-key="selectedProjectKey"
            :can-edit="canEditSprintItem(sprint)"
            :can-delete="false"
            :is-just-completed="sprint.id === justCompletedSprintId"
            :show-burndown="expandedCompletedSprints.has(sprint.id)"
            :inline-editable="false"
            @view-issues="viewSprintIssues"
            @view-on-board="viewSprintOnBoard"
            @view-category="viewIssuesByCategory"
            @view-total="(s) => viewSprintIssues(s)"
            @edit="openEditModal"
            @archive-planned="archiveSprint"
            @toggle-burndown="toggleCompletedBurndown"
          />
        </template>
      </div>

      <!-- Archived Sprints Section (collapsible) -->
      <div v-if="archivedSprints.length > 0" class="completed-section archived-section">
        <div class="completed-section-header" @click="showArchivedSprints = !showArchivedSprints">
          <span class="completed-toggle-icon">{{ showArchivedSprints ? '▾' : '▸' }}</span>
          <span class="completed-section-title">已归档</span>
          <span class="completed-section-count">{{ archivedSprints.length }}</span>
        </div>
        <template v-if="showArchivedSprints">
          <SprintCard
            v-for="sprint in archivedSprints"
            :key="sprint.id"
            :sprint="sprint"
            :show-project-badge="!selectedProject"
            :project-key="selectedProjectKey"
            :can-edit="canEditSprintItem(sprint)"
            :can-delete="false"
            :show-burndown="expandedCompletedSprints.has(sprint.id)"
            :inline-editable="false"
            @view-issues="viewSprintIssues"
            @restore="restoreSprint"
            @toggle-burndown="toggleCompletedBurndown"
          />
        </template>
      </div>
    </div>
    <div v-else-if="loadingState === 'loading'" class="empty-state">
      <a-spin :size="32" />
      <p class="empty-desc" style="margin-top: 16px;">正在加载迭代列表…</p>
    </div>
    <div v-else-if="loadingState === 'error' && !selectedProject" class="empty-state">
      <div class="empty-icon">🏃</div>
      <h3 class="empty-title">请选择一个项目</h3>
      <p class="empty-desc">选择上方的项目后，即可查看和管理该项目的迭代（Sprint）列表</p>
    </div>
    <div v-else-if="loadingState === 'error'" class="empty-state">
      <div class="empty-icon">⚠️</div>
      <h3 class="empty-title">加载失败</h3>
      <p class="empty-desc">无法获取迭代列表，请稍后重试</p>
      <a-button type="primary" size="small" @click="loadSprints">重试</a-button>
    </div>
    <div v-else-if="loadingState === 'forbidden'" class="empty-state">
      <div class="empty-icon">🔒</div>
      <h3 class="empty-title">暂无可查看的迭代</h3>
      <p class="empty-desc">当前项目尚未创建迭代，或您没有查看权限。请联系项目管理员。</p>
    </div>
    <div v-else-if="projectLoadState === 'error'" class="empty-state">
      <div class="empty-icon">⚠️</div>
      <h3 class="empty-title">项目列表加载失败</h3>
      <p class="empty-desc">无法获取可用项目，请检查网络后重试</p>
      <a-button type="primary" size="small" @click="loadProjects">重试</a-button>
    </div>
    <div v-else class="empty-state">
      <div class="empty-icon">🏃</div>
      <h3 class="empty-title">{{ selectedProject ? '暂无迭代' : '请选择一个项目' }}</h3>
      <p class="empty-desc">{{ !selectedProject ? '选择上方的项目后，即可查看和管理该项目的迭代（Sprint）列表' : canCreateSprint ? '创建第一个 Sprint 来规划团队工作' : '当前项目尚未创建迭代，请联系项目管理员。' }}</p>
      <a-button v-if="selectedProject && canCreateSprint" type="primary" size="small" @click="openCreateModal">+ 新建迭代</a-button>
    </div>

    <!-- 创建/编辑/重叠 Sprint 弹窗 -->
    <SprintFormModal
      v-model:show-create="showCreate"
      v-model:show-edit="showEdit"
      :editing-sprint="editingSprint"
      :project-id="selectedProject"
      :can-edit-fn="canEditSprintItem"
      :can-delete-fn="canDeleteSprintItem"
      @created="loadSprints"
      @updated="loadSprints"
      @archive="handleEditModalArchive"
      @restore="handleEditModalRestore"
      @delete="handleEditModalDelete"
    />

    <!-- 完成 Sprint 确认弹窗 -->
    <SprintCompleteModal
      v-model:visible="showCompleteModal"
      :sprint="completingSprint"
      @completed="onSprintCompleted"
    />

    <!-- 删除 Sprint 确认弹窗 -->
    <SprintDeleteModal
      v-model:visible="showDeleteModal"
      :sprint="deletingSprint"
      @deleted="onSprintDeleted"
    />

    <!-- Sprint 内工单快速分配抽屉 -->
    <SprintIssueDrawer
      v-model:visible="showIssueDrawer"
      :sprint-id="drawerSprintId"
      :sprint-name="drawerSprintName"
      :project-id="drawerProjectId"
      :initial-filter="drawerInitialFilter"
      @assigned="handleDrawerAssigned"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { usePermission, canEditSprintSync, canDeleteSprintSync } from '@/composables/usePermission'
import { useProjectList } from '@/composables/useProjectList'
import type { SprintVO } from '@/api/types'
import SprintIssueDrawer from './components/SprintIssueDrawer.vue'
import SprintCard from './components/SprintCard.vue'
import SprintGuidanceBanner from './components/SprintGuidanceBanner.vue'
import SprintCompleteModal from './components/SprintCompleteModal.vue'
import SprintDeleteModal from './components/SprintDeleteModal.vue'
import SprintFormModal from './components/SprintFormModal.vue'
import { useSprintNavigation, formatDate, isSprintNotStartable } from '@/composables/useSprintNavigation'
import { useSprintData } from '@/composables/useSprintData'

const router = useRouter()
const route = useRoute()
const projectStore = useProjectStore()

const selectedProject = computed({
  get: () => projectStore.selectedProjectId,
  set: (val) => projectStore.selectProject(val)
})

// 权限控制（必须在 selectedProject 定义之后）
// 页面级权限控制（用于创建按钮等需要选择项目的场景）
const { canCreateSprint, canEditSprint } = usePermission(() => selectedProject.value)

/**
 * 基于 Sprint 自身的 projectId 检查编辑权限
 * 用于 Sprint 卡片的操作按钮显隐控制
 */
function canEditSprintItem(sprint: SprintVO): boolean {
  return canEditSprintSync(sprint.projectId)
}

/**
 * 基于 Sprint 自身的 projectId 检查删除权限
 */
function canDeleteSprintItem(sprint: SprintVO): boolean {
  return canDeleteSprintSync(sprint.projectId)
}

const { projects, projectLoadState, loadProjects } = useProjectList()
const { viewSprintIssues, viewSprintOnBoard, viewIssuesByCategory, viewOverdueIssues } = useSprintNavigation(selectedProject, projects)
const {
  sprints, loadingState, activeSprints, plannedSprints, completedSprints, archivedSprints,
  hasActiveSprint, nextStartableSprint, loadSprints,
  handleActivateSprint, handleRevertToPlanned, archiveSprint, handleArchiveActiveSprint,
  restoreSprint, inlineRenameSprint
} = useSprintData(selectedProject)

const showCreate = ref(false)
const expandedCompletedSprints = ref<Set<string>>(new Set())
const showCompletedSprints = ref(false)
const showArchivedSprints = ref(false)

// ===== 编辑迭代相关 =====
const showEdit = ref(false)
const editingSprint = ref<SprintVO | null>(null)

// ===== 完成迭代相关 =====
const showCompleteModal = ref(false)
const completingSprint = ref<SprintVO | null>(null)
/** 刚完成的 Sprint ID，用于高亮动画 */
const justCompletedSprintId = ref<string | null>(null)

// ===== 删除迭代相关 =====
const showDeleteModal = ref(false)
const deletingSprint = ref<SprintVO | null>(null)

// ===== Sprint Issue Drawer =====
const showIssueDrawer = ref(false)
const drawerSprintId = ref('')
const drawerSprintName = ref('')
const drawerProjectId = ref<string | undefined>(undefined)
const drawerInitialFilter = ref<'unassigned' | string | null>(null)



const sprintGuidanceMessage = computed(() => {
  if (hasActiveSprint.value) {
    return `当前活跃迭代「${activeSprints.value[0].name}」进行中。还有 ${plannedSprints.value.length} 个计划中的迭代等待启动。`
  }
  const next = plannedSprints.value[0]
  if (next?.startDate) return `当前没有活跃的迭代。下一个迭代「${next.name}」计划于 ${formatDate(next.startDate)} 开始。`
  return '当前没有活跃的迭代。请开始一个已计划的迭代以跟踪团队工作进度。'
})

const warningBarActivateTooltip = computed<string | undefined>(() => {
  const nextSprint = nextStartableSprint.value
  if (nextSprint && !canEditSprintItem(nextSprint)) return '您的角色不具有迭代管理权限，请联系项目管理员'
  if (!nextSprint) {
    const next = plannedSprints.value[0]
    if (next?.startDate) return `开始日期（${formatDate(next.startDate)}）尚未到达`
    if (next?.endDate) return `结束日期（${formatDate(next.endDate)}）已过期，无法激活`
    return '当前没有可启动的迭代'
  }
  return `启动迭代「${nextSprint.name}」`
})

const selectedProjectKey = computed(() => {
  const p = projects.value.find(proj => proj.id === selectedProject.value)
  return p?.key || undefined
})

/**
 * "下一个 Sprint" — 没有 active 时，从 planned 中找最近的那个。
 * 仅用于 UI badge 显示"下一个"标签，不代表它是"当前"。
 */
const nextPlannedSprintId = computed<string | null>(() => {
  // 有 active 时不需要标记任何 planned 为"下一个"
  if (activeSprints.value.length > 0) return null
  // planned 列表已按 start_date ASC 排序（后端保证）
  if (plannedSprints.value.length > 0) {
    return plannedSprints.value[0].id
  }
  return null
})

// ===== 工具函数 =====

function getActivateTooltip(sprint: SprintVO): string | undefined {
  if (!canEditSprintItem(sprint)) return '您的角色不具有迭代管理权限，请联系项目管理员'
  if (hasActiveSprint.value) return `需要先完成当前活跃迭代「${activeSprints.value[0].name}」才能激活此迭代`
  if (sprint.endDate) {
    const end = new Date(sprint.endDate); const today = new Date(); today.setHours(0,0,0,0); end.setHours(0,0,0,0)
    if (end.getTime() < today.getTime()) return `结束日期（${formatDate(sprint.endDate)}）已过期，无法激活`
  }
  if (sprint.startDate) {
    const start = new Date(sprint.startDate); const today = new Date(); today.setHours(0,0,0,0); start.setHours(0,0,0,0)
    if (start.getTime() > today.getTime()) return `开始日期（${formatDate(sprint.startDate)}）尚未到达`
  }
  return undefined
}

function toggleCompletedBurndown(sprintId: string) {
  const set = new Set(expandedCompletedSprints.value)
  if (set.has(sprintId)) {
    set.delete(sprintId)
  } else {
    set.add(sprintId)
  }
  expandedCompletedSprints.value = set
}

function viewUnassignedIssues(sprint: SprintVO) {
  openIssueDrawer(sprint, 'unassigned')
}

// ===== Sprint Issue Drawer =====

function openIssueDrawer(sprint: SprintVO, initialFilter: 'unassigned' | string | null = null) {
  drawerSprintId.value = sprint.id
  drawerSprintName.value = sprint.name
  // 确定 projectId：优先用当前选中项目，其次用 sprint 上附带的 projectId
  drawerProjectId.value = selectedProject.value || sprint.projectId || undefined
  drawerInitialFilter.value = initialFilter
  showIssueDrawer.value = true
}

function handleDrawerAssigned() {
  // 分配成功后刷新 Sprint 列表以更新统计数据
  loadSprints()
}

// ===== API 调用 =====

function handleProjectChange() { loadSprints() }

async function handleCompleteSprint(sprint: SprintVO) {
  completingSprint.value = sprint
  showCompleteModal.value = true
}

async function onSprintCompleted() {
  showCompletedSprints.value = true
  justCompletedSprintId.value = completingSprint.value?.id || null
  await loadSprints()
  setTimeout(() => { justCompletedSprintId.value = null }, 3000)
}

async function handleDeleteSprint(sprint: SprintVO) {
  deletingSprint.value = sprint
  showDeleteModal.value = true
}

function onSprintDeleted() { loadSprints() }

function openCreateModal() {
  showCreate.value = true
}

function openEditModal(sprint: SprintVO) {
  editingSprint.value = sprint
  showEdit.value = true
}

async function handleEditModalArchive(sprint: SprintVO) {
  showEdit.value = false
  await archiveSprint(sprint)
}

async function handleEditModalRestore(sprint: SprintVO) {
  showEdit.value = false
  await restoreSprint(sprint)
}

async function handleEditModalDelete(sprint: SprintVO) {
  showEdit.value = false
  await handleDeleteSprint(sprint)
}

onMounted(async () => {
  await loadProjects()

  // 1. 从 URL query 恢复项目选择（优先级最高）
  const queryProject = route.query.project as string | undefined
  if (queryProject && projects.value.length > 0) {
    // URL 中使用 project key（如 DE4），需要转为 ID
    const matchedByKey = projects.value.find(p => p.key === queryProject)
    if (matchedByKey) {
      projectStore.selectProject(matchedByKey.id)
    }
  }

  // 2. 始终加载 Sprint 列表（无论有无项目选择）
  // 如果有选中项目则加载该项目的 Sprint，否则加载所有可访问项目的 Sprint
  loadSprints()

  // 同步 URL
  if (selectedProject.value) {
    syncUrlProjectParam()
  }
})

/**
 * 项目选择变化时同步 URL 参数
 */
watch(selectedProject, (val) => {
  if (val) {
    syncUrlProjectParam()
  } else {
    // 清除 URL 中的 project 参数（"全部项目"视图）
    const query = { ...route.query }
    delete query.project
    router.replace({ query })
  }
})

/**
 * 将当前选中的项目 key 同步到 URL query 参数
 */
function syncUrlProjectParam() {
  const currentProject = projects.value.find(p => p.id === selectedProject.value)
  if (currentProject) {
    const currentQueryProject = route.query.project
    if (currentQueryProject !== currentProject.key) {
      router.replace({ query: { ...route.query, project: currentProject.key } })
    }
  }
}
</script>

<style scoped>
.sprint-page {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-1);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sprint-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

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
  margin-bottom: 16px;
}

.empty-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text-1);
  margin-bottom: 8px;
}

.empty-desc {
  font-size: 13px;
  color: var(--color-text-3);
  margin-bottom: 16px;
}

/* ===== 已完成 Sprint 折叠区域 ===== */
.completed-section {
  margin-top: 8px;
}
.completed-section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  border-radius: 4px;
  user-select: none;
  transition: background 0.15s;
}
.completed-section-header:hover {
  background: var(--color-fill-1);
}
.completed-toggle-icon {
  font-size: 11px;
  color: var(--color-text-3);
  width: 12px;
  text-align: center;
}
.completed-section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-3);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.completed-section-count {
  font-size: 11px;
  color: var(--color-text-4);
  background: var(--color-fill-2);
  padding: 1px 6px;
  border-radius: 8px;
}

</style>
