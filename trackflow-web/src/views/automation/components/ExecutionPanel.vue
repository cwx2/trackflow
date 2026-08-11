<template>
  <div class="execution-panel" :class="{ collapsed: props.collapsed }">
    <div class="panel-header" @click="toggleCollapsed">
      <div class="title-cluster">
        <span class="panel-title">执行追踪</span>
        <span v-if="currentStatus" :class="['status-badge', currentStatus]">{{ statusLabel }}</span>
        <span v-if="nodeList.length" class="trace-count">{{ nodeList.length }} 个节点</span>
      </div>
      <div class="header-actions">
        <button class="toggle-button" type="button" @click.stop="toggleCollapsed" :aria-label="props.collapsed ? '展开执行追踪' : '收起执行追踪'">
          {{ props.collapsed ? '▲ 展开' : '▼ 收起' }}
        </button>
        <button class="close-btn" @click.stop="emit('close')" title="关闭执行追踪">✕</button>
      </div>
    </div>

    <Transition name="trace-body">
    <div v-if="!props.collapsed" class="panel-body">
      <aside class="node-list" aria-label="执行节点列表">
        <div v-for="(item, index) in nodeList" :key="item.nodeId" :class="['node-row', item.status, selectedNodeId === item.nodeId ? 'selected' : '']" @click="selectNode(item)">
          <span class="timeline-marker"><i>{{ statusIcon(item.status) }}</i></span>
          <span class="node-order">{{ String(index + 1).padStart(2, '0') }}</span>
          <span class="node-name">{{ item.nodeName || item.nodeId }}</span>
          <span class="node-duration">{{ item.durationMs != null ? `${item.durationMs}ms` : '—' }}</span>
        </div>
        <div v-if="nodeList.length === 0" class="empty-hint">
          <template v-if="runtimeEnabled">工作流运行中，正在等待节点返回结果。</template>
          <template v-else>点击「试运行」后，这里会按执行顺序展示节点与耗时。</template>
        </div>
      </aside>

      <main v-if="selectedDetail" class="node-detail">
        <div class="detail-heading">
          <div><span class="detail-kicker">当前节点</span><strong>{{ selectedDetail.nodeName || selectedDetail.nodeId }}</strong></div>
          <span :class="['node-state', selectedDetail.status]">{{ nodeStateLabel(selectedDetail.status) }}</span>
        </div>
        <div class="detail-stats">
          <div><span>输入</span><strong>{{ valueSummary(selectedDetail.input) }}</strong></div>
          <div><span>输出</span><strong>{{ valueSummary(selectedDetail.output) }}</strong></div>
          <div><span>耗时</span><strong>{{ selectedDetail.durationMs != null ? `${selectedDetail.durationMs}ms` : '—' }}</strong></div>
        </div>
        <div class="data-tabs" role="tablist" aria-label="节点执行数据">
          <button :class="{ active: activeDataTab === 'input' }" @click="activeDataTab = 'input'">输入</button>
          <button :class="{ active: activeDataTab === 'output' }" @click="activeDataTab = 'output'">输出</button>
          <button :class="{ active: activeDataTab === 'raw' }" @click="activeDataTab = 'raw'">原始数据</button>
          <button v-if="selectedDetail.errorInfo" :class="{ active: activeDataTab === 'error' }" @click="activeDataTab = 'error'">错误</button>
          <button v-if="streamingOutput[selectedDetail.nodeId]" :class="{ active: activeDataTab === 'stream' }" @click="activeDataTab = 'stream'">实时输出</button>
        </div>
        <section class="data-content">
          <template v-if="activeDataTab === 'input' || activeDataTab === 'output'">
            <div v-if="activeEntries.length" class="field-grid" :key="`${selectedDetail.nodeId}-${activeDataTab}`">
              <article v-for="entry in activeEntries" :key="entry.key" class="field-card"><span>{{ entry.key }}</span><strong>{{ displayValue(entry.value) }}</strong></article>
            </div>
            <div v-else class="empty-data">{{ activeDataTab === 'input' ? '此节点没有额外输入。' : '此节点没有返回业务数据。' }}</div>
          </template>
          <pre v-else-if="activeDataTab === 'raw'" class="detail-json" :key="`${selectedDetail.nodeId}-raw`">{{ formatJson({ input: selectedDetail.input, output: selectedDetail.output, error: selectedDetail.errorInfo }) }}</pre>
          <pre v-else-if="activeDataTab === 'error'" class="detail-json error-text">{{ selectedDetail.errorInfo }}</pre>
          <pre v-else ref="streamOutputRef" class="detail-json streaming">{{ streamingOutput[selectedDetail.nodeId] }}</pre>
        </section>
      </main>
      <main v-else class="node-detail empty-detail"><strong>选择一个已执行的节点</strong><span>输入、输出、错误和耗时会集中显示在这里。</span></main>
    </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'

