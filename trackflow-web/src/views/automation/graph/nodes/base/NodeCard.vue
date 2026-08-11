<template>
  <div
    class="node-card"
    :class="`status-${runStatus}`"
    :style="{ '--node-color': nodeMeta.color }"
    @click.stop="onNodeClick"
  >
    <!-- ── 标题区 ── -->
    <div class="node-header">
      <span class="flow-port flow-port-in" title="流程入口：连接上一个节点" aria-label="流程入口" />
      <div class="color-bar" />
      <div class="node-icon" :style="{ background: nodeMeta.color + '22' }">
        <component
          v-if="ICON_MAP[nodeMeta.icon]"
          :is="ICON_MAP[nodeMeta.icon]"
          :size="16"
          :style="{ color: nodeMeta.color }"
        />
        <span v-else>{{ nodeMeta.icon }}</span>
      </div>
      <div class="node-title-wrap">
        <span class="node-title">{{ nodeMeta.title }}</span>
        <span v-if="nodeMeta.description" class="node-desc">{{ nodeMeta.description }}</span>
      </div>
      <div class="node-header-actions">
        <!-- 运行状态指示 -->
        <span v-if="runStatus === 'running'" class="status-dot running" />
        <span v-else-if="runStatus === 'success'" class="status-icon success">✓</span>
        <span v-else-if="runStatus === 'failed'"  class="status-icon failed">✗</span>
        <span v-else-if="runStatus === 'skipped'" class="status-icon skipped">−</span>
        <span v-else-if="runStatus === 'cancelled'" class="status-icon cancelled">■</span>
        <!-- hover 操作菜单 -->
        <div class="hover-actions">
          <button class="hover-btn" title="试运行此节点" @click.stop="emit('run-node')">
            <IconPlayArrow :size="14" />
          </button>
          <div class="hover-more-wrap" ref="moreMenuRef">
            <button class="hover-btn" title="更多操作" @click.stop="moreMenuOpen = !moreMenuOpen">
              <IconMore :size="14" />
            </button>
            <div v-if="moreMenuOpen" class="more-menu" @click.stop>
              <button class="more-menu-item" @click="onMenuAction('rename')">重命名</button>
              <button class="more-menu-item" @click="onMenuAction('duplicate')">创建副本</button>
              <button class="more-menu-item danger" @click="onMenuAction('delete')">删除</button>
              <div class="more-menu-divider" />
              <button class="more-menu-item" @click="onMenuAction('help')">
                帮助文档<span class="help-icon">?</span>
              </button>
            </div>
          </div>
        </div>
      </div>
      <span class="flow-port flow-port-out" title="流程出口：连接下一个节点" aria-label="流程出口" />
    </div>

    <!-- ── 端口区 ── -->
    <div class="node-body">
      <!-- 输入端口（只显示非 optional 的，或 optionalExpanded 时全显） -->
      <div v-if="visibleInputs.length" class="port-section">
        <div v-for="port in visibleInputs" :key="port.name" class="port-row port-in">
          <div class="port-dot in" />
          <span class="port-label">{{ port.label || port.name }}</span>
          <span class="port-type">{{ typeLabel(port.valueType) }}</span>
        </div>
      </div>
      <!-- 可选端口折叠提示 -->
      <div
        v-if="hiddenOptionalCount > 0 && !optionalExpanded"
        class="optional-toggle"
        @click.stop="setOptionalExpanded(true)"
      >
        <span>+ {{ hiddenOptionalCount }} 个可选参数</span>
      </div>
      <!-- 展开的可选输入端口 -->
      <div v-if="optionalExpanded && optionalInputs.length" class="port-section optional-ports">
        <div v-for="port in optionalInputs" :key="port.name" class="port-row port-in">
          <div class="port-dot in" />
          <span class="port-label">{{ port.label || port.name }}</span>
          <span class="port-type">{{ typeLabel(port.valueType) }}</span>
          <button
            class="port-collapse-btn"
            title="收起此参数"
            @click.stop="setOptionalExpanded(false)"
          >×</button>
        </div>
        <div class="optional-toggle collapse" @click.stop="setOptionalExpanded(false)">
          <span>收起可选参数</span>
        </div>
      </div>
      <!-- 输入/输出分隔线 -->
      <div
        v-if="(visibleInputs.length || optionalExpanded) && outputs.length"
        class="port-divider"
      />
      <!-- 输出端口 -->
      <div v-if="outputs.length" class="port-section">
        <div v-for="port in outputs" :key="port.name" class="port-row port-out">
          <span class="port-type">{{ typeLabel(port.valueType) }}</span>
          <span class="port-label">{{ port.label || port.name }}</span>
          <div class="port-dot out" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import {
  IconSearch, IconCode, IconLink,
  IconSync, IconSend, IconClockCircle, IconList, IconRobot,
  IconBranch, IconLoop, IconStorage, IconApps, IconCheck,
  IconEdit, IconMessage, IconPlayCircle, IconRecordStop,
  IconPlayArrow, IconMore,
} from '@arco-design/web-vue/es/icon'
import type { PortDef, NodeMeta, NodeRunStatus } from './BaseNodeModel'
import { getNodeDefinition } from '../../../node-definitions'

