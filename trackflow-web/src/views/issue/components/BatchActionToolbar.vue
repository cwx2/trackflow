<template>
  <div class="batch-toolbar">
    <div class="batch-info">
      <span class="batch-count">已选 {{ selectedCount }} 个工单</span>
    </div>
    <div class="batch-actions">
      <!-- 变更状态 -->
      <a-trigger
        v-model:popup-visible="showStatusDropdown"
        trigger="click"
        position="bl"
        :popup-offset="4"
      >
        <a-button size="small" type="outline">
          <template #icon><icon-swap /></template>
          变更状态
        </a-button>
        <template #content>
          <div class="batch-dropdown status-dropdown">
            <div v-if="statusLoading" class="dropdown-loading">
              <a-spin :size="16" />
            </div>
            <template v-else>
              <div
                v-for="status in allStatuses"
                :key="status.id"
                class="dropdown-item"
                :class="{ 'partially-reachable': status.reachableCount < status.totalCount }"
                @click="handleBatchState(status)"
              >
                <span class="status-dot" :style="{ background: status.color }"></span>
                <span class="status-name">{{ localizeStatusName(status.name) }}</span>
                <span
                  v-if="status.totalCount > 1 && status.reachableCount < status.totalCount"
                  class="reachable-hint"
                >{{ status.reachableCount }}/{{ status.totalCount }}</span>
              </div>
              <div v-if="allStatuses.length === 0" class="dropdown-empty">
                <span>当前工单无可用状态转换</span>
              </div>
            </template>
          </div>
        </template>
      </a-trigger>

      <!-- 分配 -->
      <a-trigger
        v-model:popup-visible="showAssignDropdown"
        trigger="click"
        position="bl"
        :popup-offset="4"
      >
        <a-button size="small" type="outline">
          <template #icon><icon-user /></template>
          分配
        </a-button>
        <template #content>
          <div class="batch-dropdown member-dropdown">
            <div class="dropdown-search">
              <a-input
                v-model="memberSearch"
                placeholder="搜索成员..."
                size="small"
                allow-clear
              >
                <template #prefix><icon-search /></template>
              </a-input>
            </div>
            <div v-if="memberLoading" class="dropdown-loading">
              <a-spin :size="16" />
            </div>
            <template v-else>
              <div
                class="dropdown-item"
                @click="handleBatchAssign(null)"
              >
                <span class="unassigned-icon">—</span>
                <span>未分配</span>
              </div>
              <div
                v-for="member in filteredMembers"
                :key="member.userId"
                class="dropdown-item"
                @click="handleBatchAssign(member)"
              >
                <span class="member-avatar">{{ member.displayName?.charAt(0) }}</span>
                <span>{{ member.displayName }}</span>
              </div>
              <div v-if="filteredMembers.length === 0 && !memberLoading" class="dropdown-empty">
                无匹配成员
              </div>
            </template>
          </div>
        </template>
      </a-trigger>

      <!-- 移至 Sprint -->
      <a-trigger
        v-model:popup-visible="showSprintDropdown"
        trigger="click"
        position="bl"
        :popup-offset="4"
      >
        <a-button size="small" type="outline">
          <template #icon><icon-calendar /></template>
          移至 Sprint
        </a-button>
        <template #content>
          <div class="batch-dropdown">
            <div v-if="sprintWarning" class="dropdown-warning">
              {{ sprintWarning }}
            </div>
            <div v-else-if="sprintError" class="dropdown-error">
              {{ sprintError }}
            </div>
            <div v-else-if="sprintLoading" class="dropdown-loading">
              <a-spin :size="16" />
            </div>
            <template v-else>
              <div
                class="dropdown-item"
                @click="handleBatchSprint(null)"
              >
                <span>无 Sprint</span>
              </div>
              <template v-for="group in sprintGroups" :key="group.label">
                <div class="dropdown-group-label">{{ group.label }}</div>
                <div
                  v-for="sprint in group.items"
                  :key="sprint.id"
                  class="dropdown-item"
                  @click="handleBatchSprint(sprint)"
                >
                  <span>{{ sprint.name }}</span>
                </div>
              </template>
              <div v-if="sprintGroups.length === 0" class="dropdown-empty">
                该项目暂无可用 Sprint
              </div>
            </template>
          </div>
        </template>
      </a-trigger>

      <!-- 移到下一 Sprint（YouTrack 对标功能） -->
      <a-tooltip
        :content="nextSprintTooltip"
        position="top"
        mini
      >
        <a-button
          size="small"
          type="outline"
          :loading="nextSprintLoading"
          :disabled="nextSprintDisabled"
          @click="handleMoveToNextSprint"
        >
          <template #icon><icon-right-circle /></template>
          下一 Sprint
        </a-button>
      </a-tooltip>

      <!-- 变更优先级 -->
      <a-trigger
        v-model:popup-visible="showPriorityDropdown"
        trigger="click"
        position="bl"
        :popup-offset="4"
      >
        <a-button size="small" type="outline">
          <template #icon><icon-fire /></template>
          变更优先级
        </a-button>
        <template #content>
          <div class="batch-dropdown">
            <div
              v-for="p in priorityOptions"
              :key="p.value"
              class="dropdown-item"
              @click="handleBatchPriority(p.value)"
            >
              <span class="priority-dot" :class="'priority-' + p.value.toLowerCase()"></span>
              <span>{{ p.label }}</span>
            </div>
          </div>
        </template>
      </a-trigger>

      <!-- 添加标签 -->
      <a-trigger
        v-model:popup-visible="showTagDropdown"
        trigger="click"
        position="bl"
        :popup-offset="4"
      >
        <a-button size="small" type="outline">
          <template #icon><icon-tag /></template>
          添加标签
        </a-button>
        <template #content>
          <div class="batch-dropdown tag-dropdown">
            <div class="dropdown-search">
              <a-input
                v-model="tagSearch"
                placeholder="搜索标签..."
                size="small"
                allow-clear
              >
                <template #prefix><icon-search /></template>
              </a-input>
            </div>
            <div v-if="tagLoading" class="dropdown-loading">
              <a-spin :size="16" />
            </div>
            <template v-else>
              <!-- 移除标签选项 -->
              <div v-if="showRemoveSection" class="dropdown-group-label">移除标签</div>
              <div
                v-for="tag in removableTags"
                :key="'remove-' + tag.id"
                class="dropdown-item tag-remove-item"
                @click="handleBatchTagRemove(tag)"
              >
                <span class="tag-dot" :style="{ background: tag.color }"></span>
                <span class="tag-name">{{ tag.name }}</span>
                <icon-close class="tag-remove-icon" />
              </div>
              <div v-if="showRemoveSection" class="dropdown-divider"></div>

              <!-- 添加标签选项 -->
              <div v-if="showRemoveSection" class="dropdown-group-label">添加标签</div>
              <div
                v-for="tag in filteredTags"
                :key="tag.id"
                class="dropdown-item"
                @click="handleBatchTagAdd(tag)"
              >
                <span class="tag-dot" :style="{ background: tag.color }"></span>
                <span class="tag-name">{{ tag.name }}</span>
              </div>
              <div v-if="filteredTags.length === 0 && removableTags.length === 0" class="dropdown-empty">
                无可用标签
              </div>
            </template>
          </div>
        </template>
      </a-trigger>

      <!-- 添加关联 -->
      <a-trigger
        v-model:popup-visible="showLinkDropdown"
        trigger="click"
        position="bl"
        :popup-offset="4"
      >
        <a-button size="small" type="outline">
          <template #icon><icon-link /></template>
          添加关联
        </a-button>
        <template #content>
          <div class="batch-dropdown link-dropdown">
            <div class="dropdown-group-label">选择关联类型</div>
            <div
              v-for="lt in linkTypeOptions"
              :key="lt.value"
              class="dropdown-item"
              @click="handleSelectLinkType(lt.value)"
            >
              <span>{{ lt.label }}</span>
            </div>
          </div>
        </template>
      </a-trigger>

      <!-- 关联目标工单选择弹窗 -->
      <a-modal
        v-model:visible="showLinkTargetModal"
        title="选择目标工单"
        :width="480"
        :footer="false"
        unmount-on-close
      >
        <div class="link-target-search">
          <a-input
            v-model="linkTargetSearch"
            placeholder="输入工单编号或标题搜索..."
            size="small"
            allow-clear
            @input="handleLinkTargetSearchDebounced"
          >
            <template #prefix><icon-search /></template>
          </a-input>
        </div>
        <div v-if="linkTargetLoading" class="link-target-loading">
          <a-spin :size="20" />
        </div>
        <div v-else class="link-target-list">
          <div
            v-for="issue in linkTargetResults"
            :key="issue.id"
            class="link-target-item"
            @click="handleBatchLink(issue)"
          >
            <span class="link-target-key">{{ issue.issueKey }}</span>
            <span class="link-target-title">{{ issue.title }}</span>
          </div>
          <div v-if="linkTargetResults.length === 0 && linkTargetSearch" class="dropdown-empty">
            无匹配工单
          </div>
          <div v-if="!linkTargetSearch" class="dropdown-empty">
            请输入工单编号或标题搜索
          </div>
        </div>
      </a-modal>

      <!-- 批量导出 -->
      <a-dropdown trigger="click" position="bl" @select="handleExport">
        <a-button size="small" type="outline">
          <template #icon><icon-download /></template>
          导出
        </a-button>
        <template #content>
          <a-doption value="xlsx">导出 XLSX</a-doption>
          <a-doption value="csv">导出 CSV</a-doption>
        </template>
      </a-dropdown>

      <!-- 批量删除（仅有 issue:delete 权限时显示） -->
      <a-button v-if="canDelete" size="small" type="outline" status="danger" @click="confirmBatchDelete">
        <template #icon><icon-delete /></template>
        删除
      </a-button>

      <!-- 命令对话框入口 -->
      <a-button size="small" type="outline" @click="$emit('open-command')">
        <template #icon><icon-code /></template>
        命令
      </a-button>
    </div>

    <a-button size="small" type="text" @click="$emit('deselect-all')">
      取消全选
    </a-button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { IconSwap, IconUser, IconSearch, IconCalendar, IconFire, IconDelete, IconDownload, IconCode, IconTag, IconLink, IconClose, IconRightCircle } from '@arco-design/web-vue/es/icon'
