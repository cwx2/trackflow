/**
 * FlowEdge — 工作流边（现代简洁风格）
 *
 * 设计规范：
 * - idle：轻量的圆角正交线，自动绕开其他节点
 * - running：加亮，蓝色 + 流动粒子动画
 * - done：绿色
 * - warning：黄色虚线（类型宽松兼容）
 * - incompatible：红色虚线（类型不兼容）
 *
 * 实现说明：
 * - Model 使用正交路由，在其他节点外保留安全间距
 * - View 将折线路径转成圆角 SVG Path，保留清晰的流向与可点击区域
 * - Model.getArrowStyle() offset=0 禁用默认箭头尺寸参数
 * - View.getEndArrow() 自绘小三角；View.getStartArrow() 返回 null（起点用圆点代替）
 */
import { PolylineEdge, PolylineEdgeModel, h } from '@logicflow/core'
import type { ExecutionFlowStatus } from './ExecutionFlowAnimator'

// ─── 类型定义 ─────────────────────────────────────────────────────────────────

/** 端口数据类型兼容性 */
export type TypeCompat = 'compatible' | 'warning' | 'incompatible'

/**
 * 检查源端口类型 → 目标端口类型的兼容性
 * - compatible：完全兼容（相同类型、any 类型）
 * - warning：宽松兼容（string↔number 等可隐式转换）
 * - incompatible：不兼容（无法安全转换）
 */
export function checkTypeCompatibility(sourceType: string, targetType: string): TypeCompat {
  if (!sourceType || !targetType) return 'compatible'
  if (sourceType === targetType || sourceType === 'any' || targetType === 'any') return 'compatible'

  const warningPairs = new Set([
    'string->number', 'number->string',
    'object->string', 'string->object',
    'array->object',  'object->array',
    'number->boolean','boolean->number',
  ])
  return warningPairs.has(`${sourceType}->${targetType}`) ? 'warning' : 'incompatible'
}

// ─── 常量 ─────────────────────────────────────────────────────────────────────

const STROKE_WIDTH_NORMAL  = '1.5'
const STROKE_WIDTH_RUNNING = '2.5'
const OPACITY_NORMAL       = '0.48'
const OPACITY_RUNNING      = '1'
const OPACITY_DOT_NORMAL   = '0.8'

// ─── Model ───────────────────────────────────────────────────────────────────

export class FlowEdgeModel extends PolylineEdgeModel {
  /** 提供颜色给 LogicFlow 内部（选中框等），不负责实际线条渲染 */
  getEdgeStyle() {
    const style = super.getEdgeStyle()
    style.strokeWidth = 2
    style.stroke = this._resolveColor()
    return style
  }

  /** 禁用 LogicFlow 默认箭头尺寸参数（由 View.getEndArrow 自绘） */
  getArrowStyle() {
    const style = super.getArrowStyle()
    style.offset = 0
    style.verticalLength = 0
    return style
  }

  private _resolveColor(): string {
    const props = this.properties as any
    const status    = props?.flowStatus || 'idle'
    const typeCompat: TypeCompat = props?.typeCompat || 'compatible'
    return resolveEdgeColor(status, typeCompat)
  }

  /**
   * LogicFlow 默认折线只考虑起止节点。工作流画布中节点是自由摆放的，
   * 所以这里使用可见性图路由，把中间节点当成带安全边距的障碍物。
   */
  updatePoints() {
    const graphModel = (this as any).graphModel
    const start = this.startPoint
    const end = this.endPoint
    const nodes = graphModel?.nodes || []
    const obstacles = nodes
      .filter((node: any) => node.id !== this.sourceNodeId && node.id !== this.targetNodeId)
      .map((node: any) => toObstacle(node))
      .filter(Boolean) as Obstacle[]

    const route = findObstacleAvoidingRoute(start, end, obstacles)
    if (route.length >= 2) {
      this.pointsList = this.orthogonalizePath(route)
      this.points = this.getPath(this.pointsList)
      return
    }

    // 画布数据不完整或极端拥挤时退回 LogicFlow 内置路径，保证编辑不被阻断。
    super.updatePoints()
  }
}

// ─── View ─────────────────────────────────────────────────────────────────────

