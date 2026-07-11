<template>
  <div class="issue-page">
    <!-- 左侧查询面板 (YouTrack 风格) -->
    <aside class="query-panel">
      <!-- 顶部标题 -->
      <div class="panel-top">
        <div class="panel-top-title">
          <span class="panel-label">查询</span>
          <span class="panel-total">{{ totalIssues }}</span>
        </div>
        <a-button type="text" size="mini" @click="showCreateQuery = true">
          <template #icon><icon-plus /></template>
        </a-button>
      </div>

      <!-- 搜索框 -->
      <div class="panel-search">
        <a-input
          v-model="panelSearch"
          placeholder="搜索查询..."
          size="small"
          allow-clear
        >
          <template #prefix><icon-search /></template>
        </a-input>
      </div>

      <!-- 分组: 草稿箱 -->
      <div class="query-group">
        <div class="group-header" @click="toggleGroup('drafts')">
          <span class="group-arrow">{{ expandedGroups.has('drafts') ? '▾' : '▸' }}</span>
          <span class="group-title">草稿箱</span>
        </div>
      </div>

      <!-- 分组: 项目 -->
      <div class="query-group">
        <div class="group-header" @click="toggleGroup('projects')">
          <span class="group-arrow">{{ expandedGroups.has('projects') ? '▾' : '▸' }}</span>
          <span class="group-title">项目</span>
        </div>
        <div v-if="expandedGroups.has('projects')" class="group-items">
          <div
            v-for="p in projectList"
            :key="p.id"
            class="query-item"
            :class="{ active: activeProjectId === p.id }"
            @click="selectProject(p)"
          >
            <span class="query-name">{{ p.name }}</span>
          </div>
        </div>
      </div>

      <!-- 分组: 标签 -->
      <div class="query-group">
        <div class="group-header" @click="toggleGroup('tags')">
          <span class="group-arrow">{{ expandedGroups.has('tags') ? '▾' : '▸' }}</span>
          <span class="group-title">标签</span>
        </div>
      </div>

      <!-- 分组: 已保存的搜索 -->
      <div class="query-group">
        <div class="group-header" @click="toggleGroup('saved')">
          <span class="group-arrow">{{ expandedGroups.has('saved') ? '▾' : '▸' }}</span>
          <span class="group-title">已保存的搜索</span>
        </div>
        <div v-if="expandedGroups.has('saved')" class="group-items">
          <div
            v-for="q in filteredQueries"
            :key="q.id"
            class="query-item"
            :class="{ active: activeQueryId === q.id }"
            @click="selectQuery(q)"
          >
            <span class="query-name">{{ q.name }}</span>
            <span class="query-count">{{ formatCount(q.count) }}</span>
          </div>
          <div v-if="filteredQueries.length === 0" class="empty-queries">
            暂无保存的搜索
          </div>
        </div>
      </div>
    </aside>

    <!-- 右侧 Issue 列表 -->
    <section class="issue-list-area">
      <!-- 筛选栏 -->
      <div class="filter-bar">
        <div class="filter-left">
          <span class="current-query-name">{{ activeQueryName }}</span>
          <span class="issue-total-badge">{{ totalIssues }} 个问题</span>
        </div>
        <div class="filter-right">
          <a-select v-model="filterProject" placeholder="所有项目" size="small" style="width: 120px" allow-clear>
            <a-option v-for="p in projectList" :key="p.id" :value="p.id">{{ p.key }}</a-option>
          </a-select>
          <a-select v-model="filterStatus" placeholder="所有状态" size="small" style="width: 120px" allow-clear>
            <a-option value="open">待处理</a-option>
            <a-option value="in_progress">进行中</a-option>
            <a-option value="done">已完成</a-option>
          </a-select>
          <a-button type="primary" size="small" @click="toggleInlineCreate">
            {{ showInlineCreate ? '取消' : '创建工单' }}
          </a-button>
        </div>
      </div>

      <!-- 内联快速创建区域 (YouTrack 风格) -->
      <div v-if="showInlineCreate" class="inline-create">
        <div class="inline-create-row">
          <a-select
            v-model="quickForm.projectId"
            placeholder="项目"
            size="small"
            style="width: 140px"
            allow-search
          >
            <a-option v-for="p in projectList" :key="p.id" :value="p.id">{{ p.key }} - {{ p.name }}</a-option>
          </a-select>
          <a-input
            v-model="quickForm.title"
            placeholder="输入工单标题后按 Enter 快速创建..."
            size="small"
            class="inline-title-input"
            @keyup.enter="quickCreate"
            ref="quickTitleRef"
          />
          <a-select v-model="quickForm.issueType" size="small" style="width: 80px">
            <a-option value="Task">任务</a-option>
            <a-option value="Bug">缺陷</a-option>
            <a-option value="Feature">需求</a-option>
          </a-select>
          <a-select v-model="quickForm.priority" size="small" style="width: 80px">
            <a-option value="Normal">普通</a-option>
            <a-option value="High">高</a-option>
            <a-option value="Critical">紧急</a-option>
            <a-option value="Low">低</a-option>
          </a-select>
          <a-button type="primary" size="small" :loading="quickCreating" :disabled="!quickForm.projectId || !quickForm.title" @click="quickCreate">
            创建
          </a-button>
          <a-tooltip content="打开完整创建页面">
            <router-link to="/issues/create">
              <a-button type="text" size="small">
                <icon-expand />
              </a-button>
            </router-link>
          </a-tooltip>
        </div>
      </div>

      <!-- Issue 表格 -->
      <div class="issue-table">
        <div class="table-header">
          <div class="col-key">编号</div>
          <div class="col-title">标题</div>
          <div class="col-assignee">负责人</div>
          <div class="col-status">状态</div>
          <div class="col-priority">优先级</div>
          <div class="col-updated">更新时间</div>
        </div>
        <div class="table-body">
          <div
            v-for="issue in issues"
            :key="issue.id"
            class="table-row"
            @click="openIssue(issue)"
          >
            <div class="col-key">
              <span class="issue-key">{{ issue.issueKey }}</span>
            </div>
            <div class="col-title">
              <span class="issue-title-text">{{ issue.title }}</span>
            </div>
            <div class="col-assignee">
              <span class="assignee-name">{{ issue.assigneeName || '—' }}</span>
            </div>
            <div class="col-status">
              <span class="status-badge" :style="{ background: getStatusColor(issue.statusId) }">
                {{ getStatusName(issue.statusId) }}
              </span>
            </div>
            <div class="col-priority">
              <span class="priority-dot" :class="'priority-' + issue.priority?.toLowerCase()"></span>
              {{ issue.priority }}
            </div>
            <div class="col-updated">
              <span class="time-ago">{{ formatTime(issue.updatedAt) }}</span>
            </div>
          </div>

          <a-empty v-if="issues.length === 0 && !loading" description="暂无问题" />
        </div>
      </div>

      <!-- 分页 -->
      <div class="pagination-bar" v-if="totalIssues > 0">
        <a-pagination
          v-model:current="currentPage"
          :total="totalIssues"
          :page-size="pageSize"
          size="small"
          show-total
          @change="goPage"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { IconPlus, IconSearch, IconExpand } from '@arco-design/web-vue/es/icon'
