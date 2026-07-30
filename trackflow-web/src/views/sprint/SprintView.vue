<template>
  <div class="sprint-page">
    <div class="page-header">
      <h2 class="page-title">迭代管理</h2>
      <div class="header-actions">
        <a-select
          v-model="selectedProject"
          placeholder="选择项目"
          style="width: 200px"
          size="small"
          allow-search
          allow-clear
          :loading="projectLoadState === 'loading'"
          @change="handleProjectChange"
        >
          <template v-if="projectLoadState === 'error'" #empty>
            <div class="select-error-state">
              <span>加载失败</span>
              <a-link @click.stop="loadProjects">重试</a-link>
            </div>
          </template>
          <a-option v-for="p in projects" :key="p.id" :value="p.id">
            {{ p.key }} - {{ p.name }}
          </a-option>
        </a-select>
        <a-button v-if="canCreateSprint" type="primary" size="small" :disabled="!selectedProject" @click="openCreateModal">
          + 新建迭代
        </a-button>
        <a-button v-if="canEditSprint" size="small" :disabled="!selectedProject" @click="router.push('/sprint-planning')">
          📋 规划
        </a-button>
      </div>
    </div>

    <!-- Sprint 列表 -->
    <div class="sprint-list" v-if="loadingState === 'success' && sprints.length > 0">

      <!-- Sprint 导引横幅：无活跃 Sprint 时为警告，有活跃 Sprint 时为信息提示 -->
      <div v-if="plannedSprints.length > 0" class="sprint-guidance-banner" :class="{ 'is-warning': !hasActiveSprint, 'is-info': hasActiveSprint }">
        <span class="warning-bar-icon">{{ hasActiveSprint ? 'ℹ️' : '⚠️' }}</span>
        <span class="warning-bar-text">{{ sprintGuidanceMessage }}</span>
        <a-tooltip v-if="!hasActiveSprint" :content="warningBarActivateTooltip">
          <a-button
            size="mini"
            type="primary"
            class="warning-bar-action"
            :disabled="!nextStartableSprint || (nextStartableSprint && !canEditSprintItem(nextStartableSprint))"
            @click="nextStartableSprint && handleActivateSprint(nextStartableSprint.id)"
          >
            开始迭代
          </a-button>
        </a-tooltip>
      </div>

      <!-- Active Sprints -->
      <div v-for="sprint in activeSprints" :key="sprint.id" class="sprint-card active" :class="{ 'sprint-overdue': sprint.overdue }">
        <div class="sprint-header">
          <div class="sprint-info">
            <span class="sprint-status-badge active" :class="{ overdue: sprint.overdue }">
              {{ sprint.overdue ? '已超期' : '进行中' }}
            </span>
            <span v-if="!selectedProject && sprint.projectKey" class="sprint-project-badge">{{ sprint.projectKey }}</span>
            <div class="sprint-name-wrapper" @mouseenter="hoveredSprintId = sprint.id" @mouseleave="hoveredSprintId = null">
              <template v-if="inlineEditingSprintId === sprint.id">
                <input
                  ref="inlineEditInputRef"
                  v-model="inlineEditName"
                  class="sprint-name-input"
                  @keydown.enter="confirmInlineEdit"
                  @keydown.esc="cancelInlineEdit"
                  @blur="cancelInlineEdit"
                  @click.stop
                />
              </template>
              <template v-else>
                <h3 class="sprint-name active-name">{{ sprint.name }}</h3>
                <a-tooltip content="编辑迭代名称" v-if="canEditSprintItem(sprint) && hoveredSprintId === sprint.id">
                  <span class="sprint-name-edit-icon" @click.stop="startInlineEdit(sprint)">
                    <icon-edit />
                  </span>
                </a-tooltip>
              </template>
            </div>
            <span class="sprint-remaining" v-if="getRemainingDays(sprint) !== null">
              <template v-if="(getRemainingDays(sprint) ?? 0) > 0">
                <span class="remaining-icon">⏳</span> 还剩 {{ getRemainingDays(sprint) }} 天
              </template>
              <template v-else-if="getRemainingDays(sprint) === 0">
                <span class="remaining-icon warning">⚠️</span> 今天截止
              </template>
              <template v-else>
                <span class="remaining-icon overdue">🚨</span> 已超期 {{ Math.abs(getRemainingDays(sprint) ?? 0) }} 天
              </template>
            </span>
          </div>
          <div class="sprint-dates">
            {{ formatDate(sprint.startDate) }} — {{ formatDate(sprint.endDate) }}
          </div>
        </div>

        <!-- 状态警告 -->
        <div class="sprint-status-warning" v-if="sprint.statusHint">
          <span class="warning-icon">⚠️</span>
          <span class="warning-text">{{ sprint.statusHint }}</span>
        </div>

        <!-- Sprint 目标 -->
        <div class="sprint-goal-banner" v-if="sprint.goal">
          <span class="sprint-goal-banner-icon">🎯</span>
          <span class="sprint-goal-banner-text">{{ sprint.goal }}</span>
        </div>

        <!-- 进度区域 -->
        <div class="sprint-progress-section">
          <div class="progress-bar-container">
            <div class="progress-bar">
              <div
                class="progress-segment done"
                :style="{ width: getProgressPercent(sprint, 'done') + '%' }"
                :title="`已完成: ${sprint.doneIssues}`"
              ></div>
              <div
                class="progress-segment in-progress"
                :style="{ width: getProgressPercent(sprint, 'inProgress') + '%' }"
                :title="`进行中: ${sprint.inProgressIssues}`"
              ></div>
              <div
                class="progress-segment todo"
                :style="{ width: getProgressPercent(sprint, 'todo') + '%' }"
                :title="`待办: ${sprint.todoIssues}`"
              ></div>
            </div>
            <span class="progress-percent">{{ getCompletionPercent(sprint) }}%</span>
          </div>
          <div class="progress-stats">
            <span class="stat-item done stat-clickable" @click.stop="viewIssuesByCategory(sprint, 'done')">
              <span class="stat-dot"></span>
              完成 {{ sprint.doneIssues }}
            </span>
            <span class="stat-item in-progress stat-clickable" @click.stop="viewIssuesByCategory(sprint, 'in_progress')">
              <span class="stat-dot"></span>
              进行中 {{ sprint.inProgressIssues }}
            </span>
            <span class="stat-item todo stat-clickable" @click.stop="viewIssuesByCategory(sprint, 'open')">
              <span class="stat-dot"></span>
              待办 {{ sprint.todoIssues }}
            </span>
            <span class="stat-item total stat-clickable" @click.stop="openIssueDrawer(sprint)">
              共 {{ sprint.totalIssues }} 个工单
            </span>
            <span class="stat-item overdue stat-clickable" v-if="sprint.overdueIssues > 0" @click.stop="viewOverdueIssues(sprint)">
              <span class="stat-dot"></span>
              逾期 {{ sprint.overdueIssues }}
            </span>
            <span class="stat-item unassigned" v-if="sprint.unassignedIssues > 0" @click.stop="viewUnassignedIssues(sprint)">
              <span class="stat-dot"></span>
              未分配 {{ sprint.unassignedIssues }}
            </span>
            <span class="stat-item estimation" v-if="sprint.totalEstimatedHours > 0">
              <span class="stat-icon">⏱</span>
              已完成 {{ formatHours(sprint.completedEstimatedHours) }} / 共 {{ formatHours(sprint.totalEstimatedHours) }}
            </span>
          </div>
        </div>

        <!-- 燃尽图 -->
        <SprintBurndownChart
          v-if="sprint.startDate && sprint.endDate && sprint.totalIssues > 0"
          :sprint-id="sprint.id"
          :sprint-end-date="sprint.endDate"
          :is-completed="false"
        />

        <!-- 负责人分布 -->
        <SprintAssigneeDistribution
          v-if="sprint.totalIssues > 0"
          :sprint-id="sprint.id"
          :sprint-name="sprint.name"
          :project-key="selectedProjectKey || sprint.projectKey"
          @view-issues="(filter: string) => openIssueDrawer(sprint, filter)"
        />

        <div class="sprint-actions">
          <a-button size="mini" type="text" @click="openIssueDrawer(sprint)">查看工单</a-button>
          <a-button size="mini" type="text" @click="viewSprintOnBoard(sprint)">在看板中查看</a-button>
          <a-button v-if="canEditSprintItem(sprint)" size="mini" type="text" @click="openEditModal(sprint)">编辑</a-button>
          <a-button v-if="canEditSprintItem(sprint)" size="mini" type="text" @click="handleArchiveActiveSprint(sprint)">归档</a-button>
          <a-button v-if="canEditSprintItem(sprint)" size="mini" @click="handleCompleteSprint(sprint)">完成迭代</a-button>
        </div>
      </div>

      <!-- Planned Sprints -->
      <div v-for="sprint in plannedSprints" :key="sprint.id" class="sprint-card planned" :class="{ 'sprint-next': !hasActiveSprint && sprint.id === nextPlannedSprintId }">
        <div class="sprint-header">
          <div class="sprint-info">
            <span class="sprint-status-badge next" v-if="!hasActiveSprint && sprint.id === nextPlannedSprintId">下一个</span>
            <span class="sprint-status-badge planned" v-else>计划中</span>
            <span v-if="!selectedProject && sprint.projectKey" class="sprint-project-badge">{{ sprint.projectKey }}</span>
            <div class="sprint-name-wrapper" @mouseenter="hoveredSprintId = sprint.id" @mouseleave="hoveredSprintId = null">
              <template v-if="inlineEditingSprintId === sprint.id">
                <input
                  ref="inlineEditInputRef"
                  v-model="inlineEditName"
                  class="sprint-name-input"
                  @keydown.enter="confirmInlineEdit"
                  @keydown.esc="cancelInlineEdit"
                  @blur="cancelInlineEdit"
                  @click.stop
                />
              </template>
              <template v-else>
                <h3 class="sprint-name">{{ sprint.name }}</h3>
                <a-tooltip content="编辑迭代名称" v-if="canEditSprintItem(sprint) && hoveredSprintId === sprint.id">
                  <span class="sprint-name-edit-icon" @click.stop="startInlineEdit(sprint)">
                    <icon-edit />
                  </span>
                </a-tooltip>
              </template>
            </div>
          </div>
          <div class="sprint-dates" v-if="sprint.startDate">
            {{ formatDate(sprint.startDate) }} — {{ formatDate(sprint.endDate) }}
          </div>
        </div>

        <!-- 状态提示 -->
        <div class="sprint-status-hint" v-if="sprint.statusHint">
          <span class="hint-icon">💡</span>
          <span class="hint-text">{{ sprint.statusHint }}</span>
        </div>

        <!-- Sprint 目标 -->
        <div class="sprint-goal-banner" v-if="sprint.goal">
          <span class="sprint-goal-banner-icon">🎯</span>
          <span class="sprint-goal-banner-text">{{ sprint.goal }}</span>
        </div>

        <!-- 进度区域 -->
        <div class="sprint-progress-section" v-if="sprint.totalIssues > 0">
          <div class="progress-bar-container">
            <div class="progress-bar">
              <div
                class="progress-segment done"
                :style="{ width: getProgressPercent(sprint, 'done') + '%' }"
              ></div>
              <div
                class="progress-segment in-progress"
                :style="{ width: getProgressPercent(sprint, 'inProgress') + '%' }"
              ></div>
              <div
                class="progress-segment todo"
                :style="{ width: getProgressPercent(sprint, 'todo') + '%' }"
              ></div>
            </div>
            <span class="progress-percent">{{ getCompletionPercent(sprint) }}%</span>
          </div>
          <div class="progress-stats">
            <span class="stat-item done stat-clickable" @click.stop="viewIssuesByCategory(sprint, 'done')">
              <span class="stat-dot"></span>
              完成 {{ sprint.doneIssues }}
            </span>
            <span class="stat-item in-progress stat-clickable" @click.stop="viewIssuesByCategory(sprint, 'in_progress')">
              <span class="stat-dot"></span>
              进行中 {{ sprint.inProgressIssues }}
            </span>
            <span class="stat-item todo stat-clickable" @click.stop="viewIssuesByCategory(sprint, 'open')">
              <span class="stat-dot"></span>
              待办 {{ sprint.todoIssues }}
            </span>
            <span class="stat-item total stat-clickable" @click.stop="openIssueDrawer(sprint)">
              共 {{ sprint.totalIssues }} 个工单
            </span>
            <span class="stat-item unassigned" v-if="sprint.unassignedIssues > 0" @click.stop="viewUnassignedIssues(sprint)">
              <span class="stat-dot"></span>
              未分配 {{ sprint.unassignedIssues }}
            </span>
            <span class="stat-item estimation" v-if="sprint.totalEstimatedHours > 0">
              <span class="stat-icon">⏱</span>
              共 {{ formatHours(sprint.totalEstimatedHours) }}
            </span>
          </div>
        </div>
        <div class="sprint-no-issues" v-else>
          <span class="no-issues-text">暂无工单</span>
        </div>

        <!-- 负责人分布 -->
        <SprintAssigneeDistribution
          v-if="sprint.totalIssues > 0"
          :sprint-id="sprint.id"
          :sprint-name="sprint.name"
          :project-key="selectedProjectKey || sprint.projectKey"
          @view-issues="(filter: string) => openIssueDrawer(sprint, filter)"
        />

        <div class="sprint-actions">
          <a-button size="mini" type="text" @click="openIssueDrawer(sprint)" v-if="sprint.totalIssues > 0">查看工单</a-button>
          <a-button size="mini" type="text" @click="viewSprintOnBoard(sprint)" v-if="sprint.totalIssues > 0">在看板中查看</a-button>
          <a-button v-if="canEditSprintItem(sprint)" size="mini" type="text" @click="openEditModal(sprint)">编辑</a-button>
          <a-tooltip :content="getActivateTooltip(sprint)">
            <a-button type="primary" size="mini" :disabled="!canEditSprintItem(sprint) || isSprintNotStartable(sprint)" @click="handleActivateSprint(sprint.id)">开始迭代</a-button>
          </a-tooltip>
          <a-button v-if="canEditSprintItem(sprint)" size="mini" type="text" @click="archiveSprint(sprint)">归档</a-button>
          <a-button v-if="canDeleteSprintItem(sprint)" size="mini" status="danger" @click="handleDeleteSprint(sprint)">删除</a-button>
        </div>
      </div>

      <!-- Completed Sprints Section (collapsible) -->
      <div v-if="completedSprints.length > 0" class="completed-section">
        <div class="completed-section-header" @click="showCompletedSprints = !showCompletedSprints">
          <span class="completed-toggle-icon">{{ showCompletedSprints ? '▾' : '▸' }}</span>
          <span class="completed-section-title">已完成</span>
          <span class="completed-section-count">{{ completedSprints.length }}</span>
        </div>
        <template v-if="showCompletedSprints">
      <div v-for="sprint in completedSprints" :key="sprint.id" class="sprint-card completed" :class="{ 'just-completed': sprint.id === justCompletedSprintId }">
        <div class="sprint-header">
          <div class="sprint-info">
            <span class="sprint-status-badge completed">已完成</span>
            <span v-if="!selectedProject && sprint.projectKey" class="sprint-project-badge">{{ sprint.projectKey }}</span>
            <h3 class="sprint-name">{{ sprint.name }}</h3>
          </div>
          <div class="sprint-dates">
            {{ formatDate(sprint.startDate) }} — {{ formatDate(sprint.endDate) }}
          </div>
        </div>

        <!-- 完成统计 -->
        <div class="sprint-progress-section" v-if="sprint.totalIssues > 0">
          <div class="progress-bar-container">
            <div class="progress-bar">
              <div
                class="progress-segment done"
                :style="{ width: getProgressPercent(sprint, 'done') + '%' }"
              ></div>
              <div
                class="progress-segment in-progress"
                :style="{ width: getProgressPercent(sprint, 'inProgress') + '%' }"
              ></div>
              <div
                class="progress-segment todo"
                :style="{ width: getProgressPercent(sprint, 'todo') + '%' }"
              ></div>
            </div>
            <span class="progress-percent">{{ getCompletionPercent(sprint) }}%</span>
          </div>
          <div class="progress-stats">
            <span class="stat-item done stat-clickable" @click.stop="viewIssuesByCategory(sprint, 'done')">
              <span class="stat-dot"></span>
              完成 {{ sprint.doneIssues }}
            </span>
            <span class="stat-item in-progress stat-clickable" v-if="sprint.inProgressIssues > 0" @click.stop="viewIssuesByCategory(sprint, 'in_progress')">
              <span class="stat-dot"></span>
              进行中 {{ sprint.inProgressIssues }}
            </span>
            <span class="stat-item todo stat-clickable" v-if="sprint.todoIssues > 0" @click.stop="viewIssuesByCategory(sprint, 'open')">
              <span class="stat-dot"></span>
              待办 {{ sprint.todoIssues }}
            </span>
            <span class="stat-item total stat-clickable" @click.stop="viewSprintIssues(sprint)">
              共 {{ sprint.totalIssues }} 个工单
            </span>
          </div>
        </div>

        <div class="sprint-actions">
          <a-button size="mini" type="text" @click="viewSprintIssues(sprint)" v-if="sprint.totalIssues > 0">查看工单</a-button>
          <a-button size="mini" type="text" @click="viewSprintOnBoard(sprint)" v-if="sprint.totalIssues > 0">在看板中查看</a-button>
          <a-button v-if="canEditSprintItem(sprint)" size="mini" type="text" @click="openEditModal(sprint)">编辑</a-button>
          <a-button
            size="mini"
            type="text"
            @click="toggleCompletedBurndown(sprint.id)"
            v-if="sprint.startDate && sprint.endDate && sprint.totalIssues > 0"
          >
            {{ expandedCompletedSprints.has(sprint.id) ? '收起燃尽图' : '查看燃尽图' }}
          </a-button>
          <a-button v-if="canEditSprintItem(sprint)" size="mini" type="text" @click="archiveSprint(sprint)">归档</a-button>
        </div>

        <!-- 已完成 Sprint 的燃尽图（展开时显示） -->
        <SprintBurndownChart
          v-if="expandedCompletedSprints.has(sprint.id)"
          :sprint-id="sprint.id"
          :sprint-end-date="sprint.endDate"
          :is-completed="true"
        />
      </div>
        </template>
      </div>

      <!-- Archived Sprints Section (collapsible) -->
      <div v-if="archivedSprints.length > 0" class="completed-section archived-section">
        <div class="completed-section-header" @click="showArchivedSprints = !showArchivedSprints">
          <span class="completed-toggle-icon">{{ showArchivedSprints ? '▾' : '▸' }}</span>
          <span class="completed-section-title">已归档</span>
          <span class="completed-section-count">{{ archivedSprints.length }}</span>
        </div>
        <template v-if="showArchivedSprints">
          <div v-for="sprint in archivedSprints" :key="sprint.id" class="sprint-card archived">
            <div class="sprint-header">
              <div class="sprint-info">
                <span class="sprint-status-badge archived">已归档</span>
                <span v-if="!selectedProject && sprint.projectKey" class="sprint-project-badge">{{ sprint.projectKey }}</span>
                <h3 class="sprint-name">{{ sprint.name }}</h3>
              </div>
              <div class="sprint-dates">
                {{ formatDate(sprint.startDate) }} — {{ formatDate(sprint.endDate) }}
              </div>
            </div>

            <!-- 统计 -->
            <div class="sprint-progress-section" v-if="sprint.totalIssues > 0">
              <div class="progress-bar-container">
                <div class="progress-bar">
                  <div
                    class="progress-segment done"
                    :style="{ width: getProgressPercent(sprint, 'done') + '%' }"
                  ></div>
                  <div
                    class="progress-segment in-progress"
                    :style="{ width: getProgressPercent(sprint, 'inProgress') + '%' }"
                  ></div>
                  <div
                    class="progress-segment todo"
                    :style="{ width: getProgressPercent(sprint, 'todo') + '%' }"
                  ></div>
                </div>
                <span class="progress-percent">{{ getCompletionPercent(sprint) }}%</span>
              </div>
              <div class="progress-stats">
                <span class="stat-item done">
                  <span class="stat-dot"></span>
                  完成 {{ sprint.doneIssues }}
                </span>
                <span class="stat-item total">
                  共 {{ sprint.totalIssues }} 个工单
                </span>
              </div>
            </div>

            <div class="sprint-actions">
              <a-button size="mini" type="text" @click="viewSprintIssues(sprint)" v-if="sprint.totalIssues > 0">查看工单</a-button>
              <a-button
                size="mini"
                type="text"
                @click="toggleCompletedBurndown(sprint.id)"
                v-if="sprint.startDate && sprint.endDate && sprint.totalIssues > 0"
              >
                {{ expandedCompletedSprints.has(sprint.id) ? '收起燃尽图' : '查看燃尽图' }}
              </a-button>
              <a-button v-if="canEditSprintItem(sprint)" size="mini" type="text" @click="restoreSprint(sprint)">恢复</a-button>
            </div>

            <!-- 已归档 Sprint 的燃尽图（展开时显示） -->
            <SprintBurndownChart
              v-if="expandedCompletedSprints.has(sprint.id)"
              :sprint-id="sprint.id"
              :sprint-end-date="sprint.endDate"
              :is-completed="true"
            />
          </div>
        </template>
      </div>
    </div>
    <div v-else-if="loadingState === 'loading'" class="empty-state">
      <a-spin :size="32" />
      <p class="empty-desc" style="margin-top: 16px;">正在加载迭代列表…</p>
    </div>

    <!-- 错误状态：区分"未选项目加载失败"和"已选项目加载失败" -->
    <div v-else-if="loadingState === 'error' && !selectedProject" class="empty-state">
      <div class="empty-icon">🏃</div>
      <h3 class="empty-title">请选择一个项目</h3>
      <p class="empty-desc">选择上方的项目后，即可查看和管理该项目的迭代（Sprint）列表</p>
    </div>
    <div v-else-if="loadingState === 'error'" class="empty-state">
      <div class="empty-icon">⚠️</div>
      <h3 class="empty-title">加载失败</h3>
      <p class="empty-desc">无法获取迭代列表，请稍后重试</p>
      <a-button type="primary" size="small" @click="loadSprints">重试</a-button>
    </div>

    <!-- 权限不足状态 -->
    <div v-else-if="loadingState === 'forbidden'" class="empty-state">
      <div class="empty-icon">🔒</div>
      <h3 class="empty-title">暂无可查看的迭代</h3>
      <p class="empty-desc">当前项目尚未创建迭代，或您没有查看权限。请联系项目管理员。</p>
    </div>

    <!-- 项目加载失败 -->
    <div v-else-if="projectLoadState === 'error'" class="empty-state">
      <div class="empty-icon">⚠️</div>
      <h3 class="empty-title">项目列表加载失败</h3>
      <p class="empty-desc">无法获取可用项目，请检查网络后重试</p>
      <a-button type="primary" size="small" @click="loadProjects">重试</a-button>
    </div>

    <!-- 正常空状态 -->
    <div v-else class="empty-state">
      <div class="empty-icon">🏃</div>
      <h3 class="empty-title">{{ selectedProject ? '暂无迭代' : '请选择一个项目' }}</h3>
      <p class="empty-desc">
        <template v-if="!selectedProject">选择上方的项目后，即可查看和管理该项目的迭代（Sprint）列表</template>
        <template v-else-if="canCreateSprint">创建第一个 Sprint 来规划团队工作</template>
        <template v-else>当前项目尚未创建迭代，请联系项目管理员。</template>
      </p>
      <a-button v-if="selectedProject && canCreateSprint" type="primary" size="small" @click="openCreateModal">
        + 新建迭代
      </a-button>
    </div>

    <!-- 创建 Sprint 弹窗 -->
    <a-modal v-model:visible="showCreate" title="新建迭代" :width="480" @ok="handleCreate" :ok-loading="creating">
      <a-form :model="createForm" layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model="createForm.name" placeholder="如：Sprint 25" />
        </a-form-item>
        <a-form-item label="目标">
          <a-textarea v-model="createForm.goal" placeholder="本迭代目标（可选）" :auto-size="{ minRows: 2, maxRows: 4 }" />
        </a-form-item>
        <a-form-item label="开始日期">
          <a-date-picker v-model="createForm.startDate" style="width: 100%" />
        </a-form-item>
        <a-form-item label="结束日期">
          <a-date-picker v-model="createForm.endDate" style="width: 100%" />
        </a-form-item>

        <!-- 可选操作区域 -->
        <div class="create-options-section" v-if="creationPreview">
          <!-- 添加当前 Sprint 未完成工单 -->
          <div
            class="create-option-item"
            v-if="creationPreview.activeSprintId && creationPreview.unresolvedIssueCount > 0"
          >
            <a-checkbox v-model="createForm.moveUnresolvedIssues">
              <span class="option-label">添加当前 Sprint 未完成工单</span>
            </a-checkbox>
            <span class="option-desc">
              将 <strong>{{ creationPreview.activeSprintName }}</strong> 中的
              {{ creationPreview.unresolvedIssueCount }} 个未完成工单移入新迭代
            </span>
          </div>

          <!-- 设为默认 Sprint -->
          <div class="create-option-item">
            <a-checkbox v-model="createForm.setAsDefault">
              <span class="option-label">设为默认 Sprint</span>
            </a-checkbox>
            <span class="option-desc">
              <template v-if="creationPreview.hasDefaultSprint">
                当前默认为 <strong>{{ creationPreview.defaultSprintName }}</strong>，替换后新建工单将自动归属此迭代
              </template>
              <template v-else>
                启用后，该项目新创建的工单将自动分配到此迭代
              </template>
            </span>
          </div>
        </div>
      </a-form>
    </a-modal>

    <!-- 编辑 Sprint 弹窗 -->
    <a-modal v-model:visible="showEdit" title="编辑迭代" :width="480" @ok="handleUpdate" :ok-loading="updating" ok-text="保存修改">
      <a-form :model="editForm" layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model="editForm.name" placeholder="迭代名称" />
        </a-form-item>
        <a-form-item label="目标">
          <a-textarea v-model="editForm.goal" placeholder="迭代目标（可选）" :auto-size="{ minRows: 2, maxRows: 4 }" />
        </a-form-item>
        <a-form-item label="开始日期">
          <a-date-picker v-model="editForm.startDate" style="width: 100%" />
        </a-form-item>
        <a-form-item label="结束日期">
          <a-date-picker v-model="editForm.endDate" style="width: 100%" />
        </a-form-item>
      </a-form>
      <template #footer>
        <div class="edit-modal-footer">
          <!-- 左侧：归档/恢复 + 删除 -->
          <div class="edit-modal-footer-left">
            <a-button
              v-if="editingSprint && canEditSprintItem(editingSprint) && editingSprint.status !== 'archived'"
              size="small"
              type="secondary"
              @click="handleEditModalArchive"
            >归档</a-button>
            <a-button
              v-if="editingSprint && canEditSprintItem(editingSprint) && editingSprint.status === 'archived'"
              size="small"
              type="secondary"
              @click="handleEditModalRestore"
            >恢复</a-button>
            <a-button
              v-if="editingSprint && canDeleteSprintItem(editingSprint)"
              size="small"
              status="danger"
              type="secondary"
              @click="handleEditModalDelete"
            >删除</a-button>
          </div>
          <!-- 右侧：保存修改 + 取消 -->
          <div class="edit-modal-footer-right">
            <a-button size="small" @click="showEdit = false">取消</a-button>
            <a-button size="small" type="primary" :loading="updating" @click="handleUpdate">保存修改</a-button>
          </div>
        </div>
      </template>
    </a-modal>

    <!-- 完成 Sprint 确认弹窗 -->
    <a-modal
      v-model:visible="showCompleteModal"
      :title="`完成迭代：${completingSprintName}`"
      :width="560"
      :ok-loading="completing"
      :ok-text="'确认完成'"
      @ok="confirmCompleteSprint"
      @cancel="showCompleteModal = false"
    >
      <!-- 无未完成工单 -->
      <div v-if="completionPreview && completionPreview.openIssues.length === 0" class="complete-no-issues">
        <div class="complete-icon">✅</div>
        <p class="complete-desc">该迭代中所有工单已完成，确认关闭迭代？</p>
      </div>

      <!-- 有未完成工单 -->
      <div v-else-if="completionPreview" class="complete-with-issues">
        <div class="complete-warning">
          <span class="warning-icon">⚠️</span>
          <span>该迭代中仍有 <strong>{{ completionPreview.openIssues.length }}</strong> 个未完成工单</span>
        </div>

        <!-- 未完成工单列表 -->
        <div class="open-issues-list">
          <div
            v-for="issue in completionPreview.openIssues"
            :key="issue.id"
            class="open-issue-item"
          >
            <span class="issue-key">{{ issue.issueKey }}</span>
            <span class="issue-title">{{ issue.title }}</span>
            <span 
              class="issue-status-tag" 
              :style="{ 
                background: (issue.statusColor || '#6b7280') + '20',
                color: issue.statusColor || '#6b7280'
              }"
            >{{ issue.statusName || '未知' }}</span>
            <span class="issue-assignee" v-if="issue.assigneeName">{{ issue.assigneeName }}</span>
          </div>
        </div>

        <!-- 处理方式选择 -->
        <div class="move-option-section">
          <p class="move-option-label">请选择未完成工单的处理方式：</p>
          <a-radio-group v-model="moveOption" direction="vertical">
            <a-radio value="backlog">
              <span class="radio-label">移回 Backlog</span>
              <span class="radio-desc">清空工单的迭代归属，回到待规划状态</span>
            </a-radio>
            <a-radio value="next_sprint" :disabled="completionPreview.targetSprints.length === 0">
              <span class="radio-label">移入其他迭代</span>
              <span class="radio-desc" v-if="completionPreview.targetSprints.length > 0">
                将未完成工单转移到指定的迭代中
              </span>
              <span class="radio-desc disabled" v-else>
                当前项目没有其他可用迭代
              </span>
            </a-radio>
          </a-radio-group>

          <!-- 目标 Sprint 选择 -->
          <div v-if="moveOption === 'next_sprint' && completionPreview.targetSprints.length > 0" class="target-sprint-select">
            <a-select v-model="targetSprintId" placeholder="选择目标迭代" style="width: 100%">
              <a-option
                v-for="target in completionPreview.targetSprints"
                :key="target.id"
                :value="target.id"
              >
                {{ target.name }}
                <span class="target-status-tag">{{ target.status === 'active' ? '进行中' : '计划中' }}</span>
              </a-option>
            </a-select>
          </div>
        </div>
      </div>

      <!-- 加载中 -->
      <div v-else class="complete-loading">
        <a-spin :size="24" />
        <p style="margin-top: 12px; color: var(--color-text-3);">正在获取工单信息…</p>
      </div>
    </a-modal>

    <!-- 删除 Sprint 确认弹窗 -->
    <a-modal
      v-model:visible="showDeleteModal"
      :title="`删除迭代：${deletingSprintName}`"
      :width="520"
      :ok-loading="deleting"
      ok-text="确认删除"
      :ok-button-props="{ status: 'danger' }"
      @ok="confirmDeleteSprint"
      @cancel="showDeleteModal = false"
    >
      <!-- 无关联工单 -->
      <div v-if="deletionPreview && deletionPreview.totalIssues === 0" class="delete-no-issues">
        <div class="delete-warning-banner">
          <span class="warning-icon">⚠️</span>
          <span class="warning-text">此操作不可撤销</span>
        </div>
        <p class="delete-desc">
          确定删除迭代 <strong>{{ deletionPreview.sprintName }}</strong>
          <template v-if="deletionPreview.dateRange"> ({{ deletionPreview.dateRange }})</template>？
        </p>
        <p class="delete-hint">该迭代中没有工单，删除后不会影响任何工单。</p>
      </div>

      <!-- 有关联工单 -->
      <div v-else-if="deletionPreview" class="delete-with-issues">
        <div class="delete-warning-banner danger">
          <span class="warning-icon">🚨</span>
          <span class="warning-text">此操作不可撤销</span>
        </div>

        <p class="delete-desc">
          确定删除迭代 <strong>{{ deletionPreview.sprintName }}</strong>
          <template v-if="deletionPreview.dateRange"> ({{ deletionPreview.dateRange }})</template>？
        </p>

        <div class="delete-impact-info">
          <span class="impact-icon">📋</span>
          <span>该迭代包含 <strong>{{ deletionPreview.totalIssues }}</strong> 个工单，删除后这些工单的迭代归属将被清空。</span>
        </div>

        <!-- 处理方式选择 -->
        <div class="delete-move-section">
          <p class="move-option-label">请选择工单处理方式：</p>
          <a-radio-group v-model="deleteMoveOption" direction="vertical">
            <a-radio value="backlog">
              <span class="radio-label">移回 Backlog</span>
              <span class="radio-desc">清空工单的迭代归属，回到待规划状态</span>
            </a-radio>
            <a-radio value="next_sprint" :disabled="deletionPreview.targetSprints.length === 0">
              <span class="radio-label">移入其他迭代</span>
              <span class="radio-desc" v-if="deletionPreview.targetSprints.length > 0">
                将工单转移到指定的迭代中
              </span>
              <span class="radio-desc disabled" v-else>
                当前项目没有其他可用迭代
              </span>
            </a-radio>
          </a-radio-group>

          <!-- 目标 Sprint 选择 -->
          <div v-if="deleteMoveOption === 'next_sprint' && deletionPreview.targetSprints.length > 0" class="target-sprint-select">
            <a-select v-model="deleteTargetSprintId" placeholder="选择目标迭代" style="width: 100%">
              <a-option
                v-for="target in deletionPreview.targetSprints"
                :key="target.id"
                :value="target.id"
              >
                {{ target.name }}
                <span class="target-status-tag">{{ target.status === 'active' ? '进行中' : '计划中' }}</span>
              </a-option>
            </a-select>
          </div>
        </div>
      </div>

      <!-- 加载中 -->
      <div v-else class="complete-loading">
        <a-spin :size="24" />
        <p style="margin-top: 12px; color: var(--color-text-3);">正在获取迭代信息…</p>
      </div>
    </a-modal>

    <!-- 日期重叠确认弹窗 -->
    <a-modal
      v-model:visible="showOverlapConfirm"
      title="日期重叠警告"
      :width="520"
      ok-text="确认继续"
      cancel-text="取消"
      @ok="confirmOverlapAndProceed"
      @cancel="showOverlapConfirm = false"
    >
      <div class="overlap-warning-content">
        <div class="overlap-warning-header">
          <span class="overlap-warning-icon">⚠️</span>
          <span class="overlap-warning-title">
            {{ overlapContext === 'create' ? '新建迭代' : '修改后的迭代' }}日期与以下已有迭代存在重叠：
          </span>
        </div>
        <div class="overlap-sprint-list" v-if="overlapWarning">
          <div
            v-for="(sprint, index) in overlapWarning.overlappingSprints"
            :key="index"
            class="overlap-sprint-item"
          >
            <span class="overlap-sprint-name">{{ sprint.name }}</span>
            <span class="overlap-sprint-dates">{{ sprint.startDate }} ~ {{ sprint.endDate }}</span>
            <span class="overlap-sprint-status" :class="sprint.status">
              {{ sprint.status === 'active' ? '进行中' : '计划中' }}
            </span>
          </div>
        </div>
        <div class="overlap-warning-hint">
          <p>重叠的迭代可能影响"当前 Sprint"的自动检测和工单归属。</p>
          <p>如果确定要继续，请点击"确认继续"。</p>
        </div>
      </div>
    </a-modal>

    <!-- Sprint 内工单快速分配抽屉 -->
    <SprintIssueDrawer
      v-model:visible="showIssueDrawer"
      :sprint-id="drawerSprintId"
      :sprint-name="drawerSprintName"
      :project-id="drawerProjectId"
      :initial-filter="drawerInitialFilter"
      @assigned="handleDrawerAssigned"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { IconEdit } from '@arco-design/web-vue/es/icon'
import { sprintApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import { usePermission, canEditSprintSync, canDeleteSprintSync, preloadPermissions } from '@/composables/usePermission'
import { useProjectList } from '@/composables/useProjectList'
import type { SprintVO, CompletionPreviewVO, DeletionPreviewVO, CreationPreviewVO, SprintOverlapWarning } from '@/api/types'
import { ERROR_CODES } from '@/api/error-codes'
import SprintBurndownChart from './SprintBurndownChart.vue'
import SprintAssigneeDistribution from './SprintAssigneeDistribution.vue'
import SprintIssueDrawer from './SprintIssueDrawer.vue'

const router = useRouter()
const route = useRoute()
const projectStore = useProjectStore()

const selectedProject = computed({
  get: () => projectStore.selectedProjectId,
  set: (val) => projectStore.selectProject(val)
})

// 权限控制（必须在 selectedProject 定义之后）
// 页面级权限控制（用于创建按钮等需要选择项目的场景）
const { canCreateSprint, canEditSprint, canDeleteSprint } = usePermission(() => selectedProject.value)

/**
 * 基于 Sprint 自身的 projectId 检查编辑权限
 * 用于 Sprint 卡片的操作按钮显隐控制
 */
function canEditSprintItem(sprint: SprintVO): boolean {
  return canEditSprintSync(sprint.projectId)
}

/**
 * 基于 Sprint 自身的 projectId 检查删除权限
 */
function canDeleteSprintItem(sprint: SprintVO): boolean {
  return canDeleteSprintSync(sprint.projectId)
}

const { projects, projectLoadState, loadProjects } = useProjectList()
const sprints = ref<SprintVO[]>([])
const showCreate = ref(false)
const creating = ref(false)
const creationPreview = ref<CreationPreviewVO | null>(null)
const expandedCompletedSprints = ref<Set<string>>(new Set())
const showCompletedSprints = ref(false)
const showArchivedSprints = ref(false)

// ===== 编辑迭代相关 =====
const showEdit = ref(false)
const updating = ref(false)
const editingSprintId = ref<string>('')
const editingCompleted = ref(false)
const editingSprint = ref<SprintVO | null>(null)
const editForm = reactive({
  name: '',
  goal: '',
  startDate: '',
  endDate: ''
})

// ===== 内联编辑迭代名称 =====
const hoveredSprintId = ref<string | null>(null)
const inlineEditingSprintId = ref<string | null>(null)
const inlineEditName = ref<string>('')
const inlineEditInputRef = ref<HTMLInputElement[]>([])

// ===== 完成迭代相关 =====
const showCompleteModal = ref(false)
const completing = ref(false)
const completingSprintId = ref<string>('')
const completingSprintName = ref<string>('')
const completionPreview = ref<CompletionPreviewVO | null>(null)
const moveOption = ref<string>('backlog')
const targetSprintId = ref<string>('')
/** 刚完成的 Sprint ID，用于高亮动画 */
const justCompletedSprintId = ref<string | null>(null)

// ===== 删除迭代相关 =====
const showDeleteModal = ref(false)
const deleting = ref(false)
const deletingSprintId = ref<string>('')
const deletingSprintName = ref<string>('')
const deletionPreview = ref<DeletionPreviewVO | null>(null)
const deleteMoveOption = ref<string>('backlog')
const deleteTargetSprintId = ref<string>('')

/**
 * 加载状态机：
 * - idle: 未加载（未选择项目）
 * - loading: 加载中
 * - success: 加载成功（可能数据为空）
 * - error: 网络/服务端错误
 * - forbidden: 权限不足（403）
 */
type LoadingState = 'idle' | 'loading' | 'success' | 'error' | 'forbidden'
const loadingState = ref<LoadingState>('idle')

const createForm = reactive({
  name: '',
  goal: '',
  startDate: '',
  endDate: '',
  moveUnresolvedIssues: false,
  setAsDefault: false
})

// ===== 日期重叠确认 =====
const showOverlapConfirm = ref(false)
const overlapWarning = ref<SprintOverlapWarning | null>(null)
const overlapContext = ref<'create' | 'edit'>('create')

// ===== Sprint Issue Drawer（Sprint 内工单快速分配） =====
const showIssueDrawer = ref(false)
const drawerSprintId = ref('')
const drawerSprintName = ref('')
const drawerProjectId = ref<string | undefined>(undefined)
const drawerInitialFilter = ref<'unassigned' | string | null>(null)

const activeSprints = computed(() => sprints.value.filter(s => s.status === 'active' || s.status === 'Active'))
const plannedSprints = computed(() => sprints.value.filter(s => s.status === 'planned' || s.status === 'Planned'))
const completedSprints = computed(() => sprints.value.filter(s => s.status === 'completed' || s.status === 'Completed'))
const archivedSprints = computed(() => sprints.value.filter(s => s.status === 'archived' || s.status === 'Archived'))
const hasActiveSprint = computed(() => activeSprints.value.length > 0)
const nextStartableSprint = computed(() => {
  return plannedSprints.value.find(s => !isSprintNotStartable(s)) || null
})

/**
 * Sprint 导引横幅文本——根据是否有活跃 Sprint 提供不同引导
 */
const sprintGuidanceMessage = computed(() => {
  if (hasActiveSprint.value) {
    const active = activeSprints.value[0]
    const plannedCount = plannedSprints.value.length
    return `当前活跃迭代「${active.name}」进行中。还有 ${plannedCount} 个计划中的迭代等待启动。`
  }
  const next = plannedSprints.value[0]
  if (next?.startDate) {
    return `当前没有活跃的迭代。下一个迭代「${next.name}」计划于 ${formatDate(next.startDate)} 开始。`
  }
  return '当前没有活跃的迭代。请开始一个已计划的迭代以跟踪团队工作进度。'
})

/**
 * 警告栏中「开始迭代」按钮的 tooltip
 */
const warningBarActivateTooltip = computed<string | undefined>(() => {
  const nextSprint = nextStartableSprint.value
  // 权限检查改为基于 Sprint 自身的 projectId
  if (nextSprint && !canEditSprintItem(nextSprint)) {
    return '您的角色不具有迭代管理权限，请联系项目管理员'
  }
  if (!nextSprint) {
    // 所有 planned sprint 都不能启动——告知原因
    const next = plannedSprints.value[0]
    if (next?.startDate) {
      return `开始日期（${formatDate(next.startDate)}）尚未到达`
    }
    if (next?.endDate) {
      return `结束日期（${formatDate(next.endDate)}）已过期，无法激活`
    }
    return '当前没有可启动的迭代'
  }
  return `启动迭代「${nextSprint.name}」`
})

const selectedProjectKey = computed(() => {
  const p = projects.value.find(proj => proj.id === selectedProject.value)
  return p?.key || undefined
})

/**
 * "下一个 Sprint" — 没有 active 时，从 planned 中找最近的那个。
 * 仅用于 UI badge 显示"下一个"标签，不代表它是"当前"。
 */
const nextPlannedSprintId = computed<string | null>(() => {
  // 有 active 时不需要标记任何 planned 为"下一个"
  if (activeSprints.value.length > 0) return null
  // planned 列表已按 start_date ASC 排序（后端保证）
  if (plannedSprints.value.length > 0) {
    return plannedSprints.value[0].id
  }
  return null
})

// ===== 工具函数 =====

function formatDate(dateStr?: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function formatHours(hours: number): string {
  if (hours === 0) return '0h'
  if (hours >= 1) return `${Math.round(hours * 10) / 10}h`
  return `${Math.round(hours * 60)}m`
}

function getRemainingDays(sprint: SprintVO): number | null {
  if (!sprint.endDate) return null
  const end = new Date(sprint.endDate)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  end.setHours(0, 0, 0, 0)
  return Math.ceil((end.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))
}

function getProgressPercent(sprint: SprintVO, type: 'done' | 'inProgress' | 'todo'): number {
  if (sprint.totalIssues === 0) return 0
  const map = {
    done: sprint.doneIssues,
    inProgress: sprint.inProgressIssues,
    todo: sprint.todoIssues
  }
  return (map[type] / sprint.totalIssues) * 100
}

function getCompletionPercent(sprint: SprintVO): number {
  if (sprint.totalIssues === 0) return 0
  return Math.round((sprint.doneIssues / sprint.totalIssues) * 100)
}

function isSprintNotStartable(sprint: SprintVO): boolean {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  // 开始日期还没到
  if (sprint.startDate) {
    const start = new Date(sprint.startDate)
    start.setHours(0, 0, 0, 0)
    if (start.getTime() > today.getTime()) return true
  }
  // 结束日期已过期
  if (sprint.endDate) {
    const end = new Date(sprint.endDate)
    end.setHours(0, 0, 0, 0)
    if (end.getTime() < today.getTime()) return true
  }
  return false
}

function getActivateTooltip(sprint: SprintVO): string | undefined {
  // 权限检查改为基于 Sprint 自身的 projectId
  if (!canEditSprintItem(sprint)) return '您的角色不具有迭代管理权限，请联系项目管理员'
  if (hasActiveSprint.value) {
    const active = activeSprints.value[0]
    return `需要先完成当前活跃迭代「${active.name}」才能激活此迭代`
  }
  // Check end date expired first (more specific)
  if (sprint.endDate) {
    const end = new Date(sprint.endDate)
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    end.setHours(0, 0, 0, 0)
    if (end.getTime() < today.getTime()) return `结束日期（${formatDate(sprint.endDate)}）已过期，无法激活`
  }
  // Check start date not reached
  if (sprint.startDate) {
    const start = new Date(sprint.startDate)
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    start.setHours(0, 0, 0, 0)
    if (start.getTime() > today.getTime()) return `开始日期（${formatDate(sprint.startDate)}）尚未到达`
  }
  return undefined
}

function viewSprintIssues(sprint: SprintVO) {
  // 跳转到 Issue 列表，按 Sprint + 项目筛选
  const currentProject = projects.value.find(p => p.id === selectedProject.value)
  const projectKey = currentProject?.key || sprint.projectKey
  const query: Record<string, string> = { sprint: sprint.id, label: sprint.name }
  if (projectKey) {
    query.project = projectKey
  }
  router.push({ path: '/issues', query })
}

function viewSprintOnBoard(sprint: SprintVO) {
  // 跳转到看板，自动选中该 Sprint 和当前项目
  const currentProject = projects.value.find(p => p.id === selectedProject.value)
  const projectKey = currentProject?.key || sprint.projectKey
  const query: Record<string, string> = { sprint: sprint.id }
  if (projectKey) {
    query.project = projectKey
  }
  router.push({ path: '/boards', query })
}

function viewUnassignedIssues(sprint: SprintVO) {
  // 在 Sprint 视图内打开 Drawer，支持就地分配
  openIssueDrawer(sprint, 'unassigned')
}

function viewIssuesByCategory(sprint: SprintVO, category: 'done' | 'in_progress' | 'open') {
  const currentProject = projects.value.find(p => p.id === selectedProject.value)
  const projectKey = currentProject?.key || sprint.projectKey
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
  if (projectKey) {
    query.project = projectKey
  }
  router.push({ path: '/issues', query })
}

function viewOverdueIssues(sprint: SprintVO) {
  const currentProject = projects.value.find(p => p.id === selectedProject.value)
  const projectKey = currentProject?.key || sprint.projectKey
  const query: Record<string, string> = {
    sprint: sprint.id,
    overdue: 'true',
    label: `${sprint.name} - 逾期工单`
  }
  if (projectKey) {
    query.project = projectKey
  }
  router.push({ path: '/issues', query })
}

function toggleCompletedBurndown(sprintId: string) {
  const set = new Set(expandedCompletedSprints.value)
  if (set.has(sprintId)) {
    set.delete(sprintId)
  } else {
    set.add(sprintId)
  }
  expandedCompletedSprints.value = set
}

// ===== Sprint Issue Drawer =====

function openIssueDrawer(sprint: SprintVO, initialFilter: 'unassigned' | string | null = null) {
  drawerSprintId.value = sprint.id
  drawerSprintName.value = sprint.name
  // 确定 projectId：优先用当前选中项目，其次用 sprint 上附带的 projectId
  drawerProjectId.value = selectedProject.value || sprint.projectId || undefined
  drawerInitialFilter.value = initialFilter
  showIssueDrawer.value = true
}

function handleDrawerAssigned() {
  // 分配成功后刷新 Sprint 列表以更新统计数据
  loadSprints()
}

// ===== API 调用 =====

// ===== 内联编辑迭代名称 =====

function startInlineEdit(sprint: SprintVO) {
  inlineEditingSprintId.value = sprint.id
  inlineEditName.value = sprint.name
  nextTick(() => {
    const input = inlineEditInputRef.value[0]
    if (input) {
      input.focus()
      input.select()
    }
  })
}

async function confirmInlineEdit() {
  const sprintId = inlineEditingSprintId.value
  const newName = inlineEditName.value.trim()
  if (!sprintId) return
  if (!newName) {
    Message.warning('迭代名称不能为空')
    return
  }
  // 找原始 sprint 对比是否有改动
  const original = sprints.value.find(s => s.id === sprintId)
  inlineEditingSprintId.value = null
  if (!original || original.name === newName) return
  try {
    await sprintApi.update(sprintId, { name: newName })
    // 本地更新，无需全量刷新
    const idx = sprints.value.findIndex(s => s.id === sprintId)
    if (idx !== -1) {
      sprints.value[idx] = { ...sprints.value[idx], name: newName }
    }
    Message.success('迭代名称已更新')
  } catch (e: any) {
    Message.error(e.response?.data?.message || '更新失败')
  }
}

function cancelInlineEdit() {
  inlineEditingSprintId.value = null
  inlineEditName.value = ''
}

async function loadSprints() {
  loadingState.value = 'loading'
  try {
    if (selectedProject.value) {
      // 特定项目模式
      const res = await sprintApi.listByProject(selectedProject.value, { pageSize: 200, _silent403: true })
      sprints.value = res.data?.list || []
    } else {
      // 跨项目模式 — 显示所有可访问项目的 Sprint
      const res = await sprintApi.listAll({ pageSize: 200 })
      sprints.value = res.data?.list || []
    }
    loadingState.value = 'success'
    
    // 预加载所有涉及项目的权限，确保 Sprint 卡片能正确显示操作按钮
    const projectIds = [...new Set(sprints.value.map(s => s.projectId).filter(Boolean))]
    if (projectIds.length > 0) {
      await preloadPermissions(projectIds)
      // 触发响应式更新，让依赖 canEditSprintItem 的模板重新渲染
      // 通过浅拷贝 sprints 数组强制 Vue 检测到变化
      sprints.value = [...sprints.value]
    }
  } catch (e: any) {
    sprints.value = []
    if (e?.response?.status === 403) {
      loadingState.value = 'forbidden'
    } else {
      loadingState.value = 'error'
    }
  }
}

function handleProjectChange() {
  loadSprints()
}

/**
 * 处理"开始迭代"点击——如果已有活跃 Sprint，弹出提示引导用户先完成当前迭代；
 * 否则直接激活。
 */
function handleActivateSprint(id: string) {
  if (hasActiveSprint.value) {
    const active = activeSprints.value[0]
    Modal.warning({
      title: '无法激活迭代',
      content: `当前项目已有一个活跃的迭代「${active.name}」正在进行中。请先完成该迭代后再激活新的迭代。`,
      okText: '我知道了',
      hideCancel: true,
    })
    return
  }
  activateSprint(id)
}

async function activateSprint(id: string) {
  try {
    await sprintApi.activate(id)
    Message.success('迭代已开始')
    loadSprints()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  }
}

async function handleCompleteSprint(sprint: SprintVO) {
  completingSprintId.value = sprint.id
  completingSprintName.value = sprint.name
  completionPreview.value = null
  moveOption.value = 'backlog'
  targetSprintId.value = ''
  showCompleteModal.value = true

  try {
    const res = await sprintApi.completionPreview(sprint.id)
    completionPreview.value = res.data

    // 如果有未完成工单且有可迁移目标，默认选中第一个
    if (res.data.openIssues.length > 0 && res.data.targetSprints.length > 0) {
      targetSprintId.value = res.data.targetSprints[0].id
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '获取预览信息失败')
    showCompleteModal.value = false
  }
}

async function confirmCompleteSprint() {
  if (!completionPreview.value) return

  const hasOpenIssues = completionPreview.value.openIssues.length > 0

  // 有未完成工单时需要验证选项
  if (hasOpenIssues) {
    if (moveOption.value === 'next_sprint' && !targetSprintId.value) {
      Message.warning('请选择目标迭代')
      return
    }
  }

  completing.value = true
  try {
    const body = hasOpenIssues
      ? { moveOption: moveOption.value, targetSprintId: moveOption.value === 'next_sprint' ? targetSprintId.value : undefined }
      : undefined

    const res = await sprintApi.complete(completingSprintId.value, body)
    const result = res.data

    // 构建包含统计信息的 toast 消息
    // 兼容两种响应格式：
    // - 新格式 SprintCompleteResultVO: { sprint: SprintVO, totalIssues, completedIssues, ... }
    // - 旧格式 SprintVO（向后兼容）: { id, name, status, ... }
    const sprintName = result.sprint?.name ?? result.name ?? completingSprintName.value
    const totalIssueCount = result.totalIssues ?? 0
    const completedCount = result.completedIssues ?? 0
    
    let toastMessage = `迭代「${sprintName}」已完成`
    if (totalIssueCount > 0) {
      toastMessage += `：完成 ${completedCount}/${totalIssueCount} 工单`
    }
    if (result.unresolvedIssues > 0) {
      if (result.moveOption === 'backlog') {
        toastMessage += `，${result.unresolvedIssues} 个工单已移回 Backlog`
      } else if (result.moveOption === 'next_sprint' && result.targetSprintName) {
        toastMessage += `，${result.unresolvedIssues} 个工单已移入「${result.targetSprintName}」`
      }
    }
    Message.success(toastMessage)

    showCompleteModal.value = false

    // 自动展开「已完成 Sprint」区域
    showCompletedSprints.value = true

    // 记录刚完成的 Sprint ID，用于高亮动画
    justCompletedSprintId.value = completingSprintId.value

    // 刷新列表
    await loadSprints()

    // 3 秒后清除高亮状态
    setTimeout(() => {
      justCompletedSprintId.value = null
    }, 3000)
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  } finally {
    completing.value = false
  }
}

async function handleDeleteSprint(sprint: SprintVO) {
  deletingSprintId.value = sprint.id
  deletingSprintName.value = sprint.name
  deletionPreview.value = null
  deleteMoveOption.value = 'backlog'
  deleteTargetSprintId.value = ''
  showDeleteModal.value = true

  try {
    const res = await sprintApi.deletionPreview(sprint.id)
    deletionPreview.value = res.data

    // 如果有工单且有可迁移目标，默认选中第一个
    if (res.data.totalIssues > 0 && res.data.targetSprints.length > 0) {
      deleteTargetSprintId.value = res.data.targetSprints[0].id
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '获取预览信息失败')
    showDeleteModal.value = false
  }
}

async function confirmDeleteSprint() {
  if (!deletionPreview.value) return

  const hasIssues = deletionPreview.value.totalIssues > 0

  // 有工单时需要验证选项
  if (hasIssues) {
    if (deleteMoveOption.value === 'next_sprint' && !deleteTargetSprintId.value) {
      Message.warning('请选择目标迭代')
      return
    }
  }

  deleting.value = true
  try {
    const body = hasIssues
      ? { moveOption: deleteMoveOption.value, targetSprintId: deleteMoveOption.value === 'next_sprint' ? deleteTargetSprintId.value : undefined }
      : undefined

    await sprintApi.delete(deletingSprintId.value, body)
    Message.success('迭代已删除')
    showDeleteModal.value = false
    loadSprints()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
  } finally {
    deleting.value = false
  }
}

async function archiveSprint(sprint: SprintVO) {
  try {
    await sprintApi.archive(sprint.id)
    Message.success('迭代已归档')
    loadSprints()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '归档失败')
  }
}

async function handleArchiveActiveSprint(sprint: SprintVO) {
  // active Sprint 归档时给出确认提示
  const confirmed = await new Promise<boolean>((resolve) => {
    Modal.confirm({
      title: '确认归档进行中的迭代',
      content: `迭代「${sprint.name}」当前仍在进行中，确认要归档吗？归档后工单将保留原迭代关联。`,
      okText: '确认归档',
      cancelText: '取消',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    })
  })
  if (confirmed) {
    await archiveSprint(sprint)
  }
}

async function restoreSprint(sprint: SprintVO) {
  try {
    await sprintApi.restore(sprint.id)
    Message.success('迭代已恢复')
    loadSprints()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '恢复失败')
  }
}

