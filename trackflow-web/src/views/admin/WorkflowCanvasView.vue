<template>
  <div class="workflow-canvas-view">
    <!-- 画布容器 -->
    <div ref="canvasRef" class="canvas-container"></div>

    <!-- 工具条 -->
    <div class="canvas-toolbar">
      <a-tooltip content="放大">
        <a-button size="small" @click="zoomIn"><icon-plus /></a-button>
      </a-tooltip>
      <span class="zoom-label">{{ zoomPercent }}%</span>
      <a-tooltip content="缩小">
        <a-button size="small" @click="zoomOut"><icon-minus /></a-button>
      </a-tooltip>
      <a-divider direction="vertical" />
      <a-tooltip content="适应视图">
        <a-button size="small" @click="fitView"><icon-expand /></a-button>
      </a-tooltip>
      <a-tooltip content="自动整理布局">
        <a-button size="small" @click="autoLayout()"><icon-apps /></a-button>
      </a-tooltip>
    </div>

    <!-- 图例说明 -->
    <div class="canvas-legend">
      <span class="legend-item"><span class="legend-dot legend-open"></span>打开</span>
      <span class="legend-item"><span class="legend-dot legend-progress"></span>进行中</span>
      <span class="legend-item"><span class="legend-dot legend-done"></span>已完成</span>
      <span class="legend-item"><span class="legend-dot legend-cancelled"></span>已取消</span>
      <span class="legend-item"><span class="legend-initial-star">★</span> 初始状态</span>
    </div>

    <!-- 提示文字 -->
    <div class="canvas-hint">
      从节点右侧连接点拖拽到另一节点创建转换 | 点击连线配置动作 | 点击节点切换初始状态 | 拖拽节点调整位置
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { IconPlus, IconMinus, IconExpand, IconApps } from '@arco-design/web-vue/es/icon'
import LogicFlow, { HtmlNode, HtmlNodeModel } from '@logicflow/core'
import { issueApi } from '@/api'
import type { IssueStatusVO } from '@/api/types'
import { localizeStatusName } from '@/utils/fieldLabels'
import { STATUS_CATEGORY_COLORS, SERIES_TERTIARY } from '@/utils/chartColors'

import '@logicflow/core/dist/index.css'

const props = defineProps<{
  statuses: IssueStatusVO[]
  allowedTransitions: Set<string>
  initialStatusId: string | null
  selectedProject: string
  selectedType: string
  selectedRole: string
  selectedMode: 'normal' | 'author' | 'assignee'
}>()

const emit = defineEmits<{
  (e: 'toggle-transition', from: string, to: string): void
  (e: 'open-action-panel', fromStatus: IssueStatusVO, toStatus: IssueStatusVO): void
  (e: 'open-guard-panel', fromStatus: IssueStatusVO, toStatus: IssueStatusVO): void
  (e: 'click-node', status: IssueStatusVO): void
}>()

const canvasRef = ref<HTMLElement | null>(null)
const zoomPercent = ref(100)

let lf: LogicFlow | null = null
// 防止连线添加事件重复触发的锁
let edgeAddLock = false
// 标记是否已完成首次 fitView（避免重复触发）
let initialFitDone = false
// 用于在容器尺寸稳定后执行 fitView
let resizeObserver: ResizeObserver | null = null

// Category colors mapping
const categoryColors: Record<string, string> = STATUS_CATEGORY_COLORS

// ========== 自定义节点定义 ==========

class StatusNodeView extends HtmlNode {
  setHtml(rootEl: SVGForeignObjectElement) {
    const { properties } = this.props.model
    const status = properties.statusData as IssueStatusVO
    const isInitial = properties.isInitial as boolean
    const color = status?.color || categoryColors[status?.category] || SERIES_TERTIARY
    const name = localizeStatusName(status?.name || '')
    const categoryClass = `category-${status?.category || 'open'}`

    // 清除之前的内容
    rootEl.innerHTML = ''
    const div = document.createElement('div')
    div.style.cssText = 'width: 100%; height: 100%; overflow: visible;'
    rootEl.appendChild(div)

    div.innerHTML = `
      <div class="status-canvas-node ${categoryClass}" style="border-color: ${color}">
        <div class="node-color-bar" style="background: ${color}"></div>
        <div class="node-content">
          ${isInitial ? '<span class="node-initial-star">★</span>' : ''}
          <span class="node-name">${name}</span>
        </div>
      </div>
    `
  }
}

