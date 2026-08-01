import type { NodeDefinition } from './types'

export const codeDefinition: NodeDefinition = {
  type: 'code',
  meta: {
    title: '代码',
    icon: '</>',
    color: '#7c3aed',
    description: '执行 Shell/Python 脚本，处理自定义逻辑',
    category: '业务逻辑',
  },
  inputPorts: [
    { name: 'input', label: '输入数据', valueType: 'object', required: false, description: '传入脚本的数据，作为环境变量注入' },
  ],
  outputPorts: [
    { name: 'result',   label: '执行结果', valueType: 'string', description: '脚本 stdout 输出内容' },
    { name: 'exitCode', label: '退出码',   valueType: 'number', description: '退出码，0 表示成功' },
  ],
  configFields: [
    {
      key: 'language',
      label: '语言',
      type: 'select',
      defaultValue: 'shell',
      options: [
        { label: 'Shell',  value: 'shell'  },
        { label: 'Python', value: 'python' },
      ],
    },
    {
      key: 'script',
      label: '脚本内容',
      type: 'textarea',
      defaultValue: 'echo "hello world"',
      placeholder: '输入要执行的脚本代码',
    },
    { key: 'timeout', label: '超时（秒）', type: 'number', defaultValue: 30 },
  ],
}
