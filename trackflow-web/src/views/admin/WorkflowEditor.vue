<template>
  <AdminPageLayout title="工作流编辑器">
    <template #breadcrumb>
      <!-- 面包屑导航（从项目设置页跳转过来时显示） -->
      <template v-if="sourceProjectId">
        <a-breadcrumb>
          <a-breadcrumb-item>
            <router-link :to="`/projects/${sourceProjectKey}/settings?tab=workflow`">
              <icon-left class="breadcrumb-back-icon" />
              {{ sourceProjectName }} 的工作流设置
            </router-link>
          </a-breadcrumb-item>
          <a-breadcrumb-item>转换矩阵编辑</a-breadcrumb-item>
        </a-breadcrumb>
        <div class="breadcrumb-hint">
          <icon-info-circle />
          正在编辑 <strong>{{ sourceProjectName }}</strong> 的工作流规则
        </div>
      </template>
      <!-- 面包屑导航（从工作流列表页跳转过来时显示） -->
      <template v-else>
        <a-breadcrumb>
          <a-breadcrumb-item>
            <router-link to="/workflow">
              <icon-left class="breadcrumb-back-icon" />
              工作流列表
            </router-link>
          </a-breadcrumb-item>
          <a-breadcrumb-item>编辑器</a-breadcrumb-item>
        </a-breadcrumb>
      </template>
    </template>

    <template #actions>
      <a-tabs v-model:active-key="activeMainTab" class="workflow-main-tabs" type="rounded">
        <a-tab-pane key="matrix" title="状态转换矩阵" />
        <a-tab-pane key="canvas" title="状态机画布" />
        <a-tab-pane key="rules" title="自动化规则" />
      </a-tabs>
      <a-button
        :type="isDirty ? 'primary' : 'secondary'"
        :loading="saving"
        :disabled="!isDirty"
        @click="saveMatrix"
      >
        <template v-if="isDirty">
          保存工作流 ({{ pendingChangesCount }})
        </template>
        <template v-else>保存工作流</template>
      </a-button>
      <a-button @click="showHistory = true">
        <template #icon><icon-history /></template>
        变更历史
      </a-button>
    </template>

    <!-- 状态转换矩阵的筛选器（下沉到内容区顶部） -->
    <div class="content-filters" v-show="activeMainTab === 'matrix'">
      <a-select
        v-model="selectedProject"
        placeholder="选择项目"
        style="width: 180px"
        @change="onFilterChange"
      >
        <a-option value="0">全局（默认）</a-option>
        <a-option
          v-for="p in projects"
          :key="p.id"
          :value="p.id"
        >{{ p.name }}</a-option>
      </a-select>

      <a-select
        v-model="selectedType"
        placeholder="工单类型"
        style="width: 140px"
        @change="onFilterChange"
      >
        <a-option value="*">所有类型</a-option>
        <a-option
          v-for="t in issueTypes"
          :key="t"
          :value="t"
        >{{ t }}</a-option>
      </a-select>

      <a-select
        v-model="selectedRole"
        placeholder="请选择角色（必填）"
        style="width: 160px"
        :status="roleLoadingError ? 'error' : undefined"
        @change="onFilterChange"
      >
        <a-option
          v-for="role in roles"
          :key="role.id"
          :value="role.id"
        >{{ role.name }}</a-option>
        <template v-if="roles.length === 0 && !roleLoadingError" #empty>
          <div style="padding: 8px 12px; text-align: center; color: var(--color-text-3);">
            暂无角色，请先配置项目角色
          </div>
        </template>
      </a-select>
      <a-tooltip v-if="roleLoadingError" content="角色加载失败，点击重试">
        <a-button type="text" size="small" @click="retryLoadRoles">
          <template #icon><icon-refresh /></template>
        </a-button>
      </a-tooltip>
    </div>

    <!-- Author/Assignee 模式 Tab -->
    <div v-show="activeMainTab === 'matrix'" class="mode-tabs" v-if="statuses.length > 0">
      <a-radio-group v-model="selectedMode" type="button" @change="onFilterChange">
        <a-radio value="normal">
          <template #default>
            基础规则
            <a-tooltip content="对拥有该角色的所有用户生效的通用转换规则。">
              <icon-info-circle class="mode-info-icon" />
            </a-tooltip>
          </template>
        </a-radio>
        <a-radio value="author">
          <template #default>
            创建者额外规则
            <a-tooltip content="此处配置的规则仅对工单创建者额外生效。例如：允许创建者取消自己提交的工单。">
              <icon-info-circle class="mode-info-icon" />
            </a-tooltip>
          </template>
        </a-radio>
        <a-radio value="assignee">
          <template #default>
            负责人额外规则
            <a-tooltip content="此处配置的规则仅对工单负责人额外生效。例如：允许负责人直接关闭自己负责的工单。">
              <icon-info-circle class="mode-info-icon" />
            </a-tooltip>
          </template>
        </a-radio>
      </a-radio-group>
    </div>

    <!-- 矩阵工具栏：搜索 + 筛选 -->
    <div v-show="activeMainTab === 'matrix'" class="matrix-toolbar" v-if="statuses.length > 0">
      <a-input
        v-model="searchKeyword"
        placeholder="搜索状态名..."
        style="width: 200px"
        allow-clear
        @clear="searchKeyword = ''"
      >
        <template #prefix><icon-search /></template>
      </a-input>

      <a-switch
        v-model="onlyConfigured"
        checked-text="只显示已配置"
        unchecked-text="显示全部"
      />

      <span class="toolbar-stats">
        {{ filteredStatuses.length }} / {{ statuses.length }} 个状态
        <span class="toolbar-divider">|</span>
        {{ allowedTransitions.size }} 条转换规则已启用
      </span>
    </div>

    <!-- 未保存变更提示条 -->
    <div v-show="activeMainTab === 'matrix' && isDirty" class="dirty-banner">
      <icon-info-circle />
      <span>已修改 {{ pendingChangesCount }} 条转换规则，尚未保存</span>
    </div>

    <div v-show="activeMainTab === 'matrix'" class="matrix-grow-area">
    <a-spin :loading="loading" tip="加载中...">
      <!-- 转换矩阵 -->
      <div class="matrix-container" v-if="filteredStatuses.length > 0">
        <table class="matrix-table">
          <thead>
            <tr>
              <th class="corner-cell">从 ↓ / 到 →</th>
              <template v-for="group in columnGroups" :key="group.category">
                <th
                  v-if="group.statuses.length > 0"
                  :colspan="group.statuses.length"
                  class="group-header"
                  :class="'group-' + group.category"
                >
                  {{ localizeCategoryName(group.category) }}
                  <span class="group-count">({{ group.statuses.length }})</span>
                </th>
              </template>
            </tr>
            <tr>
              <th class="corner-cell corner-cell-sub"></th>
              <th
                v-for="(status, colIdx) in filteredStatuses"
                :key="status.id"
                class="col-header"
                :class="{ highlighted: highlightCol === colIdx }"
              >
                <span class="status-dot" :style="{ background: status.color }"></span>
                <span class="col-header-name" :title="localizeStatusName(status.name)">
                  {{ localizeStatusName(status.name) }}
                </span>
              </th>
            </tr>
          </thead>
          <tbody>
            <template v-for="group in rowGroups" :key="group.category">
              <!-- 行分组标题 -->
              <tr v-if="group.statuses.length > 0" class="group-row">
                <td
                  :colspan="filteredStatuses.length + 1"
                  class="group-row-header"
                  :class="'group-' + group.category"
                  @click="toggleGroupCollapse(group.category)"
                >
                  <span class="group-toggle">{{ collapsedGroups.has(group.category) ? '▶' : '▼' }}</span>
                  {{ localizeCategoryName(group.category) }}
                  <span class="group-count">({{ group.statuses.length }})</span>
                </td>
              </tr>
              <!-- 行数据 -->
              <template v-if="!collapsedGroups.has(group.category)">
                <tr
                  v-for="fromStatus in group.statuses"
                  :key="fromStatus.id"
                >
                  <td
                    class="row-header"
                    :class="{ highlighted: highlightRow === getRowIndex(fromStatus) }"
                  >
                    <span class="status-dot" :style="{ background: fromStatus.color }"></span>
                    <span class="row-header-name">{{ localizeStatusName(fromStatus.name) }}</span>
                    <span
                      class="initial-status-star"
                      :class="{ active: isInitialStatus(fromStatus.id) }"
                      :title="isInitialStatus(fromStatus.id) ? '当前为初始状态（点击取消）' : '设为初始状态'"
                      @click.stop="toggleInitialStatus(fromStatus.id)"
                    >★</span>
                  </td>
                  <td
                    v-for="(toStatus, colIdx) in filteredStatuses"
                    :key="toStatus.id"
                    class="matrix-cell"
                    :class="{
                      disabled: fromStatus.id === toStatus.id,
                      clickable: fromStatus.id !== toStatus.id && isAllowed(fromStatus.id, toStatus.id),
                      highlighted: highlightRow === getRowIndex(fromStatus) || highlightCol === colIdx
                    }"
                    @click="fromStatus.id !== toStatus.id && isAllowed(fromStatus.id, toStatus.id) && openActionPanel(fromStatus, toStatus)"
                    @contextmenu.prevent="fromStatus.id !== toStatus.id && isAllowed(fromStatus.id, toStatus.id) && openGuardPanel(fromStatus, toStatus)"
                    @mouseenter="onCellHover(fromStatus, colIdx)"
                    @mouseleave="onCellLeave"
                  >
                    <div v-if="fromStatus.id !== toStatus.id" class="cell-content">
                      <a-checkbox
                        :model-value="isAllowed(fromStatus.id, toStatus.id)"
                        @change="toggleTransition(fromStatus.id, toStatus.id)"
                        @click.stop
                        class="matrix-checkbox"
                      />
                      <span
                        v-if="hasAction(fromStatus.id, toStatus.id)"
                        class="action-dot"
                        title="已配置动作（左键点击管理）"
                      ></span>
                      <span
                        v-if="hasGuard(fromStatus.id, toStatus.id)"
                        class="guard-dot"
                        title="已配置守卫条件（右键点击管理）"
                      ></span>
                    </div>
                    <span v-else class="cell-dash">—</span>
                  </td>
                </tr>
              </template>
            </template>
          </tbody>
        </table>
      </div>

      <!-- 搜索无结果 -->
      <EmptyState
        v-else-if="!loading && statuses.length > 0 && filteredStatuses.length === 0"
        icon="search"
        title="没有匹配的状态"
        description="尝试修改搜索关键词或关闭「只显示已配置」筛选。"
      >
        <template #action>
          <a-button type="primary" @click="resetFilters">重置筛选</a-button>
        </template>
      </EmptyState>

      <!-- 空状态：角色加载失败 -->
      <EmptyState
        v-else-if="!loading && roleLoadingError"
        type="error"
        icon="exclamation-circle"
        title="角色加载失败"
        description="无法加载角色列表，可能是网络问题或服务暂时不可用。"
      >
        <template #action>
          <a-button type="primary" @click="retryLoadRoles">
            <template #icon><icon-refresh /></template>
            重新加载
          </a-button>
        </template>
      </EmptyState>

      <!-- 空状态：未选择角色 -->
      <EmptyState
        v-else-if="!loading && !selectedRole && roles.length > 0"
        icon="user"
        title="请选择角色"
        description="在上方筛选区域选择一个角色，以加载该角色的转换矩阵。"
      />

      <!-- 空状态：角色列表为空 -->
      <EmptyState
        v-else-if="!loading && roles.length === 0 && !roleLoadingError"
        icon="user"
        title="暂无可用角色"
        description="系统中未定义任何项目角色，请先在角色管理中配置角色。"
      />

      <!-- 空状态：状态加载失败 -->
      <EmptyState
        v-else-if="!loading && statusLoadingError"
        type="error"
        icon="exclamation-circle"
        title="状态加载失败"
        description="无法加载状态列表，可能是网络问题或服务暂时不可用。"
      >
        <template #action>
          <a-button type="primary" @click="retryLoadStatuses">
            <template #icon><icon-refresh /></template>
            重新加载
          </a-button>
        </template>
      </EmptyState>

      <!-- 空状态：无状态数据 -->
      <EmptyState
        v-else-if="!loading"
        icon="settings"
        title="暂无状态数据"
        description="系统中未定义任何工单状态，请先配置状态列表。"
      />
    </a-spin>

    <div class="help-text" v-if="statuses.length > 0">
      <icon-info-circle />
      <template v-if="selectedMode === 'normal'">
        勾选单元格表示允许从行状态转换到列状态（针对当前选择的角色，对所有拥有该角色的用户生效）。
      </template>
      <template v-else-if="selectedMode === 'author'">
        勾选单元格表示仅当用户是工单<b>创建者</b>时，额外允许此转换（不影响基础规则）。
      </template>
      <template v-else>
        勾选单元格表示仅当用户是工单<b>负责人</b>时，额外允许此转换（不影响基础规则）。
      </template>
      <span style="color: rgb(var(--arcoblue-6))">■</span> 蓝点=已配置动作（左键）；
      <span style="color: var(--tf-warning)">■</span> 橙点=已配置守卫条件（右键）。
      <span style="color: var(--tf-warning)">★</span> 星标=初始状态（新建工单默认进入的状态）。
      hover 单元格高亮对应行列。
    </div>
    </div>

    <!-- 状态机画布视图 -->
    <div v-show="activeMainTab === 'canvas'" class="canvas-tab-container">
      <!-- 画布视图也复用筛选器 -->
      <div class="content-filters">
        <a-select
          v-model="selectedProject"
          placeholder="选择项目"
          style="width: 180px"
          @change="onFilterChange"
        >
          <a-option value="0">全局（默认）</a-option>
          <a-option
            v-for="p in projects"
            :key="p.id"
            :value="p.id"
          >{{ p.name }}</a-option>
        </a-select>

        <a-select
          v-model="selectedType"
          placeholder="工单类型"
          style="width: 140px"
          @change="onFilterChange"
        >
          <a-option value="*">所有类型</a-option>
          <a-option
            v-for="t in issueTypes"
            :key="t"
            :value="t"
          >{{ t }}</a-option>
        </a-select>

        <a-select
          v-model="selectedRole"
          placeholder="请选择角色（必填）"
          style="width: 160px"
          @change="onFilterChange"
        >
          <a-option
            v-for="role in roles"
            :key="role.id"
            :value="role.id"
          >{{ role.name }}</a-option>
        </a-select>
      </div>

      <!-- 未保存变更提示条 -->
      <div v-if="isDirty" class="dirty-banner">
        <icon-info-circle />
        <span>已修改 {{ pendingChangesCount }} 条转换规则，尚未保存</span>
      </div>

      <WorkflowCanvasView
        v-if="statuses.length > 0 && selectedRole"
        ref="canvasViewRef"
        :statuses="statuses"
        :allowed-transitions="allowedTransitions"
        :initial-status-id="initialStatusId"
        :selected-project="selectedProject"
        :selected-type="selectedType"
        :selected-role="selectedRole"
        :selected-mode="selectedMode"
        @toggle-transition="onCanvasToggleTransition"
        @open-action-panel="openActionPanel"
        @open-guard-panel="openGuardPanel"
        @click-node="onCanvasNodeClick"
      />

      <!-- 空状态 -->
      <EmptyState
        v-else-if="!loading && statuses.length === 0"
        icon="settings"
        title="暂无状态数据"
        description="系统中未定义任何工单状态，请先配置状态列表。"
      />
      <EmptyState
        v-else-if="!loading && !selectedRole"
        icon="user"
        title="请选择角色"
        description="在上方筛选区域选择一个角色，以查看该角色的状态转换图。"
      />
    </div>

    <!-- 自动化规则面板 -->
    <div v-show="activeMainTab === 'rules'" class="rules-tab-container">
      <a-radio-group v-model="activeRuleSubTab" type="button" class="rule-sub-tabs">
        <a-radio value="on_change">字段变更规则</a-radio>
        <a-radio value="on_schedule">定时规则</a-radio>
      </a-radio-group>
      <WorkflowRulePanel
        v-show="activeRuleSubTab === 'on_change'"
        :project-id="selectedProject"
      />
      <ScheduledRulePanel
        v-show="activeRuleSubTab === 'on_schedule'"
        :project-id="selectedProject"
      />
    </div>

    <!-- 动作配置面板 -->
    <TransitionActionPanel
      v-model:visible="actionPanelVisible"
      :project-id="selectedProject"
      :old-status-id="actionPanelFrom"
      :new-status-id="actionPanelTo"
      :old-status-name="actionPanelFromName"
      :new-status-name="actionPanelToName"
      :issue-type="selectedType"
      :transition-id="actionPanelTransitionId"
      :transition-name="actionPanelTransitionName"
      @refresh="onActionRefresh"
      @name-updated="onTransitionNameUpdated"
    />

    <!-- 守卫条件面板 -->
    <TransitionGuardPanel
      v-model:visible="guardPanelVisible"
      :transition-id="guardPanelTransitionId"
      :old-status-name="guardPanelFromName"
      :new-status-name="guardPanelToName"
      :current-conditions="guardPanelConditions"
      @saved="onGuardSaved"
    />

    <!-- 变更历史抽屉 -->
    <WorkflowActivityDrawer
      v-model:visible="showHistory"
      :project-id="selectedProject"
    />
  </AdminPageLayout>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, h } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { IconInfoCircle, IconHistory, IconSearch, IconRefresh, IconLeft } from '@arco-design/web-vue/es/icon'