export class FlowEdgeView extends PolylineEdge {
  /** 主体：路径 + 起点圆点 + 可选粒子动画 */
  getEdge() {
    const { model }  = this.props as any
    const props      = model?.properties as any
    const status     = props?.flowStatus || 'idle'
    const typeCompat: TypeCompat = props?.typeCompat || 'compatible'
    const isRunning  = status === 'running'
    const isSelected = Boolean(model?.isSelected)

    const pathD  = buildRoundedPathD(model?.pointsList || [], 12)
    const color  = resolveEdgeColor(status, typeCompat, isSelected)
    const isDashed = typeCompat === 'warning' || typeCompat === 'incompatible'
    const { startPoint } = model
    const pathId = `flow-path-${model.id}`

    const hitArea = h('path', {
      d: pathD,
      fill: 'none',
      stroke: 'transparent',
      'stroke-width': '14',
      'pointer-events': 'stroke',
    })

    const selectionHalo = isSelected ? h('path', {
      d: pathD,
      fill: 'none',
      stroke: color,
      'stroke-width': '6',
      'stroke-linecap': 'round',
      opacity: '0.16',
    }) : null

    const mainPath = h('path', {
      d: pathD,
      fill: 'none',
      stroke: color,
      'stroke-width': isRunning ? STROKE_WIDTH_RUNNING : STROKE_WIDTH_NORMAL,
      'stroke-linecap': 'round',
      'stroke-linejoin': 'round',
      opacity: isRunning ? OPACITY_RUNNING : OPACITY_NORMAL,
      ...(isDashed ? { 'stroke-dasharray': '6 4' } : {}),
    })

    const startDot = startPoint ? h('circle', {
      cx: startPoint.x, cy: startPoint.y,
      r: '3.5',
      fill: color,
      opacity: isRunning ? OPACITY_RUNNING : OPACITY_DOT_NORMAL,
    }) : null

    const warningBadge = typeCompat === 'warning' ? buildWarningBadge(model) : null

    // LogicFlow 的 h() 与 Vue VNode 泛型不同，边子元素只在 SVG 渲染期使用。
    const particles: any[] = []
    if (isRunning) {
      particles.push(
        h('defs', {}, [h('path', { id: pathId, d: pathD, fill: 'none', stroke: 'none' })]),
        buildParticle(pathId, color, 0),
        buildParticle(pathId, color, 0.3),
        buildParticle(pathId, color, 0.6),
      )
    }

    return h('g', {}, [hitArea, selectionHalo, mainPath, startDot, warningBadge, ...particles].filter(Boolean) as any)
  }

  /** 末端箭头：小实心三角（9×8） */
  getEndArrow() {
    const { model }  = this.props as any
    const props      = model?.properties as any
    const status     = props?.flowStatus || 'idle'
    const typeCompat: TypeCompat = props?.typeCompat || 'compatible'
    const isRunning  = status === 'running'
    const color      = resolveEdgeColor(status, typeCompat, Boolean(model?.isSelected))

    return h('polygon', {
      points: '5,0 -4,4 -4,-4',
      fill: color,
      stroke: 'none',
      opacity: isRunning ? OPACITY_RUNNING : OPACITY_DOT_NORMAL,
    })
  }

  /** 不显示起点箭头（由 getEdge 中的圆点代替） */
  getStartArrow() { return null as any }
}

// ─── 纯函数工具（无副作用，可单元测试） ────────────────────────────────────────

/** 根据运行状态和类型兼容性计算边颜色（CSS 变量优先，硬编码兜底） */
export function resolveEdgeColor(status: ExecutionFlowStatus | string, typeCompat: TypeCompat, selected = false): string {
  if (status === 'running')             return 'var(--wf-status-running, #3b82f6)'
  if (status === 'done')                return 'var(--wf-status-success, #22c55e)'
  if (status === 'failed')              return 'var(--wf-status-failed, #ef4444)'
  if (typeCompat === 'warning')         return 'var(--wf-status-warning, #f59e0b)'
  if (typeCompat === 'incompatible')    return 'var(--wf-status-failed, #ef4444)'
  if (selected)                         return 'var(--wf-edge-color-hover, #60a5fa)'
  return 'var(--wf-edge-color, #6366f1)'
}

type Point = { x: number; y: number }
type Obstacle = { left: number; right: number; top: number; bottom: number }

const ROUTE_CLEARANCE = 26
const ROUTE_LEAD = 24
const TURN_PENALTY = 36

/** 从节点模型取出带安全间距的障碍框。 */
function toObstacle(node: any): Obstacle | null {
  if (!Number.isFinite(node?.x) || !Number.isFinite(node?.y)) return null
  const width = Number(node.width) || 0
  const height = Number(node.height) || 0
  if (!width || !height) return null
  return {
    left: node.x - width / 2 - ROUTE_CLEARANCE,
    right: node.x + width / 2 + ROUTE_CLEARANCE,
    top: node.y - height / 2 - ROUTE_CLEARANCE,
    bottom: node.y + height / 2 + ROUTE_CLEARANCE,
  }
}

/**
 * 基于障碍框边缘生成稀疏可见性图，再以转弯数优先的 Dijkstra 寻路。
 * 相比网格 A*，这里没有固定格子感，且路由会随卡片尺寸自然变化。
 */
