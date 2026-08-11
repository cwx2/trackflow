<template>
  <div class="node-config start-config">
    <a-form :model="local" layout="vertical" size="small">

      <!-- 触发参数列表 -->
      <div class="section-header">
        <span class="section-title">触发参数</span>
        <a-button type="text" size="mini" @click="addParam">
          <template #icon><IconPlus /></template>
          添加参数
        </a-button>
      </div>

      <div v-if="params.length === 0" class="empty-hint">
        暂无触发参数，工作流将以空输入启动。点击「添加参数」定义外部传入字段。
      </div>

      <div v-for="(param, index) in params" :key="index" class="param-row">
        <a-input
          v-model="param.name"
          placeholder="参数名，如 issueId"
          class="param-name"
          @input="commit"
        />
        <a-select
          v-model="param.type"
          class="param-type"
          placeholder="类型"
          @change="commit"
        >
          <a-option value="string">字符串</a-option>
          <a-option value="number">数字</a-option>
          <a-option value="boolean">布尔</a-option>
          <a-option value="object">对象</a-option>
        </a-select>
        <a-checkbox v-model="param.required" @change="commit">必填</a-checkbox>
        <a-button type="text" status="danger" size="mini" @click="removeParam(index)">
          <template #icon><IconDelete /></template>
        </a-button>
      </div>

      <a-divider style="margin: 12px 0" />

      <a-form-item label="工作流说明（可选）">
        <a-textarea
          v-model="local.description"
          placeholder="描述此工作流的用途，供协作者参考"
          :auto-size="{ minRows: 2, maxRows: 4 }"
          @input="commit"
        />
      </a-form-item>

      <a-alert type="info" style="margin-top: 4px">
        <template #icon><span>💡</span></template>
        触发参数会作为输出端口 <code>trigger</code> 对象的字段，下游节点通过 <code>trigger.参数名</code> 引用。
      </a-alert>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { IconPlus, IconDelete } from '@arco-design/web-vue/es/icon'

const props = defineProps<{ data: Record<string, any> }>()
const emit = defineEmits<{ (e: 'update:data', value: Record<string, any>): void }>()

interface TriggerParam {
  name: string
  type: string
  required: boolean
}

const local = ref<Record<string, any>>(JSON.parse(JSON.stringify(props.data || {})))
const params = ref<TriggerParam[]>([])

onMounted(() => {
  // 从 configFields 的 triggerFields 字符串解析，或从 local.params 读取
  if (local.value.params && Array.isArray(local.value.params)) {
    params.value = local.value.params
  } else if (local.value.config?.triggerFields) {
    // 兼容旧格式：逗号分隔的字符串
    params.value = String(local.value.config.triggerFields)
      .split(',')
      .map(s => s.trim())
      .filter(Boolean)
      .map(name => ({ name, type: 'string', required: false }))
  }
})

function addParam() {
  params.value.push({ name: '', type: 'string', required: false })
  commit()
}

function removeParam(index: number) {
  params.value.splice(index, 1)
  commit()
}

function commit() {
  const updated = {
    ...local.value,
    params: JSON.parse(JSON.stringify(params.value)),
    // 同步 config.triggerFields 保持向后兼容
    config: {
      ...(local.value.config || {}),
      triggerFields: params.value.map(p => p.name).filter(Boolean).join(','),
    },
  }
  emit('update:data', updated)
}

watch(() => props.data, (val) => {
  local.value = JSON.parse(JSON.stringify(val || {}))
  if (local.value.params && Array.isArray(local.value.params)) {
    params.value = local.value.params
  }
}, { deep: true })
</script>

<style scoped>
.start-config {
  padding: 12px 16px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--tf-text-secondary);
}

.empty-hint {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-surface);
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 12px;
}

.param-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.param-name {
  flex: 1;
}

.param-type {
  width: 90px;
}



:deep(.arco-alert code) {
  background: var(--tf-bg-elevated);
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 11px;
}
</style>
