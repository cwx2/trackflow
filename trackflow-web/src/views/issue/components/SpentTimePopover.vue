<template>
  <Transition name="stp-fade">
    <div v-if="visible" class="spent-time-panel" @click.self="$emit('update:visible', false)">
      <div class="stp-content" ref="panelRef">
        <div class="stp-header">
          <span class="stp-title">⏱ 工时明细</span>
          <span class="stp-total">总计: {{ formatDuration(totalMinutes) }}</span>
        </div>

        <!-- Loading state -->
        <div v-if="loading" class="stp-loading">
          <a-spin :size="16" />
          <span>加载中...</span>
        </div>

        <!-- Empty state -->
        <EmptyState
          v-else-if="entries.length === 0"
          icon="clock-circle"
          title="暂无工时记录"
          description="点击下方按钮添加第一条工时"
          :compact="true"
        />

        <!-- Time entries list -->
        <div v-else class="stp-list">
          <div
            v-for="entry in entries"
            :key="entry.id"
            class="stp-item"
          >
            <div class="stp-item-main">
              <span class="stp-user">{{ entry.userName || '未知用户' }}</span>
              <span class="stp-duration">{{ formatDuration(entry.duration) }}</span>
              <button v-if="canAddTime" class="stp-edit-btn" title="编辑" @click="$emit('edit-time', entry)">
                <svg width="12" height="12" viewBox="0 0 16 16" fill="currentColor">
                  <path d="M11.013 1.427a1.75 1.75 0 012.474 0l1.086 1.086a1.75 1.75 0 010 2.474l-8.61 8.61c-.21.21-.47.364-.756.445l-3.251.93a.75.75 0 01-.927-.928l.929-3.25c.081-.286.235-.547.445-.758l8.61-8.61zm1.414 1.06a.25.25 0 00-.354 0L10.811 3.75l1.439 1.44 1.263-1.263a.25.25 0 000-.354l-1.086-1.086zM11.189 6.25L9.75 4.81 3.34 11.22a.25.25 0 00-.065.108l-.568 1.99 1.99-.568a.25.25 0 00.108-.065l6.384-6.435z"/>
                </svg>
              </button>
            </div>
            <div class="stp-item-meta">
              <span class="stp-date">{{ formatDate(entry.workDate) }}</span>
              <span v-if="entry.workType" class="stp-type">
                <span v-if="entry.workTypeColor" class="stp-type-dot" :style="{ background: entry.workTypeColor }"></span>
                {{ entry.workType }}
              </span>
            </div>
            <div v-if="entry.description" class="stp-desc">{{ entry.description }}</div>
          </div>
        </div>

        <!-- Footer with add button -->
        <div v-if="canAddTime" class="stp-footer">
          <button class="stp-add-btn" @click="$emit('add-time')">
            <span class="stp-add-icon">+</span>
            <span>记录工时</span>
          </button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { formatDuration } from '@/utils/duration'
import { formatDate } from '@/utils/date'
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { timeEntryApi } from '@/api'
import type { TimeEntryVO } from '@/api/timeEntry'
import { EmptyState } from '@/components/base'

const props = defineProps<{
  issueId: string
  visible: boolean
  canAddTime?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  'add-time': []
  'edit-time': [entry: TimeEntryVO]
}>()

const loading = ref(false)
const entries = ref<TimeEntryVO[]>([])
const totalMinutes = ref(0)
const panelRef = ref<HTMLDivElement | null>(null)

watch(() => props.visible, async (val) => {
  if (val && props.issueId) {
    await loadEntries()
  }
})

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.visible) {
    emit('update:visible', false)
  }
}

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
})

async function loadEntries() {
  loading.value = true
  try {
    const res = await timeEntryApi.listByIssue(props.issueId)
    const list = res.data || []
    // Sort by date descending
    list.sort((a, b) => {
      if (a.workDate && b.workDate) return b.workDate.localeCompare(a.workDate)
      return 0
    })
    entries.value = list
    totalMinutes.value = list.reduce((sum, e) => sum + (e.duration || 0), 0)
  } catch {
    entries.value = []
    totalMinutes.value = 0
  } finally {
    loading.value = false
  }
}




</script>

<style scoped>
.spent-time-panel {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 50;
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  padding: 80px 280px 0 0;
}

.stp-content {
  background: var(--tf-bg-elevated, var(--color-bg-2));
  border: 1px solid var(--tf-border-light);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  max-height: 420px;
  min-width: 340px;
  max-width: 440px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.stp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--tf-border-light);
  flex-shrink: 0;
}

.stp-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--tf-text-primary);
}

.stp-total {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-accent);
}

.stp-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 32px 16px;
  justify-content: center;
  color: var(--tf-text-tertiary);
  font-size: 12px;
}

.stp-list {
  overflow-y: auto;
  flex: 1;
  min-height: 0;
  padding: 4px 0;
}

.stp-item {
  padding: 10px 16px;
  border-bottom: 1px solid var(--tf-border-light);
  transition: background 100ms;
}

.stp-item:last-child {
  border-bottom: none;
}

.stp-item:hover {
  background: var(--tf-bg-hover);
}

.stp-item-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.stp-user {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 180px;
  flex: 1;
}

.stp-duration {
  font-size: 13px;
  font-weight: 600;
  color: var(--tf-accent);
  white-space: nowrap;
}

.stp-edit-btn {
  display: none;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: none;
  background: none;
  color: var(--tf-text-tertiary);
  border-radius: 3px;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.12s, color 0.12s;
}
.stp-edit-btn:hover {
  background: var(--tf-bg-hover);
  color: var(--tf-text-primary);
}
.stp-item:hover .stp-edit-btn {
  display: flex;
}

.stp-item-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 3px;
}

.stp-date {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.stp-type {
  font-size: 11px;
  color: var(--tf-text-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
}

.stp-type-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.stp-desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--tf-text-secondary);
  line-height: 1.4;
  white-space: pre-line;
  word-break: break-word;
}

.stp-footer {
  border-top: 1px solid var(--tf-border-light);
  padding: 10px 16px;
  flex-shrink: 0;
}

.stp-add-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: none;
  color: var(--tf-accent);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  padding: 4px 0;
  transition: opacity 150ms;
}

.stp-add-btn:hover {
  opacity: 0.8;
}

.stp-add-icon {
  font-size: 15px;
  font-weight: 700;
}

/* Transition */
.stp-fade-enter-active,
.stp-fade-leave-active {
  transition: opacity 150ms;
}

.stp-fade-enter-active .stp-content,
.stp-fade-leave-active .stp-content {
  transition: transform 150ms, opacity 150ms;
}

.stp-fade-enter-from,
.stp-fade-leave-to {
  opacity: 0;
}

.stp-fade-enter-from .stp-content,
.stp-fade-leave-to .stp-content {
  transform: translateY(-4px);
  opacity: 0;
}
</style>
