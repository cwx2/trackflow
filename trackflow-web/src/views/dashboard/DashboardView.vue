<template>
  <div class="dashboard-page">
    <div class="dashboard-header">
      <h1 class="dashboard-title">工作台</h1>
      <span class="dashboard-greeting">{{ greeting }}，{{ userName }}</span>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div class="stat-card" @click="navigateToQuery('assigned-open')">
        <div class="stat-value" :class="{ loading: summaryLoading }">
          <a-skeleton v-if="summaryLoading" :animation="true" style="width:40px;height:24px" />
          <span v-else>{{ summary.assignedOpen }}</span>
        </div>
        <div class="stat-label">待处理</div>
        <div class="stat-icon stat-icon-open">📋</div>
      </div>
      <div class="stat-card" @click="navigateToQuery('assigned-in-progress')">
        <div class="stat-value" :class="{ loading: summaryLoading }">
          <a-skeleton v-if="summaryLoading" :animation="true" style="width:40px;height:24px" />
          <span v-else>{{ summary.assignedInProgress }}</span>
        </div>
        <div class="stat-label">进行中</div>
        <div class="stat-icon stat-icon-progress">🔄</div>
      </div>
      <div class="stat-card" @click="navigateToQuery('completed-week')">
        <div class="stat-value" :class="{ loading: summaryLoading }">
          <a-skeleton v-if="summaryLoading" :animation="true" style="width:40px;height:24px" />
          <span v-else>{{ summary.completedThisWeek }}</span>
        </div>
        <div class="stat-label">本周完成</div>
        <div class="stat-icon stat-icon-done">✅</div>
      </div>
      <div class="stat-card warning" @click="navigateToQuery('overdue')">
        <div class="stat-value" :class="{ loading: summaryLoading }">
          <a-skeleton v-if="summaryLoading" :animation="true" style="width:40px;height:24px" />
          <span v-else>{{ summary.overdue }}</span>
        </div>
        <div class="stat-label">已逾期</div>
        <div class="stat-icon stat-icon-overdue">⚠️</div>
      </div>
      <!-- 第五个卡片：根据角色动态展示 -->
      <div class="stat-card" :class="{ highlight: summary.testingCount > 0 && isTester }" @click="navigateToQuery(isTester ? 'testing' : 'due-soon')">
        <div class="stat-value" :class="{ loading: summaryLoading }">
          <a-skeleton v-if="summaryLoading" :animation="true" style="width:40px;height:24px" />
          <span v-else>{{ isTester ? summary.testingCount : summary.dueSoon }}</span>
        </div>
        <div class="stat-label">{{ isTester ? '待测试' : '即将到期' }}</div>
        <div class="stat-icon">{{ isTester ? '🧪' : '⏰' }}</div>
      </div>
    </div>

    <!-- 角色提示条（当测试人员没有分配工单时显示） -->
    <div v-if="showRoleHint" class="role-hint-bar">
      <span class="role-hint-icon">💡</span>
      <span class="role-hint-text">
        作为测试人员，您可以关注
        <a class="role-hint-link" @click="navigateToQuery('testing')">待测试的工单（{{ summary.testingCount }}）</a>
        或查看
        <a class="role-hint-link" @click="navigateToQuery('reported-by-me')">我报告的问题（{{ summary.reportedByMeOpen }}）</a>
      </span>
    </div>

    <!-- 双栏内容区 -->
    <div class="dashboard-content">
      <!-- 左栏：分配给我的工单 -->
      <div class="widget-card">
        <div class="widget-header">
          <h2 class="widget-title">分配给我</h2>
          <a-button type="text" size="mini" @click="$router.push('/')">查看全部</a-button>
        </div>
        <div v-if="assignedLoading" class="widget-loading">
          <a-skeleton :animation="true" v-for="i in 5" :key="i" style="margin-bottom:12px">
            <a-skeleton-line :rows="2" :widths="['60%','40%']" />
          </a-skeleton>
        </div>
        <div v-else-if="assignedIssues.length === 0" class="widget-empty">
          <span class="empty-icon">📭</span>
          <span class="empty-text">暂无分配给您的工单</span>
          <span class="empty-hint" v-if="isTester">
            您可以查看 <a class="empty-link" @click="navigateToQuery('testing')">待测试的工单</a>
          </span>
          <span class="empty-hint" v-else-if="summary.reportedByMeOpen > 0">
            您报告的 {{ summary.reportedByMeOpen }} 个工单仍在处理中
          </span>
          <span class="empty-hint" v-else>工作台显示与您相关的工单动态</span>
        </div>
        <div v-else class="issue-list">
          <div
            v-for="issue in assignedIssues"
            :key="issue.id"
            class="issue-item"
            @click="$router.push(`/issues/${issue.id}`)"
          >
            <div class="issue-row-top">
              <span class="issue-key-badge">{{ issue.issueKey }}</span>
              <span class="issue-priority" :class="'priority-' + (issue.priority || 'normal').toLowerCase()"></span>
              <span class="issue-title-text">{{ issue.title }}</span>
            </div>
            <div class="issue-row-bottom">
              <span v-if="issue.dueDate" class="issue-due" :class="{ overdue: isOverdue(issue.dueDate) }">
                {{ formatDueDate(issue.dueDate) }}
              </span>
              <span class="issue-updated">{{ formatRelativeTime(issue.updatedAt) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右栏：最近活动 -->
      <div class="widget-card">
        <div class="widget-header">
          <h2 class="widget-title">最近活动</h2>
          <a-button type="text" size="mini" @click="refreshActivity" :loading="activityLoading">
            刷新
          </a-button>
        </div>
        <div v-if="activityLoading && activities.length === 0" class="widget-loading">
          <a-skeleton :animation="true" v-for="i in 5" :key="i" style="margin-bottom:12px">
            <a-skeleton-line :rows="2" :widths="['70%','50%']" />
          </a-skeleton>
        </div>
        <div v-else-if="activities.length === 0" class="widget-empty">
          <span class="empty-icon">📭</span>
          <span class="empty-text">暂无活动记录</span>
          <span class="empty-hint">开始工作后这里会显示变更历史</span>
        </div>
        <div v-else class="activity-list">
          <div
            v-for="activity in activities"
            :key="activity.id"
            class="activity-item"
            @click="$router.push(`/issues/${activity.issueId}`)"
          >
            <div class="activity-avatar">{{ (activity.userName || 'U').charAt(0) }}</div>
            <div class="activity-content">
              <div class="activity-text">
                <span class="activity-user">{{ activity.userName }}</span>
                {{ ' ' }}
                <span class="activity-action">{{ formatAction(activity) }}</span>
                {{ ' ' }}
                <span class="activity-issue-link">{{ activity.issueKey }}</span>
              </div>
              <div class="activity-meta">
                <span class="activity-issue-title">{{ activity.issueTitle }}</span>
                <span class="activity-time">{{ formatRelativeTime(activity.createdAt) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 即将到期工单 -->
    <div class="widget-card widget-full" v-if="overdueIssues.length > 0 || overdueLoading">
      <div class="widget-header">
        <h2 class="widget-title">即将到期 & 逾期</h2>
        <span class="widget-badge warning">{{ overdueIssues.length }}</span>
      </div>
      <div v-if="overdueLoading" class="widget-loading">
        <a-skeleton :animation="true" v-for="i in 3" :key="i" style="margin-bottom:12px">
          <a-skeleton-line :rows="1" :widths="['80%']" />
        </a-skeleton>
      </div>
      <div v-else class="overdue-table">
        <div
          v-for="issue in overdueIssues"
          :key="issue.id"
          class="overdue-row"
          @click="$router.push(`/issues/${issue.id}`)"
        >
          <span class="issue-key-badge">{{ issue.issueKey }}</span>
          <span class="overdue-title">{{ issue.title }}</span>
          <span class="overdue-due" :class="{ 'is-overdue': isOverdue(issue.dueDate) }">
            {{ formatDueDate(issue.dueDate) }}
          </span>
          <span class="issue-priority" :class="'priority-' + (issue.priority || 'normal').toLowerCase()"></span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { Message } from '@arco-design/web-vue'
import { dashboardApi } from '@/api'
import type { DashboardSummaryVO, DashboardActivityVO } from '@/api/dashboard'
import type { IssueVO } from '@/api/types'
import { fieldLabelMap } from '@/utils/fieldLabels'

const router = useRouter()
const authStore = useAuthStore()

// State
const summaryLoading = ref(true)
const assignedLoading = ref(true)
const overdueLoading = ref(true)
const activityLoading = ref(true)

const summary = ref<DashboardSummaryVO>({
  assignedOpen: 0,
  assignedInProgress: 0,
  completedThisWeek: 0,
  dueSoon: 0,
  overdue: 0,
  reportedByMeOpen: 0,
  testingCount: 0,
  totalIssues: 0,
  activeProjects: 0,
  primaryRoleCode: null
})

const assignedIssues = ref<IssueVO[]>([])
const overdueIssues = ref<IssueVO[]>([])
const activities = ref<DashboardActivityVO[]>([])

// Computed
const userName = computed(() => {
  return authStore.user?.displayName || authStore.user?.username || '用户'
})

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const isTester = computed(() => summary.value.primaryRoleCode === 'tester')

/** 当测试人员没有分配工单，且有待测试工单时，显示角色引导条 */
const showRoleHint = computed(() => {
  return !summaryLoading.value
    && isTester.value
    && summary.value.assignedOpen === 0
    && summary.value.assignedInProgress === 0
    && summary.value.testingCount > 0
})

// Data loading
async function loadSummary() {
  summaryLoading.value = true
  try {
    const res = await dashboardApi.summary()
    if (res.code === 0 && res.data) {
      summary.value = res.data
    }
  } catch { Message.error({ content: '加载统计数据失败', duration: 3000 }) }
  finally { summaryLoading.value = false }
}

async function loadAssigned() {
  assignedLoading.value = true
  try {
    const res = await dashboardApi.assignedToMe(10)
    if (res.code === 0 && res.data) {
      assignedIssues.value = res.data
    }
  } catch { Message.error({ content: '加载工单列表失败', duration: 3000 }) }
  finally { assignedLoading.value = false }
}

async function loadOverdue() {
  overdueLoading.value = true
  try {
    const res = await dashboardApi.overdue(7, 10)
    if (res.code === 0 && res.data) {
      overdueIssues.value = res.data
    }
  } catch { Message.error({ content: '加载到期数据失败', duration: 3000 }) }
  finally { overdueLoading.value = false }
}

async function refreshActivity() {
  activityLoading.value = true
  try {
    const res = await dashboardApi.activity(20)
    if (res.code === 0 && res.data) {
      activities.value = res.data
    }
  } catch { Message.error({ content: '加载活动记录失败', duration: 3000 }) }
  finally { activityLoading.value = false }
}

// Helpers
function formatRelativeTime(dt: string): string {
  if (!dt) return ''
  const d = new Date(dt)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins}分钟前`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}天前`
  return d.toLocaleDateString('zh-CN')
}

function formatDueDate(dt: string): string {
  if (!dt) return ''
  const d = new Date(dt)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const target = new Date(d)
  target.setHours(0, 0, 0, 0)
  const diff = Math.floor((target.getTime() - today.getTime()) / 86400000)

  if (diff < 0) return `逾期 ${Math.abs(diff)} 天`
  if (diff === 0) return '今天到期'
  if (diff === 1) return '明天到期'
  if (diff <= 7) return `${diff} 天后到期`
  return d.toLocaleDateString('zh-CN')
}

function isOverdue(dt: string): boolean {
  if (!dt) return false
  return new Date(dt) < new Date(new Date().toDateString())
}

function formatAction(activity: DashboardActivityVO): string {
  const actionMap: Record<string, string> = {
    'create': '创建了',
    'created': '创建了',
    'update': '更新了',
    'updated': '更新了',
    'status_change': '变更了状态',
    'status_changed': '变更了状态',
    'assign': '分配了',
    'assigned': '分配了',
    'comment': '评论了',
    'commented': '评论了',
    'attach': '添加了附件',
    'attached': '添加了附件',
    'attachment_added': '添加了附件',
    'attachment_removed': '删除了附件',
    'time_logged': '记录了工时',
    'time_removed': '删除了工时',
    'deleted': '删除了',
    'reopened': '重新打开了',
    'resolved': '解决了'
  }
  let text = actionMap[activity.action] || activity.action
  if (activity.fieldName && (activity.action === 'update' || activity.action === 'updated')) {
    text = `更新了 ${fieldLabelMap[activity.fieldName] || activity.fieldName}`
  }
  return text
}

function navigateToQuery(type: string) {
  const params: Record<string, string> = {}
  switch (type) {
    case 'assigned-open':
      params.statusCategory = 'open'
      params.label = '待处理'
      break
    case 'assigned-in-progress':
      params.statusCategory = 'in_progress'
      params.label = '进行中'
      break
    case 'completed-week':
      params.statusCategory = 'done'
      params.label = '本周完成'
      break
    case 'overdue':
      params.overdue = 'true'
      params.label = '已逾期'
      break
    case 'due-soon':
      params.dueSoon = 'true'
      params.label = '即将到期'
      break
    case 'testing':
      params.statusCode = 'testing'
      params.label = '待测试'
      break
    case 'reported-by-me':
      params.reportedByMe = 'true'
      params.label = '我报告的'
      break
  }
  const query = new URLSearchParams(params).toString()
  router.push(`/?${query}`)
}

// Init
onMounted(() => {
  loadSummary()
  loadAssigned()
  loadOverdue()
  refreshActivity()
})
</script>

<style scoped>
.dashboard-page {
  padding: 24px 32px;
  overflow-y: auto;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.dashboard-header {
  margin-bottom: 24px;
}

.dashboard-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 4px;
  letter-spacing: -0.3px;
}

.dashboard-greeting {
  font-size: 13px;
  color: var(--tf-text-tertiary);
}

/* Stats Grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border-light);
  border-radius: var(--tf-radius-lg);
  padding: 16px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, transform 0.15s;
  position: relative;
  overflow: hidden;
}

.stat-card:hover {
  border-color: var(--tf-accent);
  background: var(--tf-bg-elevated);
  transform: translateY(-1px);
}

.stat-card.warning .stat-value {
  color: var(--tf-danger);
}

.stat-card.highlight {
  border-color: var(--tf-accent);
  background: var(--tf-accent-bg);
}

.stat-card.highlight .stat-value {
  color: var(--tf-accent);
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--tf-text-primary);
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin-top: 4px;
}

.stat-icon {
  position: absolute;
  top: 12px;
  right: 14px;
  font-size: 18px;
  opacity: 0.6;
}

/* Role Hint Bar */
.role-hint-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: var(--tf-accent-bg);
  border: 1px solid var(--tf-accent);
  border-radius: var(--tf-radius-md);
  margin-bottom: 16px;
}

.role-hint-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.role-hint-text {
  font-size: 13px;
  color: var(--tf-text-secondary);
}

.role-hint-link {
  color: var(--tf-accent);
  cursor: pointer;
  font-weight: 500;
  text-decoration: none;
}

.role-hint-link:hover {
  text-decoration: underline;
}

/* Dashboard Content - Two columns */
.dashboard-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
  flex: 1;
  min-height: 0;
}

/* Widget Card */
.widget-card {
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border-light);
  border-radius: var(--tf-radius-lg);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.widget-full {
  grid-column: 1 / -1;
}

.widget-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--tf-border-light);
}

