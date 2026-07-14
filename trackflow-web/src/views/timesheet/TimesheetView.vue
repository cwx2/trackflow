<template>
  <div class="timesheet-page">
    <!-- Header -->
    <div class="timesheet-header">
      <h1 class="page-title">时间表</h1>
    </div>

    <!-- Tabs -->
    <div class="timesheet-tabs">
      <button class="tab-btn" :class="{ active: activeTab === 'people' }" @click="switchTab('people')">人员</button>
      <button class="tab-btn" :class="{ active: activeTab === 'projects' }" @click="switchTab('projects')">项目</button>
      <button class="tab-btn" :class="{ active: activeTab === 'workgroups' }" @click="switchTab('workgroups')">工作群组</button>
    </div>

    <!-- ===================== 人员视图 ===================== -->
    <template v-if="activeTab === 'people'">
      <!-- User selector & filters -->
      <div class="timesheet-controls">
        <div class="controls-left">
          <div class="user-selector">
            <span class="user-avatar-dot"></span>
            <span class="user-name">{{ currentUserName }}</span>
            <span class="selector-arrow">▾</span>
          </div>
          <div class="filters">
            <span class="filter-label">项目:</span>
            <span class="filter-value">全部</span>
            <span class="filter-label">工作类型:</span>
            <span class="filter-value">全部</span>
          </div>
        </div>
      </div>

      <!-- Date range & navigation -->
      <div class="timesheet-datebar">
        <div class="date-info">
          <span class="date-range">{{ dateRangeLabel }}</span>
          <span class="total-time">总已用时间: {{ formatDuration(weekTotal) }}</span>
        </div>
        <div class="date-nav">
          <button class="nav-btn" @click="navigate(-1)">←</button>
          <button class="nav-btn today-btn" @click="goToday">今天</button>
          <button class="nav-btn" @click="navigate(1)">→</button>
          <div class="view-toggle">
            <button class="toggle-btn" :class="{ active: viewMode === 'week' }" @click="switchView('week')">周</button>
            <button class="toggle-btn" :class="{ active: viewMode === 'month' }" @click="switchView('month')">月</button>
          </div>
          <button class="add-time-btn" @click="openAddDialog()">添加已花费时间</button>
        </div>
      </div>

      <!-- Week View -->
      <WeekGrid
        v-if="viewMode === 'week'"
        :week-days="weekDays"
        :entries="timeEntries"
        :show-quota="true"
        @day-click="openAddDialog"
        @entry-click="openEditDialog"
        @issue-click="(entry) => $router.push(`/issues/${entry.issueId}`)"
      />

      <!-- Month View -->
      <MonthGrid
        v-else
        :month-days="monthDays"
        :entries="timeEntries"
        @day-click="openAddDialog"
        @entry-click="openEditDialog"
      />
    </template>

    <!-- ===================== 项目视图 ===================== -->
    <template v-else-if="activeTab === 'projects'">
      <!-- Project selector & filters -->
      <div class="timesheet-controls">
        <div class="controls-left">
          <div class="project-selector">
            <a-select
              v-model="selectedProjectId"
              placeholder="选择项目查看明细"
              allow-clear
              allow-search
              style="width: 260px"
              @change="onProjectChange"
            >
              <a-option v-for="p in projectSummaries" :key="p.projectId" :value="p.projectId">
                {{ p.projectKey }} - {{ p.projectName }}
              </a-option>
            </a-select>
          </div>
          <div class="filters">
            <span class="filter-label">汇总范围:</span>
            <span class="filter-value">{{ selectedProjectId ? '项目明细' : '所有可见项目' }}</span>
          </div>
        </div>
      </div>

      <!-- Date range & navigation -->
      <div class="timesheet-datebar">
        <div class="date-info">
          <span class="date-range">{{ dateRangeLabel }}</span>
          <span class="total-time">总已用时间: {{ formatDuration(projectViewTotal) }}</span>
        </div>
        <div class="date-nav">
          <button class="nav-btn" @click="navigate(-1)">←</button>
          <button class="nav-btn today-btn" @click="goToday">今天</button>
          <button class="nav-btn" @click="navigate(1)">→</button>
          <div class="view-toggle">
            <button class="toggle-btn" :class="{ active: viewMode === 'week' }" @click="switchView('week')">周</button>
            <button class="toggle-btn" :class="{ active: viewMode === 'month' }" @click="switchView('month')">月</button>
          </div>
        </div>
      </div>

      <!-- Project Overview (no project selected) -->
      <div v-if="!selectedProjectId" class="project-overview">
        <div v-if="projectSummaries.length === 0 && !loading" class="empty-state">
          <div class="empty-icon">📊</div>
          <div class="empty-title">暂无项目工时数据</div>
          <div class="empty-desc">当前日期范围内您可见的项目没有工时记录</div>
        </div>
        <div v-else class="project-summary-list">
          <div
            v-for="p in projectSummaries"
            :key="p.projectId"
            class="project-summary-card"
            @click="onProjectChange(p.projectId)"
          >
            <div class="project-summary-left">
              <span class="project-key-badge">{{ p.projectKey }}</span>
              <span class="project-name-text">{{ p.projectName }}</span>
            </div>
            <div class="project-summary-right">
              <span class="project-total-dur">{{ formatDuration(p.totalDuration) }}</span>
              <span class="project-entry-count">{{ p.entries.length }} 条记录</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Project Detail (project selected) -->
      <div v-else class="project-detail-view">
        <WeekGrid
          v-if="viewMode === 'week'"
          :week-days="weekDays"
          :entries="projectEntries"
          :show-user="true"
        />
        <MonthGrid
          v-else
          :month-days="monthDays"
          :entries="projectEntries"
        />
      </div>
    </template>

    <!-- ===================== 工作群组视图 ===================== -->
    <template v-else-if="activeTab === 'workgroups'">
      <div class="workgroup-empty">
        <div class="empty-state">
          <div class="empty-icon">👥</div>
          <div class="empty-title">工作群组功能暂未上线</div>
          <div class="empty-desc">工作群组允许您将团队成员分组，按群组查看聚合工时。该功能正在开发中，敬请期待。</div>
          <div class="empty-hint">
            <span class="hint-icon">💡</span>
            <span>您可以使用"项目"视图按项目维度查看团队工时汇总</span>
          </div>
        </div>
      </div>
    </template>

    <!-- Loading overlay -->
    <div v-if="loading" class="loading-overlay">
      <a-spin :size="24" />
    </div>

    <!-- Add/Edit Dialog -->
    <a-modal
      v-model:visible="showDialog"
      :title="editingEntry ? '编辑工时' : '添加花费的时间'"
      :width="560"
      :footer="false"
      @cancel="closeDialog"
    >
      <div class="time-dialog">
        <!-- 问题 -->
        <div class="dialog-field">
          <label class="dialog-label">问题</label>
          <a-select
            v-model="form.issueId"
            placeholder="Select an option"
            allow-search
            :options="issueOptions"
            @search="searchIssues"
          />
        </div>

        <!-- 作者 -->
        <div class="dialog-field">
          <label class="dialog-label">作者</label>
          <div class="author-display">
            <span class="author-avatar">{{ currentUserName.charAt(0) }}</span>
            <span class="author-name">{{ currentUserName }}</span>
          </div>
        </div>

        <!-- 单一日期 / 日期范围 切换 -->
        <div class="dialog-field">
          <div class="date-mode-toggle">
            <button class="date-mode-btn" :class="{ active: dateMode === 'single' }" @click="dateMode = 'single'">单一日期</button>
            <button class="date-mode-btn" :class="{ active: dateMode === 'range' }" @click="dateMode = 'range'">日期范围</button>
          </div>
        </div>

        <!-- 日期 + 实际用时 -->
        <div class="dialog-row">
          <div class="dialog-field flex-1">
            <label class="dialog-label">日期</label>
            <a-date-picker v-if="dateMode === 'single'" v-model="form.workDate" style="width: 100%" />
            <a-range-picker v-else v-model="form.dateRange" style="width: 100%" />
          </div>
          <div class="dialog-field flex-1">
            <label class="dialog-label">实际用时</label>
            <a-input v-model="form.durationText" placeholder="1周 1天 1时 1分">
              <template #prefix>⏱</template>
            </a-input>
          </div>
        </div>

        <!-- 添加另一个记录 -->
        <div class="add-another" v-if="!editingEntry">
          <a class="add-another-link" @click="addAnotherRecord">+ 添加另一个记录</a>
        </div>

        <!-- 额外记录列表 -->
        <div v-if="extraRecords.length > 0" class="extra-records">
          <div v-for="(rec, idx) in extraRecords" :key="idx" class="extra-record-row">
            <a-date-picker v-model="rec.workDate" style="width: 45%" size="small" />
            <a-input v-model="rec.durationText" placeholder="时长" style="width: 40%" size="small" />
            <button class="remove-record-btn" @click="extraRecords.splice(idx, 1)">✕</button>
          </div>
        </div>

        <!-- 类型 -->
        <div class="dialog-field">
          <label class="dialog-label">类型</label>
          <a-select v-model="form.workType" placeholder="Select an option" allow-clear>
            <a-option value="Development">开发</a-option>
            <a-option value="Testing">测试</a-option>
            <a-option value="Documentation">文档</a-option>
            <a-option value="Design">设计</a-option>
            <a-option value="Review">代码审查</a-option>
            <a-option value="Meeting">会议</a-option>
            <a-option value="Other">其他</a-option>
          </a-select>
        </div>

        <!-- 描述 -->
        <div class="dialog-field">
          <label class="dialog-label">描述</label>
          <a-textarea
            v-model="form.description"
            placeholder="描述这段时间您做了什么"
            :auto-size="{ minRows: 3, maxRows: 6 }"
          />
        </div>

        <!-- 底部按钮 -->
        <div class="dialog-actions">
          <div class="actions-left">
            <a-button v-if="editingEntry" status="danger" @click="deleteEntry" :loading="deleting">删除</a-button>
          </div>
          <div class="actions-right">
            <a-button @click="closeDialog">取消</a-button>
            <a-button type="primary" @click="saveEntry" :loading="saving">
              {{ editingEntry ? '保存' : '保存' }}
            </a-button>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { useAuthStore } from '@/stores/auth'
