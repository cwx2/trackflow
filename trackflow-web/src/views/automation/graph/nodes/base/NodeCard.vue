<template>
  <div
    class="node-card"
    :class="[`status-${runStatus}`, { expanded }]"
    @click.stop="onNodeClick"
  >
    <!-- ── 标题区 ── -->
    <div class="node-header" :style="{ '--node-color': nodeMeta.color }">
      <!-- 左侧彩色条 -->
      <div class="color-bar" />

      <!-- 图标 -->
      <div class="node-icon" :style="{ background: nodeMeta.color + '22' }">
        <span>{{ nodeMeta.icon }}</span>
      </div>

      <!-- 标题 + 描述 -->
      <div class="node-title-wrap">
        <span class="node-title">{{ nodeMeta.title }}</span>
        <span v-if="nodeMeta.description" class="node-desc">{{ nodeMeta.description }}</span>
      </div>

      <!-- 右侧：运行状态图标 + 展开/折叠按钮 -->
      <div class="node-header-actions">
        <span v-if="runStatus === 'running'" class="status-dot running" />
        <span v-else-if="runStatus === 'success'" class="status-icon success">✓</span>
        <span v-else-if="runStatus === 'failed'"  class="status-icon failed">✗</span>

        <!-- 展开/折叠按钮 -->
        <button
          class="expand-btn"
          :title="expanded ? '收起' : '展开'"
          @click.stop="onToggleExpand"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline v-if="!expanded" points="6 9 12 15 18 9" />
            <polyline v-else           points="18 15 12 9 6 15" />
          </svg>
        </button>
      </div>
    </div>

    <!-- ── 端口区（永远显示） ── -->
    <div class="node-body">
      <!-- 输入端口 -->
      <div v-if="inputs.length" class="port-section">
        <div v-for="port in inputs" :key="port.name" class="port-row port-in">
          <div class="port-dot in" />
          <span class="port-label">{{ port.label || port.name }}</span>
          <span class="port-type">{{ typeLabel(port.valueType) }}</span>
        </div>
      </div>
      <!-- 分隔线 -->
      <div v-if="inputs.length && outputs.length" class="port-divider" />
      <!-- 输出端口 -->
      <div v-if="outputs.length" class="port-section">
        <div v-for="port in outputs" :key="port.name" class="port-row port-out">
          <span class="port-type">{{ typeLabel(port.valueType) }}</span>
          <span class="port-label">{{ port.label || port.name }}</span>
          <div class="port-dot out" />
        </div>
      </div>

      <!-- ── 展开追加区（端口下方滑入） ── -->
      <div class="expand-extra" :class="{ visible: expanded }">
        <div class="expand-extra-inner">
          <!-- 输入参数详情 -->
          <section v-if="inputs.length" class="param-section">
            <div class="param-section-header">
              <span class="param-section-title">输入参数</span>
              <button class="add-param-btn">+</button>
            </div>
            <div class="param-table-head">
              <span>参数名称</span>
              <span>参数值</span>
            </div>
            <div v-for="port in inputs" :key="port.name" class="param-row">
              <div class="param-name">
                <div class="port-dot in small" />
                <span>{{ port.name }}</span>
                <span v-if="port.required" class="required-badge">必填</span>
              </div>
              <div class="param-value">
                <span class="param-type-tag">{{ typeLabel(port.valueType) }}</span>
                <button class="param-more">···</button>
              </div>
            </div>
          </section>

          <div class="section-divider" />

          <!-- 输出参数详情 -->
          <section v-if="outputs.length" class="param-section">
            <div class="param-section-header">
              <span class="param-section-title">输出参数</span>
              <button class="add-param-btn">+</button>
            </div>
            <div class="param-table-head">
              <span>参数名称</span>
              <span>参数类型</span>
            </div>
            <div v-for="port in outputs" :key="port.name" class="param-row">
              <div class="param-name">
                <div class="port-dot out small" />
                <span>{{ port.name }}</span>
              </div>
              <div class="param-value">
                <span class="param-type-tag">{{ typeLabel(port.valueType) }}</span>
                <button class="param-more">···</button>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { PortDef, NodeMeta, NodeRunStatus } from './BaseNodeModel'

const props = defineProps<{
  nodeId: string
  properties: Record<string, any>
  onToggleExpand?: () => void
  onNodeClick?: () => void
}>()

const expanded  = computed(() => props.properties?.expanded ?? false)
const runStatus = computed<NodeRunStatus>(() => props.properties?.runStatus ?? 'idle')
const inputs    = computed<PortDef[]>(() => props.properties?.inputs  ?? [])
const outputs   = computed<PortDef[]>(() => props.properties?.outputs ?? [])
const nodeMeta  = computed<NodeMeta>(() => props.properties?.nodeMeta ?? {
  title: '节点', icon: '⬡', color: '#6366f1', description: '',
})

const TYPE_LABELS: Record<string, string> = {
  string: '文本', number: '数字', boolean: '布尔',
  object: '对象', array: '数组', any: '任意',
}
function typeLabel(t?: string) {
  return t ? (TYPE_LABELS[t] || t) : ''
}

function onToggleExpand() {
  props.onToggleExpand?.()
}

function onNodeClick() {
  props.onNodeClick?.()
}
</script>

