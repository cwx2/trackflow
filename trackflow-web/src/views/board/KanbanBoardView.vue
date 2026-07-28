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
      <!-- 隐藏列中有工单的警告提示 -->
      <div v-if="hiddenIssueColumns.length > 0 && !loading && !showAllColumns" class="board-hidden-issues-banner">
        <span class="hidden-issues-icon">⚠️</span>
        <span class="hidden-issues-text">
          <strong>{{ hiddenIssueTotalCount }} 个工单</strong>处于未显示的状态列中（{{ hiddenIssueColumnsDetail }}）。
        </span>
        <div class="hidden-issues-actions">
          <a-button size="mini" type="outline" @click="showAllColumns = true">显示全部列</a-button>
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

    <!-- 新建卡片对话框（从头部"新建..."按钮触发） -->
    <a-modal
      v-model:visible="newCardModalVisible"
      title="新建卡片"
      :width="480"
      :ok-loading="newCardSubmitting"
      ok-text="创建"
      cancel-text="取消"
      @ok="submitNewCardModal"
      @cancel="newCardModalVisible = false"
    >
      <a-form :model="newCardForm" layout="vertical" size="medium">
        <a-form-item label="标题" required>
          <a-input v-model="newCardForm.title" placeholder="输入工单标题" :max-length="200" />
        </a-form-item>
        <a-form-item label="类型">
          <a-select v-model="newCardForm.issueType" placeholder="选择工单类型">
            <a-option value="Task">任务</a-option>
            <a-option value="Bug">缺陷</a-option>
            <a-option value="Feature">需求</a-option>
            <a-option value="Story">用户故事</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="优先级">
          <a-select v-model="newCardForm.priority" placeholder="选择优先级">
            <a-option value="Urgent">紧急</a-option>
            <a-option value="High">高</a-option>
            <a-option value="Normal">普通</a-option>
            <a-option value="Low">低</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="负责人">
          <a-select v-model="newCardForm.assigneeId" placeholder="选择负责人" allow-clear>
            <a-option v-for="m in projectMembers" :key="m.userId" :value="m.userId">
              {{ m.displayName }}
            </a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="Sprint">
          <a-select v-model="newCardForm.sprintId" placeholder="选择 Sprint" allow-clear>
            <a-option v-for="s in sprints" :key="s.id" :value="s.id">{{ s.name }}</a-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

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
import { ref, computed, watch, onMounted, onUnmounted, h, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Message, Modal, Notification } from '@arco-design/web-vue'
import { issueApi, sprintApi, boardApi, workflowApi, projectApi } from '@/api'
import type { IssueVO, IssueStatusVO, SprintVO, BoardColumnVO, BoardCardConfigVO, BoardColumnMergeGroupVO, BoardCardVO, R, TransitStatusResultVO, DeletionPreviewVO } from '@/api/types'
import { ERROR_CODES } from '@/api/error-codes'
import { useProjectStore } from '@/stores/project'
import { useAuthStore } from '@/stores/auth'
import { usePermission } from '@/composables/usePermission'
import { useProjectList } from '@/composables/useProjectList'
import { useSelection } from '@/views/issue/composables/useSelection'
import { useBatchOps } from '@/views/issue/composables/useBatchOps'

/** 看板中使用的工单类型 — 可以是精简卡片 VO（聚合 API）或完整 IssueVO（Legacy fallback） */
type BoardIssue = IssueVO | BoardCardVO
import { useManualOrder } from '@/views/issue/composables/useManualOrder'
import { localizeStatusName, localizeIssueType, localizePriority } from '@/utils/fieldLabels'
import { extractVersion, showActionFeedback } from '@/utils/transition'
import { getDueDateInfo } from '@/utils/dueDate'
import BoardSettingsDrawer from './BoardSettingsDrawer.vue'
import BoardSelector from './BoardSelector.vue'
import BacklogPanel from './BacklogPanel.vue'
import BoardChartPanel from './BoardChartPanel.vue'
import IssuePreviewDrawer from './IssuePreviewDrawer.vue'
import CloneBoardModal from './CloneBoardModal.vue'
import BatchActionToolbar from '@/views/issue/components/BatchActionToolbar.vue'
import { IconSettings, IconSearch, IconList, IconBarChart, IconPlus, IconFile, IconCalendar, IconUser, IconCopy } from '@arco-design/web-vue/es/icon'

const router = useRouter()
const route = useRoute()
const projectStore = useProjectStore()

// ===== Card Size 控制 =====
type CardSize = 'S' | 'M' | 'L' | 'XL'
const CARD_SIZE_KEY = 'tf_kanban_card_size'
const cardSize = ref<CardSize>((localStorage.getItem(CARD_SIZE_KEY) as CardSize) || 'M')
const cardSizeOptions = [
  { value: 'S' as const, label: 'S' },
  { value: 'M' as const, label: 'M' },
  { value: 'L' as const, label: 'L' },
  { value: 'XL' as const, label: 'XL' }
]

function setCardSize(size: CardSize) {
  cardSize.value = size
  localStorage.setItem(CARD_SIZE_KEY, size)
}

// ===== TV 模式（全屏大屏显示） =====
const isTvMode = ref(false)
const TV_MODE_KEY = 'tf_kanban_tv_mode'

function toggleTvMode() {
  if (!isTvMode.value) {
    // 进入 TV 模式
    const el = document.documentElement
    if (el.requestFullscreen) {
      el.requestFullscreen().then(() => {
        isTvMode.value = true
        cardSize.value = 'XL'
        localStorage.setItem(TV_MODE_KEY, 'true')
      }).catch(() => {
        // Fullscreen denied — fallback to just large card mode
        isTvMode.value = true
        cardSize.value = 'XL'
      })
    } else {
      isTvMode.value = true
      cardSize.value = 'XL'
    }
  } else {
    // 退出 TV 模式
    if (document.fullscreenElement) {
      document.exitFullscreen().catch(() => { /* ignore */ })
    }
    isTvMode.value = false
    // 恢复之前的卡片尺寸
    const saved = localStorage.getItem(CARD_SIZE_KEY) as CardSize
    cardSize.value = saved || 'M'
    localStorage.removeItem(TV_MODE_KEY)
  }
}

// 监听全屏退出事件（用户按 Esc 退出）
function onFullscreenChange() {
  if (!document.fullscreenElement && isTvMode.value) {
    isTvMode.value = false
    const saved = localStorage.getItem(CARD_SIZE_KEY) as CardSize
    cardSize.value = saved || 'M'
    localStorage.removeItem(TV_MODE_KEY)
  }
}

onMounted(() => {
  document.addEventListener('fullscreenchange', onFullscreenChange)
})
onUnmounted(() => {
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})

// ===== 未解决/未匹配列工单（Orphan Issues） =====
const showOrphanPanel = ref(false)

/** 不属于看板任何可见列的工单 */
const orphanIssues = computed(() => {
  if (!selectedProject.value || visibleStatuses.value.length === 0) return []
  const visibleStatusIds = new Set(visibleStatuses.value.map(s => s.id))
  return issues.value.filter(i => {
    if (boardColumnField.value === 'priority') {
      return !visibleStatusIds.has(i.priority || 'Normal')
    }
    return !visibleStatusIds.has(i.statusId)
  })
})

/** 未匹配列的工单数量 */
const orphanIssueCount = computed(() => orphanIssues.value.length)

/** 构建「在工单列表中打开」的 URL（含项目和状态过滤参数）
 * 支持普通列（单状态）和合并列（多状态，逗号分隔）。
 * 使用 router.resolve 保证路径与当前前端路由配置一致。
 */
function buildOpenInListUrl(col: EffectiveColumn): string {
  const query: Record<string, string> = {}
  // 添加项目过滤
  if (currentProjectKey.value) {
    query.project = currentProjectKey.value
  }
  // 添加状态过滤：普通列用 statusId，合并列用逗号分隔的多个 statusId
  if (boardColumnField.value === 'status') {
    // 状态模式：用 statusId 参数（IssueListView 支持逗号分隔多值）
    query.statusId = col.statusIds.join(',')
  } else if (boardColumnField.value === 'priority') {
    // 优先级模式：col.id 实际是优先级值（Critical/High/Normal/Low）
    query.priority = col.id
  }
  const resolved = router.resolve({ name: 'Issues', query })
  return resolved.href
}

/** 获取负责人姓名首字母/缩写 */
function getInitials(name: string): string {
  if (!name) return '?'
  // CJK: return last 1-2 characters (family name typically)
  const isCJK = /[\u4e00-\u9fff\u3400-\u4dbf]/.test(name)
  if (isCJK) {
    return name.length <= 2 ? name : name.slice(0, 2)
  }
  // Latin: first letter of first + last words
  const parts = name.trim().split(/\s+/)
  if (parts.length === 1) return parts[0][0].toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

/**
 * 卡片上"Set assignee"按钮的点击处理：乐观更新 + API 保存。
 * 阻止事件冒泡，避免打开工单预览面板。
 */
async function onCardSetAssignee(userId: string, issue: BoardIssue) {
  if (!userId) return
  const member = projectMembers.value.find(m => m.userId === userId)
  if (!member) return

  // 乐观更新
  const oldAssigneeId = issue.assigneeId
  const oldAssigneeName = issue.assigneeName
  issue.assigneeId = userId
  issue.assigneeName = member.displayName

  try {
    await issueApi.update(issue.id, { assigneeId: userId })
    issue.version = (issue.version || 0) + 1
    Message.success(`${issue.issueKey} 负责人已设置为「${member.displayName}」`)
  } catch (e: any) {
    // 回滚
    issue.assigneeId = oldAssigneeId
    issue.assigneeName = oldAssigneeName
    Message.error(e.response?.data?.message || '设置负责人失败')
  }
}

/** 检查卡片是否有可见的自定义字段（仅考虑在 visibleFields 中配置的 cf.{id} 字段） */
function hasVisibleCustomFields(issue: BoardIssue): boolean {
  if (!issue.customFieldDetails || issue.customFieldDetails.length === 0) return false
  // 检查 visibleFields 中是否有任何 cf.{id} 配置
  const hasCfConfig = cardConfig.value.visibleFields.some(f => f.startsWith('cf.'))
  if (!hasCfConfig) return false
  // 检查此 issue 是否有匹配的自定义字段值
  return getVisibleCustomFieldDetails(issue).length > 0
}

/** 获取卡片可见的自定义字段详情（仅展示在 visibleFields 中配置的 cf.{id} 字段） */
function getVisibleCustomFieldDetails(issue: BoardIssue) {
  if (!issue.customFieldDetails) return []

  // 从 visibleFields 中提取已选的自定义字段 ID
  const selectedCfIds = new Set(
    cardConfig.value.visibleFields
      .filter(f => f.startsWith('cf.'))
      .map(f => f.substring(3))
  )

  // 如果没有选择任何自定义字段，不展示
  if (selectedCfIds.size === 0) return []

  // 只返回管理员在卡片设置中选择的自定义字段
  const maxFields = cardSize.value === 'L' ? 4 : 2
  return issue.customFieldDetails
    .filter(d => selectedCfIds.has(d.customFieldId))
    .slice(0, maxFields)
}

/** 获取卡片可见的标签列表（根据卡片尺寸限制显示数量） */
function getVisibleTags(issue: BoardIssue): Array<{ id: string; name: string; color?: string }> {
  const tags = (issue as any).tags as Array<{ id: string; name: string; color?: string }> | undefined
  if (!tags || tags.length === 0) return []
  // M 尺寸最多显示 2 个标签，L/XL 最多 4 个
  const maxTags = cardSize.value === 'M' ? 2 : 4
  return tags.slice(0, maxTags)
}

/** @deprecated 兼容旧数据格式 */
function getVisibleCustomFields(issue: BoardIssue): Record<string, string> {
  const cfValues = (issue as any).customFieldValues as Record<string, string> | undefined
  if (!cfValues) return {}
  const entries = Object.entries(cfValues)
  const maxFields = cardSize.value === 'L' ? 4 : 2
  return Object.fromEntries(entries.slice(0, maxFields))
}

/** 判断卡片上是否应展示某个字段（基于 cardConfig） */
function isCardFieldVisible(field: string): boolean {
  return cardConfig.value.visibleFields.includes(field)
}

/** 获取字段在卡片上的显示模式（full_name 或 initial） */
function getCardFieldDisplayMode(field: string): 'full_name' | 'initial' {
  const modes = cardConfig.value.fieldDisplayModes
  return (modes && modes[field]) || 'full_name'
}

/** 预定义项目颜色调色板（用于"按项目着色"方案） */
const PROJECT_COLOR_PALETTE = [
  '#0ea5e9', // 天蓝
  '#10b981', // 翠绿
  '#f59e0b', // 琥珀
  '#8b5cf6', // 紫色
  '#ef4444', // 红色
  '#f97316', // 橙色
  '#14b8a6', // 青色
  '#ec4899', // 粉色
  '#6366f1', // 靛蓝
  '#84cc16', // 黄绿
]

/**
 * 根据 projectId 哈希选取调色板中的颜色，确保同一项目始终显示同一颜色。
 * 使用简单字符串哈希（djb2 变体）取模颜色数量。
 */
function getProjectColor(projectId: string): string {
  if (!projectId) return PROJECT_COLOR_PALETTE[0]
  let hash = 5381
  for (let i = 0; i < projectId.length; i++) {
    hash = ((hash << 5) + hash) + projectId.charCodeAt(i)
    hash = hash & hash // 转为 32 位整数
  }
  const index = Math.abs(hash) % PROJECT_COLOR_PALETTE.length
  return PROJECT_COLOR_PALETTE[index]
}

/** 获取卡片的颜色方案 CSS class */
function getCardColorClass(issue: BoardIssue): string {
  const scheme = cardConfig.value.colorScheme
  if (scheme === 'none') return ''
  if (scheme === 'priority') {
    const p = (issue.priority || 'Normal').toLowerCase()
    return `kanban-card--color-priority-${p}`
  }
  if (scheme === 'type') {
    const t = (issue.issueType || 'task').toLowerCase()
    return `kanban-card--color-type-${t}`
  }
  if (scheme === 'project') {
    return `kanban-card--color-project`
  }
  return ''
}

/**
 * 获取卡片"按项目着色"时的内联样式（动态颜色，无法用静态 CSS class 实现）。
 * 仅当 colorScheme === 'project' 时返回有效样式，其他情况返回空对象。
 */
function getCardProjectColorStyle(issue: BoardIssue): Record<string, string> {
  if (cardConfig.value.colorScheme !== 'project') return {}
  const color = getProjectColor(issue.projectId || '')
  return { borderLeftColor: color }
}

/** 获取卡片截止日期的状态 class */
function getCardDueDateClass(issue: BoardIssue): string {
  if (!issue.dueDate) return ''
  const isClosed = isIssueResolved(issue.statusId)
  const info = getDueDateInfo(issue.dueDate, isClosed)
  if (info.status === 'overdue') return 'card-meta-tag--overdue'
  if (info.status === 'due-soon') return 'card-meta-tag--due-soon'
  return ''
}

/** 获取卡片截止日期的 tooltip */
function getCardDueDateTooltip(issue: BoardIssue): string {
  if (!issue.dueDate) return ''
  const isClosed = isIssueResolved(issue.statusId)
  const info = getDueDateInfo(issue.dueDate, isClosed)
  return info.tooltip
}

/** 判断工单是否已关闭（用于截止日期颜色判断——已关闭的工单不显示逾期警告） */
function isIssueResolved(statusId: string): boolean {
  const config = allColumnConfigs.value.find(c => c.statusId === statusId)
  if (config) return config.statusCategory === 'done' || config.statusCategory === 'cancelled'
  const status = statuses.value.find(s => s.id === statusId)
  return status?.isClosed === true
}

/** 根据 sprintId 获取 Sprint 名称 */
function getSprintName(sprintId: string): string {
  const sprint = sprints.value.find(s => s.id === sprintId)
  return sprint?.name || ''
}

// ===== Swimlane 类型（提前声明供 URL 状态恢复使用） =====
type SwimlaneGroupBy = 'none' | 'assignee' | 'priority' | 'type' | 'sprint' | 'tag' | 'parent' | 'dueDate'
const SWIMLANE_STORAGE_KEY = 'tf_kanban_swimlane'

const selectedProject = computed({
  get: () => projectStore.selectedProjectId,
  set: (val) => projectStore.selectProject(val)
})

// 权限控制
const { canChangeStatus, canCreateIssue, canDeleteIssue, canEditSprint, canDeleteSprint } = usePermission(() => selectedProject.value)
const selectedSprint = ref<string | undefined>(undefined)
/** 用户是否手动清除了 Sprint 选择（区分"未选择"和"显式选全部"） */
let userExplicitlySelectedAll = false
const keyword = ref('')
const loading = ref(false)
const { projects, projectLoadState, loadProjects } = useProjectList()

// ===== 负责人筛选 =====
const authStore = useAuthStore()
const ASSIGNEE_FILTER_KEY = 'tf_kanban_assignee_filter'
const assigneeFilter = ref<string | undefined>(localStorage.getItem(ASSIGNEE_FILTER_KEY) || undefined)
const projectMembers = ref<Array<{ userId: string; displayName: string }>>([])

/** 当前生效的 assigneeId（me → 当前用户数据库 ID，其他 → 原值） */
const effectiveAssigneeId = computed(() => {
  if (!assigneeFilter.value) return undefined
  if (assigneeFilter.value === 'me') return authStore.user?.userId || undefined
  return assigneeFilter.value
})

/** 切换"仅显示我的"按钮 */
function toggleMyIssues() {
  if (assigneeFilter.value === 'me') {
    assigneeFilter.value = undefined
    localStorage.removeItem(ASSIGNEE_FILTER_KEY)
  } else {
    assigneeFilter.value = 'me'
    localStorage.setItem(ASSIGNEE_FILTER_KEY, 'me')
  }
  syncUrlState()
  loadIssuesWithLoading()
}

/** 负责人下拉变化 */
function onAssigneeFilterChange(val: string | undefined) {
  assigneeFilter.value = val || undefined
  if (val) {
    localStorage.setItem(ASSIGNEE_FILTER_KEY, val)
  } else {
    localStorage.removeItem(ASSIGNEE_FILTER_KEY)
  }
  syncUrlState()
  loadIssuesWithLoading()
}

/** 加载项目成员列表（用于负责人筛选下拉） */
async function loadProjectMembers() {
  if (!selectedProject.value) {
    projectMembers.value = []
    return
  }
  try {
    const res = await projectApi.listAssignableMembers(selectedProject.value)
    projectMembers.value = (res.data || []).map(m => ({
      userId: m.userId,
      displayName: m.displayName
    }))
  } catch {
    projectMembers.value = []
  }
}

const currentProjectName = computed(() => {
  if (!selectedProject.value) return ''
  const p = projects.value.find(proj => proj.id === selectedProject.value)
  return p?.name || ''
})

/** 获取当前选中项目的 key（用于 URL 可读性） */
const currentProjectKey = computed(() => {
  if (!selectedProject.value) return undefined
  const p = projects.value.find(proj => proj.id === selectedProject.value)
  return p?.key
})

// ===== URL 状态同步 =====
// URL query params: ?project=DE4&sprint=<id>&group=assignee
// 用 suppressUrlSync 标志避免从 URL 恢复状态时触发 URL 写入（循环）
let suppressUrlSync = false

/**
 * 将当前看板状态同步到 URL query params。
 * 使用 router.replace 以不产生多余的浏览器历史条目。
 */
function syncUrlState() {
  if (suppressUrlSync) return
  const query: Record<string, string> = {}
  if (currentProjectKey.value) {
    query.project = currentProjectKey.value
  }
  if (selectedSprint.value) {
    query.sprint = selectedSprint.value
  }
  if (swimlaneGroupBy.value && swimlaneGroupBy.value !== 'none') {
    query.group = swimlaneGroupBy.value
  }
  if (assigneeFilter.value) {
    query.assignee = assigneeFilter.value
  }
  router.replace({ query })
}

/**
 * 从 URL query 恢复看板状态。
 * 优先级：URL params > projectStore (localStorage) > 无选择
 * @returns 是否成功从 URL 恢复了项目
 */
function restoreFromUrl(): boolean {
  const queryProject = route.query.project as string | undefined
  const querySprint = route.query.sprint as string | undefined
  const queryGroup = route.query.group as string | undefined
  const queryAssignee = route.query.assignee as string | undefined

  let restoredProject = false

  if (queryProject) {
    // 按 key 查找项目
    const project = projects.value.find(p => p.key === queryProject)
    if (project && project.id !== selectedProject.value) {
      suppressUrlSync = true
      selectedProject.value = project.id
      suppressUrlSync = false
      restoredProject = true
    } else if (project && project.id === selectedProject.value) {
      restoredProject = true
    }
  }

  if (querySprint) {
    selectedSprint.value = querySprint
  }

  if (queryGroup && ['none', 'assignee', 'priority', 'type', 'sprint', 'tag', 'parent', 'dueDate'].includes(queryGroup)) {
    swimlaneGroupBy.value = queryGroup as SwimlaneGroupBy
    localStorage.setItem(SWIMLANE_STORAGE_KEY, queryGroup)
  }

  // 恢复负责人筛选（URL 优先于 localStorage）
  if (queryAssignee) {
    assigneeFilter.value = queryAssignee
    localStorage.setItem(ASSIGNEE_FILTER_KEY, queryAssignee)
  }

  return restoredProject
}

// ===== Board Behavior 配置 =====
const boardFilterMode = ref<'all' | 'active_sprint' | 'query'>('all')
const boardFilterQuery = ref<string | null>(null)
const boardDoneRetentionDays = ref<number | null>(null)
const boardName = ref('')
/** 看板列标识字段：status=按状态分列, priority=按优先级分列 */
const boardColumnField = ref<'status' | 'priority'>('status')

/** 是否允许卡片分配到多个 Sprint（对应 Board Settings > Cards > Allow cards to be assigned to multiple sprints） */
const allowMultipleSprints = ref(false)

/** 当前用户是否有看板编辑权限（来自 board_general_config 动态计算） */
const canEditBoard = ref(false)

/** 关联项目 ID 列表（多项目看板时有值） */
const boardLinkedProjectIds = ref<string[]>([])

/** 是否为多项目看板 */
const isMultiProjectBoard = computed(() => boardLinkedProjectIds.value.length > 0)

// ===== Backlog 配置 =====
/** Backlog 视图模式（来自 Board Settings 配置）：list=平铺，tree=树形 */
const backlogViewMode = ref<'list' | 'tree'>('list')
/** 过滤 Backlog 工单的保存搜索 ID（null 时使用默认过滤） */
const backlogSavedQueryId = ref<string | null>(null)

/** 看板页面标题：优先使用管理员设置的名称，fallback 到"项目名 看板"或"看板" */
const displayBoardName = computed(() => {
  if (boardName.value) return boardName.value
  if (currentProjectName.value) return `${currentProjectName.value} 看板`
  return '看板'
})

/** 当前是否有 Board Behavior 过滤生效 */
const isBehaviorFilterActive = computed(() => {
  return boardFilterMode.value !== 'all' || boardDoneRetentionDays.value !== null || isSmartDefaultDoneRetentionActive.value
})

/** 智能默认 14 天保留是否在生效（无配置 + 无活跃 Sprint + 无手动选择 Sprint） */
const isSmartDefaultDoneRetentionActive = computed(() => {
  return boardDoneRetentionDays.value === null && !activeSprint.value && !selectedSprint.value
})

// ===== Backlog 面板 =====
const BACKLOG_VISIBLE_KEY = 'tf_kanban_backlog_visible'
const showBacklog = ref(localStorage.getItem(BACKLOG_VISIBLE_KEY) === 'true')
const backlogPanelRef = ref<InstanceType<typeof BacklogPanel> | null>(null)
const backlogDraggingIssue = ref<BoardIssue | null>(null)

// ===== 工单预览面板 =====
const previewVisible = ref(false)
const previewIssueId = ref<string | null>(null)

function openPreview(issue: BoardIssue) {
  previewIssueId.value = issue.id
  previewVisible.value = true
}

function closePreview() {
  previewVisible.value = false
}

function onPreviewGoDetail(issueId: string) {
  router.push({ name: 'IssueDetail', params: { id: issueId } })
}

/** Handle issue updates from preview panel (inline editing) */
function onPreviewIssueUpdated(issueId: string, changes: Record<string, any>) {
  const issue = issues.value.find(i => i.id === issueId)
  if (!issue) return

  // Apply changes to the local issue object
  if (changes.statusId) {
    issue.statusId = changes.statusId
    issue.version = (issue.version || 0) + 1
  }
  if (changes.priority) {
    issue.priority = changes.priority
  }
  if ('assigneeId' in changes) {
    issue.assigneeId = changes.assigneeId || undefined
    issue.assigneeName = changes.assigneeName || undefined
  }
}

function toggleBacklog() {
  showBacklog.value = !showBacklog.value
  localStorage.setItem(BACKLOG_VISIBLE_KEY, String(showBacklog.value))
}

function onBacklogDragStart(issue: BoardIssue) {
  backlogDraggingIssue.value = issue
  // Fetch available transitions for the backlog issue's current status
  // For backlog items, all open statuses should be valid targets
  allowedTargetStatuses.value = new Set(statuses.value.map(s => s.id))
}

function onBacklogDragEnd() {
  backlogDraggingIssue.value = null
  allowedTargetStatuses.value.clear()
  dragOverColumnId.value = null
  dragOverSwimlaneKey.value = null
}

// ===== Swimlane 分组 =====
const COLLAPSED_SWIMLANES_KEY = 'tf_kanban_collapsed_swimlanes'

const swimlaneGroupBy = ref<SwimlaneGroupBy>(
  (localStorage.getItem(SWIMLANE_STORAGE_KEY) as SwimlaneGroupBy) || 'none'
)
const collapsedSwimlanes = ref<Set<string>>(
  new Set(JSON.parse(localStorage.getItem(COLLAPSED_SWIMLANES_KEY) || '[]'))
)

/** 泳道选中的值列表（null = 全选，向后兼容） */
const swimlaneSelectedValues = ref<string[] | null>(null)
/** 是否显示"未分类"泳道 */
const swimlaneShowUncategorized = ref<boolean>(true)
/** 未分类泳道位置 */
const swimlaneUncategorizedPosition = ref<'top' | 'bottom'>('bottom')
/** Issues 模式下作为泳道行的 Issue 类型 */
const swimlaneIssueType = ref<string | null>(null)

function onSwimlaneChange() {
  localStorage.setItem(SWIMLANE_STORAGE_KEY, swimlaneGroupBy.value)
  // 切换分组维度时清除折叠状态和已选值
  collapsedSwimlanes.value.clear()
  localStorage.removeItem(COLLAPSED_SWIMLANES_KEY)
  swimlaneSelectedValues.value = null
  swimlaneShowUncategorized.value = true
  swimlaneUncategorizedPosition.value = 'bottom'
  // 切换离开 parent 模式时清空 swimlaneIssueType
  if (swimlaneGroupBy.value !== 'parent') {
    swimlaneIssueType.value = null
  }
  // 清除该维度的自定义顺序（切换维度时重置排序）
  clearSwimlaneOrder()
  // 重新加载新维度的泳道排序
  loadSwimlaneOrder()
  // 同步到 URL
  syncUrlState()
  // 持久化到服务端（静默保存，不阻塞 UI）
  if (selectedProject.value) {
    boardApi.saveSwimlaneConfig(selectedProject.value, {
      groupByField: swimlaneGroupBy.value,
      selectedValues: null,
      showUncategorized: true,
      uncategorizedPosition: 'bottom',
      swimlaneIssueType: swimlaneGroupBy.value === 'parent' ? swimlaneIssueType.value : null
    }).catch(() => { /* 静默失败 */ })
  }
}

function toggleSwimlane(key: string) {
  if (collapsedSwimlanes.value.has(key)) {
    collapsedSwimlanes.value.delete(key)
  } else {
    collapsedSwimlanes.value.add(key)
  }
  localStorage.setItem(COLLAPSED_SWIMLANES_KEY, JSON.stringify([...collapsedSwimlanes.value]))
}

// ===== 泳道行拖拽排序 =====
const SWIMLANE_ORDER_KEY_PREFIX = 'tf_kanban_swimlane_order'

function getSwimlaneOrderKey(): string {
  return selectedProject.value
    ? `${SWIMLANE_ORDER_KEY_PREFIX}_${selectedProject.value}_${swimlaneGroupBy.value}`
    : `${SWIMLANE_ORDER_KEY_PREFIX}_${swimlaneGroupBy.value}`
}

/** 用户自定义泳道顺序（泳道 key 列表，null 表示使用默认顺序） */
const swimlaneCustomOrder = ref<string[] | null>(null)

/** 加载当前项目+分组维度的泳道排序 */
function loadSwimlaneOrder() {
  const key = getSwimlaneOrderKey()
  const stored = localStorage.getItem(key)
  swimlaneCustomOrder.value = stored ? JSON.parse(stored) : null
}

/** 保存泳道排序 */
function saveSwimlaneOrder(order: string[]) {
  const key = getSwimlaneOrderKey()
  localStorage.setItem(key, JSON.stringify(order))
  swimlaneCustomOrder.value = order
}

/** 清除泳道排序（切换分组时重置） */
function clearSwimlaneOrder() {
  if (selectedProject.value) {
    const key = getSwimlaneOrderKey()
    localStorage.removeItem(key)
  }
  swimlaneCustomOrder.value = null
}

/**
 * 按用户自定义顺序排列的泳道列表。
 * - 若无自定义顺序，使用 swimlanes 计算顺序（默认）
 * - 若有自定义顺序，按 key 排列，新增未知 key 追加到末尾
 */
const orderedSwimlanes = computed<SwimlaneRow[]>(() => {
  const base = swimlanes.value
  if (!swimlaneCustomOrder.value || swimlaneCustomOrder.value.length === 0) return base

  const orderMap = new Map<string, number>()
  swimlaneCustomOrder.value.forEach((key, idx) => orderMap.set(key, idx))

  const sorted = [...base].sort((a, b) => {
    const idxA = orderMap.has(a.key) ? orderMap.get(a.key)! : base.length
    const idxB = orderMap.has(b.key) ? orderMap.get(b.key)! : base.length
    return idxA - idxB
  })

  return sorted
})

/** 正在拖拽的泳道行 key */
const swimlaneDraggingKey = ref<string | null>(null)
/** 拖拽悬停的目标泳道行 key */
const swimlaneDragOverKey = ref<string | null>(null)

function onSwimlaneRowDragStart(event: DragEvent, laneKey: string) {
  // 不与卡片拖拽冲突：仅在无卡片拖拽时允许泳道行拖拽
  if (isDragging.value) {
    event.preventDefault()
    return
  }
  swimlaneDraggingKey.value = laneKey
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', `swimlane:${laneKey}`)
  }
}

