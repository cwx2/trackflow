<template>
  <div class="issue-page">
    <!-- Left query panel (YouTrack style) -->
    <aside class="query-panel" :style="{ width: panelWidth + 'px' }">
      <div class="panel-top">
        <div class="panel-top-title">
          <span class="panel-label">查询</span>
          <span class="panel-total">{{ totalIssues }}</span>
        </div>
        <a-button type="text" size="mini">
          <template #icon><icon-plus /></template>
        </a-button>
      </div>

      <div class="panel-search">
        <a-input v-model="panelSearch" placeholder="搜索查询..." size="small" allow-clear>
          <template #prefix><icon-search /></template>
        </a-input>
      </div>

      <div class="query-group">
        <div class="group-header" @click="toggleGroup('projects')">
          <span class="group-arrow">{{ expandedGroups.has('projects') ? '\u25BE' : '\u25B8' }}</span>
          <span class="group-title">项目</span>
        </div>
        <div v-if="expandedGroups.has('projects')" class="group-items">
          <div
            v-for="p in projectList"
            :key="p.id"
            class="query-item"
            :class="{ active: activeProjectId === p.id }"
            @click="selectProject(p)"
          >
            <span class="query-name">{{ p.name }}</span>
          </div>
        </div>
      </div>

      <div class="query-group">
        <div class="group-header" @click="toggleGroup('saved')">
          <span class="group-arrow">{{ expandedGroups.has('saved') ? '\u25BE' : '\u25B8' }}</span>
          <span class="group-title">已保存的搜索</span>
        </div>
        <div v-if="expandedGroups.has('saved')" class="group-items">
          <div
            v-for="q in filteredQueries"
            :key="q.id"
            class="query-item"
            :class="{ active: activeQueryId === q.id }"
            @click="selectQuery(q)"
          >
            <span class="query-name">{{ q.name }}</span>
            <span class="query-count">{{ formatCount(q.count) }}</span>
          </div>
          <div v-if="filteredQueries.length === 0" class="empty-queries">暂无保存的搜索</div>
        </div>
      </div>
    </aside>

    <!-- Resizable divider -->
    <div
      class="panel-resizer"
      title="拖动以调整宽度，双击以展开/折叠"
      @mousedown="startPanelResize"
      @dblclick="togglePanelCollapse"
    ></div>

    <!-- Right issue list area -->
    <section class="issue-list-area">
      <!-- Search/Filter bar (YouTrack style with mode toggle) -->
      <FilterBar
        :project-id="activeProjectId"
        :status-list="statusCache"
        :project-list="projectList"
        :initial-filters="initialFilterChips"
        @search="onGlobalSearch"
        @filter="onGlobalFilter"
      />

      <!-- Batch action toolbar (replaces filter bar when selected) -->
      <BatchActionToolbar
        v-if="selectedCount > 0"
        :selected-count="selectedCount"
        :selected-issues="selectedIssues"
        @deselect-all="clearSelection"
        @batch-state="onBatchState"
        @batch-assign="onBatchAssign"
        @batch-sprint="onBatchSprint"
        @batch-priority="onBatchPriority"
      />

      <!-- Filter bar -->
      <div v-else class="filter-bar">
        <div class="filter-left">
          <span class="current-query-name">{{ activeQueryName }}</span>
          <span class="issue-total-badge">{{ totalIssues }} 个问题</span>
        </div>
        <div class="filter-right">
          <a-select v-model="filterProject" placeholder="所有项目" size="small" style="width: 120px" allow-clear @change="onFilterChange">
            <a-option v-for="p in projectList" :key="p.id" :value="p.id">{{ p.key }}</a-option>
          </a-select>
          <a-button size="small" @click="toggleInlineCreate">
            {{ showInlineCreate ? '取消' : '快速创建' }}
          </a-button>
          <a-button type="primary" size="small" @click="showCreatePanel = true">创建工单</a-button>
          <ColumnConfigPopover
            :standard-columns="standardColumns"
            :custom-field-columns="customFieldColumns"
            :is-visible="isColumnVisible"
            @toggle="toggleColumn"
            @reset="resetColumns"
          />
        </div>
      </div>

      <!-- Inline quick create -->
      <div v-if="showInlineCreate && selectedCount === 0" class="inline-create">
        <div class="inline-create-row">
          <a-select v-model="quickForm.projectId" placeholder="项目" size="small" style="width: 140px" allow-search>
            <a-option v-for="p in projectList" :key="p.id" :value="p.id">{{ p.key }} - {{ p.name }}</a-option>
          </a-select>
          <a-input
            v-model="quickForm.title"
            placeholder="输入工单标题后按 Enter 快速创建..."
            size="small"
            class="inline-title-input"
            @keyup.enter="quickCreate"
          />
          <a-select v-model="quickForm.issueType" size="small" style="width: 80px">
            <a-option value="Task">任务</a-option>
            <a-option value="Bug">缺陷</a-option>
            <a-option value="Feature">需求</a-option>
          </a-select>
          <a-select v-model="quickForm.priority" size="small" style="width: 80px">
            <a-option value="Normal">普通</a-option>
            <a-option value="High">高</a-option>
            <a-option value="Critical">紧急</a-option>
            <a-option value="Low">低</a-option>
          </a-select>
          <a-button type="primary" size="small" :loading="quickCreating" :disabled="!quickForm.projectId || !quickForm.title" @click="quickCreate">
            创建
          </a-button>
        </div>
      </div>

      <!-- Issue table -->
      <a-table
        class="issue-table"
        :data="issues"
        :columns="tableColumns"
        :loading="loading"
        :pagination="false"
        :row-selection="rowSelection"
        :selected-keys="selectedKeysArray"
        row-key="id"
        :bordered="false"
        :stripe="false"
        column-resizable
        size="medium"
        :scroll="{ x: tableMinWidth }"
        @row-click="onRowClick"
        @selection-change="onSelectionChange"
        @column-resize="onColumnResize"
      >
        <!-- Custom column header (shared slot for all columns) -->
        <template #column-header="{ column }">
          <DraggableColumnHeader
            :column-key="column.dataIndex"
            :label="column.title"
            :sortable="isColumnSortable(column.dataIndex)"
            :sort-dir="getColumnSortDir(column.dataIndex)"
            :fixed="isColumnFixed(column.dataIndex)"
            @sort="onHeaderSort"
            @remove="onHeaderRemove"
            @drag-drop="onHeaderDragDrop"
          />
        </template>

        <!-- Cell slots -->
        <template #issueKey="{ record }">
          <span class="issue-key">{{ record.issueKey }}</span>
        </template>
        <template #title-cell="{ record }">
          <span class="issue-title-text">{{ record.title }}</span>
        </template>
        <template #assignee="{ record }">
          <div @click.stop>
            <a-trigger v-if="canEditIssue(record)" v-model:popup-visible="assigneeDropdowns[record.id]" trigger="click" position="bl" :popup-offset="4">
              <span class="editable-cell" @click="openAssigneeEdit(record)">
                {{ record.assigneeName || '\u2014' }}
                <icon-loading v-if="isCellEditing(record.id, 'assigneeId')" class="cell-spinner" />
              </span>
              <template #content>
                <div class="inline-dropdown member-dropdown">
                  <div class="dropdown-search">
                    <a-input v-model="assigneeSearch" placeholder="搜索成员..." size="mini" allow-clear @keydown.stop><template #prefix><icon-search /></template></a-input>
                  </div>
                  <div v-if="assigneeOptionsLoading" class="dropdown-loading"><a-spin :size="16" /></div>
                  <template v-else>
                    <div class="dropdown-item" @click="selectAssignee(record, null)"><span class="unassigned-icon">&mdash;</span><span>未分配</span></div>
                    <div v-for="m in filteredAssigneeOptions" :key="m.userId" class="dropdown-item" @click="selectAssignee(record, m)">
                      <span class="member-avatar">{{ m.displayName?.charAt(0) }}</span><span>{{ m.displayName }}</span>
                    </div>
                  </template>
                </div>
              </template>
            </a-trigger>
            <span v-else class="readonly-cell">{{ record.assigneeName || '\u2014' }}</span>
          </div>
        </template>
        <template #status="{ record }">
          <div @click.stop>
            <a-trigger v-if="canEditIssue(record)" v-model:popup-visible="statusDropdowns[record.id]" trigger="click" position="bl" :popup-offset="4">
              <span class="editable-cell status-badge" :style="{ background: getStatusColor(record.statusId) }" @click="openStatusEdit(record)">
                {{ getStatusName(record.statusId) }}
                <icon-loading v-if="isCellEditing(record.id, 'statusId')" class="cell-spinner" />
              </span>
              <template #content>
                <div class="inline-dropdown">
                  <div v-if="transitionsLoading[record.id]" class="dropdown-loading"><a-spin :size="16" /></div>
                  <template v-else>
                    <div v-for="st in availableTransitions[record.id]" :key="st.id" class="dropdown-item" @click="selectStatus(record, st)">
                      <span class="status-dot" :style="{ background: st.color }"></span><span>{{ st.name }}</span>
                    </div>
                    <div v-if="(availableTransitions[record.id] || []).length === 0" class="dropdown-empty">无可用转换</div>
                  </template>
                </div>
              </template>
            </a-trigger>
            <span v-else class="readonly-cell status-badge" :style="{ background: getStatusColor(record.statusId) }">{{ getStatusName(record.statusId) }}</span>
          </div>
        </template>
        <template #sprint="{ record }">
          <div @click.stop>
            <a-trigger v-if="canEditIssue(record)" v-model:popup-visible="sprintDropdowns[record.id]" trigger="click" position="bl" :popup-offset="4">
              <span class="editable-cell" @click="openSprintEdit(record)">
                {{ getSprintName(record.sprintId) || '\u2014' }}
                <icon-loading v-if="isCellEditing(record.id, 'sprintId')" class="cell-spinner" />
              </span>
              <template #content>
                <div class="inline-dropdown">
                  <div v-if="sprintOptionsLoading[record.id]" class="dropdown-loading"><a-spin :size="16" /></div>
                  <template v-else>
                    <div class="dropdown-item" @click="selectSprint(record, null)"><span>无 Sprint</span></div>
                    <template v-for="group in getSprintGroups(record.projectId)" :key="group.label">
                      <div class="dropdown-group-label">{{ group.label }}</div>
                      <div v-for="s in group.items" :key="s.id" class="dropdown-item" @click="selectSprint(record, s)"><span>{{ s.name }}</span></div>
                    </template>
                  </template>
                </div>
              </template>
            </a-trigger>
            <span v-else class="readonly-cell">{{ getSprintName(record.sprintId) || '\u2014' }}</span>
          </div>
        </template>
        <template #priority="{ record }">
          <div @click.stop>
            <a-trigger v-if="canEditIssue(record)" v-model:popup-visible="priorityDropdowns[record.id]" trigger="click" position="bl" :popup-offset="4">
              <span class="editable-cell" @click="priorityDropdowns[record.id] = true">
                <span class="priority-dot" :class="'priority-' + (record.priority || 'normal').toLowerCase()"></span>
                {{ record.priority || 'Normal' }}
                <icon-loading v-if="isCellEditing(record.id, 'priority')" class="cell-spinner" />
              </span>
              <template #content>
                <div class="inline-dropdown">
                  <div v-for="p in priorityOptions" :key="p.value" class="dropdown-item" @click="selectPriority(record, p.value)">
                    <span class="priority-dot" :class="'priority-' + p.value.toLowerCase()"></span><span>{{ p.label }}</span>
                  </div>
                </div>
              </template>
            </a-trigger>
            <span v-else class="readonly-cell">
              <span class="priority-dot" :class="'priority-' + (record.priority || 'normal').toLowerCase()"></span>
              {{ record.priority || 'Normal' }}
            </span>
          </div>
        </template>
        <template #updatedAt="{ record }"><span class="time-ago">{{ formatTime(record.updatedAt) }}</span></template>
        <template #issueType="{ record }"><span class="type-label">{{ record.issueType }}</span></template>
        <template #reporter="{ record }"><span class="reporter-name">{{ record.reporterId || '\u2014' }}</span></template>
        <template #createdAt="{ record }"><span class="time-ago">{{ formatTime(record.createdAt) }}</span></template>
        <template #dueDate="{ record }"><span class="time-ago">{{ record.dueDate || '\u2014' }}</span></template>

        <!-- empty -->
        <template #empty>
          <div class="empty-state">
            <icon-search class="empty-icon" />
            <p class="empty-title">暂无工单</p>
            <p class="empty-desc">尝试调整筛选条件或创建新的工单</p>
            <a-button type="primary" size="small" @click="toggleInlineCreate">创建工单</a-button>
          </div>
        </template>
      </a-table>

      <!-- Pagination -->
      <div class="pagination-bar" v-if="totalIssues > 0">
        <a-pagination v-model:current="currentPage" :total="totalIssues" :page-size="pageSize" size="small" show-total @change="goPage" />
      </div>
    </section>

    <!-- Create issue panel -->
    <IssueCreatePanel v-model:visible="showCreatePanel" :project-id="activeProjectId || undefined" @created="refreshList" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { IconPlus, IconSearch, IconLoading } from '@arco-design/web-vue/es/icon'