import { useRoute, useRouter } from 'vue-router'
import { timeEntryApi, issueApi } from '@/api'
import type { TimeEntryVO, ProjectTimeSummaryVO } from '@/api/timeEntry'
import WeekGrid from './WeekGrid.vue'
import MonthGrid from './MonthGrid.vue'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

// State
const activeTab = ref<'people' | 'projects' | 'workgroups'>((route.query.view as any) || 'people')
const viewMode = ref<'week' | 'month'>('week')
const currentWeekStart = ref(getMonday(new Date()))
const currentMonthDate = ref(new Date().toISOString().slice(0, 7)) // YYYY-MM
const loading = ref(false)
const showDialog = ref(false)
const editingEntry = ref<TimeEntryVO | null>(null)
const saving = ref(false)
const deleting = ref(false)
const timeEntries = ref<TimeEntryVO[]>([])
const issueOptions = ref<{ value: string; label: string }[]>([])

// Project view state
const projectSummaries = ref<ProjectTimeSummaryVO[]>([])
const selectedProjectId = ref<string | undefined>(undefined)
const projectEntries = ref<TimeEntryVO[]>([])

// Form
const dateMode = ref<'single' | 'range'>('single')
const extraRecords = ref<{ workDate: string; durationText: string }[]>([])
const form = ref({
  issueId: undefined as string | undefined,
  workDate: '',
  dateRange: undefined as [string, string] | undefined,
  durationText: '',
  startTimeStr: undefined as string | undefined,
  workType: undefined as string | undefined,
  description: ''
})