async function openCreateModal() {
  // 重置表单
  createForm.name = ''
  createForm.goal = ''
  createForm.startDate = ''
  createForm.endDate = ''
  createForm.moveUnresolvedIssues = false
  createForm.setAsDefault = false
  creationPreview.value = null
  showCreate.value = true

  // 加载创建预览信息（判断是否显示可选项）
  if (selectedProject.value) {
    try {
      const res = await sprintApi.creationPreview(selectedProject.value)
      creationPreview.value = res.data
    } catch (e) {
      // 预览加载失败不阻塞创建流程
      creationPreview.value = null
    }
  }
}

async function handleCreate() {
  if (!createForm.name.trim()) {
    Message.warning('请输入迭代名称')
    return
  }
  // 前端日期顺序校验
  if (createForm.startDate && createForm.endDate && createForm.startDate >= createForm.endDate) {
    Message.warning('开始日期必须早于结束日期')
    return
  }
  await doCreate(false)
}

async function doCreate(confirmOverlap: boolean) {
  creating.value = true
  try {
    await sprintApi.create(selectedProject.value!, {
      name: createForm.name.trim(),
      goal: createForm.goal || undefined,
      startDate: createForm.startDate || undefined,
      endDate: createForm.endDate || undefined,
      moveUnresolvedIssues: createForm.moveUnresolvedIssues || undefined,
      setAsDefault: createForm.setAsDefault || undefined,
      confirmOverlap: confirmOverlap || undefined
    })
    Message.success('迭代创建成功')
    showCreate.value = false
    createForm.name = ''
    createForm.goal = ''
    createForm.startDate = ''
    createForm.endDate = ''
    createForm.moveUnresolvedIssues = false
    createForm.setAsDefault = false
    loadSprints()
  } catch (e: any) {
    const code = e.response?.data?.code
    if (code === ERROR_CODES.SPRINT_DATE_OVERLAP) {
      // 检测到日期重叠，显示确认弹窗
      overlapWarning.value = e.response.data.data as SprintOverlapWarning
      overlapContext.value = 'create'
      showOverlapConfirm.value = true
    } else {
      Message.error(e.response?.data?.message || '创建失败')
    }
  } finally {
    creating.value = false
  }
}