class StatusNodeModel extends HtmlNodeModel {
  initNodeData(data: any) {
    super.initNodeData(data)
    this.width = 140
    this.height = 44
    this.text = { value: '', x: 0, y: 0, draggable: false, editable: false }
  }

  getNodeStyle() {
    const style = super.getNodeStyle()
    style.stroke = 'none'
    style.fill = 'none'
    return style
  }

  getOutlineStyle() {
    const style = super.getOutlineStyle()
    style.stroke = 'none'
    if (style.hover) style.hover.stroke = 'none'
    return style
  }

  getConnectedSourceRules() {
    const rules = super.getConnectedSourceRules()
    const selfId = this.id
    rules.push({
      message: '不能创建自连接',
      validate: (_source: any, target: any) => selfId !== target.id
    })
    return rules
  }
}

const StatusNodeDef = { type: 'status-node', view: StatusNodeView, model: StatusNodeModel }

// ========== 延迟 fitView 工具方法 ==========

/**
 * 在画布容器尺寸稳定后执行 fitView。
 * 解决问题：Tab 切换/flex 布局尚未完成时 fitView 用错误尺寸计算 viewport。
 * 策略：使用 setTimeout + requestAnimationFrame 双重保障，确保浏览器已完成 layout。
 */
function deferredFitView() {
  if (!lf || !canvasRef.value) return

  // 如果容器当前有有效尺寸，直接在下一帧执行
  const { clientWidth, clientHeight } = canvasRef.value
  if (clientWidth > 0 && clientHeight > 0) {
    requestAnimationFrame(() => {
      lf?.fitView()
    })
  } else {
    // 容器尺寸为 0（可能被 v-show 隐藏），通过 ResizeObserver 监听首次有效尺寸
    waitForContainerReady()
  }
}

/**
 * 通过 ResizeObserver 监听容器首次获得有效尺寸，然后执行 fitView。
 * 适用于组件挂载时 Tab 未激活的场景。
 */
function waitForContainerReady() {
  if (!canvasRef.value || !lf) return

  // 清理旧的 observer
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }

  resizeObserver = new ResizeObserver((entries) => {
    for (const entry of entries) {
      const { width, height } = entry.contentRect
      if (width > 0 && height > 0) {
        // 容器有了有效尺寸，执行 fitView
        requestAnimationFrame(() => {
          if (lf) {
            lf.resize()
            lf.fitView()
            initialFitDone = true
          }
        })
        // 只需要首次触发，之后断开 observer
        resizeObserver?.disconnect()
        resizeObserver = null
        break
      }
    }
  })

  resizeObserver.observe(canvasRef.value)
}

// ========== LogicFlow 初始化 ==========

function initLogicFlow() {
  if (!canvasRef.value) return

  lf = new LogicFlow({
    container: canvasRef.value,
    grid: { size: 20, visible: true, type: 'dot' },
    background: { backgroundColor: 'transparent' },
    edgeType: 'polyline',
    keyboard: { enabled: false },
    style: {
      edgeAnimation: { stroke: 'var(--color-text-3)' },
      polyline: {
        stroke: 'var(--color-text-3)',
        strokeWidth: 1.5
      },
      arrow: {
        offset: 6,
        verticalLength: 3
      }
    }
  })

  // 注册自定义节点
  lf.register(StatusNodeDef)

  // 注册事件
  lf.on('edge:click', handleEdgeClick)
  lf.on('node:click', handleNodeClick)
  lf.on('connection:not-allowed', handleConnectionNotAllowed)
  lf.on('edge:add', handleEdgeAdd)
  lf.on('node:drop', handleNodeDrop)

  // 监听缩放
  lf.on('graph:transform', () => {
    const transform = (lf as any)?.graphModel?.transformModel
    if (transform) {
      zoomPercent.value = Math.round(transform.SCALE_X * 100)
    }
  })

  renderCanvas()
}

// ========== 画布渲染 ==========