import { Modal, Message } from '@arco-design/web-vue'
import { issueApi, projectApi, sprintApi, tagApi } from '@/api'
import type { IssueVO, IssueTagVO, ProjectMemberVO, SprintVO, BatchAvailableStatusVO } from '@/api/types'
import { localizeStatusName, linkTypeLabelMap } from '@/utils/fieldLabels'

const props = defineProps<{
  selectedCount: number
  selectedIssues: IssueVO[]
  canDelete?: boolean
  /** 当前视图选中的项目 ID（用作 Sprint 加载的 fallback 上下文） */
  activeProjectId?: string | null
}>()

const emit = defineEmits<{
  'deselect-all': []
  'batch-state': [statusId: string]
  'batch-assign': [assigneeId: string | null]
  'batch-sprint': [sprintId: string | null]
  'batch-priority': [priority: string]
  'batch-tag-add': [tagId: string]
  'batch-tag-remove': [tagId: string]
  'batch-link': [linkType: string, targetIssueId: string]
  'batch-delete': []
  'batch-export': [format: string]
  'open-command': []
}>()

// ========== 状态下拉 ==========
const showStatusDropdown = ref(false)
const statusLoading = ref(false)
const allStatuses = ref<BatchAvailableStatusVO[]>([])

watch(showStatusDropdown, async (visible) => {
  if (visible) {
    statusLoading.value = true
    try {
      const issueIds = props.selectedIssues.map(i => i.id)
      if (issueIds.length === 1) {
        // 单选：使用单工单可用转换 API
        const res = await issueApi.getAvailableTransitions(issueIds[0])
        allStatuses.value = (res.data || []).map(s => ({
          id: s.id,
          name: s.name,
          color: s.color,
          category: s.category,
          isClosed: s.isClosed,
          sortOrder: s.sortOrder,
          reachableCount: 1,
          totalCount: 1
        }))
      } else {
        // 多选：使用批量可用转换 API
        const res = await issueApi.getBatchAvailableTransitions(issueIds)
        allStatuses.value = res.data || []
      }
    } catch {
      allStatuses.value = []
    } finally {
      statusLoading.value = false
    }
  }
})