// Computed
const currentUserName = computed(() => authStore.user?.displayName || authStore.user?.username || 'Test User')

const weekDays = computed(() => {
  const days = []
  const start = new Date(currentWeekStart.value)
  const dayNames = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  for (let i = 0; i < 7; i++) {
    const d = new Date(start)
    d.setDate(start.getDate() + i)
    days.push({
      date: formatDateKey(d),
      dateNum: d.getDate(),
      dayName: dayNames[d.getDay()],
      isWeekend: d.getDay() === 0 || d.getDay() === 6
    })
  }
  return days
})

const monthDays = computed(() => {
  const [year, month] = currentMonthDate.value.split('-').map(Number)
  const firstDay = new Date(year, month - 1, 1)

  // Start from Monday of the week containing the first day
  const startDate = new Date(firstDay)
  const dayOfWeek = startDate.getDay()
  const offset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek
  startDate.setDate(startDate.getDate() + offset)

  const days = []
  const current = new Date(startDate)
  // Generate 6 weeks (42 days) to cover all months
  for (let i = 0; i < 42; i++) {
    days.push({
      date: formatDateKey(current),
      dateNum: current.getDate(),
      isWeekend: current.getDay() === 0 || current.getDay() === 6,
      currentMonth: current.getMonth() === month - 1
    })
    current.setDate(current.getDate() + 1)
  }
  return days
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
  return timeEntries.value.reduce((sum, e) => sum + e.duration, 0)
})

