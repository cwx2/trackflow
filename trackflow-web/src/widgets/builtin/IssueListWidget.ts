import { defineWidget } from '../registry'
import IssueListWidget from '../../views/report/dashboard/widgets/IssueListWidget.vue'

export default defineWidget({
  type: 'issue_list',
  label: 'Issue 列表',
  icon: '📋',
  description: '按条件展示工单列表',
  defaultTitle: 'Issue 列表',
  group: 'basic',
  defaultWidth: 4,
  defaultHeight: 3,
  dataSources: ['projects'],
  configSchema: [
    {
      key: 'queryType',
      label: '查询类型',
      type: 'select',
      required: true,
      defaultValue: 'all',
      options: [
        { label: '所有工单', value: 'all' },
        { label: '未关闭的工单', value: 'open' },
        { label: '已关闭的工单', value: 'closed' },
        { label: '分配给我的未关闭工单', value: 'my_open' }
      ],
      hint: '选择工单列表的查询范围'
    },
    {
      key: 'projectId',
      label: '项目筛选',
      type: 'project-select',
      placeholder: '留空 = 所有项目',
      hint: '限定只展示特定项目的工单，留空表示全部'
    },
    {
      key: 'filterQuery',
      label: '高级过滤（可选）',
      type: 'query-input',
      placeholder: '如 priority:Critical state:Blocked created:today',
      hint: '支持搜索语法，与下拉筛选取 AND 逻辑'
    },
    {
      key: 'pageSize',
      label: '显示条数',
      type: 'number',
      defaultValue: 10,
      min: 5,
      max: 50,
      hint: '5-50 条，默认显示 10 条'
    }
  ],
  component: IssueListWidget
})
