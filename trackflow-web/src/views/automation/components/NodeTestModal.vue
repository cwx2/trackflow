<template>
  <a-modal
    :visible="visible"
    :title="`试运行节点：${nodeName}`"
    ok-text="运行节点"
    :ok-loading="loading"
    @update:visible="emit('update:visible', $event)"
    @ok="emit('run')"
  >
    <a-alert v-if="hasSideEffects" type="warning" class="node-test-warning">
      此节点会执行真实写操作、外部请求或脚本。确认后才会实际运行；测试输入不会保存到工作流。
    </a-alert>
    <a-checkbox v-if="hasSideEffects" :model-value="confirmSideEffects" class="node-test-confirm"
      @update:model-value="emit('update:confirmSideEffects', $event === true)">
      我确认允许本次节点试运行产生真实副作用
    </a-checkbox>
    <a-alert v-else-if="isSimulation" type="info" :show-icon="true" class="run-input-guide">
      <template #title>此节点将进行安全预演</template>
      审批、循环和子工作流依赖完整编排上下文。这里会检查配置和输入是否可用，不会真正创建审批、循环或启动子流程。
    </a-alert>
    <a-alert v-else type="info" :show-icon="true" class="run-input-guide">
      <template #title>{{ requiredMockFields.length ? '需要为上游引用提供模拟数据' : (inputFields.length ? '可直接运行，无需填写输入' : '此节点无需额外输入，可直接运行') }}</template>
      {{ requiredMockFields.length
        ? '单节点调试不会执行上游节点。请在下方 JSON 中为标记“需模拟”的字段提供本次测试值。'
        : inputFields.length
        ? '系统会使用节点当前配置。只有想临时替换某个输入时，才需要填写下方的覆盖值。'
        : '本次试运行将使用节点当前配置；结果不会修改工作流。' }}
    </a-alert>
    <div v-if="inputFields.length" class="run-input-fields">
      <span class="run-input-fields-label">可临时覆盖的输入（可选）</span>
      <div class="run-input-field-tags">
        <a-tag v-for="field in inputFields" :key="field.name" color="arcoblue">
          {{ field.label }}{{ field.requiresMock ? '（需模拟）' : (field.required ? '（必填）' : '（可选）') }}
        </a-tag>
      </div>
      <p v-for="field in inputFields" :key="`${field.name}-description`" class="run-input-field-description">
        <code>{{ field.name }}</code>：{{ field.source ? `来自 ${field.source}；单节点调试时需提供模拟值` : (field.description || `${field.valueType} 类型输入`) }}
      </p>
    </div>
    <div class="node-test-json-heading">
      <span>临时覆盖值（高级，可选）</span>
      <a-button v-if="inputFields.length" type="text" size="mini" @click="emit('fillExample')">填入示例</a-button>
    </div>
    <p class="run-input-hint">保持 <code>{}</code> 即使用当前配置；填写内容仅作用于本次运行，不会保存到工作流。</p>
    <a-textarea
      :model-value="inputText"
      :auto-size="{ minRows: 6, maxRows: 12 }"
      :placeholder="inputExample"
      @update:model-value="emit('update:inputText', $event)"
    />
  </a-modal>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export type NodeTestInputField = {
  name: string
  label: string
  valueType: string
  description?: string
  required: boolean
  requiresMock?: boolean
  source?: string
}

const emit = defineEmits<{
  'update:visible': [visible: boolean]
  'update:inputText': [value: string]
  'update:confirmSideEffects': [value: boolean]
  fillExample: []
  run: []
}>()

const props = defineProps<{
  visible: boolean
  nodeName: string
  loading: boolean
  hasSideEffects: boolean
  confirmSideEffects: boolean
  isSimulation: boolean
  inputFields: NodeTestInputField[]
  inputText: string
  inputExample: string
}>()

const requiredMockFields = computed(() => props.inputFields.filter(field => field.requiresMock))
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
.node-test-warning { margin-bottom: 12px; }
.node-test-confirm { display: flex; margin: 0 0 12px; }
.node-test-json-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
  color: var(--tf-text-primary);
  font-size: 13px;
  font-weight: 600;
}
</style>
