<template>
  <a-drawer
    :visible="visible"
    title="执行详情"
    :width="720"
    :footer="false"
    unmount-on-close
    @cancel="emit('update:visible', false)"
  >
    <a-spin :loading="loading" class="drawer-spin">
      <div v-if="detail" class="detail-layout">
        <!-- 顶部概览 -->
        <div class="overview-bar">
          <a-tag :color="statusColor(detail.status)" size="medium">
            {{ statusLabel(detail.status) }}
          </a-tag>
          <span class="overview-item">
            <span class="overview-label">开始：</span>{{ formatDateTime(detail.startedAt) }}
          </span>
          <span v-if="detail.durationMs" class="overview-item">
            <span class="overview-label">耗时：</span>{{ formatDurationMs(detail.durationMs) }}
          </span>
          <span v-if="detail.errorMessage" class="overview-item error-msg">
            {{ detail.errorMessage }}
          </span>
        </div>

        <!-- 主体：左侧节点列表 + 右侧详情 -->
        <div class="detail-body">
          <!-- 左侧节点列表 -->
          <div class="node-list">
            <div class="node-list-title">节点执行记录</div>
            <div
              v-for="ne in detail.nodeExecutions"
              :key="ne.id"
              class="node-item"
              :class="{ active: selectedNodeId === ne.nodeId }"
              @click="selectNode(ne.nodeId)"
            >
              <span class="node-status-dot" :class="ne.status" />
              <div class="node-item-info">
                <div class="node-item-name">{{ ne.nodeName || ne.nodeType }}</div>
                <div class="node-item-meta">
                  <a-tag :color="statusColor(ne.status)" size="small">{{ statusLabel(ne.status) }}</a-tag>
                  <span v-if="ne.durationMs" class="node-duration">{{ formatDurationMs(ne.durationMs) }}</span>
                </div>
              </div>
            </div>
            <div v-if="!detail.nodeExecutions?.length" class="no-nodes">暂无节点记录</div>
          </div>

          <!-- 右侧 JSON 详情 -->
          <div class="node-detail">
            <template v-if="selectedNode">
              <div class="detail-section">
                <div class="section-title">输入</div>
                <pre class="json-block">{{ formatJson(selectedNode.input) }}</pre>
              </div>
              <div class="detail-section">
                <div class="section-title">输出</div>
                <pre class="json-block">{{ formatJson(selectedNode.output) }}</pre>
              </div>
              <div v-if="selectedNode.errorInfo" class="detail-section">
                <div class="section-title error-title">错误信息</div>
                <pre class="json-block error-block">{{ selectedNode.errorInfo }}</pre>
              </div>
            </template>
            <div v-else class="no-selection">
              <span>← 点击左侧节点查看详情</span>
            </div>
          </div>
        </div>
      </div>
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import { formatDurationMs } from '@/utils/duration'
import { formatDateTime } from '@/utils/date'
import { ref, watch, computed } from 'vue'
import { Message } from '@arco-design/web-vue'
import { automationApi, type ExecutionDetailVO, type NodeExecutionVO } from '@/api'

const props = defineProps<{
  visible: boolean
  executionId: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
}>()

const loading = ref(false)
const detail = ref<ExecutionDetailVO | null>(null)
const selectedNodeId = ref<string>('')

const selectedNode = computed<NodeExecutionVO | undefined>(
  () => detail.value?.nodeExecutions?.find(n => n.nodeId === selectedNodeId.value)
)

function selectNode(nodeId: string) {
  selectedNodeId.value = nodeId
}

function statusColor(status: string) {
  return { running: 'blue', success: 'green', failed: 'red', cancelled: 'gray', skipped: 'orange' }[status] || 'gray'
}

function statusLabel(status: string) {
  return { running: '运行中', success: '成功', failed: '失败', cancelled: '已取消', skipped: '已跳过' }[status] || status
}





function formatJson(val: unknown): string {
  if (val === null || val === undefined) return '(空)'
  try {
    return JSON.stringify(val, null, 2)
  } catch {
    return String(val)
  }
}

async function loadDetail() {
  if (!props.executionId) return
  loading.value = true
  detail.value = null
  selectedNodeId.value = ''
  try {
    const res = await automationApi.getExecution(props.executionId)
    if (res.code === 0 && res.data) {
      detail.value = res.data
      // 默认选中第一个节点
      if (res.data.nodeExecutions?.length) {
        selectedNodeId.value = res.data.nodeExecutions[0].nodeId
      }
    } else {
      Message.error('加载详情失败')
    }
  } catch {
    Message.error('加载详情失败')
  } finally {
    loading.value = false
  }
}

watch(() => props.visible, (v) => {
  if (v) loadDetail()
})
</script>

<style scoped>
.drawer-spin { width: 100%; min-height: 300px; }

.overview-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 0 16px;
  border-bottom: 1px solid var(--tf-border);
  flex-wrap: wrap;
}

.overview-item { font-size: 13px; color: var(--tf-text-secondary); }
.overview-label { color: var(--tf-text-tertiary); margin-right: 4px; }
.error-msg { color: var(--color-danger-6); font-size: 12px; flex: 1; }

.detail-body {
  display: flex;
  gap: 0;
  margin-top: 16px;
  height: calc(100vh - 200px);
  overflow: hidden;
}

/* 左侧节点列表 */
.node-list {
  width: 220px;
  flex-shrink: 0;
  border-right: 1px solid var(--tf-border);
  overflow-y: auto;
  padding-right: 12px;
}

.node-list-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--tf-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.node-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background 150ms;
}

.node-item:hover { background: var(--tf-bg-hover); }
.node-item.active { background: var(--tf-bg-elevated); }

.node-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 4px;
  flex-shrink: 0;
}
.node-status-dot.running  { background: var(--tf-accent); animation: pulse 1.2s infinite; }
.node-status-dot.success  { background: var(--tf-success); }
.node-status-dot.failed   { background: var(--tf-danger); }
.node-status-dot.skipped  { background: var(--tf-warning); }
.node-status-dot.pending  { background: var(--tf-text-tertiary); }

@keyframes pulse { 0%,100%{opacity:1}50%{opacity:.4} }

.node-item-name {
  font-size: 13px;
  color: var(--tf-text-primary);
  font-weight: 500;
  line-height: 1.3;
}
.node-item-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}
.node-duration { font-size: 11px; color: var(--tf-text-tertiary); }
.no-nodes { font-size: 13px; color: var(--tf-text-tertiary); padding: 16px 0; }

/* 右侧 JSON 详情 */
.node-detail {
  flex: 1;
  overflow-y: auto;
  padding-left: 16px;
}

.detail-section { margin-bottom: 20px; }

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--tf-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.error-title { color: var(--color-danger-6); }

.json-block {
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  padding: 12px;
  font-size: 12px;
  font-family: 'SF Mono', 'Fira Code', monospace;
  color: var(--tf-text-primary);
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  max-height: 300px;
  overflow-y: auto;
}

.error-block {
  color: var(--color-danger-6);
  border-color: var(--color-danger-3);
  background: var(--color-danger-1);
}

.no-selection {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--tf-text-tertiary);
  font-size: 13px;
}
</style>
