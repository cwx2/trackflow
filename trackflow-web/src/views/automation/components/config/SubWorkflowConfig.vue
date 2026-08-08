<template>
  <div class="sub-workflow-config">
    <div class="config-item">
      <label class="config-label">选择子工作流</label>
      <a-select
        v-model="local.workflowId"
        size="small"
        placeholder="选择要调用的工作流"
        allow-search
        :loading="loadingWorkflows"
        class="config-select"
        @focus="loadWorkflows"
      >
        <a-option
          v-for="w in workflows"
          :key="w.id"
          :value="w.id"
          :label="w.name"
        >
          {{ w.name }}
        </a-option>
      </a-select>
    </div>

    <div v-if="selectedWorkflow" class="selected-info">
      <div class="info-item">
        <span class="info-label">工作流 ID：</span>
        <code class="info-value">{{ selectedWorkflow.id }}</code>
      </div>
      <div v-if="selectedWorkflow.description" class="info-item">
        <span class="info-label">说明：</span>
        <span class="info-value desc">{{ selectedWorkflow.description }}</span>
      </div>
    </div>

    <div class="config-tip">
      💡 子工作流的 Start 节点参数通过 <strong>输入参数</strong> 端口传入，
      End 节点输出通过 <strong>输出结果</strong> 端口传出
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { automationApi, type WorkflowVO } from '@/api'

const props = defineProps<{ data: Record<string, any> }>()
const emit = defineEmits(['update:data'])

const local = reactive({
  workflowId: props.data?.config?.workflowId ?? '',
})

const workflows = ref<WorkflowVO[]>([])
const loadingWorkflows = ref(false)

const selectedWorkflow = computed(
  () => workflows.value.find(w => w.id === local.workflowId)
)

async function loadWorkflows() {
  if (workflows.value.length > 0) return
  loadingWorkflows.value = true
  try {
    const res = await automationApi.list()
    if (res.code === 0) workflows.value = res.data || []
  } finally {
    loadingWorkflows.value = false
  }
}

watch(local, (val) => {
  emit('update:data', {
    ...props.data,
    config: { ...props.data?.config, ...val },
  })
}, { deep: true })
</script>

<style scoped>
.sub-workflow-config { padding: 12px; display: flex; flex-direction: column; gap: 14px; }

.config-item { display: flex; flex-direction: column; gap: 6px; }
.config-label { font-size: 12px; color: var(--tf-text-secondary); font-weight: 500; }
.config-select { width: 100%; }

.selected-info {
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.info-item { display: flex; gap: 6px; align-items: flex-start; font-size: 12px; }
.info-label { color: var(--tf-text-tertiary); flex-shrink: 0; }
.info-value { color: var(--tf-text-primary); font-family: monospace; }
.info-value.desc { font-family: inherit; color: var(--tf-text-secondary); }

.config-tip {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-elevated);
  border-radius: 6px;
  padding: 8px 10px;
  line-height: 1.5;
}
</style>
