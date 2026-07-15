<template>
  <div class="kanban-page">
    <!-- 顶部工具栏 -->
    <div class="board-toolbar">
      <div class="toolbar-left">
        <h2 class="page-title">看板</h2>
        <a-select
          v-model="selectedProject"
          placeholder="选择项目"
          style="width: 200px"
          size="small"
          allow-search
          :loading="projectLoadState === 'loading'"
          @change="onProjectChange"
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
        <a-select
          v-model="selectedSprint"
          placeholder="所有迭代"
          style="width: 180px"
          size="small"
          allow-clear
          :disabled="!selectedProject"
          @change="loadBoard"
        >
          <a-option v-for="s in sprints" :key="s.id" :value="s.id">
            {{ s.name }}
          </a-option>
        </a-select>
        <a-divider direction="vertical" style="margin: 0 4px" />
        <!-- Swimlane 分组选择 -->
        <a-select
          v-model="swimlaneGroupBy"
          placeholder="分组"
          style="width: 140px"
          size="small"
          :disabled="!selectedProject"
          @change="onSwimlaneChange"
        >
          <a-option value="none">无分组</a-option>
          <a-option value="assignee">按负责人</a-option>
          <a-option value="priority">按优先级</a-option>
          <a-option value="type">按类型</a-option>
          <a-option value="sprint">按迭代</a-option>
        </a-select>
      </div>
      <div class="toolbar-right">
        <div class="search-wrapper">
          <a-input
            v-model="keyword"
            placeholder="搜索工单（编号/标题/负责人）"
            size="small"
            style="width: 240px"
            allow-clear
            @input="onSearchInput"
            @press-enter="loadIssuesWithLoading"
            @clear="onSearchClear"
          >
            <template #prefix>
              <icon-search />
            </template>
          </a-input>
          <transition name="fade">
            <span v-if="isSearchActive" class="search-active-badge">
              筛选中
            </span>
          </transition>
        </div>
        <a-tooltip content="看板列设置">
          <a-button
            size="small"
            :disabled="!selectedProject"
            @click="showSettings = true"
          >
            <template #icon><icon-settings /></template>
          </a-button>
        </a-tooltip>
      </div>
    </div>

    <!-- 加载状态 -->
    <a-spin :loading="loading" tip="加载看板数据..." class="board-spin">
      <!-- ===== 无分组模式（原始平面看板） ===== -->
      <div
        v-if="selectedProject && visibleStatuses.length > 0 && !showNoSearchResults && swimlaneGroupBy === 'none'"
        class="board-container"
      >
        <template v-for="status in visibleStatuses" :key="status.id">
          <!-- 有工单的列 或 手动展开的空列：正常展示 -->
          <div
            v-if="getColumnIssues(status.id).length > 0 || expandedEmptyColumns.has(status.id)"
            class="board-column"
            :class="{
              'board-column--expanded-empty': getColumnIssues(status.id).length === 0,
              'board-column--drop-target': dragOverColumnId === status.id && !dragOverSwimlaneKey,
              'board-column--drop-forbidden': dragOverColumnId === status.id && !dragOverSwimlaneKey && !isDropAllowed(status.id)
            }"
            @dragover="onDragOver($event, status.id)"
            @dragleave="onDragLeave($event)"
            @drop="onDrop($event, status.id)"
          >
            <div class="column-header" :style="{ borderTopColor: status.color }">
              <span class="column-title">{{ localizeStatusName(status.name) }}</span>
              <span class="column-count">{{ getColumnIssues(status.id).length }}</span>
              <button
                v-if="getColumnIssues(status.id).length === 0 && !draggingIssue"
                class="column-collapse-btn"
                :aria-label="`折叠 ${localizeStatusName(status.name)} 列`"
                title="折叠此列"
                @click="collapseColumn(status.id)"
              >✕</button>
            </div>
            <div class="column-body">
              <div
                v-for="issue in getColumnIssues(status.id)"
                :key="issue.id"
                class="kanban-card"
                :class="{
                  'kanban-card--dragging': draggingIssue?.id === issue.id,
                  'kanban-card--transitioning': transitioningIssueIds.has(issue.id),
                  'kanban-card--no-drag': !isCardDraggable(issue)
                }"
                role="button"
                tabindex="0"
                :draggable="isCardDraggable(issue)"
                @dragstart="onDragStart($event, issue)"
                @dragend="onDragEnd"
                @click="openIssue(issue)"
                @keydown.enter="openIssue(issue)"
              >
                <div class="card-header">
                  <span class="card-key">{{ issue.issueKey }}</span>
                  <span
                    class="card-priority"
                    :class="issue.priority?.toLowerCase()"
                    :title="issue.priority"
                  >
                    {{ priorityIcon(issue.priority) }}
                  </span>
                </div>
                <div class="card-title">{{ issue.title }}</div>
                <div class="card-footer">
                  <span class="card-type">{{ typeLabel(issue.issueType) }}</span>
                  <span class="card-assignee" v-if="issue.assigneeName">
                    {{ issue.assigneeName }}
                  </span>
                </div>
              </div>
              <div
                v-if="getColumnIssues(status.id).length === 0"
                class="column-empty-state"
                :class="{ 'column-empty-state--drop-hint': draggingIssue && isDropAllowed(status.id) }"
              >
                <template v-if="draggingIssue && isDropAllowed(status.id)">
                  <div class="column-empty-icon">📥</div>
                  <div class="column-empty-text">释放以移动到此状态</div>
                </template>
                <template v-else-if="draggingIssue && !isDropAllowed(status.id)">
                  <div class="column-empty-icon">🚫</div>
                  <div class="column-empty-text">不允许转换到此状态</div>
                </template>
                <template v-else>
                  <div class="column-empty-icon">📭</div>
                  <div class="column-empty-text">该状态下暂无工单</div>
                  <div class="column-empty-hint">拖拽工单到此列或创建新工单</div>
                </template>
              </div>
            </div>
          </div>

          <!-- 空列：折叠为窄条 -->
          <div
            v-else
            class="board-column-collapsed"
            :class="{
              'board-column-collapsed--drop-target': dragOverColumnId === status.id && isDropAllowed(status.id),
              'board-column-collapsed--drop-forbidden': dragOverColumnId === status.id && !isDropAllowed(status.id)
            }"
            role="button"
            tabindex="0"
            :aria-label="`${localizeStatusName(status.name)}，0 个工单，点击展开`"
            :title="`${localizeStatusName(status.name)} (0 工单) - ${draggingIssue ? '释放以移动' : '点击展开'}`"
            @click="!draggingIssue && expandColumn(status.id)"
            @keydown.enter="expandColumn(status.id)"
            @dragover="onDragOver($event, status.id)"
            @dragleave="onDragLeave($event)"
            @drop="onDrop($event, status.id)"
          >
            <div class="collapsed-indicator" :style="{ backgroundColor: status.color || 'var(--color-border)' }"></div>
            <span class="collapsed-name">{{ localizeStatusName(status.name) }}</span>
            <span class="collapsed-count">0</span>
          </div>
        </template>
      </div>

      <!-- ===== Swimlane 分组模式 ===== -->
      <div
        v-if="selectedProject && visibleStatuses.length > 0 && !showNoSearchResults && swimlaneGroupBy !== 'none'"
        class="swimlane-container"
      >
        <!-- Swimlane 表头（状态列标题） -->
        <div class="swimlane-header">
          <div class="swimlane-label-cell"></div>
          <div class="swimlane-columns-header">
            <div
              v-for="status in visibleStatuses"
              :key="status.id"
              class="swimlane-col-header"
              :style="{ borderTopColor: status.color }"
            >
              <span class="column-title">{{ localizeStatusName(status.name) }}</span>
            </div>
          </div>
        </div>

        <!-- Swimlane 各行 -->
        <div class="swimlane-body">
          <div
            v-for="lane in swimlanes"
            :key="lane.key"
            class="swimlane-row"
            :class="{ 'swimlane-row--collapsed': collapsedSwimlanes.has(lane.key) }"
          >
            <!-- 泳道行标题 -->
            <div
              class="swimlane-row-header"
              role="button"
              tabindex="0"
              @click="toggleSwimlane(lane.key)"
              @keydown.enter="toggleSwimlane(lane.key)"
            >
              <span class="swimlane-toggle-icon">
                {{ collapsedSwimlanes.has(lane.key) ? '▶' : '▼' }}
              </span>
              <span class="swimlane-row-label">{{ lane.label }}</span>
              <span class="swimlane-row-count">{{ lane.issues.length }}</span>
            </div>

            <!-- 泳道行内容（状态列 × 卡片） -->
            <div v-show="!collapsedSwimlanes.has(lane.key)" class="swimlane-row-body">
              <div class="swimlane-label-cell"></div>
              <div class="swimlane-columns">
                <div
                  v-for="status in visibleStatuses"
                  :key="status.id"
                  class="swimlane-cell"
                  :class="{
                    'swimlane-cell--drop-target': dragOverColumnId === status.id && dragOverSwimlaneKey === lane.key,
                    'swimlane-cell--drop-forbidden': dragOverColumnId === status.id && dragOverSwimlaneKey === lane.key && !isDropAllowed(status.id)
                  }"
                  @dragover="onDragOverSwimlane($event, status.id, lane.key)"
                  @dragleave="onDragLeaveSwimlane($event)"
                  @drop="onDrop($event, status.id)"
                >
                  <div
                    v-for="issue in getSwimlaneColumnIssues(lane.key, status.id)"
                    :key="issue.id"
                    class="kanban-card"
                    :class="{
                      'kanban-card--dragging': draggingIssue?.id === issue.id,
                      'kanban-card--transitioning': transitioningIssueIds.has(issue.id),
                      'kanban-card--no-drag': !isCardDraggable(issue)
                    }"
                    role="button"
                    tabindex="0"
                    :draggable="isCardDraggable(issue)"
                    @dragstart="onDragStart($event, issue)"
                    @dragend="onDragEnd"
                    @click="openIssue(issue)"
                    @keydown.enter="openIssue(issue)"
                  >
                    <div class="card-header">
                      <span class="card-key">{{ issue.issueKey }}</span>
                      <span
                        class="card-priority"
                        :class="issue.priority?.toLowerCase()"
                        :title="issue.priority"
                      >
                        {{ priorityIcon(issue.priority) }}
                      </span>
                    </div>
                    <div class="card-title">{{ issue.title }}</div>
                    <div class="card-footer">
                      <span class="card-type">{{ typeLabel(issue.issueType) }}</span>
                      <span class="card-assignee" v-if="issue.assigneeName">
                        {{ issue.assigneeName }}
                      </span>
                    </div>
                  </div>
                  <!-- 空单元格 drop hint -->
                  <div
                    v-if="getSwimlaneColumnIssues(lane.key, status.id).length === 0 && draggingIssue && isDropAllowed(status.id)"
                    class="swimlane-cell-empty-hint"
                  >
                    📥
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态：搜索无结果 -->
      <div v-if="showNoSearchResults" class="empty-state">
        <div class="empty-icon">🔍</div>
        <h3 class="empty-title">未找到匹配的工单</h3>
        <p class="empty-desc">没有工单匹配关键词「{{ keyword }}」</p>
        <a-button type="primary" size="small" @click="clearSearch">清除搜索</a-button>
      </div>

      <!-- 空状态：未选择项目 -->
      <div v-else-if="!selectedProject && projectLoadState === 'error'" class="empty-state">
        <div class="empty-icon">⚠️</div>
        <h3 class="empty-title">项目列表加载失败</h3>
        <p class="empty-desc">无法获取可用项目，请检查网络后重试</p>
        <a-button type="primary" size="small" @click="loadProjects">重试</a-button>
      </div>
      <div v-else-if="!selectedProject && projectLoadState === 'success' && projects.length === 0" class="empty-state">
        <div class="empty-icon">📁</div>
        <h3 class="empty-title">暂无可访问的项目</h3>
        <p class="empty-desc">您尚未加入任何项目，请联系管理员添加为项目成员</p>
      </div>
      <div v-else-if="!selectedProject" class="empty-state">
        <div class="empty-icon">📊</div>
        <h3 class="empty-title">请选择项目</h3>
        <p class="empty-desc">从上方下拉框选择项目查看看板视图</p>
      </div>
    </a-spin>

    <!-- 看板列设置 Drawer -->
    <BoardSettingsDrawer
      v-model:visible="showSettings"
      :project-id="selectedProject || ''"
      :columns="allColumnConfigs"
      @saved="onSettingsSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, h } from 'vue'