import { issueApi, projectApi, workflowApi, transitionActionApi } from '@/api'
import type { IssueStatusVO, ProjectVO, RoleVO } from '@/api/types'
import TransitionActionPanel from './components/TransitionActionPanel.vue'
import WorkflowActivityDrawer from './components/WorkflowActivityDrawer.vue'
import WorkflowRulePanel from './WorkflowRulePanel.vue'
import ScheduledRulePanel from './ScheduledRulePanel.vue'
import TransitionGuardPanel from './components/TransitionGuardPanel.vue'
import WorkflowCanvasView from './WorkflowCanvasView.vue'
import AdminPageLayout from '@/components/admin/AdminPageLayout.vue'
import { EmptyState } from '@/components/base'
import { localizeStatusName, localizeCategoryName } from '@/utils/fieldLabels'

const route = useRoute()

const activeMainTab = ref('matrix')
const activeRuleSubTab = ref('on_change')
const selectedProject = ref('0')
const selectedType = ref('*')
const selectedRole = ref('')
const selectedMode = ref<'normal' | 'author' | 'assignee'>('normal')
const loading = ref(false)
const saving = ref(false)
const showHistory = ref(false)

// 从项目设置页跳转过来时的来源项目信息
const sourceProjectId = ref<string | null>(null)
const sourceProjectKey = computed(() => {
  if (!sourceProjectId.value) return ''
  const project = projects.value.find(p => p.id === sourceProjectId.value)
  return project?.key || sourceProjectId.value
})
const sourceProjectName = computed(() => {
  if (!sourceProjectId.value) return ''
  const project = projects.value.find(p => p.id === sourceProjectId.value)
  return project?.name || ''
})

