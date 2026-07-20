<template>
  <div class="sprint-planning-page">
    <!-- 顶部工具栏 -->
    <div class="planning-toolbar">
      <div class="toolbar-left">
        <h2 class="page-title">Sprint 规划</h2>
        <a-select
          v-model="selectedProject"
          placeholder="选择项目"
          style="width: 200px"
          size="small"
          allow-search
          :loading="projectLoadState === 'loading'"
          @change="onProjectChange"
        >
          <a-option v-for="p in projects" :key="p.id" :value="p.id">
            {{ p.key }} - {{ p.name }}
          </a-option>
        </a-select>
      </div>
      <div class="toolbar-right">
        <span v-if="selectedCount > 0" class="selection-indicator">
          已选择 {{ selectedCount }} 个工单
          <a-button size="mini" type="text" @click="clearSelection">清除</a-button>
        </span>
      </div>
    </div>

    <!-- 主内容区 -->
    <div v-if="selectedProject" class="planning-content">
      <!-- 左侧 Backlog 面板 -->
      <div class="backlog-panel">
        <div class="panel-header">
          <div class="panel-header-left">
            <h3 class="panel-title">Backlog</h3>
            <span class="panel-count">{{ backlogIssues.length }}</span>
          </div>
        </div>

        <!-- 筛选区 -->
        <div class="backlog-filters">
          <a-input
            v-model="backlogSearch"
            placeholder="搜索..."
            size="mini"
            allow-clear
            style="margin-bottom: 6px"
            @input="onBacklogSearchInput"
            @clear="loadBacklog"
          >
            <template #prefix><icon-search /></template>
          </a-input>
          <div class="filter-row">
            <a-select v-model="backlogFilterType" placeholder="类型" size="mini" allow-clear style="flex:1" @change="loadBacklog">
              <a-option value="Task">任务</a-option>
              <a-option value="Bug">缺陷</a-option>
              <a-option value="Feature">需求</a-option>
              <a-option value="Story">故事</a-option>
            </a-select>
            <a-select v-model="backlogFilterPriority" placeholder="优先级" size="mini" allow-clear style="flex:1" @change="loadBacklog">
              <a-option value="Critical">紧急</a-option>
              <a-option value="High">高</a-option>
              <a-option value="Normal">普通</a-option>
              <a-option value="Low">低</a-option>
            </a-select>
            <a-select v-model="backlogFilterAssignee" placeholder="负责人" size="mini" allow-clear style="flex:1" @change="loadBacklog">
              <a-option value="unassigned">未分配</a-option>
              <a-option v-for="m in projectMembers" :key="m.userId" :value="m.userId">
                {{ m.displayName }}
              </a-option>
            </a-select>
          </div>
        </div>

        <!-- Backlog 工单列表 -->
        <div
          class="panel-body"
          @dragover.prevent="onBacklogDragOver"
          @dragleave="onBacklogDragLeave"
          @drop="onBacklogDrop"
          :class="{ 'drop-target': backlogDropHighlight }"
        >
          <a-spin :loading="backlogLoading" class="panel-spin">
            <div class="issue-list">
              <div
                v-for="issue in backlogIssues"
                :key="issue.id"
                class="planning-card"
                :class="{
                  'planning-card--selected': selectedIds.has(issue.id),
                  'planning-card--dragging': draggingIds.has(issue.id)
                }"
                draggable="true"
                @dragstart="onDragStart($event, issue, 'backlog')"
                @dragend="onDragEnd"
                @click="onCardClick($event, issue, 'backlog')"
              >
                <div class="card-top">
                  <span class="card-key">{{ issue.issueKey }}</span>
                  <span class="card-priority" :class="issue.priority?.toLowerCase()">
                    {{ priorityIcon(issue.priority) }}
                  </span>
                </div>
                <div class="card-title">{{ issue.title }}</div>
                <div class="card-meta">
                  <span class="card-type">{{ localizeIssueType(issue.issueType) }}</span>
                  <span v-if="issue.assigneeName" class="card-assignee">{{ issue.assigneeName }}</span>
                </div>
              </div>

              <!-- 空状态 -->
              <div v-if="!backlogLoading && backlogIssues.length === 0" class="panel-empty">
                <div class="empty-icon">📋</div>
                <div class="empty-title">Backlog 为空</div>
                <div class="empty-desc">所有工单都已分配到 Sprint</div>
              </div>
            </div>
          </a-spin>
        </div>
      </div>

      <!-- 右侧 Sprint 面板区域 -->
      <div class="sprint-panels">
        <div
          v-for="sprint in targetSprints"
          :key="sprint.id"
          class="sprint-column"
        >
          <div class="panel-header">
            <div class="panel-header-left">
              <span class="sprint-badge" :class="sprint.status">
                {{ sprint.status === 'active' ? '进行中' : '计划中' }}
              </span>
              <h3 class="panel-title">{{ sprint.name }}</h3>
              <span class="panel-count">{{ getSprintIssues(sprint.id).length }}</span>
            </div>
            <div class="panel-header-right" v-if="sprint.startDate">
              <span class="sprint-dates">{{ formatDate(sprint.startDate) }} — {{ formatDate(sprint.endDate) }}</span>
            </div>
          </div>

          <!-- Sprint 工单列表（drop target） -->
          <div
            class="panel-body"
            :class="{ 'drop-target': dropTargetSprintId === sprint.id }"
            @dragover.prevent="onSprintDragOver($event, sprint.id)"
            @dragleave="onSprintDragLeave"
            @drop="onSprintDrop($event, sprint.id)"
          >
            <div class="issue-list">
              <div
                v-for="issue in getSprintIssues(sprint.id)"
                :key="issue.id"
                class="planning-card"
                :class="{
                  'planning-card--selected': selectedIds.has(issue.id),
                  'planning-card--dragging': draggingIds.has(issue.id)
                }"
                draggable="true"
                @dragstart="onDragStart($event, issue, sprint.id)"
                @dragend="onDragEnd"
                @click="onCardClick($event, issue, sprint.id)"
              >
                <div class="card-top">
                  <span class="card-key">{{ issue.issueKey }}</span>
                  <span class="card-priority" :class="issue.priority?.toLowerCase()">
                    {{ priorityIcon(issue.priority) }}
                  </span>
                </div>
                <div class="card-title">{{ issue.title }}</div>
                <div class="card-meta">
                  <span class="card-type">{{ localizeIssueType(issue.issueType) }}</span>
                  <span v-if="issue.assigneeName" class="card-assignee">{{ issue.assigneeName }}</span>
                </div>
              </div>

              <!-- Sprint 空状态 -->
              <div v-if="getSprintIssues(sprint.id).length === 0" class="panel-empty panel-empty--sprint">
                <div class="empty-icon">🎯</div>
                <div class="empty-title">暂无工单</div>
                <div class="empty-desc">从 Backlog 拖拽工单到此处</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 无 Sprint 空状态 -->
        <div v-if="targetSprints.length === 0 && !sprintsLoading" class="no-sprints-state">
          <div class="empty-icon">🏃</div>
          <h3 class="empty-title">暂无可规划的 Sprint</h3>
          <p class="empty-desc">请先创建一个 Sprint（计划中或进行中状态）</p>
          <a-button type="primary" size="small" @click="goToSprintPage">前往迭代管理</a-button>
        </div>
      </div>
    </div>

    <!-- 未选择项目 -->
    <div v-else class="empty-state">
      <div class="empty-icon">📁</div>
      <h3 class="empty-title">请选择项目</h3>
      <p class="empty-desc">从上方下拉框选择一个项目开始 Sprint 规划</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { IconSearch } from '@arco-design/web-vue/es/icon'
