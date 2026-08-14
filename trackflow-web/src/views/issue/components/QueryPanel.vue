<template>
  <!-- 左侧查询面板（YouTrack 风格）
       包含：面板顶部标题栏、搜索过滤框、草稿区、项目收藏区、标签收藏区、已保存搜索区
       宽度由 useQueryPanel 管理，支持拖拽调整和双击折叠 -->
  <aside class="query-panel" :style="{ width: panelWidth + 'px' }">

    <!-- ===== 搜索框：过滤已保存的查询列表 ===== -->
    <div class="panel-search">
      <a-input v-model="panelSearch" placeholder="过滤已保存的查询..." size="small" allow-clear>
        <template #prefix><icon-search /></template>
      </a-input>
    </div>

    <!-- ===== 草稿区：仅对有创建权限的用户显示
         用户取消创建工单时，已填写内容会自动保存为草稿
         点击草稿项可恢复编辑，右键可删除单条或全部草稿 ===== -->
    <div v-if="canCreateIssue && (hasDrafts || true)" class="query-group drafts-group">
      <div class="group-header" @click="toggleGroup('drafts')">
        <span class="group-arrow">{{ expandedGroups.has('drafts') ? '▾' : '▸' }}</span>
        <span class="group-title">草稿</span>
        <span v-if="draftCount > 0" class="draft-count-badge">{{ draftCount }}</span>
        <a-button
          v-if="canCreateIssue"
          type="text" size="mini" class="group-action-btn"
          title="新建工单"
          @click.stop="emit('open-draft-create')"
        >
          <template #icon><icon-plus :size="12" /></template>
        </a-button>
      </div>
      <div v-if="expandedGroups.has('drafts')" class="group-items">
        <EmptyState
          v-if="draftList.length === 0"
          icon="exclamation-circle"
          title="暂无草稿"
          description="取消创建工单时，已填写的内容会自动保存为草稿"
          :compact="true"
        />
        <a-dropdown
          v-for="d in draftList"
          :key="d.id"
          trigger="contextMenu"
          position="br"
        >
          <div
            class="query-item draft-item"
            :class="{ 'draft-recovered': recoveredDraftId === d.id }"
            @click="emit('open-draft', d)"
          >
            <icon-file class="query-icon" />
            <span class="query-name draft-name">{{ d.title || '无标题草稿' }}</span>
            <span class="draft-time">{{ formatDraftTime(d.updatedAt) }}</span>
            <span v-if="recoveredDraftId === d.id" class="draft-recovered-badge">刚恢复</span>
          </div>
          <template #content>
            <a-doption @click="emit('open-draft', d)">
              <template #icon><icon-edit /></template>
              继续编辑
            </a-doption>
            <a-doption class="query-ctx-delete" @click="handleDeleteDraft(d.id)">
              <template #icon><icon-delete /></template>
              删除草稿
            </a-doption>
          </template>
        </a-dropdown>
        <div v-if="draftList.length > 0" class="drafts-actions">
          <a-link type="text" class="delete-all-link" @click="handleDeleteAllDrafts">删除所有草稿</a-link>
        </div>
      </div>
    </div>

    <!-- ===== 项目收藏区：显示用户收藏的项目列表
         "所有项目"始终在顶部，点击项目名称可筛选该项目下的工单
         点击齿轮图标打开"管理收藏项目"弹窗 ===== -->
    <div class="query-group">
      <div class="group-header" @click="toggleGroup('projects')">
        <span class="group-arrow">{{ expandedGroups.has('projects') ? '▾' : '▸' }}</span>
        <span class="group-title">项目</span>
        <a-button
          type="text" size="mini" class="group-action-btn"
          title="管理收藏项目"
          @click.stop="openManageProjectsModal"
        >
          <template #icon><icon-settings :size="12" /></template>
        </a-button>
      </div>
      <div v-if="expandedGroups.has('projects')" class="group-items">
        <div
          class="query-item"
          :class="{ active: activeProjectId === null }"
          @click="emit('select-all-projects')"
        >
          <span class="query-name">所有项目</span>
        </div>
        <template v-if="favoriteProjects.length > 0">
          <div
            v-for="p in favoriteProjects"
            :key="p.id"
            class="query-item"
            :class="{ active: activeProjectId === p.id }"
            @click="emit('select-project', p)"
          >
            <span class="query-name">{{ p.name }}</span>
          </div>
        </template>
        <div v-else class="empty-queries" style="display: flex; flex-direction: column; align-items: center; gap: 6px;">
          <span>暂无收藏项目</span>
          <a-link style="font-size: 12px;" @click.stop="openManageProjectsModal">添加收藏</a-link>
        </div>
      </div>
    </div>

    <!-- ===== 管理收藏项目弹窗：从所有项目中点击星标切换收藏状态 ===== -->
    <a-modal
      v-model:visible="showManageProjectsModal"
      title="管理收藏项目"
      :width="480"
      :footer="false"
      @cancel="showManageProjectsModal = false"
    >
      <div class="manage-projects-content">
        <p class="manage-projects-hint">点击星标将项目添加到侧边栏快速访问列表。</p>
        <div class="manage-projects-search">
          <a-input v-model="manageProjectSearch" placeholder="搜索项目..." size="small" allow-clear>
            <template #prefix><icon-search /></template>
          </a-input>
        </div>
        <div v-if="manageProjectsLoading" class="manage-projects-loading">
          <a-spin :size="24" />
        </div>
        <div v-else class="manage-projects-list">
          <div
            v-for="p in filteredManageProjects"
            :key="p.id"
            class="manage-project-item"
            @click="toggleProjectFavorite(p)"
          >
            <span class="manage-project-star" :class="{ favorited: p.favorited }">
              {{ p.favorited ? '★' : '☆' }}
            </span>
            <div class="manage-project-info">
              <span class="manage-project-name">{{ p.name }}</span>
              <span class="manage-project-key">{{ p.key }}</span>
            </div>
            <span class="manage-project-action">
              {{ p.favorited ? '移除收藏' : '添加收藏' }}
            </span>
          </div>
          <div v-if="filteredManageProjects.length === 0" class="manage-projects-empty">
            没有找到匹配的项目
          </div>
        </div>
      </div>
    </a-modal>

    <!-- ===== 标签收藏区：显示用户收藏的标签列表
         点击标签名称可按该标签筛选工单，右侧显示该标签下的工单数
         点击齿轮图标打开"管理标签收藏"弹窗 ===== -->
    <div class="query-group">
      <div class="group-header" @click="toggleGroup('tags')">
        <span class="group-arrow">{{ expandedGroups.has('tags') ? '▾' : '▸' }}</span>
        <span class="group-title">标签</span>
        <a-button
          type="text" size="mini" class="group-action-btn"
          title="管理标签收藏"
          @click.stop="openManageTagsModal"
        >
          <template #icon><icon-settings :size="12" /></template>
        </a-button>
      </div>
      <div v-if="expandedGroups.has('tags')" class="group-items">
        <div
          v-for="tag in favoriteTags"
          :key="tag.id"
          class="query-item"
          :class="{ active: activeTagId === tag.id }"
          @click="emit('select-tag', tag)"
        >
          <span class="tag-color-dot" :style="{ backgroundColor: tag.color }"></span>
          <span class="query-name">{{ tag.name }}</span>
          <span class="query-count">{{ formatCount(tag.count) }}</span>
        </div>
        <EmptyState v-if="favoriteTags.length === 0" title="暂无收藏标签" :compact="true" />
      </div>
    </div>

    <!-- ===== 管理标签收藏弹窗：从所有可用标签中点击切换收藏状态 ===== -->
    <a-modal
      v-model:visible="showManageTagsModal"
      title="管理标签收藏"
      :width="440"
      :footer="false"
    >
      <div class="manage-tags-content">
        <div v-if="availableTags.length === 0" class="empty-queries" style="padding: 16px; text-align: center;">
          暂无可用标签
        </div>
        <div v-else class="manage-tags-list">
          <div
            v-for="tag in availableTags"
            :key="tag.id"
            class="manage-tag-item"
            @click="toggleTagFavorite(tag)"
          >
            <span class="tag-color-dot" :style="{ backgroundColor: tag.color }"></span>
            <span class="manage-tag-name">{{ tag.name }}</span>
            <span class="manage-tag-action">
              {{ tag.favorited ? '移除收藏' : '添加收藏' }}
            </span>
          </div>
        </div>
      </div>
    </a-modal>

    <!-- ===== 已保存的搜索区：显示用户保存的查询列表（YouTrack 核心功能）
         点击 + 号可将当前筛选条件保存为新查询
         点击齿轮图标可管理收藏的共享查询
         右键或点击 ⋯ 可编辑/重命名/删除/共享/置顶查询 ===== -->
    <div class="query-group">
      <div class="group-header" @click="toggleGroup('saved')">
        <span class="group-arrow">{{ expandedGroups.has('saved') ? '▾' : '▸' }}</span>
        <span class="group-title">已保存的搜索</span>
        <div class="group-actions">
          <a-button
            type="text" size="mini" class="group-action-btn"
            title="保存当前筛选为查询"
            @click.stop="openCreateQueryModal"
          >
            <template #icon><icon-plus :size="12" /></template>
          </a-button>
          <a-button
            type="text" size="mini" class="group-action-btn"
            title="管理查询收藏"
            @click.stop="openManageQueriesModal"
          >
            <template #icon><icon-settings :size="12" /></template>
          </a-button>
        </div>
      </div>
      <div v-if="expandedGroups.has('saved')" class="group-items">
        <a-dropdown
          v-for="q in filteredQueries"
          :key="q.id"
          trigger="contextMenu"
          position="br"
          :popup-max-height="false"
        >
          <div
            class="query-item"
            :class="{ active: activeQueryId === q.id }"
            @click="emit('select-query', q)"
          >
            <span v-if="q.icon" class="query-icon">{{ q.icon }}</span>
            <span class="query-name">{{ q.name }}</span>
            <span class="query-count">{{ formatCount(q.count) }}</span>
            <span
              class="query-action-btn"
              title="更多操作"
              @click.stop
              @contextmenu.prevent.stop
              @mousedown.stop="triggerContextMenu($event, q)"
            >⋯</span>
          </div>
          <template #content>
            <template v-if="isOwnQuery(q)">
              <a-doption @click="openEditQueryModal(q)">
                <template #icon><icon-edit /></template>
                编辑查询
              </a-doption>
              <a-doption @click="openRenameQueryModal(q)">
                <template #icon><icon-pen-fill /></template>
                重命名
              </a-doption>
              <a-doption @click="toggleQueryShared(q)">
                <template #icon><icon-share-external /></template>
                {{ q.shared ? '设为私有' : '设为共享' }}
              </a-doption>
              <a-doption @click="toggleQueryPinned(q)">
                <template #icon><icon-pushpin /></template>
                {{ q.pinned ? '取消置顶' : '置顶' }}
              </a-doption>
              <a-doption class="query-ctx-delete" @click="confirmDeleteQuery(q)">
                <template #icon><icon-delete /></template>
                删除
              </a-doption>
            </template>
            <template v-else>
              <a-doption @click="handleRemoveFavorite(q)">
                <template #icon><icon-minus-circle /></template>
                从面板移除
              </a-doption>
            </template>
          </template>
        </a-dropdown>
        <div v-if="panelLoadFailed && filteredQueries.length === 0" class="empty-queries panel-error">
          <icon-exclamation-circle-fill style="color: var(--color-warning-6); margin-right: 4px;" />
          加载失败
          <a-link :hoverable="false" style="margin-left: 8px; font-size: 12px;" @click="loadPanel()">重试</a-link>
        </div>
        <EmptyState v-else-if="filteredQueries.length === 0" title="暂无保存的搜索" :compact="true" />
      </div>
    </div>

    <!-- ===== 保存查询弹窗：将当前筛选条件保存为命名查询 ===== -->
    <a-modal
      v-model:visible="showCreateQueryModal"
      title="保存查询"
      :width="440"
      :ok-loading="createQueryLoading"
      ok-text="保存查询"
      cancel-text="取消"
      @ok="handleCreateQuery"
      @cancel="showCreateQueryModal = false"
    >
      <a-form :model="createQueryForm" layout="vertical">
        <a-form-item label="查询名称" required>
          <a-input v-model="createQueryForm.name" placeholder="输入查询名称，如：我的工单" :max-length="50" />
        </a-form-item>
        <a-form-item label="图标">
          <div class="icon-picker">
            <span
              v-for="emoji in queryIconOptions"
              :key="emoji"
              class="icon-option"
              :class="{ selected: createQueryForm.icon === emoji }"
              @click="createQueryForm.icon = createQueryForm.icon === emoji ? '' : emoji"
            >{{ emoji }}</span>
          </div>
          <div v-if="createQueryForm.icon" class="icon-preview">
            已选：{{ createQueryForm.icon }}
            <a-link @click="createQueryForm.icon = ''" style="margin-left: 8px; font-size: 12px;">清除</a-link>
          </div>
        </a-form-item>
        <a-form-item label="查询条件">
          <QueryInput
            v-model="createQueryForm.queryText"
            placeholder="输入查询条件... (如 状态: 未关闭  负责人: 我)"
            :status-list="statusCache"
            :project-list="(projectList as any)"
            :project-id="activeProjectId"
          />
        </a-form-item>
        <a-form-item label="固定到面板顶部">
          <a-switch v-model="createQueryForm.pinned" />
        </a-form-item>
        <a-form-item label="共享">
          <a-switch v-model="createQueryForm.shared" />
          <span class="form-help-text">共享后其他项目成员也能看到此查询</span>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- ===== 编辑查询弹窗：修改已保存查询的名称、条件、图标等 ===== -->
    <a-modal
      v-model:visible="showEditQueryModal"
      title="编辑查询"
      :width="440"
      :ok-loading="editQueryLoading"
      ok-text="保存修改"
      cancel-text="取消"
      @ok="handleEditQuery"
      @cancel="showEditQueryModal = false"
    >
      <a-form :model="editQueryForm" layout="vertical">
        <a-form-item label="查询名称" required>
          <a-input v-model="editQueryForm.name" placeholder="输入查询名称" :max-length="50" />
        </a-form-item>
        <a-form-item label="图标">
          <div class="icon-picker">
            <span
              v-for="emoji in queryIconOptions"
              :key="emoji"
              class="icon-option"
              :class="{ selected: editQueryForm.icon === emoji }"
              @click="editQueryForm.icon = editQueryForm.icon === emoji ? '' : emoji"
            >{{ emoji }}</span>
          </div>
          <div v-if="editQueryForm.icon" class="icon-preview">
            已选：{{ editQueryForm.icon }}
            <a-link @click="editQueryForm.icon = ''" style="margin-left: 8px; font-size: 12px;">清除</a-link>
          </div>
        </a-form-item>
        <a-form-item label="查询">
          <QueryInput
            v-model="editQueryForm.queryText"
            placeholder="输入查询条件... (如 状态: 未关闭  负责人: 我)"
            :status-list="statusCache"
            :project-list="(projectList as any)"
            :project-id="activeProjectId"
          />
        </a-form-item>
        <a-form-item label="固定到面板顶部">
          <a-switch v-model="editQueryForm.pinned" />
        </a-form-item>
        <a-form-item label="共享">
          <a-switch v-model="editQueryForm.shared" />
          <span class="form-help-text">共享后其他项目成员也能看到此查询</span>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- ===== 重命名查询弹窗：仅修改查询名称的轻量弹窗 ===== -->
    <a-modal
      v-model:visible="showRenameQueryModal"
      title="重命名查询"
      :width="360"
      :ok-loading="renameQueryLoading"
      ok-text="确认"
      cancel-text="取消"
      @ok="handleRenameQuery"
      @cancel="showRenameQueryModal = false"
    >
      <a-form :model="renameQueryForm" layout="vertical">
        <a-form-item label="新名称" field="name" :rules="[{ required: true, message: '请输入名称' }]">
          <a-input v-model="renameQueryForm.name" placeholder="输入新名称" :max-length="50" @keyup.enter="handleRenameQuery" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- ===== 管理查询收藏弹窗：从所有共享查询中选择要在面板中显示的查询 ===== -->
    <a-modal
      v-model:visible="showManageQueriesModal"
      title="管理查询收藏"
      :width="520"
      :footer="false"
      @cancel="showManageQueriesModal = false"
    >
      <div class="manage-queries-content">
        <p class="manage-queries-hint">选择要在面板中显示的共享查询。点击星标切换收藏状态。</p>
        <div class="manage-queries-search">
          <a-input v-model="manageQuerySearch" placeholder="过滤已保存的查询..." size="small" allow-clear>
            <template #prefix><icon-search /></template>
          </a-input>
        </div>
        <div class="manage-queries-list">
          <div
            v-for="q in filteredManageQueries"
            :key="q.id"
            class="manage-query-item"
            @click="toggleFavorite(q)"
          >
            <span class="manage-query-star" :class="{ favorited: q.favorited }">
              {{ q.favorited ? '★' : '☆' }}
            </span>
            <span v-if="q.icon" class="manage-query-icon">{{ q.icon }}</span>
            <span class="manage-query-name">{{ q.name }}</span>
            <span v-if="q.userId && !isOwnQueryById(q.userId)" class="manage-query-owner">共享</span>
          </div>
          <div v-if="filteredManageQueries.length === 0" class="manage-queries-empty">
            没有找到匹配的查询
          </div>
        </div>
      </div>
    </a-modal>
  </aside>