import { useRouter } from 'vue-router'
import { Message, Notification } from '@arco-design/web-vue'
import { issueApi, sprintApi, boardApi, workflowApi } from '@/api'
import type { IssueVO, IssueStatusVO, SprintVO, BoardColumnVO } from '@/api/types'
import { useProjectStore } from '@/stores/project'
import { usePermission } from '@/composables/usePermission'
import { useProjectList } from '@/composables/useProjectList'
import { localizeStatusName } from '@/utils/fieldLabels'
import BoardSettingsDrawer from './BoardSettingsDrawer.vue'
import { IconSettings, IconSearch } from '@arco-design/web-vue/es/icon'

const router = useRouter()
const projectStore = useProjectStore()

const selectedProject = computed({
  get: () => projectStore.selectedProjectId,
  set: (val) => projectStore.selectProject(val)
})

// 权限控制
const { canChangeStatus } = usePermission(() => selectedProject.value)
const selectedSprint = ref<string | undefined>(undefined)
const keyword = ref('')
const loading = ref(false)
const { projects, projectLoadState, loadProjects } = useProjectList()

// ===== Swimlane 分组 =====
type SwimlaneGroupBy = 'none' | 'assignee' | 'priority' | 'type' | 'sprint'
const SWIMLANE_STORAGE_KEY = 'tf_kanban_swimlane'
const COLLAPSED_SWIMLANES_KEY = 'tf_kanban_collapsed_swimlanes'

