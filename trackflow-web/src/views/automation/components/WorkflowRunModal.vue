<template>
  <a-modal
    :visible="visible"
    :title="`试运行「${workflowName || '工作流'}」`"
    ok-text="开始试运行"
    :ok-loading="loading"
    @update:visible="emit('update:visible', $event)"
    @ok="emit('run')"
  >
    <a-alert type="info" :show-icon="true" class="run-input-guide">
      <template #title>{{ guideTitle }}</template>
      {{ guideDescription }}
    </a-alert>
    <div v-if="requirements.length" class="run-input-fields">
      <span class="run-input-fields-label">{{ requirements.every(field => !field.required) ? '可选输入' : '需要提供的输入' }}</span>
      <div class="run-input-field-tags">
        <a-tag v-for="field in requirements" :key="field.path" :color="field.required ? 'red' : 'arcoblue'">
          {{ field.path }}{{ field.required ? '（必填）' : '（可选）' }}
        </a-tag>
      </div>
      <p v-for="field in requirements" :key="`${field.path}-description`" class="run-input-field-description">
        <code>{{ field.path }}</code>：{{ field.description || '来自开始节点的触发数据' }}
      </p>
    </div>
    <p class="run-input-hint">输入仅用于本次试运行，不会保存到工作流。</p>
    <a-textarea
      :model-value="inputText"
      :auto-size="{ minRows: 7, maxRows: 14 }"
      :placeholder="placeholder"
      @update:model-value="emit('update:inputText', $event)"
    />
  </a-modal>
</template>

<script setup lang="ts">
export type WorkflowRunInputRequirement = {
  path: string
  required: boolean
  valueType: string
  description: string
}

defineProps<{
  visible: boolean
  workflowName: string
  loading: boolean
  requirements: WorkflowRunInputRequirement[]
  guideTitle: string
  guideDescription: string
  inputText: string
  placeholder: string
}>()

const emit = defineEmits<{
  'update:visible': [visible: boolean]
  'update:inputText': [value: string]
  run: []
}>()
</script>

<style scoped>
.run-input-guide { margin: 0 0 14px; }
.run-input-fields {
  margin: 0 0 12px;
  padding: 12px;
  border: 1px solid var(--tf-border);
  border-radius: 8px;
  background: var(--tf-bg-elevated);
}
.run-input-fields-label { color: var(--tf-text-primary); font-size: 13px; font-weight: 600; }
.run-input-field-tags { display: flex; flex-wrap: wrap; gap: 6px; margin: 8px 0; }
.run-input-field-description { margin: 3px 0; color: var(--tf-text-secondary); font-size: 12px; }
.run-input-hint { margin: 0 0 8px; color: var(--tf-text-tertiary); font-size: 12px; }
</style>
