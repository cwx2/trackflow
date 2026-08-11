<template>
  <section class="node-debug-inspector" :class="{ expanded }">
    <header class="debug-summary">
      <div class="debug-title">
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
      <p>点击节点卡片上的播放按钮。这里会展示本次调试的输入、输出和错误，不会写入正式执行历史。</p>
    </div>
    <template v-else>
      <div class="debug-tabs" role="tablist" aria-label="节点调试内容">
        <button :class="{ active: activeTab === 'summary' }" @click="activeTab = 'summary'">概览</button>
        <button :class="{ active: activeTab === 'input' }" @click="activeTab = 'input'">输入</button>
        <button :class="{ active: activeTab === 'output' }" @click="activeTab = 'output'">结果</button>
        <button :class="{ active: activeTab === 'raw' }" @click="activeTab = 'raw'">原始数据</button>
        <button v-if="detail.errorInfo" :class="{ active: activeTab === 'error' }" @click="activeTab = 'error'">错误</button>
      </div>

      <div v-if="activeTab === 'summary'" class="debug-content summary-content">
        <div class="summary-card result-card" :class="status">
          <span>运行结果</span>
          <strong>{{ statusLabel }}</strong>
          <small>{{ detail.mode === 'simulated' ? '使用预演数据' : '已完成真实调用' }}</small>
        </div>
        <div class="summary-card">
          <span>输出摘要</span>
          <strong>{{ valueSummary(detail.output) }}</strong>
          <small>{{ outputCollection ? `已识别 ${outputCollection.label}` : '可在结果页查看字段' }}</small>
        </div>
        <div v-if="detail.message" class="debug-message" :class="detail.mode === 'simulated' ? 'simulated' : ''">
          {{ detail.message }}
        </div>
        <div v-if="outputCollection?.items.length" class="preview-section">
          <div class="section-heading">
            <span>{{ outputCollection.label }}</span>
            <small>展示前 {{ Math.min(outputCollection.items.length, previewLimit) }} 条</small>
          </div>
          <div class="record-list">
            <article v-for="(record, index) in outputCollection.items.slice(0, previewLimit)" :key="index" class="record-row" :style="{ animationDelay: `${index * 32}ms` }">
              <strong>{{ recordTitle(record, index) }}</strong>
              <span>{{ recordMeta(record) }}</span>
            </article>
          </div>
        </div>
        <div v-if="detail.errorInfo" class="debug-error">{{ detail.errorInfo }}</div>
      </div>

      <div v-else-if="activeTab === 'input'" class="debug-content">
        <div v-if="inputEntries.length" class="field-list">
          <div v-for="entry in inputEntries" :key="entry.key" class="field-row">
            <span>{{ entry.key }}</span><strong>{{ displayValue(entry.value) }}</strong>
          </div>
        </div>
        <div v-else class="empty-data">本节点不需要额外输入。</div>
      </div>

      <div v-else-if="activeTab === 'output'" class="debug-content">
        <template v-if="outputCollection?.items.length">
          <div class="section-heading"><span>{{ outputCollection.label }}</span><small>共 {{ outputCollection.items.length }} 条</small></div>
          <div class="record-list full">
            <article v-for="(record, index) in outputCollection.items" :key="index" class="record-row" :style="{ animationDelay: `${Math.min(index, 8) * 24}ms` }">
              <strong>{{ recordTitle(record, index) }}</strong>
              <span>{{ recordMeta(record) }}</span>
            </article>
          </div>
        </template>
        <div v-else-if="outputEntries.length" class="field-list">
          <div v-for="entry in outputEntries" :key="entry.key" class="field-row">
            <span>{{ entry.key }}</span><strong>{{ displayValue(entry.value) }}</strong>
          </div>
        </div>
        <div v-else class="empty-data">本次运行没有返回数据。</div>
      </div>

      <div v-else-if="activeTab === 'raw'" class="debug-content raw-content">
        <pre class="json-block">{{ formatJson({ input: detail.input, output: detail.output, message: detail.message }) }}</pre>
      </div>
      <div v-else class="debug-content"><pre class="error-block">{{ detail.errorInfo }}</pre></div>
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

type FieldEntry = { key: string; value: unknown }
type OutputCollection = { label: string; items: unknown[] }

const props = defineProps<{
  nodeName?: string
  status?: 'idle' | 'running' | 'success' | 'failed' | 'skipped' | 'cancelled'
  detail?: DebugDetail
}>()

