<template>
  <div
    class="node-card"
    :class="[`status-${runStatus}`, { expanded }]"
    @click.stop="onNodeClick"
  >
    <!-- ── 标题区 ── -->
    <div class="node-header" :style="{ '--node-color': nodeMeta.color }">
      <div class="color-bar" />
      <div class="node-icon" :style="{ background: nodeMeta.color + '22' }">
        <span>{{ nodeMeta.icon }}</span>
      </div>
      <div class="node-title-wrap">
        <span class="node-title">{{ nodeMeta.title }}</span>
        <span v-if="nodeMeta.description" class="node-desc">{{ nodeMeta.description }}</span>
      </div>
      <div class="node-header-actions">
        <span v-if="runStatus === 'running'" class="status-dot running" />
        <span v-else-if="runStatus === 'success'" class="status-icon success">✓</span>
        <span v-else-if="runStatus === 'failed'"  class="status-icon failed">✗</span>
        <span v-else-if="runStatus === 'skipped'" class="status-icon skipped">−</span>
        <span v-else-if="runStatus === 'cancelled'" class="status-icon cancelled">■</span>
        <div class="hover-actions">
          <button class="hover-btn" title="运行此节点" @click.stop="emit('run-node')">
            <icon-play-arrow-fill :size="12" />
          </button>
          <div class="hover-more-wrap" ref="moreMenuRef">
            <button class="hover-btn" title="更多操作" @click.stop="moreMenuOpen = !moreMenuOpen">
              <icon-more :size="14" />
            </button>
            <div v-if="moreMenuOpen" class="more-menu" @click.stop>
              <button class="more-menu-item" @click="onMenuAction('rename')">重命名</button>
              <button class="more-menu-item" @click="onMenuAction('duplicate')">创建副本</button>
              <button class="more-menu-item danger" @click="onMenuAction('delete')">删除</button>
              <div class="more-menu-divider" />
              <button class="more-menu-item" @click="onMenuAction('help')">帮助文档<span class="help-icon">?</span></button>
            </div>
          </div>
        </div>
        <button class="expand-btn" :title="expanded ? '收起' : '展开'" @click.stop="onToggleExpand">
          <icon-up v-if="expanded" :size="14" />
          <icon-down v-else :size="14" />
        </button>
      </div>
    </div>

    <!-- ── 端口区（永远显示，但只显示非折叠端口） ── -->
    <div class="node-body">
      <!-- 输入端口（只显示 visible 的） -->
      <div v-if="visibleInputs.length" class="port-section">
        <div v-for="port in visibleInputs" :key="port.name" class="port-row port-in">
          <div class="port-dot in" />
          <span class="port-label">{{ port.label || port.name }}</span>
          <span class="port-type">{{ typeLabel(port.valueType) }}</span>
        </div>
      </div>
      <!-- 可选端口折叠提示 -->
      <div v-if="hiddenOptionalCount > 0 && !optionalExpanded" class="optional-toggle" @click.stop="optionalExpanded = true">
        <span>+ {{ hiddenOptionalCount }} 个可选参数</span>
      </div>
      <!-- 展开的可选输入端口 -->
      <div v-if="optionalExpanded && optionalInputs.length" class="port-section optional-ports">
        <div v-for="port in optionalInputs" :key="port.name" class="port-row port-in">
          <div class="port-dot in" />
          <span class="port-label">{{ port.label || port.name }}</span>
          <span class="port-type">{{ typeLabel(port.valueType) }}</span>
          <button class="port-collapse-btn" title="收起此参数" @click.stop="removeOptionalPort(port.name)">×</button>
        </div>
        <div class="optional-toggle collapse" @click.stop="optionalExpanded = false">
          <span>收起可选参数</span>
        </div>
      </div>
      <!-- 分隔线 -->
      <div v-if="(visibleInputs.length || optionalExpanded) && outputs.length" class="port-divider" />
      <!-- 输出端口 -->
      <div v-if="outputs.length" class="port-section">
        <div v-for="port in outputs" :key="port.name" class="port-row port-out">
          <span class="port-type">{{ typeLabel(port.valueType) }}</span>
          <span class="port-label">{{ port.label || port.name }}</span>
          <div class="port-dot out" />
        </div>
      </div>

      <!-- ── 展开追加区 ── -->
      <div class="expand-extra" :class="{ visible: expanded }">
        <div class="expand-extra-inner">
          <!-- 输入参数详情 -->
          <section v-if="allInputs.length" class="param-section">
            <div class="param-section-header">
              <span class="param-section-title">输入参数</span>
              <button class="add-param-btn" title="添加输入端口" @click.stop="showAddPortDialog('input')">+</button>
            </div>
            <div class="param-table-head">
              <span>参数名称</span>
              <span>参数值</span>
            </div>
            <div v-for="port in allInputs" :key="port.name" class="param-row">
              <div class="param-name">
                <div class="port-dot in small" />
                <span>{{ port.name }}</span>
                <span v-if="port.required" class="required-badge">必填</span>
                <span v-else-if="port.optional" class="optional-badge">可选</span>
              </div>
              <div class="param-value">
                <span class="param-type-tag">{{ typeLabel(port.valueType) }}</span>
                <button v-if="!port.required && isCustomPort(port.name)" class="param-delete" title="删除端口" @click.stop="removePort('input', port.name)">×</button>
                <button v-else class="param-more">···</button>
              </div>
            </div>
          </section>

          <div class="section-divider" />

          <!-- 输出参数详情 -->
          <section v-if="outputs.length" class="param-section">
            <div class="param-section-header">
              <span class="param-section-title">输出参数</span>
              <button class="add-param-btn" title="添加输出端口" @click.stop="showAddPortDialog('output')">+</button>
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
                <button v-if="isCustomPort(port.name)" class="param-delete" title="删除端口" @click.stop="removePort('output', port.name)">×</button>
                <button v-else class="param-more">···</button>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>

    <!-- ── 添加端口弹窗 ── -->
    <div v-if="addPortVisible" class="add-port-dialog" @click.stop>
      <div class="add-port-title">添加{{ addPortType === 'input' ? '输入' : '输出' }}端口</div>
      <div class="add-port-field">
        <label>端口名称</label>
        <input v-model="newPortName" placeholder="例如: myVariable" class="add-port-input" />
      </div>
      <div class="add-port-field">
        <label>类型</label>
        <select v-model="newPortType" class="add-port-select">
          <option value="string">文本</option>
          <option value="number">数字</option>
          <option value="boolean">布尔</option>
          <option value="object">对象</option>
          <option value="array">数组</option>
          <option value="any">任意</option>
        </select>
      </div>
      <div v-if="addPortType === 'input'" class="add-port-field">
        <label><input type="checkbox" v-model="newPortRequired" /> 必填</label>
      </div>
      <div class="add-port-actions">
        <button class="add-port-confirm" @click.stop="confirmAddPort">确认</button>
        <button class="add-port-cancel" @click.stop="addPortVisible = false">取消</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import type { PortDef, NodeMeta, NodeRunStatus } from './BaseNodeModel'
