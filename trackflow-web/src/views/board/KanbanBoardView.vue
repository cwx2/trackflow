<template>
  <div class="kanban-page">
    <!-- 顶部工具栏 -->
    <div class="board-toolbar">
      <div class="toolbar-left">
        <h2 class="page-title">看板</h2>
        <a-select
          v-model="selectedProject"
          placeholder="选择项目"
          style="width: 200px"
          size="small"
          allow-search
          @change="loadBoard"
        >
          <a-option v-for="p in projects" :key="p.id" :value="p.id">
            {{ p.key }} - {{ p.name }}
          </a-option>
        </a-select>
        <a-select
          v-model="selectedSprint"
          placeholder="所有迭代"
          style="width: 180px"
          size="small"
          allow-clear
          :disabled="!selectedProject"
          @change="loadBoard"
        >
          <a-option v-for="s in sprints" :key="s.id" :value="s.id">
            {{ s.name }}
          </a-option>
        </a-select>
      </div>
      <div class="toolbar-right">
        <a-input-search
          v-model="keyword"
          placeholder="搜索工单"
          size="small"
          style="width: 200px"
          @search="loadBoard"
          @press-enter="loadBoard"
        />
      </div>
    </div>

    <!-- 加载状态 -->
    <a-spin :loading="loading" tip="加载看板数据..." class="board-spin">
      <!-- 看板主体 -->
      <div class="board-container" v-if="selectedProject && statuses.length > 0">
        <template v-for="status in statuses" :key="status.id">
          <!-- 有工单的列 或 手动展开的空列：正常展示 -->
          <div
            v-if="getColumnIssues(status.id).length > 0 || expandedEmptyColumns.has(status.id)"
            class="board-column"
            :class="{
              'board-column--expanded-empty': getColumnIssues(status.id).length === 0,
              'board-column--drop-target': dragOverColumnId === status.id,
              'board-column--drop-forbidden': dragOverColumnId === status.id && !isDropAllowed(status.id)
            }"
            @dragover="onDragOver($event, status.id)"
            @dragleave="onDragLeave($event)"
            @drop="onDrop($event, status.id)"
          >
            <div class="column-header" :style="{ borderTopColor: status.color }">
              <span class="column-title">{{ status.name }}</span>
              <span class="column-count">{{ getColumnIssues(status.id).length }}</span>
              <!-- 折叠按钮（仅对手动展开的空列显示） -->
              <button
                v-if="getColumnIssues(status.id).length === 0 && !draggingIssue"
                class="column-collapse-btn"
                :aria-label="`折叠 ${status.name} 列`"
                title="折叠此列"
                @click="collapseColumn(status.id)"
              >✕</button>
            </div>
            <div class="column-body">
              <div
                v-for="issue in getColumnIssues(status.id)"
                :key="issue.id"
                class="kanban-card"
                :class="{
                  'kanban-card--dragging': draggingIssue?.id === issue.id,
                  'kanban-card--transitioning': transitioningIssueIds.has(issue.id)
                }"
                role="button"
                tabindex="0"
                draggable="true"
                @dragstart="onDragStart($event, issue)"
                @dragend="onDragEnd"
                @click="openIssue(issue)"
                @keydown.enter="openIssue(issue)"
              >
                <div class="card-header">
                  <span class="card-key">{{ issue.issueKey }}</span>
                  <span
                    class="card-priority"
                    :class="issue.priority?.toLowerCase()"
                    :title="issue.priority"
                  >
                    {{ priorityIcon(issue.priority) }}
                  </span>
                </div>
                <div class="card-title">{{ issue.title }}</div>
                <div class="card-footer">
                  <span class="card-type">{{ typeLabel(issue.issueType) }}</span>
                  <span class="card-assignee" v-if="issue.assigneeName">
                    {{ issue.assigneeName }}
                  </span>
                </div>
              </div>
              <!-- 展开的空列：空状态引导 / 拖拽放置区 -->
              <div
                v-if="getColumnIssues(status.id).length === 0"
                class="column-empty-state"
                :class="{ 'column-empty-state--drop-hint': draggingIssue && isDropAllowed(status.id) }"
              >
                <template v-if="draggingIssue && isDropAllowed(status.id)">
                  <div class="column-empty-icon">📥</div>
                  <div class="column-empty-text">释放以移动到此状态</div>
                </template>
                <template v-else-if="draggingIssue && !isDropAllowed(status.id)">
                  <div class="column-empty-icon">🚫</div>
                  <div class="column-empty-text">不允许转换到此状态</div>
                </template>
                <template v-else>
                  <div class="column-empty-icon">📭</div>
                  <div class="column-empty-text">该状态下暂无工单</div>
                  <div class="column-empty-hint">拖拽工单到此列或创建新工单</div>
                </template>
              </div>
            </div>
          </div>

          <!-- 空列：折叠为窄条（拖拽时也可作为目标） -->
          <div
            v-else
            class="board-column-collapsed"
            :class="{
              'board-column-collapsed--drop-target': dragOverColumnId === status.id && isDropAllowed(status.id),
              'board-column-collapsed--drop-forbidden': dragOverColumnId === status.id && !isDropAllowed(status.id)
            }"
            role="button"
            tabindex="0"
            :aria-label="`${status.name}，0 个工单，点击展开`"
            :title="`${status.name} (0 工单) - ${draggingIssue ? '释放以移动' : '点击展开'}`"
            @click="!draggingIssue && expandColumn(status.id)"
            @keydown.enter="expandColumn(status.id)"
            @dragover="onDragOver($event, status.id)"
            @dragleave="onDragLeave($event)"
            @drop="onDrop($event, status.id)"
          >
            <div class="collapsed-indicator" :style="{ backgroundColor: status.color || 'var(--color-border)' }"></div>
            <span class="collapsed-name">{{ status.name }}</span>
            <span class="collapsed-count">0</span>
          </div>
        </template>
      </div>

      <!-- 空状态：未选择项目 -->
      <div v-else-if="!selectedProject" class="empty-state">
        <div class="empty-icon">📊</div>
        <h3 class="empty-title">请选择项目</h3>
        <p class="empty-desc">从上方下拉框选择项目查看看板视图</p>
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, h } from 'vue'
import { useRouter } from 'vue-router'
import { Message, Notification } from '@arco-design/web-vue'
import { projectApi, issueApi, sprintApi } from '@/api'
import type { IssueVO, IssueStatusVO, ProjectVO, SprintVO } from '@/api/types'
import { useProjectStore } from '@/stores/project'

