<template>
  <a-modal
    :visible="visible"
    :title="modalTitle"
    :ok-text="okText"
    cancel-text="取消"
    :ok-button-props="{ disabled: !canSubmit }"
    :width="480"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <div class="transition-comment-modal">
      <div v-if="targetStatus" class="transition-info">
        <span class="transition-label">目标状态：</span>
        <span class="status-badge" :style="{ backgroundColor: targetStatus.color || '#6b7280' }">
          {{ targetStatus.name }}
        </span>
      </div>

      <!-- ===== Required Custom Fields Section: 渲染目标状态所需的必填字段
           根据 requiredFieldIds 从 customFieldDefs 中筛选出需要用户填写的字段
           支持类型：user（项目成员选择）、list（选项下拉）、string/text（文本输入） ===== -->
      <div v-if="requiredFields.length > 0" class="required-fields-section">
        <div v-for="field in requiredFields" :key="field.id" class="required-field-item">
          <label class="field-label">
            {{ field.name }}
            <span class="required-mark">*</span>
          </label>

          <!-- User type field: member selector -->
          <a-select
            v-if="field.fieldFormat === 'user'"
            v-model="fieldValues[field.id]"
            :placeholder="`请选择${field.name}`"
            allow-clear
            :allow-search="true"
            :filter-option="filterMemberOption"
            :class="{ 'field-error': fieldErrors[field.id] }"
          >
            <a-option v-for="member in members" :key="member.userId" :value="member.userId">
              {{ member.displayName || member.username }}
            </a-option>
          </a-select>

          <!-- List type field: options selector -->
          <a-select
            v-else-if="field.fieldFormat === 'list'"
            v-model="fieldValues[field.id]"
            :placeholder="`请选择${field.name}`"
            allow-clear
            :allow-search="true"
            :class="{ 'field-error': fieldErrors[field.id] }"
          >
            <a-option
              v-for="opt in getFieldOptions(field)"
              :key="opt.id"
              :value="opt.id"
            >
              {{ opt.value }}
            </a-option>
          </a-select>

          <!-- String/text/other type field: text input -->
          <a-input
            v-else
            v-model="fieldValues[field.id]"
            :placeholder="`请输入${field.name}`"
            :class="{ 'field-error': fieldErrors[field.id] }"
          />

          <p v-if="fieldErrors[field.id]" class="field-error-msg">此字段为必填</p>
        </div>
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
/**
 * TransitionCommentModal — 状态变更确认弹窗
 *
 * 职责：
 * - 展示目标状态信息
 * - 渲染目标状态所需的必填自定义字段输入控件（require_field 规则）
 * - 提供指派人选择（可选）
 * - 提供备注/理由输入（requireComment 时必填）
 * - 统一校验后一次性提交所有信息
 *
 * 对外接口：
 * - Props：targetStatus、requiredFieldIds、customFieldDefs、members 等
 * - Emits：confirm（携带 comment + assigneeId + customFieldValues）、cancel
 */
import { ref, computed, watch, nextTick, reactive } from 'vue'
import type { StatusInfo } from './DetailSidebar.vue'
import type { ProjectMemberVO, CustomFieldDefinitionVO, CustomFieldOptionVO } from '@/api/types'

const props = defineProps<{
  visible: boolean
  targetStatus: StatusInfo | null
  /** 是否强制要求填写评论 */
  requireComment: boolean
  /** 是否显示负责人选择器 */
  showAssignee?: boolean
  /** 可分配的项目成员列表 */
  members?: ProjectMemberVO[]
  /** 目标状态所需的必填字段 ID 列表 */
  requiredFieldIds?: string[]
  /** 项目的自定义字段定义列表（用于匹配 requiredFieldIds 并渲染输入控件） */
  customFieldDefs?: CustomFieldDefinitionVO[]
}>()

const emit = defineEmits<{
  confirm: [comment: string, assigneeId: string | undefined, assigneeExplicit: boolean, customFieldValues?: Record<string, string>]
  cancel: []
}>()

