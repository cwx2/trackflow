import { ref, type Ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { handleApiError } from '@/utils/errorHandler'
import { issueApi } from '@/api'

export interface ExportOptions {
  activeProjectId: Ref<string | null>
  filterProject: Ref<string | undefined>
  hideResolved: Ref<boolean>
  globalFilterParams: Ref<Record<string, any>>
  searchKeyword: Ref<string>
  selectedIds: Ref<Set<string>>
}

export function useIssueExport(options: ExportOptions) {
  const {
    activeProjectId, filterProject, hideResolved,
    globalFilterParams, searchKeyword, selectedIds
  } = options

  const exportLoading = ref(false)

  async function handleExport(format: string | number | Record<string, any> | undefined) {
    await doExport(String(format), false)
  }

  function onBatchExport(format: string) {
    doExport(format, true)
  }

  async function doExport(format: string, selectedOnly: boolean) {
    if (format !== 'xlsx' && format !== 'csv') return
    exportLoading.value = true
    try {
      const payload: Record<string, any> = { format }

      if (selectedOnly && selectedIds.value.size > 0) {
        // 选中导出模式
        payload.issueIds = [...selectedIds.value]
      } else {
        // 筛选导出模式：复用当前筛选条件
        if (activeProjectId.value) payload.projectId = activeProjectId.value
        if (filterProject.value) payload.projectId = filterProject.value
        if (hideResolved.value) payload.hideResolved = 'true'
        // Merge global filter params
        const fp = globalFilterParams.value
        if (fp.statusId) payload.statusId = fp.statusId
        if (fp.priority) payload.priority = fp.priority
        if (fp.assigneeId) payload.assigneeId = fp.assigneeId
        if (fp.sprintId) payload.sprintId = fp.sprintId
        if (fp.sprintStatus) payload.sprintStatus = fp.sprintStatus
        if (fp.issueType) payload.issueType = fp.issueType
        if (fp.keyword) payload.keyword = fp.keyword
        if (fp.statusIdNot) payload.statusIdNot = fp.statusIdNot
        if (fp.priorityNot) payload.priorityNot = fp.priorityNot
        if (fp.assigneeIdNot) payload.assigneeIdNot = fp.assigneeIdNot
        if (fp.sprintIdNot) payload.sprintIdNot = fp.sprintIdNot
        if (fp.issueTypeNot) payload.issueTypeNot = fp.issueTypeNot
        if (searchKeyword.value.trim()) payload.keyword = searchKeyword.value.trim()
      }

      const response = await issueApi.export(payload as any)
      // response 是 Blob（responseType: 'blob'）
      const blob = response instanceof Blob ? response : new Blob([response as any])
      const ext = format === 'xlsx' ? '.xlsx' : '.csv'
      const filename = `TrackFlow_Issues_${new Date().toISOString().slice(0, 10)}${ext}`

      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = filename
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      URL.revokeObjectURL(url)

      Message.success(`导出成功 (${format.toUpperCase()})`)
    } catch (e: any) {
      // Blob error response needs special handling
      if (e.response?.data instanceof Blob) {
        const text = await e.response.data.text()
        try {
          const json = JSON.parse(text)
          Message.error(json.message || '导出失败')
        } catch {
          Message.error('导出失败')
        }
      } else {
        handleApiError(e, '导出失败')
      }
    } finally {
      exportLoading.value = false
    }
  }

  return {
    exportLoading,
    handleExport,
    onBatchExport,
    doExport
  }
}