const router = useRouter()
const projectStore = useProjectStore()

const selectedProject = computed({
  get: () => projectStore.selectedProjectId,
  set: (val) => projectStore.selectProject(val)
})
const selectedSprint = ref<string | undefined>(undefined)
const keyword = ref('')
const loading = ref(false)
const projects = ref<ProjectVO[]>([])
const sprints = ref<SprintVO[]>([])
const statuses = ref<IssueStatusVO[]>([])
const issues = ref<IssueVO[]>([])

// 被手动展开的空列集合
const expandedEmptyColumns = ref<Set<string>>(new Set())

// ===== 拖拽状态 =====
const draggingIssue = ref<IssueVO | null>(null)
const dragOverColumnId = ref<string | null>(null)
const allowedTargetStatuses = ref<Set<string>>(new Set())
const transitioningIssueIds = ref<Set<string>>(new Set())

// ===== 撤销历史 =====
interface UndoEntry {
  issueId: string
  issueKey: string
  oldStatusId: string
  newStatusId: string
  oldStatusName: string
  newStatusName: string
  timestamp: number
}
const undoStack = ref<UndoEntry[]>([])
const UNDO_TIMEOUT = 10000 // 10 秒内可撤销

function getColumnIssues(statusId: string): IssueVO[] {
  return issues.value.filter(i => i.statusId === statusId)
}

function expandColumn(statusId: string) {
  expandedEmptyColumns.value.add(statusId)
}

function collapseColumn(statusId: string) {
  expandedEmptyColumns.value.delete(statusId)
}

function priorityIcon(priority: string): string {
  const map: Record<string, string> = { Critical: '🔴', High: '🟠', Normal: '🔵', Low: '⚪' }
  return map[priority] || '🔵'
}

function typeLabel(type: string): string {
  const map: Record<string, string> = { Task: '任务', Bug: '缺陷', Feature: '需求', Story: '故事' }
  return map[type] || type
}

