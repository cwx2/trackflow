<template>
  <div class="week-grid">
    <div
      v-for="(day, index) in weekDays"
      :key="index"
      class="day-column"
      :class="{ today: isToday(day.date), weekend: day.isWeekend }"
      @click="$emit('dayClick', day.date)"
    >
      <div class="day-header">
        <span class="day-hours">{{ formatDuration(getDayTotal(day.date)) }}</span>
        <span class="day-name">{{ day.dayName }}</span>
        <span class="day-date">{{ day.dateNum }}</span>
      </div>
      <div class="day-entries">
        <div
          v-for="entry in getDayEntries(day.date)"
          :key="entry.id"
          class="time-entry"
          @click.stop="$emit('entryClick', entry)"
        >
          <div class="entry-issue" @click.stop="$emit('issueClick', entry)">{{ entry.issueKey || entry.issueId }}</div>
          <div class="entry-duration">{{ formatDuration(entry.duration) }}</div>
          <div v-if="entry.userName && showUser" class="entry-user">{{ entry.userName }}</div>
          <div v-if="entry.description && !showUser" class="entry-desc">{{ entry.description }}</div>
          <div v-if="entry.workType" class="entry-type">{{ workTypeLabel(entry.workType) }}</div>
        </div>
      </div>
      <div class="day-footer">
        <span
          class="day-total"
          :class="{ insufficient: showQuota && getDayTotal(day.date) > 0 && getDayTotal(day.date) < 480 && !day.isWeekend }"
        >
          {{ formatDuration(getDayTotal(day.date)) }}{{ showQuota ? ' / 8h' : '' }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { TimeEntryVO } from '@/api/timeEntry'

interface DayInfo {
  date: string
  dateNum: number
  dayName: string
  isWeekend: boolean
}

const props = defineProps<{
  weekDays: DayInfo[]
  entries: TimeEntryVO[]
  showUser?: boolean
  showQuota?: boolean
}>()

defineEmits<{
  dayClick: [date: string]
  entryClick: [entry: TimeEntryVO]
  issueClick: [entry: TimeEntryVO]
}>()

function getDayEntries(dateKey: string): TimeEntryVO[] {
  return props.entries.filter(e => e.workDate === dateKey)
}

function getDayTotal(dateKey: string): number {
  return getDayEntries(dateKey).reduce((sum, e) => sum + e.duration, 0)
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

function workTypeLabel(type: string): string {
  const map: Record<string, string> = {
    Development: '开发', Testing: '测试', Documentation: '文档',
    Design: '设计', Review: '代码审查', Meeting: '会议', Other: '其他'
  }
  return map[type] || type
}
</script>

<style scoped>
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
.entry-user { font-size: 10px; color: var(--tf-text-secondary); margin-top: 2px; font-weight: 500; }
.day-footer { padding: 6px 10px; border-top: 1px solid var(--tf-border-light); flex-shrink: 0; }
.day-total { font-size: 11px; color: var(--tf-text-tertiary); }
.day-total.insufficient { color: var(--tf-warning); font-weight: 500; }
</style>
