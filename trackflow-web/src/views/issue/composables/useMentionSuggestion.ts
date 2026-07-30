import { VueRenderer } from '@tiptap/vue-3'
import type { SuggestionOptions, SuggestionProps } from '@tiptap/suggestion'
import tippy, { type Instance as TippyInstance } from 'tippy.js'
import MentionList from '../components/MentionList.vue'
import { projectApi } from '@/api'
import type { ProjectMemberVO } from '@/api/types'
import { ref } from 'vue'

export interface MentionItem {
  id: string
  username: string
  displayName?: string
}

/**
 * 创建 Tiptap Mention 扩展的 suggestion 配置
 * @param projectId 项目ID（用于获取项目成员列表）
 */
export function useMentionSuggestion(projectId: () => string | undefined) {
  // 缓存项目成员列表
  const membersCache = ref<MentionItem[]>([])
  let cacheProjectId: string | undefined = undefined

  async function loadMembers(): Promise<MentionItem[]> {
    const pid = projectId()
    if (!pid) return []
    
    // 如果缓存有效，直接返回
    if (cacheProjectId === pid && membersCache.value.length > 0) {
      return membersCache.value
    }
    
    try {
      const res = await projectApi.listMembers(pid)
      if (res.code === 0 && res.data) {
        membersCache.value = res.data.map((m: ProjectMemberVO) => ({
          id: m.userId,
          username: m.username,
          displayName: m.displayName || m.username
        }))
        cacheProjectId = pid
        return membersCache.value
      }
    } catch {
      // 静默失败
    }
    return []
  }

  const suggestion: Omit<SuggestionOptions<MentionItem>, 'editor'> = {
    char: '@',
    allowSpaces: false,
    
    // 获取候选项
    items: async ({ query }) => {
      const members = await loadMembers()
      const q = query.toLowerCase()
      return members
        .filter(item => 
          item.username.toLowerCase().includes(q) || 
          (item.displayName?.toLowerCase().includes(q) ?? false)
        )
        .slice(0, 10)
    },
    
    // 渲染下拉列表
    render: () => {
      let component: VueRenderer | null = null
      let popup: TippyInstance[] | null = null

      return {
        onStart: (props: SuggestionProps<MentionItem>) => {
          component = new VueRenderer(MentionList, {
            props: {
              items: props.items,
              command: props.command
            },
            editor: props.editor
          })

          if (!props.clientRect) return

          popup = tippy('body', {
            getReferenceClientRect: props.clientRect as () => DOMRect,
            appendTo: () => document.body,
            content: component.element,
            showOnCreate: true,
            interactive: true,
            trigger: 'manual',
            placement: 'bottom-start',
            theme: 'mention',
            animation: 'shift-away',
            maxWidth: 'none'
          })
        },

        onUpdate: (props: SuggestionProps<MentionItem>) => {
          component?.updateProps({
            items: props.items,
            command: props.command
          })

          if (!props.clientRect) return

          popup?.[0]?.setProps({
            getReferenceClientRect: props.clientRect as () => DOMRect
          })
        },

        onKeyDown: (props: { event: KeyboardEvent }) => {
          if (props.event.key === 'Escape') {
            popup?.[0]?.hide()
            return true
          }

          // 将键盘事件传递给 MentionList 组件
          return (component?.ref as any)?.onKeyDown?.(props) ?? false
        },

        onExit: () => {
          popup?.[0]?.destroy()
          component?.destroy()
        }
      }
    }
  }

  // 清空缓存（当 projectId 变化时调用）
  function clearCache() {
    membersCache.value = []
    cacheProjectId = undefined
  }

  return {
    suggestion,
    clearCache
  }
}
