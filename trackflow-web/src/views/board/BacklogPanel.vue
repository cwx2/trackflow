<template>
  <transition name="backlog-slide">
    <div v-if="visible" class="backlog-panel">
      <div class="backlog-header">
        <div class="backlog-header-left">
          <h3 class="backlog-title">Backlog</h3>
          <span class="backlog-count">{{ flatIssues.length }}</span>
        </div>
        <div class="backlog-header-right">
          <!-- View mode 切换：List / Tree -->
          <div class="view-mode-toggle" role="group" aria-label="视图模式">
            <button
              class="view-mode-btn"
              :class="{ 'view-mode-btn--active': effectiveViewMode === 'list' }"
              title="平铺列表视图"
              :aria-pressed="effectiveViewMode === 'list'"
              @click="effectiveViewMode = 'list'"
            >
              <icon-list />
            </button>
            <button
              class="view-mode-btn"
              :class="{ 'view-mode-btn--active': effectiveViewMode === 'tree' }"
              title="树形层级视图"
              :aria-pressed="effectiveViewMode === 'tree'"
              @click="effectiveViewMode = 'tree'"
            >
              <icon-branch />
            </button>
          </div>
          <a-button size="mini" type="text" @click="$emit('close')">
            <template #icon><icon-close /></template>
          </a-button>
        </div>
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

      <!-- 工单列表 / 树形 -->
      <a-spin :loading="loading" class="backlog-body-spin">
        <div class="backlog-body">
          <!-- ===== List View（平铺） ===== -->
          <template v-if="effectiveViewMode === 'list'">
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
                  :title="localizePriority(issue.priority)"
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
          </template>

          <!-- ===== Tree View（树形） ===== -->
          <template v-else>
            <BacklogTreeNode
              v-for="node in treeNodes"
              :key="node.issue.id"
              :node="node"
              :depth="0"
              @drag-start="(evt: DragEvent, issue: IssueVO) => onDragStart(evt, issue)"
              @drag-end="onDragEnd"
              @open-issue="(issue: IssueVO) => $emit('open-issue', issue)"
            />
          </template>

          <!-- 空状态 -->
          <div v-if="!loading && flatIssues.length === 0" class="backlog-empty">
            <div class="backlog-empty-icon">📋</div>
            <div class="backlog-empty-title">
              {{ searchKeyword || filterType || filterPriority ? '没有匹配的工单' : 'Backlog 为空' }}
            </div>
            <div class="backlog-empty-desc">
              {{ searchKeyword || filterType || filterPriority
                ? '尝试调整筛选条件'
                : '所有工单都已在看板中' }}
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
import { issueApi, queryApi } from '@/api'
import type { IssueVO } from '@/api/types'
import { IconSearch, IconClose, IconList, IconBranch } from '@arco-design/web-vue/es/icon'
import { localizeIssueType, localizePriority } from '@/utils/fieldLabels'
import BacklogTreeNode from './BacklogTreeNode.vue'

/** 树节点结构（递归） */
export interface BacklogTreeNodeData {
  issue: IssueVO
  children: BacklogTreeNodeData[]
  /** 是否匹配搜索（false = 非搜索结果的父节点，以灰色背景区分） */
  isSearchMatch: boolean
}

