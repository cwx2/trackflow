import type { NodeDefinition } from './types'
import type { AutomationNodeDefinitionVO } from '@/api/automation'
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
import { roleAgentDefinition } from './role-agent'
import { approvalDefinition } from './approval'
import { issueGetDefinition, issueSearchDefinition, issueContextDefinition, issueTransitionDefinition, issueCommentDefinition, issueUpdateDefinition } from './trackflow-nodes'

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
  'role-agent': roleAgentDefinition,
  'approval': approvalDefinition,
  'trackflow-issue-get': issueGetDefinition,
  'trackflow-issue-search': issueSearchDefinition,
  'trackflow-issue-context': issueContextDefinition,
  'trackflow-issue-transition': issueTransitionDefinition,
  'trackflow-issue-comment': issueCommentDefinition,
  'trackflow-issue-update': issueUpdateDefinition,
}

/** 根据节点类型获取定义，不存在时返回 undefined */
export function getNodeDefinition(type: string): NodeDefinition | undefined {
  return NODE_DEFINITIONS[type]
}

/**
 * 校验画布静态呈现定义与后端可执行目录的正式端口契约。
 * 配置表单属于前端交互层，但节点类型、输入输出、必填性必须以服务端执行器为准。
 */
export function findNodeContractDrift(serverDefinitions: AutomationNodeDefinitionVO[]): string[] {
  const drift: string[] = []
  const serverByType = new Map(serverDefinitions.map(definition => [definition.type, definition]))
  for (const [type, local] of Object.entries(NODE_DEFINITIONS)) {
    const server = serverByType.get(type)
    if (!server) {
      drift.push(`前端节点 ${type} 没有后端执行器`)
      continue
    }
    comparePorts(type, '输入', local.inputPorts, server.inputPorts, drift,
      (port) => `${port.name}:${port.valueType}:${port.required}:${Boolean(port.optional)}`)
    comparePorts(type, '输出', local.outputPorts, server.outputPorts, drift,
      (port) => `${port.name}:${port.valueType}`)
    for (const localPort of local.inputPorts) {
      const serverPort = server.inputPorts.find(port => port.name === localPort.name)
      if (!serverPort) continue
      const localModes = defaultBindingModes(localPort.valueType).join(',')
      const serverModes = [...(serverPort.bindingModes || [])].sort().join(',')
      if (localModes !== serverModes) {
        drift.push(`${type}.${localPort.name} 的输入来源契约与后端不一致`)
      }
    }
    serverByType.delete(type)
  }
  for (const type of serverByType.keys()) {
    drift.push(`后端节点 ${type} 没有前端画布定义`)
  }
  return drift
}

function defaultBindingModes(valueType: string) {
  return (valueType === 'string' || valueType === 'any'
    ? ['literal', 'reference', 'template']
    : ['literal', 'reference']).sort()
}

function comparePorts<T extends { name: string }>(
  nodeType: string,
  direction: string,
  local: T[],
  server: T[],
  drift: string[],
  signature: (port: T) => string,
) {
  const localSignatures = local.map(signature).sort()
  const serverSignatures = server.map(signature).sort()
  if (localSignatures.join('|') !== serverSignatures.join('|')) {
    drift.push(`${nodeType} 的${direction}端口与后端执行契约不一致`)
  }
}

/** 左侧面板可拖拽的节点列表（排除 start/end 特殊节点） */
export const DRAGGABLE_NODES: NodeDefinition[] = [
  issueSearchDefinition,
  issueGetDefinition,
  issueContextDefinition,
  issueTransitionDefinition,
  issueCommentDefinition,
  issueUpdateDefinition,
  roleAgentDefinition,
  cliAgentDefinition,
  codeDefinition,
  httpRequestDefinition,
  subWorkflowDefinition,
  variablesDefinition,
  conditionDefinition,
  loopDefinition,
  fileInputDefinition,
  delayDefinition,
  approvalDefinition,
]
