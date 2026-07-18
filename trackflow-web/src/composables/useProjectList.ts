import { ref } from 'vue'
import { projectApi } from '@/api'
import type { ProjectVO } from '@/api/types'
import { useProjectStore } from '@/stores/project'

/**
 * 项目列表加载 composable
 *
 * 统一管理项目列表加载的状态（加载中 / 成功 / 失败），
 * 提供自动选择逻辑和重试能力。
 *
 * 使用场景：看板、迭代、创建工单等需要项目选择器的页面。
 *
 * 注意：此 composable 用于下拉选择器场景，一次性加载当前用户可见的全部项目。
 * 后端已通过 project_member 表限制返回结果（非管理员仅返回参与的项目），
 * 因此实际返回量通常远小于 pageSize 上限。
 */
export type ProjectLoadState = 'idle' | 'loading' | 'success' | 'error'

/** 下拉选择器场景的最大加载数量（后端已按成员关系过滤，实际量远小于此值） */
const SELECTOR_PAGE_SIZE = 500

export function useProjectList() {
  const projects = ref<ProjectVO[]>([])
  const projectLoadState = ref<ProjectLoadState>('idle')
  const projectStore = useProjectStore()

  /**
   * 加载当前用户可见的项目列表。
   * 成功后自动调用 projectStore.autoSelectIfNeeded() 尝试自动选中。
   * 收藏的项目排在列表前面。
   *
   * @returns 加载后的项目列表
   */
  async function loadProjects(): Promise<ProjectVO[]> {
    projectLoadState.value = 'loading'
    try {
      const res = await projectApi.list({ pageSize: SELECTOR_PAGE_SIZE })
      const list = res.data?.list || []
      // 收藏项目排前面
      list.sort((a, b) => {
        if (a.favorited && !b.favorited) return -1
        if (!a.favorited && b.favorited) return 1
        return 0
      })
      projects.value = list
      projectLoadState.value = 'success'

      // 自动选择逻辑
      const projectIds = projects.value.map(p => p.id)
      projectStore.autoSelectIfNeeded(projectIds)

      return projects.value
    } catch {
      projects.value = []
      projectLoadState.value = 'error'
      return []
    }
  }

  return {
    projects,
    projectLoadState,
    loadProjects
  }
}
