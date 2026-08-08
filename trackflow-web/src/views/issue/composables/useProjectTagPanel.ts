import { ref, computed, type Ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { projectApi, tagApi } from '@/api'
import type { TagPanelItemVO, AvailableTagVO } from '@/api/tag'
import axios from 'axios'

export interface ProjectTagPanelOptions {
  activeProjectId: Ref<string | null>
}

export function useProjectTagPanel(options: ProjectTagPanelOptions) {
  const { activeProjectId } = options

  // Projects state
  const projectList = ref<Array<{ id: string; name: string; key: string; favorited?: boolean }>>([])
  const favoriteProjects = computed(() => projectList.value.filter(p => p.favorited))
  const showManageProjectsModal = ref(false)
  const manageProjectSearch = ref('')
  const manageProjectsLoading = ref(false)
  const allProjectsForManage = ref<any[]>([])

  const filteredManageProjects = computed(() => {
    const list = allProjectsForManage.value
    if (!manageProjectSearch.value) return list
    const kw = manageProjectSearch.value.toLowerCase()
    return list.filter((p: any) => p.name.toLowerCase().includes(kw) || p.key.toLowerCase().includes(kw))
  })

  async function loadProjects() {
    try {
      const res = await projectApi.list({ pageSize: 50 })
      projectList.value = res.data?.list || []
    } catch (e) {
      if (axios.isCancel(e)) return
      projectList.value = []
    }
  }

  async function openManageProjectsModal() {
    showManageProjectsModal.value = true
    manageProjectSearch.value = ''
    manageProjectsLoading.value = true
    try {
      const res = await projectApi.list({ pageSize: 100 })
      const projects = res.data?.list || []
      const favoriteIds = new Set(projectList.value.filter(p => p.favorited).map(p => p.id))
      allProjectsForManage.value = projects.map((p: any) => ({
        ...p,
        favorited: favoriteIds.has(p.id)
      }))
    } catch {
      allProjectsForManage.value = []
    } finally {
      manageProjectsLoading.value = false
    }
  }

  async function toggleProjectFavorite(p: any) {
    try {
      const res = await projectApi.toggleFavorite(p.id)
      const newFavorited = res.data?.favorited ?? !p.favorited
      p.favorited = newFavorited
      const inList = projectList.value.find(pr => pr.id === p.id)
      if (inList) {
        inList.favorited = newFavorited
      }
    } catch (e: any) {
      Message.error(e.response?.data?.message || '操作失败')
    }
  }

  // Tags state
  const favoriteTags = ref<TagPanelItemVO[]>([])
  const activeTagId = ref<string | null>(null)
  const showManageTagsModal = ref(false)
  const availableTags = ref<AvailableTagVO[]>([])

  async function loadTags() {
    try {
      const res = await tagApi.getFavoritePanel(activeProjectId.value || undefined)
      favoriteTags.value = res.data || []
    } catch (e) {
      if (axios.isCancel(e)) return
      favoriteTags.value = []
    }
  }

  async function openManageTagsModal() {
    showManageTagsModal.value = true
    try {
      const res = await tagApi.listAvailableTags(activeProjectId.value || undefined)
      availableTags.value = res.data || []
    } catch {
      availableTags.value = []
    }
  }

  async function toggleTagFavorite(tag: AvailableTagVO) {
    try {
      if (tag.favorited) {
        await tagApi.removeFavorite(tag.id)
        tag.favorited = false
      } else {
        await tagApi.addFavorite(tag.id)
        tag.favorited = true
      }
      await loadTags()
    } catch (e: any) {
      Message.error(e.response?.data?.message || '操作失败')
    }
  }

  return {
    // Projects
    projectList,
    favoriteProjects,
    showManageProjectsModal,
    manageProjectSearch,
    manageProjectsLoading,
    filteredManageProjects,
    loadProjects,
    openManageProjectsModal,
    toggleProjectFavorite,
    // Tags
    favoriteTags,
    activeTagId,
    showManageTagsModal,
    availableTags,
    loadTags,
    openManageTagsModal,
    toggleTagFavorite
  }
}
