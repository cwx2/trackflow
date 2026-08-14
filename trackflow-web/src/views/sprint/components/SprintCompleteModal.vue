<template>
  <a-modal
    v-model:visible="localVisible"
    :title="`完成迭代：${sprintName}`"
    :width="640"
    :ok-loading="completing"
    :ok-text="okButtonText"
    @ok="confirmComplete"
    @cancel="localVisible = false"
  >
    <!-- Sprint 中无工单 -->
    <div v-if="preview && preview.totalIssues === 0" class="complete-no-issues">
      <div class="complete-icon warning-state">
        <icon-exclamation-circle />
      </div>
      <p class="complete-desc">此迭代中没有任何工单，是否仍要完成？</p>
    </div>

    <!-- 所有工单已完成 -->
    <div v-else-if="preview && preview.openIssues.length === 0" class="complete-no-issues">
      <div class="complete-icon">
        <icon-check-circle />
      </div>
      <p class="complete-desc">
        该迭代中 <strong>{{ preview.totalIssues }}</strong> 个工单全部已完成
      </p>
    </div>

    <!-- 有未完成工单 -->
    <div v-else-if="preview" class="complete-with-issues">
      <div class="complete-warning">
        <icon-exclamation-circle class="warning-icon" />
        <span>该迭代中共 <strong>{{ preview.totalIssues }}</strong> 个工单，已完成 <strong>{{ preview.completedIssues }}</strong> 个，仍有 <strong>{{ preview.openIssues.length }}</strong> 个未完成</span>
      </div>

      <!-- 未完成工单列表 -->
      <div class="open-issues-list">
        <div v-for="issue in preview.openIssues" :key="issue.id" class="open-issue-item">
          <a class="issue-key" :href="`/issue/${issue.id}`" target="_blank" @click.stop>{{ issue.issueKey }}</a>
          <span class="issue-title">{{ issue.title }}</span>
          <IssuePriorityBadge
            v-if="issue.priority"
            :priority="issue.priority"
            :color="issue.priorityColor"
            mode="dot"
            size="small"
          />
          <IssueStatusTag
            :name="issue.statusName"
            :color="issue.statusColor || DEFAULT_STATUS_COLOR"
          />
          <span class="issue-due-date" :class="{ overdue: isOverdue(issue.dueDate) }" v-if="issue.dueDate">
            <icon-calendar class="due-icon" />{{ formatDueDate(issue.dueDate) }}
          </span>
          <span class="issue-assignee" v-if="issue.assigneeName">{{ issue.assigneeName }}</span>
        </div>
      </div>

      <!-- 处理方式选择 -->
      <div class="move-option-section">
        <p class="move-option-label">请选择未完成工单的处理方式：</p>
        <a-radio-group v-model="moveOption" direction="vertical">
          <a-radio value="backlog">
            <span class="radio-label">移回 Backlog</span>
            <span class="radio-desc">清空工单的迭代归属，回到待规划状态</span>
          </a-radio>
          <a-radio value="next_sprint" :disabled="preview.targetSprints.length === 0">
            <span class="radio-label">移入其他迭代</span>
            <span class="radio-desc" v-if="preview.targetSprints.length > 0">
              将未完成工单转移到指定的迭代中
            </span>
            <span class="radio-desc disabled" v-else>
              当前项目没有其他可用迭代
            </span>
          </a-radio>
        </a-radio-group>

        <div v-if="moveOption === 'next_sprint' && preview.targetSprints.length > 0" class="target-sprint-select">
          <a-select v-model="targetSprintId" placeholder="选择目标迭代" style="width: 100%">
            <a-option v-for="target in preview.targetSprints" :key="target.id" :value="target.id">
              {{ target.name }}
              <IssueStatusTag
                :name="target.status === 'active' ? '进行中' : '计划中'"
                :color="getSprintStatusColor(target.status)"
                size="small"
                :show-dot="false"
                variant="plain"
              />
            </a-option>
          </a-select>
        </div>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-else class="complete-loading">
      <a-spin :size="24" />
      <p style="margin-top: 12px; color: var(--color-text-3);">正在获取工单信息…</p>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { handleApiError } from '@/utils/errorHandler'
import { IconExclamationCircle, IconCheckCircle, IconCalendar } from '@arco-design/web-vue/es/icon'
import { sprintApi } from '@/api'
import { IssueStatusTag, IssuePriorityBadge } from '@/components/base'
import { DEFAULT_STATUS_COLOR, getSprintStatusColor } from '@/utils/uiColors'
import type { SprintVO, CompletionPreviewVO } from '@/api/types'

const props = defineProps<{
  visible: boolean
  sprint: SprintVO | null
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  completed: []
}>()

const localVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const completing = ref(false)
const preview = ref<CompletionPreviewVO | null>(null)
const moveOption = ref('backlog')
const targetSprintId = ref('')

const sprintName = computed(() => props.sprint?.name || '')

