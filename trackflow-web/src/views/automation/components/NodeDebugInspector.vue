<template>
  <section class="node-debug-inspector" :class="{ expanded }">
    <header class="debug-summary">
      <div>
        <span :class="['debug-status', status]">{{ statusLabel }}</span>
        <strong>{{ nodeName || '节点调试' }}</strong>
        <span v-if="detail?.durationMs != null" class="debug-duration">{{ detail.durationMs }}ms</span>
      </div>
      <div class="debug-actions">
        <button type="button" @click="emit('rerun')">重新运行</button>
        <button type="button" @click="copyResult">复制</button>
        <button type="button" :title="expanded ? '退出扩展视图' : '扩展查看结果'" @click="expanded = !expanded">
          {{ expanded ? '收起' : '展开' }}
        </button>
        <button type="button" class="danger" @click="emit('clear')">清空</button>
      </div>
    </header>

    <div v-if="!detail" class="debug-empty">
      <strong>尚未运行此节点</strong>
      <p>点击节点卡片上的播放按钮，结果会显示在这里，不会写入正式执行历史。</p>
    </div>
    <template v-else>
      <div class="debug-tabs" role="tablist" aria-label="节点调试内容">
        <button :class="{ active: activeTab === 'summary' }" @click="activeTab = 'summary'">摘要</button>
        <button :class="{ active: activeTab === 'input' }" @click="activeTab = 'input'">输入</button>
        <button :class="{ active: activeTab === 'output' }" @click="activeTab = 'output'">输出</button>
        <button v-if="detail.errorInfo" :class="{ active: activeTab === 'error' }" @click="activeTab = 'error'">错误</button>
      </div>

      <div v-if="activeTab === 'summary'" class="debug-content summary-content">
        <div class="summary-card">
          <span>运行结果</span>
          <strong>{{ statusLabel }}</strong>
        </div>
        <div class="summary-card">
          <span>输出摘要</span>
          <strong>{{ valueSummary(detail.output) }}</strong>
        </div>
        <div v-if="detail.message" class="debug-message" :class="detail.mode === 'simulated' ? 'simulated' : ''">
          {{ detail.message }}
        </div>
        <div v-if="detail.errorInfo" class="debug-error">{{ detail.errorInfo }}</div>
      </div>
      <div v-else-if="activeTab === 'input'" class="debug-content">
        <pre class="json-block">{{ formatJson(detail.input) }}</pre>
      </div>
      <div v-else-if="activeTab === 'output'" class="debug-content">
        <pre class="json-block">{{ formatJson(detail.output) }}</pre>
      </div>
      <div v-else class="debug-content">
        <pre class="error-block">{{ detail.errorInfo }}</pre>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

type DebugDetail = {
  input?: unknown
  output?: unknown
  errorInfo?: string
  message?: string
  mode?: 'executed' | 'simulated'
  durationMs?: number
}

const props = defineProps<{
  nodeName?: string
  status?: 'idle' | 'running' | 'success' | 'failed' | 'skipped' | 'cancelled'
  detail?: DebugDetail
}>()

const emit = defineEmits<{
  (e: 'rerun'): void
  (e: 'clear'): void
  (e: 'copied'): void
}>()

const activeTab = ref<'summary' | 'input' | 'output' | 'error'>('summary')
const expanded = ref(false)
const statusLabel = computed(() => ({
  idle: '等待调试', running: '运行中', success: '成功', failed: '失败', skipped: '已跳过', cancelled: '已取消',
})[props.status || 'idle'])

function formatJson(value: unknown) {
  if (value == null) return '—'
  try { return JSON.stringify(value, null, 2) }
  catch { return String(value) }
}

function valueSummary(value: unknown) {
  if (value == null) return '无输出'
  if (Array.isArray(value)) return `${value.length} 条记录`
  if (typeof value === 'object') {
    const entries = Object.entries(value as Record<string, unknown>)
    const arrays = entries.filter(([, item]) => Array.isArray(item))
    if (arrays.length === 1) return `${arrays[0][0]}：${(arrays[0][1] as unknown[]).length} 条`
    return `${entries.length} 个字段`
  }
  return String(value)
}

