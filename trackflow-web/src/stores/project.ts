import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

const STORAGE_KEY = 'tf_selected_project'

/**
 * 项目选择状态管理
 * 在看板、迭代等需要选择项目的页面之间共享选中状态，
 * 并持久化到 localStorage 以支持刷新页面后恢复。
 */
export const useProjectStore = defineStore('project', () => {
  // 从 localStorage 恢复上次选中的项目
  const stored = localStorage.getItem(STORAGE_KEY)
  const selectedProjectId = ref<string | undefined>(stored || undefined)

  // 监听变化，持久化到 localStorage
  watch(selectedProjectId, (val) => {
    if (val) {
      localStorage.setItem(STORAGE_KEY, val)
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  })

  /**
   * 设置选中的项目
   */
  function selectProject(projectId: string | undefined) {
    selectedProjectId.value = projectId
  }

  /**
   * 自动选择项目：
   * 1. 如果当前已有选中且在项目列表中，保持不变
   * 2. 如果用户只有一个项目，自动选中
   * 3. 否则不做选择
   * 返回是否发生了自动选择
   */
  function autoSelectIfNeeded(projectIds: string[]): boolean {
    // 当前选中的项目仍在列表中，保持
    if (selectedProjectId.value && projectIds.includes(selectedProjectId.value)) {
      return false
    }

    // 用户只参与一个项目，自动选中
    if (projectIds.length === 1) {
      selectedProjectId.value = projectIds[0]
      return true
    }

    // 多个项目但上次选的不在列表里了，清除
    if (selectedProjectId.value && !projectIds.includes(selectedProjectId.value)) {
      selectedProjectId.value = undefined
    }

    return false
  }

  return {
    selectedProjectId,
    selectProject,
    autoSelectIfNeeded
  }
})
