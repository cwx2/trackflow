<template>
  <div class="week-grid">
    <div
      v-for="day in weekDays"
      :key="day.date"
      class="day-column"
      :class="{ today: isToday(day.date), weekend: day.isWeekend }"
      @click="$emit('dayClick', day.date)"
    >
      <div class="day-header">
        <span class="day-hours">{{ formatDuration(dayTotal(day.date)) }}</span>
        <span class="day-name">{{ day.dayName }}</span>
        <span class="day-date">{{ day.dateNum }}</span>
      </div>
      <div class="day-entries">
        <div
          v-for="entry in dayEntries(day.date)"
          :key="entry.id"
          class="time-entry"
          @click.stop="$emit('entryClick', entry)"
        >
          <div class="entry-issue" :class="{ deleted: entry.issueDeleted }" @click.stop="$emit('issueClick', entry)">
            <template v-if="entry.issueDeleted">[已删除] {{ entry.issueKey || '' }}</template>
            <template v-else>{{ entry.issueKey || entry.issueId }}</template>
          </div>
          <div v-if="entry.ongoing" class="entry-duration entry-ongoing">
            <span class="ongoing-dot"></span> 计时中
          </div>
          <div v-else class="entry-duration">{{ formatDuration(entry.duration ?? 0) }}</div>
          <div v-if="entry.userName && showUser" class="entry-user">{{ entry.userName }}</div>
          <div v-if="entry.loggedByName" class="entry-logged-by">由 {{ entry.loggedByName }} 代录</div>
          <div v-if="entry.description && !showUser" class="entry-desc">{{ entry.description }}</div>
          <div v-if="entry.workType" class="entry-type">{{ workTypeLabel(entry.workType) }}</div>
        </div>
      </div>
      <div class="day-footer">
        <span
          class="day-total"
          :class="{ insufficient: showQuota && dayTotal(day.date) > 0 && dayTotal(day.date) < quotaMinutes && !day.isWeekend }"
        >
          {{ formatDuration(dayTotal(day.date)) }}{{ showQuota && !day.isWeekend ? ` / ${quotaLabel}` : '' }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { formatDuration } from '@/utils/duration'
import { computed } from 'vue'
import { isToday, getDayEntries, getDayTotal, workTypeLabel } from '@/utils/timesheet'
import type { WeekDayInfo } from '@/utils/timesheet'
import type { TimeEntryVO } from '@/api/timeEntry'

const props = defineProps<{
  weekDays: WeekDayInfo[]
  entries: TimeEntryVO[]
  showUser?: boolean
  showQuota?: boolean
  quotaMinutes?: number
  quotaText?: string
}>()

/** 每日配额分钟数（默认 480 = 8h） */
const quotaMinutes = computed(() => props.quotaMinutes ?? 480)
/** 配额文字标签（默认 "8h"） */
const quotaLabel = computed(() => props.quotaText ?? `${Math.round(quotaMinutes.value / 60)}h`)

defineEmits<{
  dayClick: [date: string]
  entryClick: [entry: TimeEntryVO]
  issueClick: [entry: TimeEntryVO]
}>()

const dayEntries = (date: string) => getDayEntries(props.entries, date)
const dayTotal   = (date: string) => getDayTotal(props.entries, date)
</script>

<style scoped>
.week-grid { flex: 1; display: grid; grid-template-columns: repeat(7, 1fr); border-top: 1px solid var(--tf-border); overflow: hidden; }
.day-column { display: flex; flex-direction: column; border-right: 1px solid var(--tf-border-light); overflow: hidden; cursor: pointer; transition: background 0.1s; }
.day-column:last-child { border-right: none; }
.day-column:hover { background: var(--tf-bg-hover); }
.day-column.today { background: var(--tf-accent-bg); }
.day-column.today:hover { background: var(--tf-accent-medium); }
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
.entry-issue.deleted { color: var(--tf-text-tertiary); font-style: italic; cursor: default; }
.entry-issue.deleted:hover { text-decoration: none; }
.entry-duration { font-size: 12px; font-weight: 600; color: var(--tf-text-primary); }
.entry-ongoing { color: var(--tf-success); display: flex; align-items: center; gap: 4px; font-weight: 500; }
.ongoing-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--tf-success); animation: timer-pulse-anim 1.5s ease-in-out infinite; }
@keyframes timer-pulse-anim { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
.entry-desc { font-size: 10px; color: var(--tf-text-tertiary); margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.entry-type { font-size: 10px; color: var(--tf-text-muted); margin-top: 2px; }
.entry-user { font-size: 10px; color: var(--tf-text-secondary); margin-top: 2px; font-weight: 500; }
.entry-logged-by { font-size: 10px; color: var(--tf-text-tertiary); margin-top: 2px; font-style: italic; }
.day-footer { padding: 6px 10px; border-top: 1px solid var(--tf-border-light); flex-shrink: 0; }
.day-total { font-size: 11px; color: var(--tf-text-tertiary); }
.day-total.insufficient { color: var(--tf-warning); font-weight: 500; }
</style>
