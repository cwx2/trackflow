<template>
  <div class="issue-update-config">
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
        <div class="form-hint">通常来自上游"查找工单"节点的输出</div>
      </a-form-item>

      <a-form-item label="优先级">
        <a-select
          :model-value="getInputLiteral('priority')"
          placeholder="不修改"
          allow-clear
          @change="(val: any) => setInputLiteral('priority', val)"
        >
          <a-option value="critical">
            <span class="priority-dot" style="background: var(--tf-priority-critical)"></span> Critical
          </a-option>
          <a-option value="high">
            <span class="priority-dot" style="background: var(--tf-priority-high)"></span> High
          </a-option>
          <a-option value="medium">
            <span class="priority-dot" style="background: var(--tf-priority-medium)"></span> Medium
          </a-option>
          <a-option value="low">
            <span class="priority-dot" style="background: var(--tf-priority-low)"></span> Low
          </a-option>
        </a-select>
        <div class="form-hint">留空则不修改优先级</div>
      </a-form-item>

      <a-form-item label="负责人">
        <div v-if="isReference('assigneeId')" class="reference-value">
          <code>{{ referenceLabel('assigneeId') }}</code>
          <a-button size="mini" type="text" @click="clearInput('assigneeId')">改为固定值</a-button>
        </div>
        <a-input-number
          v-else
          :model-value="getInputLiteral('assigneeId')"
          placeholder="用户 ID（留空不修改）"
          style="width: 100%"
          @change="(val: any) => setInputLiteral('assigneeId', val || undefined)"
        />
        <div class="form-hint">设置工单负责人的用户 ID</div>
      </a-form-item>

      <a-form-item label="标签 ID">
        <div v-if="isReference('tagIds')" class="reference-value">
          <code>{{ referenceLabel('tagIds') }}</code>
          <a-button size="mini" type="text" @click="clearInput('tagIds')">改为固定值</a-button>
        </div>
        <a-input
          v-else
          :model-value="String(getInputLiteral('tagIds') ?? '')"
          placeholder="逗号分隔的标签 ID，如: 1,2,3"
          allow-clear
          @input="(val: any) => setInputLiteral('tagIds', val || undefined)"
        />
        <div class="form-hint">全量替换工单标签；留空不修改，设为空串清空所有标签</div>
      </a-form-item>

      <a-form-item label="自定义字段">
        <div v-if="isReference('customFields')" class="reference-value">
          <code>{{ referenceLabel('customFields') }}</code>
          <a-button size="mini" type="text" @click="clearInput('customFields')">改为固定值</a-button>
        </div>
        <a-textarea
          v-else
          :model-value="String(getInputLiteral('customFields') ?? '')"
          placeholder='JSON 格式，如 {"severity":"P1","environment":"prod"}'
          :auto-size="{ minRows: 2, maxRows: 4 }"
          @input="(val: any) => setInputLiteral('customFields', val || undefined)"
        />
        <div class="form-hint">JSON 格式的自定义字段键值对，留空不修改</div>
      </a-form-item>

      <a-alert type="info" style="margin-top: 8px">
        <template #icon><span>ℹ️</span></template>
        <div class="alert-content">
          执行身份必须有 <code>issue:edit</code> 权限。只有实际传入的字段会被更新，未传入的字段保持不变。
        </div>
      </a-alert>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{ data: Record<string, any> }>()
const emit = defineEmits<{ (e: 'update:data', value: Record<string, any>): void }>()

const local = ref<Record<string, any>>(JSON.parse(JSON.stringify(props.data || {})))

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

watch(() => props.data, (val) => {
  local.value = JSON.parse(JSON.stringify(val || {}))
}, { deep: true })
</script>

<style scoped>
.issue-update-config {
  padding: 12px 16px;
}

.form-hint {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-top: 4px;
}

.priority-dot {
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
