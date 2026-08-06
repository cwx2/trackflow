import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { sprintApi } from '@/api'
import type { SprintVO } from '@/api/types'

/**
 * Sprint 操作逻辑——提取自 SprintView 的动作处理方法。
 * 封装激活/归档/恢复/回退/内联重命名等常用操作。
 */
export function useSprintActions(options: {
  sprints: ReturnType<typeof ref<SprintVO[]>>
  selectedProject: ReturnType<typeof computed<string | null>>
  projects: ReturnType<typeof ref<Array<{ id: string; key?: string }>>>
  loadSprints: () => Promise<void> | void
}) {
  const { sprints, selectedProject, projects, loadSprints } = options
  const router = useRouter()

  const activeSprints = computed(() => sprints.value.filter(s => {
    const status = s.status?.toLowerCase()
    return status === 'active'
  }))

  const hasActiveSprint = computed(() => activeSprints.value.length > 0)

  /**
   * 判断 Sprint 是否不可启动（开始日期未到/结束日期已过）
   */
  function isSprintNotStartable(sprint: SprintVO): boolean {
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    if (sprint.startDate) {
      const start = new Date(sprint.startDate)
      start.setHours(0, 0, 0, 0)
      if (start.getTime() > today.getTime()) return true
    }
    if (sprint.endDate) {
      const end = new Date(sprint.endDate)
      end.setHours(0, 0, 0, 0)
      if (end.getTime() < today.getTime()) return true
    }
    return false
  }

  /**
   * 获取激活按钮 tooltip
   */
  function getActivateTooltip(sprint: SprintVO, canEdit: boolean): string | undefined {
    if (!canEdit) return '您的角色不具有迭代管理权限，请联系项目管理员'
    if (hasActiveSprint.value) {
      const active = activeSprints.value[0]
      return `需要先完成当前活跃迭代「${active.name}」才能激活此迭代`
    }
    if (sprint.endDate) {
      const end = new Date(sprint.endDate)
      const today = new Date()
      today.setHours(0, 0, 0, 0)
      end.setHours(0, 0, 0, 0)
      if (end.getTime() < today.getTime()) return `结束日期（${formatDate(sprint.endDate)}）已过期，无法激活`
    }
    if (sprint.startDate) {
      const start = new Date(sprint.startDate)
      const today = new Date()
      today.setHours(0, 0, 0, 0)
      start.setHours(0, 0, 0, 0)
      if (start.getTime() > today.getTime()) return `开始日期（${formatDate(sprint.startDate)}）尚未到达`
    }
    return undefined
  }

  /**
   * 处理"开始迭代"点击
   */
  function handleActivateSprint(id: string) {
    if (hasActiveSprint.value) {
      const active = activeSprints.value[0]
      Modal.warning({
        title: '无法激活迭代',
        content: `当前项目已有一个活跃的迭代「${active.name}」正在进行中。请先完成该迭代后再激活新的迭代。`,
        okText: '我知道了',
        hideCancel: true,
      })
      return
    }
    activateSprint(id)
  }

  async function activateSprint(id: string) {
    try {
      await sprintApi.activate(id)
      Message.success('迭代已开始')
      loadSprints()
    } catch (e: any) {
      Message.error(e.response?.data?.message || '操作失败')
    }
  }

  async function archiveSprint(sprint: SprintVO) {
    try {
      await sprintApi.archive(sprint.id)
      Message.success('迭代已归档')
      loadSprints()
    } catch (e: any) {
      Message.error(e.response?.data?.message || '归档失败')
    }
  }

  async function handleArchiveActiveSprint(sprint: SprintVO) {
    const confirmed = await new Promise<boolean>((resolve) => {
      Modal.confirm({
        title: '确认归档进行中的迭代',
        content: `迭代「${sprint.name}」当前仍在进行中，确认要归档吗？归档后工单将保留原迭代关联。`,
        okText: '确认归档',
        cancelText: '取消',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      })
    })
    if (confirmed) {
      await archiveSprint(sprint)
    }
  }

  async function restoreSprint(sprint: SprintVO) {
    try {
      await sprintApi.restore(sprint.id)
      Message.success('迭代已恢复')
      loadSprints()
    } catch (e: any) {
      Message.error(e.response?.data?.message || '恢复失败')
    }
  }

  function handleRevertToPlanned(sprint: SprintVO) {
    Modal.confirm({
      title: '回退迭代状态',
      content: `确定将迭代「${sprint.name}」回退为"计划中"状态？\n\n回退后可以修改日期，并在合适的时间重新激活。`,
      okText: '确认回退',
      cancelText: '取消',
      onOk: async () => {
        try {
          await sprintApi.revertToPlanned(sprint.id)
          Message.success('迭代已回退为计划中')
          loadSprints()
        } catch (e: any) {
          Message.error(e.response?.data?.message || '操作失败')
        }
      }
    })
  }

  /**
   * 内联重命名 Sprint
   */
  async function inlineRenameSprint(sprintId: string, newName: string) {
    try {
      await sprintApi.update(sprintId, { name: newName })
      const idx = sprints.value.findIndex(s => s.id === sprintId)
      if (idx !== -1) {
        sprints.value[idx] = { ...sprints.value[idx], name: newName }
      }
      Message.success('迭代名称已更新')
    } catch (e: any) {
      Message.error(e.response?.data?.message || '更新失败')
    }
  }

  /**
   * 查看 Sprint 工单（跳转到 Issue 列表）
   */
  function viewSprintIssues(sprint: SprintVO) {
    const currentProject = projects.value.find(p => p.id === selectedProject.value)
    const projectKey = currentProject?.key || sprint.projectKey
    const query: Record<string, string> = { sprint: sprint.id, label: sprint.name }
    if (projectKey) query.project = projectKey
    router.push({ path: '/issues', query })
  }

  /**
   * 查看 Sprint 看板
   */
  function viewSprintOnBoard(sprint: SprintVO) {
    const currentProject = projects.value.find(p => p.id === selectedProject.value)
    const projectKey = currentProject?.key || sprint.projectKey
    const query: Record<string, string> = { sprint: sprint.id }
    if (projectKey) query.project = projectKey
    router.push({ path: '/boards', query })
  }

  /**
   * 按工单分类查看
   */
  function viewIssuesByCategory(sprint: SprintVO, category: 'done' | 'in_progress' | 'open') {
    const currentProject = projects.value.find(p => p.id === selectedProject.value)
    const projectKey = currentProject?.key || sprint.projectKey
    const categoryLabels: Record<string, string> = { done: '已完成', in_progress: '进行中', open: '待办' }
    const query: Record<string, string> = {
      sprint: sprint.id,
      statusCategory: category,
      label: `${sprint.name} - ${categoryLabels[category]}工单`
    }
    if (projectKey) query.project = projectKey
    router.push({ path: '/issues', query })
  }

  /**
   * 查看逾期工单
   */
  function viewOverdueIssues(sprint: SprintVO) {
    const currentProject = projects.value.find(p => p.id === selectedProject.value)
    const projectKey = currentProject?.key || sprint.projectKey
    const query: Record<string, string> = {
      sprint: sprint.id,
      overdue: 'true',
      label: `${sprint.name} - 逾期工单`
    }
    if (projectKey) query.project = projectKey
    router.push({ path: '/issues', query })
  }

  function formatDate(dateStr?: string): string {
    if (!dateStr) return ''
    const d = new Date(dateStr)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  }

  return {
    activeSprints,
    hasActiveSprint,
    isSprintNotStartable,
    getActivateTooltip,
    handleActivateSprint,
    activateSprint,
    archiveSprint,
    handleArchiveActiveSprint,
    restoreSprint,
    handleRevertToPlanned,
    inlineRenameSprint,
    viewSprintIssues,
    viewSprintOnBoard,
    viewIssuesByCategory,
    viewOverdueIssues,
    formatDate
  }
}