const swimlaneGroupBy = ref<SwimlaneGroupBy>(
  (localStorage.getItem(SWIMLANE_STORAGE_KEY) as SwimlaneGroupBy) || 'none'
)
const collapsedSwimlanes = ref<Set<string>>(
  new Set(JSON.parse(localStorage.getItem(COLLAPSED_SWIMLANES_KEY) || '[]'))
)

function onSwimlaneChange() {
  localStorage.setItem(SWIMLANE_STORAGE_KEY, swimlaneGroupBy.value)
  // 切换分组维度时清除折叠状态
  collapsedSwimlanes.value.clear()
  localStorage.removeItem(COLLAPSED_SWIMLANES_KEY)
}

function toggleSwimlane(key: string) {
  if (collapsedSwimlanes.value.has(key)) {
    collapsedSwimlanes.value.delete(key)
  } else {
    collapsedSwimlanes.value.add(key)
  }
  localStorage.setItem(COLLAPSED_SWIMLANES_KEY, JSON.stringify([...collapsedSwimlanes.value]))
}

// Swimlane 数据结构
interface SwimlaneRow {
  key: string
  label: string
  issues: IssueVO[]
}

// 类型映射
const TYPE_LABELS: Record<string, string> = { Task: '任务', Bug: '缺陷', Feature: '需求', Story: '故事' }

