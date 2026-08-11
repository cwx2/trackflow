<template>
  <div class="variable-input-wrapper">
    <a-textarea
      v-if="type === 'textarea'"
      ref="inputRef"
      :model-value="modelValue ?? ''"
      :placeholder="placeholder"
      :auto-size="autoSize"
      @update:model-value="modelValue = $event"
    />
    <a-input
      v-else
      ref="inputRef"
      :model-value="modelValue ?? ''"
      :placeholder="placeholder"
      @update:model-value="modelValue = $event"
    />
    <a-dropdown trigger="click" @select="insertVariable">
      <a-button type="text" size="mini" class="insert-var-btn">
        <template #icon><icon-code /></template>
        插入变量
      </a-button>
      <template #content>
        <div class="var-dropdown-header">Issue 变量</div>
        <a-doption
          v-for="v in issueVariables"
          :key="v.value"
          :value="v.value"
        >
          <span class="var-item">
            <span class="var-name">{{ v.value }}</span>
            <span class="var-desc">{{ v.label }}</span>
          </span>
        </a-doption>
        <div class="var-dropdown-header">当前用户变量</div>
        <a-doption
          v-for="v in userVariables"
          :key="v.value"
          :value="v.value"
        >
          <span class="var-item">
            <span class="var-name">{{ v.value }}</span>
            <span class="var-desc">{{ v.label }}</span>
          </span>
        </a-doption>
      </template>
    </a-dropdown>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'

const modelValue = defineModel<string | undefined>()

defineProps<{
  placeholder?: string
  type?: 'input' | 'textarea'
  autoSize?: { minRows?: number; maxRows?: number }
}>()

const inputRef = ref<any>(null)

const issueVariables = [
  { value: '{issue.id}', label: '工单编号' },
  { value: '{issue.summary}', label: '工单标题' },
  { value: '{issue.type}', label: '工单类型' },
  { value: '{issue.priority}', label: '优先级' },
  { value: '{issue.status}', label: '当前状态' },
  { value: '{issue.assignee}', label: '负责人' },
  { value: '{issue.reporter}', label: '报告人' },
  { value: '{issue.project}', label: '项目名称' },
  { value: '{issue.url}', label: '工单链接' },
  { value: '{issue.dueDate}', label: '截止日期' },
]

const userVariables = [
  { value: '{currentUser.name}', label: '当前操作用户名' },
  { value: '{currentUser.email}', label: '当前操作用户邮箱' },
]

function insertVariable(varName: string | number | Record<string, any> | undefined) {
  if (typeof varName !== 'string') return

  const currentValue = getInputElement()?.value ?? ''
  const el = getInputElement()
  let start = currentValue.length
  let end = currentValue.length

  if (el) {
    start = el.selectionStart ?? currentValue.length
    end = el.selectionEnd ?? currentValue.length
  }

  const newVal = currentValue.slice(0, start) + varName + currentValue.slice(end)
  modelValue.value = newVal

  // Restore cursor position after the inserted variable
  nextTick(() => {
    const nativeEl = getInputElement()
    if (nativeEl) {
      const cursorPos = start + varName.length
      nativeEl.focus()
      nativeEl.setSelectionRange(cursorPos, cursorPos)
    }
  })
}

function getInputElement(): HTMLInputElement | HTMLTextAreaElement | null {
  if (!inputRef.value) return null
  // Arco textarea exposes textareaRef, input exposes inputRef
  const arcoComp = inputRef.value
  if (arcoComp.$el) {
    // For textarea: look for the textarea element
    const textarea = arcoComp.$el.querySelector('textarea')
    if (textarea) return textarea
    // For input: look for the input element
    const input = arcoComp.$el.querySelector('input')
    if (input) return input
  }
  return null
}
</script>

<style scoped>
.variable-input-wrapper {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
}

.insert-var-btn {
  align-self: flex-start;
  font-size: 12px;
  color: var(--color-text-3);
  padding: 0 4px;
  height: 22px;
}

.insert-var-btn:hover {
  color: var(--color-primary-6);
}

.var-dropdown-header {
  padding: 4px 12px 2px;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-3);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.var-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.var-name {
  font-family: monospace;
  font-size: 12px;
  color: var(--color-primary-6);
  white-space: nowrap;
}

.var-desc {
  font-size: 12px;
  color: var(--color-text-3);
  white-space: nowrap;
}
</style>