.widget-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
}

.widget-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.widget-badge.warning {
  background: rgba(248, 81, 73, 0.12);
  color: var(--tf-danger);
}

.widget-loading {
  padding: 16px;
}

.widget-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 16px;
  gap: 4px;
}

.empty-icon { font-size: 28px; margin-bottom: 4px; }
.empty-text { font-size: 13px; color: var(--tf-text-secondary); }
.empty-hint { font-size: 11px; color: var(--tf-text-tertiary); text-align: center; }

.empty-link {
  color: var(--tf-accent);
  cursor: pointer;
  font-weight: 500;
}

.empty-link:hover {
  text-decoration: underline;
}

/* Issue List */
.issue-list {
  flex: 1;
  overflow-y: auto;
}

.issue-item {
  padding: 10px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--tf-border-light);
  transition: background 0.1s;
}

.issue-item:last-child { border-bottom: none; }
.issue-item:hover { background: var(--tf-bg-hover); }

.issue-row-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.issue-key-badge {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-accent);
  flex-shrink: 0;
}

.issue-priority {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.priority-critical { background: var(--tf-danger); }
.priority-high { background: var(--tf-warning); }
.priority-normal { background: var(--tf-accent); }
.priority-low { background: var(--tf-text-tertiary); }

.issue-title-text {
  font-size: 13px;
  color: var(--tf-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.issue-row-bottom {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 4px;
  padding-left: 0;
}

.issue-due {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.issue-due.overdue {
  color: var(--tf-danger);
  font-weight: 500;
}

.issue-updated {
  font-size: 11px;
  color: var(--tf-text-muted);
}

/* Activity List */
.activity-list {
  flex: 1;
  overflow-y: auto;
}

.activity-item {
  display: flex;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--tf-border-light);
  transition: background 0.1s;
}

.activity-item:last-child { border-bottom: none; }
.activity-item:hover { background: var(--tf-bg-hover); }

.activity-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--tf-accent-bg);
  color: var(--tf-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}

.activity-content {
  flex: 1;
  min-width: 0;
}

.activity-text {
  font-size: 12px;
  color: var(--tf-text-secondary);
  line-height: 1.4;
}

.activity-user {
  font-weight: 500;
  color: var(--tf-text-primary);
}

.activity-action {
  margin: 0 4px;
}

.activity-issue-link {
  color: var(--tf-accent);
  font-weight: 500;
}

.activity-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 2px;
}

.activity-issue-title {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.activity-time {
  font-size: 11px;
  color: var(--tf-text-muted);
  flex-shrink: 0;
}

/* Overdue table */
.overdue-table {
  max-height: 240px;
  overflow-y: auto;
}

.overdue-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--tf-border-light);
  transition: background 0.1s;
}

.overdue-row:last-child { border-bottom: none; }
.overdue-row:hover { background: var(--tf-bg-hover); }

.overdue-title {
  flex: 1;
  font-size: 13px;
  color: var(--tf-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.overdue-due {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  flex-shrink: 0;
}

.overdue-due.is-overdue {
  color: var(--tf-danger);
  font-weight: 500;
}

/* Responsive */
@media (max-width: 1024px) {
  .stats-grid {
    grid-template-columns: repeat(3, 1fr);
  }
  .dashboard-content {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .dashboard-page {
    padding: 16px;
  }
}
</style>
