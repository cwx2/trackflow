import { defineWidget } from '../registry'
import ReportChartWidget from '../../views/report/dashboard/widgets/ReportChartWidget.vue'

// report_distribution 和 report 共用同一个渲染组件
defineWidget({
  type: 'report_distribution',
  label: '分布图表',
  icon: '📊',
  description: '按字段分组的条形图/饼图',
  defaultTitle: '分布报表',
  group: 'report',
  defaultWidth: 4,
  defaultHeight: 3,
  configSchema: [
    {
      key: 'reportId',
      label: '关联报表',
      type: 'report-select',
      placeholder: '选择一个已保存的报表',
      hint: '关联后将自动展示该报表的图表数据'
    }
  ],
  component: ReportChartWidget
})

export default defineWidget({
  type: 'report',
  label: '报表图表',
  icon: '📈',
  description: '关联已保存的报表定义',
  defaultTitle: '报表',
  group: 'report',
  defaultWidth: 4,
  defaultHeight: 3,
  configSchema: [
    {
      key: 'reportId',
      label: '关联报表',
      type: 'report-select',
      placeholder: '选择一个已保存的报表',
      hint: '关联后将自动展示该报表的图表数据'
    }
  ],
  component: ReportChartWidget
})