const emit = defineEmits<{ (e: 'rerun'): void; (e: 'clear'): void; (e: 'copied'): void }>()

const activeTab = ref<'summary' | 'input' | 'output' | 'raw' | 'error'>('summary')
const expanded = ref(false)
const previewLimit = 4
const statusLabel = computed(() => ({
  idle: '等待调试', running: '运行中', success: '成功', failed: '失败', skipped: '已跳过', cancelled: '已取消',
})[props.status || 'idle'])
const inputEntries = computed(() => objectEntries(props.detail?.input))
const outputEntries = computed(() => objectEntries(props.detail?.output))
const outputCollection = computed<OutputCollection | null>(() => findCollection(props.detail?.output))

function objectEntries(value: unknown): FieldEntry[] {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? Object.entries(value as Record<string, unknown>).map(([key, item]) => ({ key, value: item }))
    : []
}

function findCollection(value: unknown): OutputCollection | null {
  if (Array.isArray(value)) return { label: '返回记录', items: value }
  if (!value || typeof value !== 'object') return null
  const match = Object.entries(value as Record<string, unknown>).find(([, item]) => Array.isArray(item))
  return match ? { label: match[0], items: match[1] as unknown[] } : null
}

function formatJson(value: unknown) {
  if (value == null) return '—'
  try { return JSON.stringify(value, null, 2) } catch { return String(value) }
}

function valueSummary(value: unknown) {
  const collection = findCollection(value)
  if (collection) return `${collection.label}：${collection.items.length} 条`
  if (value == null) return '无输出'
  if (typeof value === 'object') return `${Object.keys(value as Record<string, unknown>).length} 个字段`
  return String(value)
}

function displayValue(value: unknown) {
  if (value == null || value === '') return '—'
  if (Array.isArray(value)) return `${value.length} 项`
  if (typeof value === 'object') return `${Object.keys(value as Record<string, unknown>).length} 个字段`
  const text = String(value)
  return text.length > 88 ? `${text.slice(0, 88)}…` : text
}

function recordTitle(record: unknown, index: number) {
  if (!record || typeof record !== 'object') return displayValue(record)
  const item = record as Record<string, unknown>
  return String(item.title || item.name || item.key || item.id || `记录 ${index + 1}`)
}

function recordMeta(record: unknown) {
  if (!record || typeof record !== 'object') return '基础值'
  const item = record as Record<string, unknown>
  const parts = [item.key && `编号 ${item.key}`, item.status && `状态 ${item.status}`, item.priority && `优先级 ${item.priority}`].filter(Boolean)
  return parts.length ? parts.join(' · ') : `${Object.keys(item).length} 个字段`
}

async function copyResult() {
  const value = formatJson({ input: props.detail?.input, output: props.detail?.output, error: props.detail?.errorInfo, message: props.detail?.message })
  try {
    await navigator.clipboard.writeText(value)
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = value
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    textarea.remove()
  }
  emit('copied')
}
</script>

