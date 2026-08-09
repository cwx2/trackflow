import type { NodeDefinition } from './types'

export const fileInputDefinition: NodeDefinition = {
  type: 'file-input',
  meta: {
    title: '文件输入',
    icon: '📁',
    color: '#8b5cf6',
    description: '读取有访问权限的工单附件内容，输出为字符串',
    category: '数据处理',
  },
  inputPorts: [
    { name: 'attachmentId', label: '附件 ID', valueType: 'number', required: true, description: '工单附件 ID，支持变量引用' },
  ],
  outputPorts: [
    { name: 'content', label: '文件内容', valueType: 'string', description: '文件内容' },
    { name: 'size',    label: '文件大小', valueType: 'number', description: '文件大小（字节）' },
  ],
  configFields: [
    {
      key: 'readMode',
      label: '读取模式',
      type: 'select',
      defaultValue: 'full',
      options: [
        { label: '完整读取', value: 'full' },
        { label: '前1000行', value: 'head' },
        { label: '后1000行', value: 'tail' },
      ],
    },
    { key: 'encoding', label: '编码', type: 'string', defaultValue: 'utf-8', placeholder: 'utf-8' },
  ],
}
