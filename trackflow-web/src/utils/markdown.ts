import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: false,
  breaks: true
})

/**
 * 将 Markdown 文本渲染为安全的 HTML
 */
export function renderMarkdown(content: string): string {
  if (!content) return ''
  const raw = md.render(content)
  return DOMPurify.sanitize(raw)
}

/**
 * 将 Markdown 文本渲染为纯文本（去除所有标签）
 */
export function renderPlainText(content: string): string {
  if (!content) return ''
  const html = md.render(content)
  const div = document.createElement('div')
  div.innerHTML = html
  return div.textContent || div.innerText || ''
}