function onSwimlaneRowDragEnd() {
  swimlaneDraggingKey.value = null
  swimlaneDragOverKey.value = null
}

function onSwimlaneRowDragOver(event: DragEvent, laneKey: string) {
  if (!swimlaneDraggingKey.value) return
  event.preventDefault()
  swimlaneDragOverKey.value = laneKey
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function onSwimlaneRowDragLeave(event: DragEvent) {
  const relatedTarget = event.relatedTarget as HTMLElement | null
  const currentTarget = event.currentTarget as HTMLElement
  if (relatedTarget && currentTarget.contains(relatedTarget)) return
  swimlaneDragOverKey.value = null
}

function onSwimlaneRowDrop(event: DragEvent, targetKey: string) {
  event.preventDefault()
  const fromKey = swimlaneDraggingKey.value
  swimlaneDraggingKey.value = null
  swimlaneDragOverKey.value = null

  if (!fromKey || fromKey === targetKey) return

  // Build new order by moving fromKey to targetKey's position
  const currentOrder = orderedSwimlanes.value.map(l => l.key)
  const fromIdx = currentOrder.indexOf(fromKey)
  const toIdx = currentOrder.indexOf(targetKey)
  if (fromIdx < 0 || toIdx < 0) return

  const newOrder = [...currentOrder]
  newOrder.splice(fromIdx, 1)
  // Determine drop side (above or below target)
  const insertIdx = fromIdx < toIdx ? toIdx : toIdx
  newOrder.splice(insertIdx, 0, fromKey)

  saveSwimlaneOrder(newOrder)
}

// Swimlane 数据结构
interface SwimlaneRow {
  key: string
  label: string
  issues: BoardIssue[]
}

// 类型映射（使用共享工具）
const TYPE_LABELS = { Task: '任务', Bug: '缺陷', Feature: '需求', Epic: '史诗', Story: '故事' } as Record<string, string>

const swimlanes = computed<SwimlaneRow[]>(() => {
  if (swimlaneGroupBy.value === 'none') return []

  const allIssues = issues.value

  let rows: SwimlaneRow[]
  switch (swimlaneGroupBy.value) {
    case 'assignee':
      rows = groupByAssignee(allIssues)
      break
    case 'priority':
      rows = groupByPriority(allIssues)
      break
    case 'type':
      rows = groupByType(allIssues)
      break
    case 'sprint':
      rows = groupBySprint(allIssues)
      break
    case 'tag':
      rows = groupByTag(allIssues)
      break
    case 'parent':
      rows = groupByParent(allIssues)
      break
    case 'dueDate':
      // 日期泳道不支持自定义 selectedValues 过滤，直接返回
      return groupByDueDate(allIssues)
    default:
      return []
  }

  // 如果有 selectedValues 配置，过滤泳道并生成"未分类"泳道
  const selected = swimlaneSelectedValues.value
  if (selected && selected.length > 0) {
    const selectedSet = new Set(selected)
    const filteredRows: SwimlaneRow[] = []
    const uncategorizedIssues: BoardIssue[] = []

    for (const row of rows) {
      if (selectedSet.has(row.key)) {
        filteredRows.push(row)
      } else {
        // 不在选中列表中的泳道，工单归入"未分类"
        uncategorizedIssues.push(...row.issues)
      }
    }

    // 按选中值顺序排列
    filteredRows.sort((a, b) => selected.indexOf(a.key) - selected.indexOf(b.key))

    // 添加"未分类"泳道
    if (swimlaneShowUncategorized.value && uncategorizedIssues.length > 0) {
      const uncategorizedRow: SwimlaneRow = {
        key: '__uncategorized__',
        label: '未分类',
        issues: uncategorizedIssues
      }
      if (swimlaneUncategorizedPosition.value === 'top') {
        filteredRows.unshift(uncategorizedRow)
      } else {
        filteredRows.push(uncategorizedRow)
      }
    }

    return filteredRows
  }

  return rows
})

function groupByAssignee(allIssues: BoardIssue[]): SwimlaneRow[] {
  const groups = new Map<string, BoardIssue[]>()
  const unassigned: BoardIssue[] = []

  for (const issue of allIssues) {
    if (!issue.assigneeId || !issue.assigneeName) {
      unassigned.push(issue)
    } else {
      const key = issue.assigneeId
      if (!groups.has(key)) groups.set(key, [])
      groups.get(key)!.push(issue)
    }
  }

  // 按工单数量降序排列负责人
  const rows: SwimlaneRow[] = [...groups.entries()]
    .sort((a, b) => b[1].length - a[1].length)
    .map(([assigneeId, issues]) => ({
      key: assigneeId,
      label: issues[0].assigneeName || '未知',
      issues
    }))

  // "未分配" 放在最后
  if (unassigned.length > 0) {
    rows.push({ key: '__unassigned__', label: '未分配', issues: unassigned })
  }

  return rows
}

function groupByPriority(allIssues: BoardIssue[]): SwimlaneRow[] {
  const priorities = ['Critical', 'High', 'Normal', 'Low']
  const groups = new Map<string, BoardIssue[]>()
  for (const p of priorities) groups.set(p, [])

  for (const issue of allIssues) {
    const p = issue.priority || 'Normal'
    if (!groups.has(p)) groups.set(p, [])
    groups.get(p)!.push(issue)
  }

  return priorities
    .filter(p => (groups.get(p)?.length ?? 0) > 0)
    .map(p => ({
      key: p,
      label: `${priorityIcon(p)} ${p}`,
      issues: groups.get(p)!
    }))
}

function groupByType(allIssues: BoardIssue[]): SwimlaneRow[] {
  const types = ['Bug', 'Task', 'Feature', 'Story']
  const groups = new Map<string, BoardIssue[]>()
  const other: BoardIssue[] = []

  for (const issue of allIssues) {
    const t = issue.issueType
    if (types.includes(t)) {
      if (!groups.has(t)) groups.set(t, [])
      groups.get(t)!.push(issue)
    } else {
      other.push(issue)
    }
  }

  const rows: SwimlaneRow[] = types
    .filter(t => (groups.get(t)?.length ?? 0) > 0)
    .map(t => ({
      key: t,
      label: TYPE_LABELS[t] || t,
      issues: groups.get(t)!
    }))

  if (other.length > 0) {
    rows.push({ key: '__other__', label: '其他', issues: other })
  }

  return rows
}

function groupBySprint(allIssues: BoardIssue[]): SwimlaneRow[] {
  const groups = new Map<string, BoardIssue[]>()
  const noSprint: BoardIssue[] = []

  for (const issue of allIssues) {
    if (!issue.sprintId) {
      noSprint.push(issue)
    } else {
      if (!groups.has(issue.sprintId)) groups.set(issue.sprintId, [])
      groups.get(issue.sprintId)!.push(issue)
    }
  }

  // 用 sprints 列表映射名称
  const sprintMap = new Map(sprints.value.map(s => [s.id, s.name]))

  const rows: SwimlaneRow[] = [...groups.entries()].map(([sprintId, issues]) => ({
    key: sprintId,
    label: sprintMap.get(sprintId) || `Sprint ${sprintId}`,
    issues
  }))

  if (noSprint.length > 0) {
    rows.push({ key: '__no_sprint__', label: '未规划', issues: noSprint })
  }

  return rows
}

function groupByTag(allIssues: BoardIssue[]): SwimlaneRow[] {
  const groups = new Map<string, BoardIssue[]>()
  const noTag: BoardIssue[] = []

  for (const issue of allIssues) {
    const tags = (issue as any).tags as Array<{ id: string; name: string }> | undefined
    if (!tags || tags.length === 0) {
      noTag.push(issue)
    } else {
      // 工单放入第一个标签对应的泳道（与 YouTrack 行为一致）
      const firstTag = tags[0]
      const key = firstTag.id || firstTag.name
      if (!groups.has(key)) groups.set(key, [])
      groups.get(key)!.push(issue)
    }
  }

  const rows: SwimlaneRow[] = [...groups.entries()]
    .sort((a, b) => b[1].length - a[1].length)
    .map(([tagKey, issues]) => {
      const sampleIssue = issues[0]
      const tags = (sampleIssue as any).tags as Array<{ id: string; name: string }> | undefined
      const tagName = tags?.find(t => (t.id || t.name) === tagKey)?.name || tagKey
      return { key: tagKey, label: `🏷️ ${tagName}`, issues }
    })

  if (noTag.length > 0) {
    rows.push({ key: '__no_tag__', label: '无标签', issues: noTag })
  }

  return rows
}

/**
 * Issues 模式（YouTrack Swimlanes Issues 类型）：
 * 以 swimlaneIssueType 指定类型的工单作为泳道行标题，
 * 其子工单（parentId 指向该工单）排列在对应泳道中。
 * 没有父工单（或父工单类型不匹配）的工单归入"未分类"泳道。
 */
function groupByParent(allIssues: BoardIssue[]): SwimlaneRow[] {
  const targetType = swimlaneIssueType.value

  // 收集所有作为父工单的卡片（类型匹配且本身在看板上）
  // 及子工单映射：parentId → [子工单列表]
  const parentMap = new Map<string, BoardIssue>()
  const childrenMap = new Map<string, BoardIssue[]>()
  const uncategorized: BoardIssue[] = []

  // 第一遍：识别父工单（目标类型的工单）
  for (const issue of allIssues) {
    const issueType = issue.issueType
    if (targetType && issueType === targetType) {
      parentMap.set(issue.id, issue)
    }
  }

  // 第二遍：将子工单归入父工单或未分类
  for (const issue of allIssues) {
    // 跳过父工单本身（它们成为泳道行，不作为子工单出现）
    if (parentMap.has(issue.id)) continue

    const parentId = (issue as any).parentId as string | undefined
    if (parentId && parentMap.has(parentId)) {
      if (!childrenMap.has(parentId)) childrenMap.set(parentId, [])
      childrenMap.get(parentId)!.push(issue)
    } else {
      // 无父工单或父工单不在看板上（类型不匹配）→ 未分类
      uncategorized.push(issue)
    }
  }

  // 构建泳道行
  const rows: SwimlaneRow[] = []
  for (const [parentId, parentIssue] of parentMap.entries()) {
    const children = childrenMap.get(parentId) || []
    rows.push({
      key: parentId,
      label: `${parentIssue.issueKey} ${parentIssue.title}`,
      issues: children
    })
  }

  // 按父工单 key 排序（字母数字）
  rows.sort((a, b) => {
    const keyA = parentMap.get(a.key)?.issueKey || a.key
    const keyB = parentMap.get(b.key)?.issueKey || b.key
    return keyA.localeCompare(keyB)
  })

  // 未分类泳道（未匹配父工单的工单），按 showUncategorized 配置决定是否显示
  if (swimlaneShowUncategorized.value && uncategorized.length > 0) {
    const uncategorizedRow: SwimlaneRow = {
      key: '__uncategorized__',
      label: '未分类',
      issues: uncategorized
    }
    if (swimlaneUncategorizedPosition.value === 'top') {
      rows.unshift(uncategorizedRow)
    } else {
      rows.push(uncategorizedRow)
    }
  }

  return rows
}

/**
 * 按截止日期分组 — YouTrack 风格相对日期范围泳道。
 * 分组顺序：已过期 → 今天 → 本周 → 下周 → 本月 → 更晚 → 无截止日期
 * 相对日期基于看板加载时的客户端本地时间动态计算。
 */
function groupByDueDate(allIssues: BoardIssue[]): SwimlaneRow[] {
  const now = new Date()
  const todayStr = now.toISOString().slice(0, 10)

  // 计算当周起止（周一~周日）
  const dayOfWeek = now.getDay() // 0=周日
  const daysToMonday = (dayOfWeek === 0 ? -6 : 1 - dayOfWeek)
  const monday = new Date(now)
  monday.setDate(now.getDate() + daysToMonday)
  monday.setHours(0, 0, 0, 0)
  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 6)
  sunday.setHours(23, 59, 59, 999)

  // 下周起止
  const nextMonday = new Date(monday)
  nextMonday.setDate(monday.getDate() + 7)
  const nextSunday = new Date(sunday)
  nextSunday.setDate(sunday.getDate() + 7)

  // 本月起止
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1)
  const monthEnd = new Date(now.getFullYear(), now.getMonth() + 1, 0)
  monthEnd.setHours(23, 59, 59, 999)

  type DueDateBucket = 'overdue' | 'today' | 'this_week' | 'next_week' | 'this_month' | 'later' | 'no_date'

  function getBucket(dueDate: string | undefined): DueDateBucket {
    if (!dueDate) return 'no_date'

    // dueDate 格式为 'YYYY-MM-DD'
    if (dueDate < todayStr) return 'overdue'
    if (dueDate === todayStr) return 'today'

    const d = new Date(dueDate + 'T00:00:00')
    if (d >= monday && d <= sunday) return 'this_week'
    if (d >= nextMonday && d <= nextSunday) return 'next_week'
    if (d >= monthStart && d <= monthEnd) return 'this_month'
    return 'later'
  }

  const bucketOrder: DueDateBucket[] = ['overdue', 'today', 'this_week', 'next_week', 'this_month', 'later', 'no_date']
  const bucketLabels: Record<DueDateBucket, string> = {
    overdue: '⚠️ 已过期',
    today: '📅 今天',
    this_week: '📅 本周',
    next_week: '📅 下周',
    this_month: '📅 本月',
    later: '📅 更晚',
    no_date: '— 无截止日期',
  }

  const groups: Record<DueDateBucket, BoardIssue[]> = {
    overdue: [],
    today: [],
    this_week: [],
    next_week: [],
    this_month: [],
    later: [],
    no_date: [],
  }

  for (const issue of allIssues) {
    const bucket = getBucket(issue.dueDate)
    groups[bucket].push(issue)
  }

  // 只返回有工单的泳道（保持 YouTrack 风格：空泳道不展示）
  return bucketOrder
    .filter(b => groups[b].length > 0)
    .map(b => ({
      key: b,
      label: bucketLabels[b],
      issues: groups[b],
    }))
}

