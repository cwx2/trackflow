<template>
  <a-drawer
    :visible="visible"
    :width="480"
    :footer="false"
    unmount-on-close
    class="issue-preview-drawer"
    @cancel="close"
  >
    <template #title>
      <div class="preview-title-bar">
        <span class="preview-issue-key" @click="goToDetail">{{ detail?.issueKey }}</span>
        <div class="preview-actions">
          <a-tooltip content="在新页面打开详情">
            <a-button type="text" size="mini" @click="goToDetail">
              <template #icon><icon-share-external /></template>
            </a-button>
          </a-tooltip>
        </div>
      </div>
    </template>

    <!-- 加载状态 -->
    <div v-if="loading" class="preview-loading">
      <a-spin :size="24" />
    </div>

    <!-- 加载失败 -->
    <div v-else-if="loadError" class="preview-error">
      <div class="preview-error-icon">⚠️</div>
      <p class="preview-error-msg">{{ loadError }}</p>
      <a-button size="small" @click="loadDetail">重试</a-button>
    </div>

    <!-- 内容 -->
    <div v-else-if="detail" class="preview-content">
      <!-- 标题 -->
      <h2 class="preview-issue-title">{{ detail.title }}</h2>

      <!-- 状态与优先级 -->
      <div class="preview-meta-row">
        <span
          class="preview-status-badge"
          :style="{ backgroundColor: statusColor, color: '#fff' }"
        >{{ localizeStatusName(detail.status?.name) }}</span>
        <span class="preview-priority">
          {{ priorityIcon(detail.priority) }} {{ detail.priority }}
        </span>
        <span class="preview-type">{{ typeLabel(detail.issueType) }}</span>
      </div>

      <!-- 关键字段 -->
      <div class="preview-fields">
        <div class="preview-field" v-if="detail.assigneeName">
          <span class="field-label">负责人</span>
          <span class="field-value">{{ detail.assigneeName }}</span>
        </div>
        <div class="preview-field" v-if="detail.reporterName">
          <span class="field-label">报告者</span>
          <span class="field-value">{{ detail.reporterName }}</span>
        </div>
        <div class="preview-field" v-if="detail.sprintName">
          <span class="field-label">迭代</span>
          <span class="field-value">{{ detail.sprintName }}</span>
        </div>
        <div class="preview-field" v-if="detail.dueDate">
          <span class="field-label">截止日期</span>
          <span class="field-value">{{ detail.dueDate }}</span>
        </div>
        <div class="preview-field" v-if="detail.estimatedHours">
          <span class="field-label">预估工时</span>
          <span class="field-value">
            <TimeProgressIndicator
              v-if="detail.spentHours != null || detail.estimatedHours"
              :spent="detail.spentHours || 0"
              :estimated="detail.estimatedHours"
            />
            {{ detail.estimatedHours }}h
          </span>
        </div>
        <div class="preview-field" v-if="detail.spentHours">
          <span class="field-label">已花费</span>
          <span class="field-value">{{ detail.spentHours }}h</span>
        </div>
        <!-- 自定义字段 -->
        <template v-if="detail.customFieldDetails && detail.customFieldDetails.length > 0">
          <div class="preview-field" v-for="cf in detail.customFieldDetails" :key="cf.customFieldId">
            <span class="field-label">{{ cf.fieldName }}</span>
            <span class="field-value">{{ cf.displayValue || cf.value || '—' }}</span>
          </div>
        </template>
      </div>

      <!-- 标签 -->
      <div v-if="detail.tags && detail.tags.length > 0" class="preview-tags">
        <span
          v-for="tag in detail.tags"
          :key="tag.id"
          class="preview-tag"
          :style="{ backgroundColor: tag.color + '22', color: tag.color, borderColor: tag.color + '44' }"
        >{{ tag.name }}</span>
      </div>

      <!-- 描述 -->
      <div class="preview-section">
        <h4 class="section-title">描述</h4>
        <div
          v-if="detail.description"
          class="preview-description"
          v-html="renderedDescription"
        ></div>
        <p v-else class="preview-empty-text">暂无描述</p>
      </div>

      <!-- 子任务进度 -->
      <div v-if="detail.childProgress && detail.childProgress.total > 0" class="preview-section">
        <h4 class="section-title">子任务</h4>
        <div class="preview-child-progress">
          <div class="progress-bar-track">
            <div
              class="progress-bar-fill"
              :style="{ width: childProgressPercent + '%' }"
            ></div>
          </div>
          <span class="progress-text">{{ detail.childProgress.closed }}/{{ detail.childProgress.total }}</span>
        </div>
      </div>

      <!-- 最近评论 -->
      <div v-if="comments.length > 0" class="preview-section">
        <h4 class="section-title">最近评论 ({{ comments.length }})</h4>
        <div class="preview-comments">
          <div
            v-for="comment in displayedComments"
            :key="comment.id"
            class="preview-comment"
          >
            <div class="comment-header">
              <span class="comment-author">{{ comment.userName }}</span>
              <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
            </div>
            <div class="comment-body" v-html="renderCommentContent(comment.content)"></div>
          </div>
          <a-button
            v-if="comments.length > 3"
            type="text"
            size="small"
            class="view-all-comments"
            @click="goToDetail"
          >查看全部 {{ comments.length }} 条评论</a-button>
        </div>
      </div>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { issueApi } from '@/api'
