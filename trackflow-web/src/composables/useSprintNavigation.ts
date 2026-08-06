import { computed, type Ref } from 'vue'
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

  function viewIssuesByCategory(sprint: SprintVO, category: 'done' | 'in_progress' | 'open') {
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
export function formatDate(dateStr?: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function isSprintNotStartable(sprint: SprintVO): boolean {
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