import { Message } from '@arco-design/web-vue'
import { projectApi, issueApi, queryApi, sprintApi } from '@/api'
import type { IssueVO, IssueStatusVO, ProjectMemberVO, SprintVO } from '@/api/types'
import type { TableData } from '@arco-design/web-vue'
import { useIssueList, useSelection, useInlineEdit, useBatchOps, usePermission, useColumnConfig } from './composables'
import BatchActionToolbar from './components/BatchActionToolbar.vue'
import DraggableColumnHeader from './components/DraggableColumnHeader.vue'
import IssueCreatePanel from './IssueCreatePanel.vue'
import ColumnConfigPopover from './components/ColumnConfigPopover.vue'
import FilterBar from './components/FilterBar.vue'

const router = useRouter()
const route = useRoute()

// Composables
const {
  issues, totalIssues, currentPage, pageSize, loading,
  sortState, loadIssues, goPage, updateLocalIssue
} = useIssueList()

const {
  selectedIds, selectedCount, selectedIssues,
  clearSelection
} = useSelection(issues)

const { isCellEditing, executeEdit } = useInlineEdit(issues)
const { batchTransitStatus, batchAssign, batchUpdateSprint, batchUpdatePriority } = useBatchOps()
const { loadPermissions, canEditIssue } = usePermission(issues)