import { Message } from '@arco-design/web-vue'
import { projectApi, issueApi, queryApi } from '@/api'

const router = useRouter()
const route = useRoute()

// 查询面板数据
const savedQueries = ref<any[]>([])
const projectList = ref<any[]>([])
const activeQueryId = ref<number | null>(null)
const activeQueryName = ref('所有工单')
const activeProjectId = ref<number | null>(null)
const expandedGroups = reactive(new Set<string>(['saved']))
const panelSearch = ref('')
const showCreateQuery = ref(false)

// Issue 列表
const issues = ref<any[]>([])
const totalIssues = ref(0)
const currentPage = ref(1)
const pageSize = 20
const loading = ref(false)

// 筛选
const filterProject = ref<number | undefined>(undefined)
const filterStatus = ref<string | undefined>(undefined)

// 内联快速创建
const showInlineCreate = ref(false)
const quickCreating = ref(false)
const quickTitleRef = ref<any>(null)
const quickForm = reactive({
  projectId: undefined as number | undefined,
  title: '',
  issueType: 'Task',
  priority: 'Normal'
})

function toggleInlineCreate() {
  showInlineCreate.value = !showInlineCreate.value
  if (showInlineCreate.value) {
    // 如果有筛选项目，自动填入
    if (activeProjectId.value) {
      quickForm.projectId = activeProjectId.value
    }
    setTimeout(() => {
      quickTitleRef.value?.focus()
    }, 100)
  }
}