type NodeStatus = 'idle' | 'running' | 'success' | 'failed' | 'skipped' | 'cancelled'
interface NodeEntry { nodeId: string; nodeName?: string; status: NodeStatus; durationMs?: number; input?: unknown; output?: unknown; errorInfo?: string; message?: string; mode?: 'executed' | 'simulated'; startedAt?: string }
type DataTab = 'input' | 'output' | 'raw' | 'error' | 'stream'

const props = defineProps<{
  nodeStatusMap: Record<string, NodeStatus>
  nodeExecutionDetails: Record<string, { input?: unknown; output?: unknown; errorInfo?: string; durationMs?: number; nodeName?: string; message?: string; mode?: 'executed' | 'simulated'; startedAt?: string }>
  streamingOutput: Record<string, string>
  isRunning: boolean
  runtimeEnabled?: boolean
  mode?: 'workflow' | 'node-debug'
  collapsed?: boolean
}>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'collapse-change', collapsed: boolean): void; (e: 'goHistory'): void; (e: 'rerun-debug'): void; (e: 'clear-debug'): void; (e: 'copy-debug-result'): void }>()

const selectedNodeId = ref<string | null>(null)
const activeDataTab = ref<DataTab>('output')
const streamOutputRef = ref<HTMLPreElement | null>(null)
const nodeList = computed<NodeEntry[]>(() => Object.entries(props.nodeStatusMap).map(([nodeId, status]) => ({ nodeId, status, ...props.nodeExecutionDetails[nodeId] })))
const selectedDetail = computed(() => nodeList.value.find(node => node.nodeId === selectedNodeId.value) || null)
const activeEntries = computed(() => objectEntries(activeDataTab.value === 'input' ? selectedDetail.value?.input : selectedDetail.value?.output))
const currentStatus = computed<'running' | 'success' | 'failed' | 'cancelled' | null>(() => {
  const statuses = Object.values(props.nodeStatusMap)
  if (props.isRunning || statuses.some(status => status === 'running')) return 'running'
  if (statuses.some(status => status === 'failed')) return 'failed'
  if (statuses.some(status => status === 'cancelled')) return 'cancelled'
  return statuses.length && statuses.every(status => status === 'success' || status === 'skipped') ? 'success' : null
})
const statusLabels: Record<NonNullable<typeof currentStatus.value>, string> = { running: '运行中', success: '已完成', failed: '失败', cancelled: '已取消' }
const statusLabel = computed(() => currentStatus.value ? statusLabels[currentStatus.value] : '')

function objectEntries(value: unknown) { return value && typeof value === 'object' && !Array.isArray(value) ? Object.entries(value as Record<string, unknown>).map(([key, item]) => ({ key, value: item })) : Array.isArray(value) ? [{ key: '记录数量', value: value.length }] : [] }
function toggleCollapsed() { emit('collapse-change', !props.collapsed) }
function selectNode(item: NodeEntry) { selectedNodeId.value = item.nodeId; activeDataTab.value = item.errorInfo ? 'error' : 'output' }
function statusIcon(status: NodeStatus) { return ({ idle: '○', running: '•', success: '✓', failed: '×', skipped: '−', cancelled: '■' })[status] }
function nodeStateLabel(status: NodeStatus) { return ({ idle: '等待', running: '运行中', success: '成功', failed: '失败', skipped: '已跳过', cancelled: '已取消' })[status] }
function valueSummary(value: unknown) { if (value == null) return '—'; if (Array.isArray(value)) return `${value.length} 条记录`; if (typeof value === 'object') { const arrays = Object.values(value as Record<string, unknown>).filter(Array.isArray); return arrays.length ? `${(arrays[0] as unknown[]).length} 条记录` : `${Object.keys(value as Record<string, unknown>).length} 个字段` }; return String(value) }
function displayValue(value: unknown) { if (value == null || value === '') return '—'; if (Array.isArray(value)) return `${value.length} 项`; if (typeof value === 'object') return `${Object.keys(value as Record<string, unknown>).length} 个字段`; const text = String(value); return text.length > 120 ? `${text.slice(0, 120)}…` : text }
function formatJson(value: unknown) { if (value == null) return '—'; try { return JSON.stringify(value, null, 2) } catch { return String(value) } }