function handleBatchState(status: BatchAvailableStatusVO) {
  showStatusDropdown.value = false
  emit('batch-state', status.id)
}

// ========== 分配下拉 ==========
const showAssignDropdown = ref(false)
const memberLoading = ref(false)
const memberSearch = ref('')
const members = ref<ProjectMemberVO[]>([])

const filteredMembers = computed(() => {
  if (!memberSearch.value) return members.value
  const kw = memberSearch.value.toLowerCase()
  return members.value.filter(m => m.displayName?.toLowerCase().includes(kw))
})

watch(showAssignDropdown, async (visible) => {
  if (visible) {
    memberSearch.value = ''
    memberLoading.value = true
    try {
      // 获取选中 Issue 所属项目的可分配成员（排除观察者等角色）
      const projectIds = [...new Set(props.selectedIssues.map(i => i.projectId))]
      const allMembers: ProjectMemberVO[] = []
      for (const pid of projectIds) {
        const res = await projectApi.listAssignableMembers(pid)
        allMembers.push(...(res.data || []))
      }
      // 去重 by userId
      const seen = new Set<string>()
      members.value = allMembers.filter(m => {
        if (seen.has(m.userId)) return false
        seen.add(m.userId)
        return true
      })
    } catch {
      members.value = []
    } finally {
      memberLoading.value = false
    }
  }
})

