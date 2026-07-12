<template>
  <div class="timesheet-page">
    <!-- Header -->
    <div class="timesheet-header">
      <h1 class="page-title">时间表</h1>
    </div>

    <!-- Tabs -->
    <div class="timesheet-tabs">
      <button class="tab-btn" :class="{ active: activeTab === 'people' }" @click="activeTab = 'people'">人员</button>
      <button class="tab-btn" :class="{ active: activeTab === 'projects' }" @click="activeTab = 'projects'">项目</button>
      <button class="tab-btn" :class="{ active: activeTab === 'workgroups' }" @click="activeTab = 'workgroups'">工作群组</button>
    </div>

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
          <span class="filter-edit">✏️</span>
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
    <div v-if="viewMode === 'week'" class="week-grid">
      <div
        v-for="(day, index) in weekDays"
        :key="index"
        class="day-column"
        :class="{ today: isToday(day.date), weekend: day.isWeekend }"
        @click="openAddDialog(day.date)"
      >
        <div class="day-header">
          <span class="day-hours">{{ formatDuration(getDayTotal(day.date)) }}</span>
          <span class="day-name">{{ day.dayName }}</span>
          <span class="day-date">{{ day.dateNum }}</span>
        </div>
        <div class="day-entries">
          <div v-for="entry in getDayEntries(day.date)" :key="entry.id" class="time-entry" @click.stop="openEditDialog(entry)">
            <div class="entry-issue" @click.stop="$router.push(`/issues/${entry.issueId}`)">{{ entry.issueKey || entry.issueId }}</div>
            <div class="entry-duration">{{ formatDuration(entry.duration) }}</div>
            <div v-if="entry.description" class="entry-desc">{{ entry.description }}</div>
            <div v-if="entry.workType" class="entry-type">{{ workTypeLabel(entry.workType) }}</div>
          </div>
        </div>
        <div class="day-footer">
          <span class="day-total" :class="{ insufficient: getDayTotal(day.date) > 0 && getDayTotal(day.date) < 480 && !day.isWeekend }">
            {{ formatDuration(getDayTotal(day.date)) }} / 8h
          </span>
        </div>
      </div>
    </div>

    <!-- Month View -->
    <div v-else class="month-grid">
      <div class="month-header-row">
        <div v-for="name in ['周一','周二','周三','周四','周五','周六','周日']" :key="name" class="month-header-cell">{{ name }}</div>
      </div>
      <div class="month-body">
        <div
          v-for="(day, index) in monthDays"
          :key="index"
          class="month-cell"
          :class="{ today: isToday(day.date), weekend: day.isWeekend, 'other-month': !day.currentMonth }"
          @click="openAddDialog(day.date)"
        >
          <div class="month-cell-header">
            <span class="month-cell-date">{{ day.dateNum }}</span>
            <span v-if="getDayTotal(day.date) > 0" class="month-cell-total">{{ formatDuration(getDayTotal(day.date)) }}</span>
          </div>
          <div class="month-cell-entries">
            <div v-for="entry in getDayEntries(day.date).slice(0, 2)" :key="entry.id" class="month-entry" @click.stop="openEditDialog(entry)">
              <span class="month-entry-key">{{ entry.issueKey }}</span>
              <span class="month-entry-dur">{{ formatDuration(entry.duration) }}</span>
            </div>
            <div v-if="getDayEntries(day.date).length > 2" class="month-entry-more">
              +{{ getDayEntries(day.date).length - 2 }} 更多
            </div>
          </div>
        </div>
      </div>
    </div>

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
import { timeEntryApi, issueApi } from '@/api'
import type { TimeEntryVO } from '@/api/timeEntry'

const authStore = useAuthStore()

// State
const activeTab = ref<'people' | 'projects' | 'workgroups'>('people')
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
  const lastDay = new Date(year, month, 0)

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