/** 获取某泳道中某状态列的工单 */
function getSwimlaneColumnIssues(laneKey: string, statusId: string): BoardIssue[] {
  if (!lane) return []
  return lane.issues.filter(i => i.statusId === statusId)
}

// Swimlane 模式下的拖拽 hover 状态
const dragOverSwimlaneKey = ref<string | null>(null)

function onDragOverSwimlane(event: DragEvent, statusId: string, laneKey: string) {
  event.preventDefault()
  dragOverColumnId.value = statusId
  dragOverSwimlaneKey.value = laneKey
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = isDropAllowed(statusId) ? 'move' : 'none'
  }
}

function onDragLeaveSwimlane(event: DragEvent) {
  const relatedTarget = event.relatedTarget as HTMLElement | null
  const currentTarget = event.currentTarget as HTMLElement
  if (relatedTarget && currentTarget.contains(relatedTarget)) return
  dragOverColumnId.value = null
  dragOverSwimlaneKey.value = null
}

// ===== Sprint 选择器辅助 =====

/** 当前选中的 Sprint 对象 */
const currentSelectedSprint = computed(() => {
  if (!selectedSprint.value) return null
  return sprints.value.find(s => s.id === selectedSprint.value) || null
})

/** 看板所有者（项目负责人 leadId 对应的显示名称） */
const boardOwnerName = computed(() => {
  if (!selectedProject.value) return ''
  const proj = projects.value.find(p => p.id === selectedProject.value)
  if (!proj || !proj.leadId) return ''
  const member = projectMembers.value.find(m => m.userId === proj.leadId)
  return member?.displayName || ''
})

/** 当前活跃 Sprint（status=active 或日期范围包含今天的 planned Sprint） */
const activeSprint = computed(() => {
  const active = sprints.value.find(s => s.status === 'active')
  if (active) return active
  // Fallback: planned sprint covering today
  const today = new Date().toISOString().split('T')[0]
  return sprints.value.find(s =>
    s.status === 'planned' && s.startDate && s.endDate &&
    s.startDate <= today && s.endDate >= today
  )
})

/** 当前 Sprint 目标文本 */
const currentSprintGoal = computed(() => {
  // 优先展示选中 Sprint 的目标，其次展示活跃 Sprint 的目标
  const sprint = currentSelectedSprint.value || activeSprint.value
  if (!sprint) return ''
  return sprint.goal || ''
})

/** Sprint 剩余天数（选中的 Sprint 有 endDate 时显示——active 或已开始的 planned） */
const sprintRemainingDays = computed(() => {
  const sprint = currentSelectedSprint.value
  if (!sprint || !sprint.endDate) return null
  // Show countdown for active sprint or planned sprint that has started (date-range)
  if (sprint.status !== 'active' && sprint.status !== 'planned') return null
  // For planned sprints, only show if start date has passed or is today
  if (sprint.status === 'planned' && sprint.startDate) {
    const today = new Date().toISOString().split('T')[0]
    if (sprint.startDate > today) return null
  }
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const endDate = new Date(sprint.endDate + 'T00:00:00')
  const diff = Math.ceil((endDate.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))
  return diff
})

/** 判断某 Sprint 是否为当前活跃 Sprint（用于下拉列表标记"当前"） */
function isActiveSprint(sprint: SprintVO): boolean {
  return sprint.id === activeSprint.value?.id
}

// ===== 看板 Footer：已归档 Sprint 操作 =====

/** 恢复归档 Sprint 的 loading 状态 */
const restoringArchivedSprint = ref(false)

/** 删除归档 Sprint 的 loading 状态 */
const deletingArchivedSprint = ref(false)

/** 是否显示删除归档 Sprint 的确认弹框 */
const showDeleteArchivedSprintModal = ref(false)

/** 删除预览信息 */
const deleteArchivedSprintPreview = ref<DeletionPreviewVO | null>(null)

/** 删除时的工单处理方式 */
const deleteArchivedSprintMoveOption = ref<string>('backlog')

/** 删除时的目标迭代 ID */
const deleteArchivedSprintTargetId = ref<string>('')

/** 从看板 Footer 恢复已归档 Sprint */
async function handleRestoreArchivedSprint() {
  if (!currentSelectedSprint.value) return
  restoringArchivedSprint.value = true
  try {
    await sprintApi.restore(currentSelectedSprint.value.id)
    Message.success('迭代已恢复为已完成状态')
    // 刷新 Sprint 列表
    if (selectedProject.value) {
      const res = await sprintApi.listByProject(selectedProject.value)
      sprints.value = res.data?.list || []
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '恢复失败')
  } finally {
    restoringArchivedSprint.value = false
  }
}

/** 从看板 Footer 删除已归档 Sprint（显示确认弹框） */
async function handleDeleteArchivedSprint() {
  if (!currentSelectedSprint.value) return
  deleteArchivedSprintPreview.value = null
  deleteArchivedSprintMoveOption.value = 'backlog'
  deleteArchivedSprintTargetId.value = ''
  showDeleteArchivedSprintModal.value = true

  try {
    const res = await sprintApi.deletionPreview(currentSelectedSprint.value.id)
    deleteArchivedSprintPreview.value = res.data
    if (res.data.totalIssues > 0 && res.data.targetSprints && res.data.targetSprints.length > 0) {
      deleteArchivedSprintTargetId.value = res.data.targetSprints[0].id
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '获取预览信息失败')
    showDeleteArchivedSprintModal.value = false
  }
}

/** 确认删除已归档 Sprint */
async function confirmDeleteArchivedSprint() {
  if (!currentSelectedSprint.value || !deleteArchivedSprintPreview.value) return

  const hasIssues = deleteArchivedSprintPreview.value.totalIssues > 0
  if (hasIssues && deleteArchivedSprintMoveOption.value === 'next_sprint' && !deleteArchivedSprintTargetId.value) {
    Message.warning('请选择目标迭代')
    return
  }

  deletingArchivedSprint.value = true
  try {
    const body = hasIssues
      ? { moveOption: deleteArchivedSprintMoveOption.value, targetSprintId: deleteArchivedSprintMoveOption.value === 'next_sprint' ? deleteArchivedSprintTargetId.value : undefined }
      : undefined

    await sprintApi.delete(currentSelectedSprint.value.id, body)
    Message.success('迭代已删除')
    showDeleteArchivedSprintModal.value = false
    // 清空选中的 Sprint，刷新列表
    selectedSprint.value = undefined
    if (selectedProject.value) {
      const res = await sprintApi.listByProject(selectedProject.value)
      sprints.value = res.data?.list || []
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
  } finally {
    deletingArchivedSprint.value = false
  }
}

// ===== 无活跃 Sprint 引导 =====

/** 下一个计划中的 Sprint（最近的未来 planned Sprint） */
const nextPlannedSprint = computed(() => {
  if (activeSprint.value) return null
  const today = new Date().toISOString().split('T')[0]
  // 找到开始日期在今天之后的 planned Sprint，取最近的一个
  const futurePlanned = sprints.value
    .filter(s => s.status === 'planned' && s.startDate && s.startDate > today)
    .sort((a, b) => (a.startDate || '').localeCompare(b.startDate || ''))
  return futurePlanned.length > 0 ? futurePlanned[0] : null
})

/** 工具栏中显示的下一个 Sprint 提示文字 */
const nextPlannedSprintHint = computed(() => {
  const sprint = nextPlannedSprint.value
  if (!sprint || !sprint.startDate) return ''
  const startDate = new Date(sprint.startDate + 'T00:00:00')
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const daysUntil = Math.ceil((startDate.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))
  if (daysUntil <= 0) return `${sprint.name} 今天开始`
  if (daysUntil === 1) return `${sprint.name} 明天开始`
  return `${sprint.name} ${daysUntil} 天后开始`
})

/** 引导横幅 dismissed 状态（localStorage 存储，基于项目+会话） */
const GUIDANCE_DISMISSED_KEY = 'tf_kanban_guidance_dismissed'
const guidanceDismissed = ref(false)

/** 是否显示无活跃 Sprint 引导横幅 */
const showNoActiveSprintGuidance = computed(() => {
  if (guidanceDismissed.value) return false
  if (!selectedProject.value) return false
  if (activeSprint.value) return false
  if (selectedSprint.value) return false // 用户已手动选了某个 Sprint
  if (boardFilterMode.value === 'active_sprint') return false // 已在 Sprint 模式（有对应空状态）
  if (sprints.value.length === 0) return false
  if (issues.value.length === 0) return false
  return true
})

/** 关闭引导横幅 */
function dismissGuidance() {
  guidanceDismissed.value = true
  // 当前会话记忆（刷新页面时重新显示，切换项目时重置）
  sessionStorage.setItem(`${GUIDANCE_DISMISSED_KEY}_${selectedProject.value}`, 'true')
}

/** 选择下一个计划 Sprint */
function selectNextPlannedSprint() {
  if (nextPlannedSprint.value) {
    selectedSprint.value = nextPlannedSprint.value.id
    userExplicitlySelectedAll = false
    syncUrlState()
    loadIssuesWithLoading()
    guidanceDismissed.value = true
  }
}

/** 格式化 Sprint 日期（用于引导横幅） */
function formatSprintDate(dateStr: string | undefined): string {
  if (!dateStr) return ''
  const d = new Date(dateStr + 'T00:00:00')
  return `${d.getMonth() + 1}/${d.getDate()}`
}

/** 有效的完成工单保留天数（服务端配置 > 默认 14 天） */
const effectiveDoneRetentionDays = computed(() => {
  return boardDoneRetentionDays.value
})

// 搜索相关
let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null
const isSearchActive = computed(() => keyword.value.trim().length > 0)
const showNoSearchResults = computed(() =>
  isSearchActive.value && selectedProject.value && issues.value.length === 0 && !loading.value
)

/** 是否显示 Sprint 模式无活跃迭代的空状态 */
const showSprintModeNoActiveState = computed(() =>
  selectedProject.value &&
  !loading.value &&
  boardFilterMode.value === 'active_sprint' &&
  !activeSprint.value &&
  issues.value.length === 0 &&
  !isSearchActive.value
)

/** 跳转到迭代管理页面 */
function goToSprints() {
  router.push({ name: 'Sprints' })
}

/** 跳转到迭代详情页（保持项目上下文） */
function goToSprintDetail() {
  const query: Record<string, string> = {}
  if (currentProjectKey.value) {
    query.project = currentProjectKey.value
  }
  router.push({ name: 'Sprints', query })
}

/** 格式化 Sprint 日期范围（如 "7/29 - 8/11"） */
function formatSprintDateRange(startDate?: string, endDate?: string): string {
  if (!startDate) return ''
  const formatShort = (dateStr: string) => {
    const d = new Date(dateStr + 'T00:00:00')
    return `${d.getMonth() + 1}/${d.getDate()}`
  }
  if (!endDate) return formatShort(startDate) + ' 开始'
  return `${formatShort(startDate)} - ${formatShort(endDate)}`
}

/** 打开看板设置到基本设置标签页 */
function openSettingsToGeneral() {
  showSettings.value = true
}

function onSearchInput() {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => {
    loadIssuesWithLoading()
  }, 350)
}

function onSearchClear() {
  keyword.value = ''
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  loadIssuesWithLoading()
}

function clearSearch() {
  keyword.value = ''
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  loadIssuesWithLoading()
}

/** 带 loading 状态的工单刷新 */
async function loadIssuesWithLoading() {
  if (!selectedProject.value) return
  loading.value = true
  try {
    await loadIssues()
  } catch (e: any) {
    // 会话过期（axios.Cancel from request.ts）时不显示"搜索失败"——已有过期提示和跳转
    const isSessionExpired = e?.message === '会话已过期' || e?.code === 'ERR_CANCELED'
    if (isSessionExpired) return
    issues.value = []
    Message.error('搜索失败')
  } finally {
    loading.value = false
  }
}

function onProjectChange() {
  keyword.value = ''
  selectedSprint.value = undefined
  showAllColumns.value = false  // 切换项目时重置临时显示
  userExplicitlySelectedAll = false  // Reset: allow auto-select for new project
  guidanceDismissed.value = false  // Reset guidance for new project
  resetBoardManualOrder()  // Reset manual order when switching projects
  // 切换项目时清除泳道自定义排序（不同项目的泳道数据不同）
  swimlaneCustomOrder.value = null
  // 切换项目时：保留 'me' 筛选，但清除指定用户 ID（因为不同项目的成员不同）
  if (assigneeFilter.value && assigneeFilter.value !== 'me') {
    assigneeFilter.value = undefined
    localStorage.removeItem(ASSIGNEE_FILTER_KEY)
  }
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  syncUrlState()
  loadBoard()
}
function onSprintChange() {
  // Track if user explicitly cleared the sprint selection (chose "所有迭代")
  userExplicitlySelectedAll = !selectedSprint.value
  syncUrlState()
  // Sprint change only affects issue filtering — no need to reload board columns,
  // sprints list, card config, etc. This avoids re-fetching sprints which would
  // cause Arco Select to re-render options and potentially clear the v-model value.
  loadIssuesWithLoading()
}
const sprints = ref<SprintVO[]>([])
const statuses = ref<IssueStatusVO[]>([])
const issues = ref<BoardIssue[]>([])

// ===== 卡片多选 =====
const {
  selectedIds, selectedCount, selectedIssues,
  toggle: toggleCardSelection, clearSelection
} = useSelection(issues)

const { batchTransitStatus, batchAssign, batchUpdateSprint, batchUpdatePriority, batchTagAdd, batchTagRemove, batchAddLink, batchDelete } = useBatchOps()

// ===== 手动排序（列内拖拽） =====
const {
  isManualSorted: boardManualSorted,
  loadManualOrder: loadBoardManualOrder,
  applyManualOrder: applyBoardManualOrder,
  saveOrder: saveBoardManualOrder,
  reset: resetBoardManualOrder
} = useManualOrder()

/** 判断手动排序是否被禁用（Board Settings 的 filterQuery 包含 sort by 等排序指令） */
const isManualSortDisabled = computed(() => {
  if (!boardFilterQuery.value) return false
  // Check if filterQuery contains sort-related fields (simplified check)
  try {
    const filters = JSON.parse(boardFilterQuery.value)
    return Array.isArray(filters) && filters.some((f: any) => f.field === 'sort' || f.field === 'orderBy')
  } catch {
    // string-based query: check for 'sort by' keyword
    return boardFilterQuery.value.toLowerCase().includes('sort by')
  }
})

// 看板列配置
const allColumnConfigs = ref<BoardColumnVO[]>([])
const showSettings = ref(false)

// ===== 克隆看板 =====
const showCloneModal = ref(false)

/** 当前看板显示名称（用于克隆弹窗） */
const currentBoardDisplayName = computed(() => displayBoardName.value || currentProjectName.value || '看板')

function openCloneModal() {
  showCloneModal.value = true
}

function onBoardCloned(newProjectId: string) {
  // 克隆成功后切换到新看板
  selectedProject.value = newProjectId
}

// 看板图表面板
const showChart = ref(false)
const boardChartType = ref<string>('burndown')
const boardBurndownCalculation = ref<string>('issue_count')

// 看板卡片配置（字段显示 + 颜色方案）
const cardConfig = ref<BoardCardConfigVO>({
  visibleFields: ['assignee', 'priority', 'type'],
  colorScheme: 'none',
  showCustomFieldColors: true
})

// 看板列合并配置
const columnMerges = ref<BoardColumnMergeGroupVO[]>([])

// 根据列配置过滤出可见的状态
const visibleStatuses = computed(() => {
  if (allColumnConfigs.value.length === 0) {
    if (boardColumnField.value === 'status') {
      return statuses.value
    }
    // For non-status modes with no config, return empty (will be populated by loadBoardColumns)
    return []
  }
  return allColumnConfigs.value
    .filter(c => c.visible || showAllColumns.value)
    .map(c => ({
      id: c.fieldValue || c.statusId,
      name: c.statusName,
      code: c.statusCode,
      color: c.statusColor,
      category: c.statusCategory,
      isDefault: false,
      isClosed: c.statusCategory === 'done' || c.statusCategory === 'cancelled',
      sortOrder: c.sortOrder
    } as IssueStatusVO))
})

// ===== 进度指示器：活跃状态 vs 终态 =====

/** 活跃状态列（排除已完成/已取消） — 用于进度指示器主体 */
const activeStatuses = computed(() => {
  return visibleStatuses.value.filter(s => !isClosedStatus(s))
})

/** 终态列（已完成/已取消） — 用于进度指示器弱化汇总 */
const closedStatuses = computed(() => {
  return visibleStatuses.value.filter(s => isClosedStatus(s))
})

/** 终态工单总数 */
const closedIssueCount = computed(() => {
  return closedStatuses.value.reduce((sum, s) => sum + getColumnIssues(s.id).length, 0)
})

/** 终态工单明细（用于 tooltip） */
const closedIssueDetail = computed(() => {
  return closedStatuses.value
    .filter(s => getColumnIssues(s.id).length > 0)
    .map(s => `${localizeStatusName(s.name)} ${getColumnIssues(s.id).length}`)
    .join('、')
})

/** 进度指示器 aria-label */
const progressIndicatorAriaLabel = computed(() => {
  const activeCount = activeStatuses.value.reduce((sum, s) => sum + getColumnIssues(s.id).length, 0)
  return `活跃工单分布：${activeCount} 个活跃工单` + (closedIssueCount.value > 0 ? `，${closedIssueCount.value} 个已完成` : '')
})

/**
 * 有效的看板列（考虑列合并）。
 * 合并后的列：id 取 merge_group_id，name 取 merge_title，包含多个 statusIds。
 * 未合并的列：保持原样，statusIds 只有自身一个。
 */
interface EffectiveColumn {
  /** 列标识：合并列用 mergeGroupId，普通列用 statusId */
  id: string
  name: string
  color: string
  category: string
  /** 该列包含的所有状态 ID */
  statusIds: string[]
  /** 是否为合并列 */
  isMerged: boolean
  sortOrder: number
}