const comment = ref('')
const selectedAssigneeId = ref<string | undefined>(undefined)
const textareaRef = ref()

// ===== Required fields state =====
const fieldValues = reactive<Record<string, string | undefined>>({})
const fieldErrors = reactive<Record<string, boolean>>({})

/** 从 customFieldDefs 中筛选出 requiredFieldIds 对应的字段定义 */
const requiredFields = computed<CustomFieldDefinitionVO[]>(() => {
  const ids = props.requiredFieldIds
  const defs = props.customFieldDefs
  if (!ids || ids.length === 0 || !defs || defs.length === 0) return []
  return ids
    .map(id => defs.find(d => d.id === id))
    .filter((d): d is CustomFieldDefinitionVO => d !== undefined)
})

/** 获取 list 类型字段的可选项（排除已归档的） */
function getFieldOptions(field: CustomFieldDefinitionVO): CustomFieldOptionVO[] {
  if (!field.options) return []
  return field.options.filter(o => !o.isArchived)
}

const modalTitle = computed(() => {
  if (props.requireComment) {
    return '状态变更 — 请填写理由'
  }
  return '状态变更确认'
})

const okText = computed(() => {
  if (props.targetStatus?.name) {
    return `变更为「${props.targetStatus.name}」`
  }
  return '确认变更'
})

const placeholder = computed(() => {
  if (props.requireComment) {
    return '请说明退回/变更的原因，例如：在 Firefox 中仍可复现该问题...'
  }
  return '可选：添加备注说明本次状态变更的原因'
})

/** 是否可以提交（评论必填校验 + 必填字段非空校验） */
const canSubmit = computed(() => {
  if (props.requireComment && !comment.value.trim()) return false
  // 必填字段都必须有值
  for (const field of requiredFields.value) {
    if (!fieldValues[field.id] || (typeof fieldValues[field.id] === 'string' && !(fieldValues[field.id] as string).trim())) {
      return false
    }
  }
  return true
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
    // Reset field values and errors
    for (const key of Object.keys(fieldValues)) {
      delete fieldValues[key]
    }
    for (const key of Object.keys(fieldErrors)) {
      delete fieldErrors[key]
    }
    nextTick(() => {
      textareaRef.value?.focus()
    })
  }
})

function handleOk() {
  // Validate required fields inline
  let hasError = false
  for (const field of requiredFields.value) {
    const val = fieldValues[field.id]
    if (!val || (typeof val === 'string' && !val.trim())) {
      fieldErrors[field.id] = true
      hasError = true
    } else {
      fieldErrors[field.id] = false
    }
  }
  if (hasError) return

  // Build custom field values map (only non-empty entries)
  const customFieldValues: Record<string, string> | undefined =
    requiredFields.value.length > 0
      ? Object.fromEntries(
          requiredFields.value
            .filter(f => fieldValues[f.id])
            .map(f => [f.id, fieldValues[f.id] as string])
        )
      : undefined

  const assigneeExplicit = selectedAssigneeId.value !== undefined
  emit('confirm', comment.value.trim(), selectedAssigneeId.value, assigneeExplicit, customFieldValues)
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
  color: var(--tf-text-on-accent);
}

/* ===== Required fields section ===== */
.required-fields-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
  background: var(--tf-bg-surface);
  border-radius: 6px;
  border: 1px solid var(--tf-border-default);
}

.required-field-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.required-mark {
  color: var(--tf-text-error, #f85149);
  margin-left: 2px;
}

.field-error :deep(.arco-input-wrapper),
.field-error :deep(.arco-select-view-single) {
  border-color: var(--tf-text-error, #f85149);
}

.field-error-msg {
  margin: 0;
  font-size: 12px;
  color: var(--tf-text-error, #f85149);
}

/* ===== Assignee section ===== */
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

/* ===== Comment section ===== */
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
