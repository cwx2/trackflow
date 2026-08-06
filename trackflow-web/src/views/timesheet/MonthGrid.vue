<template>
  <div class="month-grid">
    <div class="month-header-row">
      <div v-for="name in ['周一','周二','周三','周四','周五','周六','周日']" :key="name" class="month-header-cell">{{ name }}</div>
    </div>
    <div class="month-body">
      <div
        v-for="day in monthDays"
        :key="day.date"
        class="month-cell"
        :class="{ today: isToday(day.date), weekend: day.isWeekend, 'other-month': !day.currentMonth }"
        @click="$emit('dayClick', day.date)"
      >
        <div class="month-cell-header">
          <span class="month-cell-date">{{ day.dateNum }}</span>
          <span v-if="getDayTotal(day.date) > 0" class="month-cell-total">{{ formatDuration(getDayTotal(day.date)) }}</span>
        </div>
        <div class="month-cell-entries">
          <div
            v-for="entry in getDayEntries(day.date).slice(0, 2)"
            :key="entry.id"
            class="month-entry"
            @click.stop="$emit('entryClick', entry)"
          >
            <span class="month-entry-key" :class="{ deleted: entry.issueDeleted }">{{ entry.issueDeleted ? '[已删除]' : entry.issueKey }}</span>
            <span v-if="entry.ongoing" class="month-entry-dur month-entry-ongoing">⏱</span>
            <span v-else class="month-entry-dur">{{ formatDuration(entry.duration) }}</span>
          </div>
          <div v-if="getDayEntries(day.date).length > 2" class="month-entry-more">
            +{{ getDayEntries(day.date).length - 2 }} 更多
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { TimeEntryVO } from '@/api/timeEntry'

interface MonthDayInfo {
  date: string
  dateNum: number
  isWeekend: boolean
  currentMonth: boolean
}

const props = defineProps<{
  monthDays: MonthDayInfo[]
  entries: TimeEntryVO[]
}>()

defineEmits<{
  dayClick: [date: string]
  entryClick: [entry: TimeEntryVO]
}>()

function getDayEntries(dateKey: string): TimeEntryVO[] {
  return props.entries.filter(e => e.workDate === dateKey)
}

function getDayTotal(dateKey: string): number {
  return getDayEntries(dateKey).reduce((sum, e) => sum + (e.duration || 0), 0)
}

function isToday(dateKey: string): boolean {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return dateKey === `${year}-${month}-${day}`
}

function formatDuration(minutes: number): string {
  if (!minutes || minutes === 0) return '0h'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m}m`
  if (m === 0) return `${h}h`
  return `${h}h${m}m`
}
</script>

<style scoped>
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
.month-entry-key.deleted { color: var(--tf-text-tertiary); font-style: italic; }
.month-entry-dur { color: var(--tf-text-tertiary); }
.month-entry-ongoing { color: var(--tf-success, #3fb950); }
.month-entry-more { font-size: 9px; color: var(--tf-text-muted); text-align: center; padding: 1px; }
</style>
