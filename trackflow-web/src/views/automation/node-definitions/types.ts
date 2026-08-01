import type { ValueType, InputValue } from '@/api/automation'

// ====== 节点定义注册表类型 ======

/** 输入端口定义（描述层，不含实际值） */
export interface InputPortDef {
  name: string
  /** 显示标签（中文），不填则回退到 name */
  label?: string
  valueType: ValueType
  required: boolean
  description?: string
  /** 默认值（新拖入节点时预填） */
  defaultValue?: InputValue
}

/** 输出端口定义 */
export interface OutputPortDef {
  name: string
  /** 显示标签（中文），不填则回退到 name */
  label?: string
  valueType: ValueType
  description?: string
}

/** 配置项定义（节点的特有执行参数，不参与变量流） */
export interface ConfigFieldDef {
  key: string
  label: string
  type: 'string' | 'number' | 'boolean' | 'select' | 'textarea'
  defaultValue?: unknown
  options?: Array<{ label: string; value: string | number }>
  placeholder?: string
  description?: string
}

/** 节点元数据 */
export interface NodeMetaDef {
  title: string
  icon: string
  color: string
  description: string
  category: '基础节点' | '控制流' | '数据处理' | '特殊节点' | '业务逻辑' | 'TrackFlow' | 'Agent'
}

/** 节点完整定义（前端注册表核心） */
export interface NodeDefinition {
  type: string
  meta: NodeMetaDef
  inputPorts: InputPortDef[]
  outputPorts: OutputPortDef[]
  configFields: ConfigFieldDef[]
}