export function findObstacleAvoidingRoute(start: Point, end: Point, obstacles: Obstacle[]): Point[] {
  if (!isFinitePoint(start) || !isFinitePoint(end)) return []

  const startLead = { x: start.x + ROUTE_LEAD, y: start.y }
  const endLead = { x: end.x - ROUTE_LEAD, y: end.y }
  const canUseLeads = isSegmentClear(start, startLead, obstacles)
    && isSegmentClear(endLead, end, obstacles)
  const routeStart = canUseLeads ? startLead : start
  const routeEnd = canUseLeads ? endLead : end

  const xValues = uniqueNumbers([routeStart.x, routeEnd.x, ...obstacles.flatMap(box => [box.left, box.right])])
  const yValues = uniqueNumbers([routeStart.y, routeEnd.y, ...obstacles.flatMap(box => [box.top, box.bottom])])
  const candidates: Point[] = []
  for (const x of xValues) {
    for (const y of yValues) {
      const point = { x, y }
      if (!isInsideObstacle(point, obstacles)) candidates.push(point)
    }
  }

  const startKey = pointKey(routeStart)
  const endKey = pointKey(routeEnd)
  const pointMap = new Map(candidates.map(point => [pointKey(point), point]))
  pointMap.set(startKey, routeStart)
  pointMap.set(endKey, routeEnd)
  const points = [...pointMap.values()]
  const neighbors = buildVisibilityNeighbors(points, obstacles)
  const coreRoute = findLeastTurnPath(startKey, endKey, pointMap, neighbors)
  if (!coreRoute.length) return []

  return simplifyPath([
    start,
    ...(canUseLeads ? [startLead] : []),
    ...coreRoute.slice(1, -1),
    ...(canUseLeads ? [endLead] : []),
    end,
  ])
}

function isFinitePoint(point: Point) {
  return Number.isFinite(point.x) && Number.isFinite(point.y)
}

function uniqueNumbers(values: number[]) {
  return [...new Set(values.map(value => Math.round(value * 100) / 100))]
}

function pointKey(point: Point) { return `${point.x}:${point.y}` }

function isInsideObstacle(point: Point, obstacles: Obstacle[]) {
  return obstacles.some(box => point.x > box.left && point.x < box.right && point.y > box.top && point.y < box.bottom)
}

/** 仅阻止穿过障碍框内部；沿安全边距边缘行走是允许的。 */
function isSegmentClear(a: Point, b: Point, obstacles: Obstacle[]) {
  if (a.x !== b.x && a.y !== b.y) return false
  return !obstacles.some(box => {
    if (a.y === b.y) {
      if (a.y <= box.top || a.y >= box.bottom) return false
      const left = Math.min(a.x, b.x)
      const right = Math.max(a.x, b.x)
      return left < box.right && right > box.left
    }
    if (a.x <= box.left || a.x >= box.right) return false
    const top = Math.min(a.y, b.y)
    const bottom = Math.max(a.y, b.y)
    return top < box.bottom && bottom > box.top
  })
}

function buildVisibilityNeighbors(points: Point[], obstacles: Obstacle[]) {
  const neighbors = new Map<string, { key: string; direction: 'h' | 'v'; distance: number }[]>()
  const connectSorted = (items: Point[], direction: 'h' | 'v') => {
    items.sort((a, b) => direction === 'h' ? a.x - b.x : a.y - b.y)
    for (let index = 1; index < items.length; index += 1) {
      const a = items[index - 1]
      const b = items[index]
      if (!isSegmentClear(a, b, obstacles)) continue
      const distance = Math.abs(a.x - b.x) + Math.abs(a.y - b.y)
      addNeighbor(neighbors, a, b, direction, distance)
      addNeighbor(neighbors, b, a, direction, distance)
    }
  }

  const rows = new Map<number, Point[]>()
  const columns = new Map<number, Point[]>()
  points.forEach(point => {
    rows.set(point.y, [...(rows.get(point.y) || []), point])
    columns.set(point.x, [...(columns.get(point.x) || []), point])
  })
  rows.forEach(row => connectSorted(row, 'h'))
  columns.forEach(column => connectSorted(column, 'v'))
  return neighbors
}

function addNeighbor(
  neighbors: Map<string, { key: string; direction: 'h' | 'v'; distance: number }[]>,
  from: Point,
  to: Point,
  direction: 'h' | 'v',
  distance: number,
) {
  const key = pointKey(from)
  neighbors.set(key, [...(neighbors.get(key) || []), { key: pointKey(to), direction, distance }])
}