function handleBatchAssign(member: ProjectMemberVO | null) {
  showAssignDropdown.value = false
  emit('batch-assign', member?.userId || null)
}

// ========== Sprint 下拉 ==========
const showSprintDropdown = ref(false)
const sprintLoading = ref(false)
const sprintWarning = ref('')
const sprintError = ref('')
const sprints = ref<SprintVO[]>([])

interface SprintGroup {
  label: string
  items: SprintVO[]
}

const sprintGroups = computed<SprintGroup[]>(() => {
  const active = sprints.value.filter(s => s.status?.toLowerCase() === 'active')
  const planned = sprints.value.filter(s => s.status?.toLowerCase() === 'planned')
  const groups: SprintGroup[] = []
  if (active.length) groups.push({ label: '进行中', items: active })
  if (planned.length) groups.push({ label: '计划中', items: planned })
  return groups
})

/** 用于确定 Sprint 应加载哪个项目的 ID */
function resolveSprintProjectId(): string | undefined {
  // 优先从选中工单推断项目
  const projectIds = [...new Set(props.selectedIssues.map(i => i.projectId).filter(Boolean))]
  if (projectIds.length === 1) return projectIds[0]
  if (projectIds.length > 1) return undefined // 多项目场景，外层已处理
  // fallback: 使用当前视图的项目筛选
  if (props.activeProjectId) return props.activeProjectId
  return undefined
}

watch(showSprintDropdown, async (visible) => {
  if (visible) {
    sprintWarning.value = ''
    sprintError.value = ''
    sprints.value = []

    const projectIds = [...new Set(props.selectedIssues.map(i => i.projectId).filter(Boolean))]

    if (projectIds.length > 1) {
      sprintWarning.value = '批量移动 Sprint 仅支持同一项目的工单，请选择同一项目的工单'
      return
    }

    const targetProjectId = resolveSprintProjectId()
    if (!targetProjectId) {
      sprintWarning.value = '无法确定目标项目，请选择具体项目的工单或在顶部筛选项目'
      return
    }

    sprintLoading.value = true
    try {
      const res = await sprintApi.listByProject(targetProjectId)
      sprints.value = res.data?.list || []
      // 如果 API 成功但没有 active/planned Sprint，给出明确提示
      if (sprints.value.length > 0 && sprintGroups.value.length === 0) {
        // 有 Sprint 但都已完成
        sprintWarning.value = '该项目所有 Sprint 均已完成，暂无进行中或计划中的 Sprint'
      }
    } catch (e: any) {
      sprints.value = []
      const status = e.response?.status
      if (status === 403) {
        sprintError.value = '权限不足，无法查看该项目的 Sprint'
      } else if (status === 404) {
        sprintError.value = '项目不存在或已被删除'
      } else {
        sprintError.value = '加载 Sprint 列表失败，请稍后重试'
      }
    } finally {
      sprintLoading.value = false
    }
  }
})

function handleBatchSprint(sprint: SprintVO | null) {
  showSprintDropdown.value = false
  emit('batch-sprint', sprint?.id || null)
}

