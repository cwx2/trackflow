import { defineWidget } from '../registry'
import SprintProgressWidget from '../../views/report/dashboard/widgets/SprintProgressWidget.vue'

export default defineWidget({
  type: 'sprint_progress',
  label: 'Sprint 进度',
  icon: '🏃',
  description: 'Sprint 完成进度条',
  defaultTitle: 'Sprint 进度',
  group: 'agile',
  defaultWidth: 4,
  defaultHeight: 2,
  dataSources: ['sprints'],
  configSchema: [
    {
      key: 'sprintId',
      label: 'Sprint',
      type: 'sprint-select',
      required: true,
      placeholder: '选择 Sprint',
      hint: '选择要显示进度的 Sprint'
    }
  ],
  component: SprintProgressWidget
})
