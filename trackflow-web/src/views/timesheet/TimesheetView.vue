<template>
  <div class="timesheet-page">
    <!-- Header -->
    <div class="timesheet-header">
      <h1 class="page-title">时间表</h1>
    </div>

    <!-- Tab 导航 -->
    <a-tabs v-model:active-key="activeTab" class="timesheet-tabs" @change="(key) => switchTab(key as 'people' | 'projects' | 'workgroups')">

      <!-- ===== 人员视图 ===== -->
      <a-tab-pane key="people" title="人员">
        <PeopleTab
          :date-range-label="dateRangeLabel"
          :total-time-label="totalTimeLabel"
          :view-mode="viewMode"
          :week-days="weekDays"
          :month-days="monthDays"
          :entries="timeEntries"
          :quota-minutes="minutesPerDay()"
          :quota-text="quotaText()"
          :loading="loading"
          :current-user-id="authStore.user?.id"
          :current-user-name="currentUserName"
          :can-view-others="canViewOthers"
          :can-log-time="canLogTime"
          :selected-user-id="selectedUserId"
          :selectable-users="selectableUsers"
          :filter-project-id="filterProjectId"
          :filter-work-type="filterWorkType"
          :filter-projects="filterProjects"
          :filter-work-types="filterWorkTypes"
          @navigate="navigate"
          @go-today="goToday"
          @update:view-mode="viewMode = $event; onViewModeChange()"
          @open-add="openAddDialog"
          @open-edit="openEditDialog"
          @search-users="searchUsers"
          @user-change="onUserChange"
          @filter-change="onFilterChangeFromTab"
          @reset-filters="resetFilters"
        />
      </a-tab-pane>

      <!-- ===== 项目视图 ===== -->
      <a-tab-pane key="projects" title="项目">
        <ProjectsTab
          :date-range-label="dateRangeLabel"
          :total-time-label="totalTimeLabel"
          :view-mode="viewMode"
          :week-days="weekDays"
          :month-days="monthDays"
          :project-entries="projectEntries"
          :project-summaries="projectSummaries"
          :selected-project-id="selectedProjectId"
          :loading="loading"
          :load-error="loadError"
          @navigate="navigate"
          @go-today="goToday"
          @update:view-mode="viewMode = $event; onViewModeChange()"
          @project-change="onProjectChange"
          @refresh="refresh"
        />
      </a-tab-pane>

      <!-- ===== 工作群组视图 ===== -->
      <a-tab-pane key="workgroups" title="工作群组">
        <WorkgroupsTab
          :date-range-label="dateRangeLabel"
          :total-time-label="totalTimeLabel"
          :view-mode="viewMode"
          :group-summaries="groupSummaries"
          :expanded-groups="expandedGroups"
          :loading="loading"
          @navigate="navigate"
          @go-today="goToday"
          @update:view-mode="viewMode = $event; onViewModeChange()"
          @toggle-expand="toggleGroupExpand"
          @open-edit="openEditDialog"
        />
      </a-tab-pane>

    </a-tabs>

    <!-- 添加/编辑工时弹窗 -->
    <TimeEntryDialog
      ref="timeEntryDialogRef"
      :current-user-id="authStore.user?.id"
      :current-user-name="currentUserName"
      :can-log-for-others="canLogForOthers"
      :can-edit-others="canEditOthers"
      :minutes-per-day="minutesPerDay()"
      @saved="reloadCurrentTab"
      @deleted="reloadCurrentTab"
    />
  </div>