<style scoped>
/* ── 卡片容器 ── */
.node-card {
  width: 280px;
  min-height: 80px;
  height: 100%;           /* 填满 foreignObject */
  border-radius: 10px;
  background: var(--wf-node-bg);
  border: 1.5px solid var(--wf-node-border);
  box-shadow: var(--wf-node-shadow);
  overflow: hidden;
  cursor: pointer;
  transition: border-color 150ms, box-shadow 150ms;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  position: relative;
  box-sizing: border-box;
}

.node-card:hover {
  border-color: var(--wf-node-border-hover);
  box-shadow: var(--wf-node-shadow-hover);
}

/* 运行状态 */
.node-card.status-running { box-shadow: var(--wf-glow-running); border-color: var(--wf-status-running); }
.node-card.status-success  { box-shadow: var(--wf-glow-success); border-color: var(--wf-status-success); }
.node-card.status-failed   { box-shadow: var(--wf-glow-failed);  border-color: var(--wf-status-failed); }

/* ── 标题区（sticky，展开时头部不动） ── */
.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 10px 10px 0;
  border-bottom: 1px solid var(--wf-node-header-border);
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--wf-node-bg);
  /* 确保圆角不被遮住 */
  border-radius: 10px 10px 0 0;
}

.color-bar {
  width: 4px;
  align-self: stretch;
  border-radius: 0 2px 2px 0;
  background: var(--node-color, #6366f1);
  flex-shrink: 0;
}

.node-icon {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  flex-shrink: 0;
}

.node-title-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.node-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--wf-node-title);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-desc {
  font-size: 11px;
  color: var(--wf-node-subtitle);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  padding-right: 6px;
}

/* 运行状态指示 */
.status-dot.running {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--wf-status-running);
  animation: pulse-dot 1s infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50%       { opacity: 0.5; transform: scale(0.85); }
}

.status-icon {
  font-size: 13px;
  font-weight: 700;
}
.status-icon.success { color: var(--wf-status-success); }
.status-icon.failed  { color: var(--wf-status-failed); }

.expand-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  background: none;
  border-radius: 4px;
  cursor: pointer;
  color: var(--wf-port-label);
  transition: background 150ms, color 150ms;
}
.expand-btn:hover {
  background: var(--wf-node-border);
  color: var(--wf-node-title);
}

/* ── 内容区 ── */
.node-body {
  overflow: hidden;
}

/* ── 展开追加区（端口下方滑入） ── */
.expand-extra {
  max-height: 0;
  opacity: 0;
  overflow: hidden;
  transition:
    max-height 300ms cubic-bezier(0.4, 0, 0.2, 1),
    opacity    200ms ease 50ms;
}

.expand-extra.visible {
  max-height: 480px;
  opacity: 1;
}

.expand-extra-inner {
  overflow-y: auto;
  max-height: 480px;
  scrollbar-width: thin;
}

/* ── 端口区（永远显示） ── */
.port-section {
  padding: 4px 0;
}

.port-divider {
  height: 1px;
  background: var(--wf-node-header-border);
  margin: 0;
}

.port-row {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 10px;
}

.port-row.port-out {
  justify-content: flex-end;
}

.port-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.port-dot.in  { background: var(--wf-port-in);  }
.port-dot.out { background: var(--wf-port-out); }
.port-dot.small { width: 6px; height: 6px; }

.port-label {
  font-size: 11px;
  color: var(--wf-port-label);
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.port-row.port-out .port-label { text-align: right; }

.port-type {
  font-size: 10px;
  color: var(--wf-port-type);
  flex-shrink: 0;
}

/* ── 展开态参数块 ── */

.param-section {
  padding: 10px 12px;
}

.param-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.param-section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--wf-node-title);
}

.add-param-btn {
  width: 18px;
  height: 18px;
  border: none;
  background: none;
  color: var(--wf-port-label);
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 3px;
  line-height: 1;
  padding: 0;
}
.add-param-btn:hover {
  background: var(--wf-node-border);
  color: var(--wf-node-title);
}

.param-table-head {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 0 4px 4px;
  font-size: 11px;
  color: var(--wf-port-type);
  border-bottom: 1px solid var(--wf-node-header-border);
  margin-bottom: 4px;
}

.param-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 5px 4px;
  border-radius: 4px;
  transition: background 150ms;
}
.param-row:hover { background: var(--wf-node-border); }

.param-name {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--wf-node-title);
  min-width: 0;
  overflow: hidden;
}

.param-name span {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.required-badge {
  font-size: 10px;
  color: var(--wf-status-failed);
  flex-shrink: 0;
}

.param-value {
  display: flex;
  align-items: center;
  gap: 4px;
  justify-content: flex-end;
}

.param-type-tag {
  font-size: 11px;
  color: var(--wf-port-label);
  background: var(--wf-node-border);
  padding: 2px 6px;
  border-radius: 4px;
}

.param-more {
  width: 20px;
  height: 20px;
  border: none;
  background: none;
  color: var(--wf-port-label);
  cursor: pointer;
  border-radius: 3px;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  letter-spacing: -2px;
}
.param-more:hover {
  background: var(--wf-node-border);
  color: var(--wf-node-title);
}

.section-divider {
  height: 1px;
  background: var(--wf-node-header-border);
  margin: 0 12px;
}
</style>