import type { ValueType } from '@/api/automation'
import { getNodeDefinition } from '../../../node-definitions'

const props = defineProps<{
  nodeId: string
  properties: Record<string, any>
  onToggleExpand?: () => void
  onNodeClick?: () => void
}>()

const emit = defineEmits<{
  'run-node': []
  'menu-action': [action: string, nodeId: string]
}>()

const expanded    = computed(() => props.properties?.expanded ?? false)
const runStatus   = computed<NodeRunStatus>(() => props.properties?.runStatus ?? 'idle')
const inputs      = computed<PortDef[]>(() => props.properties?.inputs  ?? [])
const outputs     = computed<PortDef[]>(() => props.properties?.outputs ?? [])
const nodeMeta    = computed<NodeMeta>(() => props.properties?.nodeMeta ?? {
  title: '节点', icon: '⬡', color: '#6366f1', description: '',
})

// ── 可选端口折叠逻辑 ──
const optionalExpanded = ref(false)

/** 获取节点定义中的 optional 标记 */
const nodeDefinition = computed(() => {
  const nodeType = props.properties?.nodeType
  return nodeType ? getNodeDefinition(nodeType) : undefined
})

/** 非可选的输入端口（始终显示） */
const visibleInputs = computed<PortDef[]>(() => {
  const def = nodeDefinition.value
  if (!def) return inputs.value
  const optionalNames = new Set(
    def.inputPorts.filter(p => p.optional).map(p => p.name)
  )
  return inputs.value.filter(p => !optionalNames.has(p.name))
})

/** 可选的输入端口（折叠时隐藏） */
const optionalInputs = computed<PortDef[]>(() => {
  const def = nodeDefinition.value
  if (!def) return []
  const optionalNames = new Set(
    def.inputPorts.filter(p => p.optional).map(p => p.name)
  )
  return inputs.value.filter(p => optionalNames.has(p.name))
})

/** 隐藏的可选端口数量 */
const hiddenOptionalCount = computed(() => optionalInputs.value.length)

/** 所有输入端口（展开态参数列表用） */
const allInputs = computed<PortDef[]>(() => inputs.value)

/** 判断端口是否为用户自定义新增（非节点定义中声明的） */
function isCustomPort(portName: string): boolean {
  const def = nodeDefinition.value
  if (!def) return true
  const builtinInputs = def.inputPorts.map(p => p.name)
  const builtinOutputs = def.outputPorts.map(p => p.name)
  return !builtinInputs.includes(portName) && !builtinOutputs.includes(portName)
}

/** 移除可选端口（从视图隐藏） */
function removeOptionalPort(_portName: string) {
  // 如果所有可选端口都被单独收起，则关闭可选区
  optionalExpanded.value = false
}

// ── 动态端口增删 ──
const addPortVisible = ref(false)
const addPortType = ref<'input' | 'output'>('input')
const newPortName = ref('')
const newPortType = ref<ValueType>('string')
const newPortRequired = ref(false)

