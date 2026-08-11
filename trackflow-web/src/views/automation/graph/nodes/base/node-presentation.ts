import { getNodeDefinition } from '../../../node-definitions'

/**
 * 画布节点的端口呈现规则。
 *
 * 节点注册表描述的是“节点具备哪些能力”；画布则应优先表达“这个流程实际使用了什么”。
 * 这个模块是 NodeCard 与 BaseNodeModel 共用的唯一布局依据，避免视觉端口与 LogicFlow
 * 锚点再次出现偏移或显示不一致。
 */
export interface PresentablePort {
  name: string
  label?: string
  valueType?: string
  required?: boolean
  optional?: boolean
  value?: unknown
  [key: string]: unknown
}

export type InputSourceKind = 'missing' | 'fixed' | 'upstream' | 'expression' | 'default'

export interface PresentedInput extends PresentablePort {
  sourceKind: InputSourceKind
}

export interface NodePortPresentation {
  visibleInputs: PresentedInput[]
  collapsedInputs: PresentedInput[]
  collapsibleInputCount: number
  outputs: PresentablePort[]
  expanded: boolean
  collapsedLabel: string
}

export function getNodePortPresentation(properties: Record<string, any> | undefined): NodePortPresentation {
  const inputs = (properties?.inputs || []) as PresentablePort[]
  const outputs = (properties?.outputs || []) as PresentablePort[]
  const definition = properties?.nodeType ? getNodeDefinition(properties.nodeType) : undefined
  const defaults = new Map((definition?.inputPorts || []).map(port => [port.name, port.defaultValue]))
  const expanded = properties?.portDisplayMode === 'expanded' || properties?.optionalExpanded === true

  const presentedInputs = inputs.map(input => ({
    ...input,
    sourceKind: getInputSourceKind(input.value, defaults.get(input.name)),
  }))

  // 必填输入是流程成立的前提；非默认配置说明用户已经在使用该能力。
  // 其余能力收纳起来，避免一张节点卡把所有可选项误导成“都需要连接”。
  const compactVisibleInputs = presentedInputs.filter(input => input.required || isExplicitlyConfigured(input.sourceKind))
  const collapsibleInputs = presentedInputs.filter(input => !input.required && !isExplicitlyConfigured(input.sourceKind))
  const visibleInputs = expanded ? presentedInputs : compactVisibleInputs
  const collapsedInputs = expanded ? [] : collapsibleInputs

  return {
    visibleInputs,
    collapsedInputs,
    collapsibleInputCount: collapsibleInputs.length,
    outputs,
    expanded,
    collapsedLabel: definition?.presentation?.collapsedInputsLabel || '更多配置',
  }
}

export function isExplicitlyConfigured(sourceKind: InputSourceKind) {
  return sourceKind === 'fixed' || sourceKind === 'upstream' || sourceKind === 'expression'
}

function getInputSourceKind(value: unknown, defaultValue: unknown): InputSourceKind {
  if (value == null) return 'missing'
  if (isInputValue(value, 'ref')) return 'upstream'
  if (isInputValue(value, 'template')) return 'expression'
  if (isInputValue(value, 'literal')) {
    return sameValue(value, defaultValue) ? 'default' : 'fixed'
  }
  // 历史草稿可能存的是裸字面值，仍应当把它当作用户配置，而不是静默隐藏。
  return 'fixed'
}

function isInputValue(value: unknown, type: string): value is { type: string } {
  return Boolean(value) && typeof value === 'object' && (value as { type?: string }).type === type
}

function sameValue(left: unknown, right: unknown) {
  return JSON.stringify(left ?? null) === JSON.stringify(right ?? null)
}