<style scoped>
.node-debug-inspector { display: flex; flex-direction: column; max-height: min(68vh, 720px); animation: inspector-enter 180ms ease-out both; }
.node-debug-inspector.expanded { position: fixed; z-index: 1200; top: 64px; right: 20px; bottom: 20px; width: min(760px, calc(100vw - 48px)); max-height: none; padding: 18px; border: 1px solid var(--tf-border); border-radius: 12px; background: var(--tf-bg-surface); box-shadow: var(--tf-shadow-xl); }
.debug-summary { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 12px 14px; border-bottom: 1px solid var(--tf-border); }
.debug-title { display: flex; min-width: 0; align-items: center; gap: 8px; }
.debug-title strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--tf-text-primary); font-size: 13px; }
.debug-status { padding: 2px 7px; border-radius: 999px; background: var(--tf-bg-elevated); color: var(--tf-text-secondary); font-size: 11px; }
.debug-status.running { background: var(--tf-accent-bg); color: var(--tf-accent); animation: status-pulse 1.7s ease-in-out infinite; }
.debug-status.success { background: var(--tf-success-bg); color: var(--tf-success); }.debug-status.failed { background: var(--tf-danger-bg); color: var(--tf-danger); }
.debug-duration { color: var(--tf-text-tertiary); font-size: 11px; }.debug-actions { display: flex; flex-shrink: 0; gap: 4px; }
.debug-actions button { border: 1px solid var(--tf-border); border-radius: 5px; padding: 3px 7px; background: var(--tf-bg-body); color: var(--tf-text-secondary); cursor: pointer; font-size: 11px; transition: border-color .16s, color .16s, transform .16s; }
.debug-actions button:hover { border-color: var(--tf-accent); color: var(--tf-accent); transform: translateY(-1px); }.debug-actions .danger:hover { border-color: var(--tf-danger); color: var(--tf-danger); }
.debug-tabs { display: flex; gap: 2px; padding: 8px 12px 0; border-bottom: 1px solid var(--tf-border); }.debug-tabs button { border: none; border-bottom: 2px solid transparent; padding: 7px 9px; background: transparent; color: var(--tf-text-secondary); cursor: pointer; font-size: 12px; transition: color .16s, border-color .16s; }.debug-tabs button.active { border-bottom-color: var(--tf-accent); color: var(--tf-accent); font-weight: 600; }
.debug-content { min-height: 0; overflow: auto; padding: 12px; animation: content-enter 160ms ease-out both; }.summary-content { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-content: start; gap: 8px; }
.summary-card { display: flex; flex-direction: column; gap: 5px; padding: 11px; border: 1px solid var(--tf-border); border-radius: 8px; background: linear-gradient(135deg, var(--tf-bg-body), var(--tf-bg-surface)); }.summary-card.success { border-color: color-mix(in srgb, var(--tf-success) 35%, var(--tf-border)); }.summary-card span, .summary-card small { color: var(--tf-text-tertiary); font-size: 11px; }.summary-card strong { color: var(--tf-text-primary); font-size: 13px; }
.debug-message, .debug-error, .preview-section { grid-column: 1 / -1; }.debug-message, .debug-error { padding: 10px; border-radius: 8px; color: var(--tf-text-secondary); font-size: 12px; line-height: 1.55; background: var(--tf-bg-body); }.debug-message.simulated { background: var(--tf-accent-bg); }.debug-error { background: var(--tf-danger-bg); color: var(--tf-danger); }
.section-heading { display: flex; align-items: baseline; justify-content: space-between; margin: 2px 0 8px; color: var(--tf-text-primary); font-size: 12px; font-weight: 600; }.section-heading small { color: var(--tf-text-tertiary); font-weight: 400; }.record-list { display: flex; flex-direction: column; gap: 6px; }.record-list.full { max-height: 420px; overflow-y: auto; padding-right: 2px; }.record-row { display: flex; flex-direction: column; gap: 3px; padding: 9px 10px; border: 1px solid var(--tf-border); border-radius: 7px; background: var(--tf-bg-body); animation: record-enter 180ms ease-out both; transition: border-color .16s, transform .16s; }.record-row:hover { border-color: var(--tf-accent); transform: translateX(2px); }.record-row strong { color: var(--tf-text-primary); font-size: 12px; }.record-row span { color: var(--tf-text-tertiary); font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.field-list { display: flex; flex-direction: column; gap: 6px; }.field-row { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 9px 10px; border: 1px solid var(--tf-border); border-radius: 7px; background: var(--tf-bg-body); font-size: 12px; }.field-row span { color: var(--tf-text-tertiary); }.field-row strong { overflow: hidden; color: var(--tf-text-primary); font-weight: 500; text-align: right; text-overflow: ellipsis; white-space: nowrap; }
.json-block, .error-block { min-height: 190px; max-height: 460px; margin: 0; padding: 12px; overflow: auto; border: 1px solid var(--tf-border); border-radius: 8px; background: var(--tf-bg-body); color: var(--tf-text-secondary); font-size: 12px; line-height: 1.6; white-space: pre; }.error-block { color: var(--tf-danger); }.empty-data { padding: 18px; border: 1px dashed var(--tf-border); border-radius: 8px; color: var(--tf-text-tertiary); text-align: center; font-size: 12px; }.debug-empty { padding: 28px 18px; color: var(--tf-text-secondary); text-align: center; }.debug-empty strong { display: block; color: var(--tf-text-primary); font-size: 13px; }.debug-empty p { margin: 8px 0 0; font-size: 12px; line-height: 1.6; }
@keyframes inspector-enter { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } } @keyframes content-enter { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: translateY(0); } } @keyframes record-enter { from { opacity: 0; transform: translateX(-4px); } to { opacity: 1; transform: translateX(0); } } @keyframes status-pulse { 50% { box-shadow: 0 0 0 4px var(--tf-accent-bg); } }
@media (prefers-reduced-motion: reduce) { .node-debug-inspector, .debug-content, .record-row, .debug-status.running { animation: none; }.debug-actions button, .record-row { transition: none; } }
</style>