// ========== 移到下一 Sprint ==========
const nextSprintLoading = ref(false)
const nextSprint = ref<SprintVO | null>(null)
const nextSprintResolved = ref(false)

/** 确定"下一个 Sprint"：活跃 Sprint 之后最近的 planned Sprint
 *  若无活跃 Sprint，则是最近的 planned Sprint */
async function resolveNextSprint(): Promise<SprintVO | null> {
  const targetProjectId = resolveSprintProjectId()
  if (!targetProjectId) return null

  nextSprintLoading.value = true
  try {
    const res = await sprintApi.listByProject(targetProjectId)
    const allSprints: SprintVO[] = res.data?.list || []

    // 找活跃 Sprint
    const active = allSprints.find(s => s.status === 'active')
    // 找所有 planned Sprint，按 startDate 排序（无日期的放后面）
    const plannedSprints = allSprints
      .filter(s => s.status === 'planned')
      .sort((a, b) => {
        if (a.startDate && b.startDate) return a.startDate.localeCompare(b.startDate)
        if (a.startDate) return -1
        if (b.startDate) return 1
        return a.name.localeCompare(b.name)
      })

    if (active) {
      // 有活跃 Sprint：取 planned 中第一个（按排序即为"下一个"）
      return plannedSprints.length > 0 ? plannedSprints[0] : null
    } else {
      // 无活跃 Sprint：取最近的 planned Sprint
      return plannedSprints.length > 0 ? plannedSprints[0] : null
    }
  } catch {
    return null
  } finally {
    nextSprintLoading.value = false
  }
}

/** 计算 tooltip 文字 */
const nextSprintTooltip = computed(() => {
  if (nextSprintLoading.value) return '正在确定下一 Sprint...'
  if (!nextSprintResolved.value) return '移到下一个排期的 Sprint'
  if (nextSprint.value) return `移到 ${nextSprint.value.name}`
  return '暂无排期中的 Sprint，点击后可新建'
})

/** 是否禁用按钮 */
const nextSprintDisabled = computed(() => nextSprintLoading.value)

/** 处理"移到下一 Sprint"点击 */
async function handleMoveToNextSprint() {
  // 检查是否跨项目
  const projectIds = [...new Set(props.selectedIssues.map(i => i.projectId).filter(Boolean))]
  if (projectIds.length > 1) {
    Message.warning('批量移动 Sprint 仅支持同一项目的工单')
    return
  }

  if (!nextSprintResolved.value || nextSprintLoading.value) {
    // 还未加载，先加载
    const resolved = await resolveNextSprint()
    nextSprint.value = resolved
    nextSprintResolved.value = true
  }

  if (nextSprint.value) {
    // 有下一 Sprint，直接移动
    emit('batch-sprint', nextSprint.value.id)
  } else {
    // 无下一 Sprint，提示用户
    Message.warning('当前项目暂无排期中的 Sprint，请先在迭代管理页面创建下一个 Sprint')
  }
}

// 当 activeProjectId 或选中工单改变时，重置已解析状态（下次点击时重新加载）
watch(
  [() => props.activeProjectId, () => props.selectedIssues.map(i => i.projectId).join(',')],
  () => {
    nextSprint.value = null
    nextSprintResolved.value = false
  }
)

// 组件挂载时预加载下一 Sprint（优化用户体验，减少点击时的等待）
onMounted(async () => {
  const projectIds = [...new Set(props.selectedIssues.map(i => i.projectId).filter(Boolean))]
  // 仅单项目时预加载
  if (projectIds.length === 1 || props.activeProjectId) {
    const resolved = await resolveNextSprint()
    nextSprint.value = resolved
    nextSprintResolved.value = true
  }
})

// ========== 优先级下拉 ==========
const showPriorityDropdown = ref(false)
const priorityOptions = [
  { value: 'Critical', label: '紧急' },
  { value: 'High', label: '高' },
  { value: 'Normal', label: '普通' },
  { value: 'Low', label: '低' }
]

function handleBatchPriority(priority: string) {
  showPriorityDropdown.value = false
  emit('batch-priority', priority)
}

// ========== 批量导出 ==========
function handleExport(format: string | number | Record<string, any> | undefined) {
  emit('batch-export', String(format))
}