// Data loading
async function loadEntries() {
  loading.value = true
  try {
    let startDate: string, endDate: string
    if (viewMode.value === 'week') {
      startDate = currentWeekStart.value
      const end = new Date(currentWeekStart.value)
      end.setDate(end.getDate() + 6)
      endDate = formatDateKey(end)
    } else {
      const [year, month] = currentMonthDate.value.split('-').map(Number)
      startDate = `${year}-${String(month).padStart(2, '0')}-01`
      const lastDay = new Date(year, month, 0)
      endDate = formatDateKey(lastDay)
    }

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

// Actions
function getDayEntries(dateKey: string): TimeEntryVO[] {
  return timeEntries.value.filter(e => e.workDate === dateKey)
}

function getDayTotal(dateKey: string): number {
  return getDayEntries(dateKey).reduce((sum, e) => sum + e.duration, 0)
}

function isToday(dateKey: string): boolean {
  return dateKey === formatDateKey(new Date())
}

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
  loadEntries()
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
    loadEntries()
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
        loadEntries()
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

function workTypeLabel(type: string): string {
  const map: Record<string, string> = {
    Development: '开发', Testing: '测试', Documentation: '文档',
    Design: '设计', Review: '代码审查', Meeting: '会议', Other: '其他'
  }
  return map[type] || type
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

// Watchers
watch(currentWeekStart, () => { if (viewMode.value === 'week') loadEntries() })
watch(currentMonthDate, () => { if (viewMode.value === 'month') loadEntries() })

// Init
onMounted(() => {
  loadEntries()
  // Preload some issues for the selector
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
.filters { display: flex; align-items: center; gap: 6px; font-size: 12px; }
.filter-label { color: var(--tf-text-tertiary); }
.filter-value { color: var(--tf-text-secondary); }
.filter-edit { cursor: pointer; font-size: 11px; }

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

/* Week Grid */
.week-grid { flex: 1; display: grid; grid-template-columns: repeat(7, 1fr); border-top: 1px solid var(--tf-border); overflow: hidden; }
.day-column { display: flex; flex-direction: column; border-right: 1px solid var(--tf-border-light); overflow: hidden; cursor: pointer; transition: background 0.1s; }
.day-column:last-child { border-right: none; }
.day-column:hover { background: var(--tf-bg-hover); }
.day-column.today { background: var(--tf-accent-bg); }
.day-column.today:hover { background: rgba(56, 139, 253, 0.12); }
.day-column.weekend { background: var(--tf-bg-surface); }
.day-column.weekend:hover { background: var(--tf-bg-hover); }
.day-header { display: flex; align-items: baseline; gap: 6px; padding: 8px 10px; border-bottom: 1px solid var(--tf-border-light); flex-shrink: 0; }
.day-hours { font-size: 11px; color: var(--tf-text-tertiary); }
.day-name { font-size: 12px; font-weight: 500; color: var(--tf-text-secondary); }
.day-date { font-size: 12px; color: var(--tf-text-tertiary); }
.day-entries { flex: 1; overflow-y: auto; padding: 6px; display: flex; flex-direction: column; gap: 4px; }
.time-entry { padding: 6px 8px; border-radius: var(--tf-radius-sm); background: var(--tf-bg-elevated); border: 1px solid var(--tf-border-light); cursor: pointer; transition: border-color 0.15s; }
.time-entry:hover { border-color: var(--tf-accent); }
.entry-issue { font-size: 11px; font-weight: 500; color: var(--tf-accent); margin-bottom: 2px; cursor: pointer; }
.entry-issue:hover { text-decoration: underline; }
.entry-duration { font-size: 12px; font-weight: 600; color: var(--tf-text-primary); }
.entry-desc { font-size: 10px; color: var(--tf-text-tertiary); margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.entry-type { font-size: 10px; color: var(--tf-text-muted); margin-top: 2px; }
.day-footer { padding: 6px 10px; border-top: 1px solid var(--tf-border-light); flex-shrink: 0; }
.day-total { font-size: 11px; color: var(--tf-text-tertiary); }
.day-total.insufficient { color: var(--tf-warning); font-weight: 500; }

/* Month Grid */
.month-grid { flex: 1; display: flex; flex-direction: column; border-top: 1px solid var(--tf-border); overflow: hidden; }
.month-header-row { display: grid; grid-template-columns: repeat(7, 1fr); border-bottom: 1px solid var(--tf-border-light); }
.month-header-cell { padding: 6px 8px; font-size: 11px; font-weight: 500; color: var(--tf-text-tertiary); text-align: center; text-transform: uppercase; }
.month-body { flex: 1; display: grid; grid-template-columns: repeat(7, 1fr); grid-template-rows: repeat(6, 1fr); overflow: hidden; }
.month-cell { border-right: 1px solid var(--tf-border-light); border-bottom: 1px solid var(--tf-border-light); padding: 4px 6px; cursor: pointer; overflow: hidden; transition: background 0.1s; min-height: 0; }
.month-cell:nth-child(7n) { border-right: none; }
.month-cell:hover { background: var(--tf-bg-hover); }
.month-cell.today { background: var(--tf-accent-bg); }
.month-cell.weekend { background: var(--tf-bg-surface); }
.month-cell.other-month { opacity: 0.4; }
.month-cell-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 2px; }
.month-cell-date { font-size: 11px; font-weight: 500; color: var(--tf-text-secondary); }
.month-cell-total { font-size: 10px; color: var(--tf-accent); font-weight: 500; }
.month-cell-entries { overflow: hidden; }
.month-entry { display: flex; justify-content: space-between; padding: 1px 4px; border-radius: 2px; margin-bottom: 1px; font-size: 10px; cursor: pointer; }
.month-entry:hover { background: var(--tf-bg-hover); }
.month-entry-key { color: var(--tf-accent); font-weight: 500; }
.month-entry-dur { color: var(--tf-text-tertiary); }
.month-entry-more { font-size: 9px; color: var(--tf-text-muted); text-align: center; padding: 1px; }

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
.remove-record-btn:hover { background: rgba(248,81,73,0.1); color: var(--tf-danger); }

.dialog-actions { display: flex; justify-content: space-between; align-items: center; padding-top: 16px; border-top: 1px solid var(--tf-border-light); margin-top: 8px; }
.actions-left { display: flex; gap: 8px; }
.actions-right { display: flex; gap: 8px; }

.dialog-footer { display: flex; justify-content: space-between; width: 100%; }
.footer-right { display: flex; gap: 8px; }
.form-hint { font-size: 11px; color: var(--tf-text-tertiary); }
</style>
