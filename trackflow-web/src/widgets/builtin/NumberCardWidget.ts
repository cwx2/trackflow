import { defineWidget } from '../registry'
import NumberCardWidget from '../../views/report/dashboard/widgets/NumberCardWidget.vue'

export default defineWidget({
  type: 'number_card',
  label: '数字卡片',
  icon: '🔢',
  description: '单数字大卡片（如"本月完成: 88"）',
  defaultTitle: '统计',
  group: 'basic',
  defaultWidth: 3,
  defaultHeight: 2,
  configSchema: [
    {
      key: 'queryType',
      label: '数据来源',
      type: 'select',
      required: true,
      defaultValue: 'total',
      placeholder: '选择统计指标',
      options: [
        { label: '工单总数', value: 'total' },
        { label: '待处理工单数', value: 'open' },
        { label: '已完成工单数', value: 'closed' },
        { label: '未分配工单数', value: 'unassigned' },
        { label: '已逾期工单数', value: 'overdue' },
        { label: '完成率 (%)', value: 'completion_rate' }
      ]
    },
    {
      key: 'value',
      label: '自定义数值（留空则自动从后端获取）',
      type: 'number',
      placeholder: '留空=动态数据',
      min: 0
    },
    {
      key: 'label',
      label: '副标签',
      type: 'text',
      placeholder: '如「Open Bugs」'
    }
  ],
  component: NumberCardWidget
})
