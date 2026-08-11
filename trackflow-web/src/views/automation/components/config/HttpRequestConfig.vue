<template>
  <div class="node-config http-config">
    <div class="config-item">
      <label class="config-label">请求方法</label>
      <a-select v-model="local.method" size="small" class="config-select">
        <a-option v-for="m in methods" :key="m" :value="m">{{ m }}</a-option>
      </a-select>
    </div>

    <div class="config-item">
      <label class="config-label">请求头（JSON）</label>
      <textarea
        v-model="local.headers"
        class="json-editor"
        placeholder='{"Content-Type": "application/json"}'
        spellcheck="false"
      />
      <span v-if="headersError" class="field-error">{{ headersError }}</span>
    </div>

    <div class="config-item">
      <label class="config-label">超时（秒）</label>
      <a-input-number v-model="local.timeout" size="small" :min="1" :max="300" />
    </div>

    <div class="config-tip">
      💡 URL 和请求体通过左侧输入端口传入，支持引用上游变量
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch, computed } from 'vue'

const props = defineProps<{ data: Record<string, any> }>()
const emit = defineEmits(['update:data'])

const methods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']

const local = reactive({
  method:  props.data?.config?.method  ?? 'GET',
  headers: props.data?.config?.headers ?? '{"Content-Type": "application/json"}',
  timeout: props.data?.config?.timeout ?? 30,
})

const headersError = computed(() => {
  try { JSON.parse(local.headers); return '' } catch { return 'JSON 格式不正确' }
})

watch(local, (val) => {
  emit('update:data', {
    ...props.data,
    config: { ...props.data?.config, ...val },
  })
}, { deep: true })
</script>

<style scoped>
.http-config { padding: 12px; display: flex; flex-direction: column; gap: 14px; }

.config-item { display: flex; flex-direction: column; gap: 6px; }
.config-label { font-size: 12px; color: var(--tf-text-secondary); font-weight: 500; }
.config-select { width: 100%; }

.json-editor {
  width: 100%;
  min-height: 80px;
  padding: 8px 10px;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  color: var(--tf-text-primary);
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
  line-height: 1.5;
  resize: vertical;
  box-sizing: border-box;
}
.json-editor:focus { outline: none; border-color: var(--tf-accent); }

.field-error { font-size: 11px; color: var(--color-danger-6); }

.config-tip {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-elevated);
  border-radius: 6px;
  padding: 8px 10px;
  line-height: 1.5;
}
</style>