const effectiveColumns = computed<EffectiveColumn[]>(() => {
  const cols = visibleStatuses.value
  const merges = columnMerges.value

  if (merges.length === 0) {
    // 无合并：每个状态独占一列
    return cols.map(s => ({
      id: s.id,
      name: localizeStatusName(s.name),
      color: s.color || '',
      category: s.category || '',
      statusIds: [s.id],
      isMerged: false,
      sortOrder: s.sortOrder
    }))
  }

  // 构建 statusId → mergeGroup 的映射
  const statusToGroup = new Map<string, BoardColumnMergeGroupVO>()
  for (const group of merges) {
    for (const sid of group.statusIds) {
      statusToGroup.set(sid, group)
    }
  }

  // 遍历可见状态，生成有效列（合并组只出现一次）
  const result: EffectiveColumn[] = []
  const processedGroups = new Set<string>()

  for (const s of cols) {
    const group = statusToGroup.get(s.id)
    if (group) {
      if (!processedGroups.has(group.mergeGroupId)) {
        processedGroups.add(group.mergeGroupId)
        // 取合并组中第一个可见状态的颜色
        const firstVisibleStatus = cols.find(c => group.statusIds.includes(c.id))
        result.push({
          id: group.mergeGroupId,
          name: group.mergeTitle,
          color: firstVisibleStatus?.color || '',
          category: firstVisibleStatus?.category || '',
          statusIds: group.statusIds.filter(sid => cols.some(c => c.id === sid)),
          isMerged: true,
          sortOrder: s.sortOrder
        })
      }
      // 跳过合并组中的后续状态
    } else {
      // 普通列
      result.push({
        id: s.id,
        name: localizeStatusName(s.name),
        color: s.color || '',
        category: s.category || '',
        statusIds: [s.id],
        isMerged: false,
        sortOrder: s.sortOrder
      })
    }
  }

  return result
})

/** 获取有效列（考虑合并）中某列的所有工单 */
function getEffectiveColumnIssues(column: EffectiveColumn): BoardIssue[] {
  return issues.value.filter(i => column.statusIds.includes(i.statusId))
}

/** 获取 Swimlane 中某有效列的工单 */
function getSwimlaneEffectiveColumnIssues(laneKey: string, column: EffectiveColumn): BoardIssue[] {
  const lane = swimlanes.value.find(l => l.key === laneKey)
  if (!lane) return []
  return lane.issues.filter(i => column.statusIds.includes(i.statusId))
}

// Comma-separated visible board status IDs for Backlog exclusion
const boardStatusIdsForBacklog = computed(() => {
  return visibleStatuses.value.map(s => s.id).join(',')
})

// 隐藏列中有工单的列（用于提示 banner）
const hiddenIssueColumns = computed(() => {
  return allColumnConfigs.value.filter(c => !c.visible && c.hasHiddenIssues)
})

// 临时显示全部列（不修改持久化配置）
const showAllColumns = ref(false)

// 隐藏工单总数
const hiddenIssueTotalCount = computed(() => {
  return hiddenIssueColumns.value.reduce((sum, c) => sum + (c.issueCount || 0), 0)
})

// 隐藏列详情文字（状态名:数量）
const hiddenIssueColumnsDetail = computed(() => {
  return hiddenIssueColumns.value
    .map(c => `${localizeStatusName(c.statusName)} ${c.issueCount || 0}个`)
    .join('、')
})

// 被手动展开的空列集合
const expandedEmptyColumns = ref<Set<string>>(new Set())

// ===== 手动折叠的列（任何列，含有工单的也可以折叠） =====
const COLLAPSED_COLUMNS_KEY_PREFIX = 'tf_kanban_collapsed_columns'

function getCollapsedColumnsKey(): string {
  return selectedProject.value
    ? `${COLLAPSED_COLUMNS_KEY_PREFIX}_${selectedProject.value}`
    : COLLAPSED_COLUMNS_KEY_PREFIX
}

const collapsedColumns = ref<Set<string>>(new Set())

/** 从 localStorage 加载当前项目的折叠列状态 */
function loadCollapsedColumnsState() {
  const key = getCollapsedColumnsKey()
  const stored = localStorage.getItem(key)
  collapsedColumns.value = new Set(stored ? JSON.parse(stored) : [])
}

/** 保存折叠列状态到 localStorage */
function saveCollapsedColumnsState() {
  const key = getCollapsedColumnsKey()
  if (collapsedColumns.value.size === 0) {
    localStorage.removeItem(key)
  } else {
    localStorage.setItem(key, JSON.stringify([...collapsedColumns.value]))
  }
}

/** 判断列是否处于折叠状态（手动折叠或空列自动折叠）
 * columnId: 对于普通列为 statusId，对于合并列为 mergeGroupId
 */
function isColumnCollapsed(columnId: string): boolean {
  // 手动折叠优先级最高
  if (collapsedColumns.value.has(columnId)) return true
  // 空列且未手动展开 → 自动折叠
  const col = effectiveColumns.value.find(c => c.id === columnId)
  if (col) {
    if (getEffectiveColumnIssues(col).length === 0 && !expandedEmptyColumns.value.has(columnId)) return true
  } else {
    // fallback: 单状态列
    if (getColumnIssues(columnId).length === 0 && !expandedEmptyColumns.value.has(columnId)) return true
  }
  return false
}

/** 切换列的折叠/展开状态
 * columnId: 对于普通列为 statusId，对于合并列为 mergeGroupId
 */
function toggleColumnCollapse(columnId: string) {
  if (collapsedColumns.value.has(columnId)) {
    // 展开
    collapsedColumns.value.delete(columnId)
    expandedEmptyColumns.value.add(columnId) // 确保空列也被展开
  } else {
    // 折叠
    collapsedColumns.value.add(columnId)
    expandedEmptyColumns.value.delete(columnId)
  }
  saveCollapsedColumnsState()
}

/**
 * 获取有效列（合并列或普通列）对应的拖拽放置目标 statusId。
 * 对于合并列，使用组内第一个状态；对于普通列直接返回自身 ID。
 */
function getDropTargetStatusId(col: EffectiveColumn): string {
  return col.statusIds[0] ?? col.id
}

/**
 * 判断有效列是否为已关闭列（合并列：所有子状态均为终态才算终态）
 */
function isEffectiveColumnClosed(col: EffectiveColumn): boolean {
  if (!col.isMerged) {
    return col.category === 'done' || col.category === 'cancelled'
  }
  // 合并列：全部子状态都是终态才算终态
  return col.statusIds.every(sid => {
    const config = allColumnConfigs.value.find(c => c.statusId === sid)
    if (config) return config.statusCategory === 'done' || config.statusCategory === 'cancelled'
    return false
  })
}

/**
 * 获取有效列的预估工时（合并列：所有子状态之和）
 */
function getEffectiveColumnEstimation(col: EffectiveColumn): string {
  let total = 0
  for (const sid of col.statusIds) {
    const config = getColumnConfig(sid)
    if (config && config.totalEstimation) {
      total += Number(config.totalEstimation)
    }
  }
  if (total <= 0) return ''
  return total % 1 === 0 ? String(total) : total.toFixed(1)
}

/**
 * 获取有效列的 WIP Max（合并列：各子状态 WIP Max 之和；任一无限制则整列无限制）
 */
function getEffectiveColumnWipMax(col: EffectiveColumn): number | null {
  if (!col.isMerged) return getWipMax(col.id)
  let total = 0
  for (const sid of col.statusIds) {
    const max = getWipMax(sid)
    if (max === null) return null // 无限制
    total += max
  }
  return total || null
}

/**
 * 获取有效列的 WIP 警告状态（合并列：使用合并后的工单数和 WIP 限制计算）
 */
function getEffectiveColumnWipWarning(col: EffectiveColumn): 'wip-over' | 'wip-under' | null {
  if (!col.isMerged) return getWipWarning(col.id)
  const count = getEffectiveColumnIssues(col).length
  // 合并列：检查各子状态 WIP 限制之和
  let totalWipMin = 0
  let totalWipMax = 0
  let hasWipMax = false
  let hasWipMin = false
  for (const sid of col.statusIds) {
    const config = getColumnConfig(sid)
    if (config?.wipMax != null) { totalWipMax += config.wipMax; hasWipMax = true }
    if (config?.wipMin != null) { totalWipMin += config.wipMin; hasWipMin = true }
  }
  if (hasWipMax && count > totalWipMax) return 'wip-over'
  if (hasWipMin && count < totalWipMin) return 'wip-under'
  return null
}

/**
 * 获取有效列的 WIP CSS class
 */
function getEffectiveColumnWipClass(col: EffectiveColumn): string {
  if (!col.isMerged) return getWipClass(col.id)
  const warning = getEffectiveColumnWipWarning(col)
  if (warning === 'wip-over') return 'wip-over'
  if (warning === 'wip-under') return 'wip-under'
  return ''
}

/**
 * 判断有效列的拖拽是否允许放置
 * 合并列：只要任一子状态允许放置即可
 */
function isEffectiveColumnDropAllowed(col: EffectiveColumn): boolean {
  return col.statusIds.some(sid => isDropAllowed(sid))
}

// ===== Progress Indicator（各列卡片数 mini bar chart） =====

/** 计算进度条高度（相对活跃状态中最大列的比例） */
function getProgressBarHeight(statusId: string): string {
  const count = getColumnIssues(statusId).length
  if (count === 0) return '2px'
  const maxCount = Math.max(...activeStatuses.value.map(s => getColumnIssues(s.id).length), 1)
  const height = Math.max(4, Math.round((count / maxCount) * 24))
  return `${height}px`
}

/** 计算终态汇总柱高度（相对活跃状态按比例缩放，但上限为 16px 以弱化视觉） */
function getClosedProgressBarHeight(): string {
  if (closedIssueCount.value === 0) return '2px'
  const maxActiveCount = Math.max(...activeStatuses.value.map(s => getColumnIssues(s.id).length), 1)
  // 使用对数缩放 — 终态工单再多也不会压过活跃柱形
  const ratio = Math.min(closedIssueCount.value / maxActiveCount, 3)
  const height = Math.max(4, Math.min(16, Math.round(ratio * 8)))
  return `${height}px`
}

/** 滚动到指定列（支持普通列和合并列）
 * statusId: 可以是普通状态 ID 或合并列 ID（mergeGroupId）
 */
function scrollToColumn(statusId: string) {
  // 查找包含此 statusId 的有效列（可能是合并列）
  const col = effectiveColumns.value.find(c => c.statusIds.includes(statusId)) ||
              effectiveColumns.value.find(c => c.id === statusId)
  const targetId = col ? col.id : statusId

  // 如果列被折叠，先展开
  if (collapsedColumns.value.has(targetId)) {
    toggleColumnCollapse(targetId)
  }
  // 在下一帧滚动到目标列
  setTimeout(() => {
    const container = document.querySelector('.board-container') || document.querySelector('.swimlane-container')
    if (!container) return
    const columnEl = container.querySelector(`[data-column-id="${targetId}"]`)
    if (columnEl) {
      columnEl.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' })
    }
  }, 50)
}

// ===== 拖拽状态 =====
const draggingIssue = ref<BoardIssue | null>(null)
const dragOverColumnId = ref<string | null>(null)
const allowedTargetStatuses = ref<Set<string>>(new Set())
const requireCommentStatuses = ref<Set<string>>(new Set())
const transitioningIssueIds = ref<Set<string>>(new Set())

// Combined dragging state (from board card or backlog)
const isDragging = computed(() => !!draggingIssue.value || !!backlogDraggingIssue.value)

// ===== 可拖拽源状态 =====
const transitionableSourceStatuses = ref<Set<string>>(new Set())

// ===== 撤销历史 =====
interface UndoEntry {
  issueId: string
  issueKey: string
  oldStatusId: string
  newStatusId: string
  oldStatusName: string
  newStatusName: string
  timestamp: number
}
const undoStack = ref<UndoEntry[]>([])
const UNDO_TIMEOUT = 10000

function getColumnIssues(statusId: string): BoardIssue[] {
  let columnIssues: BoardIssue[]
  if (boardColumnField.value === 'priority') {
    // Priority mode: statusId parameter is actually the priority value (Critical/High/Normal/Low)
    columnIssues = issues.value.filter(i => (i.priority || 'Normal') === statusId)
  } else {
    // Default: status mode
    columnIssues = issues.value.filter(i => i.statusId === statusId)
  }

  // Apply manual order if available and not disabled
  if (boardManualSorted.value && !isManualSortDisabled.value) {
    const { sorted, rest } = applyBoardManualOrder(columnIssues)
    return [...sorted, ...rest]
  }

  return columnIssues
}

// ===== WIP 限制辅助函数 =====

function getColumnConfig(statusId: string): BoardColumnVO | undefined {
  if (boardColumnField.value === 'priority') {
    // 优先级模式：statusId 参数实际是 priority 值（Critical/High/Normal/Low）
    // 需按 fieldValue 查找，且这些列的 statusId 为 null
    return allColumnConfigs.value.find(c => c.fieldValue === statusId && c.statusId == null)
  }
  return allColumnConfigs.value.find(c => c.statusId === statusId)
}

function getWipMin(statusId: string): number | null {
  return getColumnConfig(statusId)?.wipMin ?? null
}

function getWipMax(statusId: string): number | null {
  return getColumnConfig(statusId)?.wipMax ?? null
}

/**
 * 判断列的 WIP 状态：'wip-over' | 'wip-under' | null
 */
function getWipWarning(statusId: string): 'wip-over' | 'wip-under' | null {
  const config = getColumnConfig(statusId)
  if (!config) return null
  const count = getColumnIssues(statusId).length
  if (config.wipMax != null && count > config.wipMax) return 'wip-over'
  if (config.wipMin != null && count < config.wipMin) return 'wip-under'
  return null
}

/**
 * 列计数的 CSS class（用于颜色变化）
 */
function getWipClass(statusId: string): string {
  const warning = getWipWarning(statusId)
  if (warning === 'wip-over') return 'column-count--over'
  if (warning === 'wip-under') return 'column-count--under'
  return ''
}

/**
 * WIP tooltip 文本
 */
function getWipTooltip(statusId: string): string {
  const config = getColumnConfig(statusId)
  if (!config) return ''
  const count = getColumnIssues(statusId).length
  const parts: string[] = []
  if (config.wipMin != null) parts.push(`最小: ${config.wipMin}`)
  if (config.wipMax != null) parts.push(`最大: ${config.wipMax}`)
  if (parts.length === 0) return `${count} 个工单`
  const warning = getWipWarning(statusId)
  let suffix = ''
  if (warning === 'wip-over') suffix = ' ⚠️ 超出限制'
  if (warning === 'wip-under') suffix = ' ⚠️ 低于最小值'
  return `${count} 个工单 (${parts.join(', ')})${suffix}`
}

/**
 * 获取列的预估工时总和（从列配置数据中读取）
 */
function getColumnEstimation(statusId: string): string {
  const config = getColumnConfig(statusId)
  if (!config || !config.totalEstimation) return ''
  // 格式化：去除尾部多余的零
  const val = Number(config.totalEstimation)
  if (val <= 0) return ''
  return val % 1 === 0 ? String(val) : val.toFixed(1)
}

/**
 * 获取看板总预估工时（所有可见列的 totalEstimation 总和）
 */
const boardTotalEstimation = computed(() => {
  if (!allColumnConfigs.value || allColumnConfigs.value.length === 0) return 0
  let total = 0
  for (const col of allColumnConfigs.value) {
    if (col.visible && col.totalEstimation) {
      total += Number(col.totalEstimation)
    }
  }
  return total
})

function expandColumn(statusId: string) {
  collapsedColumns.value.delete(statusId)
  expandedEmptyColumns.value.add(statusId)
  saveCollapsedColumnsState()
}

function collapseColumn(statusId: string) {
  collapsedColumns.value.add(statusId)
  expandedEmptyColumns.value.delete(statusId)
  saveCollapsedColumnsState()
}

function priorityIcon(priority: string): string {
  const map: Record<string, string> = { Critical: '🔴', High: '🟠', Normal: '🔵', Low: '⚪' }
  return map[priority] || '🔵'
}

function typeLabel(type: string): string {
  return localizeIssueType(type)
}

/**
 * 返回工单类型的首字母缩写（用于 Initial 显示模式）。
 */
function typeInitial(type: string): string {
  if (!type) return '?'
  const label = localizeIssueType(type)
  return label.charAt(0).toUpperCase()
}

function openIssue(issue: BoardIssue) {
  if (draggingIssue.value) return
  openPreview(issue)
}

/**
 * 卡片单击处理：
 * - Ctrl/Meta + Click：切换选中状态（多选）
 * - 无修饰键：延迟 200ms 打开预览（被双击取消则不打开）
 */
let clickTimer: ReturnType<typeof setTimeout> | null = null

function onCardClick(event: MouseEvent | KeyboardEvent, issue: BoardIssue) {
  if (draggingIssue.value) return

  if (event instanceof MouseEvent && (event.ctrlKey || event.metaKey)) {
    // Ctrl+Click: toggle this card's selection (batch mode)
    toggleCardSelection(issue.id)
  } else if (selectedCount.value > 0) {
    // Already have selection: toggle this card (stay in batch mode)
    toggleCardSelection(issue.id)
  } else {
    // No modifier, no existing selection: delay to distinguish from double-click
    if (clickTimer) clearTimeout(clickTimer)
    clickTimer = setTimeout(() => {
      openPreview(issue)
      clickTimer = null
    }, 200)
  }
}

/**
 * 卡片双击：跳转详情页（取消单击的预览）
 */
function onCardDblClick(issue: BoardIssue) {
  if (draggingIssue.value) return
  // Cancel the pending single-click preview
  if (clickTimer) {
    clearTimeout(clickTimer)
    clickTimer = null
  }
  // Close preview if open
  previewVisible.value = false
  router.push({ name: 'IssueDetail', params: { id: issue.id } })
}

/**
 * 卡片 Enter 键：若有选中则切换选中状态，否则打开预览
 * Space 键：打开预览（保持与单击一致）
 */
function onCardKeydown(event: KeyboardEvent, issue: BoardIssue) {
  if (event.key === 'Enter') {
    if (selectedCount.value > 0 || event.ctrlKey || event.metaKey) {
      toggleCardSelection(issue.id)
    } else {
      openPreview(issue)
    }
  }
  if (event.key === ' ') {
    event.preventDefault()
    openPreview(issue)
  }
  // Escape 清空选择或关闭预览
  if (event.key === 'Escape') {
    if (selectedCount.value > 0) {
      clearSelection()
    } else if (previewVisible.value) {
      closePreview()
    }
  }
}

// ===== 拖拽逻辑 =====

function isCardDraggable(issue: BoardIssue): boolean {
  // Priority mode: always draggable (no workflow constraint, just needs edit permission)
  if (boardColumnField.value === 'priority') return true
  if (!canChangeStatus.value) return false
  if (transitionableSourceStatuses.value.size === 0) return true
  return transitionableSourceStatuses.value.has(issue.statusId)
}

function onDragStart(event: DragEvent, issue: BoardIssue) {
  if (!isCardDraggable(issue)) {
    event.preventDefault()
    Message.warning('该工单当前状态不允许变更')
    return
  }

  draggingIssue.value = issue

  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', issue.id)
  }

  // Priority mode: all columns are valid targets (no workflow constraint)
  if (boardColumnField.value === 'priority') {
    allowedTargetStatuses.value = new Set(visibleStatuses.value.map(s => s.id))
    return
  }

  // Optimistic: allow all statuses immediately so drag feedback works instantly.
  // The actual workflow validation happens server-side during the drop (transitStatus API).
  // We still fetch transitions async to show correct drop-forbidden indicators once loaded.
  allowedTargetStatuses.value = new Set(statuses.value.map(s => s.id))
  requireCommentStatuses.value = new Set()

  // Fetch actual allowed transitions asynchronously (non-blocking).
  // This refines the visual feedback (green/red indicators) once the API responds,
  // but does NOT block the drag initiation — fixing Playwright/browser timing issues.
  issueApi.getAvailableTransitions(issue.id).then(res => {
    // Only update if this issue is still being dragged (user hasn't dropped yet)
    if (draggingIssue.value?.id === issue.id) {
      const allowed = res.data || []
      allowedTargetStatuses.value = new Set(allowed.map(s => s.id))
      requireCommentStatuses.value = new Set(allowed.filter(s => s.requireComment).map(s => s.id))
    }
  }).catch(() => {
    // On failure, keep all statuses allowed — backend will reject invalid transitions
    if (draggingIssue.value?.id === issue.id) {
      allowedTargetStatuses.value = new Set(statuses.value.map(s => s.id))
      requireCommentStatuses.value = new Set()
    }
  })
}

function onDragEnd() {
  draggingIssue.value = null
  dragOverColumnId.value = null
  dragOverSwimlaneKey.value = null
  allowedTargetStatuses.value.clear()
  requireCommentStatuses.value.clear()
}

function onDragOver(event: DragEvent, statusId: string) {
  event.preventDefault()
  dragOverColumnId.value = statusId
  dragOverSwimlaneKey.value = null

  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = isDropAllowed(statusId) ? 'move' : 'none'
  }
}