function openIssue(issue: IssueVO) {
  // 拖拽结束后不触发点击
  if (draggingIssue.value) return
  router.push({ name: 'IssueDetail', params: { id: issue.id } })
}

// ===== 拖拽逻辑 =====

/** 开始拖拽：获取可用目标状态 */
async function onDragStart(event: DragEvent, issue: IssueVO) {
  draggingIssue.value = issue

  // 设置拖拽数据和效果
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', issue.id)
  }

  // 异步获取该工单可以转换到哪些状态
  try {
    const res = await issueApi.getAvailableTransitions(issue.id)
    const allowed = res.data || []
    allowedTargetStatuses.value = new Set(allowed.map(s => s.id))
  } catch {
    // 获取失败时允许所有状态（后端会二次校验）
    allowedTargetStatuses.value = new Set(statuses.value.map(s => s.id))
  }
}

/** 拖拽结束：清理状态 */
function onDragEnd() {
  draggingIssue.value = null
  dragOverColumnId.value = null
  allowedTargetStatuses.value.clear()
}

/** 拖拽经过列：判断是否允许放置 */
function onDragOver(event: DragEvent, statusId: string) {
  event.preventDefault()
  dragOverColumnId.value = statusId

  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = isDropAllowed(statusId) ? 'move' : 'none'
  }
}

/** 拖拽离开列 */
function onDragLeave(event: DragEvent) {
  // 防止子元素触发 dragleave
  const relatedTarget = event.relatedTarget as HTMLElement | null
  const currentTarget = event.currentTarget as HTMLElement
  if (relatedTarget && currentTarget.contains(relatedTarget)) return
  dragOverColumnId.value = null
}

/** 判断是否允许放置到目标列 */
function isDropAllowed(targetStatusId: string): boolean {
  if (!draggingIssue.value) return false
  // 不能拖到自己当前所在列
  if (draggingIssue.value.statusId === targetStatusId) return false
  // 检查工作流是否允许
  return allowedTargetStatuses.value.has(targetStatusId)
}

/** 释放：执行状态转换 */
async function onDrop(event: DragEvent, targetStatusId: string) {
  event.preventDefault()
  dragOverColumnId.value = null

  const issue = draggingIssue.value
  if (!issue || !isDropAllowed(targetStatusId)) {
    onDragEnd()
    return
  }

  const oldStatusId = issue.statusId
  const targetStatus = statuses.value.find(s => s.id === targetStatusId)

  // 乐观更新：立即在 UI 中移动卡片
  issue.statusId = targetStatusId
  transitioningIssueIds.value.add(issue.id)

  // 清理拖拽状态
  draggingIssue.value = null
  allowedTargetStatuses.value.clear()

  // 调用后端 API
  try {
    await issueApi.transitStatus(issue.id, targetStatusId)

    // 记录撤销历史
    const undoEntry: UndoEntry = {
      issueId: issue.id,
      issueKey: issue.issueKey,
      oldStatusId: oldStatusId,
      newStatusId: targetStatusId,
      oldStatusName: statuses.value.find(s => s.id === oldStatusId)?.name || '',
      newStatusName: targetStatus?.name || '',
      timestamp: Date.now()
    }
    undoStack.value.push(undoEntry)

    // 显示带撤销按钮的通知
    const notifId = `undo-${issue.id}-${Date.now()}`
    Notification.success({
      id: notifId,
      title: '状态变更成功',
      content: `${issue.issueKey} 已移至「${targetStatus?.name || '目标状态'}」`,
      duration: UNDO_TIMEOUT,
      closable: true,
      footer: () => h('button', {
        class: 'undo-btn',
        onClick: () => {
          undoTransition(undoEntry)
          Notification.remove(notifId)
        }
      }, '↩ 撤销 (Ctrl+Z)')
    })
  } catch (e: any) {
    // 回滚：恢复原状态
    issue.statusId = oldStatusId
    const errMsg = e.response?.data?.message || '状态变更失败'
    Message.error(`${issue.issueKey} 移动失败：${errMsg}`)
  } finally {
    transitioningIssueIds.value.delete(issue.id)
  }
}

// ===== 撤销逻辑 =====

