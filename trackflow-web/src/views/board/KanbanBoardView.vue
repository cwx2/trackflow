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

    <!-- 看板主体 -->
    <div class="board-container" v-if="selectedProject && statuses.length > 0">
      <div
        v-for="status in statuses"
        :key="status.id"
        class="board-column"
      >
        <div class="column-header" :style="{ borderTopColor: status.color }">
          <span class="column-title">{{ status.name }}</span>
          <span class="column-count">{{ getColumnIssues(status.id).length }}</span>
        </div>
        <div class="column-body">
          <div
            v-for="issue in getColumnIssues(status.id)"
            :key="issue.id"
            class="kanban-card"
            @click="openIssue(issue)"
          >
            <div class="card-header">
              <span class="card-key">{{ issue.issueKey }}</span>
              <span class="card-priority" :class="issue.priority?.toLowerCase()">
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
          <div v-if="getColumnIssues(status.id).length === 0" class="column-empty">
            无工单
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="empty-state">
      <div class="empty-icon">📊</div>
      <h3 class="empty-title">{{ selectedProject ? '加载中...' : '请选择项目' }}</h3>
      <p class="empty-desc">
        {{ selectedProject ? '正在加载看板数据' : '从上方下拉框选择项目查看看板视图' }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { projectApi, issueApi, sprintApi } from '@/api'
import type { IssueVO, IssueStatusVO } from '@/api/types'

const router = useRouter()

const selectedProject = ref<string | undefined>(undefined)
const selectedSprint = ref<string | undefined>(undefined)
const keyword = ref('')
const projects = ref<any[]>([])
const sprints = ref<any[]>([])
const statuses = ref<IssueStatusVO[]>([])
const issues = ref<IssueVO[]>([])

function getColumnIssues(statusId: string): IssueVO[] {
  return issues.value.filter(i => i.statusId === statusId)
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
  router.push({ name: 'IssueDetail', params: { id: issue.id } })
}

async function loadProjects() {
  try {
    const res = await projectApi.list({ pageSize: 100 })
    projects.value = res.data?.list || []
  } catch { projects.value = [] }
}

async function loadStatuses() {
  try {
    const res = await issueApi.listStatuses()
    statuses.value = res.data || []
  } catch { statuses.value = [] }
}

async function loadSprints() {
  if (!selectedProject.value) { sprints.value = []; return }
  try {
    const res = await sprintApi.listByProject(selectedProject.value)
    sprints.value = res.data || []
  } catch { sprints.value = [] }
}

async function loadBoard() {
  if (!selectedProject.value) { issues.value = []; return }
  await loadSprints()
  try {
    const res = await issueApi.list({
      projectId: selectedProject.value,
      sprintId: selectedSprint.value || undefined,
      keyword: keyword.value || undefined,
      pageSize: 200
    })
    issues.value = res.data?.list || []
  } catch { issues.value = [] }
}

onMounted(async () => {
  await Promise.all([loadProjects(), loadStatuses()])
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

.board-container {
  flex: 1;
  display: flex;
  gap: 12px;
  padding: 16px;
  overflow-x: auto;
  overflow-y: hidden;
}

.board-column {
  min-width: 260px;
  max-width: 300px;
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--color-fill-1);
  border-radius: 8px;
  overflow: hidden;
}

.column-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
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
}

.column-count {
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-fill-3);
  padding: 2px 6px;
  border-radius: 3px;
}

.column-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.kanban-card {
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 10px 12px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.kanban-card:hover {
  border-color: rgb(var(--primary-6));
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
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

.column-empty {
  text-align: center;
  padding: 24px 8px;
  font-size: 12px;
  color: var(--color-text-4);
}

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
</style>