/** icon 字段 → Arco 图标组件映射（不在此表的直接渲染文本） */
const ICON_MAP: Record<string, any> = {
  '🔎': IconSearch, '🔍': IconSearch,
  '📋': IconList,   '📝': IconEdit,
  '🔁': IconSync,   '💬': IconMessage,
  '✏️': IconEdit,   '🔀': IconBranch,
  '🔄': IconLoop,   '⏱': IconClockCircle,
  '📁': IconStorage,'🌐': IconSend,
  '🔗': IconLink,   '📦': IconApps,
  '🛡️': IconCheck,  '🤖': IconRobot,
  '🧠': IconRobot,  '▶️': IconPlayCircle,
  '⏹️': IconRecordStop, '</>': IconCode,
}

const props = defineProps<{
  nodeId: string
  properties: Record<string, any>
  onNodeClick?: () => void
  onSetProperty?: (key: string, val: any) => void
}>()

const emit = defineEmits<{
  'run-node': []
  'menu-action': [action: string, nodeId: string]
}>()

const runStatus = computed<NodeRunStatus>(() => props.properties?.runStatus ?? 'idle')
const inputs    = computed<PortDef[]>(() => props.properties?.inputs  ?? [])
const outputs   = computed<PortDef[]>(() => props.properties?.outputs ?? [])
const nodeMeta  = computed<NodeMeta>(() => props.properties?.nodeMeta ?? {
  title: '节点', icon: '⬡', color: '#6366f1', description: '',
})

// ── 可选端口折叠逻辑 ──
const optionalExpanded = computed(() => props.properties?.optionalExpanded ?? false)

function setOptionalExpanded(val: boolean) {
  props.onSetProperty?.('optionalExpanded', val)
}

const nodeDefinition = computed(() => {
  const nodeType = props.properties?.nodeType
  return nodeType ? getNodeDefinition(nodeType) : undefined
})

/** 非可选的输入端口（始终显示） */
const visibleInputs = computed<PortDef[]>(() => {
  const def = nodeDefinition.value
  if (!def) return inputs.value
  const optionalNames = new Set(def.inputPorts.filter(p => p.optional).map(p => p.name))
  if (optionalNames.size === 0) return inputs.value
  return inputs.value.filter(p => !optionalNames.has(p.name))
})

/** 可选的输入端口 */
const optionalInputs = computed<PortDef[]>(() => {
  const def = nodeDefinition.value
  if (!def) return []
  const optionalNames = new Set(def.inputPorts.filter(p => p.optional).map(p => p.name))
  return inputs.value.filter(p => optionalNames.has(p.name))
})

const hiddenOptionalCount = computed(() => optionalInputs.value.length)

// ── 更多菜单 ──
const moreMenuOpen = ref(false)
const moreMenuRef  = ref<HTMLElement | null>(null)

function onMenuAction(action: string) {
  moreMenuOpen.value = false
  emit('menu-action', action, props.nodeId)
  props.onNodeClick?.()
}