import type { IssueDetailVO, IssueCommentVO } from '@/api/types'
import TimeProgressIndicator from '@/views/issue/components/TimeProgressIndicator.vue'
import { localizeStatusName, localizeIssueType } from '@/utils/fieldLabels'
import { renderMarkdown } from '@/utils/markdown'
import { IconShareExternal } from '@arco-design/web-vue/es/icon'

const props = defineProps<{
  visible: boolean
  issueId: string | null
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  'go-detail': [issueId: string]
}>()

const router = useRouter()

const loading = ref(false)
const loadError = ref<string | null>(null)
const detail = ref<IssueDetailVO | null>(null)
const comments = ref<IssueCommentVO[]>([])

const statusColor = computed(() => detail.value?.status?.color || 'var(--color-fill-4)')

const renderedDescription = computed(() => {
  if (!detail.value?.description) return ''
  return renderMarkdown(detail.value.description)
})

const childProgressPercent = computed(() => {
  if (!detail.value?.childProgress) return 0
  const { total, closed } = detail.value.childProgress
  return total > 0 ? Math.round((closed / total) * 100) : 0
})

const displayedComments = computed(() => comments.value.slice(0, 3))

watch(() => props.issueId, (newId) => {
  if (newId && props.visible) {
    loadDetail()
  }
}, { immediate: true })

watch(() => props.visible, (v) => {
  if (v && props.issueId) {
    loadDetail()
  }
  if (!v) {
    // Reset state when closing
    detail.value = null
    comments.value = []
    loadError.value = null
  }
})

async function loadDetail() {
  if (!props.issueId) return
  loading.value = true
  loadError.value = null

  try {
    const [detailRes, commentsRes] = await Promise.all([
      issueApi.getById(props.issueId),
      issueApi.listComments(props.issueId)
    ])
    detail.value = detailRes.data
    comments.value = commentsRes.data || []
  } catch (e: any) {
    loadError.value = e.response?.data?.message || '加载工单详情失败'
  } finally {
    loading.value = false
  }
}

function close() {
  emit('update:visible', false)
}

function goToDetail() {
  if (props.issueId) {
    emit('update:visible', false)
    router.push({ name: 'IssueDetail', params: { id: props.issueId } })
  }
}

function priorityIcon(priority: string): string {
  const map: Record<string, string> = { Critical: '🔴', High: '🟠', Normal: '🔵', Low: '⚪' }
  return map[priority] || '🔵'
}

