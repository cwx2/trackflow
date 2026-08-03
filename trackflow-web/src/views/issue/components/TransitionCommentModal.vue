<template>
  <a-modal
    :visible="visible"
    :title="modalTitle"
    :ok-text="okText"
    cancel-text="取消"
    :ok-button-props="{ disabled: requireComment && !comment.trim() }"
    :width="480"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <div class="transition-comment-modal">
      <div class="transition-info">
        <span class="transition-label">目标状态：</span>
        <span class="status-badge" :style="{ backgroundColor: targetStatus?.color || '#6b7280' }">
          {{ targetStatus?.name }}
        </span>
      </div>

      <!-- Assignee selector (optional override) -->
      <div v-if="showAssignee" class="assignee-section">
        <label class="assignee-label">指派给（可选）</label>
        <a-select
          v-model="selectedAssigneeId"
          placeholder="自动分配（上一任开发人员）"
          allow-clear
          :allow-search="true"
          :filter-option="filterMemberOption"
        >
          <a-option v-for="member in members" :key="member.userId" :value="member.userId">
            {{ member.displayName || member.username }}
          </a-option>
        </a-select>
        <p class="assignee-hint">
          留空时系统将自动分配给上一任负责人
        </p>
      </div>

      <div class="comment-section">
        <label class="comment-label">
          {{ requireComment ? '理由（必填）' : '备注（选填）' }}
        </label>
        <a-textarea
          ref="textareaRef"
          v-model="comment"
          :placeholder="placeholder"
          :max-length="2000"
          show-word-limit
          :auto-size="{ minRows: 3, maxRows: 8 }"
        />
        <p v-if="requireComment" class="comment-hint">
          此状态转换要求填写理由，请说明变更原因。
        </p>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import type { StatusInfo } from './DetailSidebar.vue'
import type { ProjectMemberVO } from '@/api/types'

const props = defineProps<{
  visible: boolean
  targetStatus: StatusInfo | null
  /** 是否强制要求填写评论 */
  requireComment: boolean
  /** 是否显示负责人选择器 */
  showAssignee?: boolean
  /** 可分配的项目成员列表 */
  members?: ProjectMemberVO[]
}>()

const emit = defineEmits<{
  confirm: [comment: string, assigneeId: string | undefined, assigneeExplicit: boolean]
  cancel: []
}>()

const comment = ref('')
const selectedAssigneeId = ref<string | undefined>(undefined)
const textareaRef = ref()

const modalTitle = computed(() => {
  if (props.requireComment) {
    return '状态变更 — 请填写理由'
  }
  return '状态变更确认'
})

const okText = computed(() => {
  return '确认变更'
})

const placeholder = computed(() => {
  if (props.requireComment) {
    return '请说明退回/变更的原因，例如：在 Firefox 中仍可复现该问题...'
  }
  return '可选：添加备注说明本次状态变更的原因'
})

/** Filter member options for search */
function filterMemberOption(inputValue: string, option: any) {
  const member = props.members?.find(m => m.userId === option.value)
  if (!member) return false
  const keyword = inputValue.toLowerCase()
  return (member.displayName || '').toLowerCase().includes(keyword) ||
    (member.username || '').toLowerCase().includes(keyword)
}

// Reset state when modal opens
watch(() => props.visible, (val) => {
  if (val) {
    comment.value = ''
    selectedAssigneeId.value = undefined
    nextTick(() => {
      textareaRef.value?.focus()
    })
  }
})

function handleOk() {
  const assigneeExplicit = selectedAssigneeId.value !== undefined
  emit('confirm', comment.value.trim(), selectedAssigneeId.value, assigneeExplicit)
}

function handleCancel() {
  emit('cancel')
}
</script>

<style scoped>
.transition-comment-modal {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.transition-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.transition-label {
  color: var(--tf-text-secondary);
  font-size: 13px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 3px;
  font-size: 12px;
  font-weight: 500;
  color: #fff;
}

.assignee-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.assignee-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.assignee-hint {
  margin: 0;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.comment-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.comment-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.comment-hint {
  margin: 0;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}
</style>