async function quickCreate() {
  if (!quickForm.projectId || !quickForm.title.trim()) return
  quickCreating.value = true
  try {
    await issueApi.create({
      projectId: String(quickForm.projectId),
      title: quickForm.title.trim(),
      issueType: quickForm.issueType,
      priority: quickForm.priority
    })
    Message.success('工单创建成功')
    quickForm.title = ''
    // 刷新列表
    loadIssues()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '创建失败')
  } finally {
    quickCreating.value = false
  }
}

// 状态映射
const statusMap: Record<number, { name: string; color: string }> = {
  1: { name: '待处理', color: '#4CAF50' },
  2: { name: '进行中', color: '#2196F3' },
  3: { name: '代码审查', color: '#9C27B0' },
  4: { name: '测试中', color: '#FF9800' },
  5: { name: '已完成', color: '#607D8B' },
  6: { name: '已取消', color: '#9E9E9E' },
  7: { name: '已重开', color: '#F44336' }
}

// 过滤查询
const filteredQueries = computed(() => {
  if (!panelSearch.value) return savedQueries.value
  const kw = panelSearch.value.toLowerCase()
  return savedQueries.value.filter(q => q.name.toLowerCase().includes(kw))
})

function getStatusName(id: number) { return statusMap[id]?.name || '未知' }
function getStatusColor(id: number) { return statusMap[id]?.color || '#666' }

function formatCount(count: number) {
  if (count >= 10000) return Math.floor(count / 1000) + 'k+'
  if (count >= 1000) return (count / 1000).toFixed(1) + 'k'
  return String(count)
}

