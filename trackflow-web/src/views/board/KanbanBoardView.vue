<template>
  <div class="kanban-page">
    <!-- 顶部工具栏 -->
    <div class="board-toolbar">
      <div class="toolbar-left">
        <h2 class="page-title">看板</h2>
        <a-select
          v-model="selectedProject"
          placeholder="选择项目"
          style="width: 200px"
          size="small"
          allow-search
          :loading="projectLoadState === 'loading'"
          @change="onProjectChange"
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
        <a-select
          v-model="selectedSprint"
          placeholder="所有迭代"
          style="width: 180px"
          size="small"
          allow-clear
          :disabled="!selectedProject"
          @change="loadBoard"
        >
          <a-option v-for="s in sprints" :key="s.id" :value="s.id">
            {{ s.name }}
          </a-option>
        </a-select>
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
        </a-select>
      </div>
      <div class="toolbar-right">
        <!-- Progress Indicator (各列卡片数 mini bar chart) -->
        <div v-if="selectedProject && visibleStatuses.length > 0" class="progress-indicator" role="img" aria-label="各列工单分布">
          <div
            v-for="status in visibleStatuses"
            :key="status.id"
            class="progress-bar"
            :class="{ 'progress-bar--collapsed': collapsedColumns.has(status.id) }"
            :style="{ height: getProgressBarHeight(status.id), backgroundColor: status.color || 'var(--color-fill-4)' }"
            :title="`${localizeStatusName(status.name)}：${getColumnIssues(status.id).length} 个工单`"
            @click="scrollToColumn(status.id)"
          ></div>
        </div>
        <a-divider v-if="selectedProject && visibleStatuses.length > 0" direction="vertical" style="margin: 0 4px" />
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
        <div class="search-wrapper">
          <a-input
            v-model="keyword"
            placeholder="搜索工单（编号/标题/负责人）"
            size="small"
            style="width: 240px"
            allow-clear
            @input="onSearchInput"
            @press-enter="loadIssuesWithLoading"
            @clear="onSearchClear"
          >
            <template #prefix>
              <icon-search />
            </template>
          </a-input>
          <transition name="fade">
            <span v-if="isSearchActive" class="search-active-badge">
              筛选中
            </span>
          </transition>
        </div>
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
        </div>
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
        <a-tooltip v-if="canEditProject" content="看板列设置">
          <a-button
            size="small"
            :disabled="!selectedProject"
            @click="showSettings = true"
          >
            <template #icon><icon-settings /></template>
          </a-button>
        </a-tooltip>
      </div>
    </div>

    <!-- 加载状态 -->
    <a-spin :loading="loading" tip="加载看板数据..." class="board-spin">
      <!-- 截断提示：工单数超过安全上限 -->
      <div v-if="boardTruncated && !loading" class="board-truncated-banner">
        <span class="truncated-icon">⚠️</span>
        <span class="truncated-text">
          当前项目共 {{ boardTotalCount }} 个工单，看板仅展示前 {{ issues.length }} 个。请使用搜索或筛选缩小范围。
        </span>
      </div>
      <!-- 隐藏列中有工单的提示 -->
      <div v-if="hiddenIssueColumns.length > 0 && !loading" class="board-hidden-issues-banner">
        <span class="hidden-issues-icon">👁️‍🗨️</span>
        <span class="hidden-issues-text">
          有工单存在于已隐藏的列中：{{ hiddenIssueColumns.map(c => localizeStatusName(c.statusName)).join('、') }}。
          <template v-if="canEditProject">
            <a-link size="small" @click="showSettings = true">打开列设置</a-link> 查看或调整。
          </template>
          <span v-else>请联系项目管理员调整列配置。</span>
        </span>
      </div>
      <div class="board-main-area">
        <!-- Backlog 面板 -->
        <BacklogPanel
          ref="backlogPanelRef"
          :visible="showBacklog"
          :project-id="selectedProject || ''"
          :board-status-ids="boardStatusIdsForBacklog"
          @close="showBacklog = false"
          @open-issue="openIssue"
          @drag-start="onBacklogDragStart"
          @drag-end="onBacklogDragEnd"
        />
      <!-- ===== 无分组模式（原始平面看板） ===== -->
      <div
        v-if="selectedProject && visibleStatuses.length > 0 && !showNoSearchResults && swimlaneGroupBy === 'none'"
        class="board-container"
      >
        <template v-for="status in visibleStatuses" :key="status.id">
          <!-- 展开状态的列（有工单/手动展开空列/非手动折叠） -->
          <div
            v-if="!isColumnCollapsed(status.id)"
            class="board-column"
            :data-column-id="status.id"
            :class="{
              'board-column--expanded-empty': getColumnIssues(status.id).length === 0,
              'board-column--drop-target': dragOverColumnId === status.id && !dragOverSwimlaneKey,
              'board-column--drop-forbidden': dragOverColumnId === status.id && !dragOverSwimlaneKey && !isDropAllowed(status.id)
            }"
            @dragover="onDragOver($event, status.id)"
            @dragleave="onDragLeave($event)"
            @drop="onDrop($event, status.id)"
          >
            <div
              class="column-header column-header--clickable"
              :style="{ borderTopColor: status.color }"
              role="button"
              tabindex="0"
              :aria-label="`折叠 ${localizeStatusName(status.name)} 列`"
              title="点击折叠此列"
              @click="toggleColumnCollapse(status.id)"
              @keydown.enter="toggleColumnCollapse(status.id)"
            >
              <span class="column-title">{{ localizeStatusName(status.name) }}</span>
              <span
                class="column-count"
                :class="getWipClass(status.id)"
                :title="getWipTooltip(status.id)"
              >{{ getColumnIssues(status.id).length }}<template v-if="getWipMax(status.id) !== null">/{{ getWipMax(status.id) }}</template></span>
              <span v-if="getWipWarning(status.id)" class="wip-warning" :class="getWipWarning(status.id)">
                {{ getWipWarning(status.id) === 'wip-over' ? '⚠' : '▽' }}
              </span>
            </div>
            <div class="column-body">
              <div
                v-for="issue in getColumnIssues(status.id)"
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
                <div v-if="cardSize !== 'S' && issue.customFieldValues && Object.keys(issue.customFieldValues).length > 0" class="card-custom-fields">
                  <span
                    v-for="(val, key) in getVisibleCustomFields(issue)"
                    :key="key"
                    class="card-cf-tag"
                  >{{ val }}</span>
                </div>
                <!-- Card metadata fields based on card config -->
                <div v-if="cardSize !== 'S' && (isCardFieldVisible('dueDate') || isCardFieldVisible('sprint') || isCardFieldVisible('estimatedHours') || isCardFieldVisible('tags'))" class="card-meta-fields">
                  <span v-if="isCardFieldVisible('dueDate') && issue.dueDate" class="card-meta-tag">📅 {{ issue.dueDate.slice(5) }}</span>
                  <span v-if="isCardFieldVisible('sprint') && issue.sprintId" class="card-meta-tag">🏃 {{ getSprintName(issue.sprintId) }}</span>
                  <span v-if="isCardFieldVisible('estimatedHours')" class="card-meta-tag"><!-- placeholder for future --></span>
                </div>
                <div class="card-footer">
                  <span v-if="isCardFieldVisible('type')" class="card-type">{{ typeLabel(issue.issueType) }}</span>
                  <span v-else class="card-type-spacer"></span>
                  <div class="card-assignee-avatar" v-if="isCardFieldVisible('assignee') && issue.assigneeName" :title="issue.assigneeName">
                    <img
                      v-if="issue.assigneeAvatarUrl"
                      :src="issue.assigneeAvatarUrl"
                      :alt="issue.assigneeName"
                      class="avatar-img"
                    />
                    <span v-else class="avatar-initials">{{ getInitials(issue.assigneeName) }}</span>
                  </div>
                </div>
              </div>
              <div
                v-if="getColumnIssues(status.id).length === 0"
                class="column-empty-state"
                :class="{ 'column-empty-state--drop-hint': isDragging && isDropAllowed(status.id) }"
              >
                <template v-if="isDragging && isDropAllowed(status.id)">
                  <div class="column-empty-icon">📥</div>
                  <div class="column-empty-text">释放以移动到此状态</div>
                </template>
                <template v-else-if="isDragging && !isDropAllowed(status.id)">
                  <div class="column-empty-icon">🚫</div>
                  <div class="column-empty-text">不允许转换到此状态</div>
                </template>
                <template v-else>
                  <div class="column-empty-icon">📭</div>
                  <div class="column-empty-text">该状态下暂无工单</div>
                  <div class="column-empty-hint">拖拽工单到此列或创建新工单</div>
                </template>
              </div>
              <!-- 内联快速创建卡片 -->
              <div
                v-if="canCreateIssue && !isDragging"
                class="add-card-area"
              >
                <div
                  v-if="addingCardColumnId === status.id && !addingCardSwimlaneKey"
                  class="add-card-form"
                >
                  <input
                    :ref="(el) => setAddCardInputRef(el, status.id, '')"
                    v-model="addCardTitle"
                    class="add-card-input"
                    placeholder="输入工单标题，回车创建"
                    :disabled="addCardSubmitting"
                    @keydown.enter.prevent="submitAddCard(status.id)"
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
                      @click="submitAddCard(status.id)"
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
                  @click="startAddCard(status.id)"
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
            :data-column-id="status.id"
            :class="{
              'board-column-collapsed--drop-target': dragOverColumnId === status.id && isDropAllowed(status.id),
              'board-column-collapsed--drop-forbidden': dragOverColumnId === status.id && !isDropAllowed(status.id)
            }"
            role="button"
            tabindex="0"
            :aria-label="`${localizeStatusName(status.name)}，${getColumnIssues(status.id).length} 个工单，点击展开`"
            :title="`${localizeStatusName(status.name)} (${getColumnIssues(status.id).length} 工单) - ${isDragging ? '释放以移动' : '点击展开'}`"
            @click="!isDragging && toggleColumnCollapse(status.id)"
            @keydown.enter="toggleColumnCollapse(status.id)"
            @dragover="onDragOver($event, status.id)"
            @dragleave="onDragLeave($event)"
            @drop="onDrop($event, status.id)"
          >
            <div class="collapsed-indicator" :style="{ backgroundColor: status.color || 'var(--color-border)' }"></div>
            <span class="collapsed-name">{{ localizeStatusName(status.name) }}</span>
            <span class="collapsed-count">{{ getColumnIssues(status.id).length }}</span>
          </div>
        </template>
      </div>

      <!-- ===== Swimlane 分组模式 ===== -->
      <div
        v-if="selectedProject && visibleStatuses.length > 0 && !showNoSearchResults && swimlaneGroupBy !== 'none'"
        class="swimlane-container"
      >
        <!-- Swimlane 表头（状态列标题） -->
        <div class="swimlane-header">
          <div class="swimlane-label-cell"></div>
          <div class="swimlane-columns-header">
            <div
              v-for="status in visibleStatuses"
              :key="status.id"
              class="swimlane-col-header"
              :class="{ 'swimlane-col-header--collapsed': collapsedColumns.has(status.id) }"
              :style="{ borderTopColor: status.color }"
              role="button"
              tabindex="0"
              :title="collapsedColumns.has(status.id) ? '点击展开此列' : '点击折叠此列'"
              @click="toggleColumnCollapse(status.id)"
              @keydown.enter="toggleColumnCollapse(status.id)"
            >
              <span class="column-title">{{ localizeStatusName(status.name) }}</span>
              <span
                v-if="!collapsedColumns.has(status.id)"
                class="column-count"
                :class="getWipClass(status.id)"
                :title="getWipTooltip(status.id)"
              >{{ getColumnIssues(status.id).length }}<template v-if="getWipMax(status.id) !== null">/{{ getWipMax(status.id) }}</template></span>
              <span
                v-else
                class="column-count"
              >{{ getColumnIssues(status.id).length }}</span>
              <span v-if="!collapsedColumns.has(status.id) && getWipWarning(status.id)" class="wip-warning" :class="getWipWarning(status.id)">
                {{ getWipWarning(status.id) === 'wip-over' ? '⚠' : '▽' }}
              </span>
            </div>
          </div>
        </div>

        <!-- Swimlane 各行 -->
        <div class="swimlane-body">
          <div
            v-for="lane in swimlanes"
            :key="lane.key"
            class="swimlane-row"
            :class="{ 'swimlane-row--collapsed': collapsedSwimlanes.has(lane.key) }"
          >
            <!-- 泳道行标题 -->
            <div
              class="swimlane-row-header"
              role="button"
              tabindex="0"
              @click="toggleSwimlane(lane.key)"
              @keydown.enter="toggleSwimlane(lane.key)"
            >
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
                  v-for="status in visibleStatuses"
                  :key="status.id"
                  class="swimlane-cell"
                  :class="{
                    'swimlane-cell--collapsed': collapsedColumns.has(status.id),
                    'swimlane-cell--drop-target': dragOverColumnId === status.id && dragOverSwimlaneKey === lane.key,
                    'swimlane-cell--drop-forbidden': dragOverColumnId === status.id && dragOverSwimlaneKey === lane.key && !isDropAllowed(status.id)
                  }"
                  @dragover="onDragOverSwimlane($event, status.id, lane.key)"
                  @dragleave="onDragLeaveSwimlane($event)"
                  @drop="onDrop($event, status.id)"
                >
                  <template v-if="!collapsedColumns.has(status.id)">
                    <div
                      v-for="issue in getSwimlaneColumnIssues(lane.key, status.id)"
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
                      <div v-if="cardSize !== 'S' && issue.customFieldValues && Object.keys(issue.customFieldValues).length > 0" class="card-custom-fields">
                        <span
                          v-for="(val, key) in getVisibleCustomFields(issue)"
                          :key="key"
                          class="card-cf-tag"
                        >{{ val }}</span>
                      </div>
                      <!-- Card metadata fields based on card config -->
                      <div v-if="cardSize !== 'S' && (isCardFieldVisible('dueDate') || isCardFieldVisible('sprint') || isCardFieldVisible('estimatedHours') || isCardFieldVisible('tags'))" class="card-meta-fields">
                        <span v-if="isCardFieldVisible('dueDate') && issue.dueDate" class="card-meta-tag">📅 {{ issue.dueDate.slice(5) }}</span>
                        <span v-if="isCardFieldVisible('sprint') && issue.sprintId" class="card-meta-tag">🏃 {{ getSprintName(issue.sprintId) }}</span>
                      </div>
                      <div class="card-footer">
                        <span v-if="isCardFieldVisible('type')" class="card-type">{{ typeLabel(issue.issueType) }}</span>
                        <span v-else class="card-type-spacer"></span>
                        <div class="card-assignee-avatar" v-if="isCardFieldVisible('assignee') && issue.assigneeName" :title="issue.assigneeName">
                          <img
                            v-if="issue.assigneeAvatarUrl"
                            :src="issue.assigneeAvatarUrl"
                            :alt="issue.assigneeName"
                            class="avatar-img"
                          />
                          <span v-else class="avatar-initials">{{ getInitials(issue.assigneeName) }}</span>
                        </div>
                      </div>
                    </div>
                    <!-- 空单元格 drop hint -->
                    <div
                      v-if="getSwimlaneColumnIssues(lane.key, status.id).length === 0 && isDragging && isDropAllowed(status.id)"
                      class="swimlane-cell-empty-hint"
                    >
                      📥
                    </div>
                    <!-- Swimlane 内联快速创建卡片 -->
                    <div
                      v-if="canCreateIssue && !isDragging"
                      class="add-card-area add-card-area--swimlane"
                    >
                      <div
                        v-if="addingCardColumnId === status.id && addingCardSwimlaneKey === lane.key"
                        class="add-card-form"
                      >
                        <input
                          :ref="(el) => setAddCardInputRef(el, status.id, lane.key)"
                          v-model="addCardTitle"
                          class="add-card-input"
                          placeholder="输入标题，回车创建"
                          :disabled="addCardSubmitting"
                          @keydown.enter.prevent="submitAddCard(status.id, lane.key)"
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
                            @click="submitAddCard(status.id, lane.key)"
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
                        @click="startAddCard(status.id, lane.key)"
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

      <!-- 空状态：搜索无结果 -->
      <div v-if="showNoSearchResults" class="empty-state">
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
      @saved="onSettingsSaved"
    />

    <!-- 工单预览侧边面板 -->
    <IssuePreviewDrawer
      :visible="previewVisible"
      :issue-id="previewIssueId"
      @update:visible="previewVisible = $event"
      @go-detail="onPreviewGoDetail"
    />

    <!-- 批量操作栏（底部固定） -->
    <transition name="slide-up">
      <div v-if="selectedCount > 0" class="batch-toolbar-wrapper">
        <BatchActionToolbar
          :selected-count="selectedCount"
          :selected-issues="selectedIssues"
          :can-delete="canDeleteIssue"
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
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, h, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Message, Notification } from '@arco-design/web-vue'
import { issueApi, sprintApi, boardApi, workflowApi, queryApi } from '@/api'
import type { IssueVO, IssueStatusVO, SprintVO, BoardColumnVO, BoardCardConfigVO, BoardColumnMergeGroupVO } from '@/api/types'
import { useProjectStore } from '@/stores/project'
import { usePermission } from '@/composables/usePermission'
import { useProjectList } from '@/composables/useProjectList'
import { useSelection } from '@/views/issue/composables/useSelection'
import { useBatchOps } from '@/views/issue/composables/useBatchOps'
import { localizeStatusName, localizeIssueType, localizePriority } from '@/utils/fieldLabels'
import { extractVersion, showActionFeedback } from '@/utils/transition'
import BoardSettingsDrawer from './BoardSettingsDrawer.vue'
import BacklogPanel from './BacklogPanel.vue'
import IssuePreviewDrawer from './IssuePreviewDrawer.vue'
import BatchActionToolbar from '@/views/issue/components/BatchActionToolbar.vue'
import { IconSettings, IconSearch, IconList } from '@arco-design/web-vue/es/icon'