function showAddPortDialog(type: 'input' | 'output') {
  addPortType.value = type
  newPortName.value = ''
  newPortType.value = 'string'
  newPortRequired.value = false
  addPortVisible.value = true
}

function confirmAddPort() {
  if (!newPortName.value.trim()) return
  addPortVisible.value = false
  // Emit event for the parent model to handle adding the port
  emit('menu-action', `add-${addPortType.value}-port`, JSON.stringify({
    name: newPortName.value.trim(),
    valueType: newPortType.value,
    required: newPortRequired.value,
  }))
}

function removePort(type: 'input' | 'output', portName: string) {
  emit('menu-action', `remove-${type}-port`, portName)
}

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
  height: fit-content;
  border-radius: 10px;
  background: var(--wf-node-bg);
  border: 1.5px solid var(--wf-node-border);
  box-shadow: var(--wf-node-shadow);
  overflow: hidden;       /* 让 border-radius 裁切子元素 */
  cursor: pointer;
  transition: border-color 150ms, box-shadow 150ms;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  position: relative;
  box-sizing: border-box;
}

/* 头部需要圆角（overflow:visible 后头部自己负责顶部圆角） */
.node-header {
  border-radius: 8px 8px 0 0;
}

.node-card:hover {
  border-color: var(--wf-node-border-hover);
  box-shadow: var(--wf-node-shadow-hover);
}

/* 运行状态 */
.node-card.status-running { box-shadow: var(--wf-glow-running); border-color: var(--wf-status-running); }
.node-card.status-success  { box-shadow: var(--wf-glow-success); border-color: var(--wf-status-success); }
.node-card.status-failed   { box-shadow: var(--wf-glow-failed);  border-color: var(--wf-status-failed); }
.node-card.status-skipped  { opacity: 0.58; border-style: dashed; }
.node-card.status-cancelled { opacity: 0.72; border-style: dashed; }

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

/* ── hover 操作区（默认隐藏，hover 卡片时显示） ── */
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
  background: var(--wf-node-border);
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

/* 更多菜单容器 */
.hover-more-wrap {
  position: relative;
}

/* 下拉菜单 */
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
.more-menu-item:hover      { background: var(--wf-node-border); }
.more-menu-item.danger     { color: var(--wf-status-failed); }
.more-menu-item.danger:hover { background: rgba(239,68,68,0.1); }

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
.status-icon.success { color: var(--wf-status-success); }
.status-icon.failed  { color: var(--wf-status-failed); }
.status-icon.skipped,
.status-icon.cancelled { color: var(--wf-port-type); }

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

/* ── 可选端口折叠区 ── */
.optional-toggle {
  padding: 4px 10px;
  font-size: 11px;
  color: var(--wf-port-label);
  cursor: pointer;
  transition: color 150ms;
  user-select: none;
}
.optional-toggle:hover {
  color: var(--wf-node-title);
}
.optional-toggle.collapse {
  padding-top: 2px;
}

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

.optional-badge {
  font-size: 10px;
  color: var(--wf-port-label);
  flex-shrink: 0;
}

/* ── 添加端口弹窗 ── */
.add-port-dialog {
  position: absolute;
  bottom: 4px;
  left: 50%;
  transform: translateX(-50%);
  background: var(--wf-node-bg);
  border: 1px solid var(--wf-node-border-hover);
  border-radius: 8px;
  padding: 12px;
  min-width: 200px;
  box-shadow: var(--tf-shadow-lg);
  z-index: 200;
}

.add-port-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--wf-node-title);
  margin-bottom: 10px;
}

.add-port-field {
  margin-bottom: 8px;
}
.add-port-field label {
  display: block;
  font-size: 11px;
  color: var(--wf-port-label);
  margin-bottom: 3px;
}

.add-port-input,
.add-port-select {
  width: 100%;
  height: 28px;
  border: 1px solid var(--wf-node-border);
  border-radius: 4px;
  background: var(--wf-node-bg);
  color: var(--wf-node-title);
  padding: 0 8px;
  font-size: 12px;
  box-sizing: border-box;
}
.add-port-input:focus,
.add-port-select:focus {
  outline: none;
  border-color: var(--wf-node-border-hover);
}

.add-port-actions {
  display: flex;
  gap: 6px;
  margin-top: 10px;
}

.add-port-confirm,
.add-port-cancel {
  flex: 1;
  height: 26px;
  border: none;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: background 150ms;
}
.add-port-confirm {
  background: var(--wf-node-border-hover);
  color: var(--wf-node-title);
}
.add-port-confirm:hover { background: var(--wf-port-label); }
.add-port-cancel {
  background: var(--wf-node-border);
  color: var(--wf-port-label);
}
.add-port-cancel:hover { background: var(--wf-node-border-hover); }

/* ── 端口删除按钮 ── */
.param-delete {
  width: 20px;
  height: 20px;
  border: none;
  background: none;
  color: var(--wf-status-failed);
  cursor: pointer;
  border-radius: 3px;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 150ms, background 150ms;
}
.param-row:hover .param-delete { opacity: 1; }
.param-delete:hover { background: rgba(239,68,68,0.1); }
</style>
