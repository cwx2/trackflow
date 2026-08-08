<template>
  <a-modal
    v-model:visible="localVisible"
    :title="`删除迭代：${sprintName}`"
    :width="520"
    :ok-loading="deleting"
    ok-text="确认删除"
    :ok-button-props="{ status: 'danger' }"
    @ok="confirmDelete"
    @cancel="localVisible = false"
  >
    <!-- 无关联工单 -->
    <div v-if="preview && preview.totalIssues === 0" class="delete-no-issues">
      <div class="delete-warning-banner">
        <span class="warning-icon">⚠️</span>
        <span class="warning-text">此操作不可撤销</span>
      </div>
      <p class="delete-desc">
        确定删除迭代 <strong>{{ preview.sprintName }}</strong>
        <template v-if="preview.dateRange"> ({{ preview.dateRange }})</template>？
      </p>
      <p class="delete-hint">该迭代中没有工单，删除后不会影响任何工单。</p>
    </div>

    <!-- 有关联工单 -->
    <div v-else-if="preview" class="delete-with-issues">
      <div class="delete-warning-banner danger">
        <span class="warning-icon">🚨</span>
        <span class="warning-text">此操作不可撤销</span>
      </div>
      <p class="delete-desc">
        确定删除迭代 <strong>{{ preview.sprintName }}</strong>
        <template v-if="preview.dateRange"> ({{ preview.dateRange }})</template>？
      </p>
      <div class="delete-impact-info">
        <span class="impact-icon">📋</span>
        <span>该迭代包含 <strong>{{ preview.totalIssues }}</strong> 个工单，删除后这些工单的迭代归属将被清空。</span>
      </div>

      <div class="delete-move-section">
        <p class="move-option-label">请选择工单处理方式：</p>
        <a-radio-group v-model="deleteMoveOption" direction="vertical">
          <a-radio value="backlog">
            <span class="radio-label">移回 Backlog</span>
            <span class="radio-desc">清空工单的迭代归属，回到待规划状态</span>
          </a-radio>
          <a-radio value="next_sprint" :disabled="preview.targetSprints.length === 0">
            <span class="radio-label">移入其他迭代</span>
            <span class="radio-desc" v-if="preview.targetSprints.length > 0">
              将工单转移到指定的迭代中
            </span>
            <span class="radio-desc disabled" v-else>
              当前项目没有其他可用迭代
            </span>
          </a-radio>
        </a-radio-group>

        <div v-if="deleteMoveOption === 'next_sprint' && preview.targetSprints.length > 0" class="target-sprint-select">
          <a-select v-model="deleteTargetSprintId" placeholder="选择目标迭代" style="width: 100%">
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
      <p style="margin-top: 12px; color: var(--color-text-3);">正在获取迭代信息…</p>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { sprintApi } from '@/api'
import { IssueStatusTag } from '@/components/base'
import { getSprintStatusColor } from '@/utils/uiColors'
import type { SprintVO, DeletionPreviewVO } from '@/api/types'

const props = defineProps<{
  visible: boolean
  sprint: SprintVO | null
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  deleted: []
}>()

const localVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const deleting = ref(false)
const preview = ref<DeletionPreviewVO | null>(null)
const deleteMoveOption = ref('backlog')
const deleteTargetSprintId = ref('')

const sprintName = computed(() => props.sprint?.name || '')

watch(() => props.visible, async (val) => {
  if (val && props.sprint) {
    preview.value = null
    deleteMoveOption.value = 'backlog'
    deleteTargetSprintId.value = ''
    try {
      const res = await sprintApi.deletionPreview(props.sprint.id)
      preview.value = res.data
      if (res.data.totalIssues > 0 && res.data.targetSprints.length > 0) {
        deleteTargetSprintId.value = res.data.targetSprints[0].id
      }
    } catch (e: any) {
      Message.error(e.response?.data?.message || '获取预览信息失败')
      localVisible.value = false
    }
  }
})

async function confirmDelete() {
  if (!preview.value) return
  const hasIssues = preview.value.totalIssues > 0
  if (hasIssues && deleteMoveOption.value === 'next_sprint' && !deleteTargetSprintId.value) {
    Message.warning('请选择目标迭代')
    return
  }

  deleting.value = true
  try {
    const body = hasIssues
      ? { moveOption: deleteMoveOption.value, targetSprintId: deleteMoveOption.value === 'next_sprint' ? deleteTargetSprintId.value : undefined }
      : undefined

    await sprintApi.delete(props.sprint!.id, body)
    Message.success('迭代已删除')
    localVisible.value = false
    emit('deleted')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
  } finally {
    deleting.value = false
  }
}
</script>

<style scoped>
.delete-no-issues, .delete-with-issues { display: flex; flex-direction: column; gap: 16px; }

.delete-warning-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--tf-warning-bg);
  border: 1px solid rgba(var(--warning-6), 0.2);
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  color: rgb(var(--warning-6));
}
.delete-warning-banner.danger {
  background: var(--tf-danger-bg);
  border-color: rgba(var(--danger-6), 0.2);
  color: rgb(var(--danger-6));
}
.warning-icon { font-size: 16px; }

.delete-desc { font-size: 14px; color: var(--color-text-1); margin: 0; line-height: 1.6; }
.delete-hint { font-size: 12px; color: var(--color-text-3); margin: 0; }

.delete-impact-info {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  background: var(--color-fill-1);
  border-radius: 6px;
  font-size: 13px;
  color: var(--color-text-1);
  line-height: 1.5;
}
.impact-icon { font-size: 14px; flex-shrink: 0; margin-top: 2px; }

.delete-move-section { display: flex; flex-direction: column; gap: 12px; }
.move-option-label { font-size: 13px; font-weight: 500; color: var(--color-text-1); margin: 0; }
.radio-label { font-size: 13px; font-weight: 500; color: var(--color-text-1); }
.radio-desc { display: block; font-size: 12px; color: var(--color-text-3); margin-top: 2px; }
.radio-desc.disabled { color: var(--color-text-4); font-style: italic; }
.target-sprint-select { margin-top: 8px; margin-left: 24px; }

.complete-loading { display: flex; flex-direction: column; align-items: center; padding: 32px 0; }
</style>
