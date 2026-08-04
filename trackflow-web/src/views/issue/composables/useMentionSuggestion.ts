import { VueRenderer } from '@tiptap/vue-3'
import type { SuggestionOptions, SuggestionProps } from '@tiptap/suggestion'
import tippy, { type Instance as TippyInstance } from 'tippy.js'
import MentionList from '../components/MentionList.vue'
import { projectApi } from '@/api'
import type { ProjectMemberVO } from '@/api/types'

export interface MentionItem {
  id: string
  username: string
  displayName?: string
}

function debounce<T extends (...args: any[]) => Promise<any>>(fn: T, delay: number): T {
  let timer: ReturnType<typeof setTimeout> | null = null
  let resolve: ((v: any) => void) | null = null
  return ((...args: any[]) => {
    if (timer) clearTimeout(timer)
    return new Promise((res) => {
      resolve = res
      timer = setTimeout(async () => {
        const result = await fn(...args)
        resolve?.(result)
      }, delay)
    })
  }) as T
}

/**
 * 创建 Tiptap Mention 扩展的 suggestion 配置
 * 采用按需搜索（懒加载）：每次输入关键词后请求后端，支持大规模成员列表。
 * @param projectId 项目ID
 */
export function useMentionSuggestion(projectId: () => string | undefined) {
  async function fetchMembers(keyword: string): Promise<MentionItem[]> {
    const pid = projectId()
    if (!pid) return []
    try {
      const res = await projectApi.searchMembers(pid, keyword, 10)
      if (res.code === 0 && res.data) {
        return res.data.map((m: ProjectMemberVO) => ({
          id: m.userId,
          username: m.username,
          displayName: m.displayName || m.username
        }))
      }
    } catch {
      // 静默失败
    }
    return []
  }

  // debounce 300ms，避免每次击键都发请求
  const debouncedFetch = debounce(fetchMembers, 300)

  const suggestion: Omit<SuggestionOptions<MentionItem>, 'editor'> = {
    char: '@',
    allowSpaces: false,

    items: async ({ query }) => {
      return debouncedFetch(query)
    },

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
          return (component?.ref as any)?.onKeyDown?.(props) ?? false
        },

        onExit: () => {
          popup?.[0]?.destroy()
          component?.destroy()
        }
      }
    }
  }

  return { suggestion }
}