// Panel state (declared before useColumnConfig so it can be passed as ref)
const activeProjectId = ref<string | null>(null)

const {
  visibleColumns, toggleColumn, reorderColumn,
  standardColumns, customFieldColumns, isVisible: isColumnVisible, resetToDefault: resetColumns
} = useColumnConfig(activeProjectId as any)

// Panel state
const savedQueries = ref<any[]>([])
const projectList = ref<any[]>([])
const activeQueryId = ref<string | null>(null)
const activeQueryName = ref('\u6240\u6709\u5de5\u5355') // "所有工单"
const expandedGroups = reactive(new Set<string>(['saved', 'projects']))
const panelSearch = ref('')

// Panel resize
const PANEL_WIDTH_KEY = 'trackflow:panel-width'
const panelWidth = ref(loadPanelWidth())

function loadPanelWidth(): number {
  try {
    const stored = localStorage.getItem(PANEL_WIDTH_KEY)
    if (stored) return Math.max(200, Math.min(500, Number(stored)))
  } catch { /* ignore */ }
  return 280
}

function startPanelResize(e: MouseEvent) {
  e.preventDefault()
  const startX = e.clientX
  const startWidth = panelWidth.value

  function onMove(ev: MouseEvent) {
    const delta = ev.clientX - startX
    panelWidth.value = Math.max(200, Math.min(500, startWidth + delta))
  }
  function onUp() {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    localStorage.setItem(PANEL_WIDTH_KEY, String(panelWidth.value))
  }
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

const panelWidthBeforeCollapse = ref(280)

function togglePanelCollapse() {
  if (panelWidth.value <= 200) {
    // 当前已是最小宽度，恢复到之前记录的宽度
    panelWidth.value = panelWidthBeforeCollapse.value
  } else {
    // 记录当前宽度，然后缩到最小
    panelWidthBeforeCollapse.value = panelWidth.value
    panelWidth.value = 200
  }
  localStorage.setItem(PANEL_WIDTH_KEY, String(panelWidth.value))
}

// Filters
const filterProject = ref<string | undefined>(undefined)
const searchKeyword = ref('')
const globalFilterParams = ref<Record<string, any>>({})
const initialFilterChips = ref<any[]>([])

function onGlobalSearch(keyword: string) {
  searchKeyword.value = keyword
  globalFilterParams.value = {}
  currentPage.value = 1
  refreshList()
}

function onGlobalFilter(filters: Record<string, any>) {
  searchKeyword.value = ''
  globalFilterParams.value = filters
  currentPage.value = 1
  refreshList()
}

// Quick create
const showInlineCreate = ref(false)
const showCreatePanel = ref(false)
const quickCreating = ref(false)
const quickForm = reactive({
  projectId: undefined as string | undefined,
  title: '',
  issueType: 'Task',
  priority: 'Normal'
})

// Status cache
const statusCache = ref<IssueStatusVO[]>([])

// Inline edit dropdowns
const statusDropdowns = reactive<Record<string, boolean>>({})
const assigneeDropdowns = reactive<Record<string, boolean>>({})
const sprintDropdowns = reactive<Record<string, boolean>>({})
const priorityDropdowns = reactive<Record<string, boolean>>({})
const transitionsLoading = reactive<Record<string, boolean>>({})
const availableTransitions = reactive<Record<string, IssueStatusVO[]>>({})
const assigneeSearch = ref('')
const assigneeOptions = ref<ProjectMemberVO[]>([])
const assigneeOptionsLoading = ref(false)
const sprintOptionsLoading = reactive<Record<string, boolean>>({})
const sprintOptionsCache = reactive<Record<string, SprintVO[]>>({})

const priorityOptions = [
  { value: 'Critical', label: '\u7D27\u6025' },
  { value: 'High', label: '\u9AD8' },
  { value: 'Normal', label: '\u666E\u901A' },
  { value: 'Low', label: '\u4F4E' }
]

// Column widths — default values, user can resize via drag
const COLUMN_WIDTH_STORAGE_KEY = 'trackflow:issue-column-widths'
const DEFAULT_COLUMN_WIDTHS: Record<string, number> = {
  issueKey: 130,
  title: 300, // min width for title
  assignee: 110,
  status: 120,
  sprint: 160,
  priority: 100,
  updatedAt: 100,
  issueType: 80,
  reporter: 110,
  createdAt: 110,
  dueDate: 100
}

const columnWidths = reactive<Record<string, number>>(loadColumnWidths())

function loadColumnWidths(): Record<string, number> {
  try {
    const stored = localStorage.getItem(COLUMN_WIDTH_STORAGE_KEY)
    if (stored) return { ...DEFAULT_COLUMN_WIDTHS, ...JSON.parse(stored) }
  } catch { /* ignore */ }
  return { ...DEFAULT_COLUMN_WIDTHS }
}

/**
 * 表格最小总宽度 = 各可见列宽度之和 + checkbox列(60px)
 */
const tableMinWidth = computed(() => {
  const sum = visibleColumns.value
    .filter(c => c.key !== 'checkbox')
    .reduce((acc, col) => {
      return acc + (columnWidths[col.key] || DEFAULT_COLUMN_WIDTHS[col.key] || 100)
    }, 0)
  return sum + 60
})

/**
 * 动态生成 a-table columns 配置
 */
const tableColumns = computed(() => {
  return visibleColumns.value
    .filter(c => c.key !== 'checkbox')
    .map(col => ({
      title: col.label,
      dataIndex: col.key,
      slotName: col.key === 'title' ? 'title-cell' : col.key,
      titleSlotName: 'column-header',
      width: columnWidths[col.key] || DEFAULT_COLUMN_WIDTHS[col.key] || 100,
      ellipsis: true,
      tooltip: col.key === 'title'
    }))
})

// Row selection config for a-table
const rowSelection = reactive({
  type: 'checkbox' as const,
  showCheckedAll: true
})

// a-table event handlers
const selectedKeysArray = computed(() => [...selectedIds.value])

function onRowClick(record: TableData) {
  router.push(`/issues/${record.id}`)
}
function onSelectionChange(rowKeys: (string | number)[]) {
  selectedIds.value = new Set(rowKeys.map(String))
}
function onColumnResize(dataIndex: string, width: number) {
  columnWidths[dataIndex] = width
  localStorage.setItem(COLUMN_WIDTH_STORAGE_KEY, JSON.stringify(columnWidths))
}

// Column header interactions
function onHeaderSort(key: string) {
  // 三态切换: null → asc → desc → null
  if (sortState.value.field !== key) {
    sortState.value = { field: key, direction: 'asc' }
  } else if (sortState.value.direction === 'asc') {
    sortState.value = { field: key, direction: 'desc' }
  } else {
    sortState.value = { field: null, direction: null }
  }
}
function onHeaderRemove(key: string) {
  toggleColumn(key)
}
function onHeaderDragDrop(fromKey: string, toKey: string) {
  reorderColumn(fromKey, toKey)
}
function getColumnSortDir(key: string): 'asc' | 'desc' | null {
  if (sortState.value.field === key) return sortState.value.direction
  return null
}
function isColumnFixed(key: string): boolean {
  const col = visibleColumns.value.find(c => c.key === key)
  return col?.fixed === true
}
function isColumnSortable(key: string): boolean {
  const col = visibleColumns.value.find(c => c.key === key)
  return col?.sortable === true
}

// Status helpers
function getStatusName(id: string) {
  const s = statusCache.value.find(st => st.id === id)
  return s?.name || '\u672A\u77E5'
}
function getStatusColor(id: string) {
  const s = statusCache.value.find(st => st.id === id)
  return s?.color || '#666'
}
function getSprintName(sprintId?: string) {
  if (!sprintId) return ''
  for (const sprints of Object.values(sprintOptionsCache)) {
    const found = (sprints as SprintVO[]).find(s => s.id === sprintId)
    if (found) return found.name
  }
  return ''
}

// Inline edit - Status
async function openStatusEdit(issue: IssueVO) {
  if (isCellEditing(issue.id, 'statusId')) return
  statusDropdowns[issue.id] = true
  transitionsLoading[issue.id] = true
  try {
    const res = await issueApi.getAvailableTransitions(issue.id)
    availableTransitions[issue.id] = res.data || []
  } catch {
    availableTransitions[issue.id] = []
    Message.error({ content: '\u83B7\u53D6\u53EF\u7528\u72B6\u6001\u5931\u8D25', duration: 3000 })
    statusDropdowns[issue.id] = false
  } finally {
    transitionsLoading[issue.id] = false
  }
}
function selectStatus(issue: IssueVO, status: IssueStatusVO) {
  statusDropdowns[issue.id] = false
  executeEdit(issue.id, 'statusId', status.id, (_signal) => issueApi.transitStatus(issue.id, status.id))
}

// Inline edit - Assignee
async function openAssigneeEdit(issue: IssueVO) {
  if (isCellEditing(issue.id, 'assigneeId')) return
  assigneeDropdowns[issue.id] = true
  assigneeSearch.value = ''
  assigneeOptionsLoading.value = true
  try {
    const res = await projectApi.listMembers(issue.projectId)
    assigneeOptions.value = res.data || []
  } catch {
    assigneeOptions.value = []
  } finally {
    assigneeOptionsLoading.value = false
  }
}
const filteredAssigneeOptions = computed(() => {
  if (!assigneeSearch.value) return assigneeOptions.value
  const kw = assigneeSearch.value.toLowerCase()
  return assigneeOptions.value.filter(m => m.displayName?.toLowerCase().includes(kw))
})
function selectAssignee(issue: IssueVO, member: ProjectMemberVO | null) {
  Object.keys(assigneeDropdowns).forEach(k => { assigneeDropdowns[k] = false })
  executeEdit(
    issue.id, 'assigneeId', member?.userId || null,
    (_signal) => issueApi.assign(issue.id, member?.userId || ''),
    () => ({ assigneeId: member?.userId || undefined, assigneeName: member?.displayName || undefined })
  )
}

// Inline edit - Sprint
async function openSprintEdit(issue: IssueVO) {
  if (isCellEditing(issue.id, 'sprintId')) return
  sprintDropdowns[issue.id] = true
  if (!sprintOptionsCache[issue.projectId]) {
    sprintOptionsLoading[issue.id] = true
    try {
      const res = await sprintApi.listByProject(issue.projectId)
      sprintOptionsCache[issue.projectId] = res.data || []
    } catch {
      sprintOptionsCache[issue.projectId] = []
    } finally {
      sprintOptionsLoading[issue.id] = false
    }
  }
}
function getSprintGroups(projectId: string) {
  const sprints = sprintOptionsCache[projectId] || []
  const groups: { label: string; items: SprintVO[] }[] = []
  const active = sprints.filter(s => s.status === 'Active')
  const planned = sprints.filter(s => s.status === 'Planned')
  const completed = sprints.filter(s => s.status === 'Completed')
  if (active.length) groups.push({ label: '\u8FDB\u884C\u4E2D', items: active })
  if (planned.length) groups.push({ label: '\u8BA1\u5212\u4E2D', items: planned })
  if (completed.length) groups.push({ label: '\u5DF2\u5B8C\u6210', items: completed })
  return groups
}
function selectSprint(issue: IssueVO, sprint: SprintVO | null) {
  sprintDropdowns[issue.id] = false
  executeEdit(issue.id, 'sprintId', sprint?.id || null, (_signal) => issueApi.update(issue.id, { sprintId: sprint?.id || null }))
}

// Inline edit - Priority
function selectPriority(issue: IssueVO, priority: string) {
  priorityDropdowns[issue.id] = false
  executeEdit(issue.id, 'priority', priority, (_signal) => issueApi.update(issue.id, { priority }))
}

// Batch operation handlers
async function onBatchState(statusId: string) {
  const result = await batchTransitStatus(selectedIssues.value, statusId)
  if (result.succeeded > 0) {
    selectedIssues.value.forEach(issue => {
      if (!result.failures.find(f => f.issueId === issue.id)) {
        updateLocalIssue(issue.id, { statusId })
      }
    })
  }
  clearSelection()
}
async function onBatchAssign(assigneeId: string | null) {
  await batchAssign(selectedIssues.value, assigneeId || '')
  refreshList()
  clearSelection()
}
async function onBatchSprint(sprintId: string | null) {
  const result = await batchUpdateSprint(selectedIssues.value, sprintId)
  if (result.succeeded > 0) {
    selectedIssues.value.forEach(issue => {
      if (!result.failures.find(f => f.issueId === issue.id)) {
        updateLocalIssue(issue.id, { sprintId: sprintId || undefined })
      }
    })
  }
  clearSelection()
}
async function onBatchPriority(priority: string) {
  const result = await batchUpdatePriority(selectedIssues.value, priority)
  if (result.succeeded > 0) {
    selectedIssues.value.forEach(issue => {
      if (!result.failures.find(f => f.issueId === issue.id)) {
        updateLocalIssue(issue.id, { priority })
      }
    })
  }
  clearSelection()
}

// Panel helpers
const filteredQueries = computed(() => {
  if (!panelSearch.value) return savedQueries.value
  const kw = panelSearch.value.toLowerCase()
  return savedQueries.value.filter((q: any) => q.name.toLowerCase().includes(kw))
})
function toggleGroup(group: string) {
  if (expandedGroups.has(group)) expandedGroups.delete(group)
  else expandedGroups.add(group)
}
function formatCount(count: number) {
  if (count >= 10000) return Math.floor(count / 1000) + 'k+'
  if (count >= 1000) return (count / 1000).toFixed(1) + 'k'
  return String(count)
}
function formatTime(dt: string) {
  if (!dt) return ''
  const d = new Date(dt)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 60) return `${mins}\u5206\u949F\u524D`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}\u5C0F\u65F6\u524D`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days}\u5929\u524D`
  return d.toLocaleDateString('zh-CN')
}
function toggleInlineCreate() {
  showInlineCreate.value = !showInlineCreate.value
  if (showInlineCreate.value && activeProjectId.value) {
    quickForm.projectId = activeProjectId.value
  }
}
async function quickCreate() {
  if (!quickForm.projectId || !quickForm.title.trim()) return
  quickCreating.value = true
  try {
    await issueApi.create({ projectId: quickForm.projectId, title: quickForm.title.trim(), issueType: quickForm.issueType, priority: quickForm.priority })
    Message.success('\u5DE5\u5355\u521B\u5EFA\u6210\u529F')
    quickForm.title = ''
    refreshList()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '\u521B\u5EFA\u5931\u8D25')
  } finally {
    quickCreating.value = false
  }
}

