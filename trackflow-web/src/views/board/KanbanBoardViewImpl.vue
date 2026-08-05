<template>
  <div class="kanban-page">
    <!-- 顶部工具栏 -->
    <div class="board-toolbar">
      <div class="toolbar-left">
        <h2 class="page-title">{{ displayBoardName }}</h2>
        <!-- 新建按钮（YouTrack 风格） -->
        <a-dropdown v-if="selectedProject && canCreateIssue" trigger="click" @select="onNewMenuSelect">
          <a-button type="primary" size="small">
            <template #icon><icon-plus /></template>
            新建...
          </a-button>
          <template #content>
            <a-doption value="card">
              <template #icon><icon-file /></template>
              新建卡片
            </a-doption>
            <a-doption value="sprint">
              <template #icon><icon-calendar /></template>
              新建 Sprint
            </a-doption>
          </template>
        </a-dropdown>
        <BoardSelector
          v-model="selectedProject"
          @update:model-value="onProjectChange"
        />
        <!-- Header Filter 输入框（YouTrack 风格：紧邻看板名称选择器） -->
        <div class="header-filter-wrapper">
          <a-input
            v-model="keyword"
            placeholder="Filter"
            size="small"
            style="width: 200px"
            allow-clear
            :disabled="!selectedProject"
            @input="onSearchInput"
            @press-enter="loadIssuesWithLoading"
            @clear="onSearchClear"
          >
            <template #prefix>
              <icon-search />
            </template>
          </a-input>
          <transition name="fade">
            <span v-if="isSearchActive" class="filter-active-badge">筛选中</span>
          </transition>
        </div>
        <a-divider direction="vertical" style="margin: 0 4px" />
        <a-select
          v-model="selectedSprint"
          placeholder="所有迭代"
          style="width: 220px"
          size="small"
          allow-clear
          :disabled="!selectedProject"
          @change="onSprintChange"
        >
          <a-option v-for="s in sprints" :key="s.id" :value="s.id" :class="{ 'sprint-option--active': isActiveSprint(s) }">
            <span class="sprint-option-content">
              <span class="sprint-option-name" :class="{ 'sprint-option-name--active': isActiveSprint(s) }">{{ s.name }}</span>
              <span v-if="isActiveSprint(s)" class="sprint-option-badge sprint-option-badge--active">当前</span>
              <span v-else-if="s.status === 'planned'" class="sprint-option-badge sprint-option-badge--planned">计划</span>
              <span v-else-if="s.status === 'completed'" class="sprint-option-badge sprint-option-badge--completed">完成</span>
              <span v-else-if="s.status === 'archived'" class="sprint-option-badge sprint-option-badge--archived">归档</span>
            </span>
          </a-option>
        </a-select>
        <!-- Sprint 日期范围 + 剩余天数 -->
        <span v-if="currentSelectedSprint && currentSelectedSprint.startDate" class="sprint-date-info">
          <span class="sprint-date-range">{{ formatSprintDateRange(currentSelectedSprint.startDate, currentSelectedSprint.endDate) }}</span>
          <span v-if="sprintRemainingDays !== null" class="sprint-countdown" :class="{ 'sprint-countdown--urgent': sprintRemainingDays <= 3, 'sprint-countdown--overdue': sprintRemainingDays < 0 }">
            <template v-if="sprintRemainingDays > 0">· 剩余 {{ sprintRemainingDays }} 天</template>
            <template v-else-if="sprintRemainingDays === 0">· 今天结束</template>
            <template v-else>· 已超期 {{ Math.abs(sprintRemainingDays) }} 天</template>
          </span>
          <a class="sprint-detail-link" @click="goToSprintDetail" title="查看迭代详情">📊</a>
        </span>
        <!-- No active sprint hint with next sprint info -->
        <span v-else-if="selectedProject && sprints.length > 0 && !activeSprint && !selectedSprint" class="sprint-no-active-hint">
          暂无活跃迭代
          <span v-if="nextPlannedSprintHint" class="sprint-next-hint">· {{ nextPlannedSprintHint }}</span>
        </span>
        <a-divider direction="vertical" style="margin: 0 4px" />
        <!-- Swimlane 分组选择 -->
        <a-select
          v-model="swimlaneGroupBy"
          placeholder="分组"
          style="width: 140px"
          size="small"
          :disabled="!selectedProject"
          @change="onSwimlaneChange"
        >
          <a-option value="none">无分组</a-option>
          <a-option value="assignee">按负责人</a-option>
          <a-option value="priority">按优先级</a-option>
          <a-option value="type">按类型</a-option>
          <a-option value="sprint">按迭代</a-option>
          <a-option value="tag">按标签</a-option>
          <a-option value="parent">按父工单</a-option>
          <a-option value="dueDate">按截止日期</a-option>
        </a-select>
      </div>
      <div class="toolbar-right">
        <!-- Progress Indicator (各列卡片数 mini bar chart) — 仅活跃状态 -->
        <div v-if="selectedProject && visibleStatuses.length > 0" class="progress-indicator" role="img" :aria-label="progressIndicatorAriaLabel">
          <!-- 活跃状态柱形 -->
          <div
            v-for="status in activeStatuses"
            :key="status.id"
            class="progress-bar"
            :class="{ 'progress-bar--collapsed': isColumnCollapsed(effectiveColumns.find(c => c.statusIds.includes(status.id))?.id || status.id) }"
            :style="{ height: getProgressBarHeight(status.id), backgroundColor: status.color || 'var(--color-fill-4)' }"
            :title="`${localizeStatusName(status.name)}：${getColumnIssues(status.id).length} 个工单`"
            @click="scrollToColumn(status.id)"
          ></div>
          <!-- 终态汇总柱形（灰色弱化） -->
          <div
            v-if="closedIssueCount > 0"
            class="progress-bar progress-bar--closed"
            :style="{ height: getClosedProgressBarHeight() }"
            :title="`已完成/已取消：${closedIssueCount} 个工单（${closedIssueDetail}）`"
          ></div>
        </div>
        <a-divider v-if="selectedProject && visibleStatuses.length > 0" direction="vertical" style="margin: 0 4px" />
        <!-- Estimation 指示器 -->
        <div v-if="selectedProject && boardTotalEstimation > 0" class="estimation-indicator" :title="'看板总预估工时: ' + boardTotalEstimation + 'h'">
          <span class="estimation-icon">⏱</span>
          <span class="estimation-value">{{ boardTotalEstimation % 1 === 0 ? boardTotalEstimation : boardTotalEstimation.toFixed(1) }}h</span>
        </div>
        <a-divider v-if="selectedProject && boardTotalEstimation > 0" direction="vertical" style="margin: 0 4px" />
        <!-- Card Size 选择器 -->
        <div class="card-size-group" role="group" aria-label="卡片尺寸">
          <button
            v-for="size in cardSizeOptions"
            :key="size.value"
            class="card-size-btn"
            :class="{ 'card-size-btn--active': cardSize === size.value }"
            :title="`卡片尺寸: ${size.label}`"
            :aria-pressed="cardSize === size.value"
            @click="setCardSize(size.value)"
          >{{ size.label }}</button>
        </div>
        <a-divider direction="vertical" style="margin: 0 4px" />
        <!-- 负责人筛选 -->
        <div class="assignee-filter-group">
          <button
            class="my-issues-btn"
            :class="{ 'my-issues-btn--active': assigneeFilter === 'me' }"
            :aria-pressed="assigneeFilter === 'me'"
            title="仅显示我的工单"
            @click="toggleMyIssues"
          >
            👤 仅我的
          </button>
          <a-select
            v-model="assigneeFilter"
            placeholder="负责人"
            size="small"
            style="width: 130px"
            allow-clear
            :disabled="!selectedProject"
            @change="onAssigneeFilterChange"
          >
            <a-option value="me">👤 我的</a-option>
            <a-option v-for="m in projectMembers" :key="m.userId" :value="m.userId">
              {{ m.displayName }}
            </a-option>
          </a-select>
        </div>
        <a-divider direction="vertical" style="margin: 0 4px" />
        <!-- Board Behavior 过滤指示器 -->
        <div v-if="isBehaviorFilterActive && selectedProject" class="behavior-filter-chips">
          <span v-if="boardFilterMode === 'active_sprint'" class="behavior-chip">
            🏃 仅活跃 Sprint
          </span>
          <span v-if="boardFilterMode === 'query'" class="behavior-chip">
            🔍 查询过滤
          </span>
          <span v-if="boardDoneRetentionDays !== null" class="behavior-chip">
            ✅ 完成{{ boardDoneRetentionDays }}天内
          </span>
          <span v-else-if="isSmartDefaultDoneRetentionActive" class="behavior-chip behavior-chip--auto">
            ✅ 自动隐藏14天前完成
          </span>
        </div>
        <!-- 未解决/未匹配列工单入口 -->
        <a-tooltip v-if="selectedProject && orphanIssueCount > 0" :content="`${orphanIssueCount} 个工单未匹配看板列`">
          <a-button
            size="small"
            status="warning"
            @click="showOrphanPanel = !showOrphanPanel"
          >
            ⚠️ {{ orphanIssueCount }}
          </a-button>
        </a-tooltip>
        <a-divider v-if="selectedProject && orphanIssueCount > 0" direction="vertical" style="margin: 0 4px" />
        <!-- TV 模式按钮 -->
        <a-tooltip :content="isTvMode ? '退出 TV 模式' : 'TV 模式（大屏显示）'">
          <a-button
            size="small"
            :type="isTvMode ? 'primary' : 'secondary'"
            :disabled="!selectedProject"
            @click="toggleTvMode"
          >
            📺 TV
          </a-button>
        </a-tooltip>
        <a-tooltip :content="showBacklog ? '收起 Backlog' : '展开 Backlog'">
          <a-button
            size="small"
            :type="showBacklog ? 'primary' : 'secondary'"
            :disabled="!selectedProject"
            @click="toggleBacklog"
          >
            <template #icon><icon-list /></template>
            Backlog
          </a-button>
        </a-tooltip>
        <a-tooltip :content="showChart ? '收起图表' : '展开图表'">
          <a-button
            size="small"
            :type="showChart ? 'primary' : 'secondary'"
            :disabled="!selectedProject"
            @click="showChart = !showChart"
          >
            <template #icon><icon-bar-chart /></template>
            Chart
          </a-button>
        </a-tooltip>
        <a-tooltip v-if="canEditBoard" content="看板列设置">
          <a-button
            size="small"
            :disabled="!selectedProject"
            @click="showSettings = true"
          >
            <template #icon><icon-settings /></template>
          </a-button>
        </a-tooltip>
        <a-tooltip v-if="selectedProject" content="克隆看板">
          <a-button
            size="small"
            :disabled="!selectedProject"
            @click="openCloneModal"
          >
            <template #icon><icon-copy /></template>
          </a-button>
        </a-tooltip>
      </div>
    </div>

    <!-- 图表面板（展开/折叠） -->
    <BoardChartPanel
      :visible="showChart"
      :project-id="selectedProject || ''"
      :sprint-id="selectedSprint"
      :chart-type="boardChartType"
      :burndown-calculation="boardBurndownCalculation"
      :estimation-field-id="cardConfig.currentEstimationFieldId"
      @close="showChart = false"
    />

    <!-- 加载状态 -->
    <a-spin :loading="loading" tip="加载看板数据..." class="board-spin">
      <!-- 无活跃 Sprint 引导横幅 -->
      <div v-if="showNoActiveSprintGuidance && !loading" class="board-guidance-banner">
        <div class="guidance-icon">📋</div>
        <div class="guidance-content">
          <div class="guidance-title">当前没有活跃迭代</div>
          <div class="guidance-desc">
            <template v-if="nextPlannedSprint">
              下一个迭代「{{ nextPlannedSprint.name }}」将于 {{ formatSprintDate(nextPlannedSprint.startDate) }} 开始。
            </template>
            <template v-else>
              暂无计划中的迭代。
            </template>
            <span v-if="!effectiveDoneRetentionDays">已自动隐藏超过 14 天的已完成工单。</span>
          </div>
        </div>
        <div class="guidance-actions">
          <a-button v-if="nextPlannedSprint" size="mini" type="primary" @click="selectNextPlannedSprint">
            查看 {{ nextPlannedSprint.name }}
          </a-button>
          <a-button size="mini" type="outline" @click="dismissGuidance">知道了</a-button>
        </div>
      </div>
      <!-- 截断提示：工单数超过安全上限 -->
      <div v-if="boardTruncated && !loading" class="board-truncated-banner">
        <span class="truncated-icon">⚠️</span>
        <span class="truncated-text">
          当前项目共 {{ boardTotalCount }} 个工单，看板仅展示前 {{ issues.length }} 个。请使用搜索或筛选缩小范围。
        </span>
      </div>
      <!-- 隐藏列中有工单的警告提示（增强版 Banner — REQ-753） -->
      <div v-if="hiddenIssueColumns.length > 0 && !loading && !showAllColumns" class="board-hidden-issues-banner board-hidden-issues-banner--enhanced">
        <div class="hidden-issues-header">
          <span class="hidden-issues-icon">⚠️</span>
          <span class="hidden-issues-text">
            <strong>{{ hiddenIssueTotalCount }} 个工单</strong>处于未显示的状态列中
          </span>
        </div>
        <div class="hidden-issues-tags">
          <button
            v-for="col in hiddenIssueColumns"
            :key="col.fieldValue || col.statusId"
            class="hidden-status-tag"
            :style="col.statusColor ? { '--tag-color': col.statusColor } : {}"
            :title="`查看「${localizeStatusName(col.statusName)}」状态下的 ${col.issueCount || 0} 个工单`"
            @click="onHiddenStatusTagClick(col)"
          >
            <span class="hidden-status-tag-dot" :style="{ background: col.statusColor || 'var(--color-fill-4)' }"></span>
            <span class="hidden-status-tag-name">{{ localizeStatusName(col.statusName) }}</span>
            <span class="hidden-status-tag-count">{{ col.issueCount || 0 }}</span>
          </button>
        </div>
        <div class="hidden-issues-actions">
          <a-button size="mini" type="primary" @click="showAllColumns = true">
            显示全部列 →
          </a-button>
          <a-button v-if="canEditBoard" size="mini" type="text" @click="showSettings = true">列设置</a-button>
        </div>
      </div>
      <!-- 临时显示全部列时的收起提示 -->
      <div v-if="showAllColumns && hiddenIssueColumns.length > 0 && !loading" class="board-hidden-issues-banner board-hidden-issues-banner--info">
        <span class="hidden-issues-icon">ℹ️</span>
        <span class="hidden-issues-text">
          已临时显示全部列（含 {{ hiddenIssueColumns.length }} 个隐藏列）。
        </span>
        <div class="hidden-issues-actions">
          <a-button size="mini" type="outline" @click="showAllColumns = false">恢复配置</a-button>
        </div>
      </div>
      <div class="board-main-area">
        <!-- Backlog 面板 -->
        <BacklogPanel
          ref="backlogPanelRef"
          :visible="showBacklog"
          :project-id="selectedProject || ''"
          :board-status-ids="boardStatusIdsForBacklog"
          :filter-keyword="keyword"
          :view-mode="backlogViewMode"
          :saved-query-id="backlogSavedQueryId"
          @close="showBacklog = false"
          @open-issue="openIssue"
          @drag-start="onBacklogDragStart"
          @drag-end="onBacklogDragEnd"
        />
        <!-- 未匹配列工单面板 -->
        <transition name="slide-right">
          <div v-if="showOrphanPanel && orphanIssueCount > 0" class="orphan-panel">
            <div class="orphan-panel-header">
              <span class="orphan-panel-title">⚠️ 未匹配列的工单（{{ orphanIssueCount }}）</span>
              <a-button size="mini" type="text" @click="showOrphanPanel = false">✕</a-button>
            </div>
            <div class="orphan-panel-desc">
              以下工单已分配到看板，但其状态不匹配任何可见列。
            </div>
            <div class="orphan-panel-list">
              <div
                v-for="issue in orphanIssues"
                :key="issue.id"
                class="orphan-issue-item"
                @click="openIssue(issue)"
              >
                <span class="orphan-issue-key">{{ issue.issueKey }}</span>
                <span class="orphan-issue-title">{{ issue.title }}</span>
                <span v-if="issue.statusName" class="orphan-issue-status">{{ issue.statusName }}</span>
              </div>
            </div>
          </div>
        </transition>
      <!-- ===== 无分组模式（原始平面看板） ===== -->
      <div
        v-if="selectedProject && visibleStatuses.length > 0 && !showNoSearchResults && !showSprintModeNoActiveState && swimlaneGroupBy === 'none'"
        class="board-container"
      >
        <template v-for="col in effectiveColumns" :key="col.id">
          <!-- 展开状态的列（有工单/手动展开空列/非手动折叠） -->
          <div
            v-if="!isColumnCollapsed(col.id)"
            class="board-column"
            :data-column-id="col.id"
            :class="{
              'board-column--expanded-empty': getEffectiveColumnIssues(col).length === 0,
              'board-column--drop-target': col.statusIds.includes(dragOverColumnId || '') && !dragOverSwimlaneKey,
              'board-column--drop-forbidden': col.statusIds.includes(dragOverColumnId || '') && !dragOverSwimlaneKey && !isEffectiveColumnDropAllowed(col)
            }"
            @dragover="onDragOver($event, getDropTargetStatusId(col))"
            @dragleave="onDragLeave($event)"
            @drop="onDrop($event, getDropTargetStatusId(col))"
          >
            <div
              class="column-header column-header--clickable"
              :style="{ borderTopColor: col.color }"
              role="button"
              tabindex="0"
              :aria-label="`折叠 ${col.name} 列`"
              title="点击折叠此列"
              @click="toggleColumnCollapse(col.id)"
              @keydown.enter="toggleColumnCollapse(col.id)"
            >
              <span class="column-title">{{ col.name }}</span>
              <span
                class="column-count"
                :class="getEffectiveColumnWipClass(col)"
                :title="col.isMerged ? undefined : getWipTooltip(col.id)"
              >{{ getEffectiveColumnIssues(col).length }}<template v-if="getEffectiveColumnWipMax(col) !== null">/{{ getEffectiveColumnWipMax(col) }}</template></span>
              <span v-if="getEffectiveColumnEstimation(col)" class="column-estimation" :title="'预估工时总计: ' + getEffectiveColumnEstimation(col) + 'h'">⏱ {{ getEffectiveColumnEstimation(col) }}h</span>
              <span v-if="getEffectiveColumnWipWarning(col)" class="wip-warning" :class="getEffectiveColumnWipWarning(col)">
                {{ getEffectiveColumnWipWarning(col) === 'wip-over' ? '⚠' : '▽' }}
              </span>
              <!-- 在工单列表中打开（hover 时显示，YouTrack 对标功能） -->
              <a
                class="column-open-in-list"
                :href="buildOpenInListUrl(col)"
                target="_blank"
                rel="noopener noreferrer"
                :title="`在工单列表中打开「${col.name}」`"
                :aria-label="`在工单列表中打开「${col.name}」`"
                @click.stop
              >
                <svg width="13" height="13" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
                  <path d="M3.75 2h3.5a.75.75 0 0 1 0 1.5h-3.5a.25.25 0 0 0-.25.25v8.5c0 .138.112.25.25.25h8.5a.25.25 0 0 0 .25-.25v-3.5a.75.75 0 0 1 1.5 0v3.5A1.75 1.75 0 0 1 12.25 14h-8.5A1.75 1.75 0 0 1 2 12.25v-8.5C2 2.784 2.784 2 3.75 2Zm6.854-1h4.146a.25.25 0 0 1 .25.25v4.146a.25.25 0 0 1-.427.177L13.03 4.03 9.28 7.78a.751.751 0 0 1-1.042-.018.751.751 0 0 1-.018-1.042l3.75-3.75-1.543-1.543A.25.25 0 0 1 10.604 1Z"/>
                </svg>
              </a>
            </div>
            <div class="column-body">
              <div
                v-for="issue in getEffectiveColumnIssues(col)"
                :key="issue.id"
                class="kanban-card"
                :class="[
                  `kanban-card--${cardSize}`,
                  getCardColorClass(issue),
                  {
                    'kanban-card--dragging': draggingIssue?.id === issue.id,
                    'kanban-card--transitioning': transitioningIssueIds.has(issue.id),
                    'kanban-card--no-drag': !isCardDraggable(issue),
                    'kanban-card--selected': selectedIds.has(issue.id)
                  }
                ]"
                :style="getCardProjectColorStyle(issue)"
                role="button"
                tabindex="0"
                :draggable="isCardDraggable(issue)"
                @dragstart="onDragStart($event, issue)"
                @dragend="onDragEnd"
                @click="onCardClick($event, issue)"
                @dblclick="onCardDblClick(issue)"
                @keydown="onCardKeydown($event, issue)"
              >
                <div class="card-header">
                  <!-- 多项目看板时显示项目 Key 标记 -->
                  <span
                    v-if="isMultiProjectBoard && issue.projectKey"
                    class="card-project-tag"
                    :title="`来自项目 ${issue.projectKey}`"
                  >{{ issue.projectKey }}</span>
                  <span class="card-key">{{ issue.issueKey }}</span>
                  <span
                    v-if="isCardFieldVisible('priority')"
                    class="card-priority"
                    :class="issue.priority?.toLowerCase()"
                    :title="localizePriority(issue.priority)"
                  >
                    {{ priorityIcon(issue.priority) }}
                  </span>
                </div>
                <div class="card-title" :class="`card-title--${cardSize}`">{{ issue.title }}</div>
                <!-- M/L: custom fields -->
                <div v-if="cardSize !== 'S' && hasVisibleCustomFields(issue)" class="card-custom-fields">
                  <template v-for="detail in getVisibleCustomFieldDetails(issue)" :key="detail.customFieldId">
                    <template v-if="detail.isMulti && detail.displayValues">
                      <span v-for="(dv, idx) in detail.displayValues" :key="idx" class="card-cf-tag" :style="cardConfig.showCustomFieldColors !== false && detail.colors?.[idx] ? { background: detail.colors[idx], color: '#fff' } : {}">{{ dv }}</span>
                    </template>
                    <span v-else-if="cardConfig.showCustomFieldColors !== false && detail.color" class="card-cf-tag" :style="{ background: detail.color, color: '#fff' }">{{ detail.displayValue }}</span>
                    <span v-else class="card-cf-tag">{{ detail.displayValue }}</span>
                  </template>
                </div>
                <!-- Card metadata fields based on card config -->
                <div v-if="cardSize !== 'S' && (isCardFieldVisible('dueDate') || isCardFieldVisible('sprint') || isCardFieldVisible('estimatedHours') || isCardFieldVisible('tags'))" class="card-meta-fields">
                  <a-tooltip v-if="isCardFieldVisible('dueDate') && issue.dueDate && getCardDueDateClass(issue)" :content="getCardDueDateTooltip(issue)" position="top" mini>
                    <span class="card-meta-tag" :class="getCardDueDateClass(issue)">📅 {{ issue.dueDate.slice(5) }}</span>
                  </a-tooltip>
                  <span v-else-if="isCardFieldVisible('dueDate') && issue.dueDate" class="card-meta-tag">📅 {{ issue.dueDate.slice(5) }}</span>
                  <span v-if="isCardFieldVisible('sprint') && issue.sprintId" class="card-meta-tag">🏃 {{ getSprintName(issue.sprintId) }}</span>
                  <span v-if="isCardFieldVisible('estimatedHours') && issue.estimatedHours" class="card-meta-tag">⏱ {{ issue.estimatedHours }}h</span>
                  <template v-if="isCardFieldVisible('tags') && (issue as any).tags && (issue as any).tags.length > 0">
                    <span v-for="tag in getVisibleTags(issue)" :key="tag.id" class="card-tag" :style="tag.color ? { background: tag.color, color: '#fff' } : {}">{{ tag.name }}</span>
                  </template>
                </div>
                <div class="card-footer">
                  <span v-if="isCardFieldVisible('type')" class="card-type">
                    <template v-if="getCardFieldDisplayMode('type') === 'initial'">{{ typeInitial(issue.issueType) }}</template>
                    <template v-else>{{ typeLabel(issue.issueType) }}</template>
                  </span>
                  <span v-else class="card-type-spacer"></span>
                  <!-- 已分配：显示头像 -->
                  <div class="card-assignee-avatar" v-if="isCardFieldVisible('assignee') && issue.assigneeName" :title="issue.assigneeName">
                    <template v-if="getCardFieldDisplayMode('assignee') === 'full_name'">
                      <span class="assignee-full-name">{{ issue.assigneeName }}</span>
                    </template>
                    <template v-else>
                      <img
                        v-if="issue.assigneeAvatarUrl"
                        :src="issue.assigneeAvatarUrl"
                        :alt="issue.assigneeName"
                        class="avatar-img"
                      />
                      <span v-else class="avatar-initials">{{ getInitials(issue.assigneeName) }}</span>
                    </template>
                  </div>
                  <!-- 未分配 + 有权限：显示 Set assignee 按钮 -->
                  <a-dropdown
                    v-else-if="isCardFieldVisible('assignee') && !issue.assigneeName && canCreateIssue && projectMembers.length > 0"
                    trigger="click"
                    @select="(userId: any) => onCardSetAssignee(userId, issue)"
                  >
                    <button
                      class="set-assignee-btn"
                      :title="'分配负责人'"
                      @click.stop
                    >
                      <icon-user class="set-assignee-icon" />
                    </button>
                    <template #content>
                      <a-doption v-for="m in projectMembers" :key="m.userId" :value="m.userId">
                        {{ m.displayName }}
                      </a-doption>
                    </template>
                  </a-dropdown>
                </div>
              </div>
              <div
                v-if="getEffectiveColumnIssues(col).length === 0"
                class="column-empty-state"
                :class="{ 'column-empty-state--drop-hint': isDragging && isEffectiveColumnDropAllowed(col) }"
              >
                <template v-if="isDragging && isEffectiveColumnDropAllowed(col)">
                  <div class="column-empty-icon">📥</div>
                  <div class="column-empty-text">释放以移动到此状态</div>
                </template>
                <template v-else-if="isDragging && !isEffectiveColumnDropAllowed(col)">
                  <div class="column-empty-icon">🚫</div>
                  <div class="column-empty-text">不允许转换到此状态</div>
                </template>
                <template v-else>
                  <div class="column-empty-icon">📭</div>
                  <div class="column-empty-text">该状态下暂无工单</div>
                  <div class="column-empty-hint">{{ isEffectiveColumnClosed(col) ? '拖拽工单到此列' : '拖拽工单到此列或创建新工单' }}</div>
                </template>
              </div>
              <!-- 内联快速创建卡片（合并列：使用第一个 statusId 创建） -->
              <div
                v-if="canCreateIssue && !isDragging && !isEffectiveColumnClosed(col)"
                class="add-card-area"
              >
                <div
                  v-if="addingCardColumnId === getDropTargetStatusId(col) && !addingCardSwimlaneKey"
                  class="add-card-form"
                >
                  <input
                    :ref="(el) => setAddCardInputRef(el, getDropTargetStatusId(col), '')"
                    v-model="addCardTitle"
                    class="add-card-input"
                    placeholder="输入工单标题，回车创建"
                    :disabled="addCardSubmitting"
                    @keydown.enter.prevent="submitAddCard(getDropTargetStatusId(col))"
                    @keydown.escape="cancelAddCard"
                    @blur="onAddCardBlur"
                  />
                  <div class="add-card-form-actions">
                    <a-select
                      v-model="addCardType"
                      size="mini"
                      style="width: 80px"
                    >
                      <a-option value="Task">任务</a-option>
                      <a-option value="Bug">缺陷</a-option>
                      <a-option value="Feature">需求</a-option>
                    </a-select>
                    <a-button
                      size="mini"
                      type="primary"
                      :loading="addCardSubmitting"
                      @mousedown.prevent
                      @click="submitAddCard(getDropTargetStatusId(col))"
                    >创建</a-button>
                    <a-button
                      size="mini"
                      @mousedown.prevent
                      @click="cancelAddCard"
                    >取消</a-button>
                  </div>
                </div>
                <button
                  v-else
                  class="add-card-btn"
                  @click="startAddCard(getDropTargetStatusId(col))"
                >
                  <span class="add-card-icon">+</span>
                  <span class="add-card-text">添加卡片</span>
                </button>
              </div>
            </div>
          </div>

          <!-- 折叠的列：窄条 -->
          <div
            v-else
            class="board-column-collapsed"
            :data-column-id="col.id"
            :class="{
              'board-column-collapsed--drop-target': col.statusIds.includes(dragOverColumnId || '') && isEffectiveColumnDropAllowed(col),
              'board-column-collapsed--drop-forbidden': col.statusIds.includes(dragOverColumnId || '') && !isEffectiveColumnDropAllowed(col)
            }"
            role="button"
            tabindex="0"
            :aria-label="`${col.name}，${getEffectiveColumnIssues(col).length} 个工单，点击展开`"
            :title="`${col.name} (${getEffectiveColumnIssues(col).length} 工单) - ${isDragging ? '释放以移动' : '点击展开'}`"
            @click="!isDragging && toggleColumnCollapse(col.id)"
            @keydown.enter="toggleColumnCollapse(col.id)"
            @dragover="onDragOver($event, getDropTargetStatusId(col))"
            @dragleave="onDragLeave($event)"
            @drop="onDrop($event, getDropTargetStatusId(col))"
          >
            <div class="collapsed-indicator" :style="{ backgroundColor: col.color || 'var(--color-border)' }"></div>
            <span class="collapsed-name">{{ col.name }}</span>
            <span class="collapsed-count">{{ getEffectiveColumnIssues(col).length }}</span>
          </div>
        </template>
      </div>

      <!-- ===== Swimlane 分组模式 ===== -->
      <div
        v-if="selectedProject && visibleStatuses.length > 0 && !showNoSearchResults && !showSprintModeNoActiveState && swimlaneGroupBy !== 'none'"
        class="swimlane-container"
      >
        <!-- Swimlane 表头（状态列标题） -->
        <div class="swimlane-header">
          <div class="swimlane-label-cell"></div>
          <div class="swimlane-columns-header">
            <div
              v-for="col in effectiveColumns"
              :key="col.id"
              class="swimlane-col-header"
              :class="{ 'swimlane-col-header--collapsed': collapsedColumns.has(col.id) }"
              :style="{ borderTopColor: col.color }"
              role="button"
              tabindex="0"
              :title="collapsedColumns.has(col.id) ? '点击展开此列' : '点击折叠此列'"
              @click="toggleColumnCollapse(col.id)"
              @keydown.enter="toggleColumnCollapse(col.id)"
            >
              <span class="column-title">{{ col.name }}</span>
              <span
                v-if="!collapsedColumns.has(col.id)"
                class="column-count"
                :class="getEffectiveColumnWipClass(col)"
                :title="col.isMerged ? undefined : getWipTooltip(col.id)"
              >{{ getEffectiveColumnIssues(col).length }}<template v-if="getEffectiveColumnWipMax(col) !== null">/{{ getEffectiveColumnWipMax(col) }}</template></span>
              <span
                v-else
                class="column-count"
              >{{ getEffectiveColumnIssues(col).length }}</span>
              <span v-if="!collapsedColumns.has(col.id) && getEffectiveColumnWipWarning(col)" class="wip-warning" :class="getEffectiveColumnWipWarning(col)">
                {{ getEffectiveColumnWipWarning(col) === 'wip-over' ? '⚠' : '▽' }}
              </span>
              <!-- 在工单列表中打开（hover 时显示，YouTrack 对标功能） -->
              <a
                v-if="!collapsedColumns.has(col.id)"
                class="column-open-in-list"
                :href="buildOpenInListUrl(col)"
                target="_blank"
                rel="noopener noreferrer"
                :title="`在工单列表中打开「${col.name}」`"
                :aria-label="`在工单列表中打开「${col.name}」`"
                @click.stop
              >
                <svg width="13" height="13" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
                  <path d="M3.75 2h3.5a.75.75 0 0 1 0 1.5h-3.5a.25.25 0 0 0-.25.25v8.5c0 .138.112.25.25.25h8.5a.25.25 0 0 0 .25-.25v-3.5a.75.75 0 0 1 1.5 0v3.5A1.75 1.75 0 0 1 12.25 14h-8.5A1.75 1.75 0 0 1 2 12.25v-8.5C2 2.784 2.784 2 3.75 2Zm6.854-1h4.146a.25.25 0 0 1 .25.25v4.146a.25.25 0 0 1-.427.177L13.03 4.03 9.28 7.78a.751.751 0 0 1-1.042-.018.751.751 0 0 1-.018-1.042l3.75-3.75-1.543-1.543A.25.25 0 0 1 10.604 1Z"/>
                </svg>
              </a>
            </div>
          </div>
        </div>

        <!-- Swimlane 各行 -->
        <div class="swimlane-body">
          <div
            v-for="lane in orderedSwimlanes"
            :key="lane.key"
            class="swimlane-row"
            :class="{
              'swimlane-row--collapsed': collapsedSwimlanes.has(lane.key),
              'swimlane-row--drag-over': swimlaneDragOverKey === lane.key,
              'swimlane-row--dragging': swimlaneDraggingKey === lane.key
            }"
            :draggable="!isDragging"
            @dragstart="onSwimlaneRowDragStart($event, lane.key)"
            @dragend="onSwimlaneRowDragEnd"
            @dragover="onSwimlaneRowDragOver($event, lane.key)"
            @dragleave="onSwimlaneRowDragLeave"
            @drop="onSwimlaneRowDrop($event, lane.key)"
          >
            <!-- 泳道行标题 -->
            <div
              class="swimlane-row-header"
              role="button"
              tabindex="0"
              @click="toggleSwimlane(lane.key)"
              @keydown.enter="toggleSwimlane(lane.key)"
            >
              <!-- 拖拽把手（YouTrack 风格：左侧 ≡ 图标） -->
              <span
                class="swimlane-drag-handle"
                title="拖拽以调整泳道顺序"
                aria-label="拖拽把手"
                @mousedown.stop
                @click.stop
              >
                ≡
              </span>
              <span class="swimlane-toggle-icon">
                {{ collapsedSwimlanes.has(lane.key) ? '▶' : '▼' }}
              </span>
              <span class="swimlane-row-label">{{ lane.label }}</span>
              <span class="swimlane-row-count">{{ lane.issues.length }}</span>
            </div>

            <!-- 泳道行内容（状态列 × 卡片） -->
            <div v-show="!collapsedSwimlanes.has(lane.key)" class="swimlane-row-body">
              <div class="swimlane-label-cell"></div>
              <div class="swimlane-columns">
                <div
                  v-for="col in effectiveColumns"
                  :key="col.id"
                  class="swimlane-cell"
                  :class="{
                    'swimlane-cell--collapsed': collapsedColumns.has(col.id),
                    'swimlane-cell--drop-target': col.statusIds.includes(dragOverColumnId || '') && dragOverSwimlaneKey === lane.key,
                    'swimlane-cell--drop-forbidden': col.statusIds.includes(dragOverColumnId || '') && dragOverSwimlaneKey === lane.key && !isEffectiveColumnDropAllowed(col)
                  }"
                  @dragover="onDragOverSwimlane($event, getDropTargetStatusId(col), lane.key)"
                  @dragleave="onDragLeaveSwimlane($event)"
                  @drop="onDrop($event, getDropTargetStatusId(col))"
                >
                  <template v-if="!collapsedColumns.has(col.id)">
                    <div
                      v-for="issue in getSwimlaneEffectiveColumnIssues(lane.key, col)"
                      :key="issue.id"
                      class="kanban-card"
                      :class="[
                        `kanban-card--${cardSize}`,
                        getCardColorClass(issue),
                        {
                          'kanban-card--dragging': draggingIssue?.id === issue.id,
                          'kanban-card--transitioning': transitioningIssueIds.has(issue.id),
                          'kanban-card--no-drag': !isCardDraggable(issue),
                          'kanban-card--selected': selectedIds.has(issue.id)
                        }
                      ]"
                      :style="getCardProjectColorStyle(issue)"
                      role="button"
                      tabindex="0"
                      :draggable="isCardDraggable(issue)"
                      @dragstart="onDragStart($event, issue)"
                      @dragend="onDragEnd"
                      @click="onCardClick($event, issue)"
                      @dblclick="onCardDblClick(issue)"
                      @keydown="onCardKeydown($event, issue)"
                    >
                      <div class="card-header">
                        <!-- 多项目看板时显示项目 Key 标记 -->
                        <span
                          v-if="isMultiProjectBoard && issue.projectKey"
                          class="card-project-tag"
                          :title="`来自项目 ${issue.projectKey}`"
                        >{{ issue.projectKey }}</span>
                        <span class="card-key">{{ issue.issueKey }}</span>
                        <span
                          v-if="isCardFieldVisible('priority')"
                          class="card-priority"
                          :class="issue.priority?.toLowerCase()"
                          :title="localizePriority(issue.priority)"
                        >
                          {{ priorityIcon(issue.priority) }}
                        </span>
                      </div>
                      <div class="card-title" :class="`card-title--${cardSize}`">{{ issue.title }}</div>
                      <!-- M/L: custom fields -->
                      <div v-if="cardSize !== 'S' && hasVisibleCustomFields(issue)" class="card-custom-fields">
                        <template v-for="detail in getVisibleCustomFieldDetails(issue)" :key="detail.customFieldId">
                          <template v-if="detail.isMulti && detail.displayValues">
                            <span v-for="(dv, idx) in detail.displayValues" :key="idx" class="card-cf-tag" :style="cardConfig.showCustomFieldColors !== false && detail.colors?.[idx] ? { background: detail.colors[idx], color: '#fff' } : {}">{{ dv }}</span>
                          </template>
                          <span v-else-if="cardConfig.showCustomFieldColors !== false && detail.color" class="card-cf-tag" :style="{ background: detail.color, color: '#fff' }">{{ detail.displayValue }}</span>
                          <span v-else class="card-cf-tag">{{ detail.displayValue }}</span>
                        </template>
                      </div>
                      <!-- Card metadata fields based on card config -->
                      <div v-if="cardSize !== 'S' && (isCardFieldVisible('dueDate') || isCardFieldVisible('sprint') || isCardFieldVisible('estimatedHours') || isCardFieldVisible('tags'))" class="card-meta-fields">
                        <a-tooltip v-if="isCardFieldVisible('dueDate') && issue.dueDate && getCardDueDateClass(issue)" :content="getCardDueDateTooltip(issue)" position="top" mini>
                          <span class="card-meta-tag" :class="getCardDueDateClass(issue)">📅 {{ issue.dueDate.slice(5) }}</span>
                        </a-tooltip>
                        <span v-else-if="isCardFieldVisible('dueDate') && issue.dueDate" class="card-meta-tag">📅 {{ issue.dueDate.slice(5) }}</span>
                        <span v-if="isCardFieldVisible('sprint') && issue.sprintId" class="card-meta-tag">🏃 {{ getSprintName(issue.sprintId) }}</span>
                        <span v-if="isCardFieldVisible('estimatedHours') && issue.estimatedHours" class="card-meta-tag">⏱ {{ issue.estimatedHours }}h</span>
                        <template v-if="isCardFieldVisible('tags') && (issue as any).tags && (issue as any).tags.length > 0">
                          <span v-for="tag in getVisibleTags(issue)" :key="tag.id" class="card-tag" :style="tag.color ? { background: tag.color, color: '#fff' } : {}">{{ tag.name }}</span>
                        </template>
                      </div>
                      <div class="card-footer">
                        <span v-if="isCardFieldVisible('type')" class="card-type">
                          <template v-if="getCardFieldDisplayMode('type') === 'initial'">{{ typeInitial(issue.issueType) }}</template>
                          <template v-else>{{ typeLabel(issue.issueType) }}</template>
                        </span>
                        <span v-else class="card-type-spacer"></span>
                        <!-- 已分配：显示头像 -->
                        <div class="card-assignee-avatar" v-if="isCardFieldVisible('assignee') && issue.assigneeName" :title="issue.assigneeName">
                          <template v-if="getCardFieldDisplayMode('assignee') === 'full_name'">
                            <span class="assignee-full-name">{{ issue.assigneeName }}</span>
                          </template>
                          <template v-else>
                            <img
                              v-if="issue.assigneeAvatarUrl"
                              :src="issue.assigneeAvatarUrl"
                              :alt="issue.assigneeName"
                              class="avatar-img"
                            />
                            <span v-else class="avatar-initials">{{ getInitials(issue.assigneeName) }}</span>
                          </template>
                        </div>
                        <!-- 未分配 + 有权限：显示 Set assignee 按钮 -->
                        <a-dropdown
                          v-else-if="isCardFieldVisible('assignee') && !issue.assigneeName && canCreateIssue && projectMembers.length > 0"
                          trigger="click"
                          @select="(userId: any) => onCardSetAssignee(userId, issue)"
                        >
                          <button
                            class="set-assignee-btn"
                            :title="'分配负责人'"
                            @click.stop
                          >
                            <icon-user class="set-assignee-icon" />
                          </button>
                          <template #content>
                            <a-doption v-for="m in projectMembers" :key="m.userId" :value="m.userId">
                              {{ m.displayName }}
                            </a-doption>
                          </template>
                        </a-dropdown>
                      </div>
                    </div>
                    <!-- 空单元格 drop hint -->
                    <div
                      v-if="getSwimlaneEffectiveColumnIssues(lane.key, col).length === 0 && isDragging && isEffectiveColumnDropAllowed(col)"
                      class="swimlane-cell-empty-hint"
                    >
                      📥
                    </div>
                    <!-- Swimlane 内联快速创建卡片（合并列：使用第一个 statusId 创建） -->
                    <div
                      v-if="canCreateIssue && !isDragging && !isEffectiveColumnClosed(col)"
                      class="add-card-area add-card-area--swimlane"
                    >
                      <div
                        v-if="addingCardColumnId === getDropTargetStatusId(col) && addingCardSwimlaneKey === lane.key"
                        class="add-card-form"
                      >
                        <input
                          :ref="(el) => setAddCardInputRef(el, getDropTargetStatusId(col), lane.key)"
                          v-model="addCardTitle"
                          class="add-card-input"
                          placeholder="输入标题，回车创建"
                          :disabled="addCardSubmitting"
                          @keydown.enter.prevent="submitAddCard(getDropTargetStatusId(col), lane.key)"
                          @keydown.escape="cancelAddCard"
                          @blur="onAddCardBlur"
                        />
                        <div class="add-card-form-actions">
                          <a-select
                            v-model="addCardType"
                            size="mini"
                            style="width: 80px"
                          >
                            <a-option value="Task">任务</a-option>
                            <a-option value="Bug">缺陷</a-option>
                            <a-option value="Feature">需求</a-option>
                          </a-select>
                          <a-button
                            size="mini"
                            type="primary"
                            :loading="addCardSubmitting"
                            @mousedown.prevent
                            @click="submitAddCard(getDropTargetStatusId(col), lane.key)"
                          >创建</a-button>
                          <a-button
                            size="mini"
                            @mousedown.prevent
                            @click="cancelAddCard"
                          >取消</a-button>
                        </div>
                      </div>
                      <button
                        v-else
                        class="add-card-btn add-card-btn--compact"
                        @click="startAddCard(getDropTargetStatusId(col), lane.key)"
                      >
                        <span class="add-card-icon">+</span>
                      </button>
                    </div>
                  </template>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态：Sprint 模式无活跃迭代 -->
      <div v-if="showSprintModeNoActiveState" class="empty-state empty-state--sprint">
        <div class="empty-icon">🏃</div>
        <h3 class="empty-title">看板已配置为仅显示当前 Sprint 工单</h3>
        <p class="empty-desc">当前项目暂无活跃迭代。请前往「迭代」页面激活一个 Sprint，或修改看板设置为「显示所有工单」。</p>
        <div class="empty-actions">
          <a-button type="primary" size="small" @click="goToSprints">前往迭代页面</a-button>
          <a-button size="small" @click="openSettingsToGeneral">修改看板设置</a-button>
        </div>
      </div>

      <!-- 空状态：搜索无结果 -->
      <div v-else-if="showNoSearchResults" class="empty-state">
        <div class="empty-icon">🔍</div>
        <h3 class="empty-title">未找到匹配的工单</h3>
        <p class="empty-desc">没有工单匹配关键词「{{ keyword }}」</p>
        <a-button type="primary" size="small" @click="clearSearch">清除搜索</a-button>
      </div>

      <!-- 空状态：未选择项目 -->
      <div v-else-if="!selectedProject && projectLoadState === 'error'" class="empty-state">
        <div class="empty-icon">⚠️</div>
        <h3 class="empty-title">项目列表加载失败</h3>
        <p class="empty-desc">无法获取可用项目，请检查网络后重试</p>
        <a-button type="primary" size="small" @click="loadProjects">重试</a-button>
      </div>
      <div v-else-if="!selectedProject && projectLoadState === 'success' && projects.length === 0" class="empty-state">
        <div class="empty-icon">📁</div>
        <h3 class="empty-title">暂无可访问的项目</h3>
        <p class="empty-desc">您尚未加入任何项目，请联系管理员添加为项目成员</p>
      </div>
      <div v-else-if="!selectedProject" class="empty-state">
        <div class="empty-icon">📊</div>
        <h3 class="empty-title">请选择项目</h3>
        <p class="empty-desc">从上方下拉框选择项目查看看板视图</p>
      </div>
      </div><!-- end board-main-area -->
    </a-spin>

    <!-- 看板列设置 Drawer -->
    <BoardSettingsDrawer
      v-model:visible="showSettings"
      :project-id="selectedProject || ''"
      :project-name="currentProjectName"
      :columns="allColumnConfigs"
      :has-active-sprint="!!activeSprint"
      @saved="onSettingsSaved"
    />

    <!-- 工单预览侧边面板 -->
    <IssuePreviewDrawer
      :visible="previewVisible"
      :issue-id="previewIssueId"
      @update:visible="previewVisible = $event"
      @go-detail="onPreviewGoDetail"
      @issue-updated="onPreviewIssueUpdated"
    />

    <!-- 克隆看板弹窗 -->
    <CloneBoardModal
      v-if="selectedProject && showCloneModal"
      v-model="showCloneModal"
      :source-project-id="selectedProject"
      :source-board-name="currentBoardDisplayName"
      :source-project-key="currentProjectKey || ''"
      @cloned="onBoardCloned"
    />

    <!-- 底部工具栏区域：默认 Footer / 批量操作栏 -->
    <transition name="slide-up">
      <!-- 批量操作栏（选中卡片时显示） -->
      <div v-if="selectedCount > 0" class="batch-toolbar-wrapper" key="batch">
        <BatchActionToolbar
          :selected-count="selectedCount"
          :selected-issues="selectedIssues"
          :can-delete="canDeleteIssue"
          :active-project-id="selectedProject"
          @deselect-all="clearSelection"
          @batch-state="onBatchState"
          @batch-assign="onBatchAssign"
          @batch-sprint="onBatchSprint"
          @batch-priority="onBatchPriority"
          @batch-tag-add="onBatchTagAdd"
          @batch-tag-remove="onBatchTagRemove"
          @batch-link="onBatchLink"
          @batch-delete="onBatchDelete"
        />
      </div>
      <!-- 默认 Footer（无选中卡片时，显示 Sprint 目标和看板所有者） -->
      <div v-else-if="selectedProject && !loading" class="board-footer-wrapper" key="footer">
        <!-- 已归档 Sprint 的特殊 Footer 提示区域 -->
        <div v-if="currentSelectedSprint && currentSelectedSprint.status === 'archived'" class="board-footer board-footer--archived">
          <div class="board-footer-left">
            <span class="footer-archived-badge">
              <span class="footer-archived-icon">📦</span>
              <span class="footer-archived-text">本迭代已归档</span>
            </span>
            <span class="footer-archived-sprint-name">{{ currentSelectedSprint.name }}</span>
          </div>
          <div class="board-footer-right">
            <a-button
              v-if="canEditSprint"
              size="small"
              type="primary"
              :loading="restoringArchivedSprint"
              @click="handleRestoreArchivedSprint"
            >
              恢复 Sprint
            </a-button>
            <a-button
              v-if="canDeleteSprint"
              size="small"
              status="danger"
              style="margin-left: 8px"
              :loading="deletingArchivedSprint"
              @click="handleDeleteArchivedSprint"
            >
              删除
            </a-button>
          </div>
        </div>
        <!-- 普通 Footer（非归档状态） -->
        <div v-else class="board-footer">
          <div class="board-footer-left">
            <!-- Board owner（项目负责人） -->
            <span v-if="boardOwnerName" class="footer-item footer-owner" :title="'看板所有者: ' + boardOwnerName">
              <span class="footer-label">👤</span>
              <span class="footer-value">{{ boardOwnerName }}</span>
            </span>
            <!-- Sprint 目标 -->
            <span v-if="currentSprintGoal" class="footer-item footer-goal" :title="'Sprint 目标: ' + currentSprintGoal">
              <span class="footer-label">🎯</span>
              <span class="footer-value footer-goal-text">{{ currentSprintGoal }}</span>
            </span>
            <span v-else-if="currentSelectedSprint && !currentSprintGoal" class="footer-item footer-goal footer-goal--empty">
              <span class="footer-label">🎯</span>
              <span class="footer-value">暂无 Sprint 目标</span>
            </span>
          </div>
          <div class="board-footer-right">
            <!-- 工单统计 -->
            <span v-if="issues.length > 0" class="footer-item footer-stats">
              <span class="footer-value">{{ issues.length }} 个工单</span>
            </span>
          </div>
        </div>
      </div>
    </transition>

    <!-- 新建卡片：复用完整创建面板（支持描述、标签、自定义字段等） -->
    <IssueCreatePanel
      v-model:visible="newCardModalVisible"
      :project-id="selectedProject || undefined"
      :sprint-id="newCardPrefilledSprintId"
      :lock-sprint="!!newCardPrefilledSprintId"
      @created="onNewCardCreated"
      @expand-to-fullscreen="onNewCardExpandFullscreen"
    />

    <!-- 新建 Sprint 对话框 -->
    <a-modal
      v-model:visible="newSprintModalVisible"
      title="新建 Sprint"
      :width="480"
      :ok-loading="newSprintSubmitting"
      ok-text="创建"
      cancel-text="取消"
      @ok="submitNewSprintModal"
      @cancel="newSprintModalVisible = false"
    >
      <a-form :model="newSprintForm" layout="vertical" size="medium">
        <a-form-item label="名称" required>
          <a-input v-model="newSprintForm.name" placeholder="输入 Sprint 名称" :max-length="100" />
        </a-form-item>
        <a-form-item label="目标">
          <a-textarea v-model="newSprintForm.goal" placeholder="Sprint 目标描述（可选）" :max-length="500" :auto-size="{ minRows: 2, maxRows: 4 }" />
        </a-form-item>
        <a-form-item label="开始日期">
          <a-date-picker v-model="newSprintForm.startDate" style="width: 100%" />
        </a-form-item>
        <a-form-item label="结束日期">
          <a-date-picker v-model="newSprintForm.endDate" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 看板页脚：删除已归档 Sprint 确认对话框 -->
    <a-modal
      v-model:visible="showDeleteArchivedSprintModal"
      title="删除迭代"
      :width="480"
      :ok-loading="deletingArchivedSprint"
      ok-text="确认删除"
      cancel-text="取消"
      ok-status="danger"
      @ok="confirmDeleteArchivedSprint"
      @cancel="showDeleteArchivedSprintModal = false"
    >
      <div v-if="deleteArchivedSprintPreview">
        <p v-if="deleteArchivedSprintPreview.totalIssues === 0">
          确定要删除迭代 <strong>{{ currentSelectedSprint?.name }}</strong> 吗？该迭代不含任何工单。
        </p>
        <template v-else>
          <p>迭代 <strong>{{ currentSelectedSprint?.name }}</strong> 包含 <strong>{{ deleteArchivedSprintPreview.totalIssues }}</strong> 个工单，请选择处理方式：</p>
          <a-radio-group v-model="deleteArchivedSprintMoveOption" direction="vertical" style="margin-top: 8px;">
            <a-radio value="backlog">移入待办（不分配迭代）</a-radio>
            <a-radio v-if="deleteArchivedSprintPreview.targetSprints && deleteArchivedSprintPreview.targetSprints.length > 0" value="next_sprint">移入其他迭代</a-radio>
          </a-radio-group>
          <a-select
            v-if="deleteArchivedSprintMoveOption === 'next_sprint' && deleteArchivedSprintPreview.targetSprints"
            v-model="deleteArchivedSprintTargetId"
            placeholder="选择目标迭代"
            style="width: 100%; margin-top: 8px"
          >
            <a-option v-for="s in deleteArchivedSprintPreview.targetSprints" :key="s.id" :value="s.id">{{ s.name }}</a-option>
          </a-select>
        </template>
      </div>
      <div v-else style="text-align: center; padding: 12px 0">
        <a-spin />
      </div>
    </a-modal>

  </div>