// ========== 标签下拉 ==========
const showTagDropdown = ref(false)
const tagLoading = ref(false)
const tagSearch = ref('')
const projectTags = ref<IssueTagVO[]>([])

/** 当前选中工单已有的标签（取交集/并集用于展示可移除的） */
const existingTagIds = computed(() => {
  const tagIds = new Set<string>()
  props.selectedIssues.forEach(issue => {
    issue.tags?.forEach(t => tagIds.add(t.id))
  })
  return tagIds
})

/** 可移除的标签：选中工单中至少有一个有该标签 */
const removableTags = computed(() => {
  return projectTags.value.filter(t => existingTagIds.value.has(t.id))
})

/** 可添加的标签：过滤掉已在所有选中工单上的标签 */
const filteredTags = computed(() => {
  const kw = tagSearch.value.toLowerCase()
  return projectTags.value
    .filter(t => !existingTagIds.value.has(t.id))
    .filter(t => !kw || t.name.toLowerCase().includes(kw))
})

const showRemoveSection = computed(() => removableTags.value.length > 0)

watch(showTagDropdown, async (visible) => {
  if (visible) {
    tagSearch.value = ''
    tagLoading.value = true
    try {
      const projectIds = [...new Set(props.selectedIssues.map(i => i.projectId))]
      if (projectIds.length === 1) {
        const res = await tagApi.listProjectTags(projectIds[0])
        projectTags.value = res.data || []
      } else {
        // 多项目场景：合并所有项目标签并去重
        const allTags: IssueTagVO[] = []
        for (const pid of projectIds) {
          const res = await tagApi.listProjectTags(pid)
          allTags.push(...(res.data || []))
        }
        const seen = new Set<string>()
        projectTags.value = allTags.filter(t => {
          if (seen.has(t.id)) return false
          seen.add(t.id)
          return true
        })
      }
    } catch {
      projectTags.value = []
    } finally {
      tagLoading.value = false
    }
  }
})

function handleBatchTagAdd(tag: IssueTagVO) {
  showTagDropdown.value = false
  emit('batch-tag-add', tag.id)
}

function handleBatchTagRemove(tag: IssueTagVO) {
  showTagDropdown.value = false
  emit('batch-tag-remove', tag.id)
}

// ========== 关联下拉 ==========
const showLinkDropdown = ref(false)
const showLinkTargetModal = ref(false)
const linkTargetSearch = ref('')
const linkTargetLoading = ref(false)
const linkTargetResults = ref<IssueVO[]>([])
const selectedLinkType = ref('')

// 使用统一的映射表生成下拉选项，保持双语格式便于用户理解
const linkTypeOptions = [
  { value: 'relates_to', label: `${linkTypeLabelMap.relates_to}（relates to）` },
  { value: 'blocks', label: `${linkTypeLabelMap.blocks}（blocks）` },
  { value: 'blocked_by', label: `${linkTypeLabelMap.blocked_by}（blocked by）` },
  { value: 'duplicates', label: `${linkTypeLabelMap.duplicates}（duplicates）` },
  { value: 'parent_of', label: `${linkTypeLabelMap.parent_of}（parent of）` },
  { value: 'child_of', label: `${linkTypeLabelMap.child_of}（child of）` }
]

function handleSelectLinkType(linkType: string) {
  showLinkDropdown.value = false
  selectedLinkType.value = linkType
  linkTargetSearch.value = ''
  linkTargetResults.value = []
  showLinkTargetModal.value = true
}

let linkSearchTimer: ReturnType<typeof setTimeout> | null = null
function handleLinkTargetSearchDebounced() {
  if (linkSearchTimer) clearTimeout(linkSearchTimer)
  linkSearchTimer = setTimeout(async () => {
    if (!linkTargetSearch.value.trim()) {
      linkTargetResults.value = []
      return
    }
    linkTargetLoading.value = true
    try {
      const res = await issueApi.list({
        keyword: linkTargetSearch.value.trim(),
        page: 1,
        pageSize: 20
      })
      // 排除已选中的工单
      const selectedIds = new Set(props.selectedIssues.map(i => i.id))
      linkTargetResults.value = (res.data?.list || []).filter((i: IssueVO) => !selectedIds.has(i.id))
    } catch {
      linkTargetResults.value = []
    } finally {
      linkTargetLoading.value = false
    }
  }, 300)
}