const swimlanes = computed<SwimlaneRow[]>(() => {
  if (swimlaneGroupBy.value === 'none') return []

  const allIssues = issues.value

  switch (swimlaneGroupBy.value) {
    case 'assignee':
      return groupByAssignee(allIssues)
    case 'priority':
      return groupByPriority(allIssues)
    case 'type':
      return groupByType(allIssues)
    case 'sprint':
      return groupBySprint(allIssues)
    default:
      return []
  }
})

function groupByAssignee(allIssues: IssueVO[]): SwimlaneRow[] {
  const groups = new Map<string, IssueVO[]>()
  const unassigned: IssueVO[] = []

  for (const issue of allIssues) {
    if (!issue.assigneeId || !issue.assigneeName) {
      unassigned.push(issue)
    } else {
      const key = issue.assigneeId
      if (!groups.has(key)) groups.set(key, [])
      groups.get(key)!.push(issue)
    }
  }

  // 按工单数量降序排列负责人
  const rows: SwimlaneRow[] = [...groups.entries()]
    .sort((a, b) => b[1].length - a[1].length)
    .map(([assigneeId, issues]) => ({
      key: assigneeId,
      label: issues[0].assigneeName || '未知',
      issues
    }))

  // "未分配" 放在最后
  if (unassigned.length > 0) {
    rows.push({ key: '__unassigned__', label: '未分配', issues: unassigned })
  }

  return rows
}

function groupByPriority(allIssues: IssueVO[]): SwimlaneRow[] {
  const priorities = ['Critical', 'High', 'Normal', 'Low']
  const groups = new Map<string, IssueVO[]>()
  for (const p of priorities) groups.set(p, [])

  for (const issue of allIssues) {
    const p = issue.priority || 'Normal'
    if (!groups.has(p)) groups.set(p, [])
    groups.get(p)!.push(issue)
  }

  return priorities
    .filter(p => (groups.get(p)?.length ?? 0) > 0)
    .map(p => ({
      key: p,
      label: `${priorityIcon(p)} ${p}`,
      issues: groups.get(p)!
    }))
}

function groupByType(allIssues: IssueVO[]): SwimlaneRow[] {
  const types = ['Bug', 'Task', 'Feature', 'Story']
  const groups = new Map<string, IssueVO[]>()
  const other: IssueVO[] = []

  for (const issue of allIssues) {
    const t = issue.issueType
    if (types.includes(t)) {
      if (!groups.has(t)) groups.set(t, [])
      groups.get(t)!.push(issue)
    } else {
      other.push(issue)
    }
  }

  const rows: SwimlaneRow[] = types
    .filter(t => (groups.get(t)?.length ?? 0) > 0)
    .map(t => ({
      key: t,
      label: TYPE_LABELS[t] || t,
      issues: groups.get(t)!
    }))

  if (other.length > 0) {
    rows.push({ key: '__other__', label: '其他', issues: other })
  }

  return rows
}

function groupBySprint(allIssues: IssueVO[]): SwimlaneRow[] {
  const groups = new Map<string, IssueVO[]>()
  const noSprint: IssueVO[] = []

  for (const issue of allIssues) {
    if (!issue.sprintId) {
      noSprint.push(issue)
    } else {
      if (!groups.has(issue.sprintId)) groups.set(issue.sprintId, [])
      groups.get(issue.sprintId)!.push(issue)
    }
  }

  // 用 sprints 列表映射名称
  const sprintMap = new Map(sprints.value.map(s => [s.id, s.name]))

  const rows: SwimlaneRow[] = [...groups.entries()].map(([sprintId, issues]) => ({
    key: sprintId,
    label: sprintMap.get(sprintId) || `Sprint ${sprintId}`,
    issues
  }))

  if (noSprint.length > 0) {
    rows.push({ key: '__no_sprint__', label: '未规划', issues: noSprint })
  }

  return rows
}

