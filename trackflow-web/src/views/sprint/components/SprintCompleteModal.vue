<template>
  <a-modal
    v-model:visible="localVisible"
    :title="`完成迭代：${sprintName}`"
    :width="560"
    :ok-loading="completing"
    ok-text="确认完成"
    @ok="confirmComplete"
    @cancel="localVisible = false"
  >
    <!-- 无未完成工单 -->
    <div v-if="preview && preview.openIssues.length === 0" class="complete-no-issues">
      <div class="complete-icon">✅</div>
      <p class="complete-desc">该迭代中所有工单已完成，确认关闭迭代？</p>
    </div>

    <!-- 有未完成工单 -->
    <div v-else-if="preview" class="complete-with-issues">
      <div class="complete-warning">
        <span class="warning-icon">⚠️</span>
        <span>该迭代中仍有 <strong>{{ preview.openIssues.length }}</strong> 个未完成工单</span>
      </div>

      <!-- 未完成工单列表 -->
      <div class="open-issues-list">
        <div v-for="issue in preview.openIssues" :key="issue.id" class="open-issue-item">
          <span class="issue-key">{{ issue.issueKey }}</span>
          <span class="issue-title">{{ issue.title }}</span>
          <IssueStatusTag
            :name="localizeStatusName(issue.statusName)"
            :color="issue.statusColor || DEFAULT_STATUS_COLOR"
          />
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
import { sprintApi } from '@/api'
import { localizeStatusName } from '@/utils/fieldLabels'
import { IssueStatusTag } from '@/components/base'
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
    } catch (e: any) {
      Message.error(e.response?.data?.message || '获取预览信息失败')
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

    const spName = result.sprint?.name ?? result.name ?? sprintName.value
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
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
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
.complete-icon { font-size: 36px; margin-bottom: 12px; }
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
.warning-icon { font-size: 16px; }

.open-issues-list {
  max-height: 200px;
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
.issue-key { color: var(--color-text-3); font-family: monospace; font-size: 11px; flex-shrink: 0; }
.issue-title { color: var(--color-text-1); flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.issue-assignee { color: var(--color-text-3); font-size: 11px; flex-shrink: 0; }

.move-option-section { display: flex; flex-direction: column; gap: 12px; }
.move-option-label { font-size: 13px; font-weight: 500; color: var(--color-text-1); margin: 0; }
.radio-label { font-size: 13px; font-weight: 500; color: var(--color-text-1); }
.radio-desc { display: block; font-size: 12px; color: var(--color-text-3); margin-top: 2px; }
.radio-desc.disabled { color: var(--color-text-4); font-style: italic; }
.target-sprint-select { margin-top: 8px; margin-left: 24px; }

.complete-loading { display: flex; flex-direction: column; align-items: center; padding: 32px 0; }
</style>