function confirmOverlapAndProceed() {
  showOverlapConfirm.value = false
  if (overlapContext.value === 'create') {
    doCreate(true)
  } else {
    doUpdate(true)
  }
}

function openEditModal(sprint: SprintVO) {
  editingSprintId.value = sprint.id
  editingCompleted.value = false  // 所有状态均允许修改日期（YouTrack 标准）
  editingSprint.value = sprint
  editForm.name = sprint.name
  editForm.goal = sprint.goal || ''
  editForm.startDate = sprint.startDate || ''
  editForm.endDate = sprint.endDate || ''
  showEdit.value = true
}

async function handleEditModalArchive() {
  if (!editingSprint.value) return
  const sprint = editingSprint.value
  showEdit.value = false
  await archiveSprint(sprint)
}

async function handleEditModalRestore() {
  if (!editingSprint.value) return
  const sprint = editingSprint.value
  showEdit.value = false
  await restoreSprint(sprint)
}

async function handleEditModalDelete() {
  if (!editingSprint.value) return
  const sprint = editingSprint.value
  showEdit.value = false
  await handleDeleteSprint(sprint)
}

async function handleUpdate() {
  if (!editForm.name.trim()) {
    Message.warning('请输入迭代名称')
    return
  }
  // 前端日期顺序校验
  if (editForm.startDate && editForm.endDate) {
    if (editForm.startDate >= editForm.endDate) {
      Message.warning('开始日期必须早于结束日期')
      return
    }
  }
  await doUpdate(false)
}