function renderCanvas() {
  if (!lf || !props.statuses.length) return

  const nodes = buildNodes()
  const edges = buildEdges()

  lf.render({ nodes, edges })

  // 如果所有节点都没有持久化位置，执行自动布局
  const hasPositions = props.statuses.some(s => s.canvasX != null && s.canvasY != null)
  if (!hasPositions) {
    nextTick(() => autoLayout(false))
  } else {
    // 使用延迟 fitView，确保容器尺寸稳定后再执行
    nextTick(() => deferredFitView())
  }
}

function buildNodes() {
  return props.statuses.map((status, index) => {
    // 使用持久化位置，或使用默认网格布局
    const x = status.canvasX ?? (200 + (index % 4) * 200)
    const y = status.canvasY ?? (100 + Math.floor(index / 4) * 120)

    return {
      id: status.id,
      type: 'status-node',
      x,
      y,
      properties: {
        statusData: status,
        isInitial: props.initialStatusId === status.id
      }
    }
  })
}

function buildEdges() {
  const edges: any[] = []
  for (const key of props.allowedTransitions) {
    const [fromId, toId] = key.split('-')
    edges.push({
      id: `edge-${fromId}-${toId}`,
      type: 'polyline',
      sourceNodeId: fromId,
      targetNodeId: toId,
      properties: {
        fromId,
        toId
      }
    })
  }
  return edges
}

// ========== 事件处理 ==========

function handleEdgeClick({ data }: { data: any }) {
  const fromId = data.properties?.fromId || data.sourceNodeId
  const toId = data.properties?.toId || data.targetNodeId
  const fromStatus = props.statuses.find(s => s.id === fromId)
  const toStatus = props.statuses.find(s => s.id === toId)
  if (fromStatus && toStatus) {
    emit('open-action-panel', fromStatus, toStatus)
  }
}

function handleNodeClick({ data }: { data: any }) {
  const status = data.properties?.statusData as IssueStatusVO
  if (status) {
    emit('click-node', status)
  }
}

function handleConnectionNotAllowed({ msg }: { msg: string }) {
  if (msg) {
    Message.warning(msg)
  }
}

function handleEdgeAdd({ data }: { data: any }) {
  if (edgeAddLock) return
  edgeAddLock = true
  setTimeout(() => { edgeAddLock = false }, 100)

  const fromId = data.sourceNodeId
  const toId = data.targetNodeId
  const key = `${fromId}-${toId}`

  // 如果已经存在该转换
  if (props.allowedTransitions.has(key)) {
    // 删除重复边
    nextTick(() => lf?.deleteEdge(data.id))
    Message.warning('该转换已存在')
    return
  }

  // 通过 emit 触发父组件添加转换
  emit('toggle-transition', fromId, toId)

  // 更新边属性
  lf?.setProperties(data.id, { fromId, toId })
}

function handleNodeDrop({ data }: { data: any }) {
  // 节点拖拽结束，持久化位置
  const statusId = data.id
  const { x, y } = data
  saveNodePosition(statusId, x, y)
}

async function saveNodePosition(statusId: string, x: number, y: number) {
  try {
    await issueApi.updateStatusPosition(statusId, Math.round(x), Math.round(y))
  } catch {
    // 静默失败，不打断用户操作
  }
}

// ========== 画布操作 ==========

function zoomIn() {
  lf?.zoom(true)
}

function zoomOut() {
  lf?.zoom(false)
}

function fitView() {
  lf?.fitView()
}

function autoLayout(savePositions = true) {
  if (!lf) return

  const graphData = lf.getGraphData() as { nodes: any[]; edges: any[] }
  if (!graphData.nodes.length) return

  // 按分类分组
  const categoryOrder = ['open', 'in_progress', 'done', 'cancelled']
  const grouped: Record<string, any[]> = {}
  for (const cat of categoryOrder) {
    grouped[cat] = []
  }

  for (const node of graphData.nodes) {
    const category = node.properties?.statusData?.category || 'open'
    if (!grouped[category]) grouped[category] = []
    grouped[category].push(node)
  }

  // 水平布局：每个分类一列
  const colWidth = 200
  const rowHeight = 90
  const startX = 150
  const startY = 80

  const positions: { statusId: string; canvasX: number; canvasY: number }[] = []

  let colIndex = 0
  for (const cat of categoryOrder) {
    const nodes = grouped[cat]
    if (!nodes || nodes.length === 0) continue

    nodes.forEach((node: any, rowIndex: number) => {
      const x = startX + colIndex * colWidth
      const y = startY + rowIndex * rowHeight

      const graphModel = (lf as any).graphModel
      graphModel.moveNode2Coordinate(node.id, x, y)
      positions.push({ statusId: node.id, canvasX: x, canvasY: y })
    })

    colIndex++
  }

  nextTick(() => deferredFitView())

  // 批量持久化位置
  if (savePositions && positions.length > 0) {
    issueApi.batchUpdateStatusPositions(positions).catch(() => {
      // 静默失败
    })
  }

  Message.success('布局已整理')
}