function onDragLeave(event: DragEvent) {
  const relatedTarget = event.relatedTarget as HTMLElement | null
  const currentTarget = event.currentTarget as HTMLElement
  if (relatedTarget && currentTarget.contains(relatedTarget)) return
  dragOverColumnId.value = null
  dragOverSwimlaneKey.value = null
}

function isDropAllowed(targetStatusId: string): boolean {
  // Priority mode: always allow (no workflow restriction, just check not same column)
  if (boardColumnField.value === 'priority') {
    if (!draggingIssue.value) return false
    // Allow within-column drop for reordering (when manual sort enabled)
    if ((draggingIssue.value.priority || 'Normal') === targetStatusId) {
      return !isManualSortDisabled.value
    }
    return true
  }
  // Allow drop from backlog panel
  if (backlogDraggingIssue.value) {
    return allowedTargetStatuses.value.has(targetStatusId)
  }
  if (!draggingIssue.value) return false
  // Allow within-column drop for reordering (when manual sort is not disabled)
  if (draggingIssue.value.statusId === targetStatusId) {
    return !isManualSortDisabled.value
  }
  return allowedTargetStatuses.value.has(targetStatusId)
}

async function onDrop(event: DragEvent, targetStatusId: string) {
  event.preventDefault()

  // ★ Save the target swimlane key BEFORE clearing (for cross-swimlane drop handling)
  const targetLaneKey = dragOverSwimlaneKey.value

  dragOverColumnId.value = null
  dragOverSwimlaneKey.value = null

  // Check if this is a backlog drop
  const backlogIssue = backlogDraggingIssue.value
  if (backlogIssue) {
    await handleBacklogDrop(backlogIssue, targetStatusId)
    return
  }

  const issue = draggingIssue.value
  if (!issue || !isDropAllowed(targetStatusId)) {
    onDragEnd()
    return
  }

  // Within-column drop: reorder card (manual sorting)
  const isWithinColumnDrop = (boardColumnField.value === 'priority')
    ? (issue.priority || 'Normal') === targetStatusId
    : issue.statusId === targetStatusId

  if (isWithinColumnDrop) {
    // ★ Even if same column, check for cross-swimlane movement (YouTrack behavior:
    //    dragging to different row same column → only update swimlane field)
    if (targetLaneKey && swimlaneGroupBy.value !== 'none') {
      // Determine current lane key for the issue
      let currentLaneKey: string | null = null
      switch (swimlaneGroupBy.value) {
        case 'assignee': currentLaneKey = issue.assigneeId || '__unassigned__'; break
        case 'priority': currentLaneKey = issue.priority || 'Normal'; break
        case 'type': currentLaneKey = issue.issueType; break
        case 'sprint': currentLaneKey = issue.sprintId || '__no_sprint__'; break
        case 'tag': currentLaneKey = null; break
        case 'parent': currentLaneKey = (issue as any).parentId || '__uncategorized__'; break
      }
      if (currentLaneKey !== targetLaneKey) {
        // Cross-swimlane, same column → update swimlane field only
        draggingIssue.value = null
        allowedTargetStatuses.value.clear()
        await handleCrossSwimlaneUpdate(issue, targetLaneKey)
        return
      }
    }
    // True within-column drop (same column, same swimlane) → reorder
    if (!isManualSortDisabled.value) {
      await handleWithinColumnReorder(issue, targetStatusId, event)
      return
    }
  }

  // Priority mode: update priority field instead of status transition
  if (boardColumnField.value === 'priority') {
    const oldPriority = issue.priority || 'Normal'
    const targetPriority = targetStatusId // In priority mode, targetStatusId is the priority value
    if (oldPriority === targetPriority) {
      onDragEnd()
      return
    }

    // WIP 限制检查（本地）
    const targetColConfig = getColumnConfig(targetPriority)
    if (targetColConfig?.wipMax != null) {
      const currentCount = getColumnIssues(targetPriority).length
      if (currentCount >= targetColConfig.wipMax) {
        // WIP 超限：弹出确认框（与状态模式一致的交互）
        draggingIssue.value = null
        allowedTargetStatuses.value.clear()
        Modal.warning({
          title: 'WIP 限制',
          content: `目标优先级列「${localizePriority(targetPriority)}」已达到 WIP 上限（${currentCount}/${targetColConfig.wipMax}），确定要继续移入吗？`,
          okText: '继续移入',
          cancelText: '取消',
          hideCancel: false,
          onOk: async () => {
            // 用户确认后执行（带 forceWip=true）
            issue.priority = targetPriority
            try {
              await issueApi.update(issue.id, { priority: targetPriority, forceWip: true })
              issue.version = (issue.version || 0) + 1
              Message.success(`${issue.issueKey} 优先级已变更为「${localizePriority(targetPriority)}」`)
              await handleCrossSwimlaneUpdate(issue, targetLaneKey)
            } catch (e: any) {
              issue.priority = oldPriority
              Message.error(`优先级变更失败：${e.response?.data?.message || '未知错误'}`)
            }
          }
        })
        return
      }
    }

    // Optimistic update
    issue.priority = targetPriority
    draggingIssue.value = null
    allowedTargetStatuses.value.clear()
    try {
      await issueApi.update(issue.id, { priority: targetPriority })
      issue.version = (issue.version || 0) + 1
      Message.success(`${issue.issueKey} 优先级已变更为「${localizePriority(targetPriority)}」`)
      // ★ Cross-swimlane field update in priority mode
      await handleCrossSwimlaneUpdate(issue, targetLaneKey)
    } catch (e: any) {
      if (e.response?.data?.code === ERROR_CODES.WIP_LIMIT_EXCEEDED) {
        // 后端防御性 WIP 校验触发（前端遗漏的情况）
        issue.priority = oldPriority
        Modal.warning({
          title: 'WIP 限制',
          content: e.response.data.message,
          okText: '继续移入',
          cancelText: '取消',
          hideCancel: false,
          onOk: async () => {
            issue.priority = targetPriority
            try {
              await issueApi.update(issue.id, { priority: targetPriority, forceWip: true })
              issue.version = (issue.version || 0) + 1
              Message.success(`${issue.issueKey} 优先级已变更为「${localizePriority(targetPriority)}」`)
              await handleCrossSwimlaneUpdate(issue, targetLaneKey)
            } catch (e2: any) {
              issue.priority = oldPriority
              Message.error(`优先级变更失败：${e2.response?.data?.message || '未知错误'}`)
            }
          }
        })
      } else {
        issue.priority = oldPriority
        Message.error(`优先级变更失败：${e.response?.data?.message || '未知错误'}`)
      }
    }
    return
  }

  const oldStatusId = issue.statusId
  const targetStatus = statuses.value.find(s => s.id === targetStatusId)

  // If the target status requires a comment, prompt the user before executing
  if (requireCommentStatuses.value.has(targetStatusId)) {
    draggingIssue.value = null
    allowedTargetStatuses.value.clear()
    requireCommentStatuses.value.clear()
    let commentText = ''
    Modal.confirm({
      title: '状态变更 — 请填写理由',
      content: () => h('div', { style: 'display:flex;flex-direction:column;gap:8px' }, [
        h('div', { style: 'display:flex;align-items:center;gap:6px' }, [
          h('span', { style: 'color:var(--color-text-3);font-size:13px' }, '目标状态：'),
          h('span', { style: `background:${targetStatus?.color || '#6b7280'};color:#fff;padding:2px 8px;border-radius:3px;font-size:12px` }, localizeStatusName(targetStatus?.name || ''))
        ]),
        h('textarea', {
          placeholder: '请说明退回/变更的原因（必填）',
          style: 'width:100%;min-height:80px;margin-top:8px;padding:8px;border:1px solid var(--color-border-2);border-radius:4px;resize:vertical;font-size:13px;background:var(--color-bg-2);color:var(--color-text-1)',
          onInput: (e: Event) => { commentText = (e.target as HTMLTextAreaElement).value }
        })
      ]),
      okText: '确认变更',
      cancelText: '取消',
      width: 480,
      onBeforeOk: () => {
        if (!commentText.trim()) {
          Message.warning('请填写变更理由')
          return false
        }
        return true
      },
      onOk: async () => {
        issue.statusId = targetStatusId
        transitioningIssueIds.value.add(issue.id)
        try {
          const res = await issueApi.transitStatus(issue.id, targetStatusId, commentText.trim(), issue.version)
          if (res.code === 0) {
            const newVersion = extractVersion(res.data)
            if (newVersion != null) issue.version = newVersion
            else issue.version = (issue.version || 0) + 1
            showActionFeedback(res.data)
            pushUndoNotification(issue, oldStatusId, targetStatusId, targetStatus)
            // ★ Cross-swimlane field update after comment-required transition
            await handleCrossSwimlaneUpdate(issue, targetLaneKey)
          } else {
            issue.statusId = oldStatusId
            Message.error(res.message || '状态变更失败')
          }
        } catch (e: any) {
          issue.statusId = oldStatusId
          Message.error(e.response?.data?.message || '状态变更失败')
        } finally {
          transitioningIssueIds.value.delete(issue.id)
        }
      }
    })
    return
  }

  // 乐观更新
  issue.statusId = targetStatusId
  transitioningIssueIds.value.add(issue.id)

  draggingIssue.value = null
  allowedTargetStatuses.value.clear()

  try {
    const res = await issueApi.transitStatus(issue.id, targetStatusId, undefined, issue.version)

    if (res.code === ERROR_CODES.WIP_LIMIT_EXCEEDED) {
      // WIP 超限：回滚乐观更新，弹确认框
      issue.statusId = oldStatusId
      transitioningIssueIds.value.delete(issue.id)
      Modal.warning({
        title: 'WIP 限制',
        content: res.message,
        okText: '继续移入',
        cancelText: '取消',
        hideCancel: false,
        onOk: async () => {
          // 用户确认后重试（带 forceWip）
          issue.statusId = targetStatusId
          transitioningIssueIds.value.add(issue.id)
          try {
            const forceRes = await issueApi.transitStatus(issue.id, targetStatusId, undefined, issue.version, undefined, true)
            if (forceRes.code === 0) {
              const newVersion = extractVersion(forceRes.data)
              if (newVersion != null) issue.version = newVersion
              else issue.version = (issue.version || 0) + 1
              showActionFeedback(forceRes.data)
              pushUndoNotification(issue, oldStatusId, targetStatusId, targetStatus)
              // ★ Cross-swimlane field update after WIP force transition
              await handleCrossSwimlaneUpdate(issue, targetLaneKey)
            } else {
              issue.statusId = oldStatusId
              Message.error(forceRes.message || '状态变更失败')
            }
          } catch (e2: any) {
            issue.statusId = oldStatusId
            Message.error(e2.response?.data?.message || '状态变更失败')
          } finally {
            transitioningIssueIds.value.delete(issue.id)
          }
        }
      })
      return
    }

    if (res.code !== 0) {
      // 其他非成功响应
      issue.statusId = oldStatusId
      Message.error(res.message || '状态变更失败')
      transitioningIssueIds.value.delete(issue.id)
      return
    }

    // 同步更新本地版本号（后端返回 TransitStatusResultVO）
    const newVersion = extractVersion(res.data)
    if (newVersion != null) {
      issue.version = newVersion
    } else {
      // fallback: 本地递增
      issue.version = (issue.version || 0) + 1
    }

    // 显示自动分配反馈
    showActionFeedback(res.data)

    pushUndoNotification(issue, oldStatusId, targetStatusId, targetStatus)

    // ★ Cross-swimlane field update (YouTrack behavior: drag to different row updates swimlane field)
    await handleCrossSwimlaneUpdate(issue, targetLaneKey)
  } catch (e: any) {
    issue.statusId = oldStatusId
    const errMsg = e.response?.data?.message || '状态变更失败'
    Message.error(`${issue.issueKey} 移动失败：${errMsg}`)
  } finally {
    transitioningIssueIds.value.delete(issue.id)
  }
}

/**
 * Handle cross-swimlane drop: update the swimlane field value.
 * Per YouTrack behavior:
 * - When swimlane is by field value (assignee/priority/type/sprint),
 *   dragging a card to another swimlane row updates that field.
 * - Returns true if a cross-swimlane update was performed.
 */
async function handleCrossSwimlaneUpdate(issue: BoardIssue, targetLaneKey: string | null): Promise<boolean> {
  // No swimlane mode or no target lane → nothing to do
  if (!targetLaneKey || swimlaneGroupBy.value === 'none') return false

  // Determine the issue's current swimlane key based on the swimlaneGroupBy dimension
  let currentLaneKey: string | null = null
  switch (swimlaneGroupBy.value) {
    case 'assignee':
      currentLaneKey = issue.assigneeId || '__unassigned__'
      break
    case 'priority':
      currentLaneKey = issue.priority || 'Normal'
      break
    case 'type':
      currentLaneKey = issue.issueType
      break
    case 'sprint':
      currentLaneKey = issue.sprintId || '__no_sprint__'
      break
    case 'tag':
      // Tag swimlane uses tag id as key; complex to handle — skip for now
      return false
    case 'parent':
      currentLaneKey = (issue as any).parentId || '__uncategorized__'
      break
    case 'dueDate':
      // 日期泳道为只读：拖拽不更新截止日期（日期按相对范围分组，不是可选泳道值）
      return false
  }

  // If the issue is already in the target swimlane, nothing to do
  if (currentLaneKey === targetLaneKey) return false

  // Build the update payload based on swimlaneGroupBy
  const updateData: Record<string, any> = {}
  switch (swimlaneGroupBy.value) {
    case 'assignee':
      // Special keys like '__unassigned__' mean set to null
      if (targetLaneKey === '__unassigned__' || targetLaneKey === '__uncategorized__') {
        updateData.assigneeId = null
      } else {
        updateData.assigneeId = targetLaneKey
      }
      break
    case 'priority':
      updateData.priority = targetLaneKey
      break
    case 'type':
      updateData.issueType = targetLaneKey
      break
    case 'sprint':
      if (targetLaneKey === '__no_sprint__' || targetLaneKey === '__uncategorized__') {
        updateData.sprintId = null
      } else if (allowMultipleSprints.value) {
        // 多 Sprint 模式：追加而非覆盖
        updateData.addToSprintId = targetLaneKey
      } else {
        updateData.sprintId = targetLaneKey
      }
      break
    case 'parent':
      // Moving to a parent swimlane sets the parentId; moving to uncategorized clears it
      if (targetLaneKey === '__uncategorized__') {
        updateData.parentId = null
      } else {
        updateData.parentId = targetLaneKey
      }
      break
    default:
      return false
  }

  // Perform the update (optimistic + API call)
  // Optimistic update - apply changes locally first
  const rollbackData: Record<string, any> = {}
  if ('assigneeId' in updateData) {
    rollbackData.assigneeId = issue.assigneeId
    rollbackData.assigneeName = issue.assigneeName
    issue.assigneeId = updateData.assigneeId || undefined
    // We don't know the assignee name for optimistic update;
    // it will be resolved after reload or from projectMembers
    if (updateData.assigneeId) {
      const member = projectMembers.value.find(m => m.userId === updateData.assigneeId)
      issue.assigneeName = member?.displayName || ''
    } else {
      issue.assigneeName = undefined
    }
  }
  if ('priority' in updateData) {
    rollbackData.priority = issue.priority
    issue.priority = updateData.priority
  }
  if ('issueType' in updateData) {
    rollbackData.issueType = issue.issueType
    issue.issueType = updateData.issueType
  }
  if ('sprintId' in updateData) {
    rollbackData.sprintId = issue.sprintId
    issue.sprintId = updateData.sprintId || undefined
  }
  if ('addToSprintId' in updateData) {
    // 多 Sprint 模式：乐观更新本地显示（让卡片移动到目标泳道）
    rollbackData.sprintId = issue.sprintId
    issue.sprintId = updateData.addToSprintId || undefined
  }
  if ('parentId' in updateData) {
    rollbackData.parentId = (issue as any).parentId
    ;(issue as any).parentId = updateData.parentId || undefined
  }

  try {
    const updateRes = await issueApi.update(issue.id, updateData)
    if (updateRes.warnings?.length) {
      updateRes.warnings.forEach((w: string) => Message.warning({ content: w, duration: 5000 }))
      // Rollback optimistic update for skipped fields
      if ('sprintId' in rollbackData) {
        issue.sprintId = rollbackData.sprintId
      }
      return false
    }
    issue.version = (issue.version || 0) + 1
    // Build a descriptive message for the swimlane change
    const fieldLabel = swimlaneGroupBy.value === 'assignee' ? '负责人'
      : swimlaneGroupBy.value === 'priority' ? '优先级'
      : swimlaneGroupBy.value === 'type' ? '类型'
      : swimlaneGroupBy.value === 'sprint' ? '迭代'
      : swimlaneGroupBy.value === 'parent' ? '父工单' : ''
    if (fieldLabel) {
      Message.success(`${issue.issueKey} ${fieldLabel}已更新`)
    }
    return true
  } catch (e: any) {
    // Rollback optimistic update
    if ('assigneeId' in rollbackData) {
      issue.assigneeId = rollbackData.assigneeId
      issue.assigneeName = rollbackData.assigneeName
    }
    if ('priority' in rollbackData) {
      issue.priority = rollbackData.priority
    }
    if ('issueType' in rollbackData) {
      issue.issueType = rollbackData.issueType
    }
    if ('sprintId' in rollbackData) {
      issue.sprintId = rollbackData.sprintId
    }
    if ('parentId' in rollbackData) {
      ;(issue as any).parentId = rollbackData.parentId
    }
    const errMsg = e.response?.data?.message || '字段更新失败'
    Message.error(`${issue.issueKey} 跨泳道更新失败：${errMsg}`)
    return false
  }
}

/** Handle within-column drop: reorder card using manual order API */
async function handleWithinColumnReorder(issue: BoardIssue, columnId: string, event: DragEvent) {
  // Determine the drop target position within the column
  const columnIssues = getColumnIssues(columnId)
  const draggedIndex = columnIssues.findIndex(i => i.id === issue.id)

  // Get the drop target element to determine position
  const dropTarget = event.target as HTMLElement | null
  let targetIndex = columnIssues.length - 1 // default: drop at end

  if (dropTarget) {
    // Find the closest card element to determine insertion point
    const cardEl = dropTarget.closest('.kanban-card') as HTMLElement | null
    if (cardEl) {
      // Find which issue this card belongs to
      const cardIndex = columnIssues.findIndex(i => {
        // Use issue key to match (card-key contains it)
        const keyEl = cardEl.querySelector('.card-key')
        return keyEl && keyEl.textContent === i.issueKey
      })
      if (cardIndex >= 0 && cardIndex !== draggedIndex) {
        // Determine if dropped above or below the target card
        const rect = cardEl.getBoundingClientRect()
        const dropY = event.clientY
        const midY = rect.top + rect.height / 2
        targetIndex = dropY < midY ? cardIndex : cardIndex + 1
        if (targetIndex > draggedIndex) targetIndex-- // Adjust for removal
      }
    }
  }

  // Only reorder if position actually changed
  if (draggedIndex === targetIndex) {
    onDragEnd()
    return
  }

  // Build new order for all issues (not just this column)
  const allIssueIds = issues.value.map(i => i.id)

  // Reorder within the column: remove from old position, insert at new position
  const reorderedColumn = [...columnIssues]
  const [moved] = reorderedColumn.splice(draggedIndex, 1)
  reorderedColumn.splice(targetIndex, 0, moved)

  // Build the full issue order list (preserving order of other columns, updating this column)
  const columnIssueSet = new Set(columnIssues.map(i => i.id))
  const fullOrder: string[] = []
  let columnInserted = false

  for (const id of allIssueIds) {
    if (columnIssueSet.has(id)) {
      if (!columnInserted) {
        // Insert all reordered column issues at the position of the first column issue
        fullOrder.push(...reorderedColumn.map(i => i.id))
        columnInserted = true
      }
      // Skip individual column issues (they're already added in order)
    } else {
      fullOrder.push(id)
    }
  }

  // End drag state
  draggingIssue.value = null
  allowedTargetStatuses.value.clear()

  // Save the new order via API
  try {
    await saveBoardManualOrder(fullOrder)
    // Force re-render by updating the issues array order
    const orderMap = new Map<string, number>()
    fullOrder.forEach((id, idx) => orderMap.set(id, idx))
    issues.value.sort((a, b) => (orderMap.get(a.id) ?? 0) - (orderMap.get(b.id) ?? 0))
  } catch {
    // Order save failed - the composable already shows an error message
  }
}