import { issueApi, sprintApi, projectApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import { useProjectList } from '@/composables/useProjectList'
import { localizeIssueType, localizePriority } from '@/utils/fieldLabels'
import type { IssueVO, SprintVO, ProjectMemberVO } from '@/api/types'

const router = useRouter()
const projectStore = useProjectStore()

const selectedProject = computed({
  get: () => projectStore.selectedProjectId,
  set: (val) => projectStore.selectProject(val)
})

const { projects, projectLoadState, loadProjects } = useProjectList()

// ===== Data =====
const backlogIssues = ref<IssueVO[]>([])
const sprintIssuesMap = ref<Map<string, IssueVO[]>>(new Map())
const sprints = ref<SprintVO[]>([])
const projectMembers = ref<ProjectMemberVO[]>([])
const backlogLoading = ref(false)
const sprintsLoading = ref(false)

// ===== Filters =====
const backlogSearch = ref('')
const backlogFilterType = ref<string | undefined>(undefined)
const backlogFilterPriority = ref<string | undefined>(undefined)
const backlogFilterAssignee = ref<string | undefined>(undefined)

// ===== Selection =====
const selectedIds = ref<Set<string>>(new Set())
const lastClickedId = ref<string | null>(null)
const lastClickedSource = ref<string | null>(null)

// ===== Drag & Drop =====
const draggingIds = ref<Set<string>>(new Set())
const dragSourcePanel = ref<string | null>(null)
const dropTargetSprintId = ref<string | null>(null)
const backlogDropHighlight = ref(false)

let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null

// ===== Computed =====
const targetSprints = computed(() =>
  sprints.value.filter(s => s.status === 'active' || s.status === 'planned')
)

const selectedCount = computed(() => selectedIds.value.size)

// ===== Methods =====

function getSprintIssues(sprintId: string): IssueVO[] {
  return sprintIssuesMap.value.get(sprintId) || []
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function priorityIcon(priority?: string): string {
  const map: Record<string, string> = { Critical: '🔴', High: '🟠', Normal: '🔵', Low: '⚪' }
  return map[priority || ''] || '🔵'
}

function clearSelection() {
  selectedIds.value = new Set()
  lastClickedId.value = null
  lastClickedSource.value = null
}

// ===== Data Loading =====

async function onProjectChange() {
  clearSelection()
  if (!selectedProject.value) return
  await Promise.all([loadBacklog(), loadSprints(), loadMembers()])
}

async function loadBacklog() {
  if (!selectedProject.value) return
  backlogLoading.value = true
  try {
    const params: Record<string, any> = {
      projectId: selectedProject.value,
      sprintId: 'none',
      hideResolved: 'true',
      page: 1,
      pageSize: 100
    }
    if (backlogSearch.value) params.keyword = backlogSearch.value
    if (backlogFilterType.value) params.issueType = backlogFilterType.value
    if (backlogFilterPriority.value) params.priority = backlogFilterPriority.value
    if (backlogFilterAssignee.value) {
      if (backlogFilterAssignee.value === 'unassigned') {
        params.assigneeId = 'none'
      } else {
        params.assigneeId = backlogFilterAssignee.value
      }
    }
    const res = await issueApi.list(params)
    backlogIssues.value = res.data?.list || []
  } catch {
    backlogIssues.value = []
    Message.error('加载 Backlog 失败')
  } finally {
    backlogLoading.value = false
  }
}

async function loadSprints() {
  if (!selectedProject.value) return
  sprintsLoading.value = true
  try {
    const res = await sprintApi.listByProject(selectedProject.value)
    sprints.value = res.data || []
    // Load issues for each active/planned sprint
    await loadAllSprintIssues()
  } catch {
    sprints.value = []
  } finally {
    sprintsLoading.value = false
  }
}

async function loadAllSprintIssues() {
  const activePlanned = sprints.value.filter(s => s.status === 'active' || s.status === 'planned')
  const newMap = new Map<string, IssueVO[]>()

  await Promise.all(activePlanned.map(async (sprint) => {
    try {
      const res = await issueApi.list({
        projectId: selectedProject.value!,
        sprintId: sprint.id,
        hideResolved: 'true',
        page: 1,
        pageSize: 100
      })
      newMap.set(sprint.id, res.data?.list || [])
    } catch {
      newMap.set(sprint.id, [])
    }
  }))

  sprintIssuesMap.value = newMap
}

async function loadMembers() {
  if (!selectedProject.value) return
  try {
    const res = await projectApi.listMembers(selectedProject.value)
    projectMembers.value = res.data || []
  } catch {
    projectMembers.value = []
  }
}

function onBacklogSearchInput() {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => loadBacklog(), 350)
}

// ===== Selection Logic =====

function onCardClick(event: MouseEvent, issue: IssueVO, source: string) {
  const ids = selectedIds.value
  if (event.ctrlKey || event.metaKey) {
    // Toggle individual selection
    const newSet = new Set(ids)
    if (newSet.has(issue.id)) {
      newSet.delete(issue.id)
    } else {
      newSet.add(issue.id)
    }
    selectedIds.value = newSet
    lastClickedId.value = issue.id
    lastClickedSource.value = source
  } else if (event.shiftKey && lastClickedId.value && lastClickedSource.value === source) {
    // Range selection within same panel
    const list = source === 'backlog' ? backlogIssues.value : getSprintIssues(source)
    const lastIdx = list.findIndex(i => i.id === lastClickedId.value)
    const curIdx = list.findIndex(i => i.id === issue.id)
    if (lastIdx >= 0 && curIdx >= 0) {
      const start = Math.min(lastIdx, curIdx)
      const end = Math.max(lastIdx, curIdx)
      const newSet = new Set(ids)
      for (let i = start; i <= end; i++) {
        newSet.add(list[i].id)
      }
      selectedIds.value = newSet
    }
  } else {
    // Single select (deselect all others)
    selectedIds.value = new Set([issue.id])
    lastClickedId.value = issue.id
    lastClickedSource.value = source
  }
}

// ===== Drag & Drop =====

function onDragStart(event: DragEvent, issue: IssueVO, source: string) {
  if (!event.dataTransfer) return

  // If dragging a selected item, drag all selected items
  let idsToMove: Set<string>
  if (selectedIds.value.has(issue.id) && selectedIds.value.size > 1) {
    idsToMove = new Set(selectedIds.value)
  } else {
    idsToMove = new Set([issue.id])
    selectedIds.value = new Set([issue.id])
  }

  draggingIds.value = idsToMove
  dragSourcePanel.value = source

  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', JSON.stringify({
    issueIds: Array.from(idsToMove),
    source
  }))

  // Custom drag image showing count
  if (idsToMove.size > 1) {
    const el = document.createElement('div')
    el.className = 'drag-ghost'
    el.textContent = `${idsToMove.size} 个工单`
    el.style.cssText = 'position:fixed;top:-100px;left:-100px;padding:6px 12px;background:var(--color-primary-light-2,#4080ff);color:#fff;border-radius:4px;font-size:12px;font-weight:500;z-index:9999;'
    document.body.appendChild(el)
    event.dataTransfer.setDragImage(el, 0, 0)
    setTimeout(() => document.body.removeChild(el), 0)
  }
}

