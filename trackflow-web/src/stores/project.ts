import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { useAuthStore } from './auth'

/**
 * 生成用户特定的 localStorage 键名
 * 格式：tf_selected_project_{userId}
 * 若无用户 ID（如登录前），返回临时键名（不实际使用）
 */
function getStorageKey(userId?: string): string {
  if (!userId) return 'tf_selected_project_anonymous'
  return `tf_selected_project_${userId}`
}

/**
 * 项目选择状态管理
 * 在看板、迭代等需要选择项目的页面之间共享选中状态，
 * 并持久化到 localStorage 以支持刷新页面后恢复。
 * 
 * 重要：偏好按用户 ID 隔离存储，避免多用户共享同一台设备时互相干扰。
 */
export const useProjectStore = defineStore('project', () => {
  const authStore = useAuthStore()

  /**
   * 当前用户的存储键名
   * 优先使用数据库用户 ID（确保持久化一致性），降级使用 Keycloak sub
   */
  const currentStorageKey = computed(() => {
    const userId = authStore.user?.userId || authStore.user?.id
    return getStorageKey(userId)
  })

  /**
   * 初始化时从 localStorage 恢复状态
   * 若用户已登录，使用用户特定键；否则不恢复（避免读取其他用户的数据）
   */
  function loadFromStorage(): string | undefined {
    const userId = authStore.user?.userId || authStore.user?.id
    if (!userId) return undefined
    const stored = localStorage.getItem(getStorageKey(userId))
    return stored || undefined
  }

  const selectedProjectId = ref<string | undefined>(loadFromStorage())

  /**
   * 监听存储键变化（用户登录/登出时），重新加载偏好
   */
  watch(currentStorageKey, (newKey, oldKey) => {
    if (newKey === oldKey) return
    // 用户变化时，从新用户的 storage 恢复或清空
    selectedProjectId.value = loadFromStorage()
  })

  /**
   * 监听 selectedProjectId 变化，持久化到 localStorage（仅当用户已登录时）
   */
  watch(selectedProjectId, (val) => {
    const userId = authStore.user?.userId || authStore.user?.id
    if (!userId) return // 未登录不写入

    const key = getStorageKey(userId)
    if (val) {
      localStorage.setItem(key, val)
    } else {
      localStorage.removeItem(key)
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
    // 这涵盖了"存储的项目 ID 对应用户无权限访问的项目"的情况
    if (selectedProjectId.value && !projectIds.includes(selectedProjectId.value)) {
      selectedProjectId.value = undefined
    }

    return false
  }

  /**
   * 清除当前用户的项目偏好（用于登出时调用）
   */
  function clearPreference() {
    const userId = authStore.user?.userId || authStore.user?.id
    if (userId) {
      localStorage.removeItem(getStorageKey(userId))
    }
    selectedProjectId.value = undefined
  }

  return {
    selectedProjectId,
    selectProject,
    autoSelectIfNeeded,
    clearPreference
  }
})
