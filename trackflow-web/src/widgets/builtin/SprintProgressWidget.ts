import { defineWidget } from '../registry'
import { defineComponent, h } from 'vue'

// Sprint Progress 目前没有独立渲染组件，显示占位提示
const SprintProgressPlaceholder = defineComponent({
  name: 'SprintProgressPlaceholder',
  setup() {
    return () => h('div', { class: 'widget-configure-hint', style: 'display:flex;flex-direction:column;align-items:center;justify-content:center;flex:1;gap:8px;' }, [
      h('span', { style: 'font-size:32px;opacity:0.4;' }, '🏃'),
      h('span', { style: 'font-size:11px;color:var(--tf-text-tertiary);text-align:center;' }, '点击「编辑配置」选择 Sprint')
    ])
  }
})

export default defineWidget({
  type: 'sprint_progress',
  label: 'Sprint 进度',
  icon: '🏃',
  description: 'Sprint 完成进度条',
  defaultTitle: 'Sprint 进度',
  group: 'agile',
  defaultWidth: 4,
  defaultHeight: 2,
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
  component: SprintProgressPlaceholder
})
