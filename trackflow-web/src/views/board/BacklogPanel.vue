<template>
  <transition name="backlog-slide">
    <div v-if="visible" class="backlog-panel">
      <div class="backlog-header">
        <div class="backlog-header-left">
          <h3 class="backlog-title">Backlog</h3>
          <span class="backlog-count">{{ filteredIssues.length }}</span>
        </div>
        <a-button size="mini" type="text" @click="$emit('close')">
          <template #icon><icon-close /></template>
        </a-button>
      </div>

      <!-- 搜索 -->
      <div class="backlog-search">
        <a-input
          v-model="searchKeyword"
          placeholder="搜索 Backlog 工单..."
          size="small"
          allow-clear
          @input="onSearchInput"
          @clear="onSearchClear"
        >
          <template #prefix>
            <icon-search />
          </template>
        </a-input>
      </div>

      <!-- 筛选 -->
      <div class="backlog-filters">
        <a-select
          v-model="filterType"
          placeholder="类型"
          size="mini"
          allow-clear
          style="width: 90px"
          @change="applyFilter"
        >
          <a-option value="Task">任务</a-option>
          <a-option value="Bug">缺陷</a-option>
          <a-option value="Feature">需求</a-option>
          <a-option value="Story">故事</a-option>
        </a-select>
        <a-select
          v-model="filterPriority"
          placeholder="优先级"
          size="mini"
          allow-clear
          style="width: 90px"
          @change="applyFilter"
        >
          <a-option value="Critical">紧急</a-option>
          <a-option value="High">高</a-option>
          <a-option value="Normal">普通</a-option>
          <a-option value="Low">低</a-option>
        </a-select>
      </div>

      <!-- 工单列表 -->
      <a-spin :loading="loading" class="backlog-body-spin">
        <div class="backlog-body">
          <div
            v-for="issue in filteredIssues"
            :key="issue.id"
            class="backlog-card"
            :draggable="true"
            @dragstart="onDragStart($event, issue)"
            @dragend="onDragEnd"
            @click="$emit('open-issue', issue)"
          >
            <div class="backlog-card-header">
              <span class="backlog-card-key">{{ issue.issueKey }}</span>
              <span
                class="backlog-card-priority"
                :class="issue.priority?.toLowerCase()"
                :title="issue.priority"
              >
                {{ priorityIcon(issue.priority) }}
              </span>
            </div>
            <div class="backlog-card-title">{{ issue.title }}</div>
            <div class="backlog-card-footer">
              <span class="backlog-card-type">{{ typeLabel(issue.issueType) }}</span>
              <span v-if="issue.assigneeName" class="backlog-card-assignee">
                {{ issue.assigneeName }}
              </span>
            </div>
          </div>

          <!-- 空状态 -->
          <div v-if="!loading && filteredIssues.length === 0" class="backlog-empty">
            <div class="backlog-empty-icon">📋</div>
            <div class="backlog-empty-title">
              {{ searchKeyword || filterType || filterPriority ? '没有匹配的工单' : 'Backlog 为空' }}
            </div>
            <div class="backlog-empty-desc">
              {{ searchKeyword || filterType || filterPriority
                ? '尝试调整筛选条件'
                : '所有工单都已分配到 Sprint' }}
            </div>
          </div>
        </div>
      </a-spin>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { issueApi } from '@/api'
import type { IssueVO } from '@/api/types'
import { IconSearch, IconClose } from '@arco-design/web-vue/es/icon'
import { localizeIssueType } from '@/utils/fieldLabels'

const props = defineProps<{
  visible: boolean
  projectId: string
}>()

const emit = defineEmits<{
  'close': []
  'open-issue': [issue: IssueVO]
  'drag-start': [issue: IssueVO]
  'drag-end': []
}>()

const loading = ref(false)
const issues = ref<IssueVO[]>([])
const searchKeyword = ref('')
const filterType = ref<string | undefined>(undefined)
const filterPriority = ref<string | undefined>(undefined)

let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null

// Filtered issues (client-side filtering after local search)
const filteredIssues = computed(() => {
  let result = issues.value
  if (filterType.value) {
    result = result.filter(i => i.issueType === filterType.value)
  }
  if (filterPriority.value) {
    result = result.filter(i => i.priority === filterPriority.value)
  }
  return result
})