/** 获取某泳道中某状态列的工单 */
function getSwimlaneColumnIssues(laneKey: string, statusId: string): IssueVO[] {
  const lane = swimlanes.value.find(l => l.key === laneKey)
  if (!lane) return []
  return lane.issues.filter(i => i.statusId === statusId)
}

// Swimlane 模式下的拖拽 hover 状态
const dragOverSwimlaneKey = ref<string | null>(null)

function onDragOverSwimlane(event: DragEvent, statusId: string, laneKey: string) {
  event.preventDefault()
  dragOverColumnId.value = statusId
  dragOverSwimlaneKey.value = laneKey
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = isDropAllowed(statusId) ? 'move' : 'none'
  }
}

function onDragLeaveSwimlane(event: DragEvent) {
  const relatedTarget = event.relatedTarget as HTMLElement | null
  const currentTarget = event.currentTarget as HTMLElement
  if (relatedTarget && currentTarget.contains(relatedTarget)) return
  dragOverColumnId.value = null
  dragOverSwimlaneKey.value = null
}

// 搜索相关
let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null
const isSearchActive = computed(() => keyword.value.trim().length > 0)
const showNoSearchResults = computed(() =>
  isSearchActive.value && selectedProject.value && issues.value.length === 0 && !loading.value
)

function onSearchInput() {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => {
    loadIssuesWithLoading()
  }, 350)
}

function onSearchClear() {
  keyword.value = ''
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  loadIssuesWithLoading()
}

function clearSearch() {
  keyword.value = ''
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  loadIssuesWithLoading()
}

/** 带 loading 状态的工单刷新 */
async function loadIssuesWithLoading() {
  if (!selectedProject.value) return
  loading.value = true
  try {
    await loadIssues()
  } catch {
    issues.value = []
    Message.error('搜索失败')
  } finally {
    loading.value = false
  }
}

function onProjectChange() {
  keyword.value = ''
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  loadBoard()
}
const sprints = ref<SprintVO[]>([])
const statuses = ref<IssueStatusVO[]>([])
const issues = ref<IssueVO[]>([])

// 看板列配置
const allColumnConfigs = ref<BoardColumnVO[]>([])
const showSettings = ref(false)

// 根据列配置过滤出可见的状态
const visibleStatuses = computed(() => {
  if (allColumnConfigs.value.length === 0) {
    return statuses.value
  }
  return allColumnConfigs.value
    .filter(c => c.visible)
    .map(c => ({
      id: c.statusId,
      name: c.statusName,
      code: c.statusCode,
      color: c.statusColor,
      category: c.statusCategory,
      isDefault: false,
      isClosed: c.statusCategory === 'done' || c.statusCategory === 'cancelled',
      sortOrder: c.sortOrder
    } as IssueStatusVO))
})

// 被手动展开的空列集合
const expandedEmptyColumns = ref<Set<string>>(new Set())

// ===== 拖拽状态 =====
const draggingIssue = ref<IssueVO | null>(null)
const dragOverColumnId = ref<string | null>(null)
const allowedTargetStatuses = ref<Set<string>>(new Set())
const transitioningIssueIds = ref<Set<string>>(new Set())

// ===== 可拖拽源状态 =====
const transitionableSourceStatuses = ref<Set<string>>(new Set())

// ===== 撤销历史 =====
interface UndoEntry {
  issueId: string
  issueKey: string
  oldStatusId: string
  newStatusId: string
  oldStatusName: string
  newStatusName: string
  timestamp: number
}
const undoStack = ref<UndoEntry[]>([])
const UNDO_TIMEOUT = 10000

function getColumnIssues(statusId: string): IssueVO[] {
  return issues.value.filter(i => i.statusId === statusId)
}

function expandColumn(statusId: string) {
  expandedEmptyColumns.value.add(statusId)
}

function collapseColumn(statusId: string) {
  expandedEmptyColumns.value.delete(statusId)
}

function priorityIcon(priority: string): string {
  const map: Record<string, string> = { Critical: '🔴', High: '🟠', Normal: '🔵', Low: '⚪' }
  return map[priority] || '🔵'
}

function typeLabel(type: string): string {
  const map: Record<string, string> = { Task: '任务', Bug: '缺陷', Feature: '需求', Story: '故事' }
  return map[type] || type
}

function openIssue(issue: IssueVO) {
  if (draggingIssue.value) return
  router.push({ name: 'IssueDetail', params: { id: issue.id } })
}

// ===== 拖拽逻辑 =====

function isCardDraggable(issue: IssueVO): boolean {
  if (!canChangeStatus.value) return false
  if (transitionableSourceStatuses.value.size === 0) return true
  return transitionableSourceStatuses.value.has(issue.statusId)
}

