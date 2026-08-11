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
  await Promise.all([loadCanViewOthers(), loadCanEditOthers(), loadCanLogForOthers()])
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
.timesheet-page { height: 100%; display: flex; flex-direction: column; overflow: hidden; position: relative; }
.timesheet-header { padding: 16px 24px 0; }
.page-title { font-size: 18px; font-weight: 600; color: var(--tf-text-primary); margin: 0; }

/* Attribute value dot */
.attr-value-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; vertical-align: middle; }

/* Tabs — 让 a-tabs 紧贴 header，去掉默认内边距 */
.timesheet-tabs { margin: 0; flex: 1; min-height: 0; display: flex; flex-direction: column; }
.timesheet-tabs :deep(.arco-tabs-nav) { padding: 0 24px; flex-shrink: 0; }
.timesheet-tabs :deep(.arco-tabs-content) { flex: 1; min-height: 0; overflow: hidden; }
.timesheet-tabs :deep(.arco-tabs-content-list) { height: 100%; }
.timesheet-tabs :deep(.arco-tabs-pane) { height: 100%; display: flex; flex-direction: column; overflow: hidden; }

/* Controls */
.timesheet-controls { padding: 12px 24px; display: flex; align-items: center; justify-content: space-between; }
.controls-left { display: flex; flex-direction: column; gap: 6px; }
.user-selector-area { display: flex; align-items: center; }
.user-selector-static { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 500; color: var(--tf-text-primary); }
.user-option { display: flex; align-items: center; gap: 8px; }
.user-option-name { font-size: 13px; color: var(--tf-text-primary); }
.user-option-self { font-size: 11px; color: var(--tf-text-tertiary); }
.project-selector { display: flex; align-items: center; gap: 8px; }
.filters { display: flex; align-items: center; gap: 8px; font-size: 12px; margin-top: 4px; }
.filter-label { color: var(--tf-text-tertiary); white-space: nowrap; }
.filter-value { color: var(--tf-text-secondary); }
.filter-reset { font-size: 12px; color: var(--tf-accent); cursor: pointer; white-space: nowrap; margin-left: 4px; text-decoration: none; }
.filter-reset:hover { text-decoration: underline; }

/* Date bar */
.timesheet-datebar { padding: 8px 24px 12px; display: flex; align-items: center; justify-content: space-between; }
.date-info { display: flex; flex-direction: column; gap: 2px; }
.date-range { font-size: 15px; font-weight: 500; color: var(--tf-text-primary); }
.total-time { font-size: 12px; color: var(--tf-text-tertiary); }
.date-nav { display: flex; align-items: center; gap: 8px; }

/* Week Grid - styles moved to WeekGrid.vue */

/* Month Grid - styles moved to MonthGrid.vue */