async function doUpdate(confirmOverlap: boolean) {
  updating.value = true
  try {
    const data: Record<string, any> = {
      name: editForm.name.trim(),
      goal: editForm.goal || ''
    }

    // 日期处理：区分"清空"和"设置"语义
    // 若用户清空了日期（原来有值，现在为空），需发送 clearXxxDate=true
    // 若用户设置了日期，直接发送日期值
    if (!editForm.startDate) {
      // 用户清空了开始日期
      if (editingSprint.value?.startDate) {
        // 原来有日期，现在清空——发送清空信号
        data.clearStartDate = true
      }
      // 原来就没有日期，无需操作
    } else {
      data.startDate = editForm.startDate
    }

    if (!editForm.endDate) {
      // 用户清空了结束日期
      if (editingSprint.value?.endDate) {
        // 原来有日期，现在清空——发送清空信号
        data.clearEndDate = true
      }
      // 原来就没有日期，无需操作
    } else {
      data.endDate = editForm.endDate
    }

    if (confirmOverlap) {
      data.confirmOverlap = true
    }
    await sprintApi.update(editingSprintId.value, data)
    Message.success('迭代更新成功')
    showEdit.value = false
    loadSprints()
  } catch (e: any) {
    const code = e.response?.data?.code
    if (code === ERROR_CODES.SPRINT_DATE_OVERLAP) {
      overlapWarning.value = e.response.data.data as SprintOverlapWarning
      overlapContext.value = 'edit'
      showOverlapConfirm.value = true
    } else {
      Message.error(e.response?.data?.message || '更新失败')
    }
  } finally {
    updating.value = false
  }
}