async function onDragStart(event: DragEvent, issue: IssueVO) {
  if (!isCardDraggable(issue)) {
    event.preventDefault()
    Message.warning('该工单当前状态不允许变更')
    return
  }

  draggingIssue.value = issue

  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', issue.id)
  }

  try {
    const res = await issueApi.getAvailableTransitions(issue.id)
    const allowed = res.data || []
    allowedTargetStatuses.value = new Set(allowed.map(s => s.id))
  } catch {
    allowedTargetStatuses.value = new Set(statuses.value.map(s => s.id))
  }
}

function onDragEnd() {
  draggingIssue.value = null
  dragOverColumnId.value = null
  dragOverSwimlaneKey.value = null
  allowedTargetStatuses.value.clear()
}

function onDragOver(event: DragEvent, statusId: string) {
  event.preventDefault()
  dragOverColumnId.value = statusId
  dragOverSwimlaneKey.value = null

  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = isDropAllowed(statusId) ? 'move' : 'none'
  }
}

function onDragLeave(event: DragEvent) {
  const relatedTarget = event.relatedTarget as HTMLElement | null
  const currentTarget = event.currentTarget as HTMLElement
  if (relatedTarget && currentTarget.contains(relatedTarget)) return
  dragOverColumnId.value = null
  dragOverSwimlaneKey.value = null
}

function isDropAllowed(targetStatusId: string): boolean {
  if (!draggingIssue.value) return false
  if (draggingIssue.value.statusId === targetStatusId) return false
  return allowedTargetStatuses.value.has(targetStatusId)
}

async function onDrop(event: DragEvent, targetStatusId: string) {
  event.preventDefault()
  dragOverColumnId.value = null
  dragOverSwimlaneKey.value = null

  const issue = draggingIssue.value
  if (!issue || !isDropAllowed(targetStatusId)) {
    onDragEnd()
    return
  }

  const oldStatusId = issue.statusId
  const targetStatus = statuses.value.find(s => s.id === targetStatusId)

  // 乐观更新
  issue.statusId = targetStatusId
  transitioningIssueIds.value.add(issue.id)

  draggingIssue.value = null
  allowedTargetStatuses.value.clear()

  try {
    await issueApi.transitStatus(issue.id, targetStatusId, undefined, issue.version)

    const undoEntry: UndoEntry = {
      issueId: issue.id,
      issueKey: issue.issueKey,
      oldStatusId: oldStatusId,
      newStatusId: targetStatusId,
      oldStatusName: statuses.value.find(s => s.id === oldStatusId)?.name || '',
      newStatusName: targetStatus?.name || '',
      timestamp: Date.now()
    }
    undoStack.value.push(undoEntry)

    const notifId = `undo-${issue.id}-${Date.now()}`
    Notification.success({
      id: notifId,
      title: '状态变更成功',
      content: `${issue.issueKey} 已移至「${targetStatus?.name || '目标状态'}」`,
      duration: UNDO_TIMEOUT,
      closable: true,
      footer: () => h('button', {
        class: 'undo-btn',
        onClick: () => {
          undoTransition(undoEntry)
          Notification.remove(notifId)
        }
      }, '↩ 撤销 (Ctrl+Z)')
    })
  } catch (e: any) {
    issue.statusId = oldStatusId
    const errMsg = e.response?.data?.message || '状态变更失败'
    Message.error(`${issue.issueKey} 移动失败：${errMsg}`)
  } finally {
    transitioningIssueIds.value.delete(issue.id)
  }
}

// ===== 撤销逻辑 =====

async function undoTransition(entry: UndoEntry) {
  const issue = issues.value.find(i => i.id === entry.issueId)
  if (!issue) {
    Message.warning('工单已不在当前视图中，无法撤销')
    return
  }

  const currentStatusId = issue.statusId
  issue.statusId = entry.oldStatusId
  transitioningIssueIds.value.add(issue.id)

  try {
    await issueApi.undoTransitStatus(issue.id, entry.oldStatusId)
    Message.success(`${entry.issueKey} 已撤销回「${entry.oldStatusName}」`)
    undoStack.value = undoStack.value.filter(e => e !== entry)
  } catch (e: any) {
    issue.statusId = currentStatusId
    const errMsg = e.response?.data?.message || '撤销失败'
    Message.error(`撤销失败：${errMsg}`)
  } finally {
    transitioningIssueIds.value.delete(issue.id)
  }
}

function handleKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'z' && !e.shiftKey) {
    const now = Date.now()
    const validEntries = undoStack.value.filter(entry => now - entry.timestamp < UNDO_TIMEOUT)
    if (validEntries.length > 0) {
      e.preventDefault()
      const lastEntry = validEntries[validEntries.length - 1]
      undoTransition(lastEntry)
    }
  }
}

// ===== 数据加载 =====

async function loadStatuses() {
  try {
    const res = await issueApi.listStatuses()
    statuses.value = res.data || []
  } catch {
    statuses.value = []
    Message.error('加载状态列表失败')
  }
}