const statuses = ref<IssueStatusVO[]>([])
const projects = ref<ProjectVO[]>([])
const roles = ref<RoleVO[]>([])
const issueTypes = ref<string[]>([])

// 角色加载状态
const roleLoadingError = ref(false)
const statusLoadingError = ref(false)

// 矩阵工具栏状态
const searchKeyword = ref('')
const onlyConfigured = ref(false)
const collapsedGroups = reactive(new Set<string>())

// 行列高亮
const highlightRow = ref<number | null>(null)
const highlightCol = ref<number | null>(null)

// 转换矩阵 Set: "fromId-toId"
const allowedTransitions = reactive(new Set<string>())

// Dirty state 管理：保存初始加载的快照，用于对比当前状态
const originalTransitions = reactive(new Set<string>())

// 是否有未保存的变更
const isDirty = computed(() => {
  if (allowedTransitions.size !== originalTransitions.size) return true
  for (const key of allowedTransitions) {
    if (!originalTransitions.has(key)) return true
  }
  for (const key of originalTransitions) {
    if (!allowedTransitions.has(key)) return true
  }
  return false
})

// 变更数量统计（新增 + 移除的规则条数）
const pendingChangesCount = computed(() => {
  let count = 0
  for (const key of allowedTransitions) {
    if (!originalTransitions.has(key)) count++
  }
  for (const key of originalTransitions) {
    if (!allowedTransitions.has(key)) count++
  }
  return count
})

