import { defineWidget } from '../registry'
import NoteWidget from '../../views/report/dashboard/widgets/NoteWidget.vue'

export default defineWidget({
  type: 'note',
  label: '快捷笔记',
  icon: '📝',
  description: '自由编辑 Markdown 内容',
  defaultTitle: '笔记',
  group: 'basic',
  defaultWidth: 4,
  defaultHeight: 3,
  configSchema: [
    {
      key: 'content',
      label: '内容',
      type: 'textarea',
      placeholder: '支持简单 HTML 标签'
    }
  ],
  component: NoteWidget
})