/** 撤销状态转换 */
async function undoTransition(entry: UndoEntry) {
  const issue = issues.value.find(i => i.id === entry.issueId)
  if (!issue) {
    Message.warning('工单已不在当前视图中，无法撤销')
    return
  }

  // 乐观更新
  const currentStatusId = issue.statusId
  issue.statusId = entry.oldStatusId
  transitioningIssueIds.value.add(issue.id)

  try {
    await issueApi.undoTransitStatus(issue.id, entry.oldStatusId)
    Message.success(`${entry.issueKey} 已撤销回「${entry.oldStatusName}」`)
    // 从撤销栈移除
    undoStack.value = undoStack.value.filter(e => e !== entry)
  } catch (e: any) {
    // 回滚
    issue.statusId = currentStatusId
    const errMsg = e.response?.data?.message || '撤销失败'
    Message.error(`撤销失败：${errMsg}`)
  } finally {
    transitioningIssueIds.value.delete(issue.id)
  }
}

/** Ctrl+Z 撤销最近一次操作 */
function handleKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'z' && !e.shiftKey) {
    // 找到最近一条有效的撤销记录（10秒内）
    const now = Date.now()
    const validEntries = undoStack.value.filter(entry => now - entry.timestamp < UNDO_TIMEOUT)
    if (validEntries.length > 0) {
      e.preventDefault()
      const lastEntry = validEntries[validEntries.length - 1]
      undoTransition(lastEntry)
    }
  }
}

// ===== 数据加载 =====

async function loadProjects() {
  try {
    const res = await projectApi.list({ pageSize: 100 })
    projects.value = res.data?.list || []
  } catch {
    projects.value = []
    Message.error('加载项目列表失败')
  }
}

async function loadStatuses() {
  try {
    const res = await issueApi.listStatuses()
    statuses.value = res.data || []
  } catch {
    statuses.value = []
    Message.error('加载状态列表失败')
  }
}

async function loadSprints() {
  if (!selectedProject.value) { sprints.value = []; return }
  try {
    const res = await sprintApi.listByProject(selectedProject.value)
    sprints.value = res.data || []
  } catch {
    sprints.value = []
    Message.error('加载迭代列表失败')
  }
}

async function loadBoard() {
  if (!selectedProject.value) { issues.value = []; return }
  // 切换项目/Sprint 时重置手动展开的列
  expandedEmptyColumns.value.clear()
  loading.value = true
  try {
    await loadSprints()
    const res = await issueApi.list({
      projectId: selectedProject.value,
      sprintId: selectedSprint.value || undefined,
      keyword: keyword.value || undefined,
      pageSize: 200
    })
    issues.value = res.data?.list || []
  } catch {
    issues.value = []
    Message.error('加载看板数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadProjects(), loadStatuses()])
  document.addEventListener('keydown', handleKeydown)

  // 自动选择：仅一个项目时自动选中
  const projectIds = projects.value.map(p => p.id)
  projectStore.autoSelectIfNeeded(projectIds)

  // 如果已有选中的项目（从 Store 恢复），自动加载看板
  if (selectedProject.value) {
    loadBoard()
  }
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.kanban-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.board-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-1);
  margin: 0;
}

.board-spin {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.board-container {
  flex: 1;
  display: flex;
  gap: 8px;
  padding: 16px;
  overflow-x: auto;
  overflow-y: hidden;
}

/* ===== 正常列 ===== */
.board-column {
  min-width: 280px;
  max-width: 340px;
  flex: 1 1 280px;
  display: flex;
  flex-direction: column;
  background: var(--color-fill-1);
  border-radius: 8px;
  overflow: hidden;
  transition: box-shadow 0.15s, border-color 0.15s;
  border: 2px solid transparent;
}

.board-column--expanded-empty {
  min-width: 200px;
  max-width: 220px;
  flex: 0 0 200px;
  opacity: 0.7;
}

/* 拖拽放置目标高亮 */
.board-column--drop-target {
  border-color: rgb(var(--primary-6));
  box-shadow: 0 0 0 2px rgba(var(--primary-6), 0.15);
  background: var(--color-fill-2);
}

.board-column--drop-forbidden {
  border-color: rgb(var(--danger-6));
  opacity: 0.6;
}

.column-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border-top: 3px solid var(--color-border);
  flex-shrink: 0;
}

.column-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-1);
  text-transform: uppercase;
  letter-spacing: 0.3px;
  flex: 1;
}