// 乐观锁版本号（从 GET 接口获取，保存时回传）
const matrixVersion = ref<number | null>(null)

// 当前选中角色名称（用于确认对话框展示）
const selectedRoleName = computed(() => {
  const role = roles.value.find(r => r.id === selectedRole.value)
  return role?.name || '未选择角色'
})

// 动作指示器: "fromId-toId" 有配置动作的转换路径
const actionPaths = reactive(new Set<string>())

// 动作面板状态
const actionPanelVisible = ref(false)
const actionPanelFrom = ref('')
const actionPanelTo = ref('')
const actionPanelFromName = ref('')
const actionPanelToName = ref('')
const actionPanelTransitionId = ref('')
const actionPanelTransitionName = ref('')

// 守卫条件面板状态
const guardPanelVisible = ref(false)
const guardPanelTransitionId = ref('')
const guardPanelFromName = ref('')
const guardPanelToName = ref('')
const guardPanelConditions = ref<string | undefined>(undefined)

// 守卫条件指示器：有守卫条件的转换路径（key: "fromId-toId"，value: transition id）
const guardPaths = reactive(new Map<string, string>())
// 每个转换路径对应的 transition id（用于配置守卫条件）
const transitionIdMap = reactive(new Map<string, string>())
// 每个转换路径对应的当前守卫条件 JSON（用于编辑面板回填）
const transitionConditionsMap = reactive(new Map<string, string>())
// 每个转换路径对应的转换显示名（用于面板展示和编辑）
const transitionNameMap = reactive(new Map<string, string>())

// 初始状态配置：当前项目+issueType 的初始状态 ID
const initialStatusId = ref<string | null>(null)

// --- 分类顺序 ---
const categoryOrder = ['open', 'in_progress', 'done', 'cancelled']

// --- 筛选后的状态列表 ---
const filteredStatuses = computed(() => {
  let result = [...statuses.value]

  // 按搜索关键词筛选
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase()
    result = result.filter(s => {
      const localized = localizeStatusName(s.name).toLowerCase()
      const original = s.name.toLowerCase()
      return localized.includes(kw) || original.includes(kw)
    })
  }

  // 只显示已配置转换
  if (onlyConfigured.value) {
    const idsWithTransitions = new Set<string>()
    for (const key of allowedTransitions) {
      const [from, to] = key.split('-')
      idsWithTransitions.add(from)
      idsWithTransitions.add(to)
    }
    result = result.filter(s => idsWithTransitions.has(s.id))
  }

  // 按 category 排序
  result.sort((a, b) => {
    const ai = categoryOrder.indexOf(a.category)
    const bi = categoryOrder.indexOf(b.category)
    if (ai !== bi) return ai - bi
    return (a.sortOrder ?? 0) - (b.sortOrder ?? 0)
  })

  return result
})

// --- 分组（行和列） ---
interface StatusGroup {
  category: string
  statuses: IssueStatusVO[]
}

const rowGroups = computed<StatusGroup[]>(() => {
  return buildGroups(filteredStatuses.value)
})

const columnGroups = computed<StatusGroup[]>(() => {
  return buildGroups(filteredStatuses.value)
})

function buildGroups(list: IssueStatusVO[]): StatusGroup[] {
  const groups: StatusGroup[] = []
  for (const cat of categoryOrder) {
    const items = list.filter(s => s.category === cat)
    if (items.length > 0) {
      groups.push({ category: cat, statuses: items })
    }
  }
  // 处理未知分类
  const known = new Set(categoryOrder)
  const unknown = list.filter(s => !known.has(s.category))
  if (unknown.length > 0) {
    groups.push({ category: 'other', statuses: unknown })
  }
  return groups
}

// --- 行索引（用于高亮） ---
function getRowIndex(status: IssueStatusVO): number {
  return filteredStatuses.value.findIndex(s => s.id === status.id)
}

// --- 分组折叠 ---
function toggleGroupCollapse(category: string) {
  if (collapsedGroups.has(category)) {
    collapsedGroups.delete(category)
  } else {
    collapsedGroups.add(category)
  }
}

// --- Hover 高亮 ---
function onCellHover(fromStatus: IssueStatusVO, colIdx: number) {
  highlightRow.value = getRowIndex(fromStatus)
  highlightCol.value = colIdx
}

function onCellLeave() {
  highlightRow.value = null
  highlightCol.value = null
}

// --- 重置筛选 ---
function resetFilters() {
  searchKeyword.value = ''
  onlyConfigured.value = false
}

function isAllowed(from: string, to: string) {
  return allowedTransitions.has(`${from}-${to}`)
}

function toggleTransition(from: string, to: string) {
  const key = `${from}-${to}`
  if (allowedTransitions.has(key)) {
    allowedTransitions.delete(key)
  } else {
    allowedTransitions.add(key)
  }
}

function hasAction(from: string, to: string): boolean {
  return actionPaths.has(`${from}-${to}`)
}

function hasGuard(from: string, to: string): boolean {
  return guardPaths.has(`${from}-${to}`)
}

function openActionPanel(fromStatus: IssueStatusVO, toStatus: IssueStatusVO) {
  if (!isAllowed(fromStatus.id, toStatus.id)) return
  actionPanelFrom.value = fromStatus.id
  actionPanelTo.value = toStatus.id
  actionPanelFromName.value = localizeStatusName(fromStatus.name)
  actionPanelToName.value = localizeStatusName(toStatus.name)
  const key = `${fromStatus.id}-${toStatus.id}`
  actionPanelTransitionId.value = transitionIdMap.get(key) || ''
  actionPanelTransitionName.value = transitionNameMap.get(key) || ''
  actionPanelVisible.value = true
}

// 用于保存后自动打开守卫面板的临时存储
const pendingGuardPanel = reactive<{
  fromStatus: IssueStatusVO | null
  toStatus: IssueStatusVO | null
}>({
  fromStatus: null,
  toStatus: null
})