function typeLabel(type: string): string {
  return localizeIssueType(type)
}

function formatTime(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  return d.toLocaleDateString('zh-CN')
}

function renderCommentContent(content: string): string {
  if (!content) return ''
  return renderMarkdown(content)
}
</script>

<style scoped>
.preview-title-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.preview-issue-key {
  font-size: 13px;
  font-weight: 600;
  color: rgb(var(--primary-6));
  cursor: pointer;
  transition: opacity 0.15s;
}
.preview-issue-key:hover {
  opacity: 0.8;
  text-decoration: underline;
}

.preview-actions {
  display: flex;
  gap: 4px;
}

/* Loading */
.preview-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 0;
}

/* Error */
.preview-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 16px;
  text-align: center;
}
.preview-error-icon {
  font-size: 32px;
  margin-bottom: 12px;
}
.preview-error-msg {
  font-size: 13px;
  color: var(--color-text-3);
  margin-bottom: 12px;
}

/* Content */
.preview-content {
  padding: 4px 0;
}

.preview-issue-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-1);
  margin: 0 0 12px;
  line-height: 1.4;
}

/* Meta row */
.preview-meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.preview-status-badge {
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 3px;
  line-height: 1.4;
}

.preview-priority {
  font-size: 12px;
  color: var(--color-text-2);
}

.preview-type {
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  padding: 2px 6px;
  border-radius: 3px;
}

/* Fields */
.preview-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
  padding: 12px;
  background: var(--color-fill-1);
  border-radius: 6px;
  margin-bottom: 16px;
}

.preview-field {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.field-label {
  font-size: 11px;
  color: var(--color-text-3);
  font-weight: 500;
}

.field-value {
  font-size: 13px;
  color: var(--color-text-1);
  display: flex;
  align-items: center;
  gap: 4px;
}

/* Tags */
.preview-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 16px;
}

.preview-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 3px;
  border: 1px solid;
  font-weight: 500;
}

/* Sections */
.preview-section {
  margin-bottom: 16px;
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-2);
  margin: 0 0 8px;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.preview-description {
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-1);
  max-height: 200px;
  overflow-y: auto;
  padding-right: 4px;
}

.preview-description :deep(p) {
  margin: 0 0 8px;
}
.preview-description :deep(ul),
.preview-description :deep(ol) {
  padding-left: 20px;
  margin: 0 0 8px;
}
.preview-description :deep(code) {
  font-size: 12px;
  background: var(--color-fill-2);
  padding: 1px 4px;
  border-radius: 3px;
}
.preview-description :deep(pre) {
  background: var(--color-fill-2);
  padding: 8px 12px;
  border-radius: 4px;
  overflow-x: auto;
  margin: 0 0 8px;
}

.preview-empty-text {
  font-size: 13px;
  color: var(--color-text-4);
  font-style: italic;
}

/* Child progress */
.preview-child-progress {
  display: flex;
  align-items: center;
  gap: 8px;
}

.progress-bar-track {
  flex: 1;
  height: 6px;
  background: var(--color-fill-3);
  border-radius: 3px;
  overflow: hidden;
}

.progress-bar-fill {
  height: 100%;
  background: rgb(var(--success-6));
  border-radius: 3px;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 12px;
  color: var(--color-text-3);
  white-space: nowrap;
}

/* Comments */
.preview-comments {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.preview-comment {
  padding: 8px 10px;
  background: var(--color-fill-1);
  border-radius: 6px;
  border: 1px solid var(--color-border);
}

.comment-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.comment-author {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-1);
}

.comment-time {
  font-size: 11px;
  color: var(--color-text-4);
}

.comment-body {
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-2);
  max-height: 60px;
  overflow: hidden;
}

.comment-body :deep(p) {
  margin: 0;
}

.view-all-comments {
  align-self: flex-start;
  font-size: 12px;
  color: rgb(var(--primary-6));
}
</style>
