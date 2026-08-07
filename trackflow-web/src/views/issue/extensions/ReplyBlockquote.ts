/**
 * 自定义 Tiptap Blockquote 扩展，支持 data-reply-to-comment-id 属性。
 * 用于在评论回复引用块中保留被引用评论的 ID，实现点击引用跳转到原始评论。
 */
import Blockquote from '@tiptap/extension-blockquote'

export const ReplyBlockquote = Blockquote.extend({
  addAttributes() {
    return {
      'data-reply-to-comment-id': {
        default: null,
        // 从 DOM 解析时读取 data-reply-to-comment-id 属性
        parseHTML: (element) => element.getAttribute('data-reply-to-comment-id'),
        // 渲染为 HTML 时输出 data-reply-to-comment-id 属性
        renderHTML: (attributes) => {
          if (!attributes['data-reply-to-comment-id']) {
            return {}
          }
          return { 'data-reply-to-comment-id': attributes['data-reply-to-comment-id'] }
        },
      },
    }
  },
})