function handleDocClick(e: MouseEvent) {
  if (moreMenuRef.value && !moreMenuRef.value.contains(e.target as Node)) {
    moreMenuOpen.value = false
  }
}

onMounted(() => document.addEventListener('click', handleDocClick))
onUnmounted(() => document.removeEventListener('click', handleDocClick))

const TYPE_LABELS: Record<string, string> = {
  string: '文本', number: '数字', boolean: '布尔',
  object: '对象', array: '数组', any: '任意',
}
function typeLabel(t?: string) {
  return t ? (TYPE_LABELS[t] || t) : ''
}

function onNodeClick() {
  props.onNodeClick?.()
}
</script>

<style scoped>
/* ── 卡片容器 ── */
.node-card {
  width: 280px;
  height: fit-content;
  border-radius: 10px;
  background: var(--wf-node-bg);
  border: 1px solid var(--wf-node-border);
  box-shadow: var(--wf-node-shadow);
  /* 让端口圆点伸出卡片边界，作为线条与参数的清晰连接点。 */
  overflow: visible;
  cursor: pointer;
  transition: transform 150ms ease, border-color 150ms ease, box-shadow 150ms ease, background 150ms ease;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  position: relative;
  box-sizing: border-box;
}

.node-card:hover {
  transform: translateY(-1px);
  border-color: var(--wf-node-border-hover);
  box-shadow: var(--wf-node-shadow-hover);
}

.node-card:focus-within {
  border-color: color-mix(in srgb, var(--node-color) 70%, var(--wf-node-border));
}

/* ── 运行状态 ── */
.node-card.status-running  { box-shadow: var(--wf-glow-running); border-color: var(--wf-status-running); }
.node-card.status-success  { box-shadow: var(--wf-glow-success); border-color: var(--wf-status-success); }
.node-card.status-failed   { box-shadow: var(--wf-glow-failed);  border-color: var(--wf-status-failed); }
.node-card.status-skipped  { opacity: 0.58; border-style: dashed; }
.node-card.status-cancelled { opacity: 0.72; border-style: dashed; }

/* ── 标题区 ── */
.node-header {
  display: flex;
  align-items: center;
  box-sizing: border-box;
  height: 49px;
  gap: 8px;
  padding: 10px 10px 10px 0;
  border-bottom: 1px solid var(--wf-node-header-border);
  border-radius: 10px 10px 0 0;
  background: linear-gradient(90deg, color-mix(in srgb, var(--node-color) 8%, var(--wf-node-bg)), var(--wf-node-bg) 54%);
}

.color-bar {
  width: 3px;
  align-self: stretch;
  border-radius: 0 3px 3px 0;
  background: var(--node-color, #6366f1);
  flex-shrink: 0;
}

.node-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
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

/* ── hover 操作区 ── */
.hover-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  opacity: 0;
  transition: opacity 150ms;
  flex-shrink: 0;
}

.node-card:hover .hover-actions {
  opacity: 1;
}

.hover-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  background: color-mix(in srgb, var(--wf-node-border) 86%, transparent);
  border-radius: 5px;
  cursor: pointer;
  color: var(--wf-port-label);
  transition: background 150ms, color 150ms;
  flex-shrink: 0;
}
.hover-btn:hover {
  background: var(--wf-node-border-hover);
  color: var(--wf-node-title);
}

.hover-more-wrap {
  position: relative;
}

.more-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  background: var(--wf-node-bg);
  border: 1px solid var(--wf-node-border);
  border-radius: 8px;
  padding: 4px 0;
  min-width: 120px;
  box-shadow: var(--tf-shadow-lg);
  z-index: 100;
  white-space: nowrap;
}

.more-menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 7px 14px;
  border: none;
  background: none;
  text-align: left;
  font-size: 13px;
  color: var(--wf-node-title);
  cursor: pointer;
  transition: background 150ms;
}
.more-menu-item:hover     { background: var(--wf-node-border); }
.more-menu-item.danger    { color: var(--wf-status-failed); }
.more-menu-item.danger:hover { background: var(--tf-danger-bg); }

.more-menu-divider {
  height: 1px;
  background: var(--wf-node-border);
  margin: 4px 0;
}