</template>
<script setup lang="ts">
import { formatDuration } from '@/utils/duration'
import { toDateKey, formatDateDisplay, startOfWeek, addWeeks, addMonths, getWeekDays, getMonthDays } from '@/utils/timesheet'
import { ref, computed, onMounted, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { useAuthStore } from '@/stores/auth'
import { useRoute, useRouter } from 'vue-router'
import { timeEntryApi, projectApi } from '@/api'
import type { TimeEntryVO, ProjectTimeSummaryVO, TimeEntryUserVO, GroupTimeSummaryVO } from '@/api/timeEntry'
import { workItemAttributeApi } from '@/api/timeEntry'
import { useTimeTrackingSettings } from '@/composables/useTimeTrackingSettings'
import PeopleTab from './components/PeopleTab.vue'
import ProjectsTab from './components/ProjectsTab.vue'
import WorkgroupsTab from './components/WorkgroupsTab.vue'
import TimeEntryDialog from './components/TimeEntryDialog.vue'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const { loadSettings: loadTTSettings, minutesPerDay, isWorkingDay, quotaText } = useTimeTrackingSettings()

// ===== 工时弹窗 ref =====
const timeEntryDialogRef = ref<InstanceType<typeof TimeEntryDialog> | null>(null)

function openAddDialog(date?: string) {
  timeEntryDialogRef.value?.open(date)
}

function openEditDialog(entry: TimeEntryVO) {
  timeEntryDialogRef.value?.openEdit(entry)
}

// State
const activeTab = ref<'people' | 'projects' | 'workgroups'>((route.query.view as any) || 'people')
const viewMode = ref<'week' | 'month'>('week')
const currentWeekStart = ref(startOfWeek(new Date()))
const currentMonthDate = ref(new Date().toISOString().slice(0, 7)) // YYYY-MM
const loading = ref(false)
const loadError = ref<string | null>(null)
const timeEntries = ref<TimeEntryVO[]>([])

// User selector state
const canViewOthers = ref(false)
const canEditOthers = ref(false)
const canLogForOthers = ref(false)
const canLogTime = ref(false)
const selectableUsers = ref<TimeEntryUserVO[]>([])
const selectedUserId = ref<string | undefined>(undefined)

// Project view state
const projectSummaries = ref<ProjectTimeSummaryVO[]>([])
const selectedProjectId = ref<string | undefined>(undefined)
const projectEntries = ref<TimeEntryVO[]>([])

// Work Groups view state
const groupSummaries = ref<GroupTimeSummaryVO[]>([])
const expandedGroups = ref<Set<string>>(new Set())

// Filter state (people view)
const filterProjectId = ref<string | undefined>(undefined)
const filterWorkType = ref<string | undefined>(undefined)
const filterProjects = ref<{ id: string; name: string; key: string }[]>([])
const filterWorkTypes = ref<{ id: string; name: string; color?: string }[]>([])

// Computed
const currentUserName = computed(() => authStore.user?.displayName || authStore.user?.username || 'Test User')

const selectedUserDisplayName = computed(() => {
  if (!selectedUserId.value) return currentUserName.value
  const user = selectableUsers.value.find(u => u.id === selectedUserId.value)
  return user?.displayName || currentUserName.value
})

const weekDays = computed(() => getWeekDays(currentWeekStart.value, isWorkingDay))

const monthDays = computed(() => {
  const [year, month] = currentMonthDate.value.split('-').map(Number)
  return getMonthDays(year, month, isWorkingDay)
})

const dateRangeLabel = computed(() => {
  if (viewMode.value === 'week') {
    const start = new Date(currentWeekStart.value)
    const end = new Date(start)
    end.setDate(start.getDate() + 6)
    return `${formatDateDisplay(start)} – ${formatDateDisplay(end)}`
  } else {
    const [year, month] = currentMonthDate.value.split('-').map(Number)
    return `${year}年${month}月`
  }
})

const weekTotal = computed(() => {
  return timeEntries.value.reduce((sum, e) => sum + (e.duration || 0), 0)
})

const projectViewTotal = computed(() => {
  if (selectedProjectId.value) {
    return projectEntries.value.reduce((sum, e) => sum + (e.duration || 0), 0)
  }
  return projectSummaries.value.reduce((sum, p) => sum + p.totalDuration, 0)
})

const groupViewTotal = computed(() => {
  return groupSummaries.value.reduce((sum, g) => sum + g.totalDuration, 0)
})

// 当前视图的总工时标签
const totalTimeLabel = computed(() => {
  if (activeTab.value === 'people') {
    return `${selectedUserDisplayName.value} 总已用时间: ${formatDuration(weekTotal.value)}`
  } else if (activeTab.value === 'projects') {
    return `总已用时间: ${formatDuration(projectViewTotal.value)}`
  } else {
    return `所有工作组总工时: ${formatDuration(groupViewTotal.value)}`
  }
})

// Tab switching
function refresh() {
  if (activeTab.value === 'people') loadEntries()
  else if (activeTab.value === 'projects') loadProjectSummaries()
  else if (activeTab.value === 'workgroups') loadGroupSummaries()
}

function switchTab(tab: 'people' | 'projects' | 'workgroups') {
  // Persist to URL query for refresh preservation
  router.replace({ query: { ...route.query, view: tab } })
  if (tab === 'people') {
    loadEntries()
  } else if (tab === 'projects') {
    loadProjectSummaries()
  } else if (tab === 'workgroups') {
    loadGroupSummaries()
  }
}

// Data loading - People view
async function loadEntries() {
  loading.value = true
  loadError.value = null
  try {
    const { startDate, endDate } = getDateRange()
    const params: { userId?: string; startDate: string; endDate: string; projectId?: string; activityId?: string } = { startDate, endDate }
    if (selectedUserId.value) {
      params.userId = selectedUserId.value
    }
    if (filterProjectId.value) {
      params.projectId = filterProjectId.value
    }
    if (filterWorkType.value) {
      // filterWorkType now holds the attribute value ID (not name)
      params.activityId = filterWorkType.value
    }
    const res = await timeEntryApi.list(params)
    if (res.code === 0 && res.data) {
      timeEntries.value = res.data
    }
  } catch (e: any) {
    loadError.value = e.response?.data?.message || '加载工时数据失败'
  } finally {
    loading.value = false
  }
}

// Data loading - Work Groups view
async function loadGroupSummaries() {
  loading.value = true
  loadError.value = null
  try {
    const { startDate, endDate } = getDateRange()
    const res = await timeEntryApi.listByGroup({ startDate, endDate })
    if (res.code === 0 && res.data) {
      groupSummaries.value = res.data
      // 自动展开第一个有工时的组
      if (expandedGroups.value.size === 0 && res.data.length > 0) {
        const firstWithTime = res.data.find(g => g.totalDuration > 0)
        if (firstWithTime) {
          expandedGroups.value.add(firstWithTime.groupId)
        } else if (res.data.length > 0) {
          expandedGroups.value.add(res.data[0].groupId)
        }
      }
    }
  } catch (e: any) {
    loadError.value = e.response?.data?.message || '加载工作组工时数据失败'
  } finally {
    loading.value = false
  }
}

function toggleGroupExpand(groupId: string) {
  if (expandedGroups.value.has(groupId)) {
    expandedGroups.value.delete(groupId)
  } else {
    expandedGroups.value.add(groupId)
  }
}

// Data loading - Project view
async function loadProjectSummaries() {
  loading.value = true
  loadError.value = null
  try {
    const { startDate, endDate } = getDateRange()
    const res = await timeEntryApi.listByProject({ startDate, endDate })
    if (res.code === 0 && res.data) {
      projectSummaries.value = res.data
    }
  } catch (e: any) {
    loadError.value = e.response?.data?.message || '加载项目工时数据失败'
  } finally {
    loading.value = false
  }
}

async function loadProjectDetail(projectId: string) {
  loading.value = true
  try {
    const { startDate, endDate } = getDateRange()
    const res = await timeEntryApi.listByProjectDetail(projectId, { startDate, endDate })
    if (res.code === 0 && res.data) {
      projectEntries.value = res.data
    }
  } catch {
    Message.error({ content: '加载项目工时明细失败', duration: 3000 })
  } finally {
    loading.value = false
  }
}

function onProjectChange(val: string | number | boolean | Record<string, any> | (string | number | boolean | Record<string, any>)[] | undefined) {
  selectedProjectId.value = (val as string) || undefined
  if (val) {
    loadProjectDetail(val as string)
  } else {
    projectEntries.value = []
    loadProjectSummaries()
  }
}

// User selector functions
async function loadCanViewOthers() {
  try {
    const res = await timeEntryApi.canViewOthers()
    if (res.code === 0) {
      canViewOthers.value = res.data === true
    }
  } catch (e) {
    console.error('[Timesheet] 加载 canViewOthers 权限失败:', e)
  }
}

async function loadCanEditOthers() {
  try {
    const res = await timeEntryApi.canEditOthers()
    if (res.code === 0) {
      canEditOthers.value = res.data === true
    }
  } catch (e) {
    console.error('[Timesheet] 加载 canEditOthers 权限失败:', e)
  }
}

async function loadCanLogForOthers() {
  try {
    const res = await timeEntryApi.canLogForOthers()
    if (res.code === 0) {
      canLogForOthers.value = res.data === true
    }
  } catch (e) {
    console.error('[Timesheet] 加载 canLogForOthers 权限失败:', e)
  }
}

async function loadCanLogTime() {
  try {
    const res = await timeEntryApi.canLogTime()
    if (res.code === 0) {
      canLogTime.value = res.data === true
    }
  } catch (e) {
    console.error('[Timesheet] 加载 canLogTime 权限失败:', e)
  }
}

async function loadSelectableUsers(keyword?: string) {
  try {
    const params = keyword ? { keyword } : undefined
    const res = await timeEntryApi.listSelectableUsers(params)
    if (res.code === 0 && res.data) {
      selectableUsers.value = res.data
    }
  } catch (e) {
    console.error('[Timesheet] 加载可选用户列表失败:', e)
  }
}

function searchUsers(keyword: string) {
  loadSelectableUsers(keyword)
}

function onUserChange(val: any) {
  selectedUserId.value = (val as string) || undefined
  loadEntries()
}


function navigate(delta: number) {
  if (viewMode.value === 'week') {
    currentWeekStart.value = addWeeks(currentWeekStart.value, delta)
  } else {
    currentMonthDate.value = addMonths(currentMonthDate.value, delta)
  }
}

function goToday() {
  const now = new Date()
  currentWeekStart.value = startOfWeek(now)
  currentMonthDate.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

// a-radio-group @change 触发
function onViewModeChange() {
  reloadCurrentTab()
}

function reloadCurrentTab() {
  if (activeTab.value === 'people') {
    loadEntries()
  } else if (activeTab.value === 'projects') {
    if (selectedProjectId.value) {
      loadProjectDetail(selectedProjectId.value)
    } else {
      loadProjectSummaries()
    }
  } else if (activeTab.value === 'workgroups') {
    loadGroupSummaries()
  }
}
// Helpers

function getDateRange(): { startDate: string; endDate: string } {
  if (viewMode.value === 'week') {
    const end = new Date(currentWeekStart.value)
    end.setDate(end.getDate() + 6)
    return { startDate: currentWeekStart.value, endDate: toDateKey(end) }
  } else {
    const [year, month] = currentMonthDate.value.split('-').map(Number)
    const startDate = `${year}-${String(month).padStart(2, '0')}-01`
    const lastDay = new Date(year, month, 0)
    return { startDate, endDate: toDateKey(lastDay) }
  }
}

async function loadFilterProjects() {
  try {
    const res = await projectApi.list({ pageSize: 100 })
    if (res.code === 0 && res.data) {
      filterProjects.value = res.data.list.map(p => ({ id: p.id, name: p.name, key: p.key }))
    }
  } catch (e) {
    console.error('[Timesheet] 加载筛选项目失败:', e)
  }
}

async function loadFilterWorkTypes() {
  try {
    const res = await workItemAttributeApi.list()
    if (res.code === 0 && res.data) {
      const workTypeAttr = res.data.find((a: any) => a.name === 'Work type' || a.name === '工作类型')
      if (workTypeAttr?.values) {
        filterWorkTypes.value = workTypeAttr.values.map((v: any) => ({ id: v.id, name: v.name, color: v.color }))
      }
    }
  } catch (e) {
    console.error('[Timesheet] 加载工作类型失败:', e)
  }
}

function onFilterChange() {
  loadEntries()
}

// PeopleTab emit 的筛选变更
function onFilterChangeFromTab(filters: { projectId?: string; workType?: string }) {
  filterProjectId.value = filters.projectId
  filterWorkType.value = filters.workType
  onFilterChange()
}

function resetFilters() {
  filterProjectId.value = undefined
  filterWorkType.value = undefined
  onFilterChange()
}

// Watchers - reload data on date navigation
watch(currentWeekStart, () => { if (viewMode.value === 'week') reloadCurrentTab() })
watch(currentMonthDate, () => { if (viewMode.value === 'month') reloadCurrentTab() })

// Init
onMounted(async () => {
  // Load time tracking settings first (needed for parseDuration + quota)
  await loadTTSettings()
  // Check if user can view/edit others' time entries
  await Promise.all([loadCanViewOthers(), loadCanEditOthers(), loadCanLogForOthers(), loadCanLogTime()])
  if (canViewOthers.value) {
    await loadSelectableUsers()
  }
  // Load filter options
  loadFilterProjects()
  loadFilterWorkTypes()
  // Restore filters from URL query
  if (route.query.projectId) {
    filterProjectId.value = route.query.projectId as string
  }
  if (route.query.activityId) {
    filterWorkType.value = route.query.activityId as string
  } else if (route.query.workType) {
    // Legacy compat: old URL had workType name, try to find matching ID
    const legacyName = route.query.workType as string
    const match = filterWorkTypes.value.find(wt => wt.name === legacyName)
    if (match) filterWorkType.value = match.id
  }
  // Load data for the currently active tab (may be restored from URL)
  if (activeTab.value === 'projects') {
    loadProjectSummaries()
  } else if (activeTab.value === 'people') {
    loadEntries()
  } else if (activeTab.value === 'workgroups') {
    loadGroupSummaries()
  }
})
</script>

<style scoped>
/* 页面容器 — TimesheetView 自身布局 */
.timesheet-page { height: 100%; display: flex; flex-direction: column; overflow: hidden; position: relative; }
.timesheet-header { padding: 16px 24px 0; }
.page-title { font-size: 18px; font-weight: 600; color: var(--tf-text-primary); margin: 0; }

/* Tabs — 让 a-tabs 紧贴 header，去掉默认内边距 */
.timesheet-tabs { margin: 0; flex: 1; min-height: 0; display: flex; flex-direction: column; }
.timesheet-tabs :deep(.arco-tabs-nav) { padding: 0 24px; flex-shrink: 0; }
.timesheet-tabs :deep(.arco-tabs-content) { flex: 1; min-height: 0; overflow: hidden; }
.timesheet-tabs :deep(.arco-tabs-content-list) { height: 100%; }
.timesheet-tabs :deep(.arco-tabs-pane) { height: 100%; display: flex; flex-direction: column; overflow: hidden; }

/*
  :deep 穿透子组件，统一 PeopleTab / ProjectsTab 的控制栏布局。
  避免两个组件各自重复同一份 CSS。
*/
.timesheet-tabs :deep(.timesheet-controls) { padding: 12px 24px; display: flex; align-items: center; }
.timesheet-tabs :deep(.controls-left) { display: flex; flex-direction: column; gap: 6px; }
.timesheet-tabs :deep(.loading-overlay) { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; background: var(--tf-fill-light); z-index: 10; pointer-events: none; }
.timesheet-tabs :deep(.attr-value-dot) { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 4px; vertical-align: middle; }
</style>