function onDragEnd() {
  draggingIds.value = new Set()
  dragSourcePanel.value = null
  dropTargetSprintId.value = null
  backlogDropHighlight.value = false
}

function onSprintDragOver(event: DragEvent, sprintId: string) {
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  dropTargetSprintId.value = sprintId
}

function onSprintDragLeave() {
  dropTargetSprintId.value = null
}

function onBacklogDragOver(event: DragEvent) {
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  backlogDropHighlight.value = true
}

function onBacklogDragLeave() {
  backlogDropHighlight.value = false
}

async function onSprintDrop(event: DragEvent, targetSprintId: string) {
  event.preventDefault()
  dropTargetSprintId.value = null

  const data = event.dataTransfer?.getData('text/plain')
  if (!data) return

  try {
    const { issueIds, source } = JSON.parse(data)
    if (source === targetSprintId) return // Dropped in same panel

    await moveIssuesToSprint(issueIds, targetSprintId)
  } catch (e: any) {
    Message.error(e.message || '移动失败')
  }
}

async function onBacklogDrop(event: DragEvent) {
  event.preventDefault()
  backlogDropHighlight.value = false

  const data = event.dataTransfer?.getData('text/plain')
  if (!data) return

  try {
    const { issueIds, source } = JSON.parse(data)
    if (source === 'backlog') return // Already in backlog

    await moveIssuesToBacklog(issueIds)
  } catch (e: any) {
    Message.error(e.message || '移动失败')
  }
}

