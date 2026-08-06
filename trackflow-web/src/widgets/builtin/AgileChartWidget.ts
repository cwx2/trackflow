import { defineWidget } from '../registry'
import AgileChartWidget from '../../views/report/dashboard/widgets/AgileChartWidget.vue'

export default defineWidget({
  type: 'agile_chart',
  label: '敏捷图表',
  icon: '📉',
  description: '燃尽图或累积流图，跟踪 Sprint 进展趋势',
  defaultTitle: '敏捷图表',
  group: 'agile',
  defaultWidth: 6,
  defaultHeight: 3,
  configSchema: [
    {
      key: 'chartType',
      label: '图表类型',
      type: 'select',
      required: true,
      defaultValue: 'burndown',
      options: [
        { label: '燃尽图 (Burndown)', value: 'burndown' },
        { label: '累积流图 (Cumulative Flow)', value: 'cumulative_flow' }
      ]
    },
    {
      key: 'sprintId',
      label: 'Sprint',
      type: 'sprint-select',
      placeholder: '选择 Sprint',
      hint: '选择要显示燃尽图的 Sprint',
      showWhen: { field: 'chartType', value: 'burndown' }
    },
    {
      key: 'projectId',
      label: '项目',
      type: 'project-select',
      placeholder: '选择项目',
      hint: '选择要显示累积流图的项目',
      showWhen: { field: 'chartType', value: 'cumulative_flow' }
    }
  ],
  component: AgileChartWidget
})
