import type { NodeDefinition } from './types'

export const cliAgentDefinition: NodeDefinition = {
  type: 'cli-agent',
  meta: {
    title: 'CLI Agent',
    icon: '🤖',
    color: '#6366f1',
    description: '执行 AI 命令行任务',
    category: '基础节点',
  },
  inputPorts: [
    { name: 'prompt',  label: '提示词',   valueType: 'string', required: true,  description: '提示词，支持引用上游变量' },
    { name: 'workDir', label: '工作目录', valueType: 'string', required: false, description: '工作目录，默认为 {workspace}' },
  ],
  outputPorts: [
    { name: 'output',   label: '输出内容', valueType: 'string', description: '命令行完整输出内容' },
    { name: 'exitCode', label: '退出码',   valueType: 'number', description: '退出码，0 表示成功' },
  ],
  configFields: [
    { key: 'command', label: '命令',       type: 'string', defaultValue: 'kiro-cli', placeholder: 'kiro-cli' },
    { key: 'args',    label: '固定参数',   type: 'string', defaultValue: '--no-interactive --trust-all-tools', placeholder: '--no-interactive' },
    { key: 'timeout', label: '超时（秒）', type: 'number', defaultValue: 2400 },
  ],
}