function findLeastTurnPath(
  startKey: string,
  endKey: string,
  points: Map<string, Point>,
  neighbors: Map<string, { key: string; direction: 'h' | 'v'; distance: number }[]>,
) {
  type QueueItem = { key: string; direction?: 'h' | 'v'; cost: number; path: string[] }
  const queue: QueueItem[] = [{ key: startKey, direction: 'h', cost: 0, path: [startKey] }]
  const best = new Map<string, number>()

  while (queue.length) {
    queue.sort((a, b) => a.cost - b.cost)
    const current = queue.shift()!
    const stateKey = `${current.key}:${current.direction || 'none'}`
    if (current.cost > (best.get(stateKey) ?? Infinity)) continue
    if (current.key === endKey) return current.path.map(key => points.get(key)!).filter(Boolean)

    for (const next of neighbors.get(current.key) || []) {
      const cost = current.cost + next.distance + (current.direction && current.direction !== next.direction ? TURN_PENALTY : 0)
      const nextState = `${next.key}:${next.direction}`
      if (cost >= (best.get(nextState) ?? Infinity)) continue
      best.set(nextState, cost)
      queue.push({ key: next.key, direction: next.direction, cost, path: [...current.path, next.key] })
    }
  }
  return [] as Point[]
}

function simplifyPath(points: Point[]) {
  const unique = points.filter((point, index) => index === 0 || point.x !== points[index - 1].x || point.y !== points[index - 1].y)
  return unique.filter((point, index) => {
    if (index === 0 || index === unique.length - 1) return true
    const previous = unique[index - 1]
    const next = unique[index + 1]
    return !((previous.x === point.x && point.x === next.x) || (previous.y === point.y && point.y === next.y))
  })
}

/** 将严格正交的点列表转为带圆角的 SVG Path。 */
export function buildRoundedPathD(points: Point[], radius: number): string {
  if (points.length < 2) return ''
  let path = `M ${points[0].x} ${points[0].y}`
  for (let index = 1; index < points.length - 1; index += 1) {
    const previous = points[index - 1]
    const current = points[index]
    const next = points[index + 1]
    const beforeDistance = Math.abs(previous.x - current.x) + Math.abs(previous.y - current.y)
    const afterDistance = Math.abs(next.x - current.x) + Math.abs(next.y - current.y)
    const corner = Math.min(radius, beforeDistance / 2, afterDistance / 2)
    if (!corner || (previous.x === next.x || previous.y === next.y)) {
      path += ` L ${current.x} ${current.y}`
      continue
    }
    const before = moveTowards(current, previous, corner)
    const after = moveTowards(current, next, corner)
    path += ` L ${before.x} ${before.y} Q ${current.x} ${current.y} ${after.x} ${after.y}`
  }
  const end = points[points.length - 1]
  return `${path} L ${end.x} ${end.y}`
}

function moveTowards(from: Point, to: Point, distance: number): Point {
  if (from.x !== to.x) return { x: from.x + Math.sign(to.x - from.x) * distance, y: from.y }
  return { x: from.x, y: from.y + Math.sign(to.y - from.y) * distance }
}

/** 在边中点上方渲染警告图标 */
function buildWarningBadge(model: any) {
  try {
    const { startPoint, endPoint } = model
    if (!startPoint || !endPoint) return null
    return h('text', {
      x: (startPoint.x + endPoint.x) / 2,
      y: (startPoint.y + endPoint.y) / 2 - 6,
      'font-size': '11',
      'text-anchor': 'middle',
      fill: 'var(--wf-status-warning, #f59e0b)',
    }, '⚠')
  } catch { return null }
}

/**
 * 构建单个流动粒子（沿路径运动的 SVG animateMotion）
 * 注意：glow 和 core 各自持有独立的 mpath 实例，不共享虚拟节点
 */
function buildParticle(pathId: string, color: string, delayS: number) {
  const motionAttrs = {
    dur: '0.9s',
    begin: `${delayS}s`,
    repeatCount: 'indefinite',
    rotate: 'auto',
    calcMode: 'spline' as const,
    keySplines: '0.4 0 0.6 1',
    keyTimes: '0;1',
  }

  const href = `#${pathId}`

  // 每个 animateMotion 需要独立的 mpath 实例
  const glow = h('circle', { r: 4, fill: color, opacity: '0.25' }, [
    h('animateMotion', motionAttrs, [h('mpath', { 'xlink:href': href })]),
  ])
  const core = h('circle', { r: 2.5, fill: '#fff', opacity: '0.9' }, [
    h('animateMotion', motionAttrs, [h('mpath', { 'xlink:href': href })]),
  ])

  return h('g', {}, [glow, core])
}

// ─── 注册导出 ─────────────────────────────────────────────────────────────────

export const FlowEdge = {
  type: 'flow-edge',
  view: FlowEdgeView,
  model: FlowEdgeModel,
}

export default FlowEdge