onMounted(async () => {
  await loadProjects()

  // 1. 从 URL query 恢复项目选择（优先级最高）
  const queryProject = route.query.project as string | undefined
  if (queryProject && projects.value.length > 0) {
    // URL 中使用 project key（如 DE4），需要转为 ID
    const matchedByKey = projects.value.find(p => p.key === queryProject)
    if (matchedByKey) {
      projectStore.selectProject(matchedByKey.id)
    }
  }

  // 2. 始终加载 Sprint 列表（无论有无项目选择）
  // 如果有选中项目则加载该项目的 Sprint，否则加载所有可访问项目的 Sprint
  loadSprints()

  // 同步 URL
  if (selectedProject.value) {
    syncUrlProjectParam()
  }
})

/**
 * 项目选择变化时同步 URL 参数
 */
watch(selectedProject, (val) => {
  if (val) {
    syncUrlProjectParam()
  } else {
    // 清除 URL 中的 project 参数（"全部项目"视图）
    const query = { ...route.query }
    delete query.project
    router.replace({ query })
  }
})

/**
 * 将当前选中的项目 key 同步到 URL query 参数
 */
function syncUrlProjectParam() {
  const currentProject = projects.value.find(p => p.id === selectedProject.value)
  if (currentProject) {
    const currentQueryProject = route.query.project
    if (currentQueryProject !== currentProject.key) {
      router.replace({ query: { ...route.query, project: currentProject.key } })
    }
  }
}
</script>