</template>


<script setup lang="ts">
/**
 * QueryPanel — 左侧查询面板组件
 *
 * 职责：
 * - 展示草稿列表（取消创建时自动保存的临时工单）
 * - 展示收藏项目列表（快速切换项目过滤）
 * - 展示收藏标签列表（快速按标签过滤）
 * - 展示已保存的搜索查询列表（YouTrack 核心 Saved Query 功能）
 * - 管理面板宽度（拖拽调整 / 双击折叠）
 * - 内置 6 个 Modal（管理收藏项目、管理标签、保存查询、编辑查询、重命名查询、管理查询收藏）
 *
 * 对外接口：
 * - Props：父组件传入共享状态（activeProjectId/activeQueryId 等），面板自身不持有这些状态
 * - Emits：用户操作后通知父组件（选中查询/项目/标签、打开草稿等）
 * - defineExpose：暴露 loadPanel/loadProjects/loadTags/savedQueries 供父组件在 onMounted 和路由变化时调用
 */
import { computed } from 'vue'
import {
  IconPlus, IconSearch, IconEdit, IconPenFill, IconShareExternal,
  IconPushpin, IconDelete, IconSettings, IconMinusCircle, IconExclamationCircleFill,
  IconFile
} from '@arco-design/web-vue/es/icon'
import { Message } from '@arco-design/web-vue'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { useDrafts } from '@/composables/useDrafts'
import type { IssueStatusVO } from '@/api/types'
import { useQueryPanel } from '../composables/useQueryPanel'
import { useProjectTagPanel } from '../composables/useProjectTagPanel'
import QueryInput from './QueryInput.vue'
import type { IssueDraft } from '@/composables/useDrafts'
import { EmptyState } from '@/components/base'