const props = defineProps<{
  visible: boolean
  projectId: string
  /** Comma-separated status IDs currently shown on the board (to exclude from Backlog) */
  boardStatusIds: string
  /** External filter keyword from the board header Filter input (synced from parent) */
  filterKeyword?: string
  /**
   * Backlog 视图模式（来自 Board Settings 配置）：list=平铺，tree=树形。
   * 用户可在面板内覆盖（临时），默认跟随此 prop。
   */
  viewMode?: 'list' | 'tree'
  /**
   * 过滤 Backlog 工单的保存搜索 ID（null 时使用默认过滤）。
   */
  savedQueryId?: string | null
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

/**
 * 当前生效的视图模式：优先使用用户在面板内切换的值，否则跟随 prop。
 * 切换 prop 时（Board Settings 保存后），重置用户覆盖值。
 */
const userOverrideViewMode = ref<'list' | 'tree' | null>(null)
const effectiveViewMode = computed<'list' | 'tree'>({
  get: () => userOverrideViewMode.value ?? (props.viewMode || 'list'),
  set: (val) => { userOverrideViewMode.value = val }
})

// 当 viewMode prop 改变时，清除用户覆盖，跟随 prop
watch(() => props.viewMode, () => {
  userOverrideViewMode.value = null
})

let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null

// ===== 过滤 =====

/** List 模式下的过滤结果 */
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

/** 所有展开的平铺工单（用于计数，不管 view mode） */
const flatIssues = computed(() => filteredIssues.value)

// ===== Tree View 构建 =====

/**
 * 构建树形节点列表。
 * - 有父工单（parentId）且父工单在 issues 中的：嵌套在父节点下
 * - 其余（无父/父不在列表中）：作为顶层节点
 * - 不在搜索结果中但作为搜索结果父节点的工单，以 isSearchMatch=false 标记（灰色背景）
 */
const treeNodes = computed<BacklogTreeNodeData[]>(() => {
  const allIssues = filteredIssues.value
  const issueMap = new Map<string, IssueVO>(allIssues.map(i => [i.id, i]))

  // 同时需要显示 "非搜索结果但是搜索结果父节点" 的工单
  // 如果 issues.value 和 filteredIssues.value 不同（有类型/优先级过滤），则父节点需要从全集查找
  // 简化：只从 filteredIssues 中构建树（type/priority 过滤应用后父子关系仍保持）
  const childrenMap = new Map<string, IssueVO[]>()
  const roots: IssueVO[] = []

  for (const issue of allIssues) {
    const parentId = (issue as any).parentId as string | undefined
    if (parentId && issueMap.has(parentId)) {
      if (!childrenMap.has(parentId)) {
        childrenMap.set(parentId, [])
      }
      childrenMap.get(parentId)!.push(issue)
    } else {
      roots.push(issue)
    }
  }

  function buildNode(issue: IssueVO, isMatch: boolean): BacklogTreeNodeData {
    const children = (childrenMap.get(issue.id) || []).map(c => buildNode(c, true))
    return { issue, children, isSearchMatch: isMatch }
  }

  return roots.map(r => buildNode(r, true))
})

// Watch visibility and project changes to load data
watch(
  () => [props.visible, props.projectId, props.boardStatusIds, props.savedQueryId],
  ([visible, projectId]) => {
    if (visible && projectId) {
      loadBacklog()
    }
  },
  { immediate: true }
)

// Watch external filterKeyword changes to reload backlog
let filterKeywordDebounceTimer: ReturnType<typeof setTimeout> | null = null
watch(
  () => props.filterKeyword,
  () => {
    if (!props.visible || !props.projectId) return
    if (filterKeywordDebounceTimer) clearTimeout(filterKeywordDebounceTimer)
    filterKeywordDebounceTimer = setTimeout(() => {
      loadBacklog()
    }, 350)
  }
)

async function loadBacklog() {
  if (!props.projectId) return
  loading.value = true
  try {
    const PAGE_SIZE = 100
    let page = 1
    let allIssues: IssueVO[] = []

    // Merge internal search keyword with external filter keyword (prefer external if set)
    const effectiveKeyword = props.filterKeyword?.trim() || searchKeyword.value || undefined

    if (props.savedQueryId) {
      // 使用配置的 Saved Search 过滤工单（再叠加 statusIdNot 排除看板上的工单）
      // executeById 通过路径传 id，再传 statusIdNot 等追加过滤
      while (true) {
        const res = await queryApi.executeById(props.savedQueryId, {
          page,
          pageSize: PAGE_SIZE,
          hideResolved: 'true'
        })
        const list = (res.data?.list || []) as IssueVO[]
        const total = res.data?.pagination?.total || 0
        // 客户端排除在看板上的工单
        const boardStatusSet = new Set(props.boardStatusIds ? props.boardStatusIds.split(',') : [])
        const filtered = boardStatusSet.size > 0
          ? list.filter(i => !boardStatusSet.has(i.statusId))
          : list
        // 客户端关键词过滤
        const keyworded = effectiveKeyword
          ? filtered.filter(i => i.title?.toLowerCase().includes(effectiveKeyword.toLowerCase()) || i.issueKey?.toLowerCase().includes(effectiveKeyword.toLowerCase()))
          : filtered
        allIssues = allIssues.concat(keyworded)
        if (allIssues.length >= total || list.length < PAGE_SIZE || allIssues.length >= 500) {
          break
        }
        page++
      }
    } else {
      // 默认过滤：不在看板上的所有未解决工单
      while (true) {
        const res = await issueApi.list({
          projectId: props.projectId,
          hideResolved: 'true',
          statusIdNot: props.boardStatusIds || undefined,
          keyword: effectiveKeyword,
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

.backlog-header-right {
  display: flex;
  align-items: center;
  gap: 4px;
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

/* ===== View Mode Toggle ===== */
.view-mode-toggle {
  display: flex;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  overflow: hidden;
}

.view-mode-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 22px;
  border: none;
  background: transparent;
  color: var(--color-text-3);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  padding: 0;
}

.view-mode-btn:hover {
  background: var(--color-fill-2);
  color: var(--color-text-1);
}

.view-mode-btn + .view-mode-btn {
  border-left: 1px solid var(--color-border);
}

.view-mode-btn--active {
  background: rgb(var(--primary-6));
  color: #fff;
}

.view-mode-btn--active:hover {
  background: rgb(var(--primary-6));
  color: #fff;
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
