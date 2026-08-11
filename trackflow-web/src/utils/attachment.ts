/**
 * 附件上传校验工具
 *
 * 与后端 AttachmentConfig 保持同步的客户端校验，
 * 在上传前给出即时反馈，减少无效网络请求。
 */

/** 最大文件大小：50MB */
export const MAX_FILE_SIZE = 50 * 1024 * 1024

/** 单工单最大附件数量 */
export const MAX_ATTACHMENTS_PER_ISSUE = 50

/** 禁止上传的文件扩展名（与后端黑名单同步） */
export const BLOCKED_EXTENSIONS = new Set([
  'exe', 'bat', 'cmd', 'sh', 'ps1', 'vbs', 'js', 'msi',
  'dll', 'com', 'scr', 'pif', 'hta', 'cpl', 'inf', 'reg',
  'ws', 'wsf', 'wsc', 'lnk'
])

export interface FileValidationResult {
  valid: boolean
  message?: string
}

/**
 * 校验文件是否允许上传
 */
export function validateFile(file: File): FileValidationResult {
  // 空文件
  if (file.size === 0) {
    return { valid: false, message: '文件为空，无法上传' }
  }

  // 文件大小
  if (file.size > MAX_FILE_SIZE) {
    const sizeMB = Math.round(file.size / (1024 * 1024))
    return { valid: false, message: `文件大小 ${sizeMB}MB 超出限制（最大 50MB）` }
  }

  // 文件扩展名
  const ext = getExtension(file.name)
  if (ext && BLOCKED_EXTENSIONS.has(ext)) {
    return { valid: false, message: `不允许上传 .${ext} 类型的文件` }
  }

  return { valid: true }
}

/**
 * 校验附件数量是否超限
 */
export function validateAttachmentCount(currentCount: number): FileValidationResult {
  if (currentCount >= MAX_ATTACHMENTS_PER_ISSUE) {
    return { valid: false, message: `附件数量已达上限（最多 ${MAX_ATTACHMENTS_PER_ISSUE} 个）` }
  }
  return { valid: true }
}

/**
 * 获取人类可读的文件大小限制描述
 */
export function getMaxFileSizeText(): string {
  return '50MB'
}

/**
 * 获取允许的文件类型提示文本（排除黑名单后的说明）
 */
export function getBlockedExtensionsText(): string {
  return Array.from(BLOCKED_EXTENSIONS).map(ext => `.${ext}`).join(', ')
}

function getExtension(filename: string): string {
  const dot = filename.lastIndexOf('.')
  if (dot < 0) return ''
  return filename.substring(dot + 1).toLowerCase()
}


/**
 * 将字节数格式化为人类可读的文件大小。
 * 例：1536 → "1.5 KB"，2097152 → "2.0 MB"
 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`
}