</template>

<script setup lang="ts">
import { useKanbanBoard } from './composables'
import { localizeStatusName, localizePriority } from '@/utils/fieldLabels'
import BoardSettingsDrawer from './BoardSettingsDrawer.vue'
import BoardSelector from './BoardSelector.vue'
import BacklogPanel from './BacklogPanel.vue'
import BoardChartPanel from './BoardChartPanel.vue'
import IssuePreviewDrawer from './IssuePreviewDrawer.vue'
import CloneBoardModal from './CloneBoardModal.vue'
import IssueCreatePanel from '@/views/issue/IssueCreatePanel.vue'
import BatchActionToolbar from '@/views/issue/components/BatchActionToolbar.vue'
import { IconSettings, IconSearch, IconList, IconBarChart, IconPlus, IconFile, IconCalendar, IconUser, IconCopy } from '@arco-design/web-vue/es/icon'

const {
  // Core state
  selectedProject, selectedSprint, keyword, loading, issues, statuses, sprints,
  projects, projectLoadState,
  // Card size
  cardSize, cardSizeOptions, setCardSize,
  // TV mode
  isTvMode, toggleTvMode,
  // Orphan issues
  showOrphanPanel, orphanIssues, orphanIssueCount,
  // Column config
  allColumnConfigs, showSettings, effectiveColumns, visibleStatuses,
  hiddenIssueColumns, showAllColumns,
  boardColumnField, isMultiProjectBoard,
  getColumnIssues, getEffectiveColumnIssues,
  getEffectiveColumnWipClass, getEffectiveColumnWipMax, getEffectiveColumnWipWarning,
  getEffectiveColumnEstimation,
  getWipTooltip, getColumnConfig, getWipWarning,
  isColumnCollapsed, toggleColumnCollapse, expandedEmptyColumns,
  collapsedColumns,
  // Board behavior
  boardFilterMode, boardFilterQuery, boardDoneRetentionDays, boardName,
  canEditBoard, displayBoardName,
  isBehaviorFilterActive, isSmartDefaultDoneRetentionActive,
  allowMultipleSprints, boardLinkedProjectIds,
  // Backlog
  showBacklog, toggleBacklog, backlogPanelRef, backlogDraggingIssue, backlogViewMode, backlogSavedQueryId,
  boardStatusIdsForBacklog,
  onBacklogDragStart, onBacklogDragEnd,
  // Preview
  previewVisible, previewIssueId, openPreview, closePreview,
  onPreviewGoDetail, onPreviewIssueUpdated,
  // Swimlane
  swimlaneGroupBy, onSwimlaneChange, swimlanes, orderedSwimlanes,
  collapsedSwimlanes, toggleSwimlane,
  swimlaneSelectedValues, swimlaneShowUncategorized, swimlaneUncategorizedPosition, swimlaneIssueType,
  getSwimlaneColumnIssues,
  dragOverSwimlaneKey, onDragOverSwimlane, onDragLeaveSwimlane,
  // Swimlane row drag
  swimlaneDraggingKey, swimlaneDragOverKey,
  onSwimlaneRowDragStart, onSwimlaneRowDragEnd, onSwimlaneRowDragOver,
  onSwimlaneRowDragLeave, onSwimlaneRowDrop,
  // Sprint
  currentSelectedSprint, activeSprint, currentSprintGoal, sprintRemainingDays,
  isActiveSprint, formatSprintDateRange,
  // Sprint archived footer
  restoringArchivedSprint, deletingArchivedSprint,
  showDeleteArchivedSprintModal, deleteArchivedSprintPreview,
  deleteArchivedSprintMoveOption, deleteArchivedSprintTargetId,
  handleRestoreArchivedSprint, handleDeleteArchivedSprint, confirmDeleteArchivedSprint,
  // No active sprint guidance
  guidanceDismissed, showSprintModeNoActiveState,
  nextPlannedSprint, dismissGuidance,
  // Card config
  cardConfig, isCardFieldVisible, getCardFieldDisplayMode,
  getCardColorClass, getCardProjectColorStyle, getCardDueDateClass, getCardDueDateTooltip,
  hasVisibleCustomFields, getVisibleCustomFieldDetails, getVisibleTags, getVisibleCustomFields,
  // Card interactions
  onCardClick, onCardDblClick, onCardKeydown, onCardSetAssignee,
  // Drag
  draggingIssue, dragOverColumnId, isDragging,
  allowedTargetStatuses, transitioningIssueIds,
  isCardDraggable, onDragStart, onDragEnd, onDragOver, onDragLeave, onDrop,
  isDropAllowed, isEffectiveColumnDropAllowed, getDropTargetStatusId,
  // Selection
  selectedIds, selectedCount, selectedIssues, toggleCardSelection, clearSelection,
  // Manual order
  boardManualSorted, isManualSortDisabled,
  // Batch operations
  batchTransitStatus, batchAssign, batchUpdateSprint, batchUpdatePriority,
  batchTagAdd, batchTagRemove, batchAddLink, batchDelete,
  // Search
  isSearchActive, onSearchInput, onSearchClear, clearSearch,
  showNoSearchResults,
  // Assignee filter
  assigneeFilter, effectiveAssigneeId, projectMembers,
  toggleMyIssues, onAssigneeFilterChange,
  // Permission
  canChangeStatus, canCreateIssue, canDeleteIssue, canEditSprint, canDeleteSprint,
  // Project helpers
  currentProjectName, currentProjectKey, onProjectChange, onSprintChange,
  // Navigation
  openIssue, goToSprintDetail, buildOpenInListUrl,
  // Extra template-referenced names
  showNoActiveSprintGuidance, selectNextPlannedSprint, formatSprintDate,
  effectiveDoneRetentionDays, hiddenIssueTotalCount, openCloneModal,
  // New card/sprint
  newCardModalVisible, newCardPrefilledSprintId, onNewCardCreated, onNewCardExpandFullscreen,
  newSprintModalVisible, newSprintSubmitting, newSprintForm, onNewMenuSelect, submitNewSprintModal,
  // Inline add card
  addingCardColumnId, addingCardSwimlaneKey, addCardTitle, addCardType, addCardSubmitting,
  startAddCard, cancelAddCard, onAddCardBlur, submitAddCard, setAddCardInputRef,
  isClosedStatus,
  // Board chart
  showChart, boardChartType, boardBurndownCalculation,
  // Clone board
  showCloneModal,
  // Undo
  undoStack,
  // Progress
  progressColumns, closedIssueCount, getClosedProgressBarHeight,
  scrollToColumn,
  // Hidden columns
  onHiddenStatusTagClick,
  // Settings saved
  onSettingsSaved,
  // Board truncated
  boardTruncated, boardTotalCount,
  // Helper functions
  getInitials, getSprintName,
  priorityIcon, typeLabel, typeInitial, getActiveSprintId,
  // onBatch handlers
  onBatchTransit, onBatchAssign, onBatchSprint, onBatchPriority,
  onBatchTagAdd, onBatchTagRemove, onBatchLink, onBatchDelete,
  // Load
  loadBoard, loadIssuesWithLoading,
} = useKanbanBoard()
</script>

<style scoped>
@import './KanbanBoardView.css';
</style>
