<template>
  <div class="node-config end-config">
    <a-form :model="local" layout="vertical" size="small">

      <a-form-item label="最终结果来源">
        <div v-if="isRef" class="reference-value">
          <code>{{ refLabel }}</code>
          <a-button size="mini" type="text" @click="clearInput">改为固定值</a-button>
        </div>
        <a-textarea
          v-else
          :model-value="resultValue"
          placeholder="通常由上游节点连线传入，也可填写固定的结束描述"
          :auto-size="{ minRows: 2, maxRows: 4 }"
          @input="setResultLiteral"
        />
        <template #extra>工作流最终输出，将记录到执行历史中。建议通过连线从上游节点传入。</template>
      </a-form-item>

      <a-form-item label="结束描述（可选）">
        <a-input
          v-model="local.description"
          placeholder="简要描述工作流正常结束的标志，如：任务已完成"
          @input="commit"
        />
        <template #extra>仅作备注，不影响执行逻辑。</template>
      </a-form-item>

      <a-alert type="info" style="margin-top: 8px">
        <template #icon><span>⏹</span></template>
        <div class="alert-content">
          结束节点是工作流的出口，执行到此节点时工作流标记为完成。
          所有分支最终都应连接到结束节点。
        </div>
      </a-alert>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'

const props = defineProps<{ data: Record<string, any> }>()
const emit = defineEmits<{ (e: 'update:data', value: Record<string, any>): void }>()

const local = ref<Record<string, any>>(JSON.parse(JSON.stringify(props.data || {})))

function findResultInput() {
  return (local.value.inputs || []).find((item: any) => item.name === 'result')
}

const isRef = computed(() => {
  const item = findResultInput()
  return item?.value?.type === 'ref'
})

const refLabel = computed(() => {
  const item = findResultInput()
  return item?.value?.type === 'ref' ? `${item.value.nodeId}.${item.value.outputName}` : ''
})

const resultValue = computed(() => {
  const item = findResultInput()
  if (item?.value?.type === 'literal') {
    const val = item.value.value
    return val === null || val === undefined ? '' : String(val)
  }
  return ''
})

function setResultLiteral(value: string) {
  const item = findResultInput()
  if (item) {
    item.value = value ? { type: 'literal', value } : null
  }
  commit()
}

function clearInput() {
  const item = findResultInput()
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
.end-config {
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
}

.alert-content {
  font-size: 12px;
  line-height: 1.5;
}



</style>
