import { defineWidget } from '../registry'
import ActivityFeedWidget from '../../views/report/dashboard/widgets/ActivityFeedWidget.vue'

export default defineWidget({
  type: 'activity_feed',
  label: '活动流',
  icon: '🔔',
  description: '最近的 Issue 活动（评论/状态变更）',
  defaultTitle: '最近活动',
  group: 'basic',
  defaultWidth: 4,
  defaultHeight: 3,
  dataSources: ['projects', 'users'],
  configSchema: [
    {
      key: 'projectIds',
      label: '项目范围',
      type: 'multi-project-select',
      placeholder: '留空 = 所有可见项目',
      hint: '选择要监控的项目，留空表示所有项目',
      maxTagCount: 3
    },
    {
      key: 'actions',
      label: '活动类型',
      type: 'multi-select',
      placeholder: '留空 = 所有类型',
      hint: '选择要显示的活动类型，留空表示全部',
      maxTagCount: 3,
      options: [
        { label: '创建工单', value: 'create' },
        { label: '状态变更', value: 'status_change' },
        { label: '添加评论', value: 'comment' },
        { label: '修改字段', value: 'field_change' },
        { label: '分配/取消分配', value: 'assign' },
        { label: '上传附件', value: 'attachment' },
        { label: '添加/移除标签', value: 'tag' },
        { label: '添加/移除关联', value: 'link' },
        { label: '删除工单', value: 'delete' }
      ]
    },
    {
      key: 'userIds',
      label: '用户范围',
      type: 'multi-user-select',
      placeholder: '留空 = 所有人',
      hint: '选择要关注的用户，留空表示所有人',
      maxTagCount: 3
    },
    {
      key: 'limit',
      label: '显示条数',
      type: 'number',
      defaultValue: 10,
      min: 1,
      max: 50,
      hint: '1-50 条，默认显示最近 10 条'
    }
  ],
  component: ActivityFeedWidget
})
