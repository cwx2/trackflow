<template>
  <div class="issue-transition-config">
    <a-form layout="vertical" size="small">
      <a-form-item label="工单 ID">
        <div v-if="isReference('issueId')" class="reference-value">
          <code>{{ referenceLabel('issueId') }}</code>
          <a-button size="mini" type="text" @click="clearInput('issueId')">改为固定值</a-button>
        </div>
        <a-input-number
          v-else
          :model-value="getInputLiteral('issueId')"
          placeholder="输入工单 ID 或从上游节点连线传入"
          style="width: 100%"
          @change="(val: any) => setInputLiteral('issueId', val)"
        />
        <template #extra>通常来自上游"查找工单"节点的输出</template>
      </a-form-item>

      <a-form-item label="目标状态">
        <a-select
          :model-value="getInputLiteral('statusId')"
          placeholder="选择目标状态"
          :loading="statusesLoading"
          allow-clear
          @change="(val: any) => setInputLiteral('statusId', val)"
        >
          <a-option v-for="s in statuses" :key="s.id" :value="Number(s.id)">
            <span class="status-dot" :style="{ backgroundColor: s.color }"></span>
            {{ s.name }}
          </a-option>
        </a-select>
        <template #extra>若 issueId 来自上游变量，此处展示所有状态供选择</template>
      </a-form-item>

      <a-form-item label="流转说明">
        <a-textarea
          :model-value="String(getInputLiteral('comment') ?? '')"
          placeholder="某些状态转换需要填写理由，建议说明原因"
          :auto-size="{ minRows: 2, maxRows: 5 }"
          @input="(val: any) => setInputLiteral('comment', val || undefined)"
        />
      </a-form-item>

      <a-form-item label="工单版本（乐观锁）">
        <a-input-number
          :model-value="getInputLiteral('version')"
          placeholder="可选，用于并发安全"
          style="width: 100%"
          @change="(val: any) => setInputLiteral('version', val || undefined)"
        />
        <template #extra>传入工单当前版本号可防止并发覆盖</template>
      </a-form-item>

      <a-alert type="info" style="margin-top: 8px">
        <template #icon><span>ℹ️</span></template>
        <div class="alert-content">
          状态转换需满足项目工作流规则，执行身份必须有 <code>issue:change_status</code> 权限。
        </div>
      </a-alert>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { issueApi } from '@/api'
import type { IssueStatusVO } from '@/api/types'

const props = defineProps<{ data: Record<string, any> }>()
const emit = defineEmits<{ (e: 'update:data', value: Record<string, any>): void }>()

const local = ref<Record<string, any>>(JSON.parse(JSON.stringify(props.data || {})))

// Data sources
const statuses = ref<IssueStatusVO[]>([])
const statusesLoading = ref(false)

// Input helpers
function findInput(name: string) {
  return (local.value.inputs || []).find((item: any) => item.name === name)
}

function getInputLiteral(name: string): any {
  const item = findInput(name)
  return item?.value?.type === 'literal' ? item.value.value : undefined
}

function isReference(name: string): boolean {
  const item = findInput(name)
  return item?.value?.type === 'ref'
}

function referenceLabel(name: string): string {
  const item = findInput(name)
  if (item?.value?.type === 'ref') {
    return `${item.value.nodeId}.${item.value.outputName}`
  }
  return ''
}

function setInputLiteral(name: string, value: unknown) {
  const item = findInput(name)
  if (item) {
    item.value = (value === undefined || value === null || value === '')
      ? null
      : { type: 'literal', value }
  }
  commit()
}

function clearInput(name: string) {
  const item = findInput(name)
  if (item) item.value = null
  commit()
}

function commit() {
  emit('update:data', JSON.parse(JSON.stringify(local.value)))
}

// Load all statuses
async function loadStatuses() {
  statusesLoading.value = true
  try {
    const res = await issueApi.listStatuses()
    if (res.code === 0) {
      statuses.value = res.data || []
    }
  } catch { /* ignore */ }
  finally { statusesLoading.value = false }
}

// Sync props
watch(() => props.data, (val) => {
  local.value = JSON.parse(JSON.stringify(val || {}))
}, { deep: true })

onMounted(() => {
  loadStatuses()
})
</script>

<style scoped>
.issue-transition-config {
  padding: 12px 16px;
}

.form-hint {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-top: 4px;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;
}

.reference-value {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.reference-value code {
  color: var(--color-primary-6);
}

.alert-content {
  font-size: 12px;
  line-height: 1.5;
}

.alert-content code {
  padding: 1px 4px;
  background: var(--tf-bg-surface);
  border-radius: 3px;
  font-size: 11px;
}

:deep(.arco-form-item) {
  margin-bottom: 12px;
}

:deep(.arco-form-item-label) {
  font-size: 12px;
  color: var(--tf-text-secondary);
}

:deep(.arco-alert) {
  padding: 8px 12px;
  font-size: 12px;
}
</style>
