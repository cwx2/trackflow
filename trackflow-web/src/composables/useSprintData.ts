import { ref, computed, type ComputedRef } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { sprintApi } from '@/api'
import { preloadPermissions } from '@/composables/usePermission'
import type { SprintVO } from '@/api/types'
import { isSprintNotStartable } from './useSprintNavigation'

/**
 * Sprint 数据加载和状态操作的 composable
 */
export function useSprintData(selectedProject: ComputedRef<string | null | undefined>) {
  const sprints = ref<SprintVO[]>([])
  type LoadingState = 'idle' | 'loading' | 'success' | 'error' | 'forbidden'
  const loadingState = ref<LoadingState>('idle')

  const activeSprints = computed(() => sprints.value.filter(s => s.status === 'active' || s.status === 'Active'))
  const plannedSprints = computed(() => sprints.value.filter(s => s.status === 'planned' || s.status === 'Planned'))
  const completedSprints = computed(() => sprints.value.filter(s => s.status === 'completed' || s.status === 'Completed'))
  const archivedSprints = computed(() => sprints.value.filter(s => s.status === 'archived' || s.status === 'Archived'))
  const hasActiveSprint = computed(() => activeSprints.value.length > 0)
  const nextStartableSprint = computed(() => plannedSprints.value.find(s => !isSprintNotStartable(s)) || null)

  async function loadSprints() {
    loadingState.value = 'loading'
    try {
      if (selectedProject.value) {
        const res = await sprintApi.listByProject(selectedProject.value, { pageSize: 200, _silent403: true })
        sprints.value = res.data?.list || []
      } else {
        const res = await sprintApi.listAll({ pageSize: 200 })
        sprints.value = res.data?.list || []
      }
      loadingState.value = 'success'
      const projectIds = [...new Set(sprints.value.map(s => s.projectId).filter(Boolean))]
      if (projectIds.length > 0) {
        await preloadPermissions(projectIds)
        sprints.value = [...sprints.value]
      }
    } catch (e: any) {
      sprints.value = []
      loadingState.value = e?.response?.status === 403 ? 'forbidden' : 'error'
    }
  }

  function handleActivateSprint(id: string) {
    if (hasActiveSprint.value) {
      Modal.warning({
        title: '无法激活迭代',
        content: `当前项目已有一个活跃的迭代「${activeSprints.value[0].name}」正在进行中。请先完成该迭代后再激活新的迭代。`,
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

  async function handleRevertToPlanned(sprint: SprintVO) {
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
    if (confirmed) await archiveSprint(sprint)
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

  async function inlineRenameSprint(sprintId: string, newName: string) {
    try {
      await sprintApi.update(sprintId, { name: newName })
      const idx = sprints.value.findIndex(s => s.id === sprintId)
      if (idx !== -1) sprints.value[idx] = { ...sprints.value[idx], name: newName }
      Message.success('迭代名称已更新')
    } catch (e: any) {
      Message.error(e.response?.data?.message || '更新失败')
    }
  }

  return {
    sprints,
    loadingState,
    activeSprints,
    plannedSprints,
    completedSprints,
    archivedSprints,
    hasActiveSprint,
    nextStartableSprint,
    loadSprints,
    handleActivateSprint,
    handleRevertToPlanned,
    archiveSprint,
    handleArchiveActiveSprint,
    restoreSprint,
    inlineRenameSprint
  }
}