function openGuardPanel(fromStatus: IssueStatusVO, toStatus: IssueStatusVO) {
  const key = `${fromStatus.id}-${toStatus.id}`
  const tid = transitionIdMap.get(key)
  if (!tid) {
    // 转换尚未保存，弹出 Modal 提示并提供"立即保存"选项
    Modal.confirm({
      title: '需要先保存工作流',
      content: `您需要先保存当前的工作流变更，才能配置「${localizeStatusName(fromStatus.name)} → ${localizeStatusName(toStatus.name)}」的守卫条件。现在保存吗？`,
      okText: '立即保存',
      cancelText: '取消',
      async onOk() {
        // 记录待打开的守卫面板信息
        pendingGuardPanel.fromStatus = fromStatus
        pendingGuardPanel.toStatus = toStatus
        // 触发保存流程（直接调用内部保存逻辑，不再弹确认框）
        await doSaveMatrixAndOpenGuard()
      }
    })
    return
  }
  // 获取当前守卫条件（从 transitions 数据获取）
  const conditions = getTransitionConditions(key)
  guardPanelTransitionId.value = tid
  guardPanelFromName.value = localizeStatusName(fromStatus.name)
  guardPanelToName.value = localizeStatusName(toStatus.name)
  guardPanelConditions.value = conditions
  guardPanelVisible.value = true
}

// 保存工作流并在成功后自动打开守卫面板
async function doSaveMatrixAndOpenGuard() {
  const transitions = Array.from(allowedTransitions).map(key => {
    const [from, to] = key.split('-')
    return { from: Number(from), to: Number(to), allowed: true }
  })

  saving.value = true
  try {
    const projectId = selectedProject.value || '0'
    await workflowApi.updateTransitionMatrix(projectId, {
      issueType: selectedType.value,
      roleId: Number(selectedRole.value),
      author: selectedMode.value === 'author' ? true : false,
      assignee: selectedMode.value === 'assignee' ? true : false,
      version: matrixVersion.value ?? undefined,
      transitions
    })
    Message.success('工作流已保存')
    // 保存成功后重新加载以获取最新版本号和 transition id
    await loadMatrix()
    
    // 如果有待打开的守卫面板，现在打开它
    if (pendingGuardPanel.fromStatus && pendingGuardPanel.toStatus) {
      const fromStatus = pendingGuardPanel.fromStatus
      const toStatus = pendingGuardPanel.toStatus
      const key = `${fromStatus.id}-${toStatus.id}`
      const tid = transitionIdMap.get(key)
      
      // 清除临时存储
      pendingGuardPanel.fromStatus = null
      pendingGuardPanel.toStatus = null
      
      if (tid) {
        // 现在可以打开守卫面板了
        const conditions = getTransitionConditions(key)
        guardPanelTransitionId.value = tid
        guardPanelFromName.value = localizeStatusName(fromStatus.name)
        guardPanelToName.value = localizeStatusName(toStatus.name)
        guardPanelConditions.value = conditions
        guardPanelVisible.value = true
      }
    }
  } catch (e: any) {
    const code = e.response?.data?.code
    if (code === 40911) {
      // 乐观锁冲突：工作流已被其他人修改
      Modal.warning({
        title: '保存失败',
        content: '工作流已被其他人修改，请刷新后重试。点击"刷新"获取最新数据。',
        okText: '刷新',
        async onOk() {
          await loadMatrix()
          Message.info('已刷新为最新数据，请重新编辑后保存')
        }
      })
    } else {
      Message.error(e.response?.data?.message || '保存失败，请检查权限或重试')
    }
    // 清除待打开的守卫面板信息
    pendingGuardPanel.fromStatus = null
    pendingGuardPanel.toStatus = null
  } finally {
    saving.value = false
  }
}

function getTransitionConditions(key: string): string | undefined {
  return transitionConditionsMap.get(key)
}

function onGuardSaved() {
  // 重新加载矩阵以获取最新守卫条件
  loadMatrix()
}

// ========== 画布视图 ==========
const canvasViewRef = ref<InstanceType<typeof WorkflowCanvasView> | null>(null)

function onCanvasToggleTransition(fromId: string, toId: string) {
  toggleTransition(fromId, toId)
}

function onCanvasNodeClick(status: IssueStatusVO) {
  // 点击节点时切换初始状态
  toggleInitialStatus(status.id)
}

async function loadActionPaths() {
  try {
    const projectId = selectedProject.value || '0'
    const res = await transitionActionApi.list(projectId)
    const actions = res.data || []
    actionPaths.clear()
    for (const a of actions) {
      actionPaths.add(`${a.oldStatusId}-${a.newStatusId}`)
    }
  } catch {
    actionPaths.clear()
  }
}

function onActionRefresh() {
  loadActionPaths()
}

function onTransitionNameUpdated(_transitionId: string, newName: string | null) {
  // 更新本地缓存
  const key = `${actionPanelFrom.value}-${actionPanelTo.value}`
  if (newName) {
    transitionNameMap.set(key, newName)
  } else {
    transitionNameMap.delete(key)
  }
}

// 记录上一次成功加载的筛选条件（用于取消时恢复）
const lastLoadedFilters = reactive({
  project: '0',
  type: '*',
  role: '',
  mode: 'normal' as 'normal' | 'author' | 'assignee'
})

function snapshotFilters() {
  lastLoadedFilters.project = selectedProject.value
  lastLoadedFilters.type = selectedType.value
  lastLoadedFilters.role = selectedRole.value
  lastLoadedFilters.mode = selectedMode.value
}

function revertFilters() {
  selectedProject.value = lastLoadedFilters.project
  selectedType.value = lastLoadedFilters.type
  selectedRole.value = lastLoadedFilters.role
  selectedMode.value = lastLoadedFilters.mode
}

function doFilterChange() {
  snapshotFilters()
  loadMatrix()
  loadActionPaths()
  loadInitialStatuses()
}

function onFilterChange() {
  if (isDirty.value) {
    const count = pendingChangesCount.value
    Modal.confirm({
      title: '未保存的变更',
      content: '您有 ' + count + ' 条未保存的转换规则变更，切换筛选条件将丢弃这些变更。确定要继续吗？',
      okText: '丢弃变更',
      cancelText: '取消',
      onOk() {
        doFilterChange()
      },
      onCancel() {
        revertFilters()
      }
    })
    return
  }
  doFilterChange()
}

async function loadStatuses() {
  statusLoadingError.value = false
  try {
    const res = await issueApi.listStatuses()
    statuses.value = res.data || []
  } catch {
    statuses.value = []
    statusLoadingError.value = true
    Message.error('加载状态列表失败')
  }
}

async function retryLoadStatuses() {
  await loadStatuses()
  if (!statusLoadingError.value) {
    // 状态加载成功后检查是否可以加载矩阵
    if (selectedRole.value) {
      await loadMatrix()
    }
    Message.success('状态加载成功')
  }
}

async function loadProjects() {
  try {
    const res = await projectApi.list({ page: 1, pageSize: 100 })
    projects.value = res.data?.list || []
  } catch {
    projects.value = []
    Message.error('加载项目列表失败')
  }
}