function buildFilters() {
  const filters: Record<string, any> = {}
  if (activeProjectId.value) filters.projectId = activeProjectId.value
  if (filterProject.value) filters.projectId = filterProject.value
  if (activeQueryId.value) filters.queryId = activeQueryId.value
  if (searchKeyword.value.trim()) filters.keyword = searchKeyword.value.trim()
  // Merge global filter params (from FilterBar's filter mode)
  Object.assign(filters, globalFilterParams.value)
  return filters
}
function refreshList() { loadIssues(buildFilters()).then(() => { loadPermissions(); preloadSprintNames() }) }

/** 预加载当前列表中涉及到的 sprint 名称 */
async function preloadSprintNames() {
  const projectIds = [...new Set(issues.value.map(i => i.projectId).filter(Boolean))]
  const toLoad = projectIds.filter(pid => !sprintOptionsCache[pid])
  await Promise.all(toLoad.map(async (pid) => {
    try {
      const res = await sprintApi.listByProject(pid)
      sprintOptionsCache[pid] = res.data || []
    } catch {
      sprintOptionsCache[pid] = []
    }
  }))
}
function onFilterChange() { currentPage.value = 1; refreshList() }
function selectQuery(q: any) {
  activeQueryId.value = q.id; activeQueryName.value = q.name; activeProjectId.value = null; currentPage.value = 1; refreshList()
}
function selectProject(p: any) {
  activeProjectId.value = p.id; activeQueryId.value = null; activeQueryName.value = p.name; currentPage.value = 1; refreshList()
}