/** Handle drop from Backlog panel: assign sprint + change status */
async function handleBacklogDrop(issue: BoardIssue, targetStatusId: string) {
  backlogDraggingIssue.value = null
  allowedTargetStatuses.value.clear()

  // Determine which sprint to assign
  const targetSprintId = selectedSprint.value || getActiveSprintId()
  if (!targetSprintId) {
    Message.warning('请先选择一个 Sprint 或确保项目有活跃的 Sprint')
    return
  }

  const targetStatus = statuses.value.find(s => s.id === targetStatusId)

  /**
   * 回滚：撤销已执行的 Sprint 分配，并展示错误信息（errorMessage 为 null 时静默回滚）
   */
  async function rollbackSprintAssignment(errorMessage: string | null) {
    try {
      // 多 Sprint 模式下移除刚追加的关联，否则清空主 Sprint
      const rollbackPayload = (allowMultipleSprints.value && issue.sprintId)
        ? { removeFromSprintId: targetSprintId }
        : { sprintId: null }
      await issueApi.update(issue.id, rollbackPayload)
    } catch {
      // best-effort rollback，忽略错误
    }
    if (errorMessage) {
      Message.error(`${issue.issueKey} 状态变更被拒绝：${errorMessage}`)
    }
  }

  /**
   * 成功路径：将工单从 Backlog 移入看板并更新本地状态
   */
  function finalizeBacklogDrop(res: R<TransitStatusResultVO>) {
    const extracted = extractVersion(res.data)
    const newVersion = extracted != null ? extracted : (issue.version || 0) + 2
    showActionFeedback(res.data)

    // Remove from backlog panel
    backlogPanelRef.value?.removeIssue(issue.id)

    // Add to board issues list with updated status/sprint/version
    const updatedIssue: BoardIssue = {
      ...issue,
      statusId: targetStatusId,
      sprintId: targetSprintId,
      version: newVersion,
    }
    issues.value.push(updatedIssue)

    Message.success(`${issue.issueKey} 已添加到看板「${localizeStatusName(targetStatus?.name)}」`)
  }

  try {
    // Step 1: Assign sprint — use addToSprintId in multi-sprint mode (preserves existing associations)
    const sprintPayload = (allowMultipleSprints.value && issue.sprintId)
      ? { addToSprintId: targetSprintId }
      : { sprintId: targetSprintId }
    await issueApi.update(issue.id, sprintPayload)

    // Step 2: Transition status (if different from current)
    if (issue.statusId !== targetStatusId) {
      const res = await issueApi.transitStatus(issue.id, targetStatusId, undefined, issue.version)

      // ★ Handle WIP limit exceeded — same UX as column-to-column drag
      if (res.code === ERROR_CODES.WIP_LIMIT_EXCEEDED) {
        Modal.warning({
          title: 'WIP 限制',
          content: res.message,
          okText: '继续移入',
          cancelText: '取消',
          hideCancel: false,
          onOk: async () => {
            try {
              const forceRes = await issueApi.transitStatus(issue.id, targetStatusId, undefined, issue.version, undefined, true)
              if (forceRes.code === 0) {
                finalizeBacklogDrop(forceRes)
              } else {
                await rollbackSprintAssignment(forceRes.message || '状态变更失败')
              }
            } catch (e2: any) {
              await rollbackSprintAssignment(e2.response?.data?.message || '状态变更失败')
            }
          },
          onCancel: async () => {
            // User cancelled — rollback the sprint assignment (no error message needed)
            await rollbackSprintAssignment(null)
          },
        })
        return
      }

      // ★ Handle close confirmation required — prompt user to force close
      if (res.code === ERROR_CODES.CLOSE_CONFIRMATION_REQUIRED) {
        Modal.warning({
          title: '确认关闭',
          content: res.message,
          okText: '强制关闭',
          cancelText: '取消',
          hideCancel: false,
          onOk: async () => {
            try {
              const forceRes = await issueApi.transitStatus(issue.id, targetStatusId, undefined, issue.version, true)
              if (forceRes.code === 0) {
                finalizeBacklogDrop(forceRes)
              } else {
                await rollbackSprintAssignment(forceRes.message || '状态变更失败')
              }
            } catch (e2: any) {
              await rollbackSprintAssignment(e2.response?.data?.message || '状态变更失败')
            }
          },
          onCancel: async () => {
            await rollbackSprintAssignment(null)
          },
        })
        return
      }

      // ★ Any other non-success code: rollback sprint assignment
      if (res.code !== 0) {
        await rollbackSprintAssignment(res.message || '状态变更失败')
        return
      }

      // Success path
      finalizeBacklogDrop(res)
    } else {
      // Status unchanged, only sprint was updated — version +1
      const newVersion = (issue.version || 0) + 1

      // Remove from backlog panel
      backlogPanelRef.value?.removeIssue(issue.id)

      // Add to board issues list
      const updatedIssue: BoardIssue = {
        ...issue,
        sprintId: targetSprintId,
        version: newVersion,
      }
      issues.value.push(updatedIssue)

      Message.success(`${issue.issueKey} 已添加到看板「${localizeStatusName(targetStatus?.name)}」`)
    }
  } catch (e: any) {
    const errMsg = e.response?.data?.message || '操作失败'
    Message.error(`${issue.issueKey} 移入看板失败：${errMsg}`)
  }
}

/** Get the active sprint ID for the current project */
function getActiveSprintId(): string | undefined {
  return activeSprint.value?.id
}

// ===== 撤销逻辑 =====

/** 推送撤销通知（复用于正常拖拽和强制 WIP 确认后的成功路径） */
function pushUndoNotification(issue: BoardIssue, oldStatusId: string, newStatusId: string, targetStatus: IssueStatusVO | undefined) {
  const undoEntry: UndoEntry = {
    issueId: issue.id,
    issueKey: issue.issueKey,
    oldStatusId: oldStatusId,
    newStatusId: newStatusId,
    oldStatusName: statuses.value.find(s => s.id === oldStatusId)?.name || '',
    newStatusName: targetStatus?.name || '',
    timestamp: Date.now()
  }
  undoStack.value.push(undoEntry)

  const notifId = `undo-${issue.id}-${Date.now()}`
  Notification.success({
    id: notifId,
    title: '状态变更成功',
    content: `${issue.issueKey} 已移至「${localizeStatusName(targetStatus?.name)}」`,
    duration: UNDO_TIMEOUT,
    closable: true,
    footer: () => h('button', {
      class: 'undo-btn',
      onClick: () => {
        undoTransition(undoEntry)
        Notification.remove(notifId)
      }
    }, '↩ 撤销 (Ctrl+Z)')
  })
}

async function undoTransition(entry: UndoEntry) {
  const issue = issues.value.find(i => i.id === entry.issueId)
  if (!issue) {
    Message.warning('工单已不在当前视图中，无法撤销')
    return
  }

  const currentStatusId = issue.statusId
  issue.statusId = entry.oldStatusId
  transitioningIssueIds.value.add(issue.id)

  try {
    const res = await issueApi.undoTransitStatus(issue.id, entry.oldStatusId)
    // 同步更新版本号
    const newVersion = extractVersion(res.data)
    if (newVersion != null) {
      issue.version = newVersion
    } else {
      issue.version = (issue.version || 0) + 1
    }
    Message.success(`${entry.issueKey} 已撤销回「${localizeStatusName(entry.oldStatusName)}」`)
    undoStack.value = undoStack.value.filter(e => e !== entry)
  } catch (e: any) {
    issue.statusId = currentStatusId
    const errMsg = e.response?.data?.message || '撤销失败'
    Message.error(`撤销失败：${errMsg}`)
  } finally {
    transitioningIssueIds.value.delete(issue.id)
  }
}

function handleKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'z' && !e.shiftKey) {
    const now = Date.now()
    const validEntries = undoStack.value.filter(entry => now - entry.timestamp < UNDO_TIMEOUT)
    if (validEntries.length > 0) {
      e.preventDefault()
      const lastEntry = validEntries[validEntries.length - 1]
      undoTransition(lastEntry)
    }
  }
  // Escape: 优先关闭预览面板 → 然后清空选择
  if (e.key === 'Escape') {
    if (previewVisible.value) {
      closePreview()
    } else if (selectedCount.value > 0) {
      clearSelection()
    }
  }
}

// ===== 批量操作处理 =====

async function onBatchState(statusId: string) {
  const result = await batchTransitStatus(selectedIssues.value, statusId)
  if (result.succeeded > 0) {
    selectedIssues.value.forEach(issue => {
      if (!result.failures.find(f => f.issueId === issue.id)) {
        issue.statusId = statusId
        // 批量操作不返回单个版本号，本地递增
        issue.version = (issue.version || 0) + 1
      }
    })
  }
  clearSelection()
}

async function onBatchAssign(assigneeId: string | null) {
  await batchAssign(selectedIssues.value, assigneeId || '')
  // 需要刷新看板数据以获取更新后的 assigneeName
  await loadIssues()
  clearSelection()
}

async function onBatchSprint(sprintId: string | null) {
  const result = await batchUpdateSprint(selectedIssues.value, sprintId)
  if (result.succeeded > 0) {
    selectedIssues.value.forEach(issue => {
      if (!result.failures.find(f => f.issueId === issue.id)) {
        issue.sprintId = sprintId || undefined
      }
    })
  }
  clearSelection()
}

async function onBatchPriority(priority: string) {
  const result = await batchUpdatePriority(selectedIssues.value, priority)
  if (result.succeeded > 0) {
    selectedIssues.value.forEach(issue => {
      if (!result.failures.find(f => f.issueId === issue.id)) {
        issue.priority = priority
      }
    })
  }
  clearSelection()
}

async function onBatchTagAdd(tagId: string) {
  const result = await batchTagAdd(selectedIssues.value, tagId)
  if (result.succeeded > 0) {
    await loadIssues()
  }
  clearSelection()
}

async function onBatchTagRemove(tagId: string) {
  const result = await batchTagRemove(selectedIssues.value, tagId)
  if (result.succeeded > 0) {
    await loadIssues()
  }
  clearSelection()
}

async function onBatchLink(linkType: string, targetIssueId: string) {
  const result = await batchAddLink(selectedIssues.value, linkType, targetIssueId)
  if (result.succeeded > 0) {
    await loadIssues()
  }
  clearSelection()
}

async function onBatchDelete() {
  const result = await batchDelete(selectedIssues.value)
  if (result.succeeded > 0) {
    // 从视图中移除已删除的工单
    const deletedIds = new Set(
      selectedIssues.value
        .map(i => i.id)
        .filter(id => !result.failures.some(f => f.issueId === id))
    )
    issues.value = issues.value.filter(i => !deletedIds.has(i.id))
  }
  clearSelection()
}

// ===== 数据加载 =====

async function loadStatuses() {
  try {
    const res = await issueApi.listStatuses()
    statuses.value = res.data || []
  } catch {
    statuses.value = []
    Message.error('加载状态列表失败')
  }
}

async function loadBoardColumns() {
  if (!selectedProject.value) {
    allColumnConfigs.value = []
    return
  }
  try {
    const res = await boardApi.getColumns(selectedProject.value)
    allColumnConfigs.value = res.data || []
  } catch {
    allColumnConfigs.value = []
  }
}

async function loadCardConfig() {
  if (!selectedProject.value) {
    cardConfig.value = { visibleFields: ['assignee', 'priority', 'type'], colorScheme: 'none' }
    return
  }
  try {
    const res = await boardApi.getCardConfig(selectedProject.value)
    if (res.data) {
      cardConfig.value = res.data
    }
  } catch {
    cardConfig.value = { visibleFields: ['assignee', 'priority', 'type'], colorScheme: 'none' }
  }
}

async function loadSwimlaneConfig() {
  if (!selectedProject.value) {
    swimlaneGroupBy.value = 'none'
    swimlaneSelectedValues.value = null
    swimlaneShowUncategorized.value = true
    swimlaneUncategorizedPosition.value = 'bottom'
    swimlaneIssueType.value = null
    return
  }
  try {
    const res = await boardApi.getSwimlaneConfig(selectedProject.value)
    if (res.data && res.data.groupByField) {
      swimlaneGroupBy.value = res.data.groupByField as SwimlaneGroupBy
      // 同步到 localStorage（兼容本地快速切换）
      localStorage.setItem(SWIMLANE_STORAGE_KEY, res.data.groupByField)
      // 加载值选择配置
      swimlaneSelectedValues.value = res.data.selectedValues || null
      swimlaneShowUncategorized.value = res.data.showUncategorized !== false
      swimlaneUncategorizedPosition.value = res.data.uncategorizedPosition || 'bottom'
      swimlaneIssueType.value = res.data.swimlaneIssueType ?? null
    }
  } catch {
    // 保持当前 localStorage 中的值
  }
}

async function loadColumnMerges() {
  if (!selectedProject.value) {
    columnMerges.value = []
    return
  }
  try {
    const res = await boardApi.getColumnMerges(selectedProject.value)
    columnMerges.value = res.data || []
  } catch {
    columnMerges.value = []
  }
}

async function loadTransitionableStatuses() {
  if (!selectedProject.value || !canChangeStatus.value) {
    transitionableSourceStatuses.value = new Set()
    return
  }
  try {
    const res = await workflowApi.getTransitionableStatuses(selectedProject.value)
    transitionableSourceStatuses.value = new Set(res.data || [])
  } catch {
    transitionableSourceStatuses.value = new Set()
  }
}

function onSettingsSaved() {
  loadBoardColumns()
  loadCardConfig()
  loadSwimlaneConfig()
  loadColumnMerges()
  // Reload behavior config and re-filter issues
  loadBoardBehavior().then(() => loadIssues())
}

async function loadSprints() {
  if (!selectedProject.value) { sprints.value = []; return }
  try {
    const res = await sprintApi.listByProject(selectedProject.value)
    const rawSprints = res.data?.list || []

    // Sort sprints: active first, then planned, then completed; within group by startDate desc
    const statusOrder: Record<string, number> = { active: 0, planned: 1, completed: 2 }
    rawSprints.sort((a, b) => {
      const orderA = statusOrder[a.status] ?? 9
      const orderB = statusOrder[b.status] ?? 9
      if (orderA !== orderB) return orderA - orderB
      // Within same status group: by startDate descending (most recent first)
      const dateA = a.startDate || ''
      const dateB = b.startDate || ''
      return dateB.localeCompare(dateA)
    })
    sprints.value = rawSprints

    // Auto-select active sprint if no explicit user/URL selection
    // Logic (following YouTrack): active > (planned with earliest start date that hasn't ended)
    if (!selectedSprint.value && !userExplicitlySelectedAll) {
      const activeSprintItem = rawSprints.find(s => s.status === 'active')
      if (activeSprintItem) {
        selectedSprint.value = activeSprintItem.id
        syncUrlState()
      } else {
        // Fallback: find a planned sprint whose date range contains today
        const today = new Date().toISOString().split('T')[0]
        const currentDateSprint = rawSprints.find(s =>
          s.status === 'planned' && s.startDate && s.endDate &&
          s.startDate <= today && s.endDate >= today
        )
        if (currentDateSprint) {
          selectedSprint.value = currentDateSprint.id
          syncUrlState()
        }
        // If no current sprint found, leave as "所有迭代" (sprint-no-active-hint will show)
      }
    }
  } catch {
    sprints.value = []
    Message.error('加载迭代列表失败')
  }
}

/** 加载 Board Behavior 配置（过滤模式 + 完成工单保留天数 + 看板名称） */
async function loadBoardBehavior() {
  if (!selectedProject.value) return
  try {
    const res = await boardApi.getGeneralConfig(selectedProject.value)
    if (res.data) {
      boardFilterMode.value = (res.data.filterMode as 'all' | 'active_sprint' | 'query') || 'all'
      boardFilterQuery.value = res.data.filterQuery ?? null
      boardDoneRetentionDays.value = res.data.doneRetentionDays ?? null
      canEditBoard.value = res.data.currentUserCanEdit ?? false
      boardName.value = res.data.name || ''
      boardColumnField.value = (res.data.columnField as 'status' | 'priority') || 'status'
      // 多 Sprint 配置
      allowMultipleSprints.value = res.data.allowMultipleSprints ?? false
      // Backlog 配置
      backlogViewMode.value = (res.data.backlogViewMode as 'list' | 'tree') || 'list'
      backlogSavedQueryId.value = res.data.backlogSavedQueryId ?? null
      // 关联项目
      boardLinkedProjectIds.value = res.data.linkedProjectIds || []
    }
  } catch {
    boardFilterMode.value = 'all'
    boardFilterQuery.value = null
    boardDoneRetentionDays.value = null
    canEditBoard.value = false
    boardName.value = ''
    allowMultipleSprints.value = false
    backlogViewMode.value = 'list'
    backlogSavedQueryId.value = null
    boardLinkedProjectIds.value = []
  }
}

/** 加载看板图表配置（类型 + 计算方式） */
async function loadChartConfig() {
  if (!selectedProject.value) return
  try {
    const res = await boardApi.getChartConfig(selectedProject.value)
    if (res.data) {
      boardChartType.value = res.data.chartType || 'burndown'
      boardBurndownCalculation.value = res.data.burndownCalculation || 'issue_count'
    }
  } catch {
    // 加载失败使用默认值
    boardChartType.value = 'burndown'
    boardBurndownCalculation.value = 'issue_count'
  }
}

async function loadBoard() {
  if (!selectedProject.value) { issues.value = []; return }
  expandedEmptyColumns.value.clear()
  loadCollapsedColumnsState()
  // Restore guidance dismissed state from sessionStorage
  guidanceDismissed.value = sessionStorage.getItem(`${GUIDANCE_DISMISSED_KEY}_${selectedProject.value}`) === 'true'
  loading.value = true
  try {
    await Promise.all([loadSprints(), loadBoardColumns(), loadCardConfig(), loadSwimlaneConfig(), loadColumnMerges(), loadTransitionableStatuses(), loadBoardBehavior(), loadProjectMembers(), loadChartConfig()])
    // 加载泳道自定义排序（需在 loadSwimlaneConfig 之后，确保 swimlaneGroupBy 已恢复）
    loadSwimlaneOrder()
    await loadIssues()
    // Load manual order for board card sorting
    if (selectedProject.value) {
      await loadBoardManualOrder({ type: 'project', id: selectedProject.value })
    }
  } catch (e: any) {
    // 会话过期时不显示"加载失败"——已有过期提示和跳转
    const isSessionExpired = e?.message === '会话已过期' || e?.code === 'ERR_CANCELED'
    if (isSessionExpired) return
    issues.value = []
    Message.error('加载看板数据失败')
  } finally {
    loading.value = false
  }
}

/** 看板安全上限：超过此数量的工单将截断并提示用户 */
const BOARD_MAX_ISSUES = 2000
const boardTruncated = ref(false)
const boardTotalCount = ref(0)

/**
 * 加载看板工单数据（使用看板专用聚合 API，单次请求）。
 * 服务端按列分组返回数据，前端无需客户端分组计算。
 */
async function loadIssues() {
  if (!selectedProject.value) { issues.value = []; boardTruncated.value = false; return }

  // Board Behavior 过滤逻辑现在由服务端统一执行（filterMode / filterQuery / doneRetentionDays）。
  // 前端仅传递用户交互产生的显式筛选参数。

  // 用户显式选择的 Sprint（通过 Sprint 下拉选择器）
  const effectiveSprintId = selectedSprint.value || undefined

  // 前端仍可显式传 excludeDoneBefore 覆盖服务端配置（当用户无活跃 Sprint 且无显式选择时保留前端 14 天默认值）
  const DEFAULT_DONE_RETENTION_DAYS = 14
  let excludeDoneBefore: string | undefined
  // 仅当 filterMode 不是由服务端控制 doneRetentionDays（即 boardDoneRetentionDays 为 null）且无 Sprint 选择时，
  // 前端使用默认 14 天保留（向后兼容）
  if (boardDoneRetentionDays.value === null && !activeSprint.value && !selectedSprint.value) {
    const cutoffDate = new Date(Date.now() - DEFAULT_DONE_RETENTION_DAYS * 24 * 60 * 60 * 1000)
    excludeDoneBefore = cutoffDate.toISOString().split('T')[0]
  }
  // 如果 boardDoneRetentionDays 已配置（非 null），服务端会自动应用，前端不传

  // 收集已折叠列的状态 ID（折叠列不需要返回具体工单）
  // REQ-388 修复：合并列（mergeGroupId）需展开为其包含的所有 statusIds，
  // 否则服务端 parseCollapsedStatusIds() 对非数字字符串执行 Long.parseLong() 时
  // 会抛出 NumberFormatException 并静默忽略，导致合并列折叠优化完全失效。
  const collapsedStatusIdSet = new Set<string>()
  for (const colId of collapsedColumns.value) {
    // 检查是否是合并列 ID（非纯数字 = mergeGroupId）
    const mergedCol = effectiveColumns.value.find(c => c.isMerged && c.id === colId)
    if (mergedCol) {
      // 展开为合并组包含的所有 statusId
      for (const sid of mergedCol.statusIds) {
        collapsedStatusIdSet.add(sid)
      }
    } else {
      collapsedStatusIdSet.add(colId)
    }
  }
  const collapsedIds = [...collapsedStatusIdSet].join(',')

  // REQ-386: 泳道服务端过滤
  // 仅当：(1) 泳道分组已配置（非 none）(2) 有选中值 (3) showUncategorized=false 时才做服务端过滤
  // showUncategorized=true 时不传（服务端需返回全量数据，前端要构建"未分类"泳道）
  let swimlaneFieldParam: string | undefined
  let swimlaneValuesParam: string | undefined
  if (
    swimlaneGroupBy.value !== 'none' &&
    swimlaneSelectedValues.value && swimlaneSelectedValues.value.length > 0 &&
    swimlaneShowUncategorized.value === false
  ) {
    swimlaneFieldParam = swimlaneGroupBy.value
    swimlaneValuesParam = swimlaneSelectedValues.value.join(',')
  }

  try {
    const res = await boardApi.getBoardData({
      projectId: selectedProject.value,
      sprintId: effectiveSprintId,
      assigneeId: effectiveAssigneeId.value || undefined,
      keyword: keyword.value || undefined,
      excludeDoneBefore,
      collapsedStatusIds: collapsedIds || undefined,
      swimlaneField: swimlaneFieldParam,
      swimlaneValues: swimlaneValuesParam
    })

    const boardData = res.data
    if (!boardData) {
      issues.value = []
      boardTotalCount.value = 0
      boardTruncated.value = false
      return
    }

    // 从聚合数据中同步列配置（消除 getColumns 竞态：列定义与工单数据来自同一响应）
    if (boardData.columnConfigs && boardData.columnConfigs.length > 0) {
      allColumnConfigs.value = boardData.columnConfigs
    }

    // 从聚合数据中提取所有工单（平铺，供 getColumnIssues/swimlanes 使用）
    const allIssues: BoardCardVO[] = []
    for (const col of boardData.columns) {
      if (col.issues && col.issues.length > 0) {
        allIssues.push(...col.issues)
      }
    }

    boardTotalCount.value = boardData.totalIssueCount
    boardTruncated.value = boardData.truncated
    issues.value = allIssues
  } catch (e: any) {
    // 如果聚合 API 失败（如后端未部署），fallback 到旧方式
    const status = e.response?.status
    if (status === 404 || status === 405) {
      await loadIssuesLegacy(effectiveSprintId, excludeDoneBefore)
    } else {
      throw e
    }
  }
}

