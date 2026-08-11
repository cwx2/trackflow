<template>
  <div class="workspace-tab">
    <!-- 分配给我的工单 -->
    <div class="workspace-section">
      <div class="section-header">
        <h3 class="section-title">
          <icon-user class="section-icon" />
          分配给我的工单
        </h3>
        <span class="section-count" v-if="assignedTotal > 0">{{ assignedTotal }} 条</span>
      </div>

      <a-spin :loading="assignedLoading" style="width: 100%">
        <div v-if="assignedIssues.length > 0" class="issue-list">
          <div
            v-for="issue in assignedIssues"
            :key="issue.id"
            class="issue-row"
            @click="navigateToIssue(issue.issueKey)"
          >
            <router-link
              :to="`/issues/${issue.issueKey}`"
              class="issue-key"
              @click.stop
            >{{ issue.issueKey }}</router-link>
            <span class="issue-title">{{ issue.title }}</span>
            <span class="issue-project">{{ extractProjectKey(issue.issueKey) }}</span>
            <span
              class="issue-priority"
              :class="`priority-${issue.priority?.toLowerCase()}`"
            >{{ priorityLabel(issue.priority) }}</span>
            <span
              class="issue-status"
              :style="{ color: issue.statusColor || 'var(--tf-text-tertiary)' }"
            >{{ issue.statusName }}</span>
            <span class="issue-time">{{ formatTime(issue.updatedAt) }}</span>
          </div>
        </div>

        <EmptyState v-else-if="!assignedLoading" icon="check-circle" title="没有分配给你的待办工单" description="当有工单分配给你时，会显示在这里" :compact="true" />
      </a-spin>

      <div v-if="assignedTotal > assignedIssues.length" class="view-all">
        <router-link to="/issues?assignedToMe=true&hideResolved=true" class="view-all-link">
          查看全部 →
        </router-link>
      </div>
    </div>

    <!-- 我报告的工单 -->
    <div class="workspace-section">
      <div class="section-header">
        <h3 class="section-title">
          <icon-edit class="section-icon" />
          我报告的工单
        </h3>
        <span class="section-count" v-if="reportedTotal > 0">{{ reportedTotal }} 条</span>
      </div>

      <a-spin :loading="reportedLoading" style="width: 100%">
        <div v-if="reportedIssues.length > 0" class="issue-list">
          <div
            v-for="issue in reportedIssues"
            :key="issue.id"
            class="issue-row"
            @click="navigateToIssue(issue.issueKey)"
          >
            <router-link
              :to="`/issues/${issue.issueKey}`"
              class="issue-key"
              @click.stop
            >{{ issue.issueKey }}</router-link>
            <span class="issue-title">{{ issue.title }}</span>
            <span class="issue-project">{{ extractProjectKey(issue.issueKey) }}</span>
            <span
              class="issue-priority"
              :class="`priority-${issue.priority?.toLowerCase()}`"
            >{{ priorityLabel(issue.priority) }}</span>
            <span
              class="issue-status"
              :style="{ color: issue.statusColor || 'var(--tf-text-tertiary)' }"
            >{{ issue.statusName }}</span>
            <span class="issue-time">{{ formatTime(issue.createdAt) }}</span>
          </div>
        </div>

        <EmptyState v-else-if="!reportedLoading" icon="file" title="你还没有报告过工单" description="创建工单后，会在这里显示" :compact="true" />
      </a-spin>

      <div v-if="reportedTotal > reportedIssues.length" class="view-all">
        <router-link to="/issues?reportedByMe=true" class="view-all-link">
          查看全部 →
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { issueApi } from '@/api/issue'
import { EmptyState } from '@/components/base'
import type { IssueVO } from '@/api/types'

const router = useRouter()

const assignedIssues = ref<IssueVO[]>([])
const assignedTotal = ref(0)
const assignedLoading = ref(false)

const reportedIssues = ref<IssueVO[]>([])
const reportedTotal = ref(0)
const reportedLoading = ref(false)

const PRIORITY_LABELS: Record<string, string> = {
  critical: '紧急',
  major: '重要',
  normal: '普通',
  minor: '次要',
  trivial: '轻微'
}

function priorityLabel(priority?: string): string {
  if (!priority) return ''
  return PRIORITY_LABELS[priority.toLowerCase()] || priority
}