watch(nodeList, items => { if (items.length && !items.some(item => item.nodeId === selectedNodeId.value)) selectedNodeId.value = items[items.length - 1].nodeId }, { immediate: true })
watch(() => props.streamingOutput[selectedNodeId.value || ''], () => nextTick(() => { if (activeDataTab.value === 'stream' && streamOutputRef.value) streamOutputRef.value.scrollTop = streamOutputRef.value.scrollHeight }))
</script>

<style scoped>
.execution-panel { display: flex; flex-direction: column; min-height: 0; height: 100%; background: var(--tf-bg-surface); }.execution-panel.collapsed .panel-header { border-bottom-color: transparent; }.panel-header { display: flex; align-items: center; justify-content: space-between; min-height: 42px; padding: 0 14px; border-bottom: 1px solid var(--tf-border); cursor: pointer; user-select: none; }.panel-header:hover { background: var(--tf-bg-hover); }.title-cluster, .header-actions { display: flex; align-items: center; gap: 8px; }.panel-title { color: var(--tf-text-primary); font-size: 13px; font-weight: 650; }.trace-count, .toggle-button { color: var(--tf-text-tertiary); font-size: 11px; }.toggle-button { padding: 3px 6px; border: 1px solid transparent; border-radius: 5px; background: transparent; cursor: pointer; transition: color .16s, background .16s; }.toggle-button:hover { color: var(--tf-text-primary); background: var(--tf-bg-hover); }.status-badge, .node-state { padding: 2px 7px; border-radius: 999px; font-size: 11px; font-weight: 600; }.status-badge.running, .node-state.running { color: var(--tf-accent); background: var(--tf-accent-bg); animation: status-pulse 1.7s ease-in-out infinite; }.status-badge.success, .node-state.success { color: var(--tf-success); background: var(--tf-success-bg); }.status-badge.failed, .node-state.failed { color: var(--tf-danger); background: var(--tf-danger-bg); }.status-badge.cancelled, .node-state.cancelled { color: var(--tf-text-secondary); background: var(--tf-bg-elevated); }.close-btn { padding: 2px; border: none; background: transparent; color: var(--tf-text-tertiary); cursor: pointer; }.close-btn:hover { color: var(--tf-text-primary); }.panel-body { display: flex; flex: 1; min-height: 0; overflow: hidden; }.trace-body-enter-active, .trace-body-leave-active { transition: opacity .16s ease, transform .2s ease; }.trace-body-enter-from, .trace-body-leave-to { opacity: 0; transform: translateY(8px); }.node-list { width: 280px; flex-shrink: 0; overflow-y: auto; padding: 8px 0; border-right: 1px solid var(--tf-border); }.node-row { position: relative; display: flex; align-items: center; gap: 7px; min-height: 36px; padding: 0 11px 0 14px; cursor: pointer; color: var(--tf-text-secondary); font-size: 12px; transition: background .16s, color .16s; }.node-row:hover { background: var(--tf-bg-hover); }.node-row.selected { color: var(--tf-text-primary); background: linear-gradient(90deg, var(--tf-accent-bg), transparent); }.timeline-marker { position: relative; z-index: 1; display: grid; width: 16px; height: 16px; place-items: center; border-radius: 50%; background: var(--tf-bg-surface); border: 1px solid var(--tf-border); }.timeline-marker::after { position: absolute; top: 15px; left: 7px; width: 1px; height: 26px; background: var(--tf-border); content: ''; }.node-row:last-child .timeline-marker::after { display: none; }.timeline-marker i { font-size: 10px; font-style: normal; }.node-row.running .timeline-marker { border-color: var(--tf-accent); color: var(--tf-accent); }.node-row.success .timeline-marker { border-color: var(--tf-success); color: var(--tf-success); }.node-row.failed .timeline-marker { border-color: var(--tf-danger); color: var(--tf-danger); }.node-row.skipped .timeline-marker, .node-row.cancelled .timeline-marker { color: var(--tf-text-tertiary); }.node-order { color: var(--tf-text-tertiary); font-size: 10px; }.node-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.node-duration { color: var(--tf-text-tertiary); font-size: 10px; }.node-detail { display: flex; flex: 1; flex-direction: column; min-width: 0; overflow: auto; padding: 14px 16px; }.detail-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.detail-heading > div { display: flex; align-items: baseline; gap: 8px; min-width: 0; }.detail-kicker { color: var(--tf-text-tertiary); font-size: 11px; }.detail-heading strong { overflow: hidden; color: var(--tf-text-primary); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }.detail-stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin: 12px 0; }.detail-stats > div { display: flex; flex-direction: column; gap: 4px; min-width: 0; padding: 9px 10px; border: 1px solid var(--tf-border); border-radius: 8px; background: linear-gradient(135deg, var(--tf-bg-body), var(--tf-bg-surface)); }.detail-stats span { color: var(--tf-text-tertiary); font-size: 10px; }.detail-stats strong { overflow: hidden; color: var(--tf-text-primary); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.data-tabs { display: flex; gap: 3px; border-bottom: 1px solid var(--tf-border); }.data-tabs button { padding: 7px 10px; border: none; border-bottom: 2px solid transparent; background: transparent; color: var(--tf-text-secondary); cursor: pointer; font-size: 12px; }.data-tabs button.active { border-bottom-color: var(--tf-accent); color: var(--tf-accent); font-weight: 600; }.data-content { min-height: 0; padding-top: 10px; animation: content-enter .18s ease-out both; }.field-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 8px; }.field-card { display: flex; flex-direction: column; gap: 5px; min-height: 58px; padding: 10px; border: 1px solid var(--tf-border); border-radius: 8px; background: var(--tf-bg-body); transition: border-color .16s, transform .16s; }.field-card:hover { border-color: var(--tf-accent); transform: translateY(-1px); }.field-card span { color: var(--tf-text-tertiary); font-size: 11px; }.field-card strong { overflow: hidden; color: var(--tf-text-primary); font-size: 12px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }.detail-json { max-height: 360px; margin: 0; padding: 12px; overflow: auto; border: 1px solid var(--tf-border); border-radius: 8px; background: var(--tf-bg-body); color: var(--tf-text-secondary); font-size: 12px; line-height: 1.6; white-space: pre; }.detail-json.streaming { color: var(--tf-streaming); }.error-text { color: var(--tf-danger); }.empty-data { padding: 22px; border: 1px dashed var(--tf-border); border-radius: 8px; color: var(--tf-text-tertiary); text-align: center; font-size: 12px; }.empty-detail { align-items: center; justify-content: center; gap: 6px; color: var(--tf-text-tertiary); font-size: 12px; }.empty-detail strong { color: var(--tf-text-primary); font-size: 13px; }.empty-hint { padding: 18px 16px; color: var(--tf-text-tertiary); font-size: 12px; line-height: 1.7; text-align: center; } @keyframes content-enter { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: translateY(0); } } @keyframes status-pulse { 50% { box-shadow: 0 0 0 4px var(--tf-accent-bg); } } @media (max-width: 900px) { .node-list { width: 220px; }.detail-stats { grid-template-columns: 1fr; } } @media (prefers-reduced-motion: reduce) { .status-badge.running, .node-state.running, .data-content, .trace-body-enter-active, .trace-body-leave-active { animation: none; transition: none; }.node-row, .field-card { transition: none; } }
</style>