function formatTime(dt: string) {
  if (!dt) return ''
  const d = new Date(dt)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 60) return `${mins}分钟前`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days}天前`
  return d.toLocaleDateString('zh-CN')
}

function toggleGroup(group: string) {
  if (expandedGroups.has(group)) {
    expandedGroups.delete(group)
  } else {
    expandedGroups.add(group)
  }
}

async function loadPanel() {
  try {
    const res = await queryApi.getPanel()
    const data = res.data || {}
    savedQueries.value = [
      ...(data.pinned || []),
      ...(data.queries || [])
    ]
  } catch (e) {
    // Mock 数据
    savedQueries.value = [
      { id: 1, name: 'Todo to me', count: 7 },
      { id: 2, name: 'ALL-Todo', count: 389 },
      { id: 3, name: 'Testing', count: 24 },
      { id: 4, name: 'Assigned to me', count: 457 },
      { id: 5, name: 'Commented by me', count: 133 },
      { id: 6, name: 'Reported by me', count: 178 },
      { id: 7, name: 'Pending Code Review', count: 0 },
      { id: 8, name: 'Check-Expired YT', count: 887 },
      { id: 9, name: 'Check-Unassigned YT', count: 24 }
    ]
  }
}

async function loadProjects() {
  try {
    const res = await projectApi.list({ pageSize: 50 })
    projectList.value = res.data?.list || []
  } catch (e) {
    projectList.value = []
  }
}

async function loadIssues() {
  loading.value = true
  try {
    const params: any = { page: currentPage.value, pageSize }
    if (activeQueryId.value) {
      const res = await queryApi.executeById(String(activeQueryId.value), params)
      issues.value = res.data?.list || []
      totalIssues.value = res.data?.pagination?.total || 0
    } else {
      if (filterProject.value) params.projectId = filterProject.value
      if (activeProjectId.value) params.projectId = activeProjectId.value
      const res = await issueApi.list(params)
      issues.value = res.data?.list || []
      totalIssues.value = res.data?.pagination?.total || 0
    }
  } catch (e) {
    issues.value = []
    totalIssues.value = 0
  } finally {
    loading.value = false
  }
}

function selectQuery(q: any) {
  activeQueryId.value = q.id
  activeQueryName.value = q.name
  activeProjectId.value = null
  currentPage.value = 1
  loadIssues()
}

function selectProject(p: any) {
  activeProjectId.value = p.id
  activeQueryId.value = null
  activeQueryName.value = p.name
  currentPage.value = 1
  loadIssues()
}

function openIssue(issue: any) {
  router.push(`/issues/${issue.id}`)
}

function goPage(page: number) {
  currentPage.value = page
  loadIssues()
}

onMounted(() => {
  // 如果 URL 带了 project 参数
  if (route.query.project) {
    activeProjectId.value = Number(route.query.project)
  }
  loadPanel()
  loadProjects()
  loadIssues()
})
</script>

<style scoped>
.issue-page {
  display: flex;
  height: 100%;
}

/* ===== 左侧查询面板 ===== */
.query-panel {
  width: 280px;
  background: var(--tf-bg-surface);
  border-right: 1px solid var(--tf-border);
  overflow-y: auto;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}

.panel-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px 8px;
}

.panel-top-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.panel-total {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-elevated);
  padding: 2px 6px;
  border-radius: 8px;
}

.panel-search {
  padding: 4px 10px 8px;
}

/* 查询分组 */
.query-group {
  padding: 0 6px;
  margin-bottom: 2px;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 8px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.15s;
}
.group-header:hover {
  background: var(--tf-bg-hover);
}

.group-arrow {
  font-size: 10px;
  width: 14px;
  color: var(--tf-text-tertiary);
}

.group-title {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.6px;
}

.group-items {
  padding-left: 8px;
}

.query-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 32px;
  padding: 0 12px;
  cursor: pointer;
  border-radius: 4px;
  margin: 1px 0;
  transition: background 0.15s;
}
.query-item:hover {
  background: var(--tf-bg-hover);
}
.query-item.active {
  background: var(--tf-accent-bg);
  color: var(--tf-accent);
}

.query-name {
  font-size: 13px;
  color: var(--tf-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.query-item.active .query-name {
  color: var(--tf-accent);
}

.query-count {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  flex-shrink: 0;
  margin-left: 8px;
}

.empty-queries {
  padding: 12px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
  text-align: center;
}

/* ===== 右侧 Issue 列表 ===== */
.issue-list-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--tf-border);
  flex-shrink: 0;
}

.filter-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.current-query-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.issue-total-badge {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.filter-right {
  display: flex;
  gap: 8px;
  align-items: center;
}

/* 内联快速创建 */
.inline-create {
  padding: 8px 16px;
  background: var(--tf-bg-surface);
  border-bottom: 1px solid var(--tf-border);
  flex-shrink: 0;
}

.inline-create-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.inline-title-input {
  flex: 1;
}

/* ===== Issue 表格 ===== */
.issue-table {
  flex: 1;
  overflow-y: auto;
}

.table-header {
  display: flex;
  align-items: center;
  height: 36px;
  padding: 0 16px;
  background: var(--tf-bg-surface);
  border-bottom: 1px solid var(--tf-border);
  font-size: 11px;
  color: var(--tf-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.6px;
  position: sticky;
  top: 0;
  z-index: 1;
}

.table-row {
  display: flex;
  align-items: center;
  height: 40px;
  padding: 0 16px;
  border-bottom: 1px solid var(--tf-border-light);
  cursor: pointer;
  transition: background 0.15s;
  font-size: 13px;
}
.table-row:hover {
  background: var(--tf-bg-hover);
}

.col-key { width: 100px; flex-shrink: 0; }
.col-title { flex: 1; min-width: 0; }
.col-assignee { width: 100px; flex-shrink: 0; }
.col-status { width: 100px; flex-shrink: 0; }
.col-priority { width: 80px; flex-shrink: 0; }
.col-updated { width: 90px; flex-shrink: 0; text-align: right; }

.issue-key {
  color: var(--tf-accent);
  font-weight: 500;
  font-size: 12px;
}

.issue-title-text {
  color: var(--tf-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

.assignee-name {
  color: var(--tf-text-secondary);
  font-size: 12px;
}

.status-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 3px;
  font-size: 11px;
  color: #fff;
  font-weight: 500;
}

.priority-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
}
.priority-critical { background: var(--tf-danger); }
.priority-high { background: var(--tf-warning); }
.priority-normal { background: var(--tf-accent); }
.priority-low { background: var(--tf-text-tertiary); }

.time-ago {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

/* ===== 分页 ===== */
.pagination-bar {
  display: flex;
  justify-content: center;
  padding: 12px 16px;
  border-top: 1px solid var(--tf-border);
  flex-shrink: 0;
}
</style>