<style scoped>
.sprint-page {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-1);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sprint-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sprint-card {
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 16px 20px;
  transition: border-color 0.15s;
}
.sprint-card:hover {
  border-color: rgb(var(--primary-6));
}
.sprint-card.active {
  border-left: 3px solid rgb(var(--primary-6));
}
.sprint-card.sprint-next {
  border-left: 3px solid rgb(var(--primary-6));
}
.sprint-card.completed {
  opacity: 0.7;
}
.sprint-card.archived {
  opacity: 0.5;
}

.sprint-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sprint-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sprint-status-badge {
  font-size: 11px;
  padding: 4px 8px;
  border-radius: 3px;
  font-weight: 500;
  letter-spacing: 0.2px;
  flex-shrink: 0;
}
.sprint-status-badge.active { background: rgba(var(--primary-6), 0.1); color: rgb(var(--primary-6)); }
.sprint-status-badge.planned { background: var(--color-fill-2); color: var(--color-text-3); }
.sprint-status-badge.next { background: rgba(var(--primary-6), 0.1); color: rgb(var(--primary-6)); font-weight: 600; }
.sprint-status-badge.completed { background: var(--color-fill-2); color: var(--color-text-3); }
.sprint-status-badge.archived { background: var(--color-fill-2); color: var(--color-text-4); }

.sprint-name {
  font-size: 14px;
  color: var(--color-text-1);
  font-weight: 500;
}

/* 项目标识 badge（跨项目视图时显示） */
.sprint-project-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 3px;
  background: var(--color-fill-2);
  color: var(--color-text-2);
  letter-spacing: 0.3px;
  flex-shrink: 0;
}

/* 活跃 Sprint 名称加粗 */
.sprint-name.active-name {
  font-weight: 700;
}

/* Sprint 名称区域：hover 时显示编辑图标 */
.sprint-name-wrapper {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sprint-name-edit-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 3px;
  color: var(--color-text-3);
  cursor: pointer;
  flex-shrink: 0;
  transition: color 0.15s, background 0.15s;
}
.sprint-name-edit-icon:hover {
  color: var(--color-text-1);
  background: var(--color-fill-2);
}

/* 内联编辑输入框 */
.sprint-name-input {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-1);
  background: var(--color-bg-2);
  border: 1px solid rgb(var(--primary-6));
  border-radius: 4px;
  padding: 2px 8px;
  outline: none;
  min-width: 120px;
  max-width: 320px;
  width: auto;
  box-shadow: 0 0 0 2px rgba(var(--primary-6), 0.15);
  transition: box-shadow 0.15s;
}

.sprint-remaining {
  font-size: 12px;
  color: var(--color-text-2);
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: var(--color-fill-1);
  border-radius: 3px;
}
.sprint-remaining .remaining-icon.warning {
  color: rgb(var(--warning-6));
}
.sprint-remaining .remaining-icon.overdue {
  color: rgb(var(--danger-6));
}

.sprint-dates {
  font-size: 12px;
  color: var(--color-text-3);
}

/* ===== 进度区域 ===== */
.sprint-progress-section {
  margin-top: 12px;
  padding: 12px 0;
}

.progress-bar-container {
  display: flex;
  align-items: center;
  gap: 12px;
}

.progress-bar {
  flex: 1;
  height: 6px;
  background: var(--color-fill-2);
  border-radius: 3px;
  overflow: hidden;
  display: flex;
}

.progress-segment {
  height: 100%;
  transition: width 0.3s ease;
}
.progress-segment.done {
  background: #3fb950;
}
.progress-segment.in-progress {
  background: #58a6ff;
}
.progress-segment.todo {
  background: var(--color-fill-3);
}

.progress-percent {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-1);
  min-width: 36px;
  text-align: right;
}