async function copyResult() {
  const value = JSON.stringify({
    input: props.detail?.input,
    output: props.detail?.output,
    error: props.detail?.errorInfo,
    message: props.detail?.message,
  }, null, 2)
  try {
    await navigator.clipboard.writeText(value)
    emit('copied')
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = value
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    textarea.remove()
    emit('copied')
  }
}
</script>

<style scoped>
.node-debug-inspector { min-height: 0; display: flex; flex-direction: column; height: 100%; }
.node-debug-inspector.expanded {
  position: fixed; z-index: 1200; top: 64px; right: 20px; bottom: 20px;
  width: min(760px, calc(100vw - 48px)); padding: 18px;
  border: 1px solid var(--tf-border); border-radius: 12px;
  background: var(--tf-bg-surface); box-shadow: var(--tf-shadow-xl);
}
.debug-summary { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 12px 14px; border-bottom: 1px solid var(--tf-border); }
.debug-summary > div:first-child { display: flex; min-width: 0; align-items: center; gap: 8px; }
.debug-summary strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--tf-text-primary); font-size: 13px; }
.debug-status { padding: 2px 7px; border-radius: 999px; background: var(--tf-bg-elevated); color: var(--tf-text-secondary); font-size: 11px; }
.debug-status.running { background: var(--tf-accent-bg); color: var(--tf-accent); }
.debug-status.success { background: var(--tf-success-bg); color: var(--tf-success); }
.debug-status.failed { background: var(--tf-danger-bg); color: var(--tf-danger); }
.debug-duration { color: var(--tf-text-tertiary); font-size: 11px; }
.debug-actions { display: flex; flex-shrink: 0; gap: 4px; }
.debug-actions button { border: 1px solid var(--tf-border); border-radius: 5px; padding: 3px 7px; background: var(--tf-bg-body); color: var(--tf-text-secondary); cursor: pointer; font-size: 11px; }
.debug-actions button:hover { border-color: var(--tf-accent); color: var(--tf-accent); }
.debug-actions .danger:hover { border-color: var(--tf-danger); color: var(--tf-danger); }
.debug-tabs { display: flex; gap: 2px; padding: 8px 12px 0; border-bottom: 1px solid var(--tf-border); }
.debug-tabs button { border: none; border-bottom: 2px solid transparent; padding: 7px 9px; background: transparent; color: var(--tf-text-secondary); cursor: pointer; font-size: 12px; }
.debug-tabs button.active { border-bottom-color: var(--tf-accent); color: var(--tf-accent); font-weight: 600; }
.debug-content { flex: 1; min-height: 0; overflow: auto; padding: 12px; }
.summary-content { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-content: start; gap: 8px; }
.summary-card { display: flex; flex-direction: column; gap: 5px; padding: 10px; border: 1px solid var(--tf-border); border-radius: 8px; background: var(--tf-bg-body); }
.summary-card span { color: var(--tf-text-tertiary); font-size: 11px; }
.summary-card strong { color: var(--tf-text-primary); font-size: 13px; }
.debug-message, .debug-error { grid-column: 1 / -1; padding: 10px; border-radius: 8px; color: var(--tf-text-secondary); font-size: 12px; line-height: 1.55; }
.debug-message { background: var(--tf-bg-body); }
.debug-message.simulated { background: var(--tf-accent-bg); }
.debug-error { background: var(--tf-danger-bg); color: var(--tf-danger); }
.json-block, .error-block { min-height: 240px; margin: 0; padding: 12px; overflow: auto; border: 1px solid var(--tf-border); border-radius: 8px; background: var(--tf-bg-body); color: var(--tf-text-secondary); font-size: 12px; line-height: 1.6; white-space: pre; }
.error-block { color: var(--tf-danger); }
.debug-empty { padding: 28px 18px; color: var(--tf-text-secondary); text-align: center; }
.debug-empty strong { display: block; color: var(--tf-text-primary); font-size: 13px; }
.debug-empty p { margin: 8px 0 0; font-size: 12px; line-height: 1.6; }
</style>