/* Project Overview */
.project-overview { flex: 1; overflow-y: auto; padding: 0 24px 24px; }
.project-summary-list { display: flex; flex-direction: column; gap: 8px; }
.project-summary-card { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; border-radius: var(--tf-radius-md); background: var(--tf-bg-elevated); border: 1px solid var(--tf-border-light); cursor: pointer; transition: border-color 0.15s, background 0.1s; }
.project-summary-card:hover { border-color: var(--tf-accent); background: var(--tf-bg-hover); }
.project-summary-left { display: flex; align-items: center; gap: 10px; }
.project-key-badge { font-size: 11px; font-weight: 600; color: var(--tf-accent); background: var(--tf-accent-bg); padding: 2px 8px; border-radius: var(--tf-radius-sm); }
.project-name-text { font-size: 14px; font-weight: 500; color: var(--tf-text-primary); }
.project-summary-right { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
.project-total-dur { font-size: 14px; font-weight: 600; color: var(--tf-text-primary); }
.project-entry-count { font-size: 11px; color: var(--tf-text-tertiary); }

/* Project detail */
.project-detail-view { flex: 1; display: flex; flex-direction: column; overflow: hidden; }

/* Workgroup empty state */
.workgroup-empty { flex: 1; display: flex; align-items: center; justify-content: center; padding: 48px 24px; }

/* Work Groups view */
.group-overview { flex: 1; overflow-y: auto; padding: 0 24px 24px; display: flex; flex-direction: column; gap: 8px; }
.group-block { border: 1px solid var(--tf-border-light); border-radius: var(--tf-radius-md); overflow: hidden; }
.group-header { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; background: var(--tf-bg-elevated); cursor: pointer; transition: background 0.15s; user-select: none; }
.group-header:hover { background: var(--tf-bg-hover); }
.group-header.expanded { border-bottom: 1px solid var(--tf-border-light); }
.group-header-left { display: flex; align-items: center; gap: 8px; }
.group-expand-icon { font-size: 10px; color: var(--tf-text-tertiary); width: 12px; }
.group-icon { font-size: 14px; }
.group-name { font-size: 14px; font-weight: 600; color: var(--tf-text-primary); }
.group-member-count { font-size: 11px; color: var(--tf-text-tertiary); background: var(--tf-bg-surface); padding: 1px 6px; border-radius: 10px; }
.group-header-right { display: flex; align-items: center; }
.group-total-dur { font-size: 14px; font-weight: 600; color: var(--tf-accent); }
.group-members { display: flex; flex-direction: column; }
.member-row { display: flex; align-items: center; gap: 12px; padding: 8px 16px; border-bottom: 1px solid var(--tf-border-light); }
.member-row:last-child { border-bottom: none; }
.member-info { display: flex; align-items: center; gap: 8px; min-width: 140px; max-width: 140px; }
.member-name { font-size: 13px; color: var(--tf-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.member-bar-area { flex: 1; display: flex; flex-wrap: wrap; gap: 4px; min-height: 24px; align-items: center; }
.member-entries { display: flex; flex-wrap: wrap; gap: 4px; }
.member-entry-chip { font-size: 11px; padding: 2px 8px; border-radius: 10px; background: var(--tf-accent-bg); color: var(--tf-accent); cursor: pointer; transition: opacity 0.15s; white-space: nowrap; }
.member-entry-chip:hover { opacity: 0.8; }
.member-no-entries { font-size: 12px; color: var(--tf-text-tertiary); font-style: italic; }
.member-total { min-width: 48px; text-align: right; }
.member-total-dur { font-size: 13px; font-weight: 600; color: var(--tf-text-primary); }
.member-total-zero { font-size: 13px; color: var(--tf-text-tertiary); }

/* Loading */
.loading-overlay { position: absolute; top: 0; left: 0; right: 0; bottom: 0; display: flex; align-items: center; justify-content: center; background: var(--tf-fill-light); z-index: 10; pointer-events: none; }

/* Dialog */
.time-dialog { padding: 4px 0; }
.dialog-field { margin-bottom: 16px; }
.dialog-label { display: block; font-size: 12px; color: var(--tf-text-tertiary); margin-bottom: 6px; font-weight: 500; }
.dialog-row { display: flex; gap: 12px; }
.flex-1 { flex: 1; }

.author-display { display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: var(--tf-bg-surface); border: 1px solid var(--tf-border-light); border-radius: var(--tf-radius-md); }
.author-name { font-size: 13px; color: var(--tf-text-primary); }

.date-mode-toggle { display: inline-flex; border: 1px solid var(--tf-border); border-radius: var(--tf-radius-md); overflow: hidden; }
.date-mode-btn { padding: 6px 14px; font-size: 12px; border: none; background: transparent; color: var(--tf-text-secondary); cursor: pointer; transition: background 0.15s, color 0.15s; }
.date-mode-btn.active { background: var(--tf-bg-elevated); color: var(--tf-text-primary); font-weight: 500; }
.date-mode-btn + .date-mode-btn { border-left: 1px solid var(--tf-border); }

.add-another { margin-bottom: 16px; }
.add-another-link { font-size: 13px; color: var(--tf-accent); cursor: pointer; text-decoration: none; }
.add-another-link:hover { text-decoration: underline; }

.extra-records { margin-bottom: 16px; display: flex; flex-direction: column; gap: 8px; }
.extra-record-row { display: flex; align-items: center; gap: 8px; }
.remove-record-btn { width: 24px; height: 24px; border: none; background: transparent; color: var(--tf-text-tertiary); cursor: pointer; font-size: 14px; border-radius: 4px; display: flex; align-items: center; justify-content: center; }
.remove-record-btn:hover { background: var(--tf-bg-hover); color: var(--tf-text-primary); }

.dialog-actions { display: flex; justify-content: space-between; align-items: center; padding-top: 16px; border-top: 1px solid var(--tf-border-light); }
.actions-left { display: flex; gap: 8px; }
.actions-right { display: flex; gap: 8px; }
</style>
