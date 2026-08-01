<template>
  <div class="code-config">
    <div class="config-item">
      <label class="config-label">语言</label>
      <a-select v-model="local.language" size="small" class="config-select">
        <a-option value="shell">Shell</a-option>
        <a-option value="python">Python</a-option>
      </a-select>
    </div>

    <div class="config-item">
      <label class="config-label">脚本内容</label>
      <textarea
        v-model="local.script"
        class="code-editor"
        :placeholder="local.language === 'python' ? 'print(...)' : 'echo hello'"
        spellcheck="false"
      />
    </div>

    <div class="config-item">
      <label class="config-label">超时（秒）</label>
      <a-input-number v-model="local.timeout" size="small" :min="1" :max="3600" />
    </div>

    <div class="config-tip">
      💡 Shell 脚本的 stdout 将作为 <code>result</code> 输出；
      输入数据会以环境变量形式注入（如 <code>$INPUT_key</code>）
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'

const props = defineProps<{ data: Record<string, any> }>()
const emit = defineEmits(['update:data'])

const local = reactive({
  language: props.data?.config?.language ?? 'shell',
  script: props.data?.config?.script ?? 'echo "hello world"',
  timeout: props.data?.config?.timeout ?? 30,
})

watch(local, (val) => {
  emit('update:data', {
    ...props.data,
    config: { ...props.data?.config, ...val },
  })
}, { deep: true })
</script>

<style scoped>
.code-config { padding: 12px; display: flex; flex-direction: column; gap: 14px; }

.config-item { display: flex; flex-direction: column; gap: 6px; }
.config-label { font-size: 12px; color: var(--tf-text-secondary); font-weight: 500; }
.config-select { width: 100%; }

.code-editor {
  width: 100%;
  min-height: 160px;
  padding: 10px;
  background: #0d1117;
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  color: #e6edf3;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.6;
  resize: vertical;
  tab-size: 2;
  box-sizing: border-box;
}
.code-editor:focus { outline: none; border-color: var(--tf-accent); }

.config-tip {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-elevated);
  border-radius: 6px;
  padding: 8px 10px;
  line-height: 1.5;
}
.config-tip code {
  background: var(--tf-bg-hover);
  padding: 1px 4px;
  border-radius: 3px;
  font-family: monospace;
  color: var(--tf-accent);
}
</style>
