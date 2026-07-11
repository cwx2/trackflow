<template>
  <div class="issue-detail-page" v-if="issue">
    <DetailTopBar
      :project-name="projectName"
      :issue-key="issue.issueKey"
      :reporter="reporterName"
      :created-ago="timeAgo(issue.createdAt)"
      :updated-ago="timeAgo(issue.updatedAt)"
      @copy="copyIssue"
      @toggle-sidebar="sidebarVisible = !sidebarVisible"
    />

    <div class="page-body">
      <DetailMainContent
        :issue-key="issue.issueKey"
        :issue-type="issue.issueType"
        :title="issue.title"
        :description="issue.description || ''"
        :tags="issueTags"
        :available-tags="projectTags"
        :links="issueLinks"
        :attachments="issueAttachments"
        @update-title="onUpdateTitle"
        @update-desc="onUpdateDesc"
        @remove-tag="onRemoveTag"
        @add-tag="onAddTag"
        @create-tag="onCreateTag"
        @add-link="() => {}"
        @upload="() => {}"
      >
        <template #activity>
          <ActivityStream :items="activityItems" />
          <CommentInput @submit="onAddComment" />
        </template>
      </DetailMainContent>

      <DetailSidebar
        v-show="sidebarVisible"
        :status="currentStatus"
        :transitions="availableTransitions"
        :fields="sidebarFields"
        @transition="onTransition"
        @edit-field="onEditField"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { renderMarkdown } from '@/utils/markdown'
import DetailTopBar from './components/DetailTopBar.vue'
import DetailMainContent from './components/DetailMainContent.vue'
import DetailSidebar from './components/DetailSidebar.vue'
import ActivityStream from './components/ActivityStream.vue'
import CommentInput from './components/CommentInput.vue'
import type { ActivityItem } from './components/ActivityStream.vue'
import type { SidebarField, StatusInfo } from './components/DetailSidebar.vue'
import {
  mockProjects, mockTags, mockStatuses, mockSprints,
  getIssue, getUser, getStatus, getIssueTags, getIssueLinks,
  getIssueComments, getIssueActivities, getIssueAttachments,
  addComment, addIssueTag, removeIssueTag, createTag,
  updateIssueField, transitStatus
} from '@/mock/data'

const route = useRoute()
const sidebarVisible = ref(true)

// 当前 Issue（响应式引用 mock 数据）
const issueId = computed(() => (route.params.id as string) || '1389')
const issue = computed(() => getIssue(issueId.value))

// 项目信息
const projectName = computed(() => {
  const p = mockProjects.find(p => p.id === issue.value?.projectId)
  return p?.name || ''
})

// 报告人
const reporterName = computed(() => getUser(issue.value?.reporterId || null)?.displayName || '未知')

// 标签
const issueTags = computed(() => getIssueTags(issueId.value))
const projectTags = computed(() => mockTags.filter(t => t.projectId === issue.value?.projectId))

// 关联
const issueLinks = computed(() =>
  getIssueLinks(issueId.value).map(l => ({
    ...l,
    typeLabel: l.linkType
  }))
)

// 附件
const issueAttachments = computed(() =>
  getIssueAttachments(issueId.value).map(a => ({
    id: a.id,
    fileName: a.fileName,
    sizeText: formatSize(a.fileSize)
  }))
)

// 状态
const currentStatus = computed<StatusInfo>(() => {
  const s = getStatus(issue.value?.statusId || '1')
  return { id: s?.id || '1', name: s?.name || '未知', color: s?.color || '#666' }
})

// 可用转换
const availableTransitions = computed<StatusInfo[]>(() => {
  return mockStatuses
    .filter(s => s.id !== issue.value?.statusId)
    .map(s => ({ id: s.id, name: s.name, color: s.color }))
})

