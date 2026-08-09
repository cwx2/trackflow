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
 * 处理含有内嵌 Markdown 语法的 HTML 内容。
 *
 * 某些评论通过 API/seed 数据创建时，内容被简单地包裹在 <p> 标签中，
 * 但内部文本仍保留 Markdown 语法（如 **bold**）和字面 \n。
 * 本函数将此类内容还原为纯 Markdown 文本后重新渲染。
 */
export function renderHtmlWithMarkdown(html: string): string {
  if (!html) return ''

  // 检测 HTML 内容中是否存在未渲染的 Markdown 语法
  // 常见模式：**text**、*text*、```code```、## heading、[link](url)
  const markdownPatterns = /\*\*[^*]+\*\*|\*[^*]+\*|```[\s\S]*?```|^#{1,6}\s|\[[^\]]+\]\([^)]+\)/m

  // 检测字面 \n（JSON 双转义后在 HTML 中显示为文本 \n）
  const literalNewline = /\\n/

  const hasMarkdown = markdownPatterns.test(html)
  const hasLiteralNewline = literalNewline.test(html)

  if (!hasMarkdown && !hasLiteralNewline) {
    // 纯 HTML 内容，无需处理，直接返回（已由 DOMPurify 清洗）
    return DOMPurify.sanitize(html)
  }

  // 将 HTML 结构转换回纯文本/Markdown：
  // 1. <p>...</p> → 内容 + 双换行
  // 2. <br> → 换行
  // 3. 其他标签保留其文本内容
  let text = html
    .replace(/<p[^>]*>\s*<\/p>/gi, '\n') // 空 <p> 标签 → 换行
    .replace(/<\/p>\s*<p[^>]*>/gi, '\n\n') // </p><p> → 双换行
    .replace(/<p[^>]*>/gi, '') // 移除开始 <p>
    .replace(/<\/p>/gi, '\n\n') // 结束 </p> → 双换行
    .replace(/<br\s*\/?>/gi, '\n') // <br> → 换行
    .replace(/<blockquote[^>]*>/gi, '> ') // blockquote 开始
    .replace(/<\/blockquote>/gi, '\n') // blockquote 结束
    .replace(/<[^>]+>/g, '') // 移除所有剩余 HTML 标签

  // 处理字面 \n（数据中存储为 \\n，在页面上显示为文本 \n）
  text = text.replace(/\\n/g, '\n')

  // 清理多余空行
  text = text.replace(/\n{3,}/g, '\n\n').trim()

  // 通过 Markdown 渲染管道重新生成 HTML
  return renderMarkdown(text)
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
