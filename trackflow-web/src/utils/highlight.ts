/**
 * Search keyword highlighting utilities.
 * Safely escapes HTML and wraps matched keywords in <mark> tags.
 */

/**
 * Escape HTML special characters to prevent XSS when using v-html.
 */
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

/**
 * Escape special regex characters in a string.
 */
function escapeRegex(str: string): string {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * Highlight keyword occurrences in text by wrapping them with <mark> tags.
 * Returns safe HTML string (input text is HTML-escaped first).
 *
 * @param text - The plain text to highlight within
 * @param keyword - The search keyword to highlight (case-insensitive)
 * @returns HTML string with highlighted keywords, safe for v-html
 */
export function highlightKeyword(text: string, keyword: string): string {
  if (!text) return ''
  if (!keyword || !keyword.trim()) return escapeHtml(text)

  const trimmed = keyword.trim()
  const escaped = escapeHtml(text)
  const keywordEscaped = escapeHtml(trimmed)

  // Build regex from escaped keyword for matching in already-escaped text
  const pattern = escapeRegex(keywordEscaped)
  const regex = new RegExp(`(${pattern})`, 'gi')

  return escaped.replace(regex, '<mark class="search-highlight">$1</mark>')
}

/**
 * Extract a context snippet from text around the first keyword match.
 * Used when a match comes from description/comments and needs to be shown as context.
 *
 * @param text - Full text (e.g., description)
 * @param keyword - Search keyword
 * @param maxLength - Maximum length of the returned snippet (default 120)
 * @returns A snippet with the keyword match in context, or empty string if no match
 */
export function extractMatchContext(text: string, keyword: string, maxLength = 120): string {
  if (!text || !keyword || !keyword.trim()) return ''

  const trimmed = keyword.trim().toLowerCase()
  const lowerText = text.toLowerCase()
  const idx = lowerText.indexOf(trimmed)

  if (idx === -1) return ''

  // Calculate context window
  const contextBefore = 30
  const start = Math.max(0, idx - contextBefore)
  const end = Math.min(text.length, start + maxLength)

  let snippet = text.slice(start, end)
  if (start > 0) snippet = '...' + snippet
  if (end < text.length) snippet = snippet + '...'

  return snippet
}