watch(currentPage, () => refreshList())
watch(sortState, () => refreshList(), { deep: true })

// Init
async function loadPanel() {
  try {
    const res = await queryApi.getPanel()
    const data = res.data || {}
    savedQueries.value = [...(data.pinned || []), ...(data.queries || [])]
  } catch {
    savedQueries.value = [
      { id: '1', name: 'Assigned to me', count: 12 },
      { id: '2', name: 'Reported by me', count: 8 },
      { id: '3', name: 'All open', count: 45 }
    ]
  }
}
async function loadProjects() {
  try { const res = await projectApi.list({ pageSize: 50 }); projectList.value = res.data?.list || [] }
  catch { projectList.value = [] }
}
async function loadStatuses() {
  try { const res = await issueApi.listStatuses(); statusCache.value = res.data || [] }
  catch { statusCache.value = [] }
}

onMounted(async () => {
  if (route.query.project) activeProjectId.value = String(route.query.project)

  await loadPanel()
  loadProjects()
  await loadStatuses()

  // Handle dashboard filter params (statusId, label, sprint, etc.)
  if (route.query.statusId || route.query.overdue || route.query.dueSoon || route.query.sprint) {
    applyDashboardFilter()
  } else {
    refreshList()
  }
})

function applyDashboardFilter() {
  const filters: Record<string, any> = {}
  const chips: any[] = []

  if (route.query.statusId) {
    const statusIds = String(route.query.statusId).split(',')
    filters.statusId = String(route.query.statusId)

    // Build chip with status names
    const statusNames = statusIds.map(id => {
      const s = statusCache.value.find(st => st.id === id)
      return s?.name || id
    })
    chips.push({
      fieldKey: 'status',
      operator: 'any_of',
      values: statusIds,
      valueLabels: statusNames
    })
  }

  if (route.query.overdue) {
    filters.overdue = 'true'
    // No chip needed — displayed in label
  }

  if (route.query.dueSoon) {
    filters.dueSoon = 'true'
  }

  if (route.query.sprint) {
    filters.sprintId = String(route.query.sprint)
    chips.push({
      fieldKey: 'sprint',
      operator: 'equals',
      values: [String(route.query.sprint)],
      valueLabels: ['Sprint']
    })
  }

  // Set display label
  if (route.query.label) {
    activeQueryName.value = String(route.query.label)
  }

  initialFilterChips.value = chips
  globalFilterParams.value = filters
  refreshList()
}
</script>