.column-count {
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-fill-3);
  padding: 2px 6px;
  border-radius: 3px;
}

.column-collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: none;
  background: var(--color-fill-3);
  color: var(--color-text-3);
  border-radius: 3px;
  cursor: pointer;
  font-size: 10px;
  line-height: 1;
  transition: background 0.15s, color 0.15s;
}
.column-collapse-btn:hover {
  background: var(--color-fill-4);
  color: var(--color-text-1);
}

.column-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 60px;
}

/* ===== 折叠的空列 ===== */
.board-column-collapsed {
  flex: 0 0 36px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 12px 4px;
  background: var(--color-fill-1);
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
  overflow: hidden;
  border: 2px solid transparent;
}
.board-column-collapsed:hover {
  background: var(--color-fill-2);
}
.board-column-collapsed:focus-visible {
  outline: 2px solid rgb(var(--primary-6));
  outline-offset: -2px;
}

/* 折叠列作为拖拽目标时 */
.board-column-collapsed--drop-target {
  border-color: rgb(var(--primary-6));
  background: var(--color-fill-2);
}
.board-column-collapsed--drop-forbidden {
  border-color: rgb(var(--danger-6));
  opacity: 0.5;
}

.collapsed-indicator {
  width: 20px;
  height: 3px;
  border-radius: 2px;
  flex-shrink: 0;
}

.collapsed-name {
  writing-mode: vertical-rl;
  text-orientation: mixed;
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-3);
  letter-spacing: 0.3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-height: 120px;
}

.collapsed-count {
  font-size: 10px;
  color: var(--color-text-4);
  background: var(--color-fill-3);
  padding: 1px 4px;
  border-radius: 3px;
}

/* ===== 卡片 ===== */
.kanban-card {
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 10px 12px;
  cursor: grab;
  transition: border-color 0.15s, box-shadow 0.15s, opacity 0.15s, transform 0.15s;
  user-select: none;
}
.kanban-card:hover {
  border-color: rgb(var(--primary-6));
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.kanban-card:active {
  cursor: grabbing;
}
.kanban-card:focus-visible {
  outline: 2px solid rgb(var(--primary-6));
  outline-offset: 1px;
}

/* 正在被拖拽的卡片 */
.kanban-card--dragging {
  opacity: 0.4;
  transform: scale(0.97);
  border-color: rgb(var(--primary-6));
}

/* 正在执行状态转换的卡片 */
.kanban-card--transitioning {
  opacity: 0.6;
  pointer-events: none;
  position: relative;
}
.kanban-card--transitioning::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--color-fill-2);
  border-radius: 6px;
  animation: pulse 1s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 0.6; }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.card-key {
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-3);
}

.card-priority {
  font-size: 10px;
}

.card-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.card-type {
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  padding: 2px 6px;
  border-radius: 3px;
}

.card-assignee {
  font-size: 11px;
  color: var(--color-text-2);
}

/* ===== 展开空列的空状态 ===== */
.column-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px 8px;
  text-align: center;
  border-radius: 6px;
  transition: background 0.15s;
}

.column-empty-state--drop-hint {
  background: rgba(var(--primary-6), 0.06);
  border: 1px dashed rgb(var(--primary-6));
}

.column-empty-icon {
  font-size: 24px;
  margin-bottom: 8px;
}

.column-empty-text {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-3);
  margin-bottom: 4px;
}

.column-empty-hint {
  font-size: 11px;
  color: var(--color-text-4);
}

/* ===== 页面空状态 ===== */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text-1);
  margin-bottom: 8px;
}

.empty-desc {
  font-size: 13px;
  color: var(--color-text-3);
}

/* ===== 撤销按钮（Notification footer 中） ===== */
:global(.undo-btn) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  margin-top: 8px;
  border: 1px solid rgb(var(--primary-6));
  background: transparent;
  color: rgb(var(--primary-6));
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
:global(.undo-btn:hover) {
  background: rgb(var(--primary-6));
  color: #fff;
}
</style>
