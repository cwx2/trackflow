<template>
  <div class="execution-panel">
    <!-- 面板标题栏 -->
    <div class="panel-header" @click="collapsed = !collapsed">
      <span class="panel-title">
        执行日志
        <span v-if="currentStatus" :class="['status-badge', currentStatus]">
          {{ statusLabel }}
        </span>
      </span>
      <div style="display:flex;align-items:center;gap:8px;">
        <span class="toggle-icon">{{ collapsed ? '▲' : '▼' }}</span>
        <button class="close-btn" @click.stop="emit('close')" title="关闭">✕</button>
      </div>
    </div>

    <!-- 展开内容 -->
    <div v-if="!collapsed" class="panel-body">
      <!-- 节点状态列表 -->
      <div class="node-list">
        <div
          v-for="item in nodeList"
          :key="item.nodeId"
          :class="['node-row', item.status, selectedNodeId === item.nodeId ? 'selected' : '']"
          @click="selectNode(item)"
        >
          <span class="node-status-icon">
            {{ statusIcon(item.status) }}
          </span>
          <span class="node-name">{{ item.nodeName || item.nodeId }}</span>
          <span class="node-duration">{{ item.durationMs ? item.durationMs + 'ms' : '' }}</span>
        </div>
        <div v-if="nodeList.length === 0" class="empty-hint">
          <template v-if="runtimeEnabled">
            工作流运行中，等待触发事件<br />
            <a class="history-link" @click="emit('goHistory')">查看执行历史 →</a>
          </template>
          <template v-else>
            点击「试运行」查看执行日志
          </template>
        </div>
      </div>

      <!-- 选中节点的输入/输出详情 -->
      <div v-if="selectedDetail" class="node-detail">
        <div class="detail-section">
          <div class="detail-label">输入</div>
          <pre class="detail-json">{{ formatJson(selectedDetail.input) }}</pre>
        </div>
        <div class="detail-section">
          <div class="detail-label">输出</div>
          <pre class="detail-json">{{ formatJson(selectedDetail.output) }}</pre>
        </div>
        <div v-if="selectedDetail.errorInfo" class="detail-section error">
          <div class="detail-label">错误</div>
          <pre class="detail-json error-text">{{ selectedDetail.errorInfo }}</pre>
        </div>
        <!-- 流式输出 -->
        <div v-if="streamingOutput[selectedDetail.nodeId]" class="detail-section">
          <div class="detail-label">实时输出</div>
          <pre class="detail-json streaming" ref="streamOutputRef">{{ streamingOutput[selectedDetail.nodeId] }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'

interface NodeEntry {
  nodeId: string
  nodeName?: string
  status: 'idle' | 'running' | 'success' | 'failed' | 'skipped' | 'cancelled'
  durationMs?: number
  input?: unknown
  output?: unknown
  errorInfo?: string
}

const props = defineProps<{
  nodeStatusMap: Record<string, 'idle' | 'running' | 'success' | 'failed' | 'skipped' | 'cancelled'>
  nodeExecutionDetails: Record<string, { input?: unknown; output?: unknown; errorInfo?: string; durationMs?: number; nodeName?: string }>
  streamingOutput: Record<string, string>
  isRunning: boolean
  runtimeEnabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'goHistory'): void
}>()

const collapsed    = ref(false)
const selectedNodeId = ref<string | null>(null)
const streamOutputRef = ref<HTMLPreElement | null>(null)

// 从外部状态构建节点列表
const nodeList = computed<NodeEntry[]>(() =>
  Object.entries(props.nodeStatusMap).map(([nodeId, status]) => ({
    nodeId,
    status,
    ...props.nodeExecutionDetails[nodeId],
  }))
)

const selectedDetail = computed(() =>
  nodeList.value.find(n => n.nodeId === selectedNodeId.value) || null
)

const currentStatus = computed(() => {
  if (props.isRunning) return 'running'
  if (Object.values(props.nodeStatusMap).some(s => s === 'failed')) return 'failed'
  if (Object.values(props.nodeStatusMap).some(s => s === 'cancelled')) return 'cancelled'
  if (Object.values(props.nodeStatusMap).length > 0 &&
      Object.values(props.nodeStatusMap).every(s => s === 'success' || s === 'skipped')) return 'success'
  return null
})