async function loadRoles() {
  roleLoadingError.value = false
  try {
    const res = await workflowApi.listProjectRoles()
    roles.value = res.data || []
    // 默认选中第一个角色
    if (roles.value.length > 0 && !selectedRole.value) {
      selectedRole.value = roles.value[0].id
    }
  } catch {
    roles.value = []
    roleLoadingError.value = true
    Message.error('加载角色列表失败')
  }
}

async function retryLoadRoles() {
  await loadRoles()
  if (!roleLoadingError.value && selectedRole.value) {
    // 角色加载成功后自动加载矩阵
    await loadMatrix()
    Message.success('角色加载成功')
  }
}

async function loadIssueTypes() {
  try {
    const res = await workflowApi.listIssueTypes()
    issueTypes.value = res.data || []
  } catch {
    issueTypes.value = ['Bug', 'Task', 'Feature']
  }
}

async function loadMatrix() {
  if (!selectedRole.value) return
  loading.value = true
  try {
    const params: Record<string, string | boolean> = { roleId: selectedRole.value }
    if (selectedType.value !== '*') params.issueType = selectedType.value

    // 按模式过滤 author/assignee
    if (selectedMode.value === 'normal') {
      params.author = false
      params.assignee = false
    } else if (selectedMode.value === 'author') {
      params.author = true
    } else if (selectedMode.value === 'assignee') {
      params.assignee = true
    }

    const projectId = selectedProject.value || '0'

    const res = await workflowApi.getTransitionMatrix(projectId, params)
    const matrix = res.data
    const transitions = matrix?.transitions || []

    allowedTransitions.clear()
    originalTransitions.clear()
    transitionIdMap.clear()
    guardPaths.clear()
    transitionConditionsMap.clear()
    transitionNameMap.clear()
    for (const t of transitions) {
      const key = `${t.oldStatusId}-${t.newStatusId}`
      allowedTransitions.add(key)
      originalTransitions.add(key)
      // 记录 transition id（用于守卫条件配置）
      transitionIdMap.set(key, t.id)
      // 存储 conditions JSON（用于编辑面板回填）
      if (t.conditions) {
        transitionConditionsMap.set(key, t.conditions)
      }
      // 存储转换显示名
      if (t.transitionName) {
        transitionNameMap.set(key, t.transitionName)
      }
      // 记录有守卫条件的路径
      if (t.conditions && t.conditions !== '{}' && t.conditions !== 'null') {
        try {
          const parsed = JSON.parse(t.conditions)
          if (parsed.conditions && parsed.conditions.length > 0) {
            guardPaths.set(key, t.id)
          }
        } catch {
          // 忽略解析失败
        }
      }
    }

    // 记录版本号用于乐观锁
    matrixVersion.value = matrix?.version ?? null
  } catch {
    allowedTransitions.clear()
    originalTransitions.clear()
    matrixVersion.value = null
    Message.error('加载工作流数据失败')
  } finally {
    loading.value = false
  }
}

/**
 * 加载当前项目+issueType 的初始状态配置
 */
async function loadInitialStatuses() {
  const projectId = selectedProject.value || '0'
  try {
    const res = await workflowApi.listInitialStatuses(projectId)
    const configs = res.data || []
    // 找到匹配当前 issueType 的配置（精确类型优先，再通配）
    const currentType = selectedType.value || '*'
    const exactMatch = configs.find(c => c.issueType === currentType)
    const wildcardMatch = configs.find(c => c.issueType === '*')
    const match = exactMatch || wildcardMatch
    initialStatusId.value = match ? match.statusId : null
  } catch {
    initialStatusId.value = null
  }
}

/**
 * 判断指定状态是否为当前筛选条件下的初始状态
 */
function isInitialStatus(statusId: string): boolean {
  return initialStatusId.value === statusId
}

/**
 * 切换指定状态的初始状态标记
 */
async function toggleInitialStatus(statusId: string) {
  const projectId = selectedProject.value || '0'
  const issueType = selectedType.value || '*'
  try {
    if (isInitialStatus(statusId)) {
      // 取消初始状态
      await workflowApi.clearInitialStatus(projectId, issueType)
      initialStatusId.value = null
      Message.success('已清除初始状态设置')
    } else {
      // 设为初始状态
      await workflowApi.setInitialStatus(projectId, statusId, issueType)
      initialStatusId.value = statusId
      Message.success('已设为初始状态')
    }
  } catch {
    Message.error('设置初始状态失败')
  }
}

