<template>
  <div class="node-config generic-node-config">
    <a-form v-if="definition" :model="local" layout="vertical" size="small">
      <div v-if="definition.inputPorts.length" class="section-title">输入参数</div>
      <a-form-item v-for="port in definition.inputPorts" :key="port.name" :label="port.label || port.name">
        <template v-if="port.description" #extra>{{ port.description }}</template>
        <div v-if="isReference(port.name)" class="reference-value">
          <code>{{ referenceLabel(port.name) }}</code>
          <a-button size="mini" type="text" @click="clearValue(port.name)">改为固定值</a-button>
        </div>
        <a-switch
          v-else-if="port.valueType === 'boolean'"
          :model-value="Boolean(literalValue(port.name))"
          @change="value => setLiteral(port.name, value)"
        />
        <a-input-number
          v-else-if="port.valueType === 'number'"
          :model-value="numberValue(port.name)"
          style="width: 100%"
          @change="value => setLiteral(port.name, value)"
        />
        <a-textarea
          v-else-if="port.valueType === 'object' || port.valueType === 'array'"
          :model-value="jsonValue(port.name)"
          :placeholder="port.valueType === 'array' ? '[]' : '{}'"
          :auto-size="{ minRows: 2, maxRows: 6 }"
          @change="value => setJsonLiteral(port.name, value, port.valueType)"
        />
        <a-input
          v-else
          :model-value="stringValue(port.name)"
          :placeholder="port.description"
          @input="value => setLiteral(port.name, value)"
        />
      </a-form-item>

      <template v-if="definition.configFields.length">
        <a-divider />
        <div class="section-title">运行配置</div>
        <a-form-item v-for="field in definition.configFields" :key="field.key" :label="field.label">
          <template v-if="field.description" #extra>{{ field.description }}</template>
          <a-select
            v-if="field.type === 'select'"
            :model-value="local.config?.[field.key]"
            @change="value => setConfig(field.key, value)"
          >
            <a-option v-for="option in field.options" :key="option.value" :value="option.value">
              {{ option.label }}
            </a-option>
          </a-select>
          <a-input-number
            v-else-if="field.type === 'number'"
            :model-value="Number(local.config?.[field.key] ?? field.defaultValue ?? 0)"
            style="width: 100%"
            @change="value => setConfig(field.key, value)"
          />
          <a-switch
            v-else-if="field.type === 'boolean'"
            :model-value="Boolean(local.config?.[field.key])"
            @change="value => setConfig(field.key, value)"
          />
          <a-textarea
            v-else-if="field.type === 'textarea'"
            :model-value="String(local.config?.[field.key] ?? '')"
            @input="value => setConfig(field.key, value)"
          />
          <a-input
            v-else
            :model-value="String(local.config?.[field.key] ?? '')"
            :placeholder="field.placeholder"
            @input="value => setConfig(field.key, value)"
          />
        </a-form-item>
      </template>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import type { NodeDefinition } from '../../node-definitions'

const props = defineProps<{ data: Record<string, any>; definition?: NodeDefinition }>()
const emit = defineEmits<{ (event: 'update:data', value: Record<string, any>): void }>()
const local = reactive<any>(JSON.parse(JSON.stringify(props.data || {})))

watch(() => props.data, value => Object.assign(local, JSON.parse(JSON.stringify(value || {}))), { deep: true })

function input(name: string) { return (local.inputs || []).find((item: any) => item.name === name) }
function literalValue(name: string) { return input(name)?.value?.type === 'literal' ? input(name).value.value : undefined }
function isReference(name: string) { return input(name)?.value?.type === 'ref' }
function referenceLabel(name: string) {
  const value = input(name)?.value
  return value ? `${value.nodeId}.${value.outputName}` : ''
}
function numberValue(name: string) {
  const value = literalValue(name)
  return value === undefined || value === '' ? undefined : Number(value)
}
function stringValue(name: string) { return String(literalValue(name) ?? '') }
function jsonValue(name: string) {
  const value = literalValue(name)
  return value === undefined ? '' : JSON.stringify(value, null, 2)
}
function setLiteral(name: string, value: unknown) {
  const item = input(name)
  if (item) item.value = value === undefined || value === '' ? null : { type: 'literal', value }
  commit()
}
function setJsonLiteral(name: string, value: string, type: string) {
  if (!value.trim()) return setLiteral(name, undefined)
  try {
    const parsed = JSON.parse(value)
    if (type === 'array' && !Array.isArray(parsed)) throw new Error('必须是数组')
    if (type === 'object' && (Array.isArray(parsed) || typeof parsed !== 'object')) throw new Error('必须是对象')
    setLiteral(name, parsed)
  } catch (error: any) {
    Message.warning(`JSON 格式错误：${error.message}`)
  }
}
function clearValue(name: string) { const item = input(name); if (item) item.value = null; commit() }
function setConfig(key: string, value: unknown) { local.config ||= {}; local.config[key] = value; commit() }
function commit() { emit('update:data', JSON.parse(JSON.stringify(local))) }
</script>

<style scoped>
.generic-node-config { padding: 12px 16px; }
.section-title { margin-bottom: 10px; font-size: 12px; font-weight: 600; color: var(--tf-text-secondary); }
.form-hint { margin-top: 4px; font-size: 11px; color: var(--tf-text-tertiary); }
.reference-value { display: flex; align-items: center; justify-content: space-between; width: 100%; }
.reference-value code { color: var(--color-primary-6); }
</style>
