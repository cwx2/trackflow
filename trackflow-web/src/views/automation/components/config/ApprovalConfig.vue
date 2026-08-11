<template>
  <div class="node-config approval-config">
    <a-form :model="local" layout="vertical" size="small">

      <!-- 审批标题 -->
      <a-form-item label="审批标题">
        <div v-if="isRef('title')" class="reference-value">
          <code>{{ refLabel('title') }}</code>
          <a-button size="mini" type="text" @click="clearInput('title')">改为固定值</a-button>
        </div>
        <a-input
          v-else
          :model-value="literalStr('title')"
          placeholder="如：部署到生产环境前请求审批"
          @input="(v: string) => setLiteral('title', v)"
        />
        <template #extra>必填。显示在审批通知和审批详情页的标题。</template>
      </a-form-item>

      <!-- 风险说明 -->
      <a-form-item label="风险说明">
        <div v-if="isRef('description')" class="reference-value">
          <code>{{ refLabel('description') }}</code>
          <a-button size="mini" type="text" @click="clearInput('description')">改为固定值</a-button>
        </div>
        <a-textarea
          v-else
          :model-value="literalStr('description')"
          placeholder="说明操作的风险与影响，帮助审批人做决策"
          :auto-size="{ minRows: 2, maxRows: 5 }"
          @input="(v: string) => setLiteral('description', v)"
        />
      </a-form-item>

      <a-divider style="margin: 10px 0" />

      <!-- 风险等级 -->
      <a-form-item label="风险等级">
        <a-select
          :model-value="local.config?.riskLevel ?? 'medium'"
          @change="(v: any) => setConfig('riskLevel', v)"
        >
          <a-option value="low">
            <span class="risk-dot low" />低
          </a-option>
          <a-option value="medium">
            <span class="risk-dot medium" />中
          </a-option>
          <a-option value="high">
            <span class="risk-dot high" />高
          </a-option>
          <a-option value="critical">
            <span class="risk-dot critical" />关键
          </a-option>
        </a-select>
        <template #extra>影响审批通知的紧急程度标识，不影响审批逻辑。</template>
      </a-form-item>

      <!-- 审批有效期 -->
      <a-form-item label="审批有效期（小时）">
        <a-input-number
          :model-value="Number(local.config?.expiryHours ?? 24)"
          :min="1"
          :max="720"
          style="width: 100%"
          placeholder="24"
          @change="(v: any) => setConfig('expiryHours', v)"
        >
          <template #suffix>小时</template>
        </a-input-number>
        <template #extra>超时未审批将自动拒绝，工作流沿「拒绝」分支继续。最长 720 小时（30 天）。</template>
      </a-form-item>

      <a-alert type="warning" style="margin-top: 8px">
        <template #icon><span>🛡️</span></template>
        <div class="alert-content">
          审批节点会暂停工作流等待人工操作。<br />
          批准 → <strong>approved</strong> 输出端口，拒绝 → <strong>rejected</strong> 输出端口。
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

function isRef(name: string): boolean {
  return findInput(name)?.value?.type === 'ref'
}

function refLabel(name: string): string {
  const v = findInput(name)?.value
  return v?.type === 'ref' ? `${v.nodeId}.${v.outputName}` : ''
}

function literalStr(name: string): string {
  const item = findInput(name)
  return item?.value?.type === 'literal' ? String(item.value.value ?? '') : ''
}

function setLiteral(name: string, value: string) {
  const item = findInput(name)
  if (item) {
    item.value = value ? { type: 'literal', value } : null
  }
  commit()
}

function clearInput(name: string) {
  const item = findInput(name)
  if (item) item.value = null
  commit()
}

function setConfig(key: string, value: unknown) {
  local.value.config = { ...(local.value.config || {}), [key]: value }
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
.approval-config {
  padding: 12px 16px;
}

.reference-value {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.reference-value code {
  color: var(--color-primary-6);
  font-size: 12px;
}

.risk-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;
}

.risk-dot.low      { background: #10b981; }
.risk-dot.medium   { background: #f59e0b; }
.risk-dot.high     { background: #f97316; }
.risk-dot.critical { background: #dc2626; }

.alert-content {
  font-size: 12px;
  line-height: 1.6;
}



</style>