const statusLabel = computed(() => {
  const map: Record<string, string> = {
    running: '运行中...',
    success: '成功',
    failed:  '失败',
    cancelled: '已取消',
  }
  return currentStatus.value ? (map[currentStatus.value] || '') : ''
})

function selectNode(item: NodeEntry) {
  selectedNodeId.value = item.nodeId
}

function statusIcon(status: NodeEntry['status']) {
  const icons: Record<NodeEntry['status'], string> = {
    idle: '○', running: '⏳', success: '✓', failed: '✗', skipped: '−', cancelled: '■',
  }
  return icons[status]
}

function formatJson(val: unknown) {
  if (val == null) return '—'
  try { return JSON.stringify(val, null, 2) }
  catch { return String(val) }
}

// 流式输出自动滚动到底部
watch(() => props.streamingOutput[selectedNodeId.value || ''], () => {
  nextTick(() => {
    if (streamOutputRef.value) {
      streamOutputRef.value.scrollTop = streamOutputRef.value.scrollHeight
    }
  })
})
</script>

<style scoped>
.execution-panel {
  border-top: 1px solid var(--tf-border);
  background: var(--tf-bg-surface);
  display: flex;
  flex-direction: column;
  max-height: 300px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  cursor: pointer;
  user-select: none;
}
.panel-header:hover { background: var(--tf-bg-hover); }

.panel-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--tf-text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
}
.toggle-icon { font-size: 10px; color: var(--tf-text-tertiary); }

.close-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--tf-text-tertiary);
  font-size: 12px;
  padding: 0 2px;
  line-height: 1;
}
.close-btn:hover { color: var(--tf-text-primary); }

.status-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 10px;
  font-weight: 500;
}
.status-badge.running { background: var(--tf-accent-bg); color: var(--tf-accent); }
.status-badge.success { background: var(--tf-success-bg); color: var(--tf-success); }
.status-badge.failed  { background: var(--tf-danger-bg); color: var(--tf-danger); }
.status-badge.cancelled { background: var(--tf-bg-elevated); color: var(--tf-text-secondary); }

.panel-body {
  display: flex;
  flex: 1;
  overflow: hidden;
  min-height: 120px;
}

.node-list {
  width: 200px;
  flex-shrink: 0;
  border-right: 1px solid var(--tf-border);
  overflow-y: auto;
}

.node-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  cursor: pointer;
  font-size: 12px;
  transition: background 0.1s;
  border-left: 2px solid transparent;
}
.node-row:hover           { background: var(--tf-bg-hover); }
.node-row.selected        { background: var(--tf-bg-elevated); border-left-color: var(--tf-accent); }
.node-row.running .node-status-icon { color: var(--tf-accent); }
.node-row.success .node-status-icon { color: var(--tf-success); }
.node-row.failed  .node-status-icon { color: var(--tf-danger); }
.node-row.skipped .node-status-icon,
.node-row.cancelled .node-status-icon { color: var(--tf-text-tertiary); }

.node-status-icon { font-size: 11px; width: 14px; flex-shrink: 0; }
.node-name { flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.node-duration { color: var(--tf-text-tertiary); font-size: 10px; }

.node-detail {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.detail-section { display: flex; flex-direction: column; gap: 4px; }
.detail-label {
  font-size: 10px;
  font-weight: 600;
  color: var(--tf-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.detail-json {
  font-size: 11px;
  color: var(--tf-text-secondary);
  background: var(--tf-bg-body);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  padding: 8px;
  margin: 0;
  max-height: 100px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.detail-json.streaming { max-height: 140px; color: var(--tf-streaming); }
.error-text { color: var(--tf-danger); }

.empty-hint { padding: 16px 10px; text-align: center; color: var(--tf-text-tertiary); font-size: 12px; line-height: 1.8; }
.history-link { color: var(--tf-accent); cursor: pointer; text-decoration: none; }
.history-link:hover { text-decoration: underline; }
</style>