const projectViewTotal = computed(() => {
  if (selectedProjectId.value) {
    return projectEntries.value.reduce((sum, e) => sum + e.duration, 0)
  }
  return projectSummaries.value.reduce((sum, p) => sum + p.totalDuration, 0)
})

// Tab switching
function switchTab(tab: 'people' | 'projects' | 'workgroups') {
  activeTab.value = tab
  // Persist to URL query for refresh preservation
  router.replace({ query: { ...route.query, view: tab } })
  if (tab === 'people') {
    loadEntries()
  } else if (tab === 'projects') {
    loadProjectSummaries()
  }
  // workgroups: no data to load
}

// Data loading - People view
async function loadEntries() {
  loading.value = true
  try {
    const { startDate, endDate } = getDateRange()
    const res = await timeEntryApi.list({ startDate, endDate })
    if (res.code === 0 && res.data) {
      timeEntries.value = res.data
    }
  } catch {
    Message.error({ content: '加载工时数据失败', duration: 3000 })
  } finally {
    loading.value = false
  }
}

// Data loading - Project view
async function loadProjectSummaries() {
  loading.value = true
  try {
    const { startDate, endDate } = getDateRange()
    const res = await timeEntryApi.listByProject({ startDate, endDate })
    if (res.code === 0 && res.data) {
      projectSummaries.value = res.data
    }
  } catch {
    Message.error({ content: '加载项目工时数据失败', duration: 3000 })
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

function onProjectChange(val: string | undefined) {
  selectedProjectId.value = val || undefined
  if (val) {
    loadProjectDetail(val)
  } else {
    projectEntries.value = []
    loadProjectSummaries()
  }
}

function getDateRange(): { startDate: string; endDate: string } {
  if (viewMode.value === 'week') {
    const startDate = currentWeekStart.value
    const end = new Date(currentWeekStart.value)
    end.setDate(end.getDate() + 6)
    return { startDate, endDate: formatDateKey(end) }
  } else {
    const [year, month] = currentMonthDate.value.split('-').map(Number)
    const startDate = `${year}-${String(month).padStart(2, '0')}-01`
    const lastDay = new Date(year, month, 0)
    return { startDate, endDate: formatDateKey(lastDay) }
  }
}

async function searchIssues(keyword: string) {
  try {
    const params: Record<string, any> = { pageSize: 15 }
    if (keyword && keyword.length >= 1) params.keyword = keyword
    const res = await issueApi.list(params)
    if (res.code === 0 && res.data) {
      issueOptions.value = res.data.list.map(i => ({
        value: i.id,
        label: `${i.issueKey} - ${i.title}`
      }))
    }
  } catch { /* silent */ }
}

// People view helpers — kept for future use if needed
// (Grid rendering delegated to WeekGrid / MonthGrid sub-components)

// Project view helpers — delegated to sub-components

function navigate(delta: number) {
  if (viewMode.value === 'week') {
    const d = new Date(currentWeekStart.value)
    d.setDate(d.getDate() + delta * 7)
    currentWeekStart.value = formatDateKey(d)
  } else {
    const [year, month] = currentMonthDate.value.split('-').map(Number)
    const d = new Date(year, month - 1 + delta, 1)
    currentMonthDate.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  }
}

function goToday() {
  currentWeekStart.value = getMonday(new Date())
  const now = new Date()
  currentMonthDate.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function switchView(mode: 'week' | 'month') {
  viewMode.value = mode
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
  }
}

function openAddDialog(date?: string) {
  editingEntry.value = null
  dateMode.value = 'single'
  extraRecords.value = []
  form.value = {
    issueId: undefined,
    workDate: date || formatDateKey(new Date()),
    dateRange: undefined,
    durationText: '',
    startTimeStr: undefined,
    workType: undefined,
    description: ''
  }
  showDialog.value = true
  searchIssues('')
}

function addAnotherRecord() {
  extraRecords.value.push({ workDate: form.value.workDate, durationText: '' })
}

function openEditDialog(entry: TimeEntryVO) {
  editingEntry.value = entry
  dateMode.value = 'single'
  extraRecords.value = []
  form.value = {
    issueId: entry.issueId,
    workDate: entry.workDate,
    dateRange: undefined,
    durationText: formatDurationInput(entry.duration),
    startTimeStr: entry.startTime != null ? `${String(Math.floor(entry.startTime / 60)).padStart(2, '0')}:${String(entry.startTime % 60).padStart(2, '0')}` : undefined,
    workType: entry.workType || undefined,
    description: entry.description || ''
  }
  // Ensure current issue is in options
  if (entry.issueKey) {
    const existing = issueOptions.value.find(o => o.value === entry.issueId)
    if (!existing) {
      issueOptions.value = [{ value: entry.issueId, label: `${entry.issueKey} - ${entry.issueTitle || ''}` }, ...issueOptions.value]
    }
  }
  showDialog.value = true
}

function closeDialog() {
  showDialog.value = false
  editingEntry.value = null
}

async function saveEntry() {
  if (!form.value.issueId || !form.value.workDate || !form.value.durationText) {
    Message.warning('请填写工单、日期和时长')
    return
  }

  const duration = parseDuration(form.value.durationText)
  if (!duration || duration <= 0) {
    Message.warning('时长格式无效，请使用如 2h30m, 1h, 45m')
    return
  }

  const startTime = form.value.startTimeStr ? parseTimeToMinutes(form.value.startTimeStr) : undefined

  saving.value = true
  try {
    if (editingEntry.value) {
      await timeEntryApi.update(editingEntry.value.id, {
        issueId: form.value.issueId,
        workDate: form.value.workDate,
        duration,
        startTime,
        workType: form.value.workType || undefined,
        description: form.value.description || undefined
      })
      Message.success('工时已更新')
    } else {
      await timeEntryApi.create({
        issueId: form.value.issueId,
        workDate: form.value.workDate,
        duration,
        startTime,
        workType: form.value.workType || undefined,
        description: form.value.description || undefined
      })
      Message.success('工时已添加')
    }
    closeDialog()
    reloadCurrentTab()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function deleteEntry() {
  if (!editingEntry.value) return

  Modal.warning({
    title: '确认删除',
    content: '确定要删除这条工时记录吗？此操作不可撤销。',
    okText: '删除',
    cancelText: '取消',
    hideCancel: false,
    onOk: async () => {
      deleting.value = true
      try {
        await timeEntryApi.delete(editingEntry.value!.id)
        Message.success('工时已删除')
        closeDialog()
        reloadCurrentTab()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '删除失败')
      } finally {
        deleting.value = false
      }
    }
  })
}

// Helpers
function formatDuration(minutes: number): string {
  if (!minutes || minutes === 0) return '0h'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m}m`
  if (m === 0) return `${h}h`
  return `${h}h${m}m`
}

function formatDurationInput(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m}m`
  if (m === 0) return `${h}h`
  return `${h}h${m}m`
}

function parseDuration(text: string): number | null {
  const cleaned = text.trim().toLowerCase()
  let total = 0
  const weekMatch = cleaned.match(/(\d+)\s*w/)
  const dayMatch = cleaned.match(/(\d+)\s*d/)
  const hourMatch = cleaned.match(/(\d+)\s*h/)
  const minMatch = cleaned.match(/(\d+)\s*m/)

  if (weekMatch) total += parseInt(weekMatch[1]) * 5 * 8 * 60
  if (dayMatch) total += parseInt(dayMatch[1]) * 8 * 60
  if (hourMatch) total += parseInt(hourMatch[1]) * 60
  if (minMatch) total += parseInt(minMatch[1])

  // If just a number, treat as hours
  if (!weekMatch && !dayMatch && !hourMatch && !minMatch) {
    const num = parseFloat(cleaned)
    if (!isNaN(num)) total = Math.round(num * 60)
  }

  return total > 0 ? total : null
}

function parseTimeToMinutes(timeStr: string): number | undefined {
  if (!timeStr) return undefined
  const parts = timeStr.split(':')
  if (parts.length !== 2) return undefined
  return parseInt(parts[0]) * 60 + parseInt(parts[1])
}

function formatDateKey(d: Date): string {
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatDateDisplay(d: Date): string {
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}`
}

function getMonday(d: Date): string {
  const date = new Date(d)
  const day = date.getDay()
  const diff = date.getDate() - day + (day === 0 ? -6 : 1)
  date.setDate(diff)
  return formatDateKey(date)
}

// Watchers - reload data on date navigation
watch(currentWeekStart, () => { if (viewMode.value === 'week') reloadCurrentTab() })
watch(currentMonthDate, () => { if (viewMode.value === 'month') reloadCurrentTab() })

// Init
onMounted(() => {
  // Load data for the currently active tab (may be restored from URL)
  if (activeTab.value === 'projects') {
    loadProjectSummaries()
  } else if (activeTab.value === 'people') {
    loadEntries()
  }
  // Preload some issues for the add dialog
  searchIssues('')
})
</script>

<style scoped>
.timesheet-page { height: 100%; display: flex; flex-direction: column; overflow: hidden; position: relative; }
.timesheet-header { padding: 16px 24px 0; }
.page-title { font-size: 18px; font-weight: 600; color: var(--tf-text-primary); margin: 0; }

/* Tabs */
.timesheet-tabs { display: flex; gap: 0; padding: 12px 24px 0; border-bottom: 1px solid var(--tf-border-light); }
.tab-btn { padding: 8px 16px; font-size: 13px; color: var(--tf-text-secondary); background: none; border: none; border-bottom: 2px solid transparent; cursor: pointer; transition: color 0.15s, border-color 0.15s; margin-bottom: -1px; }
.tab-btn:hover { color: var(--tf-text-primary); }
.tab-btn.active { color: var(--tf-accent); border-bottom-color: var(--tf-accent); font-weight: 500; }

/* Controls */
.timesheet-controls { padding: 12px 24px; display: flex; align-items: center; justify-content: space-between; }
.controls-left { display: flex; flex-direction: column; gap: 6px; }
.user-selector { display: flex; align-items: center; gap: 8px; cursor: pointer; font-size: 14px; font-weight: 500; color: var(--tf-text-primary); }
.user-avatar-dot { width: 12px; height: 12px; border-radius: 50%; background: var(--tf-accent); }
.selector-arrow { font-size: 10px; color: var(--tf-text-tertiary); }
.project-selector { display: flex; align-items: center; gap: 8px; }
.filters { display: flex; align-items: center; gap: 6px; font-size: 12px; margin-top: 4px; }
.filter-label { color: var(--tf-text-tertiary); }
.filter-value { color: var(--tf-text-secondary); }

/* Date bar */
.timesheet-datebar { padding: 8px 24px 12px; display: flex; align-items: center; justify-content: space-between; }
.date-info { display: flex; flex-direction: column; gap: 2px; }
.date-range { font-size: 15px; font-weight: 500; color: var(--tf-text-primary); }
.total-time { font-size: 12px; color: var(--tf-text-tertiary); }
.date-nav { display: flex; align-items: center; gap: 8px; }
.nav-btn { height: 28px; padding: 0 10px; border: 1px solid var(--tf-border); border-radius: var(--tf-radius-md); background: transparent; color: var(--tf-text-secondary); font-size: 12px; cursor: pointer; transition: background 0.15s, color 0.15s; }
.nav-btn:hover { background: var(--tf-bg-hover); color: var(--tf-text-primary); }
.today-btn { font-weight: 500; }
.view-toggle { display: flex; border: 1px solid var(--tf-border); border-radius: var(--tf-radius-md); overflow: hidden; }
.toggle-btn { height: 28px; padding: 0 12px; border: none; background: transparent; color: var(--tf-text-secondary); font-size: 12px; cursor: pointer; transition: background 0.15s, color 0.15s; }
.toggle-btn + .toggle-btn { border-left: 1px solid var(--tf-border); }
.toggle-btn.active { background: var(--tf-accent-bg); color: var(--tf-accent); font-weight: 500; }
.add-time-btn { height: 32px; padding: 0 14px; border: none; border-radius: var(--tf-radius-md); background: var(--tf-accent); color: #fff; font-size: 12px; font-weight: 500; cursor: pointer; transition: opacity 0.15s; }
.add-time-btn:hover { opacity: 0.9; }

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

/* Empty state */
.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 48px 24px; text-align: center; }
.empty-icon { font-size: 48px; margin-bottom: 16px; opacity: 0.7; }
.empty-title { font-size: 16px; font-weight: 600; color: var(--tf-text-primary); margin-bottom: 8px; }
.empty-desc { font-size: 13px; color: var(--tf-text-tertiary); max-width: 360px; line-height: 1.5; }
.empty-hint { display: flex; align-items: center; gap: 6px; margin-top: 20px; padding: 10px 16px; background: var(--tf-bg-surface); border-radius: var(--tf-radius-md); border: 1px solid var(--tf-border-light); }
.hint-icon { font-size: 14px; }
.empty-hint span:last-child { font-size: 12px; color: var(--tf-text-secondary); }

/* Loading */
.loading-overlay { position: absolute; top: 0; left: 0; right: 0; bottom: 0; display: flex; align-items: center; justify-content: center; background: rgba(0,0,0,0.05); z-index: 10; pointer-events: none; }

/* Dialog */
.time-dialog { padding: 4px 0; }
.dialog-field { margin-bottom: 16px; }
.dialog-label { display: block; font-size: 12px; color: var(--tf-text-tertiary); margin-bottom: 6px; font-weight: 500; }
.dialog-row { display: flex; gap: 12px; }
.flex-1 { flex: 1; }

.author-display { display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: var(--tf-bg-surface); border: 1px solid var(--tf-border-light); border-radius: var(--tf-radius-md); }
.author-avatar { width: 24px; height: 24px; border-radius: 4px; background: linear-gradient(135deg, #f59e0b, #ef4444); display: flex; align-items: center; justify-content: center; font-size: 11px; color: #fff; font-weight: 600; }
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
