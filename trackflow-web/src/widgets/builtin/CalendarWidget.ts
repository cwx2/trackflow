import { defineWidget } from '../registry'
import CalendarWidget from '../../views/report/dashboard/widgets/CalendarWidget.vue'

export default defineWidget({
  type: 'calendar',
  label: '到期日历',
  icon: '📅',
  description: 'Issue 到期日期日历视图',
  defaultTitle: '到期日历',
  group: 'basic',
  defaultWidth: 6,
  defaultHeight: 4,
  dataSources: ['projects'],
  configSchema: [
    {
      key: 'projectId',
      label: '项目',
      type: 'project-select',
      required: true,
      placeholder: '选择项目（必填）',
      hint: '选择要显示到期日历的项目'
    }
  ],
  component: CalendarWidget
})