async function loadBoardColumns() {
  if (!selectedProject.value) {
    allColumnConfigs.value = []
    return
  }
  try {
    const res = await boardApi.getColumns(selectedProject.value)
    allColumnConfigs.value = res.data || []
  } catch {
    allColumnConfigs.value = []
  }
}

async function loadTransitionableStatuses() {
  if (!selectedProject.value || !canChangeStatus.value) {
    transitionableSourceStatuses.value = new Set()
    return
  }
  try {
    const res = await workflowApi.getTransitionableStatuses(selectedProject.value)
    transitionableSourceStatuses.value = new Set(res.data || [])
  } catch {
    transitionableSourceStatuses.value = new Set()
  }
}

function onSettingsSaved() {
  loadBoardColumns()
}

async function loadSprints() {
  if (!selectedProject.value) { sprints.value = []; return }
  try {
    const res = await sprintApi.listByProject(selectedProject.value)
    sprints.value = res.data || []
  } catch {
    sprints.value = []
    Message.error('加载迭代列表失败')
  }
}

async function loadBoard() {
  if (!selectedProject.value) { issues.value = []; return }
  expandedEmptyColumns.value.clear()
  loading.value = true
  try {
    await Promise.all([loadSprints(), loadBoardColumns(), loadTransitionableStatuses()])
    await loadIssues()
  } catch {
    issues.value = []
    Message.error('加载看板数据失败')
  } finally {
    loading.value = false
  }
}

async function loadIssues() {
  if (!selectedProject.value) { issues.value = []; return }
  const res = await issueApi.list({
    projectId: selectedProject.value,
    sprintId: selectedSprint.value || undefined,
    keyword: keyword.value || undefined,
    pageSize: 100
  })
  issues.value = res.data?.list || []
}

onMounted(async () => {
  await Promise.all([loadProjects(), loadStatuses()])
  document.addEventListener('keydown', handleKeydown)

  if (selectedProject.value) {
    loadBoard()
  }
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
})
</script>

<style scoped>
.kanban-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.board-toolbar {
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

.search-wrapper {
  display: flex;
  align-items: center;
  gap: 6px;
}

.search-active-badge {
  font-size: 11px;
  color: rgb(var(--primary-6));
  background: rgba(var(--primary-6), 0.1);
  padding: 2px 8px;
  border-radius: 3px;
  white-space: nowrap;
  font-weight: 500;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-1);
  margin: 0;
}

.board-spin {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== 平面看板（无分组） ===== */
.board-container {
  flex: 1;
  display: flex;
  gap: 8px;
  padding: 16px;
  overflow-x: auto;
  overflow-y: hidden;
}

/* ===== 正常列 ===== */
.board-column {
  min-width: 220px;
  max-width: 320px;
  flex: 1 1 220px;
  display: flex;
  flex-direction: column;
  background: var(--color-fill-1);
  border-radius: 8px;
  overflow: hidden;
  transition: box-shadow 0.15s, border-color 0.15s;
  border: 2px solid transparent;
}

.board-column--expanded-empty {
  min-width: 200px;
  max-width: 220px;
  flex: 0 0 200px;
  opacity: 0.7;
}

.board-column--drop-target {
  border-color: rgb(var(--primary-6));
  box-shadow: 0 0 0 2px rgba(var(--primary-6), 0.15);
  background: var(--color-fill-2);
}

.board-column--drop-forbidden {
  border-color: rgb(var(--danger-6));
  opacity: 0.6;
}

.column-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border-top: 3px solid var(--color-border);
  flex-shrink: 0;
}

.column-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-1);
  text-transform: uppercase;
  letter-spacing: 0.3px;
  flex: 1;
}

.column-count {
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-fill-3);
  padding: 2px 6px;
  border-radius: 3px;
}

.column-collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: none;
  background: var(--color-fill-3);
  color: var(--color-text-3);
  border-radius: 3px;
  cursor: pointer;
  font-size: 10px;
  line-height: 1;
  transition: background 0.15s, color 0.15s;
}
.column-collapse-btn:hover {
  background: var(--color-fill-4);
  color: var(--color-text-1);
}

.column-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 60px;
}

/* ===== 折叠的空列 ===== */
.board-column-collapsed {
  flex: 0 0 36px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 12px 4px;
  background: var(--color-fill-1);
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
  overflow: hidden;
  border: 2px solid transparent;
}
.board-column-collapsed:hover {
  background: var(--color-fill-2);
}
.board-column-collapsed:focus-visible {
  outline: 2px solid rgb(var(--primary-6));
  outline-offset: -2px;
}

.board-column-collapsed--drop-target {
  border-color: rgb(var(--primary-6));
  background: var(--color-fill-2);
}
.board-column-collapsed--drop-forbidden {
  border-color: rgb(var(--danger-6));
  opacity: 0.5;
}

.collapsed-indicator {
  width: 20px;
  height: 3px;
  border-radius: 2px;
  flex-shrink: 0;
}