/**
 * Legacy 加载方式（循环分页调用通用 Issue 列表 API）。
 * 仅作为聚合 API 不可用时的 fallback。
 */
async function loadIssuesLegacy(effectiveSprintId: string | undefined, excludeDoneBefore: string | undefined) {
  const PAGE_SIZE = 100
  let page = 1
  let allIssues: BoardIssue[] = []
  let total = 0

  // 优化：传入可见列的 statusId 过滤，减少不必要的数据传输
  const visibleStatusIds = visibleStatuses.value.map(s => s.id).join(',')

  while (true) {
    const res = await issueApi.list({
      projectId: selectedProject.value!,
      statusId: visibleStatusIds || undefined,
      sprintId: effectiveSprintId,
      assigneeId: effectiveAssigneeId.value || undefined,
      keyword: keyword.value || undefined,
      excludeDoneBefore,
      page,
      pageSize: PAGE_SIZE
    })
    const list = res.data?.list || []
    total = res.data?.pagination?.total || 0
    allIssues = allIssues.concat(list)

    if (allIssues.length >= total || allIssues.length >= BOARD_MAX_ISSUES || list.length < PAGE_SIZE) {
      break
    }
    page++
  }

  boardTotalCount.value = total
  boardTruncated.value = allIssues.length < total
  issues.value = allIssues
}

// ===== 内联快速创建卡片 =====
const addingCardColumnId = ref<string | null>(null)
const addingCardSwimlaneKey = ref<string | null>(null)
const addCardTitle = ref('')
const addCardType = ref<string>('Task')
const addCardSubmitting = ref(false)

/** 存储 input refs，用于自动聚焦 */
function setAddCardInputRef(el: any, _statusId: string, _laneKey: string) {
  if (el) {
    nextTick(() => el.focus())
  }
}

/** 判断某状态是否为终态（已完成/已取消），终态列不允许直接创建卡片 */
function isClosedStatus(status: { category?: string; isClosed?: boolean }): boolean {
  if (status.isClosed) return true
  const cat = status.category?.toLowerCase()
  return cat === 'done' || cat === 'cancelled'
}

/** 开始添加卡片：展开内联表单 */
function startAddCard(statusId: string, swimlaneKey?: string) {
  addingCardColumnId.value = statusId
  addingCardSwimlaneKey.value = swimlaneKey || null
  addCardTitle.value = ''
  addCardType.value = 'Task'
}

/** 取消添加卡片 */
function cancelAddCard() {
  addingCardColumnId.value = null
  addingCardSwimlaneKey.value = null
  addCardTitle.value = ''
  keepFormOpen = false
}

/** blur 时如果标题为空则取消——但提交后保持打开 */
let keepFormOpen = false

function onAddCardBlur() {
  if (keepFormOpen) return
  // 使用 setTimeout 避免点击"创建"按钮时提前关闭
  setTimeout(() => {
    if (!addCardTitle.value.trim() && !addCardSubmitting.value && !keepFormOpen) {
      cancelAddCard()
    }
  }, 200)
}

/** 提交创建卡片 */
async function submitAddCard(statusId: string, swimlaneKey?: string) {
  const title = addCardTitle.value.trim()
  if (!title || !selectedProject.value) return

  addCardSubmitting.value = true
  keepFormOpen = true
  try {
    // 确定 Sprint
    const sprintId = selectedSprint.value || getActiveSprintId()

    // 确定负责人（按负责人分组时预填）
    let assigneeId: string | undefined
    if (swimlaneGroupBy.value === 'assignee' && swimlaneKey && swimlaneKey !== '__unassigned__') {
      assigneeId = swimlaneKey
    }

    const createData: Record<string, any> = {
      projectId: selectedProject.value,
      title,
      issueType: addCardType.value,
      sprintId: sprintId || undefined,
      assigneeId: assigneeId || undefined
    }

    const res = await issueApi.create(createData as any)
    const newIssue = res.data

    if (newIssue) {
      // 如果创建的 Issue 状态不是目标列的状态，需要做状态转换
      // （初始状态通常是 default/待处理，但用户在其他列创建需要转换）
      const defaultStatus = statuses.value.find(s => s.isDefault)
      if (defaultStatus && defaultStatus.id !== statusId) {
        try {
          const transitRes = await issueApi.transitStatus(newIssue.id, statusId, undefined, newIssue.version)
          // 更新本地状态和版本号
          newIssue.statusId = statusId
          if (transitRes.data != null) {
            newIssue.version = transitRes.data
          } else {
            newIssue.version = (newIssue.version || 0) + 1
          }
        } catch {
          // 转换失败不影响创建，卡片将出现在默认状态列
          Message.warning(`工单已创建，但无法自动转换到「${localizeStatusName(visibleStatuses.value.find(s => s.id === statusId)?.name)}」状态`)
        }
      }

      // 构建本地 BoardIssue 添加到看板
      const issueVO: BoardIssue = {
        id: newIssue.id,
        issueKey: newIssue.issueKey,
        title: newIssue.title,
        issueType: newIssue.issueType || addCardType.value,
        priority: newIssue.priority || 'Normal',
        statusId: newIssue.statusId || statusId,
        projectId: selectedProject.value!,
        sprintId: sprintId || undefined,
        assigneeId: newIssue.assigneeId || assigneeId,
        assigneeName: newIssue.assigneeName || '',
      }
      issues.value.push(issueVO)

      Message.success(`${newIssue.issueKey} 创建成功`)

      // 清空标题但保持表单打开，方便连续创建
      addCardTitle.value = ''
      // 保持 keepFormOpen 直到下一帧，防止 blur 关闭表单
      nextTick(() => {
        // 延迟重置 keepFormOpen，让 blur 有时间判断
        setTimeout(() => { keepFormOpen = false }, 250)
      })
    }
  } catch (e: any) {
    const errMsg = e.response?.data?.message || '创建失败'
    Message.error(`创建工单失败：${errMsg}`)
    keepFormOpen = false
  } finally {
    addCardSubmitting.value = false
  }
}

// ===== 头部"新建..."按钮相关 =====
const newCardModalVisible = ref(false)
const newCardSubmitting = ref(false)
const newCardForm = ref({
  title: '',
  issueType: 'Task',
  priority: 'Normal',
  assigneeId: undefined as string | undefined,
  sprintId: undefined as string | undefined
})

const newSprintModalVisible = ref(false)
const newSprintSubmitting = ref(false)
const newSprintForm = ref({
  name: '',
  goal: '',
  startDate: undefined as string | undefined,
  endDate: undefined as string | undefined
})

/** "新建..."按钮下拉菜单选择处理 */
function onNewMenuSelect(value: string | number | Record<string, any> | undefined) {
  if (value === 'card') {
    // 打开新建卡片对话框，预填当前 Sprint
    newCardForm.value = {
      title: '',
      issueType: 'Task',
      priority: 'Normal',
      assigneeId: undefined,
      sprintId: selectedSprint.value || getActiveSprintId() || undefined
    }
    newCardModalVisible.value = true
  } else if (value === 'sprint') {
    newSprintForm.value = { name: '', goal: '', startDate: undefined, endDate: undefined }
    newSprintModalVisible.value = true
  }
}

/** 提交新建卡片对话框 */
async function submitNewCardModal() {
  const title = newCardForm.value.title.trim()
  if (!title || !selectedProject.value) {
    Message.warning('请输入工单标题')
    return
  }

  newCardSubmitting.value = true
  try {
    const createData: Record<string, any> = {
      projectId: selectedProject.value,
      title,
      issueType: newCardForm.value.issueType
    }
    if (newCardForm.value.priority && newCardForm.value.priority !== 'Normal') {
      createData.priority = newCardForm.value.priority
    }
    if (newCardForm.value.assigneeId) {
      createData.assigneeId = newCardForm.value.assigneeId
    }
    if (newCardForm.value.sprintId) {
      createData.sprintId = newCardForm.value.sprintId
    }

    const res = await issueApi.create(createData as any)
    const newIssue = res.data
    if (newIssue) {
      // 构建本地 BoardIssue 添加到看板
      const issueVO: BoardIssue = {
        id: newIssue.id,
        issueKey: newIssue.issueKey,
        title: newIssue.title,
        issueType: newIssue.issueType || newCardForm.value.issueType,
        priority: newIssue.priority || newCardForm.value.priority,
        statusId: newIssue.statusId,
        projectId: selectedProject.value!,
        sprintId: newCardForm.value.sprintId,
        assigneeId: newIssue.assigneeId || newCardForm.value.assigneeId,
        assigneeName: newIssue.assigneeName || '',
      }
      issues.value.push(issueVO)
      Message.success(`${newIssue.issueKey} 创建成功`)
      newCardModalVisible.value = false
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '创建工单失败')
  } finally {
    newCardSubmitting.value = false
  }
}

/** 提交新建 Sprint 对话框 */
async function submitNewSprintModal() {
  const name = newSprintForm.value.name.trim()
  if (!name || !selectedProject.value) {
    Message.warning('请输入 Sprint 名称')
    return
  }

  newSprintSubmitting.value = true
  try {
    const res = await sprintApi.create(selectedProject.value, {
      name,
      goal: newSprintForm.value.goal || undefined,
      startDate: newSprintForm.value.startDate || undefined,
      endDate: newSprintForm.value.endDate || undefined
    })
    if (res.data) {
      sprints.value.push(res.data)
      Message.success(`Sprint「${res.data.name}」创建成功`)
      newSprintModalVisible.value = false
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '创建 Sprint 失败')
  } finally {
    newSprintSubmitting.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadProjects(), loadStatuses()])
  document.addEventListener('keydown', handleKeydown)

  // URL 状态恢复优先级：URL params > projectStore (localStorage) > 首个项目
  restoreFromUrl()

  if (selectedProject.value) {
    // 如果恢复了状态或已有选择，同步到 URL 并加载看板
    syncUrlState()
    loadBoard()
  } else {
    // 尝试自动选择：首个项目（收藏项目已排在前面）
    if (projects.value.length > 0) {
      selectedProject.value = projects.value[0].id
      syncUrlState()
      loadBoard()
    }
  }
})

// 监听路由 query 变化（浏览器前进/后退按钮）
watch(() => route.query, (newQuery, oldQuery) => {
  // 避免自身 replace 触发的变化导致循环
  if (JSON.stringify(newQuery) === JSON.stringify(oldQuery)) return

  // 仅当仍在看板路由时才响应 query 变化（导航离开时忽略，避免清空 store）
  if (route.name !== 'Boards') return

  const queryProject = newQuery.project as string | undefined
  const querySprint = newQuery.sprint as string | undefined
  const queryGroup = newQuery.group as string | undefined
  const queryAssignee = newQuery.assignee as string | undefined

  suppressUrlSync = true

  // Track whether any state actually changed (to avoid redundant loadBoard calls)
  let stateChanged = false

  // 恢复项目
  if (queryProject) {
    const project = projects.value.find(p => p.key === queryProject)
    if (project && project.id !== selectedProject.value) {
      selectedProject.value = project.id
      selectedSprint.value = querySprint || undefined
      swimlaneGroupBy.value = (queryGroup as SwimlaneGroupBy) || 'none'
      localStorage.setItem(SWIMLANE_STORAGE_KEY, swimlaneGroupBy.value)
      suppressUrlSync = false
      loadBoard()
      return
    }
  } else if (selectedProject.value) {
    // URL 无项目参数但仍有选中项目（浏览器后退到无参数的 /boards）
    // 不清除 store 中的选中状态——重新同步 URL 并保持看板显示
    suppressUrlSync = false
    syncUrlState()
    return
  }

  // 恢复 Sprint
  if (querySprint !== selectedSprint.value) {
    selectedSprint.value = querySprint || undefined
    stateChanged = true
  }

  // 恢复分组
  if (queryGroup && queryGroup !== swimlaneGroupBy.value) {
    swimlaneGroupBy.value = queryGroup as SwimlaneGroupBy
    localStorage.setItem(SWIMLANE_STORAGE_KEY, swimlaneGroupBy.value)
    stateChanged = true
  } else if (!queryGroup && swimlaneGroupBy.value !== 'none') {
    swimlaneGroupBy.value = 'none'
    localStorage.setItem(SWIMLANE_STORAGE_KEY, 'none')
    stateChanged = true
  }  // 恢复负责人筛选
  if (queryAssignee !== assigneeFilter.value) {
    assigneeFilter.value = queryAssignee || undefined
    if (queryAssignee) {
      localStorage.setItem(ASSIGNEE_FILTER_KEY, queryAssignee)
    } else {
      localStorage.removeItem(ASSIGNEE_FILTER_KEY)
    }
    stateChanged = true
  }

  suppressUrlSync = false

  // 仅在实际状态发生变化时重新加载（避免与 onSprintChange/onProjectChange 的 loadBoard 重复调用）
  if (stateChanged && selectedProject.value) {
    loadBoard()
  }
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
})
</script>

<style scoped>
.kanban-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  position: relative;
}

.board-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ===== 负责人筛选 ===== */
.assignee-filter-group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.my-issues-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-2);
  font-size: 12px;
  font-weight: 500;
  border-radius: 4px;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
  line-height: 1.4;
}

.my-issues-btn:hover {
  background: var(--color-fill-2);
  border-color: rgb(var(--primary-6));
  color: rgb(var(--primary-6));
}

.my-issues-btn--active {
  background: rgba(var(--primary-6), 0.1);
  border-color: rgb(var(--primary-6));
  color: rgb(var(--primary-6));
  font-weight: 600;
}

.my-issues-btn--active:hover {
  background: rgba(var(--primary-6), 0.15);
}

.header-filter-wrapper {
  display: flex;
  align-items: center;
  gap: 6px;
}

.filter-active-badge {
  font-size: 11px;
  color: rgb(var(--primary-6));
  background: rgba(var(--primary-6), 0.1);
  padding: 2px 8px;
  border-radius: 3px;
  white-space: nowrap;
  font-weight: 500;
}

/* ===== Board Behavior Filter Chips ===== */
.behavior-filter-chips {
  display: flex;
  align-items: center;
  gap: 4px;
}

.behavior-chip {
  font-size: 11px;
  color: rgb(var(--warning-6));
  background: rgba(var(--warning-6), 0.1);
  padding: 2px 8px;
  border-radius: 3px;
  white-space: nowrap;
  font-weight: 500;
}

.behavior-chip--auto {
  color: var(--color-text-3);
  background: var(--color-fill-2);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-1);
  margin: 0;
}

/* ===== Sprint Selector Styles ===== */
.sprint-option-content {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.sprint-option-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sprint-option-name--active {
  font-weight: 600;
  color: var(--color-text-1);
}

.sprint-option-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  white-space: nowrap;
  flex-shrink: 0;
}

.sprint-option-badge--active {
  color: rgb(var(--success-6));
  background: rgba(var(--success-6), 0.1);
  font-weight: 500;
}

.sprint-option-badge--planned {
  color: rgb(var(--primary-6));
  background: rgba(var(--primary-6), 0.08);
}

.sprint-option-badge--completed {
  color: var(--color-text-4);
  background: var(--color-fill-2);
}

.sprint-option-badge--archived {
  color: var(--color-text-4);
  background: var(--color-fill-3);
  font-style: italic;
}

.sprint-countdown {
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  padding: 2px 8px;
  border-radius: 3px;
  white-space: nowrap;
}

.sprint-countdown--urgent {
  color: rgb(var(--warning-6));
  background: rgba(var(--warning-6), 0.1);
}

.sprint-countdown--overdue {
  color: rgb(var(--danger-6));
  background: rgba(var(--danger-6), 0.1);
}

.sprint-date-info {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.sprint-date-range {
  font-size: 11px;
  color: var(--color-text-3);
}

.sprint-detail-link {
  font-size: 12px;
  cursor: pointer;
  opacity: 0.6;
  transition: opacity 150ms;
  text-decoration: none;
  margin-left: 2px;
}

.sprint-detail-link:hover {
  opacity: 1;
}

.sprint-no-active-hint {
  font-size: 11px;
  color: var(--color-text-4);
  white-space: nowrap;
  font-style: italic;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.sprint-next-hint {
  color: rgb(var(--primary-6));
  font-style: normal;
  font-weight: 500;
}

/* ===== Progress Indicator (mini bar chart) ===== */
.progress-indicator {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 28px;
  padding: 2px 4px;
  border-radius: 4px;
  background: var(--color-fill-1);
}

.progress-bar {
  width: 12px;
  min-height: 2px;
  border-radius: 2px;
  cursor: pointer;
  transition: opacity 0.15s, transform 0.15s;
  opacity: 0.8;
}

.progress-bar:hover {
  opacity: 1;
  transform: scaleY(1.15);
}

.progress-bar--collapsed {
  opacity: 0.35;
}

.progress-bar--closed {
  background: var(--color-fill-4) !important;
  opacity: 0.45;
  border-left: 1px solid var(--color-border);
  margin-left: 2px;
  cursor: default;
  background-image: repeating-linear-gradient(
    45deg,
    transparent,
    transparent 2px,
    rgba(255, 255, 255, 0.08) 2px,
    rgba(255, 255, 255, 0.08) 4px
  ) !important;
}

.board-spin {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.board-main-area {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* ===== 平面看板（无分组） ===== */
.board-container {
  flex: 1;
  display: flex;
  gap: 8px;
  padding: 16px;
  overflow-x: auto;
  overflow-y: hidden;
}

/* ===== 正常列 ===== */
.board-column {
  min-width: 220px;
  max-width: 320px;
  flex: 1 1 220px;
  display: flex;
  flex-direction: column;
  background: var(--color-fill-1);
  border-radius: 8px;
  overflow: hidden;
  transition: box-shadow 0.15s, border-color 0.15s;
  border: 2px solid transparent;
}

.board-column--expanded-empty {
  min-width: 200px;
  max-width: 220px;
  flex: 0 0 200px;
  opacity: 0.7;
}

.board-column--drop-target {
  border-color: rgb(var(--primary-6));
  box-shadow: 0 0 0 2px rgba(var(--primary-6), 0.15);
  background: var(--color-fill-2);
}

.board-column--drop-forbidden {
  border-color: rgb(var(--danger-6));
  opacity: 0.6;
}

.column-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border-top: 3px solid var(--color-border);
  flex-shrink: 0;
}

.column-header--clickable {
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}
.column-header--clickable:hover {
  background: var(--color-fill-2);
}
.column-header--clickable:focus-visible {
  outline: 2px solid rgb(var(--primary-6));
  outline-offset: -2px;
}

.column-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-1);
  text-transform: uppercase;
  letter-spacing: 0.3px;
  flex: 1;
}

.column-count {
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-fill-3);
  padding: 2px 6px;
  border-radius: 3px;
}

.column-estimation {
  font-size: 11px;
  color: var(--tf-text-tertiary, var(--color-text-3));
  padding: 2px 4px;
  border-radius: 3px;
  white-space: nowrap;
}

.estimation-indicator {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  color: var(--tf-text-secondary, var(--color-text-2));
  cursor: default;
  white-space: nowrap;
}

.estimation-icon {
  font-size: 12px;
}

