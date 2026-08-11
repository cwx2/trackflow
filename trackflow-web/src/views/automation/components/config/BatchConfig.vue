<template>
  <div class="node-config batch-config">
    <a-alert type="info" :show-icon="true">
      批处理会把列表中的每一项分别传给一个已发布子工作流。每项完成后再继续下一项，刷新或重启后会从检查点继续。
    </a-alert>

    <a-form :model="local" layout="vertical" size="small">
      <a-form-item label="处理子工作流" required>
        <a-select
          v-model="local.workflowId"
          placeholder="选择一个已发布的工作流"
          allow-search
          :loading="loadingWorkflows"
          @focus="loadWorkflows"
        >
          <a-option v-for="workflow in publishedWorkflows" :key="workflow.id" :value="workflow.id">
            {{ workflow.name }}
          </a-option>
        </a-select>
        <template #extra>子工作流应通过开始节点接收单项数据，并以结束节点返回结果。</template>
      </a-form-item>

      <a-form-item label="子流程输入字段">
        <a-input v-model="local.itemInputKey" placeholder="item" />
        <template #extra>当前项目会以此字段传入子工作流；同时会传入从 0 开始的 index。</template>
      </a-form-item>

      <a-form-item label="单次最多处理">
        <a-input-number v-model="local.maxItems" :min="1" :max="1000" style="width: 100%" />
        <template #extra>超过上限的项目不会丢失，会出现在“跳过项”输出中。</template>
      </a-form-item>

      <a-form-item label="单项失败策略">
        <a-radio-group v-model="local.onItemFailure" direction="vertical">
          <a-radio value="continue">继续处理其余项目</a-radio>
          <a-radio value="stop">立即停止，剩余项目记为跳过</a-radio>
        </a-radio-group>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { automationApi, type WorkflowVO } from '@/api'

const props = defineProps<{ data: Record<string, any> }>()
const emit = defineEmits<{ (event: 'update:data', value: Record<string, any>): void }>()

const local = reactive({
  workflowId: props.data?.config?.workflowId ?? '',
  itemInputKey: props.data?.config?.itemInputKey ?? 'item',
  maxItems: props.data?.config?.maxItems ?? 100,
  onItemFailure: props.data?.config?.onItemFailure ?? 'continue',
})
const workflows = ref<WorkflowVO[]>([])
const loadingWorkflows = ref(false)
const publishedWorkflows = computed(() => workflows.value.filter(workflow => workflow.status === 'published'))

async function loadWorkflows() {
  if (workflows.value.length > 0) return
  loadingWorkflows.value = true
  try {
    const response = await automationApi.list()
    if (response.code === 0) workflows.value = response.data || []
  } finally {
    loadingWorkflows.value = false
  }
}

watch(local, value => {
  emit('update:data', { ...props.data, config: { ...props.data?.config, ...value } })
}, { deep: true })

watch(() => props.data, value => {
  local.workflowId = value?.config?.workflowId ?? ''
  local.itemInputKey = value?.config?.itemInputKey ?? 'item'
  local.maxItems = value?.config?.maxItems ?? 100
  local.onItemFailure = value?.config?.onItemFailure ?? 'continue'
}, { deep: true })
</script>

<style scoped>
.batch-config { padding: 12px; display: flex; flex-direction: column; gap: 14px; }
.batch-config :deep(.arco-alert) { line-height: 1.55; }
</style>