async function moveIssuesToSprint(issueIds: string[], targetSprintId: string) {
  try {
    await issueApi.batch({
      operation: 'sprint',
      issueIds,
      sprintId: targetSprintId,
      silent: true
    })
    Message.success(`已将 ${issueIds.length} 个工单移入 Sprint`)
    clearSelection()
    // Refresh data
    await Promise.all([loadBacklog(), loadAllSprintIssues()])
  } catch (e: any) {
    Message.error(e.response?.data?.message || '移动失败')
  }
}

async function moveIssuesToBacklog(issueIds: string[]) {
  try {
    await issueApi.batch({
      operation: 'sprint',
      issueIds,
      sprintId: '0', // 0 means remove sprint
      silent: true
    })
    Message.success(`已将 ${issueIds.length} 个工单移回 Backlog`)
    clearSelection()
    await Promise.all([loadBacklog(), loadAllSprintIssues()])
  } catch (e: any) {
    Message.error(e.response?.data?.message || '移动失败')
  }
}

function goToSprintPage() {
  router.push('/sprints')
}

// ===== Lifecycle =====
watch(selectedProject, (val) => {
  if (val) onProjectChange()
}, { immediate: false })

// On mount, load projects and auto-select if available
import { onMounted } from 'vue'
onMounted(async () => {
  await loadProjects()
  if (selectedProject.value) {
    onProjectChange()
  }
})
</script>