// ========== 画布刷新 ==========

function refreshCanvas() {
  if (!lf) return
  renderCanvas()
}

// 监听数据变化时刷新画布
let refreshTimer: ReturnType<typeof setTimeout> | null = null
watch(
  () => [props.statuses.length, props.allowedTransitions.size, props.initialStatusId],
  () => {
    // 去抖刷新
    if (refreshTimer) clearTimeout(refreshTimer)
    refreshTimer = setTimeout(() => refreshCanvas(), 100)
  }
)

// ========== 暴露方法 ==========

defineExpose({
  refreshCanvas,
  autoLayout
})

// ========== 生命周期 ==========

onMounted(() => {
  nextTick(() => initLogicFlow())
})

onBeforeUnmount(() => {
  if (refreshTimer) clearTimeout(refreshTimer)
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  if (lf) {
    lf.destroy()
    lf = null
  }
})
</script>

<style scoped>
.workflow-canvas-view {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 500px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  overflow: hidden;
  background: var(--color-bg-1);
}

.canvas-container {
  width: 100%;
  height: 100%;
}

/* 工具条 */
.canvas-toolbar {
  position: absolute;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  box-shadow: var(--tf-shadow);
  z-index: 10;
}

.zoom-label {
  font-size: 12px;
  color: var(--color-text-2);
  min-width: 36px;
  text-align: center;
}

/* 图例 */
.canvas-legend {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  gap: 12px;
  padding: 6px 12px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  font-size: 11px;
  color: var(--color-text-3);
  z-index: 10;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.legend-open { background: var(--tf-accent); }
.legend-progress { background: var(--tf-warning); }
.legend-done { background: var(--tf-success); }
.legend-cancelled { background: var(--tf-danger); }

.legend-initial-star {
  color: var(--tf-warning);
  font-size: 12px;
}

/* 提示文字 */
.canvas-hint {
  position: absolute;
  bottom: 56px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 11px;
  color: var(--color-text-4);
  white-space: nowrap;
  z-index: 10;
}
</style>

<style>
/* 覆盖 LogicFlow 内置样式，使画布跟随主题 */
.lf-graph {
  background: transparent !important;
}

.lf-canvas-overlay {
  background: transparent !important;
}

/* 网格点（dot 类型）适配主题 */
.lf-grid svg circle {
  fill: var(--tf-text-muted) !important;
  opacity: 0.4;
}

.lf-grid svg path {
  stroke: var(--tf-text-muted) !important;
  opacity: 0.4;
}

/* 自定义节点全局样式（LogicFlow 要求非 scoped） */
.status-canvas-node {
  display: flex;
  align-items: center;
  width: 140px;
  height: 44px;
  background: var(--tf-bg-surface);
  border: 2px solid var(--color-border-2);
  border-radius: 8px;
  cursor: pointer;
  transition: box-shadow 150ms ease, border-color 150ms ease;
  overflow: hidden;
  user-select: none;
}

.status-canvas-node:hover {
  box-shadow: var(--tf-shadow);
}

.status-canvas-node .node-color-bar {
  width: 4px;
  height: 100%;
  flex-shrink: 0;
}

.status-canvas-node .node-content {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 10px;
  overflow: hidden;
}

.status-canvas-node .node-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.status-canvas-node .node-initial-star {
  color: var(--tf-warning);
  font-size: 14px;
  flex-shrink: 0;
}

/* 分类高亮边框 */
.status-canvas-node.category-open { border-color: var(--tf-accent); }
.status-canvas-node.category-in_progress { border-color: var(--tf-warning); }
.status-canvas-node.category-done { border-color: var(--tf-success); }
.status-canvas-node.category-cancelled { border-color: var(--tf-danger); }
</style>
