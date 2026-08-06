import { defineWidget } from '../registry'
import BoardStatusWidget from '../../views/report/dashboard/widgets/BoardStatusWidget.vue'

export default defineWidget({
  type: 'agile_board_status',
  label: '看板状态',
  icon: '📊',
  description: 'Sprint 工单状态分布（待处理/进行中/已完成）',
  defaultTitle: '看板状态',
  group: 'agile',
  defaultWidth: 4,
  defaultHeight: 3,
  configSchema: [
    {
      key: 'sprintId',
      label: 'Sprint',
      type: 'sprint-select',
      placeholder: '选择 Sprint',
      hint: '选择要显示状态分布的 Sprint'
    }
  ],
  component: BoardStatusWidget
})