.estimation-value {
  font-weight: 500;
}

.column-collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: none;
  background: var(--color-fill-3);
  color: var(--color-text-3);
  border-radius: 3px;
  cursor: pointer;
  font-size: 10px;
  line-height: 1;
  transition: background 0.15s, color 0.15s;
}
.column-collapse-btn:hover {
  background: var(--color-fill-4);
  color: var(--color-text-1);
}

.column-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 60px;
}

/* ===== 折叠的空列 ===== */
.board-column-collapsed {
  flex: 0 0 36px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 12px 4px;
  background: var(--color-fill-1);
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
  overflow: hidden;
  border: 2px solid transparent;
}
.board-column-collapsed:hover {
  background: var(--color-fill-2);
}
.board-column-collapsed:focus-visible {
  outline: 2px solid rgb(var(--primary-6));
  outline-offset: -2px;
}

.board-column-collapsed--drop-target {
  border-color: rgb(var(--primary-6));
  background: var(--color-fill-2);
}
.board-column-collapsed--drop-forbidden {
  border-color: rgb(var(--danger-6));
  opacity: 0.5;
}

.collapsed-indicator {
  width: 20px;
  height: 3px;
  border-radius: 2px;
  flex-shrink: 0;
}

.collapsed-name {
  writing-mode: vertical-rl;
  text-orientation: mixed;
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-3);
  letter-spacing: 0.3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-height: 120px;
}

.collapsed-count {
  font-size: 10px;
  color: var(--color-text-4);
  background: var(--color-fill-3);
  padding: 1px 4px;
  border-radius: 3px;
}

/* ===== 卡片 ===== */
.kanban-card {
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 10px 12px;
  cursor: grab;
  transition: border-color 0.15s, box-shadow 0.15s, opacity 0.15s, transform 0.15s;
  user-select: none;
}

/* Card Size Variants */
.kanban-card--S {
  padding: 6px 10px;
}
.kanban-card--S .card-header {
  margin-bottom: 4px;
}
.kanban-card--S .card-footer {
  margin-top: 6px;
}

.kanban-card--M {
  padding: 10px 12px;
}

.kanban-card--L {
  padding: 12px 14px;
}
.kanban-card--L .card-header {
  margin-bottom: 8px;
}
.kanban-card--L .card-footer {
  margin-top: 10px;
}

/* Card title line-clamp by size */
.card-title--S {
  -webkit-line-clamp: 1;
}
.card-title--M {
  -webkit-line-clamp: 2;
}
.card-title--L {
  -webkit-line-clamp: 3;
}

.kanban-card:hover {
  border-color: rgb(var(--primary-6));
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.kanban-card:active {
  cursor: grabbing;
}
.kanban-card:focus-visible {
  outline: 2px solid rgb(var(--primary-6));
  outline-offset: 1px;
}

.kanban-card--no-drag {
  cursor: pointer;
}
.kanban-card--no-drag:active {
  cursor: pointer;
}

.kanban-card--dragging {
  opacity: 0.4;
  transform: scale(0.97);
  border-color: rgb(var(--primary-6));
}

.kanban-card--transitioning {
  opacity: 0.6;
  pointer-events: none;
  position: relative;
}
.kanban-card--transitioning::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--color-fill-2);
  border-radius: 6px;
  animation: pulse 1s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 0.6; }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.card-key {
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-3);
}

/* 多项目看板 — 项目来源标记 */
.card-project-tag {
  font-size: 10px;
  font-weight: 600;
  color: rgb(var(--primary-6));
  background: rgba(var(--primary-6), 0.12);
  padding: 1px 4px;
  border-radius: 3px;
  margin-right: 4px;
  flex-shrink: 0;
}

.card-priority {
  font-size: 10px;
}

.card-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.card-type {
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  padding: 2px 6px;
  border-radius: 3px;
}

.card-assignee-avatar {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-initials {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 600;
  color: var(--color-white);
  background: rgb(var(--primary-6));
  border-radius: 50%;
}

/* Full name 模式：显示负责人姓名文字 */
.assignee-full-name {
  font-size: 11px;
  color: var(--color-text-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 70px;
}

/* ===== Custom Fields on card ===== */
.card-custom-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 6px;
}

.card-cf-tag {
  font-size: 10px;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  padding: 1px 6px;
  border-radius: 3px;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== Card Tags ===== */
.card-tag {
  font-size: 10px;
  color: var(--color-text-2);
  background: var(--color-fill-3);
  padding: 1px 6px;
  border-radius: 3px;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

/* ===== Card Size Toggle Group ===== */
.card-size-group {
  display: flex;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  overflow: hidden;
}

.card-size-btn {
  border: none;
  background: transparent;
  color: var(--color-text-3);
  font-size: 11px;
  font-weight: 600;
  padding: 4px 10px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  line-height: 1;
}
.card-size-btn:hover {
  background: var(--color-fill-2);
  color: var(--color-text-1);
}
.card-size-btn + .card-size-btn {
  border-left: 1px solid var(--color-border);
}
.card-size-btn--active {
  background: rgb(var(--primary-6));
  color: #fff;
}
.card-size-btn--active:hover {
  background: rgb(var(--primary-6));
  color: #fff;
}

/* ===== 展开空列的空状态 ===== */
.column-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px 8px;
  text-align: center;
  border-radius: 6px;
  transition: background 0.15s;
}

.column-empty-state--drop-hint {
  background: rgba(var(--primary-6), 0.06);
  border: 1px dashed rgb(var(--primary-6));
}

.column-empty-icon {
  font-size: 24px;
  margin-bottom: 8px;
}

.column-empty-text {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-3);
  margin-bottom: 4px;
}

.column-empty-hint {
  font-size: 11px;
  color: var(--color-text-4);
}

/* ===== 内联快速创建卡片 ===== */
.add-card-area {
  margin-top: 4px;
  padding: 2px 0;
}

.add-card-area--swimlane {
  margin-top: 2px;
}

.add-card-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 6px 10px;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  color: var(--color-text-3);
  font-size: 12px;
  transition: background 0.15s, color 0.15s;
}
.add-card-btn:hover {
  background: var(--color-fill-2);
  color: var(--color-text-1);
}
.add-card-btn:focus-visible {
  outline: 2px solid rgb(var(--primary-6));
  outline-offset: -1px;
}

.add-card-btn--compact {
  width: auto;
  padding: 4px 8px;
  justify-content: center;
}
.add-card-btn--compact .add-card-text {
  display: none;
}

.add-card-icon {
  font-size: 14px;
  font-weight: 500;
  line-height: 1;
}

.add-card-text {
  font-size: 12px;
}

.add-card-form {
  background: var(--color-bg-2);
  border: 1px solid rgb(var(--primary-6));
  border-radius: 6px;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.add-card-input {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: var(--color-fill-1);
  color: var(--color-text-1);
  font-size: 13px;
  line-height: 1.4;
  outline: none;
  transition: border-color 0.15s;
}
.add-card-input:focus {
  border-color: rgb(var(--primary-6));
}
.add-card-input::placeholder {
  color: var(--color-text-4);
}
.add-card-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.add-card-form-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* ===== 页面空状态 ===== */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
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
}

.empty-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}

.empty-state--sprint .empty-desc {
  max-width: 420px;
  line-height: 1.6;
}

/* ===== Swimlane 分组视图 ===== */
.swimlane-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: auto;
  padding: 0;
}

.swimlane-header {
  display: flex;
  position: sticky;
  top: 0;
  z-index: 5;
  background: var(--color-bg-1);
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.swimlane-label-cell {
  width: 200px;
  min-width: 200px;
  flex-shrink: 0;
}

.swimlane-columns-header {
  display: flex;
  flex: 1;
  gap: 2px;
  padding: 8px 16px 8px 0;
}

.swimlane-col-header {
  flex: 1;
  min-width: 160px;
  padding: 8px 12px;
  border-top: 3px solid var(--color-border);
  background: var(--color-fill-1);
  border-radius: 6px 6px 0 0;
  text-align: center;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}
.swimlane-col-header:hover {
  background: var(--color-fill-2);
}
.swimlane-col-header:focus-visible {
  outline: 2px solid rgb(var(--primary-6));
  outline-offset: -2px;
}
.swimlane-col-header--collapsed {
  flex: 0 0 40px;
  min-width: 40px;
  padding: 8px 4px;
  opacity: 0.6;
}
.swimlane-col-header--collapsed .column-title {
  writing-mode: vertical-rl;
  text-orientation: mixed;
  font-size: 11px;
  max-height: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.swimlane-body {
  flex: 1;
}

.swimlane-row {
  border-bottom: 1px solid var(--color-border);
}

.swimlane-row--collapsed .swimlane-row-header {
  border-bottom: none;
}

.swimlane-row-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: var(--color-fill-1);
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
  position: sticky;
  left: 0;
}
.swimlane-row-header:hover {
  background: var(--color-fill-2);
}
.swimlane-row-header:focus-visible {
  outline: 2px solid rgb(var(--primary-6));
  outline-offset: -2px;
}

.swimlane-toggle-icon {
  font-size: 10px;
  color: var(--color-text-3);
  width: 14px;
  text-align: center;
  transition: transform 0.15s;
}

.swimlane-row-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
}

.swimlane-row-count {
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-fill-3);
  padding: 2px 8px;
  border-radius: 3px;
}

.swimlane-row-body {
  display: flex;
}

.swimlane-columns {
  display: flex;
  flex: 1;
  gap: 2px;
  padding: 8px 16px 12px 0;
}

.swimlane-cell {
  flex: 1;
  min-width: 160px;
  min-height: 60px;
  padding: 6px;
  background: var(--color-fill-1);
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: background 0.15s, border-color 0.15s;
  border: 2px solid transparent;
}

.swimlane-cell--collapsed {
  flex: 0 0 40px;
  min-width: 40px;
  min-height: 40px;
  padding: 4px;
}

.swimlane-cell--drop-target {
  border-color: rgb(var(--primary-6));
  background: var(--color-fill-2);
}

.swimlane-cell--drop-forbidden {
  border-color: rgb(var(--danger-6));
  opacity: 0.6;
}

.swimlane-cell-empty-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  font-size: 16px;
  opacity: 0.5;
}

/* ===== 列头「在工单列表中打开」按钮 ===== */
.column-header {
  position: relative;
}

.column-open-in-list {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 4px;
  color: var(--color-text-3);
  text-decoration: none;
  flex-shrink: 0;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.15s, visibility 0.15s, background 0.15s, color 0.15s;
  margin-left: auto;
}

.column-open-in-list:hover {
  color: rgb(var(--primary-6));
  background: var(--color-fill-3);
}

/* 列头 hover 或聚焦时显示按钮 */
.column-header:hover .column-open-in-list,
.swimlane-col-header:hover .column-open-in-list,
.column-header:focus-within .column-open-in-list,
.swimlane-col-header:focus-within .column-open-in-list {
  opacity: 1;
  visibility: visible;
}

/* Swimlane 列头中的按钮需特殊调整（列头是 flex 居中对齐） */
.swimlane-col-header .column-open-in-list {
  margin-left: 4px;
}


:global(.undo-btn) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  margin-top: 8px;
  border: 1px solid rgb(var(--primary-6));
  background: transparent;
  color: rgb(var(--primary-6));
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
:global(.undo-btn:hover) {
  background: rgb(var(--primary-6));
  color: #fff;
}

/* ===== WIP 限制警告样式 ===== */
.column-count--over {
  color: rgb(var(--danger-6));
  background: rgba(var(--danger-6), 0.1);
  font-weight: 600;
}

.column-count--under {
  color: rgb(var(--warning-6));
  background: rgba(var(--warning-6), 0.1);
  font-weight: 600;
}

.wip-warning {
  font-size: 12px;
  line-height: 1;
  flex-shrink: 0;
}

.wip-warning.wip-over {
  color: rgb(var(--danger-6));
}

.wip-warning.wip-under {
  color: rgb(var(--warning-6));
}

/* ===== 选中状态 ===== */
.kanban-card--selected {
  border-color: rgb(var(--primary-6));
  background: rgba(var(--primary-6), 0.06);
  box-shadow: 0 0 0 1px rgb(var(--primary-6));
}
.kanban-card--selected:hover {
  border-color: rgb(var(--primary-6));
  box-shadow: 0 0 0 1px rgb(var(--primary-6)), 0 2px 8px rgba(0, 0, 0, 0.06);
}

/* ===== 底部批量操作栏 ===== */
.batch-toolbar-wrapper {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 20;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.1);
}

/* ===== 底部默认 Footer ===== */
.board-footer-wrapper {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 15;
}

.board-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 16px;
  background: var(--color-bg-2);
  border-top: 1px solid var(--color-border);
  min-height: 36px;
  font-size: 12px;
  color: var(--color-text-3);
}

.board-footer-left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
  min-width: 0;
}

.board-footer-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.footer-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.footer-label {
  font-size: 13px;
  flex-shrink: 0;
}

.footer-value {
  color: var(--color-text-2);
  font-size: 12px;
}

.footer-owner .footer-value {
  font-weight: 500;
  color: var(--color-text-1);
}

.footer-goal-text {
  max-width: 400px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.footer-goal--empty .footer-value {
  color: var(--color-text-4);
  font-style: italic;
}

.footer-stats .footer-value {
  color: var(--color-text-3);
}

/* 已归档 Sprint Footer 样式 */
.board-footer--archived {
  background: var(--color-fill-2);
  border-top: 1px solid var(--color-warning-light-4, #faad14);
}

.footer-archived-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: var(--color-warning-light-1, #fffbe6);
  border: 1px solid var(--color-warning-light-4, #faad14);
  border-radius: 10px;
  font-size: 12px;
  color: var(--color-warning-6, #d48806);
}

.footer-archived-icon {
  font-size: 13px;
}

.footer-archived-text {
  font-weight: 500;
}

.footer-archived-sprint-name {
  font-size: 12px;
  color: var(--color-text-2);
  font-weight: 500;
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: transform 200ms ease-out, opacity 200ms ease-out;
}
.slide-up-enter-from,
.slide-up-leave-to {
  transform: translateY(100%);
  opacity: 0;
}

/* ===== Card color scheme border ===== */
.kanban-card--color-priority-critical {
  border-left: 3px solid #ef4444;
}
.kanban-card--color-priority-high {
  border-left: 3px solid #f59e0b;
}
.kanban-card--color-priority-normal {
  border-left: 3px solid #3b82f6;
}
.kanban-card--color-priority-low {
  border-left: 3px solid #9ca3af;
}

.kanban-card--color-type-bug {
  border-left: 3px solid #ef4444;
}
.kanban-card--color-type-task {
  border-left: 3px solid #3b82f6;
}
.kanban-card--color-type-feature {
  border-left: 3px solid #10b981;
}
.kanban-card--color-type-story {
  border-left: 3px solid #8b5cf6;
}
.kanban-card--color-type-epic {
  border-left: 3px solid #f59e0b;
}

/* Project color scheme: width set by class, actual color via inline style */
.kanban-card--color-project {
  border-left: 3px solid transparent;
}

/* ===== Card meta fields row ===== */
.card-meta-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 6px;
}

.card-meta-tag {
  font-size: 10px;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  padding: 1px 6px;
  border-radius: 3px;
  white-space: nowrap;
}

.card-meta-tag:empty {
  display: none;
}

.card-meta-tag--overdue {
  color: var(--tf-danger, #f85149) !important;
  background: rgba(248, 81, 73, 0.1) !important;
  font-weight: 500;
}

.card-meta-tag--due-soon {
  color: var(--tf-warning, #d29922) !important;
  background: rgba(210, 153, 34, 0.1) !important;
  font-weight: 500;
}

.card-type-spacer {
  flex: 1;
}

/* ===== Set Assignee Button（卡片未分配时） ===== */
.set-assignee-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: 1px dashed var(--color-border);
  background: transparent;
  border-radius: 50%;
  cursor: pointer;
  color: var(--color-text-4);
  padding: 0;
  transition: border-color 0.15s, color 0.15s, background 0.15s;
  flex-shrink: 0;
}

.set-assignee-btn:hover {
  border-color: rgb(var(--primary-6));
  color: rgb(var(--primary-6));
  background: rgba(var(--primary-6), 0.06);
}

.set-assignee-icon {
  font-size: 12px;
}

/* ===== 无活跃 Sprint 引导横幅 ===== */
.board-guidance-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: rgba(var(--primary-6), 0.06);
  border: 1px solid rgba(var(--primary-6), 0.2);
  border-radius: 6px;
  margin: 0 16px 8px;
  flex-shrink: 0;
}

.guidance-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.guidance-content {
  flex: 1;
  min-width: 0;
}

.guidance-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
  margin-bottom: 2px;
}

.guidance-desc {
  font-size: 12px;
  color: var(--color-text-3);
  line-height: 1.5;
}

.guidance-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

/* ===== 截断提示横幅 ===== */
.board-truncated-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(var(--warning-6), 0.08);
  border: 1px solid rgba(var(--warning-6), 0.3);
  border-radius: 6px;
  margin: 0 16px 8px;
  flex-shrink: 0;
}

.truncated-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.truncated-text {
  font-size: 12px;
  color: var(--color-text-2);
  line-height: 1.4;
}

/* ===== 隐藏列有工单提示横幅 ===== */
.board-hidden-issues-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(var(--warning-6), 0.08);
  border: 1px solid rgba(var(--warning-6), 0.3);
  border-radius: 6px;
  margin: 0 16px 8px;
  flex-shrink: 0;
}

.board-hidden-issues-banner--info {
  background: rgba(var(--primary-6), 0.06);
  border-color: rgba(var(--primary-6), 0.2);
}

.hidden-issues-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.hidden-issues-text {
  font-size: 12px;
  color: var(--color-text-2);
  line-height: 1.4;
  flex: 1;
}

.hidden-issues-text strong {
  color: var(--color-text-1);
  font-weight: 600;
}

.hidden-issues-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

/* ===== TV 模式样式 ===== */
.kanban-page:fullscreen {
  background: var(--color-bg-1);
}

.kanban-page:fullscreen .board-toolbar {
  padding: 16px 32px;
}

.kanban-page:fullscreen .kanban-card {
  min-height: 100px;
  font-size: 16px;
}

.kanban-page:fullscreen .card-title {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.5;
}

.kanban-page:fullscreen .column-header-title {
  font-size: 16px;
}

.kanban-page:fullscreen .board-column {
  min-width: 320px;
}

/* ===== XL 卡片尺寸 ===== */
.kanban-card--XL {
  padding: 14px 16px;
  min-height: 90px;
}

.kanban-card--XL .card-title {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
  -webkit-line-clamp: 4;
}

/* ===== Orphan Issues 面板 ===== */
.orphan-panel {
  position: absolute;
  top: 0;
  right: 0;
  width: 320px;
  height: 100%;
  background: var(--color-bg-2);
  border-left: 1px solid var(--color-border);
  z-index: 20;
  display: flex;
  flex-direction: column;
  box-shadow: -2px 0 8px rgba(0, 0, 0, 0.08);
}

.orphan-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.orphan-panel-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
}

.orphan-panel-desc {
  padding: 8px 16px;
  font-size: 12px;
  color: var(--color-text-3);
  border-bottom: 1px solid var(--color-border-2);
}

.orphan-panel-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.orphan-issue-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  cursor: pointer;
  transition: background 150ms;
}

.orphan-issue-item:hover {
  background: var(--color-fill-2);
}

.orphan-issue-key {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-2);
  flex-shrink: 0;
}

.orphan-issue-title {
  font-size: 13px;
  color: var(--color-text-1);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.orphan-issue-status {
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-fill-3);
  padding: 1px 6px;
  border-radius: 3px;
  flex-shrink: 0;
}

/* Slide transition for orphan panel */
.slide-right-enter-active,
.slide-right-leave-active {
  transition: transform 200ms ease, opacity 200ms ease;
}

.slide-right-enter-from,
.slide-right-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

/* ===== 泳道行拖拽排序 ===== */

/* 拖拽把手 */
.swimlane-drag-handle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  font-size: 14px;
  color: var(--color-text-4);
  cursor: grab;
  user-select: none;
  border-radius: 3px;
  flex-shrink: 0;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.15s, visibility 0.15s, color 0.15s, background 0.15s;
}

/* 鼠标悬停在泳道行标题时显示拖拽把手 */
.swimlane-row-header:hover .swimlane-drag-handle {
  opacity: 1;
  visibility: visible;
}

.swimlane-drag-handle:hover {
  color: var(--color-text-2);
  background: var(--color-fill-3);
}

.swimlane-drag-handle:active {
  cursor: grabbing;
}

/* 正在被拖拽的泳道行 */
.swimlane-row--dragging {
  opacity: 0.4;
}

.swimlane-row--dragging .swimlane-row-header {
  cursor: grabbing;
}

/* 拖拽目标（悬停在其上方的泳道行）：显示插入线 */
.swimlane-row--drag-over {
  border-top: 2px solid rgb(var(--primary-6));
  margin-top: -2px;
}
</style>