// ===== Props =====
// 父组件传入的共享状态，面板只读取、不持有这些数据
const props = defineProps<{
  /** 当前总工单数（显示在面板顶部） */
  totalIssues: number
  /** 当前用户是否有创建工单权限 */
  canCreateIssue: boolean
  /** 当前激活的项目 ID（null 表示所有项目） */
  activeProjectId: string | null
  /** 当前激活的查询 ID */
  activeQueryId: string | null
  /** 当前激活的标签 ID */
  activeTagId: string | null
  /** 状态缓存（传给 QueryInput） */
  statusCache: IssueStatusVO[]
  /** issue 类型选项（传给 useQueryPanel） */
  issueTypeOptions: { value: string; label: string; color: string | null; description?: string | null; isDefault?: boolean }[]
  /** 优先级选项（传给 useQueryPanel） */
  priorityOptions: { value: string; label: string; color: string | null; description?: string | null; isDefault?: boolean }[]
  /** 是否隐藏已解决工单 */
  hideResolved: boolean
  /** 刚恢复的草稿 ID（高亮显示） */
  recoveredDraftId: string | null
}>()

// ===== Emits =====
// 用户在面板内发生操作时，通过事件通知父组件更新过滤状态和 UI
const emit = defineEmits<{
  /** 选中一个 Saved Query */
  'select-query': [q: any]
  /** 选中一个项目 */
  'select-project': [p: any]
  /** 点击"所有项目" */
  'select-all-projects': []
  /** 选中一个标签 */
  'select-tag': [tag: any]
  /** 打开草稿编辑 */
  'open-draft': [draft: IssueDraft]
  /** 新建工单（草稿区 + 按钮） */
  'open-draft-create': []
  /** 面板加载完成，刷新列表 */
  'refresh-list': []
}>()