// 侧边栏字段
const sidebarFields = computed<SidebarField[]>(() => {
  const i = issue.value
  if (!i) return []
  const assignee = getUser(i.assigneeId)
  const sprint = mockSprints.find(s => s.id === i.sprintId)
  return [
    { key: 'priority', label: '优先级', value: i.priority, dot: priorityDot(i.priority) },
    { key: 'issueType', label: '类型', value: i.issueType },
    { key: 'assignee', label: '负责人', value: assignee?.displayName || '未分配' },
    { key: 'sprint', label: '迭代', value: sprint?.name || '无' },
    { key: 'dueDate', label: '截止日期', value: i.dueDate || '无' },
    { key: 'estimatedHours', label: '预估工时', value: i.estimatedHours ? `${i.estimatedHours}h` : '无' },
    { key: '_sep', label: '', value: '', readonly: true },
    { key: 'reporter', label: '报告人', value: reporterName.value, readonly: true },
    { key: 'created', label: '创建时间', value: formatDateTime(i.createdAt), readonly: true },
    { key: 'updated', label: '更新时间', value: formatDateTime(i.updatedAt), readonly: true },
  ]
})

// 活动流
const activityItems = computed<ActivityItem[]>(() => {
  const items: ActivityItem[] = []
  const comments = getIssueComments(issueId.value)
  const activities = getIssueActivities(issueId.value)

  for (const c of comments) {
    const isHtml = c.content.trim().startsWith('<')
    items.push({
      id: 'c_' + c.id, type: 'comment', user: c.userName,
      html: isHtml ? c.content : renderMarkdown(c.content),
      timeAgo: timeAgo(c.createdAt), ts: new Date(c.createdAt).getTime(),
    })
  }
  for (const a of activities) {
    if (a.action === 'commented') continue
    items.push({
      id: 'a_' + a.id, type: 'change', user: a.userName,
      action: a.action, field: a.fieldName || undefined, from: a.oldValue || undefined, to: a.newValue || undefined,
      timeAgo: timeAgo(a.createdAt), ts: new Date(a.createdAt).getTime(),
    })
  }
  return items
})

// ========== Actions ==========
function copyIssue() {
  navigator.clipboard.writeText(`${issue.value?.issueKey} ${issue.value?.title}`)
  Message.success('已复制')
}

function onUpdateTitle(val: string) {
  updateIssueField(issueId.value, 'title', val)
}

function onUpdateDesc(val: string) {
  updateIssueField(issueId.value, 'description', val)
}

function onRemoveTag(tagId: string) {
  removeIssueTag(issueId.value, tagId)
}

function onAddTag(tag: { id: string; name: string; color: string }) {
  addIssueTag(issueId.value, tag.id)
}

function onCreateTag(name: string) {
  const tag = createTag(issue.value?.projectId || '1', name)
  addIssueTag(issueId.value, tag.id)
}

function onAddComment(content: string) {
  addComment(issueId.value, '2', content) // '2' = VanceChang (当前用户模拟)
}

function onTransition(target: StatusInfo) {
  transitStatus(issueId.value, target.id)
  Message.success(`状态已变更为 ${target.name}`)
}

function onEditField(key: string) {
  Message.info(`编辑字段: ${key}（功能开发中）`)
}

// ========== Utils ==========
function timeAgo(dt: string | number) {
  const ts = typeof dt === 'number' ? dt : new Date(dt).getTime()
  const diff = Date.now() - ts
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins} 分钟前`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return `${hrs} 小时前`
  return `${Math.floor(hrs / 24)} 天前`
}

function formatDateTime(dt: string) {
  return new Date(dt).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function formatSize(bytes: number) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

function priorityDot(p: string) {
  const m: Record<string, string> = { Critical: '#f44336', High: '#ff9800', Normal: '#4caf50', Low: '#9e9e9e' }
  return m[p] || '#666'
}
</script>

<style scoped>
.issue-detail-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--tf-bg-body);
  color: var(--tf-text-primary);
  overflow: hidden;
}

.page-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}
</style>