const router = useRouter()
const projectStore = useProjectStore()

// ===== Card Size 控制 =====
type CardSize = 'S' | 'M' | 'L'
const CARD_SIZE_KEY = 'tf_kanban_card_size'
const cardSize = ref<CardSize>((localStorage.getItem(CARD_SIZE_KEY) as CardSize) || 'M')
const cardSizeOptions = [
  { value: 'S' as const, label: 'S' },
  { value: 'M' as const, label: 'M' },
  { value: 'L' as const, label: 'L' }
]

function setCardSize(size: CardSize) {
  cardSize.value = size
  localStorage.setItem(CARD_SIZE_KEY, size)
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

/** 获取卡片可见的自定义字段（最多显示 3 个） */
function getVisibleCustomFields(issue: IssueVO): Record<string, string> {
  if (!issue.customFieldValues) return {}
  const entries = Object.entries(issue.customFieldValues)
  const maxFields = cardSize.value === 'L' ? 4 : 2
  return Object.fromEntries(entries.slice(0, maxFields))
}

/** 判断卡片上是否应展示某个字段（基于 cardConfig） */
function isCardFieldVisible(field: string): boolean {
  return cardConfig.value.visibleFields.includes(field)
}

/** 获取卡片的颜色方案 CSS class */
function getCardColorClass(issue: IssueVO): string {
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
  return ''
}

/** 根据 sprintId 获取 Sprint 名称 */
function getSprintName(sprintId: string): string {
  const sprint = sprints.value.find(s => s.id === sprintId)
  return sprint?.name || ''
}

const selectedProject = computed({
  get: () => projectStore.selectedProjectId,
  set: (val) => projectStore.selectProject(val)
})

// 权限控制
const { canChangeStatus, canCreateIssue, canEditProject, canDeleteIssue } = usePermission(() => selectedProject.value)
const selectedSprint = ref<string | undefined>(undefined)
const keyword = ref('')
const loading = ref(false)
const { projects, projectLoadState, loadProjects } = useProjectList()
const currentProjectName = computed(() => {
  if (!selectedProject.value) return ''
  const p = projects.value.find(proj => proj.id === selectedProject.value)
  return p?.name || ''
})

// ===== Board Behavior 配置 =====
const boardFilterMode = ref<'all' | 'active_sprint' | 'query'>('all')
const boardFilterQuery = ref<string | null>(null)
const boardDoneRetentionDays = ref<number | null>(null)

/** 当前是否有 Board Behavior 过滤生效 */
const isBehaviorFilterActive = computed(() => {
  return boardFilterMode.value !== 'all' || boardDoneRetentionDays.value !== null
})

// ===== Backlog 面板 =====
const BACKLOG_VISIBLE_KEY = 'tf_kanban_backlog_visible'
const showBacklog = ref(localStorage.getItem(BACKLOG_VISIBLE_KEY) === 'true')
const backlogPanelRef = ref<InstanceType<typeof BacklogPanel> | null>(null)
const backlogDraggingIssue = ref<IssueVO | null>(null)

// ===== 工单预览面板 =====
const previewVisible = ref(false)
const previewIssueId = ref<string | null>(null)

function openPreview(issue: IssueVO) {
  previewIssueId.value = issue.id
  previewVisible.value = true
}

function closePreview() {
  previewVisible.value = false
}

function onPreviewGoDetail(issueId: string) {
  router.push({ name: 'IssueDetail', params: { id: issueId } })
}

function toggleBacklog() {
  showBacklog.value = !showBacklog.value
  localStorage.setItem(BACKLOG_VISIBLE_KEY, String(showBacklog.value))
}

function onBacklogDragStart(issue: IssueVO) {
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
type SwimlaneGroupBy = 'none' | 'assignee' | 'priority' | 'type' | 'sprint' | 'tag'
const SWIMLANE_STORAGE_KEY = 'tf_kanban_swimlane'
const COLLAPSED_SWIMLANES_KEY = 'tf_kanban_collapsed_swimlanes'

const swimlaneGroupBy = ref<SwimlaneGroupBy>(
  (localStorage.getItem(SWIMLANE_STORAGE_KEY) as SwimlaneGroupBy) || 'none'
)
const collapsedSwimlanes = ref<Set<string>>(
  new Set(JSON.parse(localStorage.getItem(COLLAPSED_SWIMLANES_KEY) || '[]'))
)

function onSwimlaneChange() {
  localStorage.setItem(SWIMLANE_STORAGE_KEY, swimlaneGroupBy.value)
  // 切换分组维度时清除折叠状态
  collapsedSwimlanes.value.clear()
  localStorage.removeItem(COLLAPSED_SWIMLANES_KEY)
  // 持久化到服务端（静默保存，不阻塞 UI）
  if (selectedProject.value) {
    boardApi.saveSwimlaneConfig(selectedProject.value, {
      groupByField: swimlaneGroupBy.value
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

// Swimlane 数据结构
interface SwimlaneRow {
  key: string
  label: string
  issues: IssueVO[]
}

// 类型映射（使用共享工具）
const TYPE_LABELS = { Task: '任务', Bug: '缺陷', Feature: '需求', Epic: '史诗', Story: '故事' } as Record<string, string>

const swimlanes = computed<SwimlaneRow[]>(() => {
  if (swimlaneGroupBy.value === 'none') return []

  const allIssues = issues.value

  switch (swimlaneGroupBy.value) {
    case 'assignee':
      return groupByAssignee(allIssues)
    case 'priority':
      return groupByPriority(allIssues)
    case 'type':
      return groupByType(allIssues)
    case 'sprint':
      return groupBySprint(allIssues)
    case 'tag':
      return groupByTag(allIssues)
    default:
      return []
  }
})

function groupByAssignee(allIssues: IssueVO[]): SwimlaneRow[] {
  const groups = new Map<string, IssueVO[]>()
  const unassigned: IssueVO[] = []

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

function groupByPriority(allIssues: IssueVO[]): SwimlaneRow[] {
  const priorities = ['Critical', 'High', 'Normal', 'Low']
  const groups = new Map<string, IssueVO[]>()
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

function groupByType(allIssues: IssueVO[]): SwimlaneRow[] {
  const types = ['Bug', 'Task', 'Feature', 'Story']
  const groups = new Map<string, IssueVO[]>()
  const other: IssueVO[] = []

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

function groupBySprint(allIssues: IssueVO[]): SwimlaneRow[] {
  const groups = new Map<string, IssueVO[]>()
  const noSprint: IssueVO[] = []

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

function groupByTag(allIssues: IssueVO[]): SwimlaneRow[] {
  const groups = new Map<string, IssueVO[]>()
  const noTag: IssueVO[] = []

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

/** 获取某泳道中某状态列的工单 */
function getSwimlaneColumnIssues(laneKey: string, statusId: string): IssueVO[] {
  const lane = swimlanes.value.find(l => l.key === laneKey)
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

// 搜索相关
let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null
const isSearchActive = computed(() => keyword.value.trim().length > 0)
const showNoSearchResults = computed(() =>
  isSearchActive.value && selectedProject.value && issues.value.length === 0 && !loading.value
)

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
  } catch {
    issues.value = []
    Message.error('搜索失败')
  } finally {
    loading.value = false
  }
}

function onProjectChange() {
  keyword.value = ''
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  loadBoard()
}
const sprints = ref<SprintVO[]>([])
const statuses = ref<IssueStatusVO[]>([])
const issues = ref<IssueVO[]>([])

// ===== 卡片多选 =====
const {
  selectedIds, selectedCount, selectedIssues,
  toggle: toggleCardSelection, clearSelection
} = useSelection(issues)

const { batchTransitStatus, batchAssign, batchUpdateSprint, batchUpdatePriority, batchTagAdd, batchTagRemove, batchAddLink, batchDelete } = useBatchOps()

// 看板列配置
const allColumnConfigs = ref<BoardColumnVO[]>([])
const showSettings = ref(false)

// 看板卡片配置（字段显示 + 颜色方案）
const cardConfig = ref<BoardCardConfigVO>({
  visibleFields: ['assignee', 'priority', 'type'],
  colorScheme: 'none'
})

// 看板列合并配置
const columnMerges = ref<BoardColumnMergeGroupVO[]>([])

// 根据列配置过滤出可见的状态
const visibleStatuses = computed(() => {
  if (allColumnConfigs.value.length === 0) {
    return statuses.value
  }
  return allColumnConfigs.value
    .filter(c => c.visible)
    .map(c => ({
      id: c.statusId,
      name: c.statusName,
      code: c.statusCode,
      color: c.statusColor,
      category: c.statusCategory,
      isDefault: false,
      isClosed: c.statusCategory === 'done' || c.statusCategory === 'cancelled',
      sortOrder: c.sortOrder
    } as IssueStatusVO))
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
function getEffectiveColumnIssues(column: EffectiveColumn): IssueVO[] {
  return issues.value.filter(i => column.statusIds.includes(i.statusId))
}

/** 获取 Swimlane 中某有效列的工单 */
function getSwimlaneEffectiveColumnIssues(laneKey: string, column: EffectiveColumn): IssueVO[] {
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

/** 判断列是否处于折叠状态（手动折叠或空列自动折叠） */
function isColumnCollapsed(statusId: string): boolean {
  // 手动折叠优先级最高
  if (collapsedColumns.value.has(statusId)) return true
  // 空列且未手动展开 → 自动折叠
  if (getColumnIssues(statusId).length === 0 && !expandedEmptyColumns.value.has(statusId)) return true
  return false
}

/** 切换列的折叠/展开状态 */
function toggleColumnCollapse(statusId: string) {
  if (collapsedColumns.value.has(statusId)) {
    // 展开
    collapsedColumns.value.delete(statusId)
    expandedEmptyColumns.value.add(statusId) // 确保空列也被展开
  } else {
    // 折叠
    collapsedColumns.value.add(statusId)
    expandedEmptyColumns.value.delete(statusId)
  }
  saveCollapsedColumnsState()
}

// ===== Progress Indicator（各列卡片数 mini bar chart） =====

/** 计算进度条高度（相对最大列的比例） */
function getProgressBarHeight(statusId: string): string {
  const count = getColumnIssues(statusId).length
  if (count === 0) return '2px'
  const maxCount = Math.max(...visibleStatuses.value.map(s => getColumnIssues(s.id).length), 1)
  const height = Math.max(4, Math.round((count / maxCount) * 24))
  return `${height}px`
}

/** 滚动到指定列 */
function scrollToColumn(statusId: string) {
  // 如果列被折叠，先展开
  if (collapsedColumns.value.has(statusId)) {
    toggleColumnCollapse(statusId)
  }
  // 在下一帧滚动到目标列
  setTimeout(() => {
    const container = document.querySelector('.board-container') || document.querySelector('.swimlane-container')
    if (!container) return
    const columnEl = container.querySelector(`[data-column-id="${statusId}"]`)
    if (columnEl) {
      columnEl.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' })
    }
  }, 50)
}

// ===== 拖拽状态 =====
const draggingIssue = ref<IssueVO | null>(null)
const dragOverColumnId = ref<string | null>(null)
const allowedTargetStatuses = ref<Set<string>>(new Set())
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

function getColumnIssues(statusId: string): IssueVO[] {
  return issues.value.filter(i => i.statusId === statusId)
}

// ===== WIP 限制辅助函数 =====

function getColumnConfig(statusId: string): BoardColumnVO | undefined {
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

function openIssue(issue: IssueVO) {
  if (draggingIssue.value) return
  openPreview(issue)
}

/**
 * 卡片单击处理：
 * - Ctrl/Meta + Click：切换选中状态（多选）
 * - 无修饰键：延迟 200ms 打开预览（被双击取消则不打开）
 */
let clickTimer: ReturnType<typeof setTimeout> | null = null

function onCardClick(event: MouseEvent | KeyboardEvent, issue: IssueVO) {
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
function onCardDblClick(issue: IssueVO) {
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
function onCardKeydown(event: KeyboardEvent, issue: IssueVO) {
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

function isCardDraggable(issue: IssueVO): boolean {
  if (!canChangeStatus.value) return false
  if (transitionableSourceStatuses.value.size === 0) return true
  return transitionableSourceStatuses.value.has(issue.statusId)
}

async function onDragStart(event: DragEvent, issue: IssueVO) {
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

  try {
    const res = await issueApi.getAvailableTransitions(issue.id)
    const allowed = res.data || []
    allowedTargetStatuses.value = new Set(allowed.map(s => s.id))
  } catch {
    allowedTargetStatuses.value = new Set(statuses.value.map(s => s.id))
  }
}

function onDragEnd() {
  draggingIssue.value = null
  dragOverColumnId.value = null
  dragOverSwimlaneKey.value = null
  allowedTargetStatuses.value.clear()
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
  // Allow drop from backlog panel
  if (backlogDraggingIssue.value) {
    return allowedTargetStatuses.value.has(targetStatusId)
  }
  if (!draggingIssue.value) return false
  if (draggingIssue.value.statusId === targetStatusId) return false
  return allowedTargetStatuses.value.has(targetStatusId)
}

async function onDrop(event: DragEvent, targetStatusId: string) {
  event.preventDefault()
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

  const oldStatusId = issue.statusId
  const targetStatus = statuses.value.find(s => s.id === targetStatusId)

  // 乐观更新
  issue.statusId = targetStatusId
  transitioningIssueIds.value.add(issue.id)

  draggingIssue.value = null
  allowedTargetStatuses.value.clear()

  try {
    const res = await issueApi.transitStatus(issue.id, targetStatusId, undefined, issue.version)

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

    const undoEntry: UndoEntry = {
      issueId: issue.id,
      issueKey: issue.issueKey,
      oldStatusId: oldStatusId,
      newStatusId: targetStatusId,
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
  } catch (e: any) {
    issue.statusId = oldStatusId
    const errMsg = e.response?.data?.message || '状态变更失败'
    Message.error(`${issue.issueKey} 移动失败：${errMsg}`)
  } finally {
    transitioningIssueIds.value.delete(issue.id)
  }
}

/** Handle drop from Backlog panel: assign sprint + change status */
async function handleBacklogDrop(issue: IssueVO, targetStatusId: string) {
  backlogDraggingIssue.value = null
  allowedTargetStatuses.value.clear()

  // Determine which sprint to assign
  const targetSprintId = selectedSprint.value || getActiveSprintId()
  if (!targetSprintId) {
    Message.warning('请先选择一个 Sprint 或确保项目有活跃的 Sprint')
    return
  }

  const targetStatus = statuses.value.find(s => s.id === targetStatusId)

  try {
    // Update sprint first, then change status
    await issueApi.update(issue.id, { sprintId: targetSprintId })
    // Transition status if different from current
    let newVersion = issue.version
    if (issue.statusId !== targetStatusId) {
      const res = await issueApi.transitStatus(issue.id, targetStatusId, undefined, issue.version)
      const extracted = extractVersion(res.data)
      if (extracted != null) {
        newVersion = extracted
      } else {
        // update + transitStatus = version +2 (each updateById increments version)
        newVersion = (issue.version || 0) + 2
      }
      showActionFeedback(res.data)
    } else {
      // Only sprint update, version +1
      newVersion = (issue.version || 0) + 1
    }

    // Remove from backlog panel
    backlogPanelRef.value?.removeIssue(issue.id)

    // Add to board issues list
    const updatedIssue: IssueVO = {
      ...issue,
      statusId: targetStatusId,
      sprintId: targetSprintId,
      version: newVersion
    }
    issues.value.push(updatedIssue)

    Message.success(`${issue.issueKey} 已添加到看板「${localizeStatusName(targetStatus?.name)}」`)
  } catch (e: any) {
    const errMsg = e.response?.data?.message || '操作失败'
    Message.error(`${issue.issueKey} 移入看板失败：${errMsg}`)
  }
}

/** Get the active sprint ID for the current project */
function getActiveSprintId(): string | undefined {
  const activeSprint = sprints.value.find(s => s.status === 'active')
  return activeSprint?.id
}

// ===== 撤销逻辑 =====

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
    return
  }
  try {
    const res = await boardApi.getSwimlaneConfig(selectedProject.value)
    if (res.data && res.data.groupByField) {
      swimlaneGroupBy.value = res.data.groupByField as SwimlaneGroupBy
      // 同步到 localStorage（兼容本地快速切换）
      localStorage.setItem(SWIMLANE_STORAGE_KEY, res.data.groupByField)
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
    sprints.value = res.data || []
  } catch {
    sprints.value = []
    Message.error('加载迭代列表失败')
  }
}

/** 加载 Board Behavior 配置（过滤模式 + 完成工单保留天数） */
async function loadBoardBehavior() {
  if (!selectedProject.value) return
  try {
    const res = await boardApi.getGeneralConfig(selectedProject.value)
    if (res.data) {
      boardFilterMode.value = (res.data.filterMode as 'all' | 'active_sprint' | 'query') || 'all'
      boardFilterQuery.value = res.data.filterQuery ?? null
      boardDoneRetentionDays.value = res.data.doneRetentionDays ?? null
    }
  } catch {
    boardFilterMode.value = 'all'
    boardFilterQuery.value = null
    boardDoneRetentionDays.value = null
  }
}

async function loadBoard() {
  if (!selectedProject.value) { issues.value = []; return }
  expandedEmptyColumns.value.clear()
  loadCollapsedColumnsState()
  loading.value = true
  try {
    await Promise.all([loadSprints(), loadBoardColumns(), loadCardConfig(), loadSwimlaneConfig(), loadColumnMerges(), loadTransitionableStatuses(), loadBoardBehavior()])
    await loadIssues()
  } catch {
    issues.value = []
    Message.error('加载看板数据失败')
  } finally {
    loading.value = false
  }
}

/** 看板安全上限：超过此数量的工单将截断并提示用户 */
const BOARD_MAX_ISSUES = 500
const boardTruncated = ref(false)
const boardTotalCount = ref(0)

/**
 * 加载看板全量工单（自动分页循环加载）。
 * 看板需要展示所有工单以保证 WIP 计数和 Progress Indicator 准确。
 */
async function loadIssues() {
  if (!selectedProject.value) { issues.value = []; boardTruncated.value = false; return }

  const PAGE_SIZE = 100
  let page = 1
  let allIssues: IssueVO[] = []
  let total = 0

  // Board Behavior: Query 模式 — 使用 QueryExecutor 服务端过滤
  if (boardFilterMode.value === 'query' && boardFilterQuery.value) {
    let filters: any[] = []
    try {
      filters = JSON.parse(boardFilterQuery.value)
    } catch {
      // 无效的 filter query，不加载
      issues.value = []
      boardTotalCount.value = 0
      boardTruncated.value = false
      return
    }
    // 注入项目过滤条件
    filters = [{ field: 'project', operator: 'eq', value: [selectedProject.value] }, ...filters]
    // 如果有额外 Sprint 选中，也追加
    if (selectedSprint.value) {
      filters.push({ field: 'sprint', operator: 'eq', value: [selectedSprint.value] })
    }
    // 如果有搜索关键词，追加
    if (keyword.value) {
      filters.push({ field: 'keyword', operator: 'contains', value: [keyword.value] })
    }
    // 循环加载所有页
    while (true) {
      const res = await queryApi.executeAdhoc({ filters, page, pageSize: PAGE_SIZE })
      const list = res.data?.list || []
      total = res.data?.pagination?.total || 0
      allIssues = allIssues.concat(list)
      if (allIssues.length >= total || allIssues.length >= BOARD_MAX_ISSUES || list.length < PAGE_SIZE) {
        break
      }
      page++
    }
  } else {
    // Board Behavior: 确定 sprint 过滤参数
    let effectiveSprintId = selectedSprint.value || undefined
    if (!effectiveSprintId && boardFilterMode.value === 'active_sprint') {
      const activeSprint = sprints.value.find(s => s.status === 'active')
      effectiveSprintId = activeSprint?.id
      // 如果没有活跃 Sprint，显示为空（无工单匹配）
      if (!effectiveSprintId) {
        issues.value = []
        boardTotalCount.value = 0
        boardTruncated.value = false
        return
      }
    }

    // 循环加载所有页，直到获取全部工单或达到安全上限
    while (true) {
      const res = await issueApi.list({
        projectId: selectedProject.value,
        sprintId: effectiveSprintId,
        keyword: keyword.value || undefined,
        page,
        pageSize: PAGE_SIZE
      })
      const list = res.data?.list || []
      total = res.data?.pagination?.total || 0
      allIssues = allIssues.concat(list)

      // 已加载全部 或 到达安全上限
      if (allIssues.length >= total || allIssues.length >= BOARD_MAX_ISSUES || list.length < PAGE_SIZE) {
        break
      }
      page++
    }
  }

  // Board Behavior: 已完成工单保留天数过滤（客户端过滤）
  if (boardDoneRetentionDays.value !== null && boardDoneRetentionDays.value > 0) {
    const retentionMs = boardDoneRetentionDays.value * 24 * 60 * 60 * 1000
    const cutoffDate = new Date(Date.now() - retentionMs)
    // 获取所有"已完成"类别的状态 ID
    const doneStatusIds = new Set(
      allColumnConfigs.value
        .filter(c => c.statusCategory === 'done')
        .map(c => c.statusId)
    )
    allIssues = allIssues.filter(issue => {
      // 非已完成状态的工单不过滤
      if (!doneStatusIds.has(issue.statusId)) return true
      // 已完成工单：检查 resolvedAt 或 updatedAt 是否在保留期内
      const resolvedDate = issue.resolvedAt ? new Date(issue.resolvedAt) : (issue.updatedAt ? new Date(issue.updatedAt) : null)
      if (!resolvedDate) return true // 没有日期信息保留
      return resolvedDate >= cutoffDate
    })
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

      // 构建本地 IssueVO 添加到看板
      const issueVO: IssueVO = {
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
        reporterId: newIssue.reporterId,
        version: newIssue.version,
        createdAt: newIssue.createdAt,
        updatedAt: newIssue.updatedAt
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

onMounted(async () => {
  await Promise.all([loadProjects(), loadStatuses()])
  document.addEventListener('keydown', handleKeydown)

  if (selectedProject.value) {
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

.search-wrapper {
  display: flex;
  align-items: center;
  gap: 6px;
}

.search-active-badge {
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

/* ===== 撤销按钮（Notification footer 中） ===== */
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

.card-type-spacer {
  flex: 1;
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
  background: rgba(var(--primary-6), 0.06);
  border: 1px solid rgba(var(--primary-6), 0.2);
  border-radius: 6px;
  margin: 0 16px 8px;
  flex-shrink: 0;
}

.hidden-issues-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.hidden-issues-text {
  font-size: 12px;
  color: var(--color-text-2);
  line-height: 1.4;
}
</style>
