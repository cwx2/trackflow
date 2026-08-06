import { defineWidget } from '../registry'
import ProjectTeamWidget from '../../views/report/dashboard/widgets/ProjectTeamWidget.vue'

export default defineWidget({
  type: 'project_team',
  label: '项目成员',
  icon: '👥',
  description: '展示项目团队成员及各自未关闭工单数',
  defaultTitle: '项目成员',
  group: 'basic',
  defaultWidth: 4,
  defaultHeight: 3,
  dataSources: ['projects'],
  configSchema: [
    {
      key: 'projectId',
      label: '项目',
      type: 'project-select',
      required: true,
      placeholder: '选择项目（必填）',
      hint: '选择要展示团队成员的项目'
    },
    {
      key: 'limit',
      label: '显示条数',
      type: 'number',
      min: 1,
      max: 50,
      placeholder: '留空 = 显示全部',
      hint: '限制显示的成员数量，留空表示全部'
    }
  ],
  component: ProjectTeamWidget
})
