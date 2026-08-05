import { ref, onUnmounted } from 'vue'

/**
 * 生成带认证的文件 URL（Blob URL）
 *
 * 问题背景：
 * 后端 FileController 要求 Bearer Token 认证，
 * 但 HTML <img src="..."> 无法自动携带 Authorization header，
 * 导致图片加载返回 401。
 *
 * 解决方案：通过 fetch + token 获取文件二进制数据，
 * 创建 Blob URL 供 <img> 使用。
 */
export function useAuthenticatedFile() {
  // 追踪所有创建的 blob URL，组件卸载时释放内存
  const blobUrls = ref<string[]>([])

  onUnmounted(() => {
    blobUrls.value.forEach(url => URL.revokeObjectURL(url))
    blobUrls.value = []
  })

  /**
   * 获取带认证的文件 Blob URL
   * @param filePath MinIO 中的对象路径（如 issues/123/attachments/uuid.png）
   * @returns Blob URL 或 null（加载失败时）
   */
  async function getAuthenticatedBlobUrl(filePath: string): Promise<string | null> {
    const token = localStorage.getItem('tf_access_token')
    if (!token) return null

    try {
      const resp = await fetch(`/api/v1/files/${filePath}`, {
        headers: { 'Authorization': `Bearer ${token}` }
      })
      if (!resp.ok) return null

      const blob = await resp.blob()
      const url = URL.createObjectURL(blob)
      blobUrls.value.push(url)
      return url
    } catch {
      return null
    }
  }

  /**
   * 带认证下载文件
   * @param filePath MinIO 中的对象路径
   * @param fileName 保存时使用的文件名
   */
  async function downloadFile(filePath: string, fileName: string): Promise<void> {
    const token = localStorage.getItem('tf_access_token')
    if (!token) return

    try {
      const resp = await fetch(`/api/v1/files/${filePath}`, {
        headers: { 'Authorization': `Bearer ${token}` }
      })
      if (!resp.ok) throw new Error(`Download failed: ${resp.status}`)

      const blob = await resp.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = fileName
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
    } catch (e) {
      console.error('File download failed:', e)
      throw e
    }
  }

  return {
    getAuthenticatedBlobUrl,
    downloadFile,
    blobUrls
  }
}

/**
 * 批量加载附件缩略图的 Blob URL 映射
 * 返回一个 reactive Map<filePath, blobUrl>
 */
export function useAttachmentThumbnails() {
  const thumbnailMap = ref<Record<string, string>>({})
  const loadingSet = ref<Set<string>>(new Set())

  onUnmounted(() => {
    Object.values(thumbnailMap.value).forEach(url => URL.revokeObjectURL(url))
    thumbnailMap.value = {}
  })

  /**
   * 加载单个缩略图
   */
  async function loadThumbnail(filePath: string): Promise<void> {
    if (thumbnailMap.value[filePath] || loadingSet.value.has(filePath)) return

    loadingSet.value.add(filePath)
    const token = localStorage.getItem('tf_access_token')
    if (!token) {
      loadingSet.value.delete(filePath)
      return
    }

    try {
      const resp = await fetch(`/api/v1/files/${filePath}`, {
        headers: { 'Authorization': `Bearer ${token}` }
      })
      if (!resp.ok) {
        loadingSet.value.delete(filePath)
        return
      }

      const blob = await resp.blob()
      const url = URL.createObjectURL(blob)
      thumbnailMap.value[filePath] = url
    } catch {
      // 静默失败，图片将不显示
    } finally {
      loadingSet.value.delete(filePath)
    }
  }

  /**
   * 批量加载多个缩略图
   */
  async function loadThumbnails(filePaths: string[]): Promise<void> {
    const toLoad = filePaths.filter(fp => !thumbnailMap.value[fp] && !loadingSet.value.has(fp))
    await Promise.allSettled(toLoad.map(fp => loadThumbnail(fp)))
  }

  /**
   * 清除不再需要的缩略图 URL（释放内存）
   */
  function clearThumbnail(filePath: string): void {
    const url = thumbnailMap.value[filePath]
    if (url) {
      URL.revokeObjectURL(url)
      delete thumbnailMap.value[filePath]
    }
  }

  return {
    thumbnailMap,
    loadingSet,
    loadThumbnail,
    loadThumbnails,
    clearThumbnail
  }
}
