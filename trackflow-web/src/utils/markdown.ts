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

/**
 * 将 tiptap 编辑器输出的 HTML 转换为 Markdown 文本。
 * 轻量级 regex 实现，专为 tiptap/ProseMirror 的 HTML 结构设计。
 */
export function htmlToMarkdown(html: string): string {
  if (!html) return ''
  let result = html
  result = result.replace(/<h1[^>]*>(.*?)<\/h1>/gi, '# $1\n')
  result = result.replace(/<h2[^>]*>(.*?)<\/h2>/gi, '## $1\n')
  result = result.replace(/<h3[^>]*>(.*?)<\/h3>/gi, '### $1\n')
  result = result.replace(/<strong>(.*?)<\/strong>/gi, '**$1**')
  result = result.replace(/<em>(.*?)<\/em>/gi, '*$1*')
  result = result.replace(/<s>(.*?)<\/s>/gi, '~~$1~~')
  result = result.replace(/<code>(.*?)<\/code>/gi, '`$1`')
  result = result.replace(/<a[^>]*href="([^"]*)"[^>]*>(.*?)<\/a>/gi, '[$2]($1)')
  result = result.replace(/<blockquote[^>]*>(.*?)<\/blockquote>/gis, (_, c) => c.replace(/<p[^>]*>(.*?)<\/p>/gi, '> $1\n'))
  result = result.replace(/<li[^>]*>(.*?)<\/li>/gi, '- $1\n')
  result = result.replace(/<\/?[uo]l[^>]*>/gi, '')
  result = result.replace(/<pre[^>]*><code[^>]*>(.*?)<\/code><\/pre>/gis, '```\n$1\n```\n')
  result = result.replace(/<p[^>]*>(.*?)<\/p>/gi, '$1\n\n')
  result = result.replace(/<br\s*\/?>/gi, '\n')
  result = result.replace(/<[^>]+>/g, '')
  result = result.replace(/\n{3,}/g, '\n\n')
  return result.trim()
}

/**
 * 将 Markdown 文本转换为简易 HTML（供 tiptap 编辑器加载）。
 * 轻量级 regex 实现，覆盖常见 Markdown 语法。
 */
export function markdownToEditorHtml(markdown: string): string {
  if (!markdown) return '<p></p>'
  let html = markdown
  html = html.replace(/^### (.*$)/gm, '<h3>$1</h3>')
  html = html.replace(/^## (.*$)/gm, '<h2>$1</h2>')
  html = html.replace(/^# (.*$)/gm, '<h1>$1</h1>')
  html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\*(.*?)\*/g, '<em>$1</em>')
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>')
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>')
  html = html.replace(/^> (.*$)/gm, '<blockquote><p>$1</p></blockquote>')
  html = html.replace(/^- (.*$)/gm, '<li>$1</li>')
  html = html.split('\n\n').map(p => {
    if (p.startsWith('<h') || p.startsWith('<pre') || p.startsWith('<blockquote') || p.startsWith('<li')) return p
    if (p.trim()) return `<p>${p.replace(/\n/g, '<br>')}</p>`
    return ''
  }).join('')
  html = html.replace(/((<li>.*?<\/li>\s*)+)/g, '<ul>$1</ul>')
  return html || '<p></p>'
}