.collapsed-name {
  writing-mode: vertical-rl;
  text-orientation: mixed;
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-3);
  letter-spacing: 0.3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-height: 120px;
}

.collapsed-count {
  font-size: 10px;
  color: var(--color-text-4);
  background: var(--color-fill-3);
  padding: 1px 4px;
  border-radius: 3px;
}

/* ===== 卡片 ===== */
.kanban-card {
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 10px 12px;
  cursor: grab;
  transition: border-color 0.15s, box-shadow 0.15s, opacity 0.15s, transform 0.15s;
  user-select: none;
}
.kanban-card:hover {
  border-color: rgb(var(--primary-6));
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.kanban-card:active {
  cursor: grabbing;
}
.kanban-card:focus-visible {
  outline: 2px solid rgb(var(--primary-6));
  outline-offset: 1px;
}

.kanban-card--no-drag {
  cursor: pointer;
}
.kanban-card--no-drag:active {
  cursor: pointer;
}

.kanban-card--dragging {
  opacity: 0.4;
  transform: scale(0.97);
  border-color: rgb(var(--primary-6));
}

.kanban-card--transitioning {
  opacity: 0.6;
  pointer-events: none;
  position: relative;
}
.kanban-card--transitioning::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--color-fill-2);
  border-radius: 6px;
  animation: pulse 1s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 0.6; }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.card-key {
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-3);
}

.card-priority {
  font-size: 10px;
}

.card-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.card-type {
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  padding: 2px 6px;
  border-radius: 3px;
}

.card-assignee {
  font-size: 11px;
  color: var(--color-text-2);
}

/* ===== 展开空列的空状态 ===== */
.column-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px 8px;
  text-align: center;
  border-radius: 6px;
  transition: background 0.15s;
}

.column-empty-state--drop-hint {
  background: rgba(var(--primary-6), 0.06);
  border: 1px dashed rgb(var(--primary-6));
}

.column-empty-icon {
  font-size: 24px;
  margin-bottom: 8px;
}

.column-empty-text {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-3);
  margin-bottom: 4px;
}

.column-empty-hint {
  font-size: 11px;
  color: var(--color-text-4);
}

/* ===== 页面空状态 ===== */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
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
}

/* ===== Swimlane 分组视图 ===== */
.swimlane-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: auto;
  padding: 0;
}

.swimlane-header {
  display: flex;
  position: sticky;
  top: 0;
  z-index: 5;
  background: var(--color-bg-1);
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.swimlane-label-cell {
  width: 200px;
  min-width: 200px;
  flex-shrink: 0;
}

.swimlane-columns-header {
  display: flex;
  flex: 1;
  gap: 2px;
  padding: 8px 16px 8px 0;
}

.swimlane-col-header {
  flex: 1;
  min-width: 160px;
  padding: 8px 12px;
  border-top: 3px solid var(--color-border);
  background: var(--color-fill-1);
  border-radius: 6px 6px 0 0;
  text-align: center;
}

.swimlane-body {
  flex: 1;
}

.swimlane-row {
  border-bottom: 1px solid var(--color-border);
}

.swimlane-row--collapsed .swimlane-row-header {
  border-bottom: none;
}

.swimlane-row-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: var(--color-fill-1);
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
  position: sticky;
  left: 0;
}
.swimlane-row-header:hover {
  background: var(--color-fill-2);
}
.swimlane-row-header:focus-visible {
  outline: 2px solid rgb(var(--primary-6));
  outline-offset: -2px;
}

.swimlane-toggle-icon {
  font-size: 10px;
  color: var(--color-text-3);
  width: 14px;
  text-align: center;
  transition: transform 0.15s;
}

.swimlane-row-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
}

.swimlane-row-count {
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-fill-3);
  padding: 2px 8px;
  border-radius: 3px;
}

.swimlane-row-body {
  display: flex;
}

.swimlane-columns {
  display: flex;
  flex: 1;
  gap: 2px;
  padding: 8px 16px 12px 0;
}

.swimlane-cell {
  flex: 1;
  min-width: 160px;
  min-height: 60px;
  padding: 6px;
  background: var(--color-fill-1);
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: background 0.15s, border-color 0.15s;
  border: 2px solid transparent;
}

.swimlane-cell--drop-target {
  border-color: rgb(var(--primary-6));
  background: var(--color-fill-2);
}

.swimlane-cell--drop-forbidden {
  border-color: rgb(var(--danger-6));
  opacity: 0.6;
}

.swimlane-cell-empty-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  font-size: 16px;
  opacity: 0.5;
}

/* ===== 撤销按钮（Notification footer 中） ===== */
:global(.undo-btn) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  margin-top: 8px;
  border: 1px solid rgb(var(--primary-6));
  background: transparent;
  color: rgb(var(--primary-6));
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
:global(.undo-btn:hover) {
  background: rgb(var(--primary-6));
  color: #fff;
}
</style>
