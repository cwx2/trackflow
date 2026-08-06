<template>
  <div class="timesheet-page">
    <!-- Header -->
    <div class="timesheet-header">
      <h1 class="page-title">时间表</h1>
    </div>

    <!-- Tabs -->
    <div class="timesheet-tabs">
      <button class="tab-btn" :class="{ active: activeTab === 'people' }" @click="switchTab('people')">人员</button>
      <button class="tab-btn" :class="{ active: activeTab === 'projects' }" @click="switchTab('projects')">项目</button>
      <button class="tab-btn" :class="{ active: activeTab === 'workgroups' }" @click="switchTab('workgroups')">工作群组</button>
    </div>

    <!-- ===================== 人员视图 ===================== -->
    <template v-if="activeTab === 'people'">
      <!-- User selector & filters -->
      <div class="timesheet-controls">
        <div class="controls-left">
          <div class="user-selector-area">
            <template v-if="canViewOthers">
              <a-select
                v-model="selectedUserId"
                placeholder="选择用户"
                allow-search
                allow-clear
                style="width: 220px"
                :filter-option="false"
                @search="searchUsers"
                @change="onUserChange"
                @clear="onUserChange(undefined)"
              >
                <a-option v-for="u in selectableUsers" :key="u.id" :value="u.id" :label="u.displayName || u.username">
                  <div class="user-option">
                    <span class="user-option-avatar">{{ (u.displayName || u.username).charAt(0) }}</span>
                    <span class="user-option-name">{{ u.displayName || u.username }}</span>
                    <span v-if="u.id === authStore.user?.id" class="user-option-self">(我)</span>
                  </div>
                </a-option>
              </a-select>
            </template>
            <template v-else>
              <div class="user-selector-static">
                <span class="user-avatar-dot"></span>
                <span class="user-name">{{ currentUserName }}</span>
              </div>
            </template>
          </div>
          <div class="filters">
            <span class="filter-label">项目:</span>
            <a-select
              v-model="filterProjectId"
              placeholder="全部"
              allow-clear
              allow-search
              :style="{ width: '160px' }"
              size="small"
              @change="onFilterChange"
            >
              <a-option v-for="p in filterProjects" :key="p.id" :value="p.id">
                {{ p.key }} - {{ p.name }}
              </a-option>
            </a-select>
            <span class="filter-label">工作类型:</span>
            <a-select
              v-model="filterWorkType"
              placeholder="全部"
              allow-clear
              :style="{ width: '140px' }"
              size="small"
              @change="onFilterChange"
            >
              <a-option v-for="wt in filterWorkTypes" :key="wt.id" :value="wt.id">
                <span v-if="wt.color" class="attr-value-dot" :style="{ background: wt.color }"></span>
                {{ wt.name }}
              </a-option>
            </a-select>
            <a v-if="filterProjectId || filterWorkType" class="filter-reset" @click="resetFilters">重置</a>
          </div>
        </div>
      </div>

      <!-- Date range & navigation -->
      <div class="timesheet-datebar">
        <div class="date-info">
          <span class="date-range">{{ dateRangeLabel }}</span>
          <span class="total-time">{{ selectedUserDisplayName }} 总已用时间: {{ formatDuration(weekTotal) }}</span>
        </div>
        <div class="date-nav">
          <button class="nav-btn" @click="navigate(-1)">←</button>
          <button class="nav-btn today-btn" @click="goToday">今天</button>
          <button class="nav-btn" @click="navigate(1)">→</button>
          <div class="view-toggle">
            <button class="toggle-btn" :class="{ active: viewMode === 'week' }" @click="switchView('week')">周</button>
            <button class="toggle-btn" :class="{ active: viewMode === 'month' }" @click="switchView('month')">月</button>
          </div>
          <button class="add-time-btn" @click="openAddDialog()">添加已花费时间</button>
        </div>
      </div>

      <!-- Week View -->
      <WeekGrid
        v-if="viewMode === 'week'"
        :week-days="weekDays"
        :entries="timeEntries"
        :show-quota="true"
        :quota-minutes="minutesPerDay()"
        :quota-text="quotaText()"
        @day-click="openAddDialog"
        @entry-click="openEditDialog"
        @issue-click="(entry) => { if (!entry.issueDeleted) $router.push(`/issues/${entry.issueKey || entry.issueId}`) }"
      />

      <!-- Month View -->
      <MonthGrid
        v-else
        :month-days="monthDays"
        :entries="timeEntries"
        @day-click="openAddDialog"
        @entry-click="openEditDialog"
      />
    </template>

    <!-- ===================== 项目视图 ===================== -->
    <template v-else-if="activeTab === 'projects'">
      <!-- Project selector & filters -->
      <div class="timesheet-controls">
        <div class="controls-left">
          <div class="project-selector">
            <a-select
              v-model="selectedProjectId"
              placeholder="选择项目查看明细"
              allow-clear
              allow-search
              style="width: 260px"
              @change="onProjectChange"
            >
              <a-option v-for="p in projectSummaries" :key="p.projectId" :value="p.projectId">
                {{ p.projectKey }} - {{ p.projectName }}
              </a-option>
            </a-select>
          </div>
          <div class="filters">
            <span class="filter-label">汇总范围:</span>
            <span class="filter-value">{{ selectedProjectId ? '项目明细' : '所有可见项目' }}</span>
          </div>
        </div>
      </div>

      <!-- Date range & navigation -->
      <div class="timesheet-datebar">
        <div class="date-info">
          <span class="date-range">{{ dateRangeLabel }}</span>
          <span class="total-time">总已用时间: {{ formatDuration(projectViewTotal) }}</span>
        </div>
        <div class="date-nav">
          <button class="nav-btn" @click="navigate(-1)">←</button>
          <button class="nav-btn today-btn" @click="goToday">今天</button>
          <button class="nav-btn" @click="navigate(1)">→</button>
          <div class="view-toggle">
            <button class="toggle-btn" :class="{ active: viewMode === 'week' }" @click="switchView('week')">周</button>
            <button class="toggle-btn" :class="{ active: viewMode === 'month' }" @click="switchView('month')">月</button>
          </div>
        </div>
      </div>

      <!-- Project Overview (no project selected) -->
      <div v-if="!selectedProjectId" class="project-overview">
        <div v-if="projectSummaries.length === 0 && !loading" class="empty-state">
          <div class="empty-icon">📊</div>
          <div class="empty-title">暂无项目工时数据</div>
          <div class="empty-desc">当前日期范围内您可见的项目没有工时记录</div>
        </div>
        <div v-else class="project-summary-list">
          <div
            v-for="p in projectSummaries"
            :key="p.projectId"
            class="project-summary-card"
            @click="onProjectChange(p.projectId)"
          >
            <div class="project-summary-left">
              <span class="project-key-badge">{{ p.projectKey }}</span>
              <span class="project-name-text">{{ p.projectName }}</span>
            </div>
            <div class="project-summary-right">
              <span class="project-total-dur">{{ formatDuration(p.totalDuration) }}</span>
              <span class="project-entry-count">{{ p.entries.length }} 条记录</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Project Detail (project selected) -->
      <div v-else class="project-detail-view">
        <WeekGrid
          v-if="viewMode === 'week'"
          :week-days="weekDays"
          :entries="projectEntries"
          :show-user="true"
        />
        <MonthGrid
          v-else
          :month-days="monthDays"
          :entries="projectEntries"
        />
      </div>
    </template>

    <!-- ===================== 工作群组视图 ===================== -->
    <template v-else-if="activeTab === 'workgroups'">
      <!-- Date range & navigation -->
      <div class="timesheet-datebar">
        <div class="date-info">
          <span class="date-range">{{ dateRangeLabel }}</span>
          <span class="total-time">所有工作组总工时: {{ formatDuration(groupViewTotal) }}</span>
        </div>
        <div class="date-nav">
          <button class="nav-btn" @click="navigate(-1)">←</button>
          <button class="nav-btn today-btn" @click="goToday">今天</button>
          <button class="nav-btn" @click="navigate(1)">→</button>
          <div class="view-toggle">
            <button class="toggle-btn" :class="{ active: viewMode === 'week' }" @click="switchView('week')">周</button>
            <button class="toggle-btn" :class="{ active: viewMode === 'month' }" @click="switchView('month')">月</button>
          </div>
        </div>
      </div>

      <!-- 无数据空状态 -->
      <div v-if="groupSummaries.length === 0 && !loading" class="workgroup-empty">
        <div class="empty-state">
          <div class="empty-icon">👥</div>
          <div class="empty-title">暂无工作组数据</div>
          <div class="empty-desc">当前系统中没有工作组，或工作组内尚无成员。<br>管理员可在「系统管理 → 用户组」中创建工作组并分配成员。</div>
        </div>
      </div>

      <!-- 工作组列表（展开/折叠） -->
      <div v-else class="group-overview">
        <div v-for="group in groupSummaries" :key="group.groupId" class="group-block">
          <!-- 工作组标题行（可点击展开） -->
          <div
            class="group-header"
            :class="{ expanded: expandedGroups.has(group.groupId) }"
            @click="toggleGroupExpand(group.groupId)"
          >
            <div class="group-header-left">
              <span class="group-expand-icon">{{ expandedGroups.has(group.groupId) ? '▼' : '▶' }}</span>
              <span class="group-icon">👥</span>
              <span class="group-name">{{ group.groupName }}</span>
              <span class="group-member-count">{{ group.memberCount }} 人</span>
            </div>
            <div class="group-header-right">
              <span class="group-total-dur">{{ formatDuration(group.totalDuration) }}</span>
            </div>
          </div>

          <!-- 展开后的成员列表 -->
          <div v-if="expandedGroups.has(group.groupId)" class="group-members">
            <div
              v-for="member in group.members"
              :key="member.userId"
              class="member-row"
            >
              <div class="member-info">
                <span class="member-avatar">{{ (member.displayName || member.username || '?').charAt(0) }}</span>
                <span class="member-name">{{ member.displayName || member.username }}</span>
              </div>
              <div class="member-bar-area">
                <div class="member-entries" v-if="member.entries && member.entries.length > 0">
                  <span
                    v-for="entry in member.entries"
                    :key="entry.id"
                    class="member-entry-chip"
                    :title="`${entry.issueKey || entry.issueId} ${entry.workDate} ${formatDuration(entry.duration || 0)}${entry.description ? ' - ' + entry.description : ''}`"
                    @click="openEditDialog(entry)"
                  >
                    {{ entry.issueKey || '?' }} {{ formatDuration(entry.duration || 0) }}
                  </span>
                </div>
                <span v-else class="member-no-entries">无工时记录</span>
              </div>
              <div class="member-total">
                <span :class="member.totalDuration > 0 ? 'member-total-dur' : 'member-total-zero'">
                  {{ formatDuration(member.totalDuration) }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- Loading overlay -->
    <div v-if="loading" class="loading-overlay">
      <a-spin :size="24" />
    </div>

    <!-- Add/Edit Dialog -->
    <a-modal
      v-model:visible="showDialog"
      :title="editingEntry ? '编辑工时' : '添加花费的时间'"
      :width="560"
      :footer="false"
      @cancel="closeDialog"
    >
      <div class="time-dialog">
        <!-- 问题 -->
        <div class="dialog-field">
          <label class="dialog-label">问题</label>
          <a-select
            v-model="form.issueId"
            placeholder="Select an option"
            allow-search
            :options="issueOptions"
            @search="searchIssues"
          />
        </div>

        <!-- 作者 -->
        <div class="dialog-field">
          <label class="dialog-label">作者</label>
          <template v-if="canLogForOthers && !editingEntry">
            <a-select
              v-model="form.forUserId"
              placeholder="选择用户（默认为自己）"
              allow-search
              allow-clear
              :filter-option="false"
              @search="searchUsersForDialog"
              @clear="form.forUserId = undefined"
            >
              <a-option v-for="u in dialogSelectableUsers" :key="u.id" :value="u.id" :label="u.displayName || u.username">
                <div class="user-option">
                  <span class="user-option-avatar">{{ (u.displayName || u.username).charAt(0) }}</span>
                  <span class="user-option-name">{{ u.displayName || u.username }}</span>
                  <span v-if="u.id === authStore.user?.id" class="user-option-self">(我)</span>
                </div>
              </a-option>
            </a-select>
          </template>
          <template v-else>
            <div class="author-display">
              <span class="author-avatar">{{ currentUserName.charAt(0) }}</span>
              <span class="author-name">{{ currentUserName }}</span>
            </div>
          </template>
        </div>

        <!-- 单一日期 / 日期范围 切换 -->
        <div class="dialog-field">
          <div class="date-mode-toggle">
            <button class="date-mode-btn" :class="{ active: dateMode === 'single' }" @click="dateMode = 'single'">单一日期</button>
            <button class="date-mode-btn" :class="{ active: dateMode === 'range' }" @click="dateMode = 'range'">日期范围</button>
          </div>
        </div>

        <!-- 日期 + 实际用时 -->
        <div class="dialog-row">
          <div class="dialog-field flex-1">
            <label class="dialog-label">日期</label>
            <a-date-picker v-if="dateMode === 'single'" v-model="form.workDate" style="width: 100%" />
            <a-range-picker v-else v-model="form.dateRange" style="width: 100%" />
          </div>
          <div class="dialog-field flex-1">
            <label class="dialog-label">实际用时</label>
            <a-input v-model="form.durationText" placeholder="1周 1天 1时 1分">
              <template #prefix>⏱</template>
            </a-input>
          </div>
        </div>

        <!-- 添加另一个记录 -->
        <div class="add-another" v-if="!editingEntry">
          <a class="add-another-link" @click="addAnotherRecord">+ 添加另一个记录</a>
        </div>

        <!-- 额外记录列表 -->
        <div v-if="extraRecords.length > 0" class="extra-records">
          <div v-for="(rec, idx) in extraRecords" :key="idx" class="extra-record-row">
            <a-date-picker v-model="rec.workDate" style="width: 45%" size="small" />
            <a-input v-model="rec.durationText" placeholder="时长" style="width: 40%" size="small" />
            <button class="remove-record-btn" @click="extraRecords.splice(idx, 1)">✕</button>
          </div>
        </div>

        <!-- 工作项属性（动态加载） -->
        <div v-for="attr in projectAttributes" :key="attr.id" class="dialog-field">
          <label class="dialog-label">{{ attr.name }}</label>
          <a-select v-model="formAttributeValues[attr.id]" :placeholder="`选择${attr.name}`" allow-clear>
            <a-option v-for="val in attr.values" :key="val.id" :value="val.id">
              <span v-if="val.color" class="attr-value-dot" :style="{ background: val.color }"></span>
              {{ val.name }}
            </a-option>
          </a-select>
        </div>

        <!-- 描述 -->
        <div class="dialog-field">
          <label class="dialog-label">描述</label>
          <a-textarea
            v-model="form.description"
            placeholder="描述这段时间您做了什么"
            :auto-size="{ minRows: 3, maxRows: 6 }"
          />
        </div>

        <!-- 底部按钮 -->
        <div class="dialog-actions">
          <div class="actions-left">
            <a-button v-if="editingEntry" status="danger" @click="deleteEntry" :loading="deleting">删除</a-button>
          </div>
          <div class="actions-right">
            <a-button @click="closeDialog">取消</a-button>
            <a-button type="primary" @click="saveEntry" :loading="saving">
              {{ editingEntry ? '保存' : '保存' }}
            </a-button>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { useAuthStore } from '@/stores/auth'
import { useRoute, useRouter } from 'vue-router'
import { timeEntryApi, issueApi, projectApi } from '@/api'
import type { TimeEntryVO, ProjectTimeSummaryVO, TimeEntryUserVO, WorkItemAttributeVO, GroupTimeSummaryVO } from '@/api/timeEntry'
import { workItemAttributeApi } from '@/api/timeEntry'
import { useTimeTrackingSettings } from '@/composables/useTimeTrackingSettings'
import WeekGrid from './WeekGrid.vue'
import MonthGrid from './MonthGrid.vue'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const { settings: ttSettings, loadSettings: loadTTSettings, minutesPerDay, minutesPerWeek, isWorkingDay, quotaText } = useTimeTrackingSettings()

// State
const activeTab = ref<'people' | 'projects' | 'workgroups'>((route.query.view as any) || 'people')
const viewMode = ref<'week' | 'month'>('week')
const currentWeekStart = ref(getMonday(new Date()))
const currentMonthDate = ref(new Date().toISOString().slice(0, 7)) // YYYY-MM
const loading = ref(false)
const showDialog = ref(false)
const editingEntry = ref<TimeEntryVO | null>(null)
const saving = ref(false)
const deleting = ref(false)
const timeEntries = ref<TimeEntryVO[]>([])
const issueOptions = ref<{ value: string; label: string }[]>([])

// User selector state
const canViewOthers = ref(false)
const canEditOthers = ref(false)
const canLogForOthers = ref(false)
const selectableUsers = ref<TimeEntryUserVO[]>([])
const selectedUserId = ref<string | undefined>(undefined)
// Dialog user selector state (for log-for-others)
const dialogSelectableUsers = ref<TimeEntryUserVO[]>([])

// Project view state
const projectSummaries = ref<ProjectTimeSummaryVO[]>([])
const selectedProjectId = ref<string | undefined>(undefined)
const projectEntries = ref<TimeEntryVO[]>([])

// Work Groups view state
const groupSummaries = ref<GroupTimeSummaryVO[]>([])
const expandedGroups = ref<Set<string>>(new Set())

// Filter state (people view)
const filterProjectId = ref<string | undefined>(undefined)
const filterWorkType = ref<string | undefined>(undefined)
const filterProjects = ref<{ id: string; name: string; key: string }[]>([])
const filterWorkTypes = ref<{ id: string; name: string; color?: string }[]>([])
// Work item attributes (dynamically loaded per project)
const projectAttributes = ref<WorkItemAttributeVO[]>([])
const formAttributeValues = ref<Record<string, string>>({})
const loadedAttributeProjectId = ref<string | null>(null)
// Form
const dateMode = ref<'single' | 'range'>('single')
const extraRecords = ref<{ workDate: string; durationText: string }[]>([])
const form = ref({
  issueId: undefined as string | undefined,
  workDate: '',
  dateRange: undefined as [string, string] | undefined,
  durationText: '',
  startTimeStr: undefined as string | undefined,
  description: '',
  forUserId: undefined as string | undefined
})

// Computed
const currentUserName = computed(() => authStore.user?.displayName || authStore.user?.username || 'Test User')

const selectedUserDisplayName = computed(() => {
  if (!selectedUserId.value) return currentUserName.value
  const user = selectableUsers.value.find(u => u.id === selectedUserId.value)
  return user?.displayName || currentUserName.value
})

const weekDays = computed(() => {
  const days = []
  const start = new Date(currentWeekStart.value)
  const dayNames = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  for (let i = 0; i < 7; i++) {
    const d = new Date(start)
    d.setDate(start.getDate() + i)
    // Convert JS getDay() (0=Sun, 6=Sat) to ISO day-of-week (1=Mon, 7=Sun)
    const jsDow = d.getDay()
    const isoDow = jsDow === 0 ? 7 : jsDow
    days.push({
      date: formatDateKey(d),
      dateNum: d.getDate(),
      dayName: dayNames[d.getDay()],
      isWeekend: !isWorkingDay(isoDow)
    })
  }
  return days
})

const monthDays = computed(() => {
  const [year, month] = currentMonthDate.value.split('-').map(Number)
  const firstDay = new Date(year, month - 1, 1)

  // Start from Monday of the week containing the first day
  const startDate = new Date(firstDay)
  const dayOfWeek = startDate.getDay()
  const offset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek
  startDate.setDate(startDate.getDate() + offset)

  const days = []
  const current = new Date(startDate)
  // Generate 6 weeks (42 days) to cover all months
  for (let i = 0; i < 42; i++) {
    const jsDow = current.getDay()
    const isoDow = jsDow === 0 ? 7 : jsDow
    days.push({
      date: formatDateKey(current),
      dateNum: current.getDate(),
      isWeekend: !isWorkingDay(isoDow),
      currentMonth: current.getMonth() === month - 1
    })
    current.setDate(current.getDate() + 1)
  }
  return days
})

const dateRangeLabel = computed(() => {
  if (viewMode.value === 'week') {
    const start = new Date(currentWeekStart.value)
    const end = new Date(start)
    end.setDate(start.getDate() + 6)
    return `${formatDateDisplay(start)} – ${formatDateDisplay(end)}`
  } else {
    const [year, month] = currentMonthDate.value.split('-').map(Number)
    return `${year}年${month}月`
  }
})

const weekTotal = computed(() => {
  return timeEntries.value.reduce((sum, e) => sum + (e.duration || 0), 0)
})

const projectViewTotal = computed(() => {
  if (selectedProjectId.value) {
    return projectEntries.value.reduce((sum, e) => sum + (e.duration || 0), 0)
  }
  return projectSummaries.value.reduce((sum, p) => sum + p.totalDuration, 0)
})

const groupViewTotal = computed(() => {
  return groupSummaries.value.reduce((sum, g) => sum + g.totalDuration, 0)
})

// Tab switching
function switchTab(tab: 'people' | 'projects' | 'workgroups') {
  activeTab.value = tab
  // Persist to URL query for refresh preservation
  router.replace({ query: { ...route.query, view: tab } })
  if (tab === 'people') {
    loadEntries()
  } else if (tab === 'projects') {
    loadProjectSummaries()
  } else if (tab === 'workgroups') {
    loadGroupSummaries()
  }
}

// Data loading - People view
async function loadEntries() {
  loading.value = true
  try {
    const { startDate, endDate } = getDateRange()
    const params: { userId?: string; startDate: string; endDate: string; projectId?: string; activityId?: string } = { startDate, endDate }
    if (selectedUserId.value) {
      params.userId = selectedUserId.value
    }
    if (filterProjectId.value) {
      params.projectId = filterProjectId.value
    }
    if (filterWorkType.value) {
      // filterWorkType now holds the attribute value ID (not name)
      params.activityId = filterWorkType.value
    }
    const res = await timeEntryApi.list(params)
    if (res.code === 0 && res.data) {
      timeEntries.value = res.data
    }
  } catch {
    Message.error({ content: '加载工时数据失败', duration: 3000 })
  } finally {
    loading.value = false
  }
}

// Data loading - Work Groups view
async function loadGroupSummaries() {
  loading.value = true
  try {
    const { startDate, endDate } = getDateRange()
    const res = await timeEntryApi.listByGroup({ startDate, endDate })
    if (res.code === 0 && res.data) {
      groupSummaries.value = res.data
      // 自动展开第一个有工时的组
      if (expandedGroups.value.size === 0 && res.data.length > 0) {
        const firstWithTime = res.data.find(g => g.totalDuration > 0)
        if (firstWithTime) {
          expandedGroups.value.add(firstWithTime.groupId)
        } else if (res.data.length > 0) {
          expandedGroups.value.add(res.data[0].groupId)
        }
      }
    }
  } catch {
    Message.error({ content: '加载工作组工时数据失败', duration: 3000 })
  } finally {
    loading.value = false
  }
}

function toggleGroupExpand(groupId: string) {
  if (expandedGroups.value.has(groupId)) {
    expandedGroups.value.delete(groupId)
  } else {
    expandedGroups.value.add(groupId)
  }
}

// Data loading - Project view
async function loadProjectSummaries() {
  loading.value = true
  try {
    const { startDate, endDate } = getDateRange()
    const res = await timeEntryApi.listByProject({ startDate, endDate })
    if (res.code === 0 && res.data) {
      projectSummaries.value = res.data
    }
  } catch {
    Message.error({ content: '加载项目工时数据失败', duration: 3000 })
  } finally {
    loading.value = false
  }
}

async function loadProjectDetail(projectId: string) {
  loading.value = true
  try {
    const { startDate, endDate } = getDateRange()
    const res = await timeEntryApi.listByProjectDetail(projectId, { startDate, endDate })
    if (res.code === 0 && res.data) {
      projectEntries.value = res.data
    }
  } catch {
    Message.error({ content: '加载项目工时明细失败', duration: 3000 })
  } finally {
    loading.value = false
  }
}

function onProjectChange(val: string | number | boolean | Record<string, any> | (string | number | boolean | Record<string, any>)[] | undefined) {
  selectedProjectId.value = (val as string) || undefined
  if (val) {
    loadProjectDetail(val as string)
  } else {
    projectEntries.value = []
    loadProjectSummaries()
  }
}

// User selector functions
async function loadCanViewOthers() {
  try {
    const res = await timeEntryApi.canViewOthers()
    if (res.code === 0) {
      canViewOthers.value = res.data === true
    }
  } catch (e) {
    console.error('[Timesheet] 加载 canViewOthers 权限失败:', e)
  }
}

async function loadCanEditOthers() {
  try {
    const res = await timeEntryApi.canEditOthers()
    if (res.code === 0) {
      canEditOthers.value = res.data === true
    }
  } catch (e) {
    console.error('[Timesheet] 加载 canEditOthers 权限失败:', e)
  }
}

async function loadCanLogForOthers() {
  try {
    const res = await timeEntryApi.canLogForOthers()
    if (res.code === 0) {
      canLogForOthers.value = res.data === true
    }
  } catch (e) {
    console.error('[Timesheet] 加载 canLogForOthers 权限失败:', e)
  }
}

async function loadSelectableUsers(keyword?: string) {
  try {
    const params = keyword ? { keyword } : undefined
    const res = await timeEntryApi.listSelectableUsers(params)
    if (res.code === 0 && res.data) {
      selectableUsers.value = res.data
    }
  } catch (e) {
    console.error('[Timesheet] 加载可选用户列表失败:', e)
  }
}

function searchUsers(keyword: string) {
  loadSelectableUsers(keyword)
}

async function searchUsersForDialog(keyword: string) {
  try {
    const params = keyword ? { keyword } : undefined
    const res = await timeEntryApi.listSelectableUsers(params)
    if (res.code === 0 && res.data) {
      dialogSelectableUsers.value = res.data
    }
  } catch (e) {
    console.error('[Timesheet] 搜索用户失败:', e)
  }
}

function onUserChange(val: string | number | boolean | Record<string, any> | (string | number | boolean | Record<string, any>)[] | undefined) {
  selectedUserId.value = (val as string) || undefined
  loadEntries()
}

// Filter functions
function onFilterChange() {
  loadEntries()
  // Persist filters to URL
  const query: Record<string, string> = { ...route.query as Record<string, string> }
  if (filterProjectId.value) {
    query.projectId = filterProjectId.value
  } else {
    delete query.projectId
  }
  if (filterWorkType.value) {
    query.activityId = filterWorkType.value
  } else {
    delete query.activityId
  }
  // Clean up legacy param
  delete query.workType
  router.replace({ query })
}

function resetFilters() {
  filterProjectId.value = undefined
  filterWorkType.value = undefined
  onFilterChange()
}

async function loadFilterProjects() {
  try {
    const res = await projectApi.list({ pageSize: 100 })
    if (res.code === 0 && res.data) {
      filterProjects.value = res.data.list.map(p => ({
        id: p.id,
        name: p.name,
        key: p.key
      }))
    }
  } catch (e) {
    console.error('[Timesheet] 加载筛选项目列表失败:', e)
  }
}

async function loadFilterWorkTypes() {
  try {
    const res = await workItemAttributeApi.list()
    if (res.code === 0 && res.data) {
      // Find the "Work type" attribute and use its values
      const workTypeAttr = res.data.find(a => a.name === 'Work type' || a.name === '工作类型')
      if (workTypeAttr && workTypeAttr.values) {
        filterWorkTypes.value = workTypeAttr.values.map(v => ({
          id: v.id,
          name: v.name,
          color: v.color
        }))
      }
    }
  } catch (e) {
    console.error('[Timesheet] 加载工作类型失败:', e)
  }
}

function getDateRange(): { startDate: string; endDate: string } {
  if (viewMode.value === 'week') {
    const startDate = currentWeekStart.value
    const end = new Date(currentWeekStart.value)
    end.setDate(end.getDate() + 6)
    return { startDate, endDate: formatDateKey(end) }
  } else {
    const [year, month] = currentMonthDate.value.split('-').map(Number)
    const startDate = `${year}-${String(month).padStart(2, '0')}-01`
    const lastDay = new Date(year, month, 0)
    return { startDate, endDate: formatDateKey(lastDay) }
  }
}

async function searchIssues(keyword: string) {
  try {
    const params: Record<string, any> = { pageSize: 15 }
    if (keyword && keyword.length >= 1) params.keyword = keyword
    const res = await issueApi.list(params)
    if (res.code === 0 && res.data) {
      issueOptions.value = res.data.list.map(i => ({
        value: i.id,
        label: `${i.issueKey} - ${i.title}`
      }))
    }
  } catch (e) {
    console.error('[Timesheet] 搜索工单失败:', e)
  }
}

/**
 * 根据 issue 获取其所属项目的工作项属性
 */
async function loadAttributesForIssue(issueId: string) {
  try {
    // 获取 issue 详情以确定 projectId
    const issueRes = await issueApi.getDetail(issueId)
    if (issueRes.code === 0 && issueRes.data?.projectId) {
      const projectId = issueRes.data.projectId
      if (loadedAttributeProjectId.value === projectId) return // 已加载
      loadedAttributeProjectId.value = projectId
      const attrRes = await workItemAttributeApi.listByProject(projectId)
      if (attrRes.code === 0 && attrRes.data) {
        projectAttributes.value = attrRes.data
      }
    }
  } catch (e) {
    console.error('[Timesheet] 加载工单项目属性失败:', e)
  }
}

// People view helpers — kept for future use if needed
// (Grid rendering delegated to WeekGrid / MonthGrid sub-components)

// Project view helpers — delegated to sub-components

function navigate(delta: number) {
  if (viewMode.value === 'week') {
    const d = new Date(currentWeekStart.value)
    d.setDate(d.getDate() + delta * 7)
    currentWeekStart.value = formatDateKey(d)
  } else {
    const [year, month] = currentMonthDate.value.split('-').map(Number)
    const d = new Date(year, month - 1 + delta, 1)
    currentMonthDate.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  }
}

function goToday() {
  currentWeekStart.value = getMonday(new Date())
  const now = new Date()
  currentMonthDate.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function switchView(mode: 'week' | 'month') {
  viewMode.value = mode
  reloadCurrentTab()
}

function reloadCurrentTab() {
  if (activeTab.value === 'people') {
    loadEntries()
  } else if (activeTab.value === 'projects') {
    if (selectedProjectId.value) {
      loadProjectDetail(selectedProjectId.value)
    } else {
      loadProjectSummaries()
    }
  } else if (activeTab.value === 'workgroups') {
    loadGroupSummaries()
  }
}

function openAddDialog(date?: string) {
  editingEntry.value = null
  dateMode.value = 'single'
  extraRecords.value = []
  formAttributeValues.value = {}
  form.value = {
    issueId: undefined,
    workDate: date || formatDateKey(new Date()),
    dateRange: undefined,
    durationText: '',
    startTimeStr: undefined,
    description: '',
    forUserId: undefined
  }
  showDialog.value = true
  searchIssues('')
  // Pre-load dialog selectable users if user can log for others
  if (canLogForOthers.value && dialogSelectableUsers.value.length === 0) {
    searchUsersForDialog('')
  }
}

function addAnotherRecord() {
  extraRecords.value.push({ workDate: form.value.workDate, durationText: '' })
}

function openEditDialog(entry: TimeEntryVO) {
  // Check if this is someone else's entry and whether user has edit permission
  const isOwnEntry = entry.userId === authStore.user?.id
  if (!isOwnEntry && !canEditOthers.value) {
    Message.warning('无权编辑他人工时记录')
    return
  }

  editingEntry.value = entry
  dateMode.value = 'single'
  extraRecords.value = []
  formAttributeValues.value = {}
  // Populate attribute values from entry
  if (entry.attributeValues) {
    for (const av of entry.attributeValues) {
      formAttributeValues.value[av.attributeId] = av.valueId
    }
  }
  form.value = {
    issueId: entry.issueId,
    workDate: entry.workDate,
    dateRange: undefined,
    durationText: formatDurationInput(entry.duration),
    startTimeStr: entry.startTime != null ? `${String(Math.floor(entry.startTime / 60)).padStart(2, '0')}:${String(entry.startTime % 60).padStart(2, '0')}` : undefined,
    description: entry.description || ''
  }
  // Ensure current issue is in options
  if (entry.issueKey) {
    const existing = issueOptions.value.find(o => o.value === entry.issueId)
    if (!existing) {
      const label = entry.issueDeleted
        ? `[已删除] ${entry.issueKey} - ${entry.issueTitle || ''}`
        : `${entry.issueKey} - ${entry.issueTitle || ''}`
      issueOptions.value = [{ value: entry.issueId, label }, ...issueOptions.value]
    }
  } else if (entry.issueDeleted) {
    // Issue key is null (issue fully deleted), show placeholder
    const existing = issueOptions.value.find(o => o.value === entry.issueId)
    if (!existing) {
      issueOptions.value = [{ value: entry.issueId, label: '[已删除工单]' }, ...issueOptions.value]
    }
  }
  // Load attributes for the issue's project
  loadAttributesForIssue(entry.issueId)
  showDialog.value = true
}

function closeDialog() {
  showDialog.value = false
  editingEntry.value = null
}

async function saveEntry() {
  // 根据 dateMode 分支校验日期字段
  if (dateMode.value === 'single') {
    if (!form.value.issueId || !form.value.workDate || !form.value.durationText) {
      Message.warning('请填写工单、日期和时长')
      return
    }
  } else {
    // 日期范围模式
    if (!form.value.issueId || !form.value.dateRange || !form.value.dateRange[0] || !form.value.dateRange[1] || !form.value.durationText) {
      Message.warning('请填写工单、日期范围和时长')
      return
    }
  }

  const totalDuration = parseDuration(form.value.durationText)
  if (!totalDuration || totalDuration <= 0) {
    Message.warning('时长格式无效，请使用如 2h30m, 1h, 45m')
    return
  }

  const startTime = form.value.startTimeStr ? parseTimeToMinutes(form.value.startTimeStr) : undefined

  saving.value = true
  try {
    if (dateMode.value === 'single') {
      // 单一日期模式 - 使用现有逻辑
      if (editingEntry.value) {
        await timeEntryApi.update(editingEntry.value.id, {
          issueId: form.value.issueId,
          workDate: form.value.workDate,
          duration: totalDuration,
          startTime,
          description: form.value.description || undefined,
          attributeValues: Object.keys(formAttributeValues.value).length > 0
            ? Object.fromEntries(Object.entries(formAttributeValues.value).filter(([, v]) => v))
            : undefined
        })
        Message.success('工时已更新')
      } else {
        await timeEntryApi.create({
          issueId: form.value.issueId,
          workDate: form.value.workDate,
          duration: totalDuration,
          startTime,
          description: form.value.description || undefined,
          forUserId: form.value.forUserId || undefined,
          attributeValues: Object.keys(formAttributeValues.value).length > 0
            ? Object.fromEntries(Object.entries(formAttributeValues.value).filter(([, v]) => v))
            : undefined
        })
        Message.success('工时已添加')
      }
    } else {
      // 日期范围模式 - 按工作日拆分创建独立记录
      const workingDays = getWorkingDaysInRange(form.value.dateRange![0], form.value.dateRange![1])
      if (workingDays.length === 0) {
        Message.warning('所选日期范围内没有工作日')
        return
      }
      // 将总时长按工作日数平均分配（取整到分钟）
      const durationPerDay = Math.round(totalDuration / workingDays.length)
      if (durationPerDay <= 0) {
        Message.warning('每日分配时长过小，请增加总时长或缩小日期范围')
        return
      }
      // 为每个工作日创建一条工时记录
      for (const day of workingDays) {
        await timeEntryApi.create({
          issueId: form.value.issueId,
          workDate: day,
          duration: durationPerDay,
          startTime,
          description: form.value.description || undefined,
          forUserId: form.value.forUserId || undefined,
          attributeValues: Object.keys(formAttributeValues.value).length > 0
            ? Object.fromEntries(Object.entries(formAttributeValues.value).filter(([, v]) => v))
            : undefined
        })
      }
      Message.success(`已为 ${workingDays.length} 个工作日分别创建工时记录`)
    }
    closeDialog()
    reloadCurrentTab()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

/**
 * 获取日期范围内的工作日列表（排除周六和周日）
 */
function getWorkingDaysInRange(startDateStr: string, endDateStr: string): string[] {
  const workingDays: string[] = []
  const start = new Date(startDateStr)
  const end = new Date(endDateStr)
  const current = new Date(start)

  while (current <= end) {
    const dayOfWeek = current.getDay()
    // 排除周六(6)和周日(0)
    if (dayOfWeek !== 0 && dayOfWeek !== 6) {
      workingDays.push(formatDateKey(current))
    }
    current.setDate(current.getDate() + 1)
  }

  return workingDays
}

async function deleteEntry() {
  if (!editingEntry.value) return

  Modal.warning({
    title: '确认删除',
    content: '确定要删除这条工时记录吗？此操作不可撤销。',
    okText: '删除',
    cancelText: '取消',
    hideCancel: false,
    onOk: async () => {
      deleting.value = true
      try {
        await timeEntryApi.delete(editingEntry.value!.id)
        Message.success('工时已删除')
        closeDialog()
        reloadCurrentTab()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '删除失败')
      } finally {
        deleting.value = false
      }
    }
  })
}

// Helpers
function formatDuration(minutes: number): string {
  if (!minutes || minutes === 0) return '0h'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m}m`
  if (m === 0) return `${h}h`
  return `${h}h${m}m`
}

function formatDurationInput(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m}m`
  if (m === 0) return `${h}h`
  return `${h}h${m}m`
}

function parseDuration(text: string): number | null {
  const cleaned = text.trim().toLowerCase()
  let total = 0
  const weekMatch = cleaned.match(/(\d+)\s*w/)
  const dayMatch = cleaned.match(/(\d+)\s*d/)
  const hourMatch = cleaned.match(/(\d+)\s*h/)
  const minMatch = cleaned.match(/(\d+)\s*m/)

  const mPerDay = minutesPerDay()
  const mPerWeek = minutesPerWeek()

  if (weekMatch) total += parseInt(weekMatch[1]) * mPerWeek
  if (dayMatch) total += parseInt(dayMatch[1]) * mPerDay
  if (hourMatch) total += parseInt(hourMatch[1]) * 60
  if (minMatch) total += parseInt(minMatch[1])

  // If just a number, treat as hours
  if (!weekMatch && !dayMatch && !hourMatch && !minMatch) {
    const num = parseFloat(cleaned)
    if (!isNaN(num)) total = Math.round(num * 60)
  }

  return total > 0 ? total : null
}

function parseTimeToMinutes(timeStr: string): number | undefined {
  if (!timeStr) return undefined
  const parts = timeStr.split(':')
  if (parts.length !== 2) return undefined
  return parseInt(parts[0]) * 60 + parseInt(parts[1])
}

function formatDateKey(d: Date): string {
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatDateDisplay(d: Date): string {
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}`
}

function getMonday(d: Date): string {
  const date = new Date(d)
  const day = date.getDay()
  const diff = date.getDate() - day + (day === 0 ? -6 : 1)
  date.setDate(diff)
  return formatDateKey(date)
}

// Watchers - reload data on date navigation
watch(currentWeekStart, () => { if (viewMode.value === 'week') reloadCurrentTab() })
watch(currentMonthDate, () => { if (viewMode.value === 'month') reloadCurrentTab() })

// Watch issue selection to load project-specific attributes
watch(() => form.value.issueId, (newId) => {
  if (newId) {
    loadAttributesForIssue(newId)
  } else {
    projectAttributes.value = []
    formAttributeValues.value = {}
    loadedAttributeProjectId.value = null
  }
})

// Init
onMounted(async () => {
  // Load time tracking settings first (needed for parseDuration + quota)
  await loadTTSettings()
  // Check if user can view/edit others' time entries
  await Promise.all([loadCanViewOthers(), loadCanEditOthers(), loadCanLogForOthers()])
  if (canViewOthers.value) {
    await loadSelectableUsers()
  }
  // Load filter options
  loadFilterProjects()
  loadFilterWorkTypes()
  // Restore filters from URL query
  if (route.query.projectId) {
    filterProjectId.value = route.query.projectId as string
  }
  if (route.query.activityId) {
    filterWorkType.value = route.query.activityId as string
  } else if (route.query.workType) {
    // Legacy compat: old URL had workType name, try to find matching ID
    const legacyName = route.query.workType as string
    const match = filterWorkTypes.value.find(wt => wt.name === legacyName)
    if (match) filterWorkType.value = match.id
  }
  // Load data for the currently active tab (may be restored from URL)
  if (activeTab.value === 'projects') {
    loadProjectSummaries()
  } else if (activeTab.value === 'people') {
    loadEntries()
  } else if (activeTab.value === 'workgroups') {
    loadGroupSummaries()
  }
  // Preload some issues for the add dialog
  searchIssues('')
})
</script>

<style scoped>
.timesheet-page { height: 100%; display: flex; flex-direction: column; overflow: hidden; position: relative; }
.timesheet-header { padding: 16px 24px 0; }
.page-title { font-size: 18px; font-weight: 600; color: var(--tf-text-primary); margin: 0; }

/* Attribute value dot */
.attr-value-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; vertical-align: middle; }

/* Tabs */
.timesheet-tabs { display: flex; gap: 0; padding: 12px 24px 0; border-bottom: 1px solid var(--tf-border-light); }
.tab-btn { padding: 8px 16px; font-size: 13px; color: var(--tf-text-secondary); background: none; border: none; border-bottom: 2px solid transparent; cursor: pointer; transition: color 0.15s, border-color 0.15s; margin-bottom: -1px; }
.tab-btn:hover { color: var(--tf-text-primary); }
.tab-btn.active { color: var(--tf-accent); border-bottom-color: var(--tf-accent); font-weight: 500; }

/* Controls */
.timesheet-controls { padding: 12px 24px; display: flex; align-items: center; justify-content: space-between; }
.controls-left { display: flex; flex-direction: column; gap: 6px; }
.user-selector-area { display: flex; align-items: center; }
.user-selector-static { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 500; color: var(--tf-text-primary); }
.user-avatar-dot { width: 12px; height: 12px; border-radius: 50%; background: var(--tf-accent); }
.user-option { display: flex; align-items: center; gap: 8px; }
.user-option-avatar { width: 20px; height: 20px; border-radius: 50%; background: linear-gradient(135deg, var(--tf-accent), #8b5cf6); display: flex; align-items: center; justify-content: center; font-size: 10px; color: #fff; font-weight: 600; flex-shrink: 0; }
.user-option-name { font-size: 13px; color: var(--tf-text-primary); }
.user-option-self { font-size: 11px; color: var(--tf-text-tertiary); }
.project-selector { display: flex; align-items: center; gap: 8px; }
.filters { display: flex; align-items: center; gap: 8px; font-size: 12px; margin-top: 4px; }
.filter-label { color: var(--tf-text-tertiary); white-space: nowrap; }
.filter-value { color: var(--tf-text-secondary); }
.filter-reset { font-size: 12px; color: var(--tf-accent); cursor: pointer; white-space: nowrap; margin-left: 4px; text-decoration: none; }
.filter-reset:hover { text-decoration: underline; }

/* Date bar */
.timesheet-datebar { padding: 8px 24px 12px; display: flex; align-items: center; justify-content: space-between; }
.date-info { display: flex; flex-direction: column; gap: 2px; }
.date-range { font-size: 15px; font-weight: 500; color: var(--tf-text-primary); }
.total-time { font-size: 12px; color: var(--tf-text-tertiary); }
.date-nav { display: flex; align-items: center; gap: 8px; }
.nav-btn { height: 28px; padding: 0 10px; border: 1px solid var(--tf-border); border-radius: var(--tf-radius-md); background: transparent; color: var(--tf-text-secondary); font-size: 12px; cursor: pointer; transition: background 0.15s, color 0.15s; }
.nav-btn:hover { background: var(--tf-bg-hover); color: var(--tf-text-primary); }
.today-btn { font-weight: 500; }
.view-toggle { display: flex; border: 1px solid var(--tf-border); border-radius: var(--tf-radius-md); overflow: hidden; }
.toggle-btn { height: 28px; padding: 0 12px; border: none; background: transparent; color: var(--tf-text-secondary); font-size: 12px; cursor: pointer; transition: background 0.15s, color 0.15s; }
.toggle-btn + .toggle-btn { border-left: 1px solid var(--tf-border); }
.toggle-btn.active { background: var(--tf-accent-bg); color: var(--tf-accent); font-weight: 500; }
.add-time-btn { height: 32px; padding: 0 14px; border: none; border-radius: var(--tf-radius-md); background: var(--tf-accent); color: #fff; font-size: 12px; font-weight: 500; cursor: pointer; transition: opacity 0.15s; }
.add-time-btn:hover { opacity: 0.9; }

/* Week Grid - styles moved to WeekGrid.vue */

/* Month Grid - styles moved to MonthGrid.vue */

/* Project Overview */
.project-overview { flex: 1; overflow-y: auto; padding: 0 24px 24px; }
.project-summary-list { display: flex; flex-direction: column; gap: 8px; }
.project-summary-card { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; border-radius: var(--tf-radius-md); background: var(--tf-bg-elevated); border: 1px solid var(--tf-border-light); cursor: pointer; transition: border-color 0.15s, background 0.1s; }
.project-summary-card:hover { border-color: var(--tf-accent); background: var(--tf-bg-hover); }
.project-summary-left { display: flex; align-items: center; gap: 10px; }
.project-key-badge { font-size: 11px; font-weight: 600; color: var(--tf-accent); background: var(--tf-accent-bg); padding: 2px 8px; border-radius: var(--tf-radius-sm); }
.project-name-text { font-size: 14px; font-weight: 500; color: var(--tf-text-primary); }
.project-summary-right { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
.project-total-dur { font-size: 14px; font-weight: 600; color: var(--tf-text-primary); }
.project-entry-count { font-size: 11px; color: var(--tf-text-tertiary); }

/* Project detail */
.project-detail-view { flex: 1; display: flex; flex-direction: column; overflow: hidden; }

/* Workgroup empty state */
.workgroup-empty { flex: 1; display: flex; align-items: center; justify-content: center; padding: 48px 24px; }

/* Work Groups view */
.group-overview { flex: 1; overflow-y: auto; padding: 0 24px 24px; display: flex; flex-direction: column; gap: 8px; }
.group-block { border: 1px solid var(--tf-border-light); border-radius: var(--tf-radius-md); overflow: hidden; }
.group-header { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; background: var(--tf-bg-elevated); cursor: pointer; transition: background 0.15s; user-select: none; }
.group-header:hover { background: var(--tf-bg-hover); }
.group-header.expanded { border-bottom: 1px solid var(--tf-border-light); }
.group-header-left { display: flex; align-items: center; gap: 8px; }
.group-expand-icon { font-size: 10px; color: var(--tf-text-tertiary); width: 12px; }
.group-icon { font-size: 14px; }
.group-name { font-size: 14px; font-weight: 600; color: var(--tf-text-primary); }
.group-member-count { font-size: 11px; color: var(--tf-text-tertiary); background: var(--tf-bg-surface); padding: 1px 6px; border-radius: 10px; }
.group-header-right { display: flex; align-items: center; }
.group-total-dur { font-size: 14px; font-weight: 600; color: var(--tf-accent); }
.group-members { display: flex; flex-direction: column; }
.member-row { display: flex; align-items: center; gap: 12px; padding: 8px 16px; border-bottom: 1px solid var(--tf-border-light); }
.member-row:last-child { border-bottom: none; }
.member-info { display: flex; align-items: center; gap: 8px; min-width: 140px; max-width: 140px; }
.member-avatar { width: 24px; height: 24px; border-radius: 50%; background: linear-gradient(135deg, var(--tf-accent), #8b5cf6); display: flex; align-items: center; justify-content: center; font-size: 11px; color: #fff; font-weight: 600; flex-shrink: 0; }
.member-name { font-size: 13px; color: var(--tf-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.member-bar-area { flex: 1; display: flex; flex-wrap: wrap; gap: 4px; min-height: 24px; align-items: center; }
.member-entries { display: flex; flex-wrap: wrap; gap: 4px; }
.member-entry-chip { font-size: 11px; padding: 2px 8px; border-radius: 10px; background: var(--tf-accent-bg); color: var(--tf-accent); cursor: pointer; transition: opacity 0.15s; white-space: nowrap; }
.member-entry-chip:hover { opacity: 0.8; }
.member-no-entries { font-size: 12px; color: var(--tf-text-tertiary); font-style: italic; }
.member-total { min-width: 48px; text-align: right; }
.member-total-dur { font-size: 13px; font-weight: 600; color: var(--tf-text-primary); }
.member-total-zero { font-size: 13px; color: var(--tf-text-tertiary); }

/* Empty state */
.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 48px 24px; text-align: center; }
.empty-icon { font-size: 48px; margin-bottom: 16px; opacity: 0.7; }
.empty-title { font-size: 16px; font-weight: 600; color: var(--tf-text-primary); margin-bottom: 8px; }
.empty-desc { font-size: 13px; color: var(--tf-text-tertiary); max-width: 360px; line-height: 1.5; }
.empty-hint { display: flex; align-items: center; gap: 6px; margin-top: 20px; padding: 10px 16px; background: var(--tf-bg-surface); border-radius: var(--tf-radius-md); border: 1px solid var(--tf-border-light); }
.hint-icon { font-size: 14px; }
.empty-hint span:last-child { font-size: 12px; color: var(--tf-text-secondary); }

/* Loading */
.loading-overlay { position: absolute; top: 0; left: 0; right: 0; bottom: 0; display: flex; align-items: center; justify-content: center; background: rgba(0,0,0,0.05); z-index: 10; pointer-events: none; }

/* Dialog */
.time-dialog { padding: 4px 0; }
.dialog-field { margin-bottom: 16px; }
.dialog-label { display: block; font-size: 12px; color: var(--tf-text-tertiary); margin-bottom: 6px; font-weight: 500; }
.dialog-row { display: flex; gap: 12px; }
.flex-1 { flex: 1; }

.author-display { display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: var(--tf-bg-surface); border: 1px solid var(--tf-border-light); border-radius: var(--tf-radius-md); }
.author-avatar { width: 24px; height: 24px; border-radius: 4px; background: linear-gradient(135deg, #f59e0b, #ef4444); display: flex; align-items: center; justify-content: center; font-size: 11px; color: #fff; font-weight: 600; }
.author-name { font-size: 13px; color: var(--tf-text-primary); }

.date-mode-toggle { display: inline-flex; border: 1px solid var(--tf-border); border-radius: var(--tf-radius-md); overflow: hidden; }
.date-mode-btn { padding: 6px 14px; font-size: 12px; border: none; background: transparent; color: var(--tf-text-secondary); cursor: pointer; transition: background 0.15s, color 0.15s; }
.date-mode-btn.active { background: var(--tf-bg-elevated); color: var(--tf-text-primary); font-weight: 500; }
.date-mode-btn + .date-mode-btn { border-left: 1px solid var(--tf-border); }

.add-another { margin-bottom: 16px; }
.add-another-link { font-size: 13px; color: var(--tf-accent); cursor: pointer; text-decoration: none; }
.add-another-link:hover { text-decoration: underline; }

.extra-records { margin-bottom: 16px; display: flex; flex-direction: column; gap: 8px; }
.extra-record-row { display: flex; align-items: center; gap: 8px; }
.remove-record-btn { width: 24px; height: 24px; border: none; background: transparent; color: var(--tf-text-tertiary); cursor: pointer; font-size: 14px; border-radius: 4px; display: flex; align-items: center; justify-content: center; }
.remove-record-btn:hover { background: var(--tf-bg-hover); color: var(--tf-text-primary); }

.dialog-actions { display: flex; justify-content: space-between; align-items: center; padding-top: 16px; border-top: 1px solid var(--tf-border-light); }
.actions-left { display: flex; gap: 8px; }
.actions-right { display: flex; gap: 8px; }
</style>