.help-icon {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 1px solid var(--wf-port-label);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: var(--wf-port-label);
}

/* ── 运行状态指示 ── */
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
.status-icon.success  { color: var(--wf-status-success); }
.status-icon.failed   { color: var(--wf-status-failed); }
.status-icon.skipped,
.status-icon.cancelled { color: var(--wf-port-type); }

/* ── 端口区 ── */
.node-body {
  overflow: visible;
}

.port-section {
  padding: 4px 0;
}

.port-divider {
  height: 1px;
  background: var(--wf-node-header-border);
}

.port-row {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 10px;
  position: relative;
  transition: background 120ms ease;
}

.port-row:hover { background: color-mix(in srgb, var(--node-color) 6%, transparent); }

.port-row.port-out {
  justify-content: flex-end;
}

.port-dot {
  /*
   * 端口的圆心必须等于 BaseNodeModel 中的锚点：
   * 卡片边缘 +/- PORT_HANDLE_OFFSET（6px）以及端口行的垂直中线。
   * 使用绝对定位，不能再通过 flex + 负 margin 估算位置。
   */
  position: absolute;
  /* NodeCard 的 1px 外边框使内容盒较模型坐标下移 1px。 */
  top: calc(50% - 1px);
  z-index: 3;
  box-sizing: border-box;
  width: 14px;
  height: 14px;
  border: 2px solid currentColor;
  border-radius: 50%;
  background: transparent;
  color: var(--wf-port-in);
  pointer-events: none;
  transform: translateY(-50%);
  transition: transform 120ms ease, box-shadow 120ms ease, background 120ms ease;
}

/* 标题栏端口只表示执行顺序；参数区端口只表示数据绑定。 */
.flow-port {
  position: absolute;
  top: 17px;
  z-index: 4;
  box-sizing: border-box;
  width: 14px;
  height: 14px;
  border: 2px solid var(--wf-flow-port, #94a3b8);
  border-radius: 50%;
  background: var(--wf-node-bg);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--wf-flow-port, #94a3b8) 36%, transparent);
  pointer-events: none;
  transition: transform 120ms ease, box-shadow 120ms ease;
}
.flow-port-in { left: -14px; }
.flow-port-out { right: -14px; }
.node-card:hover .flow-port {
  transform: scale(1.14);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--wf-flow-port, #94a3b8) 18%, transparent);
}

/* 输入/输出均为空心插座；颜色表达数据进入与流出方向。 */
.port-dot.in {
  color: var(--wf-port-in);
  left: -14px; /* 内容盒从外边框内侧起算，补偿 1px 后圆心精确落在锚点。 */
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--wf-port-in) 36%, transparent);
}

/* 输出端口以橙色空心环与输入端口区分。 */
.port-dot.out {
  color: var(--wf-port-out);
  right: -14px; /* 同理，右侧向外补偿 1px。 */
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--wf-port-out) 36%, transparent);
}
.port-row:hover .port-dot {
  transform: translateY(-50%) scale(1.14);
  box-shadow: 0 0 0 3px color-mix(in srgb, currentColor 18%, transparent);
}
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
  line-height: 16px;
  padding: 0 5px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--wf-port-type) 9%, transparent);
}

/* ── 可选端口折叠区 ── */
.optional-toggle {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  font-size: 11px;
  color: var(--wf-port-label);
  cursor: pointer;
  transition: color 150ms;
  user-select: none;
}
.optional-toggle:hover { color: var(--wf-node-title); }
.optional-toggle.collapse { padding-top: 0; }

.optional-ports {
  border-left: 2px dashed var(--wf-node-border-hover);
  margin-left: 6px;
}

.port-collapse-btn {
  width: 16px;
  height: 16px;
  border: none;
  background: none;
  color: var(--wf-port-label);
  cursor: pointer;
  font-size: 12px;
  border-radius: 3px;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 150ms, background 150ms;
  flex-shrink: 0;
}
.port-row:hover .port-collapse-btn { opacity: 1; }
.port-collapse-btn:hover { background: var(--wf-node-border); color: var(--wf-status-failed); }
</style>