function handleBatchLink(targetIssue: IssueVO) {
  showLinkTargetModal.value = false
  emit('batch-link', selectedLinkType.value, targetIssue.id)
}

// ========== 批量删除 ==========
function confirmBatchDelete() {
  Modal.confirm({
    title: '确认批量删除',
    content: `确定要删除选中的 ${props.selectedCount} 个工单吗？工单将移入回收站，可随时恢复。`,
    okText: '删除',
    cancelText: '取消',
    okButtonProps: { status: 'danger' },
    onOk() {
      emit('batch-delete')
    }
  })
}
</script>

<style scoped>
.batch-toolbar {
  display: flex;
  align-items: center;
  padding: 8px 16px;
  background: var(--tf-bg-elevated);
  border-bottom: 1px solid var(--tf-border);
  gap: 12px;
  animation: slideDown 200ms ease-out;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.batch-info {
  display: flex;
  align-items: center;
}

.batch-count {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.batch-actions {
  display: flex;
  gap: 8px;
  flex: 1;
}

/* 下拉菜单 */
.batch-dropdown {
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  padding: 4px;
  min-width: 160px;
  max-height: 280px;
  overflow-y: auto;
}

.member-dropdown {
  min-width: 200px;
}

.dropdown-search {
  padding: 4px;
  margin-bottom: 4px;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: var(--tf-text-primary);
  transition: background 0.15s;
}
.dropdown-item:hover {
  background: var(--tf-bg-hover);
}

.dropdown-group-label {
  padding: 6px 8px 2px;
  font-size: 11px;
  color: var(--tf-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-weight: 500;
}

.dropdown-loading {
  display: flex;
  justify-content: center;
  padding: 12px;
}

.dropdown-empty {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.dropdown-warning {
  padding: 8px 12px;
  font-size: 12px;
  color: var(--tf-warning);
  background: rgba(210, 153, 34, 0.1);
  border-radius: 4px;
  margin: 4px;
}

.dropdown-error {
  padding: 8px 12px;
  font-size: 12px;
  color: var(--tf-danger);
  background: rgba(248, 81, 73, 0.1);
  border-radius: 4px;
  margin: 4px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.member-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--tf-accent-bg);
  color: var(--tf-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 500;
  flex-shrink: 0;
}

.unassigned-icon {
  width: 24px;
  text-align: center;
  color: var(--tf-text-tertiary);
}

.priority-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.priority-critical { background: var(--tf-danger); }
.priority-high { background: var(--tf-warning); }
.priority-normal { background: var(--tf-accent); }
.priority-low { background: var(--tf-text-tertiary); }

/* 状态下拉 - 可达性提示 */
.status-dropdown .dropdown-item {
  justify-content: flex-start;
}
.status-dropdown .status-name {
  flex: 1;
}
.status-dropdown .reachable-hint {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-hover);
  padding: 1px 6px;
  border-radius: 3px;
  flex-shrink: 0;
}
.status-dropdown .dropdown-item.partially-reachable {
  opacity: 0.75;
}
.status-dropdown .dropdown-item.partially-reachable:hover {
  opacity: 1;
}

/* 标签下拉 */
.tag-dropdown {
  min-width: 200px;
}

.tag-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.tag-name {
  flex: 1;
}

.tag-remove-item {
  color: var(--tf-text-secondary);
}
.tag-remove-item:hover {
  color: var(--tf-danger);
}

.tag-remove-icon {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  flex-shrink: 0;
}
.tag-remove-item:hover .tag-remove-icon {
  color: var(--tf-danger);
}

.dropdown-divider {
  height: 1px;
  background: var(--tf-border);
  margin: 4px 8px;
}

/* 关联下拉 */
.link-dropdown {
  min-width: 200px;
}

/* 关联目标工单选择弹窗 */
.link-target-search {
  margin-bottom: 12px;
}

.link-target-loading {
  display: flex;
  justify-content: center;
  padding: 24px;
}

.link-target-list {
  max-height: 300px;
  overflow-y: auto;
}

.link-target-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}
.link-target-item:hover {
  background: var(--tf-bg-hover);
}

.link-target-key {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-secondary);
  flex-shrink: 0;
}

.link-target-title {
  font-size: 13px;
  color: var(--tf-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
