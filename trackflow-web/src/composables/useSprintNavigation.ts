import { type Ref } from 'vue'
import { useRouter } from 'vue-router'
import type { SprintVO, ProjectVO } from '@/api/types'

/**
 * Sprint 视图中的导航和工具函数
 */
export function useSprintNavigation(
  selectedProject: Ref<string | null | undefined>,
  projects: Ref<ProjectVO[]>
) {
  const router = useRouter()

  function getProjectKey(sprint?: SprintVO): string | undefined {
    const currentProject = projects.value.find(p => p.id === selectedProject.value)
    return currentProject?.key || sprint?.projectKey
  }

  function viewSprintIssues(sprint: SprintVO) {
    const projectKey = getProjectKey(sprint)
    const query: Record<string, string> = { sprint: sprint.id, label: sprint.name }
    if (projectKey) query.project = projectKey
    router.push({ path: '/issues', query })
  }

  function viewSprintOnBoard(sprint: SprintVO) {
    const projectKey = getProjectKey(sprint)
    const query: Record<string, string> = { sprint: sprint.id }
    if (projectKey) query.project = projectKey
    router.push({ path: '/boards', query })
  }

  function viewIssuesByCategory(sprint: SprintVO, category: string) {
    const projectKey = getProjectKey(sprint)
    const categoryLabels: Record<string, string> = {
      done: '已完成',
      in_progress: '进行中',
      open: '待办'
    }
    const query: Record<string, string> = {
      sprint: sprint.id,
      statusCategory: category,
      label: `${sprint.name} - ${categoryLabels[category]}工单`
    }
    if (projectKey) query.project = projectKey
    router.push({ path: '/issues', query })
  }

  function viewOverdueIssues(sprint: SprintVO) {
    const projectKey = getProjectKey(sprint)
    const query: Record<string, string> = {
      sprint: sprint.id,
      overdue: 'true',
      label: `${sprint.name} - 逾期工单`
    }
    if (projectKey) query.project = projectKey
    router.push({ path: '/issues', query })
  }

  return {
    viewSprintIssues,
    viewSprintOnBoard,
    viewIssuesByCategory,
    viewOverdueIssues
  }
}

/**
 * Sprint 日期/状态工具函数
 */
// 统一从 utils/date 导出，保持此模块的 API 兼容
export { formatDate } from '@/utils/date'

/**
 * 判断 Sprint 是否不可启动。
 *
 * 规则：
 * - 结束日期已过 → 不可启动（无论是否有活跃 Sprint）
 * - 开始日期未到 + 已有活跃 Sprint → 不可启动（需等当前迭代完成）
 * - 开始日期未到 + 无活跃 Sprint → 可启动（允许提前启动下一个）
 *
 * @param sprint 待判断的 Sprint
 * @param hasActiveSprint 当前项目是否存在活跃的 Sprint
 */
export function isSprintNotStartable(sprint: SprintVO, hasActiveSprint = true): boolean {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  // 结束日期已过 → 绝对不可启动
  if (sprint.endDate) {
    const end = new Date(sprint.endDate)
    end.setHours(0, 0, 0, 0)
    if (end.getTime() < today.getTime()) return true
  }
  // 开始日期未到：仅在已有活跃 Sprint 时阻止（有活跃的情况下没必要提前启动）
  if (sprint.startDate) {
    const start = new Date(sprint.startDate)
    start.setHours(0, 0, 0, 0)
    if (start.getTime() > today.getTime()) {
      return hasActiveSprint
    }
  }
  return false
}