// ===== 草稿管理 =====
// useDrafts 是全局单例，数据存储在 localStorage，跨组件共享同一份草稿列表
// 删除操作直接在面板内处理，不需要通知父组件
const { draftList, draftCount, hasDrafts, deleteDraft, deleteAllDrafts } = useDrafts()

function formatDraftTime(timestamp: number): string {
  const now = Date.now()
  const diff = now - timestamp
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`
  return new Date(timestamp).toLocaleDateString()
}

function handleDeleteDraft(draftId: string) {
  deleteDraft(draftId)
  Message.success('草稿已删除')
}

function handleDeleteAllDrafts() {
  const { confirmDangerDelete } = useConfirmDelete()
  confirmDangerDelete({
    itemName: `全部 ${draftCount.value} 个草稿`,
    impactDescription: '删除后无法恢复',
    confirmText: '全部删除',
    onConfirm: () => {
      deleteAllDrafts()
      Message.success('所有草稿已删除')
    }
  })
}

// ===== 项目与标签面板 =====
// useProjectTagPanel 管理：项目列表加载、收藏项目、标签列表、收藏标签
// activeProjectId 以 computed 包装传入，使 composable 内部能响应 Props 变化
const activeProjectIdRef = computed(() => props.activeProjectId)

const {
  projectList, favoriteProjects,
  showManageProjectsModal, manageProjectSearch, manageProjectsLoading, filteredManageProjects,
  loadProjects, openManageProjectsModal, toggleProjectFavorite,
  favoriteTags, showManageTagsModal, availableTags,
  loadTags, openManageTagsModal, toggleTagFavorite,
} = useProjectTagPanel({ activeProjectId: activeProjectIdRef })

// ===== 查询面板核心逻辑 =====
// useQueryPanel 管理：已保存查询的加载/创建/编辑/删除/收藏、面板宽度调整、搜索过滤
// statusCache/issueTypeOptions/priorityOptions 以 computed 包装传入，确保响应式
const statusCacheRef = computed(() => props.statusCache)
const issueTypeOptionsRef = computed(() => props.issueTypeOptions)
const priorityOptionsRef = computed(() => props.priorityOptions)
const hideResolvedRef = computed(() => props.hideResolved)

function refreshList() {
  emit('refresh-list')
}

function getIssueTypeLabelForRecord(issueType: string): string {
  const opt = props.issueTypeOptions.find(o => o.value === issueType || o.value.toLowerCase() === issueType.toLowerCase())
  return opt?.label || issueType
}

const {
  expandedGroups, panelSearch, panelLoadFailed, panelWidth,
  filteredQueries, savedQueries,
  showCreateQueryModal, createQueryLoading, queryIconOptions, createQueryForm,
  showEditQueryModal, editQueryLoading, editQueryForm,
  showRenameQueryModal, renameQueryLoading, renameQueryForm,
  showManageQueriesModal, manageQuerySearch, filteredManageQueries,
  loadPanel, isOwnQuery, isOwnQueryById,
  openCreateQueryModal, handleCreateQuery, confirmDeleteQuery,
  openEditQueryModal, handleEditQuery, openRenameQueryModal, handleRenameQuery,
  toggleQueryShared, toggleQueryPinned,
  openManageQueriesModal, toggleFavorite, handleRemoveFavorite,
  triggerContextMenu, startPanelResize, togglePanelCollapse,
} = useQueryPanel({
  statusCache: statusCacheRef,
  projectList,
  issueTypeOptions: issueTypeOptionsRef,
  priorityOptions: priorityOptionsRef,
  activeProjectId: activeProjectIdRef,
  hideResolved: hideResolvedRef,
  refreshList,
  getIssueTypeLabelForRecord,
})

// ===== 工具函数 =====
function formatCount(count: number) {
  if (count >= 10000) return Math.floor(count / 1000) + 'k+'
  if (count >= 1000) return (count / 1000).toFixed(1) + 'k'
  return String(count)
}

function toggleGroup(group: string) {
  if (expandedGroups.has(group)) expandedGroups.delete(group)
  else expandedGroups.add(group)
}

// ===== 暴露给父组件的方法和数据 =====
// 父组件（IssueListViewImpl）通过 queryPanelRef 调用这些方法，
// 例如：onMounted 时初始化数据、切换项目时刷新面板
defineExpose({
  loadPanel,
  loadProjects,
  loadTags,
  savedQueries,
  projectList,
  startPanelResize,
  togglePanelCollapse,
  panelWidth,
})
</script>


<style scoped>
.query-panel {
  background: var(--tf-bg-surface);
  overflow-y: auto;
  overflow-x: hidden;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease;
}
.panel-top { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px 8px; }
.panel-top-title { display: flex; align-items: center; gap: 8px; }
.panel-label { font-size: 14px; font-weight: 500; color: var(--tf-text-primary); }
.panel-total { font-size: 11px; color: var(--tf-text-tertiary); background: var(--tf-bg-elevated); padding: 2px 6px; border-radius: 8px; }
.panel-search { padding: 4px 10px 8px; }
.query-group { padding: 0 6px; margin-bottom: 2px; }
.group-header { display: flex; align-items: center; gap: 4px; height: 32px; padding: 0 8px; cursor: pointer; border-radius: 4px; transition: background 0.15s; }
.group-header:hover { background: var(--tf-bg-hover); }
.group-arrow { font-size: 10px; width: 14px; color: var(--tf-text-tertiary); }
.group-title { font-size: 11px; color: var(--tf-text-tertiary); font-weight: 500; text-transform: uppercase; letter-spacing: 0.6px; }
.group-items { padding-left: 8px; }
.query-item { display: flex; align-items: center; justify-content: space-between; height: 32px; padding: 0 12px; cursor: pointer; border-radius: 4px; margin: 1px 0; transition: background 0.15s; }
.query-item:hover { background: var(--tf-bg-hover); }
.query-item.active { background: var(--tf-accent-bg); color: var(--tf-accent); }
.query-name { font-size: 13px; color: var(--tf-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.query-item.active .query-name { color: var(--tf-accent); }
.query-count { font-size: 11px; color: var(--tf-text-tertiary); flex-shrink: 0; margin-left: 8px; }
.query-icon { font-size: 12px; flex-shrink: 0; margin-right: 4px; }
.empty-queries { padding: 12px; font-size: 12px; color: var(--tf-text-tertiary); text-align: center; }

/* Tags */
.tag-color-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; margin-right: 6px; }
.manage-tags-content { max-height: 360px; overflow-y: auto; }
.manage-tags-list { display: flex; flex-direction: column; gap: 2px; }
.manage-tag-item { display: flex; align-items: center; padding: 8px 12px; border-radius: 4px; cursor: pointer; transition: background 0.15s; }
.manage-tag-item:hover { background: var(--tf-bg-hover); }
.manage-tag-name { flex: 1; font-size: 13px; color: var(--tf-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.manage-tag-action { font-size: 12px; color: var(--tf-accent); flex-shrink: 0; margin-left: 8px; }

/* Drafts */
.drafts-group { border-bottom: 1px solid var(--tf-border); padding-bottom: 4px; margin-bottom: 4px; }
.draft-count-badge { font-size: 10px; color: var(--tf-text-tertiary); background: var(--tf-bg-elevated); padding: 1px 5px; border-radius: 8px; margin-left: 4px; }
.draft-item { position: relative; }
.draft-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.draft-time { font-size: 10px; color: var(--tf-text-quaternary); flex-shrink: 0; margin-left: 4px; }
.draft-recovered { background: var(--tf-accent-bg) !important; animation: draft-pulse 1.5s ease-in-out infinite; }
@keyframes draft-pulse { 0%, 100% { background: var(--tf-accent-bg); } 50% { background: var(--tf-bg-hover); } }
.draft-recovered-badge { font-size: 9px; color: var(--tf-accent); background: rgba(var(--accent-rgb, 88,166,255), 0.15); padding: 1px 4px; border-radius: 3px; margin-left: 4px; flex-shrink: 0; }
.drafts-actions { padding: 4px 8px; text-align: center; }
.delete-all-link { font-size: 11px; color: var(--tf-text-tertiary); }
.delete-all-link:hover { color: var(--color-danger-light-4); }

/* Group action button */
.group-action-btn { margin-left: auto; opacity: 0; transition: opacity 0.15s; }
.group-actions { margin-left: auto; display: flex; gap: 2px; opacity: 0; transition: opacity 0.15s; }
.group-actions .group-action-btn { margin-left: 0; opacity: 1; }
.group-header:hover .group-action-btn { opacity: 1; }
.group-header:hover .group-actions { opacity: 1; }

/* Query action button (⋯) */
.query-action-btn { font-size: 14px; color: var(--tf-text-quaternary); cursor: pointer; padding: 2px 4px; border-radius: 3px; opacity: 0; transition: opacity 0.15s, color 0.15s, background 0.15s; flex-shrink: 0; line-height: 1; }
.query-item:hover .query-action-btn { opacity: 1; }
.query-action-btn:hover { color: var(--tf-text-primary); background: var(--tf-bg-hover); }
.query-ctx-delete { color: var(--tf-danger) !important; }
.query-ctx-delete:hover { background: var(--tf-danger-bg) !important; }

/* Icon picker */
.icon-picker { display: flex; flex-wrap: wrap; gap: 6px; }
.icon-option { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; font-size: 16px; border-radius: 6px; cursor: pointer; border: 1px solid var(--tf-border); transition: all 0.15s; }
.icon-option:hover { background: var(--tf-bg-hover); transform: scale(1.1); }
.icon-option.selected { background: var(--tf-accent-bg); border-color: var(--tf-accent); }
.icon-preview { margin-top: 8px; font-size: 12px; color: var(--tf-text-secondary); }
.form-help-text { font-size: 12px; color: var(--tf-text-tertiary); margin-left: 8px; }

/* Manage queries modal */
.manage-queries-content { display: flex; flex-direction: column; gap: 12px; }
.manage-queries-hint { font-size: 12px; color: var(--tf-text-tertiary); margin: 0; }
.manage-queries-search { margin-bottom: 4px; }
.manage-queries-list { max-height: 400px; overflow-y: auto; display: flex; flex-direction: column; gap: 2px; }
.manage-query-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; border-radius: 6px; cursor: pointer; transition: background 100ms; }
.manage-query-item:hover { background: var(--tf-bg-hover); }
.manage-query-star { font-size: 16px; color: var(--tf-text-tertiary); transition: color 100ms; flex-shrink: 0; }
.manage-query-star.favorited { color: var(--tf-accent); }
.manage-query-icon { font-size: 14px; flex-shrink: 0; }
.manage-query-name { font-size: 13px; color: var(--tf-text-primary); flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.manage-query-owner { font-size: 11px; color: var(--tf-text-tertiary); flex-shrink: 0; padding: 1px 6px; background: var(--tf-bg-surface); border-radius: 3px; }
.manage-queries-empty { text-align: center; padding: 24px; font-size: 13px; color: var(--tf-text-tertiary); }

/* Manage projects modal */
.manage-projects-content { display: flex; flex-direction: column; gap: 12px; }
.manage-projects-hint { font-size: 12px; color: var(--tf-text-tertiary); margin: 0; }
.manage-projects-search { margin-bottom: 4px; }
.manage-projects-loading { display: flex; justify-content: center; padding: 32px; }
.manage-projects-list { max-height: 400px; overflow-y: auto; display: flex; flex-direction: column; gap: 2px; }
.manage-project-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; border-radius: 6px; cursor: pointer; transition: background 100ms; }
.manage-project-item:hover { background: var(--tf-bg-hover); }
.manage-project-star { font-size: 16px; color: var(--tf-text-tertiary); transition: color 100ms; flex-shrink: 0; }
.manage-project-star.favorited { color: var(--tf-accent); }
.manage-project-info { flex: 1; display: flex; flex-direction: column; gap: 1px; overflow: hidden; }
.manage-project-name { font-size: 13px; color: var(--tf-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.manage-project-key { font-size: 11px; color: var(--tf-text-tertiary); font-family: monospace; }
.manage-project-action { font-size: 12px; color: var(--tf-accent); flex-shrink: 0; white-space: nowrap; }
.manage-projects-empty { text-align: center; padding: 24px; font-size: 13px; color: var(--tf-text-tertiary); }
</style>
