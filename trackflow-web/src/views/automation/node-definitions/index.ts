import type { NodeDefinition } from './types'
import { startDefinition }       from './start'
import { endDefinition }         from './end'
import { cliAgentDefinition }    from './cli-agent'
import { variablesDefinition }   from './variables'
import { conditionDefinition }   from './condition'
import { loopDefinition }        from './loop'
import { fileInputDefinition }   from './file-input'
import { delayDefinition }       from './delay'
import { codeDefinition }        from './code'
import { httpRequestDefinition } from './http-request'
import { subWorkflowDefinition } from './sub-workflow'

export type { NodeDefinition, InputPortDef, OutputPortDef, ConfigFieldDef, NodeMetaDef } from './types'

/** 所有节点定义的注册表，key = nodeType */
export const NODE_DEFINITIONS: Record<string, NodeDefinition> = {
  'start':        startDefinition,
  'end':          endDefinition,
  'cli-agent':    cliAgentDefinition,
  'variables':    variablesDefinition,
  'condition':    conditionDefinition,
  'loop':         loopDefinition,
  'file-input':   fileInputDefinition,
  'delay':        delayDefinition,
  'code':         codeDefinition,
  'http-request': httpRequestDefinition,
  'sub-workflow': subWorkflowDefinition,
}

/** 根据节点类型获取定义，不存在时返回 undefined */
export function getNodeDefinition(type: string): NodeDefinition | undefined {
  return NODE_DEFINITIONS[type]
}

/** 左侧面板可拖拽的节点列表（排除 start/end 特殊节点） */
export const DRAGGABLE_NODES: NodeDefinition[] = [
  cliAgentDefinition,
  codeDefinition,
  httpRequestDefinition,
  subWorkflowDefinition,
  variablesDefinition,
  conditionDefinition,
  loopDefinition,
  fileInputDefinition,
  delayDefinition,
]
