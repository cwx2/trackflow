import type { NodeDefinition } from './types'

export const httpRequestDefinition: NodeDefinition = {
  type: 'http-request',
  meta: {
    title: 'HTTP 请求',
    icon: '🌐',
    color: '#0891b2',
    description: '发送 HTTP 请求，调用外部 API 或服务',
    category: '业务逻辑',
  },
  inputPorts: [
    { name: 'url',  label: '请求地址', valueType: 'string', required: true,  description: '请求 URL，支持变量引用' },
    { name: 'body', label: '请求体',   valueType: 'string', required: false, description: 'POST/PUT 请求体（JSON 字符串）' },
  ],
  outputPorts: [
    { name: 'responseBody', label: '响应体',   valueType: 'string', description: 'HTTP 响应 body 内容' },
    { name: 'statusCode',   label: '状态码',   valueType: 'number', description: 'HTTP 状态码，如 200、404' },
  ],
  configFields: [
    {
      key: 'method',
      label: '请求方法',
      type: 'select',
      defaultValue: 'GET',
      options: [
        { label: 'GET',    value: 'GET'    },
        { label: 'POST',   value: 'POST'   },
        { label: 'PUT',    value: 'PUT'    },
        { label: 'DELETE', value: 'DELETE' },
        { label: 'PATCH',  value: 'PATCH'  },
      ],
    },
    {
      key: 'headers',
      label: '请求头（JSON）',
      type: 'textarea',
      defaultValue: '{"Content-Type": "application/json"}',
      placeholder: '{"Authorization": "Bearer xxx"}',
    },
    { key: 'timeout', label: '超时（秒）', type: 'number', defaultValue: 30 },
  ],
}