async function saveMatrix() {
  const modeLabel = selectedMode.value === 'normal' ? '基础规则'
    : selectedMode.value === 'author' ? '创建者额外规则' : '负责人额外规则'

  // 计算变更详情
  const added: { fromId: string; toId: string; fromName: string; toName: string }[] = []
  const removed: { fromId: string; toId: string; fromName: string; toName: string }[] = []

  const statusMap = new Map(statuses.value.map(s => [s.id, s]))

  for (const key of allowedTransitions) {
    if (!originalTransitions.has(key)) {
      const [fromId, toId] = key.split('-')
      const fromStatus = statusMap.get(fromId)
      const toStatus = statusMap.get(toId)
      added.push({
        fromId, toId,
        fromName: fromStatus ? localizeStatusName(fromStatus.name) : fromId,
        toName: toStatus ? localizeStatusName(toStatus.name) : toId
      })
    }
  }
  for (const key of originalTransitions) {
    if (!allowedTransitions.has(key)) {
      const [fromId, toId] = key.split('-')
      const fromStatus = statusMap.get(fromId)
      const toStatus = statusMap.get(toId)
      removed.push({
        fromId, toId,
        fromName: fromStatus ? localizeStatusName(fromStatus.name) : fromId,
        toName: toStatus ? localizeStatusName(toStatus.name) : toId
      })
    }
  }

  const totalChanges = added.length + removed.length

  // 获取影响分析数据（仅在有删除转换时）
  let impactData: Record<string, number> | null = null
  let totalAffected = 0

  if (removed.length > 0) {
    try {
      // 取出删除转换的源状态 ID（去重）
      const affectedStatusIds = [...new Set(removed.map(r => Number(r.fromId)))]
      const projectIdNum = selectedProject.value ? Number(selectedProject.value) : 0
      const res = await workflowApi.analyzeImpact({
        statusIds: affectedStatusIds,
        projectId: projectIdNum > 0 ? projectIdNum : undefined,
        issueType: selectedType.value !== '*' ? selectedType.value : undefined
      })
      if (res.code === 0 && res.data) {
        impactData = res.data.statusIssueCounts
        totalAffected = res.data.totalAffectedIssues
      }
    } catch {
      // 影响分析失败不阻断保存流程，仅展示变更摘要
    }
  }

  // 构建确认对话框的内容 VNode
  const renderContent = () => {
    const children: any[] = []

    // 变更摘要
    children.push(h('div', { style: 'margin-bottom: 12px; font-weight: 500; color: var(--color-text-1);' },
      `本次变更（${totalChanges} 条规则）：`
    ))

    // 显示新增的转换
    if (added.length > 0) {
      const addedItems = added.slice(0, 5).map(item =>
        h('div', { style: 'padding: 2px 0; color: var(--color-text-2); font-size: 13px;' }, [
          h('span', { style: 'color: var(--tf-success); margin-right: 6px;' }, '＋'),
          `新增转换：${item.fromName} → ${item.toName}`
        ])
      )
      if (added.length > 5) {
        addedItems.push(h('div', { style: 'padding: 2px 0; color: var(--color-text-3); font-size: 12px;' },
          `…及另外 ${added.length - 5} 条新增转换`
        ))
      }
      children.push(...addedItems)
    }

    // 显示删除的转换
    if (removed.length > 0) {
      const removedItems = removed.slice(0, 5).map(item =>
        h('div', { style: 'padding: 2px 0; color: var(--color-text-2); font-size: 13px;' }, [
          h('span', { style: 'color: var(--tf-danger); margin-right: 6px;' }, '✕'),
          `删除转换：${item.fromName} → ${item.toName}`
        ])
      )
      if (removed.length > 5) {
        removedItems.push(h('div', { style: 'padding: 2px 0; color: var(--color-text-3); font-size: 12px;' },
          `…及另外 ${removed.length - 5} 条删除转换`
        ))
      }
      children.push(...removedItems)
    }

    // 影响分析（仅在有删除转换时显示）
    if (removed.length > 0 && impactData) {
      children.push(h('div', {
        style: 'margin-top: 16px; padding: 10px 12px; border-radius: 6px; background: var(--color-warning-light-1); border: 1px solid var(--color-warning-light-3);'
      }, [
        h('div', { style: 'font-weight: 500; color: var(--color-warning-6); margin-bottom: 6px; font-size: 13px;' },
          '⚠️ 影响分析'
        ),
        ...Object.entries(impactData)
          .filter(([_, count]) => count > 0)
          .map(([statusId, count]) => {
            const status = statusMap.get(statusId)
            const statusName = status ? localizeStatusName(status.name) : statusId
            return h('div', { style: 'padding: 2px 0; color: var(--color-text-2); font-size: 13px;' },
              `当前有 ${count} 个工单处于"${statusName}"状态`)
          }),
        totalAffected > 0
          ? h('div', { style: 'padding-top: 4px; color: var(--color-text-3); font-size: 12px;' },
              `这些工单的"${selectedRoleName.value}"角色用户将失去被删除的转换选项`)
          : null
      ].filter(Boolean)))
    } else if (removed.length > 0 && !impactData) {
      // 影响分析加载失败时的提示
      children.push(h('div', {
        style: 'margin-top: 12px; color: var(--color-text-3); font-size: 12px;'
      }, '（影响分析数据暂不可用）'))
    }

    // 模式+筛选上下文信息
    children.push(h('div', {
      style: 'margin-top: 12px; padding-top: 8px; border-top: 1px solid var(--color-border-2); color: var(--color-text-3); font-size: 12px;'
    }, `适用范围：${modeLabel} / ${selectedRoleName.value} / ${selectedProject.value === '0' ? '全局' : '项目级'}`))

    return h('div', { style: 'line-height: 1.6;' }, children)
  }

  Modal.confirm({
    title: `确认应用 ${totalChanges} 项工作流变更`,
    content: renderContent,
    okText: '确认保存',
    cancelText: '取消',
    width: 520,
    async onOk() {
      const transitions = Array.from(allowedTransitions).map(key => {
        const [from, to] = key.split('-')
        return { from: Number(from), to: Number(to), allowed: true }
      })

      saving.value = true
      try {
        const projectId = selectedProject.value || '0'
        await workflowApi.updateTransitionMatrix(projectId, {
          issueType: selectedType.value,
          roleId: Number(selectedRole.value),
          author: selectedMode.value === 'author' ? true : false,
          assignee: selectedMode.value === 'assignee' ? true : false,
          version: matrixVersion.value ?? undefined,
          transitions
        })
        Message.success('工作流已保存')
        // 保存成功后重新加载以获取最新版本号
        await loadMatrix()
      } catch (e: any) {
        const code = e.response?.data?.code
        if (code === 40911) {
          // 乐观锁冲突：工作流已被其他人修改
          Modal.warning({
            title: '保存失败',
            content: '工作流已被其他人修改，请刷新后重试。点击"刷新"获取最新数据。',
            okText: '刷新',
            async onOk() {
              await loadMatrix()
              Message.info('已刷新为最新数据，请重新编辑后保存')
            }
          })
        } else {
          Message.error(e.response?.data?.message || '保存失败，请检查权限或重试')
        }
      } finally {
        saving.value = false
      }
    }
  })
}

onMounted(async () => {
  // 先加载基础数据（状态、项目、角色、工单类型）
  await Promise.all([loadStatuses(), loadProjects(), loadRoles(), loadIssueTypes()])
  
  // 检查是否从项目设置页跳转过来（携带 project query 参数）
  const projectFromQuery = route.query.project as string | undefined
  if (projectFromQuery && projectFromQuery !== '0') {
    // 记录来源项目，用于面包屑导航
    sourceProjectId.value = projectFromQuery
    // 自动选择对应项目
    selectedProject.value = projectFromQuery
    snapshotFilters()
  }
  
  await Promise.all([loadMatrix(), loadActionPaths(), loadInitialStatuses()])
  snapshotFilters()

  // 浏览器关闭/刷新时提示
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

function handleBeforeUnload(e: BeforeUnloadEvent) {
  if (isDirty.value) {
    // 推荐方式：调用 preventDefault() 触发浏览器标准的离开确认对话框
    // 现代浏览器不支持自定义消息，会显示浏览器默认的"离开此网站？"提示
    e.preventDefault()
    // 兼容旧版浏览器（Chrome/Edge < 119）
    e.returnValue = true
  }
}

// Vue Router 离开页面前拦截
onBeforeRouteLeave(() => {
  if (!isDirty.value) {
    return true
  }
  return new Promise<boolean>((resolve) => {
    Modal.confirm({
      title: '未保存的变更',
      content: '您有未保存的工作流变更，确定要离开吗？离开后变更将丢失。',
      okText: '离开页面',
      cancelText: '留在此页',
      onOk() {
        resolve(true)
      },
      onCancel() {
        resolve(false)
      }
    })
  })
})
</script>

<style scoped>
/* 面包屑导航 */
.breadcrumb-nav {
  margin-bottom: 16px;
  padding: 12px 16px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
}

.breadcrumb-nav :deep(.arco-breadcrumb-item a) {
  color: rgb(var(--arcoblue-6));
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.breadcrumb-nav :deep(.arco-breadcrumb-item a:hover) {
  color: rgb(var(--arcoblue-5));
}

.breadcrumb-back-icon {
  font-size: 14px;
}

.breadcrumb-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-text-3);
}

.breadcrumb-hint strong {
  color: var(--color-text-1);
  font-weight: 500;
}

.workflow-main-tabs {
  :deep(.arco-tabs-nav) {
    &::before {
      display: none;
    }
  }
}

.content-filters {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: var(--tf-bg-surface);
  border-radius: 6px;
}

/* 矩阵工具栏 */
.matrix-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 8px 12px;
  background: var(--tf-bg-surface);
  border-radius: 6px;
}