function extractProjectKey(issueKey: string): string {
  if (!issueKey) return ''
  const idx = issueKey.lastIndexOf('-')
  return idx > 0 ? issueKey.substring(0, idx) : issueKey
}

function formatTime(dateStr?: string): string {
  if (!dateStr) return ''
  try {
    const date = new Date(dateStr)
    const now = new Date()
    const diffMs = now.getTime() - date.getTime()
    const diffMin = Math.floor(diffMs / 60000)
    const diffHour = Math.floor(diffMs / 3600000)
    const diffDay = Math.floor(diffMs / 86400000)

    if (diffMin < 1) return '刚刚'
    if (diffMin < 60) return `${diffMin} 分钟前`
    if (diffHour < 24) return `${diffHour} 小时前`
    if (diffDay < 7) return `${diffDay} 天前`
    return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
  } catch {
    return dateStr
  }
}

function navigateToIssue(issueKey: string) {
  router.push(`/issues/${issueKey}`)
}

async function loadAssignedToMe() {
  assignedLoading.value = true
  try {
    const res = await issueApi.list({
      assignedToMe: 'true',
      hideResolved: 'true',
      pageSize: 20,
      page: 1,
      sort: '-updated_at'
    })
    if (res.code === 0 && res.data) {
      assignedIssues.value = res.data.list || []
      assignedTotal.value = res.data.pagination?.total || res.data.list?.length || 0
    }
  } catch {
    // 静默处理
  } finally {
    assignedLoading.value = false
  }
}

async function loadReportedByMe() {
  reportedLoading.value = true
  try {
    const res = await issueApi.list({
      reportedByMe: 'true',
      pageSize: 20,
      page: 1,
      sort: '-created_at'
    })
    if (res.code === 0 && res.data) {
      reportedIssues.value = res.data.list || []
      reportedTotal.value = res.data.pagination?.total || res.data.list?.length || 0
    }
  } catch {
    // 静默处理
  } finally {
    reportedLoading.value = false
  }
}

onMounted(() => {
  loadAssignedToMe()
  loadReportedByMe()
})
</script>

<style scoped>
.workspace-tab {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

/* ===== Section ===== */
.workspace-section {
  display: flex;
  flex-direction: column;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--tf-border-light);
}

.section-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}

.section-icon {
  font-size: 14px;
  color: var(--tf-text-tertiary);
}

.section-count {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-hover);
  padding: 2px 8px;
  border-radius: 10px;
}

/* ===== Issue List ===== */
.issue-list {
  display: flex;
  flex-direction: column;
}

.issue-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 150ms;
}

.issue-row:hover {
  background: var(--tf-bg-hover);
}

.issue-key {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-accent);
  white-space: nowrap;
  flex-shrink: 0;
  text-decoration: none;
  min-width: 70px;
}

.issue-key:hover {
  text-decoration: underline;
}

.issue-title {
  font-size: 13px;
  color: var(--tf-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.issue-project {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  white-space: nowrap;
  flex-shrink: 0;
  padding: 1px 5px;
  border-radius: 3px;
  background: var(--tf-bg-hover);
}

.issue-priority {
  font-size: 11px;
  white-space: nowrap;
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--tf-bg-hover);
}

.priority-critical {
  color: var(--tf-danger);
}

.priority-major {
  color: var(--tf-warning);
}

.priority-normal {
  color: var(--tf-text-secondary);
}

.priority-minor {
  color: var(--tf-text-tertiary);
}

.priority-trivial {
  color: var(--tf-text-quaternary);
}

.issue-status {
  font-size: 11px;
  white-space: nowrap;
  flex-shrink: 0;
  min-width: 50px;
  text-align: right;
}

.issue-time {
  font-size: 11px;
  color: var(--tf-text-quaternary);
  white-space: nowrap;
  flex-shrink: 0;
  min-width: 60px;
  text-align: right;
}

/* ===== Empty State ===== */
/* ===== View All Link ===== */
.view-all {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--tf-border-lightest);
  text-align: center;
}

.view-all-link {
  font-size: 12px;
  color: var(--tf-accent);
  text-decoration: none;
  transition: opacity 150ms;
}

.view-all-link:hover {
  opacity: 0.8;
  text-decoration: underline;
}
</style>