<style scoped>
.sprint-planning-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== Toolbar ===== */
.planning-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-1);
  margin: 0;
}
.selection-indicator {
  font-size: 12px;
  color: rgb(var(--primary-6));
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: rgba(var(--primary-6), 0.08);
  border-radius: 4px;
}

/* ===== Main Content ===== */
.planning-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* ===== Backlog Panel ===== */
.backlog-panel {
  width: 300px;
  min-width: 300px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--color-border);
  background: var(--color-bg-1);
  overflow: hidden;
}
.backlog-filters {
  padding: 8px 12px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}
.filter-row {
  display: flex;
  gap: 4px;
}

/* ===== Sprint Panels Area ===== */
.sprint-panels {
  flex: 1;
  display: flex;
  overflow-x: auto;
  padding: 0;
  gap: 0;
}
.sprint-column {
  min-width: 300px;
  max-width: 400px;
  flex: 1;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--color-border);
  overflow: hidden;
}
.sprint-column:last-child {
  border-right: none;
}

/* ===== Shared Panel Styles ===== */
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px 8px;
  flex-shrink: 0;
}
.panel-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.panel-header-right {
  display: flex;
  align-items: center;
}
.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
  margin: 0;
}
.panel-count {
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-fill-3);
  padding: 2px 6px;
  border-radius: 3px;
}
.sprint-badge {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 3px;
  font-weight: 500;
}
.sprint-badge.active {
  background: rgba(var(--primary-6), 0.1);
  color: rgb(var(--primary-6));
}
.sprint-badge.planned {
  background: var(--color-fill-2);
  color: var(--color-text-3);
}
.sprint-dates {
  font-size: 11px;
  color: var(--color-text-3);
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 4px 8px 8px;
  transition: background 0.15s;
  border: 2px solid transparent;
  border-radius: 4px;
  margin: 0 4px 4px;
}
.panel-body.drop-target {
  background: rgba(var(--primary-6), 0.04);
  border-color: rgba(var(--primary-6), 0.3);
}

.panel-spin {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.issue-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* ===== Planning Card ===== */
.planning-card {
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 8px 10px;
  cursor: grab;
  transition: border-color 0.15s, box-shadow 0.15s, opacity 0.15s;
  user-select: none;
}
.planning-card:hover {
  border-color: rgb(var(--primary-6));
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}
.planning-card:active {
  cursor: grabbing;
}
.planning-card--selected {
  border-color: rgb(var(--primary-6));
  background: rgba(var(--primary-6), 0.04);
  box-shadow: 0 0 0 1px rgba(var(--primary-6), 0.3);
}
.planning-card--dragging {
  opacity: 0.4;
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.card-key {
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-3);
  font-family: monospace;
}
.card-priority {
  font-size: 10px;
}
.card-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-1);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}
.card-type {
  font-size: 10px;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  padding: 1px 5px;
  border-radius: 3px;
}
.card-assignee {
  font-size: 10px;
  color: var(--color-text-2);
}

/* ===== Empty States ===== */
.panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  text-align: center;
}
.panel-empty--sprint {
  padding: 48px 16px;
  min-height: 200px;
}
.empty-icon {
  font-size: 32px;
  margin-bottom: 8px;
}
.empty-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-2);
  margin-bottom: 4px;
}
.empty-desc {
  font-size: 12px;
  color: var(--color-text-3);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  padding: 64px 24px;
}

.no-sprints-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  text-align: center;
  padding: 64px 24px;
}
</style>