const okButtonText = computed(() => {
  if (!preview.value) return '确认完成'
  if (preview.value.totalIssues === 0) return '仍然完成'
  if (preview.value.openIssues.length > 0) return '处理并完成'
  return '确认完成'
})

/** 判断日期是否已过期 */
function isOverdue(dateStr?: string): boolean {
  if (!dateStr) return false
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return new Date(dateStr) < today
}

/** 格式化截止日期为简短显示 */
function formatDueDate(dateStr?: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const month = date.getMonth() + 1
  const day = date.getDate()
  return `${month}/${day}`
}

watch(() => props.visible, async (val) => {
  if (val && props.sprint) {
    preview.value = null
    moveOption.value = 'backlog'
    targetSprintId.value = ''
    try {
      const res = await sprintApi.completionPreview(props.sprint.id)
      preview.value = res.data
      if (res.data.openIssues.length > 0 && res.data.targetSprints.length > 0) {
        targetSprintId.value = res.data.targetSprints[0].id
      }
    } catch (e) {
      handleApiError(e, '获取预览信息失败')
      localVisible.value = false
    }
  }
})

async function confirmComplete() {
  if (!preview.value) return
  const hasOpenIssues = preview.value.openIssues.length > 0
  if (hasOpenIssues && moveOption.value === 'next_sprint' && !targetSprintId.value) {
    Message.warning('请选择目标迭代')
    return
  }

  completing.value = true
  try {
    const body = hasOpenIssues
      ? { moveOption: moveOption.value, targetSprintId: moveOption.value === 'next_sprint' ? targetSprintId.value : undefined }
      : undefined

    const res = await sprintApi.complete(props.sprint!.id, body)
    const result = res.data

    const spName = result.sprint?.name ?? sprintName.value
    const totalIssueCount = result.totalIssues ?? 0
    const completedCount = result.completedIssues ?? 0
    let toastMessage = `迭代「${spName}」已完成`
    if (totalIssueCount > 0) {
      toastMessage += `：完成 ${completedCount}/${totalIssueCount} 工单`
    }
    if (result.unresolvedIssues > 0) {
      if (result.moveOption === 'backlog') {
        toastMessage += `，${result.unresolvedIssues} 个工单已移回 Backlog`
      } else if (result.moveOption === 'next_sprint' && result.targetSprintName) {
        toastMessage += `，${result.unresolvedIssues} 个工单已移入「${result.targetSprintName}」`
      }
    }
    Message.success(toastMessage)
    localVisible.value = false
    emit('completed')
  } catch (e) {
    handleApiError(e, '操作失败')
  } finally {
    completing.value = false
  }
}
</script>

<style scoped>
.complete-no-issues {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 0;
  text-align: center;
}
.complete-icon { font-size: 36px; margin-bottom: 12px; color: var(--tf-success); }
.complete-icon.warning-state { color: var(--tf-warning); }
.complete-desc { font-size: 14px; color: var(--color-text-2); }

.complete-with-issues { display: flex; flex-direction: column; gap: 16px; }

.complete-warning {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--tf-warning-bg);
  border-radius: 6px;
  font-size: 13px;
  color: var(--color-text-1);
}
.warning-icon { font-size: 16px; color: var(--tf-warning); flex-shrink: 0; }

.open-issues-list {
  max-height: 240px;
  overflow-y: auto;
  border: 1px solid var(--color-border);
  border-radius: 6px;
}
.open-issue-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 12px;
  border-bottom: 1px solid var(--color-border);
}
.open-issue-item:last-child { border-bottom: none; }
.issue-key {
  color: var(--color-text-3);
  font-family: monospace;
  font-size: 11px;
  flex-shrink: 0;
  text-decoration: none;
  transition: color 0.15s;
}
.issue-key:hover { color: var(--tf-accent, rgb(var(--primary-6))); }
.issue-title { color: var(--color-text-1); flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0; }
.issue-due-date {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--color-text-3);
  flex-shrink: 0;
  white-space: nowrap;
}
.issue-due-date .due-icon { font-size: 12px; }
.issue-due-date.overdue { color: var(--tf-error, rgb(var(--danger-6))); font-weight: 500; }
.issue-assignee { color: var(--color-text-3); font-size: 11px; flex-shrink: 0; max-width: 64px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.move-option-section { display: flex; flex-direction: column; gap: 12px; }
.move-option-label { font-size: 13px; font-weight: 500; color: var(--color-text-1); margin: 0; }
.radio-label { font-size: 13px; font-weight: 500; color: var(--color-text-1); }
.radio-desc { display: block; font-size: 12px; color: var(--color-text-3); margin-top: 2px; }
.radio-desc.disabled { color: var(--color-text-4); font-style: italic; }
.target-sprint-select { margin-top: 8px; margin-left: 24px; }

.complete-loading { display: flex; flex-direction: column; align-items: center; padding: 32px 0; }
</style>