.progress-stats {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.stat-item {
  font-size: 12px;
  color: var(--color-text-2);
  display: flex;
  align-items: center;
  gap: 4px;
}
.stat-item.total {
  color: var(--color-text-3);
  margin-left: auto;
}
.stat-item.overdue {
  color: rgb(var(--danger-6));
  font-weight: 500;
}
.stat-clickable {
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 3px;
  transition: background 0.15s;
}
.stat-clickable:hover {
  background: var(--color-fill-2);
}
.stat-item.unassigned {
  color: rgb(var(--warning-6));
  font-weight: 500;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 3px;
  transition: background 0.15s;
}
.stat-item.unassigned:hover {
  background: rgba(var(--warning-6), 0.08);
}

.stat-item.estimation {
  color: var(--color-text-2);
  font-weight: 500;
  padding: 2px 6px;
  background: var(--color-fill-1);
  border-radius: 3px;
}
.stat-item.estimation .stat-icon {
  font-size: 11px;
}

.stat-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.stat-item.done .stat-dot { background: #3fb950; }
.stat-item.in-progress .stat-dot { background: #58a6ff; }
.stat-item.todo .stat-dot { background: var(--color-fill-3); }
.stat-item.overdue .stat-dot { background: rgb(var(--danger-6)); }
.stat-item.unassigned .stat-dot { background: rgb(var(--warning-6)); }

/* ===== 其他 ===== */
.sprint-no-issues {
  margin-top: 12px;
  padding: 8px 0;
}
.no-issues-text {
  font-size: 12px;
  color: var(--color-text-3);
  font-style: italic;
}

.sprint-goal-banner {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 10px;
  padding: 8px 12px;
  background: rgba(var(--primary-6), 0.05);
  border-left: 2px solid rgba(var(--primary-6), 0.4);
  border-radius: 0 4px 4px 0;
  font-size: 13px;
  color: var(--color-text-2);
  line-height: 1.5;
}

.sprint-goal-banner-icon {
  font-size: 13px;
  flex-shrink: 0;
  margin-top: 1px;
}

.sprint-goal-banner-text {
  flex: 1;
  min-width: 0;
  word-break: break-word;
  white-space: pre-wrap;
}

.sprint-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.edit-modal-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.edit-modal-footer-left {
  display: flex;
  gap: 8px;
}

.edit-modal-footer-right {
  display: flex;
  gap: 8px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text-1);
  margin-bottom: 8px;
}

.empty-desc {
  font-size: 13px;
  color: var(--color-text-3);
  margin-bottom: 16px;
}

/* ===== 完成迭代弹窗 ===== */
.complete-no-issues {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 0;
  text-align: center;
}
.complete-icon {
  font-size: 36px;
  margin-bottom: 12px;
}
.complete-desc {
  font-size: 14px;
  color: var(--color-text-2);
}

.complete-with-issues {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.complete-warning {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: rgba(var(--warning-6), 0.08);
  border-radius: 6px;
  font-size: 13px;
  color: var(--color-text-1);
}
.warning-icon {
  font-size: 16px;
}

.open-issues-list {
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid var(--color-border);
  border-radius: 6px;
}
.open-issue-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 12px;
  border-bottom: 1px solid var(--color-border);
}
.open-issue-item:last-child {
  border-bottom: none;
}
.issue-status-tag {
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 500;
  flex-shrink: 0;
  white-space: nowrap;
}
.issue-key {
  color: var(--color-text-3);
  font-family: monospace;
  font-size: 11px;
  flex-shrink: 0;
}
.issue-title {
  color: var(--color-text-1);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.issue-assignee {
  color: var(--color-text-3);
  font-size: 11px;
  flex-shrink: 0;
}

.move-option-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.move-option-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
  margin: 0;
}
.radio-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
}
.radio-desc {
  display: block;
  font-size: 12px;
  color: var(--color-text-3);
  margin-top: 2px;
}
.radio-desc.disabled {
  color: var(--color-text-4);
  font-style: italic;
}
.target-sprint-select {
  margin-top: 8px;
  margin-left: 24px;
}
.target-status-tag {
  font-size: 11px;
  color: var(--color-text-3);
  margin-left: 8px;
}

.complete-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 0;
}

/* ===== 编辑弹窗提示 ===== */
.edit-completed-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--color-fill-1);
  border-radius: 6px;
  margin-top: 4px;
}
.hint-icon {
  font-size: 14px;
  flex-shrink: 0;
}
.hint-text {
  font-size: 12px;
  color: var(--color-text-3);
}

/* ===== Sprint 状态警告/提示 ===== */
.sprint-card.sprint-overdue {
  border-left-color: rgb(var(--danger-6));
}
.sprint-status-badge.active.overdue {
  background: rgba(var(--danger-6), 0.1);
  color: rgb(var(--danger-6));
}

.sprint-status-warning {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-top: 8px;
  background: rgba(var(--danger-6), 0.06);
  border: 1px solid rgba(var(--danger-6), 0.15);
  border-radius: 4px;
  font-size: 12px;
  color: rgb(var(--danger-6));
}
.sprint-status-warning .warning-icon {
  font-size: 13px;
  flex-shrink: 0;
}
.sprint-status-warning .warning-text {
  flex: 1;
}

.sprint-status-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-top: 8px;
  background: rgba(var(--primary-6), 0.06);
  border: 1px solid rgba(var(--primary-6), 0.15);
  border-radius: 4px;
  font-size: 12px;
  color: rgb(var(--primary-6));
}
.sprint-status-hint .hint-icon {
  font-size: 13px;
}
.sprint-status-hint .hint-text {
  flex: 1;
  color: rgb(var(--primary-6));
}

/* ===== 删除迭代弹窗 ===== */
.delete-no-issues,
.delete-with-issues {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.delete-warning-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: rgba(var(--warning-6), 0.08);
  border: 1px solid rgba(var(--warning-6), 0.2);
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  color: rgb(var(--warning-6));
}
.delete-warning-banner.danger {
  background: rgba(var(--danger-6), 0.08);
  border-color: rgba(var(--danger-6), 0.2);
  color: rgb(var(--danger-6));
}

.delete-desc {
  font-size: 14px;
  color: var(--color-text-1);
  margin: 0;
  line-height: 1.6;
}

.delete-hint {
  font-size: 12px;
  color: var(--color-text-3);
  margin: 0;
}

.delete-impact-info {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  background: var(--color-fill-1);
  border-radius: 6px;
  font-size: 13px;
  color: var(--color-text-1);
  line-height: 1.5;
}
.impact-icon {
  font-size: 14px;
  flex-shrink: 0;
  margin-top: 2px;
}

.delete-move-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ===== 创建迭代可选项 ===== */
.create-options-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 4px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
}

.create-option-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.create-option-item .option-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
}

.create-option-item .option-desc {
  font-size: 12px;
  color: var(--color-text-3);
  margin-left: 24px;
  line-height: 1.5;
}

/* ===== 无活跃 Sprint 警告条 ===== */
.sprint-guidance-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-radius: 6px;
  margin-bottom: 4px;
}
.sprint-guidance-banner.is-warning {
  background: rgba(var(--warning-6), 0.08);
  border: 1px solid rgba(var(--warning-6), 0.25);
}
.sprint-guidance-banner.is-info {
  background: rgba(var(--primary-6), 0.06);
  border: 1px solid rgba(var(--primary-6), 0.15);
}
.warning-bar-icon {
  font-size: 16px;
  flex-shrink: 0;
}
.warning-bar-text {
  flex: 1;
  font-size: 13px;
  color: var(--color-text-1);
  line-height: 1.4;
}
.warning-bar-action {
  flex-shrink: 0;
}

/* ===== 已完成 Sprint 折叠区域 ===== */
.completed-section {
  margin-top: 8px;
}
.completed-section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  border-radius: 4px;
  user-select: none;
  transition: background 0.15s;
}
.completed-section-header:hover {
  background: var(--color-fill-1);
}
.completed-toggle-icon {
  font-size: 11px;
  color: var(--color-text-3);
  width: 12px;
  text-align: center;
}
.completed-section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-3);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.completed-section-count {
  font-size: 11px;
  color: var(--color-text-4);
  background: var(--color-fill-2);
  padding: 1px 6px;
  border-radius: 8px;
}

/* ===== 日期重叠确认弹窗 ===== */
.overlap-warning-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.overlap-warning-header {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.overlap-warning-icon {
  font-size: 18px;
  line-height: 1.4;
}
.overlap-warning-title {
  font-size: 13px;
  color: var(--color-text-1);
  line-height: 1.5;
}
.overlap-sprint-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: var(--color-fill-1);
  border-radius: 6px;
}
.overlap-sprint-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 8px;
  background: var(--color-bg-2);
  border-radius: 4px;
}
.overlap-sprint-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.overlap-sprint-dates {
  font-size: 12px;
  color: var(--color-text-3);
  white-space: nowrap;
}
.overlap-sprint-status {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
  white-space: nowrap;
}
.overlap-sprint-status.active {
  color: var(--color-success-6);
  background: var(--color-success-1);
}
.overlap-sprint-status.planned {
  color: var(--color-primary-6);
  background: var(--color-primary-1);
}
.overlap-warning-hint {
  font-size: 12px;
  color: var(--color-text-3);
  line-height: 1.6;
}
.overlap-warning-hint p {
  margin: 0;
}

/* ===== 刚完成 Sprint 高亮动画 ===== */
.sprint-card.just-completed {
  animation: just-completed-highlight 3s ease-out;
  position: relative;
}

@keyframes just-completed-highlight {
  0% {
    border-color: rgb(var(--success-6));
    box-shadow: 0 0 0 3px rgba(var(--success-6), 0.25);
    background: rgba(var(--success-6), 0.06);
  }
  50% {
    border-color: rgb(var(--success-6));
    box-shadow: 0 0 0 2px rgba(var(--success-6), 0.15);
    background: rgba(var(--success-6), 0.04);
  }
  100% {
    border-color: var(--color-border);
    box-shadow: none;
    background: var(--color-bg-2);
  }
}
</style>