// Watch visibility and project changes to load data
watch(
  () => [props.visible, props.projectId],
  ([visible, projectId]) => {
    if (visible && projectId) {
      loadBacklog()
    }
  },
  { immediate: true }
)

async function loadBacklog() {
  if (!props.projectId) return
  loading.value = true
  try {
    const PAGE_SIZE = 100
    let page = 1
    let allIssues: IssueVO[] = []

    // 循环加载所有未规划工单（Backlog 通常量不大，但也需处理超 100 的情况）
    while (true) {
      const res = await issueApi.list({
        projectId: props.projectId,
        sprintId: 'none',
        hideResolved: 'true',
        keyword: searchKeyword.value || undefined,
        page,
        pageSize: PAGE_SIZE
      })
      const list = res.data?.list || []
      const total = res.data?.pagination?.total || 0
      allIssues = allIssues.concat(list)

      if (allIssues.length >= total || list.length < PAGE_SIZE || allIssues.length >= 500) {
        break
      }
      page++
    }

    issues.value = allIssues
  } catch {
    issues.value = []
    Message.error('加载 Backlog 失败')
  } finally {
    loading.value = false
  }
}

function onSearchInput() {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => {
    loadBacklog()
  }, 350)
}

function onSearchClear() {
  searchKeyword.value = ''
  loadBacklog()
}

function applyFilter() {
  // Filters are computed client-side, no need to reload
}

function onDragStart(event: DragEvent, issue: IssueVO) {
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', issue.id)
    event.dataTransfer.setData('application/x-backlog-issue', JSON.stringify({
      id: issue.id,
      issueKey: issue.issueKey,
      title: issue.title,
      statusId: issue.statusId
    }))
  }
  emit('drag-start', issue)
}

function onDragEnd() {
  emit('drag-end')
}

function priorityIcon(priority: string): string {
  const map: Record<string, string> = { Critical: '🔴', High: '🟠', Normal: '🔵', Low: '⚪' }
  return map[priority] || '🔵'
}

function typeLabel(type: string): string {
  return localizeIssueType(type)
}

/** Called by parent to refresh after a successful drop */
function removeIssue(issueId: string) {
  issues.value = issues.value.filter(i => i.id !== issueId)
}

function refresh() {
  loadBacklog()
}

defineExpose({ removeIssue, refresh })
</script>

<style scoped>
.backlog-panel {
  width: 280px;
  min-width: 280px;
  height: 100%;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--color-border);
  background: var(--color-bg-1);
  overflow: hidden;
  flex-shrink: 0;
}

.backlog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px 8px;
  flex-shrink: 0;
}

.backlog-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.backlog-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-1);
  margin: 0;
}

.backlog-count {
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-fill-3);
  padding: 2px 6px;
  border-radius: 3px;
}

.backlog-search {
  padding: 0 12px 8px;
  flex-shrink: 0;
}

.backlog-filters {
  display: flex;
  gap: 6px;
  padding: 0 12px 8px;
  flex-shrink: 0;
}

.backlog-body-spin {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.backlog-body {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

/* ===== Backlog Card ===== */
.backlog-card {
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 8px 10px;
  cursor: grab;
  transition: border-color 0.15s, box-shadow 0.15s;
  user-select: none;
}

.backlog-card:hover {
  border-color: rgb(var(--primary-6));
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.06);
}

.backlog-card:active {
  cursor: grabbing;
}

.backlog-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.backlog-card-key {
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-3);
}

.backlog-card-priority {
  font-size: 10px;
}

.backlog-card-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-1);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.backlog-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}

.backlog-card-type {
  font-size: 10px;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  padding: 1px 5px;
  border-radius: 3px;
}

.backlog-card-assignee {
  font-size: 10px;
  color: var(--color-text-2);
}

/* ===== Empty State ===== */
.backlog-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  text-align: center;
}

.backlog-empty-icon {
  font-size: 32px;
  margin-bottom: 8px;
}

.backlog-empty-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-2);
  margin-bottom: 4px;
}

.backlog-empty-desc {
  font-size: 12px;
  color: var(--color-text-3);
}

/* ===== Slide Animation ===== */
.backlog-slide-enter-active,
.backlog-slide-leave-active {
  transition: width 0.2s ease, opacity 0.2s ease;
  overflow: hidden;
}

.backlog-slide-enter-from,
.backlog-slide-leave-to {
  width: 0;
  min-width: 0;
  opacity: 0;
}
</style>
