<template>
  <!-- 未配置项目时显示引导 -->
  <div v-if="!config.projectId" class="widget-configure-hint">
    <icon-calendar :size="32" class="hint-icon" />
    <span class="hint-text">点击「编辑配置」选择项目</span>
  </div>
  <!-- 已配置项目，渲染月历 -->
  <div v-else class="widget-calendar">
    <!-- 月份导航 -->
    <div class="calendar-nav">
      <button class="calendar-nav-btn" @click.stop="prevMonth" title="上一月">
        <icon-left :size="12" />
      </button>
      <span class="calendar-title">{{ calendarTitle }}</span>
      <button class="calendar-nav-btn" @click.stop="nextMonth" title="下一月">
        <icon-right :size="12" />
      </button>
    </div>
    <!-- 星期表头 -->
    <div class="calendar-weekdays">
      <span v-for="wd in ['日','一','二','三','四','五','六']" :key="wd" class="calendar-wd">{{ wd }}</span>
    </div>
    <!-- 日期格子 -->
    <div class="calendar-grid">
      <div
        v-for="cell in calendarCells"
        :key="cell.date"
        class="calendar-cell"
        :class="{
          'other-month': !cell.isCurrentMonth,
          'today': cell.isToday,
          'has-issues': cell.issues.length > 0
        }"
      >
        <span class="cell-day">{{ cell.day }}</span>
        <template v-if="cell.isCurrentMonth">
          <div
            v-for="issue in cell.issues.slice(0, 2)"
            :key="issue.id"
            class="cell-issue"
            :class="{ overdue: cell.isOverdue && !cell.isToday }"
            :title="issue.issueKey + ' ' + issue.title"
            @click.stop="navigateToIssue(issue.id)"
          >
            <span class="cell-issue-key">{{ issue.issueKey }}</span>
            <span class="cell-issue-title">{{ issue.title }}</span>
          </div>
          <div v-if="cell.issues.length > 2" class="cell-issue-more">
            +{{ cell.issues.length - 2 }} 更多
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { IconCalendar, IconLeft, IconRight } from '@arco-design/web-vue/es/icon'
import { issueApi } from '@/api/issue'
import type { IssueVO } from '@/api/types'

const props = defineProps<{
  config: Record<string, any>
}>()

const emit = defineEmits<{
  loaded: []
  error: [message: string]
}>()

const router = useRouter()

const calendarYear = ref(new Date().getFullYear())
const calendarMonth = ref(new Date().getMonth() + 1)
const calendarIssueMap = ref<Map<string, IssueVO[]>>(new Map())

interface CalendarCell {
  date: string
  day: number
  isCurrentMonth: boolean
  isToday: boolean
  isOverdue: boolean
  issues: IssueVO[]
}

const calendarCells = computed((): CalendarCell[] => {
  const today = new Date()
  const todayStr = formatDate(today)
  const year = calendarYear.value
  const month = calendarMonth.value
  const firstDay = new Date(year, month - 1, 1)
  const lastDay = new Date(year, month, 0)
  const cells: CalendarCell[] = []
  const startWeekday = firstDay.getDay()

  for (let i = 0; i < startWeekday; i++) {
    const d = new Date(year, month - 1, -startWeekday + i + 1)
    const dateStr = formatDate(d)
    cells.push({ date: dateStr, day: d.getDate(), isCurrentMonth: false, isToday: false, isOverdue: false, issues: [] })
  }

  for (let day = 1; day <= lastDay.getDate(); day++) {
    const d = new Date(year, month - 1, day)
    const dateStr = formatDate(d)
    const issues = calendarIssueMap.value.get(dateStr) || []
    const isOverdue = dateStr < todayStr
    cells.push({ date: dateStr, day, isCurrentMonth: true, isToday: dateStr === todayStr, isOverdue, issues })
  }

  const remaining = 42 - cells.length
  for (let i = 1; i <= remaining; i++) {
    const d = new Date(year, month, i)
    const dateStr = formatDate(d)
    cells.push({ date: dateStr, day: i, isCurrentMonth: false, isToday: false, isOverdue: false, issues: [] })
  }

  return cells
})

const calendarTitle = computed(() => `${calendarYear.value} 年 ${calendarMonth.value} 月`)

function formatDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function prevMonth() {
  if (calendarMonth.value === 1) {
    calendarYear.value--
    calendarMonth.value = 12
  } else {
    calendarMonth.value--
  }
  loadCalendarData()
}

function nextMonth() {
  if (calendarMonth.value === 12) {
    calendarYear.value++
    calendarMonth.value = 1
  } else {
    calendarMonth.value++
  }
  loadCalendarData()
}

function navigateToIssue(issueId: string) {
  router.push(`/issues/${issueId}`)
}

async function loadCalendarData() {
  const config = props.config
  if (!config.projectId) {
    calendarIssueMap.value = new Map()
    return
  }

  try {
    const year = calendarYear.value
    const month = calendarMonth.value
    const dueAfter = `${year}-${String(month).padStart(2, '0')}-01`
    const lastDay = new Date(year, month, 0).getDate()
    const dueBefore = `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`

    const res = await issueApi.list({
      projectId: config.projectId,
      dueAfter,
      dueBefore,
      pageSize: 100,
      page: 1
    })

    const map = new Map<string, IssueVO[]>()
    const issues = res.data?.list || []
    for (const issue of issues) {
      if (!issue.dueDate) continue
      const dateKey = issue.dueDate.substring(0, 10)
      if (!map.has(dateKey)) map.set(dateKey, [])
      map.get(dateKey)!.push(issue)
    }
    calendarIssueMap.value = map
    emit('loaded')
  } catch (e: any) {
    emit('error', e.response?.data?.message || '加载日历数据失败')
  }
}

async function loadData(_force = false) {
  if (!props.config.projectId) {
    emit('loaded')
    return
  }
  await loadCalendarData()
}

onMounted(() => {
  loadData()
})

defineExpose({ loadData })
</script>

<style scoped>
.widget-configure-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: 8px;
  padding: 12px;
}

.hint-icon {
  color: var(--tf-text-tertiary);
  opacity: 0.4;
}

.hint-text {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  text-align: center;
  line-height: 1.4;
}

.widget-calendar {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 4px;
  overflow: hidden;
}

.calendar-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2px;
  flex-shrink: 0;
}

.calendar-nav-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: none;
  background: none;
  color: var(--tf-text-secondary);
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.calendar-nav-btn:hover {
  background: var(--tf-bg-hover);
  color: var(--tf-text-primary);
}

.calendar-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--tf-text-primary);
}

.calendar-weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 1px;
  flex-shrink: 0;
}

.calendar-wd {
  text-align: center;
  font-size: 10px;
  color: var(--tf-text-tertiary);
  padding: 2px 0;
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 1px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.calendar-cell {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-height: 28px;
  padding: 2px 3px;
  border-radius: 3px;
  border: 1px solid transparent;
  transition: background 0.1s;
}

.calendar-cell.has-issues {
  background: var(--tf-bg-hover);
}

.calendar-cell.today {
  border-color: var(--tf-accent, #58a6ff);
}

.calendar-cell.today .cell-day {
  color: var(--tf-accent, #58a6ff);
  font-weight: 700;
}

.calendar-cell.other-month {
  opacity: 0.35;
}

.cell-day {
  font-size: 10px;
  color: var(--tf-text-secondary);
  line-height: 1.2;
  font-weight: 500;
}

.cell-issue {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 1px 3px;
  background: rgba(88, 166, 255, 0.15);
  border-radius: 2px;
  cursor: pointer;
  transition: background 0.1s;
  overflow: hidden;
}

.cell-issue:hover {
  background: rgba(88, 166, 255, 0.3);
}

.cell-issue.overdue {
  background: rgba(248, 81, 73, 0.15);
}

.cell-issue.overdue:hover {
  background: rgba(248, 81, 73, 0.3);
}

.cell-issue-key {
  font-size: 9px;
  font-weight: 600;
  color: var(--tf-accent, #58a6ff);
  white-space: nowrap;
  flex-shrink: 0;
}

.cell-issue.overdue .cell-issue-key {
  color: var(--tf-danger, #f85149);
}

.cell-issue-title {
  font-size: 9px;
  color: var(--tf-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-issue-more {
  font-size: 9px;
  color: var(--tf-text-tertiary);
  padding: 0 2px;
  cursor: default;
}
</style>