.toolbar-stats {
  font-size: 12px;
  color: var(--tf-text-muted);
  margin-left: auto;
}

.toolbar-divider {
  margin: 0 8px;
  color: var(--tf-border);
}

.matrix-container {
  overflow: auto;
  flex: 1;
  max-height: 100%;
}

.matrix-table {
  border-collapse: collapse;
  width: 100%;
  min-width: max-content;
  table-layout: auto;
  background: var(--tf-bg-surface);
}

.matrix-table th,
.matrix-table td {
  padding: 6px 8px;
  border: 1px solid var(--tf-border);
  text-align: center;
  font-size: 11px;
}

/* Sticky thead */
.matrix-table thead {
  position: sticky;
  top: 0;
  z-index: 4;
}

.matrix-table thead th {
  background: var(--tf-bg-elevated);
}

/* 分组头（列方向） */
.group-header {
  background: var(--tf-bg-elevated);
  color: var(--tf-text-tertiary);
  font-weight: 600;
  font-size: 11px;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  padding: 4px 8px;
  border-bottom: none;
}

.group-header.group-open { border-top: 2px solid var(--tf-accent); }
.group-header.group-in_progress { border-top: 2px solid var(--tf-warning); }
.group-header.group-done { border-top: 2px solid var(--tf-success); }
.group-header.group-cancelled { border-top: 2px solid var(--tf-danger); }

.group-count {
  font-weight: 400;
  opacity: 0.7;
}

.corner-cell {
  background: var(--tf-bg-elevated);
  color: var(--tf-text-secondary);
  font-weight: 500;
  text-align: left;
  min-width: 140px;
  position: sticky;
  left: 0;
  z-index: 5;
}

.corner-cell-sub {
  border-top: none;
}

.col-header {
  background: var(--tf-bg-elevated);
  color: var(--tf-text-primary);
  font-weight: 500;
  white-space: nowrap;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: background-color 100ms ease;
}

.col-header.highlighted {
  background: var(--tf-bg-hover);
}

.col-header-name {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
}

/* 行分组标题 */
.group-row-header {
  background: var(--tf-bg-elevated);
  color: var(--tf-text-tertiary);
  font-weight: 600;
  font-size: 11px;
  letter-spacing: 0.5px;
  text-align: left;
  padding: 4px 12px;
  cursor: pointer;
  user-select: none;
  position: sticky;
  left: 0;
  z-index: 2;
  transition: background-color 150ms ease;
}

.group-row-header:hover {
  background: var(--tf-bg-hover);
}

.group-row-header.group-open { border-left: 3px solid var(--tf-accent); }
.group-row-header.group-in_progress { border-left: 3px solid var(--tf-warning); }
.group-row-header.group-done { border-left: 3px solid var(--tf-success); }
.group-row-header.group-cancelled { border-left: 3px solid var(--tf-danger); }

.group-toggle {
  display: inline-block;
  width: 16px;
  font-size: 10px;
}

.row-header {
  background: var(--tf-bg-surface);
  color: var(--tf-text-primary);
  text-align: left;
  font-weight: 500;
  white-space: nowrap;
  position: sticky;
  left: 0;
  z-index: 1;
  transition: background-color 100ms ease;
}

.row-header-name {
  flex: 1;
}

.initial-status-star {
  display: inline-block;
  margin-left: 6px;
  font-size: 12px;
  color: var(--tf-text-muted);
  cursor: pointer;
  opacity: 0;
  transition: opacity 150ms ease, color 150ms ease;
}

.row-header:hover .initial-status-star {
  opacity: 1;
}

.initial-status-star.active {
  opacity: 1;
  color: var(--tf-warning);
}

.initial-status-star:hover {
  color: var(--tf-warning);
  opacity: 1;
}

.row-header.highlighted {
  background: var(--tf-bg-hover);
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
}

.matrix-cell {
  background: var(--tf-bg-body);
  transition: background-color 100ms ease;
}
.matrix-cell.disabled {
  background: var(--tf-bg-elevated);
}
.matrix-cell.clickable {
  cursor: pointer;
}
.matrix-cell.clickable:hover {
  background: var(--tf-bg-hover);
}
.matrix-cell.highlighted {
  background: var(--tf-bg-hover);
}

.cell-content {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.action-dot {
  position: absolute;
  top: -4px;
  right: -8px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--tf-accent);
  box-shadow: 0 0 0 2px var(--tf-bg-body);
}

.guard-dot {
  position: absolute;
  top: -4px;
  right: -16px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--tf-warning);
  box-shadow: 0 0 0 2px var(--tf-bg-body);
}

.matrix-checkbox {
  line-height: 1;
}

.matrix-checkbox :deep(.arco-checkbox-icon) {
  width: 14px;
  height: 14px;
}

.cell-dash {
  color: var(--tf-text-muted);
  font-size: 10px;
}

.help-text {
  margin-top: 16px;
  font-size: var(--font-size-sm);
  color: var(--tf-text-muted);
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 模式 Tab */
.mode-tabs {
  margin-bottom: 12px;
}

.mode-info-icon {
  margin-left: 4px;
  font-size: 12px;
  color: var(--tf-text-muted);
  vertical-align: middle;
  cursor: help;
}

/* 未保存变更提示条 */
/* 矩阵区域 flex 填充 */
.matrix-grow-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.matrix-grow-area :deep(.arco-spin) {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.matrix-grow-area :deep(.arco-spin-children) {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.dirty-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 12px;
  background: var(--tf-accent-light);
  border: 1px solid rgba(var(--arcoblue-6), 0.2);
  border-radius: 6px;
  font-size: 12px;
  color: rgb(var(--arcoblue-6));
  animation: fadeIn 200ms ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

.rules-tab-container {
  padding-top: 8px;
}

.rule-sub-tabs {
  margin-bottom: 16px;
}

.canvas-tab-container {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.canvas-tab-container .content-filters {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: var(--tf-bg-surface);
  border-radius: 6px;
}

.canvas-tab-container .workflow-canvas-view {
  flex: 1;
  min-height: 500px;
}
</style>