<style scoped>
.issue-page { display: flex; height: 100%; }

/* Left panel */
.query-panel { background: var(--tf-bg-surface); overflow-y: auto; overflow-x: hidden; flex-shrink: 0; display: flex; flex-direction: column; transition: width 0.2s ease; }
.panel-resizer { width: 4px; flex-shrink: 0; cursor: col-resize; background: transparent; position: relative; z-index: 2; transition: background 0.15s; }
.panel-resizer:hover, .panel-resizer:active { background: var(--tf-accent, #58a6ff); }
.panel-resizer::after { content: ''; position: absolute; top: 0; bottom: 0; left: -2px; right: -2px; }
.panel-top { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px 8px; }
.panel-top-title { display: flex; align-items: center; gap: 8px; }
.panel-label { font-size: 14px; font-weight: 500; color: var(--tf-text-primary); }
.panel-total { font-size: 11px; color: var(--tf-text-tertiary); background: var(--tf-bg-elevated); padding: 2px 6px; border-radius: 8px; }
.panel-search { padding: 4px 10px 8px; }
.query-group { padding: 0 6px; margin-bottom: 2px; }
.group-header { display: flex; align-items: center; gap: 4px; height: 32px; padding: 0 8px; cursor: pointer; border-radius: 4px; transition: background 0.15s; }
.group-header:hover { background: var(--tf-bg-hover); }
.group-arrow { font-size: 10px; width: 14px; color: var(--tf-text-tertiary); }
.group-title { font-size: 11px; color: var(--tf-text-tertiary); font-weight: 500; text-transform: uppercase; letter-spacing: 0.6px; }
.group-items { padding-left: 8px; }
.query-item { display: flex; align-items: center; justify-content: space-between; height: 32px; padding: 0 12px; cursor: pointer; border-radius: 4px; margin: 1px 0; transition: background 0.15s; }
.query-item:hover { background: var(--tf-bg-hover); }
.query-item.active { background: var(--tf-accent-bg); color: var(--tf-accent); }
.query-name { font-size: 13px; color: var(--tf-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.query-item.active .query-name { color: var(--tf-accent); }
.query-count { font-size: 11px; color: var(--tf-text-tertiary); flex-shrink: 0; margin-left: 8px; }
.empty-queries { padding: 12px; font-size: 12px; color: var(--tf-text-tertiary); text-align: center; }

/* Right area */
.issue-list-area { flex: 1; display: flex; flex-direction: column; min-width: 0; overflow: hidden; }

.filter-bar { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid var(--tf-border); flex-shrink: 0; }
.filter-left { display: flex; align-items: center; gap: 12px; }
.current-query-name { font-size: 14px; font-weight: 500; color: var(--tf-text-primary); }
.issue-total-badge { font-size: 12px; color: var(--tf-text-tertiary); }
.filter-right { display: flex; gap: 8px; align-items: center; }

/* Inline create */
.inline-create { padding: 8px 16px; background: var(--tf-bg-surface); border-bottom: 1px solid var(--tf-border); flex-shrink: 0; }
.inline-create-row { display: flex; align-items: center; gap: 8px; }
.inline-title-input { flex: 1; }

/* Table */
.issue-table { flex: 1; min-height: 0; overflow: hidden; }
.issue-table :deep(.arco-table) { font-size: 13px; }
.issue-table :deep(.arco-scrollbar-container.arco-table-content) { overflow-y: auto !important; }
.issue-table :deep(.arco-table-th) { font-size: 11px; color: var(--tf-text-tertiary); text-transform: uppercase; letter-spacing: 0.5px; background: var(--tf-bg-surface); }
.issue-table :deep(.arco-table-tr) { cursor: pointer; transition: background 0.15s; }
.issue-table :deep(.arco-table-tr:hover .arco-table-td) { background: var(--tf-bg-hover); }
.issue-table :deep(.arco-table-td) { padding: 8px 12px; }
.issue-table :deep(.arco-table-col-resize-handle) { width: 3px; background: transparent; transition: background 0.15s; }
.issue-table :deep(.arco-table-col-resize-handle:hover),
.issue-table :deep(.arco-table-col-resize-handle.active) { background: var(--tf-accent); }

.issue-key { color: var(--tf-accent); font-weight: 500; font-size: 12px; }
.issue-title-text { color: var(--tf-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; display: block; }
.type-label { font-size: 12px; color: var(--tf-text-secondary); }
.reporter-name { font-size: 12px; color: var(--tf-text-secondary); }

/* Editable cells */
.editable-cell { display: inline-flex; align-items: center; gap: 4px; cursor: pointer; padding: 2px 6px; border-radius: 3px; transition: background 0.15s; font-size: 12px; color: var(--tf-text-secondary); }
.editable-cell:hover { background: var(--tf-bg-hover); }
.readonly-cell { display: inline-flex; align-items: center; gap: 4px; padding: 2px 6px; font-size: 12px; color: var(--tf-text-secondary); cursor: default; }
.cell-spinner { font-size: 12px; color: var(--tf-text-tertiary); animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

.status-badge { padding: 2px 8px; border-radius: 3px; font-size: 11px; color: #fff; font-weight: 500; }
.priority-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; }
.priority-critical { background: var(--tf-danger); }
.priority-high { background: var(--tf-warning); }
.priority-normal { background: var(--tf-accent); }
.priority-low { background: var(--tf-text-tertiary); }
.time-ago { font-size: 11px; color: var(--tf-text-tertiary); }

/* Inline dropdowns */
.inline-dropdown { background: var(--tf-bg-elevated); border: 1px solid var(--tf-border); border-radius: 6px; padding: 4px; min-width: 150px; max-height: 240px; overflow-y: auto; }
.member-dropdown { min-width: 200px; }
.dropdown-search { padding: 4px; margin-bottom: 4px; }
.dropdown-item { display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-radius: 4px; cursor: pointer; font-size: 13px; color: var(--tf-text-primary); transition: background 0.15s; }
.dropdown-item:hover { background: var(--tf-bg-hover); }
.dropdown-group-label { padding: 6px 8px 2px; font-size: 11px; color: var(--tf-text-tertiary); text-transform: uppercase; letter-spacing: 0.5px; font-weight: 500; }
.dropdown-loading { display: flex; justify-content: center; padding: 12px; }
.dropdown-empty { padding: 12px; text-align: center; font-size: 12px; color: var(--tf-text-tertiary); }
.status-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.member-avatar { width: 24px; height: 24px; border-radius: 50%; background: var(--tf-accent-bg); color: var(--tf-accent); display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 500; flex-shrink: 0; }
.unassigned-icon { width: 24px; text-align: center; color: var(--tf-text-tertiary); }

/* Empty state */
.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 48px 24px; gap: 8px; }
.empty-icon { font-size: 36px; color: var(--tf-text-quaternary); }
.empty-title { font-size: 14px; font-weight: 500; color: var(--tf-text-primary); margin: 0; }
.empty-desc { font-size: 12px; color: var(--tf-text-tertiary); margin: 0 0 8px; }

/* Pagination */
.pagination-bar { display: flex; justify-content: center; padding: 12px 16px; border-top: 1px solid var(--tf-border); flex-shrink: 0; }
</style>
