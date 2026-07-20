<template>
  <div class="issue-page">
    <!-- Left query panel (YouTrack style) -->
    <aside class="query-panel" :style="{ width: panelWidth + 'px' }">
      <div class="panel-top">
        <div class="panel-top-title">
          <span class="panel-label">查询</span>
          <span class="panel-total">{{ totalIssues }}</span>
        </div>
        <a-button type="text" size="mini">
          <template #icon><icon-plus /></template>
        </a-button>
      </div>

      <div class="panel-search">
        <a-input v-model="panelSearch" placeholder="搜索查询..." size="small" allow-clear>
          <template #prefix><icon-search /></template>
        </a-input>
      </div>

      <div class="query-group">
        <div class="group-header" @click="toggleGroup('projects')">
          <span class="group-arrow">{{ expandedGroups.has('projects') ? '\u25BE' : '\u25B8' }}</span>
          <span class="group-title">项目</span>
        </div>
        <div v-if="expandedGroups.has('projects')" class="group-items">
          <div
            class="query-item"
            :class="{ active: activeProjectId === null }"
            @click="selectAllProjects"
          >
            <span class="query-name">所有项目</span>
          </div>
          <div
            v-for="p in projectList"
            :key="p.id"
            class="query-item"
            :class="{ active: activeProjectId === p.id }"
            @click="selectProject(p)"
          >
            <span class="query-name">{{ p.name }}</span>
          </div>
        </div>
      </div>

      <div class="query-group">
        <div class="group-header" @click="toggleGroup('saved')">
          <span class="group-arrow">{{ expandedGroups.has('saved') ? '\u25BE' : '\u25B8' }}</span>
          <span class="group-title">已保存的搜索</span>
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
              @click="selectQuery(q)"
            >
              <span class="query-icon" v-if="q.icon">{{ q.icon }}</span>
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
          <div v-if="filteredQueries.length === 0" class="empty-queries">暂无保存的搜索</div>
        </div>
      </div>

      <!-- Create query modal -->
      <a-modal
        v-model:visible="showCreateQueryModal"
        title="保存查询"
        :width="400"
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
          <a-form-item label="筛选条件">
            <div class="query-preview-filters">
              <template v-if="createQueryFiltersPreview.length > 0">
                <div v-for="(f, i) in createQueryFiltersPreview" :key="i" class="preview-chip">
                  {{ f }}
                </div>
              </template>
              <span v-else class="preview-empty">无筛选条件（将返回所有工单）</span>
            </div>
          </a-form-item>
          <a-form-item label="固定到面板顶部">
            <a-switch v-model="createQueryForm.pinned" />
          </a-form-item>
        </a-form>
      </a-modal>

      <!-- Edit query modal -->
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
              :project-list="projectList"
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

      <!-- Rename query modal -->
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
        <a-form layout="vertical">
          <a-form-item label="新名称" required>
            <a-input v-model="renameQueryForm.name" placeholder="输入新名称" :max-length="50" @keyup.enter="handleRenameQuery" />
          </a-form-item>
        </a-form>
      </a-modal>

      <!-- Manage queries (favorites) modal -->
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
            <a-input v-model="manageQuerySearch" placeholder="搜索查询..." size="small" allow-clear>
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
              <span class="manage-query-icon" v-if="q.icon">{{ q.icon }}</span>
              <span class="manage-query-name">{{ q.name }}</span>
              <span class="manage-query-owner" v-if="q.userId && !isOwnQueryById(q.userId)">共享</span>
            </div>
            <div v-if="filteredManageQueries.length === 0" class="manage-queries-empty">
              没有找到匹配的查询
            </div>
          </div>
        </div>
      </a-modal>
    </aside>

    <!-- Resizable divider -->
    <div
      class="panel-resizer"
      title="拖动以调整宽度，双击以展开/折叠"
      @mousedown="startPanelResize"
      @dblclick="togglePanelCollapse"
    ></div>

    <!-- Right issue list area -->
    <section class="issue-list-area">
      <!-- Search/Filter bar (YouTrack style with mode toggle) -->
      <FilterBar
        ref="filterBarRef"
        :project-id="activeProjectId"
        :status-list="statusCache"
        :project-list="projectList"
        :initial-filters="initialFilterChips"
        :active-query-name="activeQueryId ? activeQueryName : null"
        :is-owned-query="activeQueryOwned"
        :readonly-filter-labels="activeQueryReadonlyLabels"
        @search="onGlobalSearch"
        @filter="onGlobalFilter"
        @clear-query="onClearQuery"
        @chip-click="onQueryChipClick"
      />

      <!-- Batch action toolbar (replaces filter bar when selected) -->
      <BatchActionToolbar
        v-if="selectedCount > 0"
        :selected-count="selectedCount"
        :selected-issues="selectedIssues"
        :can-delete="canDeleteIssue()"
        :active-project-id="activeProjectId"
        @deselect-all="clearSelection"
        @batch-state="onBatchState"
        @batch-assign="onBatchAssign"
        @batch-sprint="onBatchSprint"
        @batch-priority="onBatchPriority"
        @batch-tag-add="onBatchTagAdd"
        @batch-tag-remove="onBatchTagRemove"
        @batch-link="onBatchLink"
        @batch-delete="onBatchDelete"
        @batch-export="onBatchExport"
        @open-command="showCommandDialog = true"
      />

      <!-- Filter bar -->
      <div v-else class="filter-bar">
        <div class="filter-left">
          <span class="current-query-name">{{ activeQueryName }}</span>
          <span class="issue-total-badge">{{ totalIssues }} 个问题</span>
          <button
            class="hide-resolved-toggle"
            :class="{ active: hideResolved }"
            :title="hideResolved ? '点击显示已解决工单' : '点击隐藏已解决工单'"
            @click="toggleHideResolved"
          >
            <icon-check-circle />
            <span class="toggle-label">{{ hideResolved ? '已隐藏已解决' : '隐藏已解决' }}</span>
          </button>
        </div>
        <div class="filter-right">
          <a-select v-model="filterProject" placeholder="所有项目" size="small" style="width: 120px" allow-clear @change="onFilterChange">
            <a-option v-for="p in projectList" :key="p.id" :value="p.id">{{ p.key }}</a-option>
          </a-select>
          <a-button v-if="canCreateIssueGlobal" size="small" @click="toggleInlineCreate">
            {{ showInlineCreate ? '取消' : '快速创建' }}
          </a-button>
          <a-button v-if="canCreateIssueGlobal" type="primary" size="small" @click="showCreatePanel = true">创建工单</a-button>
          <a-dropdown trigger="click" position="br" @select="handleExport">
            <a-button size="small" type="text" title="导出数据" :loading="exportLoading">
              <template #icon><icon-download /></template>
            </a-button>
            <template #content>
              <a-doption value="xlsx">
                <template #icon><icon-file /></template>
                导出 XLSX
              </a-doption>
              <a-doption value="csv">
                <template #icon><icon-code /></template>
                导出 CSV
              </a-doption>
            </template>
          </a-dropdown>
          <ColumnConfigPopover
            v-if="isTableLayout"
            :standard-columns="standardColumns"
            :custom-field-columns="customFieldColumns"
            :is-visible="isColumnVisible"
            @toggle="toggleColumn"
            @reset="resetColumns"
          />
          <ViewSettingsMenu
            :layout="layout"
            :density="density"
            :structure="structure"
            @update:layout="setLayout"
            @update:density="setDensity"
            @update:structure="setStructure"
          />
          <a-dropdown trigger="click" position="br">
            <a-button size="small" type="text" title="预览模式">
              <template #icon><icon-eye /></template>
            </a-button>
            <template #content>
              <a-doption @click="setPreviewMode('sidebar')" :class="{ 'doption-active': previewMode === 'sidebar' }">
                <template #icon><icon-layout /></template>
                侧边栏预览
              </a-doption>
              <a-doption @click="setPreviewMode('off')" :class="{ 'doption-active': previewMode === 'off' }">
                <template #icon><icon-expand /></template>
                关闭预览（跳转详情页）
              </a-doption>
            </template>
          </a-dropdown>
        </div>
      </div>

      <!-- Inline quick create -->
      <div v-if="showInlineCreate && selectedCount === 0" class="inline-create">
        <div class="inline-create-row">
          <a-select v-model="quickForm.projectId" placeholder="项目" size="small" style="width: 140px" allow-search>
            <a-option v-for="p in projectList" :key="p.id" :value="p.id">{{ p.key }} - {{ p.name }}</a-option>
          </a-select>
          <a-input
            v-model="quickForm.title"
            placeholder="输入工单标题后按 Enter 快速创建..."
            size="small"
            class="inline-title-input"
            @keyup.enter="quickCreate"
          />
          <a-select v-model="quickForm.issueType" size="small" style="width: 80px">
            <a-option v-for="(label, value) in issueTypeLabelMap" :key="value" :value="value">{{ label }}</a-option>
          </a-select>
          <a-select v-model="quickForm.priority" size="small" style="width: 80px">
            <a-option value="Normal">普通</a-option>
            <a-option value="High">高</a-option>
            <a-option value="Critical">紧急</a-option>
            <a-option value="Low">低</a-option>
          </a-select>
          <a-button type="primary" size="small" :loading="quickCreating" :disabled="!quickForm.projectId || !quickForm.title" @click="quickCreate">
            创建
          </a-button>
        </div>
      </div>

      <!-- Issue table (Table layout mode) -->
      <a-table
        v-if="isTableLayout"
        class="issue-table"
        :data="issues"
        :columns="tableColumns"
        :loading="loading"
        :pagination="false"
        :row-selection="rowSelection"
        :selected-keys="selectedKeysArray"
        :row-class="getRowClass"
        row-key="id"
        :bordered="false"
        :stripe="false"
        column-resizable
        size="medium"
        :scroll="{ x: tableMinWidth }"
        @row-click="onRowClick"
        @row-dblclick="onRowDblClick"
        @selection-change="onSelectionChange"
        @column-resize="onColumnResize"
      >
        <!-- Custom column header (shared slot for all columns) -->
        <template #column-header="{ column }">
          <DraggableColumnHeader
            :column-key="column.dataIndex"
            :label="column.title"
            :sortable="isColumnSortable(column.dataIndex)"
            :sort-dir="getColumnSortDir(column.dataIndex)"
            :fixed="isColumnFixed(column.dataIndex)"
            @sort="onHeaderSort"
            @remove="onHeaderRemove"
            @drag-drop="onHeaderDragDrop"
          />
        </template>

        <!-- Cell slots -->
        <template #issueKey="{ record }">
          <span class="issue-key">{{ record.issueKey }}</span>
        </template>
        <template #title-cell="{ record }">
          <span class="issue-title-text">{{ record.title }}</span>
        </template>
        <template #assignee="{ record }">
          <div @click.stop>
            <a-trigger v-if="canEditIssue(record)" v-model:popup-visible="assigneeDropdowns[record.id]" trigger="click" position="bl" :popup-offset="4">
              <span class="editable-cell" @click="openAssigneeEdit(record)">
                {{ record.assigneeName || '\u2014' }}
                <icon-loading v-if="isCellEditing(record.id, 'assigneeId')" class="cell-spinner" />
              </span>
              <template #content>
                <div class="inline-dropdown member-dropdown">
                  <div class="dropdown-search">
                    <a-input v-model="assigneeSearch" placeholder="搜索成员..." size="mini" allow-clear @keydown.stop><template #prefix><icon-search /></template></a-input>
                  </div>
                  <div v-if="assigneeOptionsLoading" class="dropdown-loading"><a-spin :size="16" /></div>
                  <template v-else>
                    <div class="dropdown-item" @click="selectAssignee(record, null)"><span class="unassigned-icon">&mdash;</span><span>未分配</span></div>
                    <div v-for="m in filteredAssigneeOptions" :key="m.userId" class="dropdown-item" @click="selectAssignee(record, m)">
                      <span class="member-avatar">{{ m.displayName?.charAt(0) }}</span><span>{{ m.displayName }}</span>
                    </div>
                  </template>
                </div>
              </template>
            </a-trigger>
            <span v-else class="readonly-cell">{{ record.assigneeName || '\u2014' }}</span>
          </div>
        </template>
        <template #status="{ record }">
          <div @click.stop>
            <a-trigger v-if="canEditIssue(record)" v-model:popup-visible="statusDropdowns[record.id]" trigger="click" position="bl" :popup-offset="4">
              <span class="editable-cell status-badge" :style="{ background: getStatusColor(record.statusId, record.statusColor) }" @click="openStatusEdit(record)">
                {{ getStatusName(record.statusId, record.statusName) }}
                <icon-loading v-if="isCellEditing(record.id, 'statusId')" class="cell-spinner" />
              </span>
              <template #content>
                <div class="inline-dropdown">
                  <div v-if="transitionsLoading[record.id]" class="dropdown-loading"><a-spin :size="16" /></div>
                  <template v-else>
                    <div v-for="st in availableTransitions[record.id]" :key="st.id" class="dropdown-item" @click="selectStatus(record, st)">
                      <span class="status-dot" :style="{ background: st.color }"></span><span>{{ localizeStatusName(st.name) }}</span>
                    </div>
                    <div v-if="(availableTransitions[record.id] || []).length === 0" class="dropdown-empty">无可用转换</div>
                  </template>
                </div>
              </template>
            </a-trigger>
            <span v-else class="readonly-cell status-badge" :style="{ background: getStatusColor(record.statusId, record.statusColor) }">{{ getStatusName(record.statusId, record.statusName) }}</span>
          </div>
        </template>
        <template #sprint="{ record }">
          <div @click.stop>
            <a-trigger v-if="canEditIssue(record)" v-model:popup-visible="sprintDropdowns[record.id]" trigger="click" position="bl" :popup-offset="4">
              <span class="editable-cell" @click="openSprintEdit(record)">
                {{ getSprintName(record.sprintId, record.sprintName) || '\u2014' }}
                <icon-loading v-if="isCellEditing(record.id, 'sprintId')" class="cell-spinner" />
              </span>
              <template #content>
                <div class="inline-dropdown">
                  <div v-if="sprintOptionsLoading[record.id]" class="dropdown-loading"><a-spin :size="16" /></div>
                  <template v-else>
                    <div class="dropdown-item" @click="selectSprint(record, null)"><span>无 Sprint</span></div>
                    <template v-for="group in getSprintGroups(record.projectId)" :key="group.label">
                      <div class="dropdown-group-label">{{ group.label }}</div>
                      <div v-for="s in group.items" :key="s.id" class="dropdown-item" @click="selectSprint(record, s)"><span>{{ s.name }}</span></div>
                    </template>
                  </template>
                </div>
              </template>
            </a-trigger>
            <span v-else class="readonly-cell">{{ getSprintName(record.sprintId, record.sprintName) || '\u2014' }}</span>
          </div>
        </template>
        <template #priority="{ record }">
          <div @click.stop>
            <a-trigger v-if="canEditIssue(record)" v-model:popup-visible="priorityDropdowns[record.id]" trigger="click" position="bl" :popup-offset="4">
              <span class="editable-cell" @click="priorityDropdowns[record.id] = true">
                <span class="priority-dot" :class="'priority-' + (record.priority || 'normal').toLowerCase()"></span>
                {{ localizePriority(record.priority) }}
                <icon-loading v-if="isCellEditing(record.id, 'priority')" class="cell-spinner" />
              </span>
              <template #content>
                <div class="inline-dropdown">
                  <div v-for="p in priorityOptions" :key="p.value" class="dropdown-item" @click="selectPriority(record, p.value)">
                    <span class="priority-dot" :class="'priority-' + p.value.toLowerCase()"></span><span>{{ p.label }}</span>
                  </div>
                </div>
              </template>
            </a-trigger>
            <span v-else class="readonly-cell">
              <span class="priority-dot" :class="'priority-' + (record.priority || 'normal').toLowerCase()"></span>
              {{ localizePriority(record.priority) }}
            </span>
          </div>
        </template>
        <template #updatedAt="{ record }"><span class="time-ago">{{ formatTime(record.updatedAt) }}</span></template>
        <template #issueType="{ record }"><span class="type-label">{{ localizeIssueType(record.issueType) }}</span></template>
        <template #reporter="{ record }"><span class="reporter-name">{{ record.reporterName || '\u2014' }}</span></template>
        <template #createdAt="{ record }"><span class="time-ago">{{ formatTime(record.createdAt) }}</span></template>
        <template #dueDate="{ record }">
          <a-tooltip v-if="record.dueDate && getDueDateStatus(record) !== 'normal'" :content="getDueDateTooltip(record)" position="top" mini>
            <span class="due-date-cell" :class="'due-' + getDueDateStatus(record)">{{ record.dueDate }}</span>
          </a-tooltip>
          <span v-else class="time-ago">{{ record.dueDate || '\u2014' }}</span>
        </template>
        <template #childProgress="{ record }">
          <span v-if="record.childCount > 0" class="child-progress-cell" :title="`${record.childClosedCount}/${record.childCount} 子任务已完成`">
            <span class="child-progress-bar">
              <span class="child-progress-fill" :style="{ width: Math.round(record.childClosedCount / record.childCount * 100) + '%' }"></span>
            </span>
            <span class="child-progress-text">{{ record.childClosedCount }}/{{ record.childCount }}</span>
          </span>
          <span v-else class="time-ago">&mdash;</span>
        </template>

        <!-- Custom field columns (cf_ prefix) -->
        <template #customFieldCell="{ record, column }">
          <span
            v-if="record.customFieldColors?.[column.dataIndex]"
            class="cf-cell cf-badge"
            :style="{ background: record.customFieldColors[column.dataIndex], color: '#fff' }"
          >{{ record.customFieldValues?.[column.dataIndex] || '\u2014' }}</span>
          <span v-else class="cf-cell">{{ record.customFieldValues?.[column.dataIndex] || '\u2014' }}</span>
        </template>

        <!-- empty -->
        <template #empty>
          <div class="empty-state">
            <icon-search class="empty-icon" />
            <p class="empty-title">暂无工单</p>
            <p class="empty-desc">尝试调整筛选条件或创建新的工单</p>
            <a-button v-if="canCreateIssueGlobal" type="primary" size="small" @click="toggleInlineCreate">创建工单</a-button>
          </div>
        </template>
      </a-table>

      <!-- Issue list layout (List layout mode) -->
      <IssueListLayout
        v-if="isListLayout"
        :issues="issues"
        :density="density"
        :structure="structure"
        :loading="loading"
        :sort-state="sortState"
        :active-issue-id="previewIssueId"
        :selected-ids="selectedIds"
        :show-checkbox="canBatchOps"
        @item-click="onListItemClick"
        @item-dblclick="onListItemDblClick"
        @sort-change="onListSortChange"
        @select="onListItemSelect"
      />

      <!-- Pagination -->
      <div class="pagination-bar" v-if="totalIssues > 0">
        <a-pagination v-model:current="currentPage" :total="totalIssues" :page-size="pageSize" size="small" show-total @change="goPage" />
      </div>
    </section>

    <!-- Create issue panel -->
    <IssueCreatePanel v-model:visible="showCreatePanel" :project-id="activeProjectId || undefined" @created="refreshList" />

    <!-- Sidebar preview drawer -->
    <IssuePreviewDrawer
      :visible="previewVisible"
      :issue-id="previewIssueId"
      @update:visible="onPreviewVisibleChange"
      @go-detail="onPreviewGoDetail"
    />

    <!-- Apply Command Dialog (Ctrl+Alt+J) -->
    <ApplyCommandDialog
      :visible="showCommandDialog"
      :selected-count="selectedCount"
      :selected-issues="selectedIssues"
      @update:visible="showCommandDialog = $event"
      @executed="onCommandExecuted"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { IconPlus, IconSearch, IconLoading, IconEdit, IconPenFill, IconShareExternal, IconPushpin, IconDelete, IconLock, IconCheckCircle, IconEye, IconLayout, IconExpand, IconDownload, IconFile, IconCode } from '@arco-design/web-vue/es/icon'
import { Message, Modal } from '@arco-design/web-vue'
import { projectApi, issueApi, queryApi, sprintApi } from '@/api'
import type { IssueVO, IssueStatusVO, ProjectMemberVO, SprintVO } from '@/api/types'
import type { TableData } from '@arco-design/web-vue'
import { useAuthStore } from '@/stores/auth'
import { localizeStatusName, localizeIssueType, localizePriority, issueTypeLabelMap, priorityLabelMap, priorityReverseLabelMap, queryFieldKeyToLabel, queryFieldLabelToKey } from '@/utils/fieldLabels'
import { useIssueList, useSelection, useInlineEdit, useBatchOps, usePermission, useColumnConfig, useViewSettings } from './composables'
import BatchActionToolbar from './components/BatchActionToolbar.vue'
import DraggableColumnHeader from './components/DraggableColumnHeader.vue'
import IssueCreatePanel from './IssueCreatePanel.vue'
import IssuePreviewDrawer from '../board/IssuePreviewDrawer.vue'
import ColumnConfigPopover from './components/ColumnConfigPopover.vue'
import FilterBar from './components/FilterBar.vue'
import QueryInput from './components/QueryInput.vue'
import ApplyCommandDialog from './components/ApplyCommandDialog.vue'
import ViewSettingsMenu from './components/ViewSettingsMenu.vue'
import IssueListLayout from './components/IssueListLayout.vue'

const router = useRouter()
const route = useRoute()

// Composables
const {
  issues, totalIssues, currentPage, pageSize, loading,
  sortState, loadIssues, goPage, updateLocalIssue
} = useIssueList()

const {
  selectedIds, selectedCount, selectedIssues,
  clearSelection
} = useSelection(issues)

const { isCellEditing, executeEdit } = useInlineEdit(issues)
const { batchTransitStatus, batchAssign, batchUpdateSprint, batchUpdatePriority, batchTagAdd, batchTagRemove, batchAddLink, batchDelete } = useBatchOps()
const { loadPermissions, canEditIssue, canDeleteIssue } = usePermission(issues)

// View settings (layout/density/structure)
const {
  layout, density, structure,
  isTreeMode, isListLayout, isTableLayout,
  setLayout, setDensity, setStructure
} = useViewSettings()

// 全局级创建权限：统一使用 authStore.canCreateIssue
const authStore = useAuthStore()
const canCreateIssueGlobal = authStore.canCreateIssue

// 全局级批量操作权限：控制 checkbox 列和批量工具栏是否显示
const canBatchOps = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  if (authStore.permissionsLoaded) {
    return authStore.hasGlobalPermission('nav:batch_ops')
  }
  // 权限未加载时乐观显示
  return true
})

// 全局级 Sprint 查看权限：控制是否预加载 Sprint 数据
const canViewSprintGlobal = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  if (authStore.permissionsLoaded) {
    return authStore.hasGlobalPermission('nav:sprint_view') || authStore.hasGlobalPermission('nav:sprint_manage')
  }
  return true
})

// Panel state (declared before useColumnConfig so it can be passed as ref)
const activeProjectId = ref<string | null>(null)
const filterBarRef = ref<InstanceType<typeof FilterBar> | null>(null)

const {
  visibleColumns, toggleColumn, reorderColumn,
  standardColumns, customFieldColumns, isVisible: isColumnVisible, resetToDefault: resetColumns
} = useColumnConfig(activeProjectId as any)

// Panel state
const savedQueries = ref<any[]>([])
const projectList = ref<any[]>([])
const activeQueryId = ref<string | null>(null)
const activeQueryName = ref('\u6240\u6709\u5de5\u5355') // "所有工单"
const activeQueryObj = ref<any>(null) // Track full active query object for chip-click
const expandedGroups = reactive(new Set<string>(['saved', 'projects']))
const panelSearch = ref('')

// Computed: whether the active query belongs to the current user (for edit permission)
const activeQueryOwned = computed(() => {
  if (!activeQueryObj.value) return false
  return isOwnQuery(activeQueryObj.value)
})

// Computed: readonly filter labels for non-owned queries (shown in FilterBar chip tooltip/expansion)
const activeQueryReadonlyLabels = computed<string[]>(() => {
  if (!activeQueryObj.value?.filters) return []
  let filters: any[]
  if (typeof activeQueryObj.value.filters === 'string') {
    try { filters = JSON.parse(activeQueryObj.value.filters) } catch { return [] }
  } else {
    filters = activeQueryObj.value.filters
  }
  if (!Array.isArray(filters)) return []

  const operatorLabels: Record<string, string> = {
    eq: '=', neq: '≠', in: '∈', not_in: '∉', contains: '包含', open: '未关闭'
  }

  return filters.map((f: any) => {
    const fieldLabel = queryFieldKeyToLabel[f.field] || f.field
    const op = f.operator

    // Handle special "open" operator (means all non-closed statuses)
    if (op === 'open') return `${fieldLabel}: 未关闭`

    let values: string
    if (Array.isArray(f.value)) {
      values = f.value.map((v: string) => {
        if (v === '${currentUser}') return '我'
        if (f.field === 'type') return issueTypeLabelMap[v] || v
        if (f.field === 'priority') return priorityLabelMap[v] || v
        if (f.field === 'status') {
          const st = statusCache.value.find(s => s.code === v || s.id === v)
          return st ? localizeStatusName(st.name) : v
        }
        if (f.field === 'project') {
          const p = projectList.value.find(pr => pr.id === v)
          return p ? p.name : v
        }
        return v
      }).join(', ')
    } else {
      values = String(f.value || '')
    }

    const opLabel = (op && op !== 'eq') ? ` ${operatorLabels[op] || op}` : ':'
    return `${fieldLabel}${opLabel} ${values}`
  }).filter(l => l && l.trim())
})

// Create query modal state
const showCreateQueryModal = ref(false)
const createQueryLoading = ref(false)
const queryIconOptions = ['🧪', '🐛', '🚀', '⚡', '📋', '🎯', '🔥', '💡', '⭐', '🏷️', '📌', '🔍', '✅', '⏳', '🎨', '🛡️']
const createQueryForm = reactive({
  name: '',
  pinned: true,
  icon: ''
})

// Computed: preview of current filters for the create modal
const createQueryFiltersPreview = computed(() => {
  const previews: string[] = []
  if (activeQueryId.value) {
    const q = savedQueries.value.find(sq => sq.id === activeQueryId.value)
    if (q) previews.push(`基于查询: ${q.name}`)
  }
  if (activeProjectId.value) {
    const p = projectList.value.find(pr => pr.id === activeProjectId.value)
    if (p) previews.push(`项目: ${p.name}`)
  }
  if (searchKeyword.value) {
    previews.push(`关键字: ${searchKeyword.value}`)
  }
  if (globalFilterParams.value.statusId) {
    previews.push(`状态: ${globalFilterParams.value.statusId}`)
  }
  if (globalFilterParams.value.assigneeId) {
    previews.push(`负责人: ${globalFilterParams.value.assigneeId}`)
  }
  if (globalFilterParams.value.priority) {
    previews.push(`优先级: ${globalFilterParams.value.priority}`)
  }
  if (globalFilterParams.value.sprintId) {
    previews.push(`Sprint: ${globalFilterParams.value.sprintId}`)
  }
  return previews
})

function openCreateQueryModal() {
  createQueryForm.name = ''
  createQueryForm.pinned = true
  createQueryForm.icon = ''
  showCreateQueryModal.value = true
}

async function handleCreateQuery() {
  if (!createQueryForm.name.trim()) {
    Message.warning('请输入查询名称')
    return
  }
  createQueryLoading.value = true
  try {
    const filters = buildCurrentFilters()

    await queryApi.create({
      name: createQueryForm.name.trim(),
      filters: filters,
      pinned: createQueryForm.pinned,
      shared: false,
      icon: createQueryForm.icon || undefined
    })
    Message.success('查询已保存')
    showCreateQueryModal.value = false
    loadPanel() // Refresh panel to show new query
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存查询失败')
  } finally {
    createQueryLoading.value = false
  }
}

async function confirmDeleteQuery(q: any) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除查询「${q.name}」吗？此操作不可撤销。`,
    okText: '删除',
    cancelText: '取消',
    okButtonProps: { status: 'danger' },
    async onOk() {
      try {
        await queryApi.delete(q.id)
        Message.success('查询已删除')
        if (activeQueryId.value === q.id) {
          activeQueryId.value = null
          activeQueryName.value = '所有工单'
          activeQueryObj.value = null
          refreshList()
        }
        loadPanel()
      } catch (e: any) {
        Message.error(e.response?.data?.message || '删除失败')
      }
    }
  })
}

// ========== Edit query ==========
const showEditQueryModal = ref(false)
const editQueryLoading = ref(false)
const editQueryForm = reactive({
  id: '',
  name: '',
  icon: '',
  pinned: false,
  shared: false,
  filters: [] as any[],
  queryText: ''
})

// (editQueryFiltersPreview removed — replaced by editable queryText input)

// ===== Query text ↔ Filters JSON conversion (YouTrack-style editable query) =====

// (Field label mappings imported from @/utils/fieldLabels: queryFieldKeyToLabel, queryFieldLabelToKey)

/**
 * Convert filter JSON array to human-readable query text.
 * e.g. [{"field":"project","operator":"eq","value":["id"]}] → "项目: DE4"
 */
function filtersToQueryText(filters: any[]): string {
  if (!filters || filters.length === 0) return ''
  const parts: string[] = []
  for (const f of filters) {
    // Resolve field label: built-in fields use queryFieldKeyToLabel, custom fields use their name
    let fieldLabel: string
    if (f.field.startsWith('cf.') || f.field.startsWith('customField.')) {
      // Custom field: the field key is "cf.{id}", display as field name stored in filter
      // If filter has a displayName property, use it; otherwise fallback to key
      fieldLabel = f.displayName || f.field
    } else {
      fieldLabel = queryFieldKeyToLabel[f.field] || f.field
    }
    const op = f.operator

    // Special operators
    if (op === 'open') { parts.push(`${fieldLabel}: 未关闭`); continue }
    if (op === 'closed') { parts.push(`${fieldLabel}: 已关闭`); continue }
    if (op === 'is_empty') { parts.push(`${fieldLabel}: 无`); continue }
    if (op === 'is_not_empty') { parts.push(`${fieldLabel}: 有`); continue }

    let values: string
    if (Array.isArray(f.value)) {
      values = f.value.map((v: string) => {
        if (v === '${currentUser}') return '我'
        if (f.field === 'type') return issueTypeLabelMap[v] || v
        if (f.field === 'priority') return priorityLabelMap[v] || v
        if (f.field === 'status') {
          const st = statusCache.value.find(s => s.code === v || s.id === v)
          return st ? localizeStatusName(st.name) : v
        }
        if (f.field === 'project') {
          const p = projectList.value.find(pr => pr.id === v)
          return p ? (p.key || p.name) : v
        }
        return v
      }).join(', ')
    } else {
      values = String(f.value || '')
    }

    // Operator display
    let prefix = ''
    if (op === 'neq' || op === 'not_in') prefix = '-'

    // Between renders as "field: val1 .. val2"
    if (op === 'between' && Array.isArray(f.value) && f.value.length >= 2) {
      parts.push(`${fieldLabel}: ${f.value[0]} .. ${f.value[1]}`)
    } else {
      parts.push(`${fieldLabel}: ${prefix}${values}`)
    }
  }
  return parts.join('  ')
}

/**
 * Parse human-readable query text back into filter JSON array.
 * Supports YouTrack-style syntax:
 * - "字段: 值" — basic filter
 * - "字段: 值1, 值2" — multi-value (OR, operator: in)
 * - "字段: -值" — exclude (operator: neq/not_in)
 * - "字段: 值1 .. 值2" — range (operator: between)
 * - "字段: 无" — is_empty
 * - "字段: 有" — is_not_empty
 * - "字段: 未关闭" — status open
 * - "字段: 已关闭" — status closed
 */
function queryTextToFilters(text: string): any[] {
  if (!text || !text.trim()) return []
  const filters: any[] = []

  // Normalize Chinese colon
  const normalized = text.replace(/：/g, ':')

  // Strategy: find all known field labels followed by ":" in the text
  // Then extract value between current field's ":" and next field's start
  const allFields = [...Object.keys(queryFieldLabelToKey)]
  // Sort by length descending so longer names match first (e.g. "创建日期" before "日期")
  allFields.sort((a, b) => b.length - a.length)

  interface FieldMatch { field: string; valueStart: number; matchStart: number }
  const matches: FieldMatch[] = []

  for (const fieldLabel of allFields) {
    // Find all occurrences of "fieldLabel:" in the text
    let searchFrom = 0
    while (true) {
      const idx = normalized.indexOf(`${fieldLabel}:`, searchFrom)
      if (idx < 0) break
      // Make sure it's at start or after whitespace (not middle of another word)
      const charBefore = idx > 0 ? normalized[idx - 1] : ' '
      if (idx === 0 || charBefore === ' ') {
        const valueStart = idx + fieldLabel.length + 1 // after ":"
        // Skip optional space after colon
        const afterColon = normalized.substring(valueStart)
        const spaceMatch = afterColon.match(/^\s*/)
        const actualValueStart = valueStart + (spaceMatch ? spaceMatch[0].length : 0)
        matches.push({ field: fieldLabel, valueStart: actualValueStart, matchStart: idx })
      }
      searchFrom = idx + 1
    }
  }

  // Sort by position in text
  matches.sort((a, b) => a.matchStart - b.matchStart)

  // Extract value for each field (from valueStart to next field's matchStart)
  for (let i = 0; i < matches.length; i++) {
    const { field: fieldLabel, valueStart } = matches[i]
    const valueEnd = i + 1 < matches.length ? matches[i + 1].matchStart : normalized.length
    let valuePart = normalized.substring(valueStart, valueEnd).trim()

    const fieldKey = queryFieldLabelToKey[fieldLabel] || fieldLabel

    // Handle special keywords
    if (valuePart === '未关闭') { filters.push({ field: fieldKey, operator: 'open', value: [] }); continue }
    if (valuePart === '已关闭') { filters.push({ field: fieldKey, operator: 'closed', value: [] }); continue }
    if (valuePart === '无') { filters.push({ field: fieldKey, operator: 'is_empty', value: [] }); continue }
    if (valuePart === '有') { filters.push({ field: fieldKey, operator: 'is_not_empty', value: [] }); continue }

    // Check for range operator ".."
    if (valuePart.includes('..')) {
      const rangeParts = valuePart.split('..').map(p => p.trim())
      if (rangeParts.length === 2 && rangeParts[0] && rangeParts[1]) {
        filters.push({ field: fieldKey, operator: 'between', value: rangeParts })
        continue
      }
    }

    // Check for exclude prefix "-"
    const isNegative = valuePart.startsWith('-')
    if (isNegative) valuePart = valuePart.substring(1).trim()

    // Split by comma for multi-value
    const values = valuePart.split(/[,，]/).map(v => v.trim()).filter(v => v)
    if (values.length === 0) continue

    const resolvedValues = values.map(v => resolveValueToId(fieldKey, v))

    const operator = isNegative
      ? (resolvedValues.length > 1 ? 'not_in' : 'neq')
      : (resolvedValues.length > 1 ? 'in' : 'eq')

    filters.push({ field: fieldKey, operator, value: resolvedValues })
  }
  return filters
}

/** Resolve a human-readable value back to its internal ID/key */
function resolveValueToId(fieldKey: string, v: string): string {
  if (v === '我') return '${currentUser}'
  if (fieldKey === 'project') {
    const p = projectList.value.find(pr => pr.key === v || pr.name === v)
    return p ? p.id : v
  }
  if (fieldKey === 'priority') return priorityReverseLabelMap[v] || v
  if (fieldKey === 'type') {
    const entry = Object.entries(issueTypeLabelMap).find(([, label]) => label === v)
    return entry ? entry[0] : v
  }
  if (fieldKey === 'status') {
    const st = statusCache.value.find(s => localizeStatusName(s.name) === v || s.name === v)
    return st ? st.id : v
  }
  return v
}

function openEditQueryModal(q: any) {
  editQueryForm.id = q.id
  editQueryForm.name = q.name || ''
  editQueryForm.icon = q.icon || ''
  editQueryForm.pinned = q.pinned || false
  editQueryForm.shared = q.shared || false
  // Parse filters and convert to human-readable query text
  try {
    editQueryForm.filters = q.filters ? JSON.parse(q.filters) : []
  } catch {
    editQueryForm.filters = []
  }
  editQueryForm.queryText = filtersToQueryText(editQueryForm.filters)
  showEditQueryModal.value = true
}

async function handleEditQuery() {
  if (!editQueryForm.name.trim()) {
    Message.warning('请输入查询名称')
    return
  }
  editQueryLoading.value = true
  try {
    // Parse query text back to filters JSON
    const newFilters = queryTextToFilters(editQueryForm.queryText)
    const originalQueryText = filtersToQueryText(editQueryForm.filters)
    const filtersChanged = editQueryForm.queryText.trim() !== originalQueryText.trim()

    const updateData: Record<string, any> = {
      name: editQueryForm.name.trim(),
      icon: editQueryForm.icon || '',
      pinned: editQueryForm.pinned,
      shared: editQueryForm.shared
    }
    // Always send the parsed filters (user may have edited the query text)
    if (filtersChanged) {
      updateData.filters = newFilters
    }
    await queryApi.update(editQueryForm.id, updateData)
    Message.success('查询已更新')
    showEditQueryModal.value = false

    // If this was the active query, update local state
    if (activeQueryId.value === editQueryForm.id) {
      activeQueryName.value = editQueryForm.name.trim()
      // Update activeQueryObj to reflect changes
      if (activeQueryObj.value) {
        activeQueryObj.value = {
          ...activeQueryObj.value,
          name: editQueryForm.name.trim(),
          icon: editQueryForm.icon || '',
          pinned: editQueryForm.pinned,
          shared: editQueryForm.shared,
          ...(filtersChanged ? { filters: JSON.stringify(newFilters) } : {})
        }
      }
      // If filters were changed, refresh the list
      if (filtersChanged) {
        refreshList()
      }
    }
    loadPanel()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '更新失败')
  } finally {
    editQueryLoading.value = false
  }
}

// ========== Rename query ==========
const showRenameQueryModal = ref(false)
const renameQueryLoading = ref(false)
const renameQueryForm = reactive({
  id: '',
  name: ''
})

function openRenameQueryModal(q: any) {
  renameQueryForm.id = q.id
  renameQueryForm.name = q.name || ''
  showRenameQueryModal.value = true
}

async function handleRenameQuery() {
  if (!renameQueryForm.name.trim()) {
    Message.warning('请输入查询名称')
    return
  }
  renameQueryLoading.value = true
  try {
    await queryApi.update(renameQueryForm.id, { name: renameQueryForm.name.trim() })
    Message.success('重命名成功')
    showRenameQueryModal.value = false
    // Update local activeQueryName if this is the currently selected query
    if (activeQueryId.value === renameQueryForm.id) {
      activeQueryName.value = renameQueryForm.name.trim()
    }
    loadPanel()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '重命名失败')
  } finally {
    renameQueryLoading.value = false
  }
}

// ========== Toggle shared/pinned ==========
async function toggleQueryShared(q: any) {
  try {
    await queryApi.update(q.id, { shared: !q.shared })
    Message.success(q.shared ? '已设为私有' : '已设为共享')
    loadPanel()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  }
}

async function toggleQueryPinned(q: any) {
  try {
    await queryApi.update(q.id, { pinned: !q.pinned })
    Message.success(q.pinned ? '已取消置顶' : '已置顶')
    loadPanel()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  }
}

// ========== Manage query favorites ==========
const showManageQueriesModal = ref(false)
const manageQuerySearch = ref('')
const availableQueries = ref<any[]>([])

/** Check if a query belongs to the current user AND is editable (not shared/system) */
function isOwnQuery(q: any): boolean {
  if (!q) return false
  const currentUserId = authStore.user?.userId || authStore.user?.id || ''
  // Must be created by current user AND not shared (shared queries are system/public, not editable)
  return q.userId === String(currentUserId) && !q.shared
}
function isOwnQueryById(userId: string): boolean {
  const currentUserId = authStore.user?.userId || authStore.user?.id || ''
  return userId === String(currentUserId)
}

const filteredManageQueries = computed(() => {
  if (!manageQuerySearch.value) return availableQueries.value
  const kw = manageQuerySearch.value.toLowerCase()
  return availableQueries.value.filter((q: any) => q.name.toLowerCase().includes(kw))
})

async function openManageQueriesModal() {
  showManageQueriesModal.value = true
  manageQuerySearch.value = ''
  try {
    const res = await queryApi.getAvailableQueries(activeProjectId.value || undefined)
    availableQueries.value = res.data || []
  } catch (e: any) {
    Message.error('加载可用查询失败')
    availableQueries.value = []
  }
}

async function toggleFavorite(q: any) {
  try {
    if (q.favorited) {
      await queryApi.removeFavorite(q.id)
      q.favorited = false
      Message.success(`已从面板移除「${q.name}」`)
    } else {
      await queryApi.addFavorite(q.id)
      q.favorited = true
      Message.success(`已添加「${q.name}」到面板`)
    }
    loadPanel()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  }
}

async function handleRemoveFavorite(q: any) {
  try {
    await queryApi.removeFavorite(q.id)
    Message.success(`已从面板移除「${q.name}」`)
    loadPanel()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  }
}

// Context menu trigger (for the ⋯ button — dispatches a synthetic contextmenu event
// so the a-dropdown with trigger="contextMenu" picks it up)
function triggerContextMenu(event: MouseEvent, _q: any) {
  const target = (event.target as HTMLElement).closest('.query-item')
  if (target) {
    const contextMenuEvent = new MouseEvent('contextmenu', {
      bubbles: true,
      cancelable: true,
      clientX: event.clientX,
      clientY: event.clientY
    })
    target.dispatchEvent(contextMenuEvent)
  }
}

// Helper: build filters from current active state (reused from handleCreateQuery)
function buildCurrentFilters(): any[] {
  const filters: any[] = []
  if (globalFilterParams.value.statusId) {
    const statusIds = String(globalFilterParams.value.statusId).split(',')
    const statusCodes = statusIds.map(id => {
      const s = statusCache.value.find(st => st.id === id)
      return s?.code || id
    })
    filters.push({ field: 'status', operator: 'in', value: statusCodes })
  }
  if (globalFilterParams.value.assigneeId) {
    filters.push({ field: 'assignee', operator: 'eq', value: [globalFilterParams.value.assigneeId] })
  }
  if (globalFilterParams.value.priority) {
    filters.push({ field: 'priority', operator: 'eq', value: [globalFilterParams.value.priority] })
  }
  if (globalFilterParams.value.sprintId) {
    filters.push({ field: 'sprint', operator: 'eq', value: [globalFilterParams.value.sprintId] })
  }
  if (globalFilterParams.value.issueType) {
    filters.push({ field: 'type', operator: 'eq', value: [globalFilterParams.value.issueType] })
  }
  if (searchKeyword.value.trim()) {
    filters.push({ field: 'keyword', operator: 'contains', value: [searchKeyword.value.trim()] })
  }
  if (activeProjectId.value) {
    filters.push({ field: 'project', operator: 'eq', value: [activeProjectId.value] })
  }
  return filters
}

// Panel resize
const PANEL_WIDTH_KEY = 'trackflow:panel-width'
const panelWidth = ref(loadPanelWidth())

function loadPanelWidth(): number {
  try {
    const stored = localStorage.getItem(PANEL_WIDTH_KEY)
    if (stored) return Math.max(200, Math.min(500, Number(stored)))
  } catch { /* ignore */ }
  return 280
}

function startPanelResize(e: MouseEvent) {
  e.preventDefault()
  const startX = e.clientX
  const startWidth = panelWidth.value

  function onMove(ev: MouseEvent) {
    const delta = ev.clientX - startX
    panelWidth.value = Math.max(200, Math.min(500, startWidth + delta))
  }
  function onUp() {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    localStorage.setItem(PANEL_WIDTH_KEY, String(panelWidth.value))
  }
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

const panelWidthBeforeCollapse = ref(280)

function togglePanelCollapse() {
  if (panelWidth.value <= 200) {
    // 当前已是最小宽度，恢复到之前记录的宽度
    panelWidth.value = panelWidthBeforeCollapse.value
  } else {
    // 记录当前宽度，然后缩到最小
    panelWidthBeforeCollapse.value = panelWidth.value
    panelWidth.value = 200
  }
  localStorage.setItem(PANEL_WIDTH_KEY, String(panelWidth.value))
}

// Filters
const filterProject = ref<string | undefined>(undefined)
const searchKeyword = ref('')
const globalFilterParams = ref<Record<string, any>>({})
const initialFilterChips = ref<any[]>([])

// Hide resolved toggle (persisted in localStorage)
const HIDE_RESOLVED_KEY = 'trackflow:hide-resolved'
const hideResolved = ref(loadHideResolved())

function loadHideResolved(): boolean {
  try {
    return localStorage.getItem(HIDE_RESOLVED_KEY) === 'true'
  } catch { return false }
}

function toggleHideResolved() {
  hideResolved.value = !hideResolved.value
  localStorage.setItem(HIDE_RESOLVED_KEY, String(hideResolved.value))
  currentPage.value = 1
  refreshList()
}

function onGlobalSearch(keyword: string) {
  searchKeyword.value = keyword
  globalFilterParams.value = {}
  // If user types in search while a saved query is active, keep the query active for combined filtering
  currentPage.value = 1
  refreshList()
}

function onGlobalFilter(filters: Record<string, any>) {
  searchKeyword.value = ''
  globalFilterParams.value = filters
  // If user modifies filters while a saved query is active, switch to ad-hoc mode
  if (activeQueryId.value) {
    activeQueryId.value = null
    activeQueryName.value = '所有工单'
    activeQueryObj.value = null
  }
  currentPage.value = 1
  refreshList()
}

function onClearQuery() {
  activeQueryId.value = null
  activeQueryName.value = '所有工单'
  activeQueryObj.value = null
  searchKeyword.value = ''
  globalFilterParams.value = {}
  currentPage.value = 1
  // Remove query param from URL
  const { project, ...rest } = route.query
  router.replace({ query: project ? { project } : {} })
  // Clear FilterBar chips
  filterBarRef.value?.clearAll()
  refreshList()
}

function onQueryChipClick() {
  // If the user owns the query, open the edit modal for it
  if (activeQueryObj.value && isOwnQuery(activeQueryObj.value)) {
    openEditQueryModal(activeQueryObj.value)
  }
}

// Quick create
const showInlineCreate = ref(false)
const showCreatePanel = ref(false)
const quickCreating = ref(false)
const quickForm = reactive({
  projectId: undefined as string | undefined,
  title: '',
  issueType: 'Task',
  priority: 'Normal'
})

// Apply Command dialog
const showCommandDialog = ref(false)

// ===== Preview mode (YouTrack-style Sidebar / Off) =====
const PREVIEW_MODE_KEY = 'trackflow:preview-mode'
type PreviewMode = 'sidebar' | 'off'
const previewMode = ref<PreviewMode>(loadPreviewMode())
const previewVisible = ref(false)
const previewIssueId = ref<string | null>(null)
const activeIssueIndex = ref<number>(-1)

function loadPreviewMode(): PreviewMode {
  try {
    const stored = localStorage.getItem(PREVIEW_MODE_KEY)
    if (stored === 'sidebar' || stored === 'off') return stored
  } catch { /* ignore */ }
  return 'off'
}

function setPreviewMode(mode: PreviewMode) {
  previewMode.value = mode
  localStorage.setItem(PREVIEW_MODE_KEY, mode)
  if (mode === 'off') {
    previewVisible.value = false
    previewIssueId.value = null
    activeIssueIndex.value = -1
  }
}

function openPreview(issue: IssueVO, index: number) {
  previewIssueId.value = issue.id
  previewVisible.value = true
  activeIssueIndex.value = index
}

function closePreview() {
  previewVisible.value = false
  activeIssueIndex.value = -1
}

function onPreviewVisibleChange(val: boolean) {
  previewVisible.value = val
  if (!val) {
    activeIssueIndex.value = -1
  }
}

function onPreviewGoDetail(issueId: string) {
  previewVisible.value = false
  router.push({ name: 'IssueDetail', params: { id: issueId } })
}

// Keyboard navigation for preview mode
function handleKeyboardNav(e: KeyboardEvent) {
  // Apply Command dialog: Ctrl+Alt+J
  if (e.key === 'j' && e.ctrlKey && e.altKey && selectedCount.value > 0) {
    e.preventDefault()
    showCommandDialog.value = true
    return
  }

  // Only handle when preview mode is sidebar and preview is visible
  if (previewMode.value !== 'sidebar' || !previewVisible.value) return
  // Don't intercept when focus is in an input
  const tag = (e.target as HTMLElement)?.tagName?.toLowerCase()
  if (tag === 'input' || tag === 'textarea' || tag === 'select') return

  if (e.key === 'ArrowDown') {
    e.preventDefault()
    navigateIssue(1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    navigateIssue(-1)
  } else if (e.key === 'Escape') {
    e.preventDefault()
    closePreview()
  } else if (e.key === 'Enter') {
    e.preventDefault()
    if (previewIssueId.value) {
      onPreviewGoDetail(previewIssueId.value)
    }
  }
}

function navigateIssue(direction: number) {
  const newIndex = activeIssueIndex.value + direction
  if (newIndex >= 0 && newIndex < issues.value.length) {
    activeIssueIndex.value = newIndex
    const issue = issues.value[newIndex]
    previewIssueId.value = issue.id
  }
}

// Status cache
const statusCache = ref<IssueStatusVO[]>([])

// Inline edit dropdowns
const statusDropdowns = reactive<Record<string, boolean>>({})
const assigneeDropdowns = reactive<Record<string, boolean>>({})
const sprintDropdowns = reactive<Record<string, boolean>>({})
const priorityDropdowns = reactive<Record<string, boolean>>({})
const transitionsLoading = reactive<Record<string, boolean>>({})
const availableTransitions = reactive<Record<string, IssueStatusVO[]>>({})
const assigneeSearch = ref('')
const assigneeOptions = ref<ProjectMemberVO[]>([])
const assigneeOptionsLoading = ref(false)
const sprintOptionsLoading = reactive<Record<string, boolean>>({})
const sprintOptionsCache = reactive<Record<string, SprintVO[]>>({})

const priorityOptions = [
  { value: 'Critical', label: '\u7D27\u6025' },
  { value: 'High', label: '\u9AD8' },
  { value: 'Normal', label: '\u666E\u901A' },
  { value: 'Low', label: '\u4F4E' }
]

// Column widths — default values, user can resize via drag
const COLUMN_WIDTH_STORAGE_KEY = 'trackflow:issue-column-widths'
const DEFAULT_COLUMN_WIDTHS: Record<string, number> = {
  issueKey: 130,
  title: 300, // min width for title
  assignee: 110,
  status: 120,
  sprint: 160,
  priority: 100,
  updatedAt: 100,
  issueType: 80,
  reporter: 110,
  createdAt: 110,
  dueDate: 100
}

const columnWidths = reactive<Record<string, number>>(loadColumnWidths())

function loadColumnWidths(): Record<string, number> {
  try {
    const stored = localStorage.getItem(COLUMN_WIDTH_STORAGE_KEY)
    if (stored) return { ...DEFAULT_COLUMN_WIDTHS, ...JSON.parse(stored) }
  } catch { /* ignore */ }
  return { ...DEFAULT_COLUMN_WIDTHS }
}

/**
 * 表格最小总宽度 = 各可见列宽度之和 + checkbox列(60px，仅有权限时)
 */
const tableMinWidth = computed(() => {
  const sum = visibleColumns.value
    .filter(c => c.key !== 'checkbox')
    .reduce((acc, col) => {
      return acc + (columnWidths[col.key] || DEFAULT_COLUMN_WIDTHS[col.key] || 100)
    }, 0)
  return sum + (canBatchOps.value ? 60 : 0)
})

/**
 * 动态生成 a-table columns 配置
 */
const tableColumns = computed(() => {
  return visibleColumns.value
    .filter(c => c.key !== 'checkbox')
    .map(col => ({
      title: col.label,
      dataIndex: col.key,
      slotName: col.key === 'title' ? 'title-cell' : col.key.startsWith('cf_') ? 'customFieldCell' : col.key,
      titleSlotName: 'column-header',
      width: columnWidths[col.key] || DEFAULT_COLUMN_WIDTHS[col.key] || 100,
      ellipsis: true,
      tooltip: col.key === 'title'
    }))
})

// Row selection config for a-table (only show when user has batch ops permission)
const rowSelection = computed(() => {
  if (!canBatchOps.value) return undefined
  return {
    type: 'checkbox' as const,
    showCheckedAll: true
  }
})

// a-table event handlers
const selectedKeysArray = computed(() => [...selectedIds.value])

function onRowClick(record: TableData) {
  if (previewMode.value === 'sidebar') {
    const index = issues.value.findIndex(i => i.id === record.id)
    openPreview(record as unknown as IssueVO, index)
  } else {
    router.push(`/issues/${record.issueKey}`)
  }
}
function onRowDblClick(record: TableData) {
  // Double-click always navigates to full detail page regardless of preview mode
  router.push(`/issues/${record.issueKey}`)
}

// List layout event handlers
function onListItemClick(issue: IssueVO) {
  if (previewMode.value === 'sidebar') {
    const index = issues.value.findIndex(i => i.id === issue.id)
    openPreview(issue, index)
  } else {
    router.push(`/issues/${issue.issueKey}`)
  }
}
function onListItemDblClick(issue: IssueVO) {
  router.push(`/issues/${issue.issueKey}`)
}
function onListSortChange(field: string) {
  // 三态切换: null → asc → desc → null (same logic as table)
  if (sortState.value.field !== field) {
    sortState.value = { field, direction: 'asc' }
  } else if (sortState.value.direction === 'asc') {
    sortState.value = { field, direction: 'desc' }
  } else {
    sortState.value = { field: null, direction: null }
  }
  currentPage.value = 1
  refreshList()
}
function onListItemSelect(issue: IssueVO) {
  if (selectedIds.value.has(issue.id)) {
    selectedIds.value.delete(issue.id)
  } else {
    selectedIds.value.add(issue.id)
  }
  selectedIds.value = new Set(selectedIds.value)
}

function onSelectionChange(rowKeys: (string | number)[]) {
  selectedIds.value = new Set(rowKeys.map(String))
}
function onColumnResize(dataIndex: string, width: number) {
  columnWidths[dataIndex] = width
  localStorage.setItem(COLUMN_WIDTH_STORAGE_KEY, JSON.stringify(columnWidths))
}

// Column header interactions
function onHeaderSort(key: string) {
  // 三态切换: null → asc → desc → null
  if (sortState.value.field !== key) {
    sortState.value = { field: key, direction: 'asc' }
  } else if (sortState.value.direction === 'asc') {
    sortState.value = { field: key, direction: 'desc' }
  } else {
    sortState.value = { field: null, direction: null }
  }
}
function onHeaderRemove(key: string) {
  toggleColumn(key)
}
function onHeaderDragDrop(fromKey: string, toKey: string) {
  reorderColumn(fromKey, toKey)
}
function getColumnSortDir(key: string): 'asc' | 'desc' | null {
  if (sortState.value.field === key) return sortState.value.direction
  return null
}
function isColumnFixed(key: string): boolean {
  const col = visibleColumns.value.find(c => c.key === key)
  return col?.fixed === true
}
function isColumnSortable(key: string): boolean {
  const col = visibleColumns.value.find(c => c.key === key)
  return col?.sortable === true
}

// Status helpers
function isResolved(statusId: string): boolean {
  const s = statusCache.value.find(st => st.id === statusId)
  return s?.isClosed === true
}

// Due date helpers
function getDueDateStatus(record: TableData): 'overdue' | 'due-soon' | 'normal' {
  if (!record.dueDate) return 'normal'
  // Closed issues don't show warning colors
  if (isResolved(record.statusId as string)) return 'normal'
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const due = new Date(record.dueDate as string)
  due.setHours(0, 0, 0, 0)
  const diffDays = Math.floor((due.getTime() - today.getTime()) / 86400000)
  if (diffDays < 0) return 'overdue'
  if (diffDays <= 3) return 'due-soon'
  return 'normal'
}
function getDueDateTooltip(record: TableData): string {
  if (!record.dueDate) return ''
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const due = new Date(record.dueDate as string)
  due.setHours(0, 0, 0, 0)
  const diffDays = Math.floor((due.getTime() - today.getTime()) / 86400000)
  if (diffDays < 0) return `已逾期 ${Math.abs(diffDays)} 天`
  if (diffDays === 0) return '今天到期'
  if (diffDays === 1) return '明天到期'
  return `${diffDays} 天后到期`
}

function getRowClass(record: TableData): string {
  const classes: string[] = []
  if (isResolved(record.statusId as string)) classes.push('issue-resolved')
  if (previewMode.value === 'sidebar' && previewVisible.value && record.id === previewIssueId.value) {
    classes.push('issue-previewing')
  }
  return classes.join(' ')
}
function getStatusName(id: string, inlineName?: string) {
  if (inlineName) return localizeStatusName(inlineName)
  const s = statusCache.value.find(st => st.id === id)
  return localizeStatusName(s?.name)
}
function getStatusColor(id: string, inlineColor?: string) {
  if (inlineColor) return inlineColor
  const s = statusCache.value.find(st => st.id === id)
  return s?.color || '#666'
}
function getSprintName(sprintId?: string, inlineName?: string) {
  if (inlineName) return inlineName
  if (!sprintId) return ''
  for (const sprints of Object.values(sprintOptionsCache)) {
    const found = (sprints as SprintVO[]).find(s => s.id === sprintId)
    if (found) return found.name
  }
  return ''
}

// Inline edit - Status
async function openStatusEdit(issue: IssueVO) {
  if (isCellEditing(issue.id, 'statusId')) return
  statusDropdowns[issue.id] = true
  transitionsLoading[issue.id] = true
  try {
    const res = await issueApi.getAvailableTransitions(issue.id)
    availableTransitions[issue.id] = res.data || []
  } catch {
    availableTransitions[issue.id] = []
    Message.error({ content: '\u83B7\u53D6\u53EF\u7528\u72B6\u6001\u5931\u8D25', duration: 3000 })
    statusDropdowns[issue.id] = false
  } finally {
    transitionsLoading[issue.id] = false
  }
}
function selectStatus(issue: IssueVO, status: IssueStatusVO) {
  statusDropdowns[issue.id] = false
  executeEdit(issue.id, 'statusId', status.id, (_signal) => issueApi.transitStatus(issue.id, status.id, undefined, issue.version))
}

// Inline edit - Assignee
async function openAssigneeEdit(issue: IssueVO) {
  if (isCellEditing(issue.id, 'assigneeId')) return
  assigneeDropdowns[issue.id] = true
  assigneeSearch.value = ''
  assigneeOptionsLoading.value = true
  try {
    const res = await projectApi.listAssignableMembers(issue.projectId)
    assigneeOptions.value = res.data || []
  } catch {
    assigneeOptions.value = []
  } finally {
    assigneeOptionsLoading.value = false
  }
}
const filteredAssigneeOptions = computed(() => {
  if (!assigneeSearch.value) return assigneeOptions.value
  const kw = assigneeSearch.value.toLowerCase()
  return assigneeOptions.value.filter(m => m.displayName?.toLowerCase().includes(kw))
})
function selectAssignee(issue: IssueVO, member: ProjectMemberVO | null) {
  Object.keys(assigneeDropdowns).forEach(k => { assigneeDropdowns[k] = false })
  executeEdit(
    issue.id, 'assigneeId', member?.userId || null,
    (_signal) => issueApi.assign(issue.id, member?.userId || ''),
    () => ({ assigneeId: member?.userId || undefined, assigneeName: member?.displayName || undefined })
  )
}

// Inline edit - Sprint
async function openSprintEdit(issue: IssueVO) {
  if (isCellEditing(issue.id, 'sprintId')) return
  sprintDropdowns[issue.id] = true
  if (!sprintOptionsCache[issue.projectId]) {
    sprintOptionsLoading[issue.id] = true
    try {
      const res = await sprintApi.listByProject(issue.projectId, { _silent403: true })
      sprintOptionsCache[issue.projectId] = res.data || []
    } catch {
      sprintOptionsCache[issue.projectId] = []
    } finally {
      sprintOptionsLoading[issue.id] = false
    }
  }
}
function getSprintGroups(projectId: string) {
  const sprints = sprintOptionsCache[projectId] || []
  const groups: { label: string; items: SprintVO[] }[] = []
  const active = sprints.filter(s => s.status?.toLowerCase() === 'active')
  const planned = sprints.filter(s => s.status?.toLowerCase() === 'planned')
  const completed = sprints.filter(s => s.status?.toLowerCase() === 'completed')
  if (active.length) groups.push({ label: '\u8FDB\u884C\u4E2D', items: active })
  if (planned.length) groups.push({ label: '\u8BA1\u5212\u4E2D', items: planned })
  if (completed.length) groups.push({ label: '\u5DF2\u5B8C\u6210', items: completed })
  return groups
}
function selectSprint(issue: IssueVO, sprint: SprintVO | null) {
  sprintDropdowns[issue.id] = false
  executeEdit(issue.id, 'sprintId', sprint?.id || null, (_signal) => issueApi.update(issue.id, { sprintId: sprint?.id || null, version: issue.version }))
}

// Inline edit - Priority
function selectPriority(issue: IssueVO, priority: string) {
  priorityDropdowns[issue.id] = false
  executeEdit(issue.id, 'priority', priority, (_signal) => issueApi.update(issue.id, { priority, version: issue.version }))
}

// Batch operation handlers
async function onBatchState(statusId: string) {
  const result = await batchTransitStatus(selectedIssues.value, statusId)
  if (result.succeeded > 0) {
    selectedIssues.value.forEach(issue => {
      if (!result.failures.find(f => f.issueId === issue.id)) {
        updateLocalIssue(issue.id, { statusId })
      }
    })
  }
  clearSelection()
}
async function onBatchAssign(assigneeId: string | null) {
  await batchAssign(selectedIssues.value, assigneeId || '')
  refreshList()
  clearSelection()
}
async function onBatchSprint(sprintId: string | null) {
  const result = await batchUpdateSprint(selectedIssues.value, sprintId)
  if (result.succeeded > 0) {
    selectedIssues.value.forEach(issue => {
      if (!result.failures.find(f => f.issueId === issue.id)) {
        updateLocalIssue(issue.id, { sprintId: sprintId || undefined })
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
        updateLocalIssue(issue.id, { priority })
      }
    })
  }
  clearSelection()
}
async function onBatchTagAdd(tagId: string) {
  const result = await batchTagAdd(selectedIssues.value, tagId)
  if (result.succeeded > 0) {
    refreshList()
  }
  clearSelection()
}
async function onBatchTagRemove(tagId: string) {
  const result = await batchTagRemove(selectedIssues.value, tagId)
  if (result.succeeded > 0) {
    refreshList()
  }
  clearSelection()
}
async function onBatchLink(linkType: string, targetIssueId: string) {
  const result = await batchAddLink(selectedIssues.value, linkType, targetIssueId)
  if (result.succeeded > 0) {
    refreshList()
  }
  clearSelection()
}
async function onBatchDelete() {
  const result = await batchDelete(selectedIssues.value)
  if (result.succeeded > 0) {
    refreshList()
  }
  clearSelection()
}

function onCommandExecuted() {
  refreshList()
  clearSelection()
}

// ========== 导出功能 ==========
const exportLoading = ref(false)

async function handleExport(format: string | number | Record<string, any> | undefined) {
  await doExport(String(format), false)
}

function onBatchExport(format: string) {
  doExport(format, true)
}

async function doExport(format: string, selectedOnly: boolean) {
  if (format !== 'xlsx' && format !== 'csv') return
  exportLoading.value = true
  try {
    const payload: Record<string, any> = { format }

    if (selectedOnly && selectedIds.value.size > 0) {
      // 选中导出模式
      payload.issueIds = [...selectedIds.value]
    } else {
      // 筛选导出模式：复用当前筛选条件
      if (activeProjectId.value) payload.projectId = activeProjectId.value
      if (filterProject.value) payload.projectId = filterProject.value
      if (hideResolved.value) payload.hideResolved = 'true'
      // Merge global filter params
      const fp = globalFilterParams.value
      if (fp.statusId) payload.statusId = fp.statusId
      if (fp.priority) payload.priority = fp.priority
      if (fp.assigneeId) payload.assigneeId = fp.assigneeId
      if (fp.sprintId) payload.sprintId = fp.sprintId
      if (fp.issueType) payload.issueType = fp.issueType
      if (fp.keyword) payload.keyword = fp.keyword
      if (fp.statusIdNot) payload.statusIdNot = fp.statusIdNot
      if (fp.priorityNot) payload.priorityNot = fp.priorityNot
      if (fp.assigneeIdNot) payload.assigneeIdNot = fp.assigneeIdNot
      if (fp.sprintIdNot) payload.sprintIdNot = fp.sprintIdNot
      if (fp.issueTypeNot) payload.issueTypeNot = fp.issueTypeNot
      if (searchKeyword.value.trim()) payload.keyword = searchKeyword.value.trim()
    }

    const response = await issueApi.export(payload as any)
    // response 是 Blob（responseType: 'blob'）
    const blob = response instanceof Blob ? response : new Blob([response as any])
    const ext = format === 'xlsx' ? '.xlsx' : '.csv'
    const filename = `TrackFlow_Issues_${new Date().toISOString().slice(0, 10)}${ext}`

    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)

    Message.success(`导出成功 (${format.toUpperCase()})`)
  } catch (e: any) {
    // Blob error response needs special handling
    if (e.response?.data instanceof Blob) {
      const text = await e.response.data.text()
      try {
        const json = JSON.parse(text)
        Message.error(json.message || '导出失败')
      } catch {
        Message.error('导出失败')
      }
    } else {
      Message.error(e.response?.data?.message || '导出失败')
    }
  } finally {
    exportLoading.value = false
  }
}

// Panel helpers
const filteredQueries = computed(() => {
  if (!panelSearch.value) return savedQueries.value
  const kw = panelSearch.value.toLowerCase()
  return savedQueries.value.filter((q: any) => q.name.toLowerCase().includes(kw))
})
function toggleGroup(group: string) {
  if (expandedGroups.has(group)) expandedGroups.delete(group)
  else expandedGroups.add(group)
}
function formatCount(count: number) {
  if (count >= 10000) return Math.floor(count / 1000) + 'k+'
  if (count >= 1000) return (count / 1000).toFixed(1) + 'k'
  return String(count)
}

function formatTime(dt: string) {
  if (!dt) return ''
  const d = new Date(dt)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 60) return `${mins}\u5206\u949F\u524D`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}\u5C0F\u65F6\u524D`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days}\u5929\u524D`
  return d.toLocaleDateString('zh-CN')
}
const QUICK_CREATE_PROJECT_KEY = 'trackflow:quick-create-project'

function resolveQuickCreateProject(): string | undefined {
  // Priority 1: current active project filter (sidebar or dropdown)
  if (activeProjectId.value) return activeProjectId.value
  if (filterProject.value) return filterProject.value

  // Priority 2: last used project from localStorage
  const lastUsed = localStorage.getItem(QUICK_CREATE_PROJECT_KEY)
  if (lastUsed && projectList.value.some(p => p.id === lastUsed)) return lastUsed

  // Priority 3: user only belongs to one project
  if (projectList.value.length === 1) return projectList.value[0].id

  return undefined
}

function toggleInlineCreate() {
  showInlineCreate.value = !showInlineCreate.value
  if (showInlineCreate.value) {
    quickForm.projectId = resolveQuickCreateProject()
  }
}
async function quickCreate() {
  if (!quickForm.projectId) {
    Message.warning('请先选择项目')
    return
  }
  if (!quickForm.title.trim()) {
    Message.warning('请输入工单标题')
    return
  }
  quickCreating.value = true
  try {
    await issueApi.create({ projectId: quickForm.projectId, title: quickForm.title.trim(), issueType: quickForm.issueType, priority: quickForm.priority })
    Message.success('\u5DE5\u5355\u521B\u5EFA\u6210\u529F')
    // Remember last used project
    localStorage.setItem(QUICK_CREATE_PROJECT_KEY, quickForm.projectId)
    quickForm.title = ''
    refreshList()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '\u521B\u5EFA\u5931\u8D25')
  } finally {
    quickCreating.value = false
  }
}

function buildFilters() {
  const filters: Record<string, any> = {}
  if (activeProjectId.value) filters.projectId = activeProjectId.value
  if (filterProject.value) filters.projectId = filterProject.value
  if (activeQueryId.value) filters.queryId = activeQueryId.value
  if (searchKeyword.value.trim()) filters.keyword = searchKeyword.value.trim()
  if (hideResolved.value) filters.hideResolved = 'true'
  // Merge global filter params (from FilterBar's filter mode)
  Object.assign(filters, globalFilterParams.value)
  return filters
}
function refreshList() { loadIssues(buildFilters()).then(() => { loadPermissions(); preloadSprintNames() }) }

/** 预加载当前列表中涉及到的 sprint 名称 */
async function preloadSprintNames() {
  // 用户无 sprint:view 权限时跳过，避免触发 403
  if (!canViewSprintGlobal.value) return
  const projectIds = [...new Set(issues.value.map(i => i.projectId).filter(Boolean))]
  const toLoad = projectIds.filter(pid => !sprintOptionsCache[pid])
  await Promise.all(toLoad.map(async (pid) => {
    try {
      const res = await sprintApi.listByProject(pid, { _silent403: true })
      sprintOptionsCache[pid] = res.data || []
    } catch {
      sprintOptionsCache[pid] = []
    }
  }))
}
function onFilterChange() {
  currentPage.value = 1
  // Sync project context when filterProject dropdown changes
  if (filterProject.value) {
    const matched = projectList.value.find(p => p.id === filterProject.value)
    activeProjectId.value = filterProject.value
    activeQueryName.value = matched?.name || '所有工单'
    router.replace({ query: { ...route.query, project: matched?.key || filterProject.value } })
  } else {
    // Cleared project filter
    activeProjectId.value = null
    activeQueryName.value = '所有工单'
    const { project, ...rest } = route.query
    router.replace({ query: rest })
  }
  refreshList()
}

/**
 * 将 Saved Query 的 filters JSON 转换为 FilterBar 可展示的 InitialFilter[] 格式
 * 处理: 字段名映射、值名称解析、特殊操作符（open）、${currentUser} 替换
 */
function parseSavedQueryFilters(filtersRaw: string | any[] | null | undefined): any[] {
  if (!filtersRaw) return []

  let filters: any[]
  if (typeof filtersRaw === 'string') {
    try { filters = JSON.parse(filtersRaw) } catch { return [] }
  } else {
    filters = filtersRaw
  }
  if (!Array.isArray(filters) || filters.length === 0) return []

  // Field name mapping: DB filter field → FilterBar fieldKey
  const fieldMap: Record<string, string> = {
    type: 'issueType',
    status: 'status',
    sprint: 'sprint',
    assignee: 'assignee',
    priority: 'priority',
    project: 'project',
    reporter: 'reporter',
  }

  // Operator mapping: DB operator → FilterBar operator
  const operatorMap: Record<string, string> = {
    eq: 'is',
    neq: 'is_not',
    in: 'any_of',
    not_in: 'none_of',
    contains: 'is',
    open: 'any_of',  // "open" means all non-closed statuses
  }

  const currentUserId = authStore.user?.userId || authStore.user?.id || ''

  const chips: any[] = []

  for (const f of filters) {
    const fieldKey = fieldMap[f.field]
    if (!fieldKey) continue  // Skip unknown fields (like 'keyword', 'reporter')

    // Skip reporter field — FilterBar doesn't have it
    if (fieldKey === 'reporter') continue

    const operator = operatorMap[f.operator] || 'is'
    let values: string[] = []
    let valueLabels: string[] = []

    // Handle special operator "open" = all non-closed statuses
    if (f.field === 'status' && f.operator === 'open') {
      // "open" is a semantic operator meaning "all non-closed"
      // Show a single summarized chip instead of listing all open statuses
      const openStatuses = statusCache.value.filter(s => !s.isClosed)
      values = openStatuses.map(s => s.id)
      valueLabels = ['未关闭']
    } else {
      // Normal values
      const rawValues: string[] = f.value || []
      for (const v of rawValues) {
        // Replace ${currentUser}
        const resolvedValue = v === '${currentUser}' ? currentUserId : v

        switch (fieldKey) {
          case 'status': {
            // Values are status codes — resolve to IDs and labels
            const status = statusCache.value.find(s => s.code === resolvedValue || s.id === resolvedValue)
            if (status) {
              values.push(status.id)
              valueLabels.push(localizeStatusName(status.name))
            } else {
              values.push(resolvedValue)
              valueLabels.push(resolvedValue)
            }
            break
          }
          case 'issueType': {
            values.push(resolvedValue)
            valueLabels.push(issueTypeLabelMap[resolvedValue] || resolvedValue)
            break
          }
          case 'priority': {
            values.push(resolvedValue)
            valueLabels.push(priorityLabelMap[resolvedValue] || resolvedValue)
            break
          }
          case 'sprint': {
            values.push(resolvedValue)
            // Try to resolve sprint name from cache
            let sprintLabel = `Sprint #${resolvedValue}`
            for (const sprints of Object.values(sprintOptionsCache)) {
              const matched = sprints.find(s => s.id === resolvedValue)
              if (matched) { sprintLabel = matched.name; break }
            }
            valueLabels.push(sprintLabel)
            break
          }
          case 'assignee': {
            values.push(resolvedValue)
            if (v === '${currentUser}') {
              valueLabels.push('我')
            } else {
              valueLabels.push(resolvedValue)
            }
            break
          }
          case 'project': {
            values.push(resolvedValue)
            const proj = projectList.value.find(p => p.id === resolvedValue)
            valueLabels.push(proj ? proj.name : resolvedValue)
            break
          }
          default: {
            values.push(resolvedValue)
            valueLabels.push(resolvedValue)
          }
        }
      }
    }

    if (values.length > 0) {
      chips.push({ fieldKey, operator, values, valueLabels })
    }
  }

  return chips
}

function selectQuery(q: any) {
  activeQueryId.value = q.id; activeQueryName.value = q.name; activeQueryObj.value = q; activeProjectId.value = null; filterProject.value = undefined; searchKeyword.value = ''; globalFilterParams.value = {}; currentPage.value = 1
  const { project, ...rest } = route.query
  router.replace({ query: rest })

  // YouTrack style: clicking a saved query only shows the query name chip in search bar
  // Does NOT expand filter conditions — user must click the chip to see/edit conditions
  filterBarRef.value?.clearAll()

  refreshList()
}
function selectAllProjects() {
  if (activeProjectId.value === null) return // Already showing all projects
  activeProjectId.value = null; activeQueryId.value = null; activeQueryName.value = '所有工单'; activeQueryObj.value = null; filterProject.value = undefined; currentPage.value = 1
  const { project, ...rest } = route.query
  router.replace({ query: rest })
  refreshList()
  loadPanel()
}
function selectProject(p: any) {
  if (activeProjectId.value === p.id) {
    // Toggle off: clicking active project clears the filter
    selectAllProjects()
    return
  }
  // Select project
  activeProjectId.value = p.id; activeQueryId.value = null; activeQueryName.value = p.name; activeQueryObj.value = null; filterProject.value = p.id; currentPage.value = 1
  router.replace({ query: { ...route.query, project: p.key } })
  refreshList()
  loadPanel()
}

watch(currentPage, () => refreshList())
watch(sortState, () => refreshList(), { deep: true })

// Init
async function loadPanel() {
  try {
    const res = await queryApi.getPanel(activeProjectId.value || undefined)
    const data = res.data || {}
    savedQueries.value = [...(data.pinned || []), ...(data.queries || [])]
  } catch {
    savedQueries.value = [
      { id: '1', name: 'Assigned to me', count: 12 },
      { id: '2', name: 'Reported by me', count: 8 },
      { id: '3', name: 'All open', count: 45 }
    ]
  }
}
async function loadProjects() {
  try { const res = await projectApi.list({ pageSize: 50 }); projectList.value = res.data?.list || [] }
  catch { projectList.value = [] }
}
async function loadStatuses() {
  try { const res = await issueApi.listStatuses(); statusCache.value = res.data || [] }
  catch { statusCache.value = [] }
}

onMounted(async () => {
  await loadPanel()
  await loadProjects()
  await loadStatuses()

  // Resolve project from URL param (supports both key and id for backward compat)
  if (route.query.project) {
    const queryProject = String(route.query.project)
    const matched = projectList.value.find(p => p.key === queryProject || p.id === queryProject)
    if (matched) {
      activeProjectId.value = matched.id
      activeQueryName.value = matched.name
      filterProject.value = matched.id
    } else {
      // Fallback: treat as ID directly (backward compat for old bookmarks)
      activeProjectId.value = queryProject
    }
  }

  // Sync project context display from URL param after projectList is loaded
  if (activeProjectId.value && projectList.value.length > 0 && !filterProject.value) {
    const matched = projectList.value.find(p => p.id === activeProjectId.value)
    if (matched) {
      activeQueryName.value = matched.name
      filterProject.value = matched.id
    }
  }

  // Handle dashboard filter params (statusId, statusCode, statusCategory, label, sprint, etc.)
  if (route.query.statusId || route.query.statusCode || route.query.statusCategory || route.query.statusName || route.query.overdue || route.query.dueSoon || route.query.sprint || route.query.reportedByMe || route.query.priority || route.query.issueType || route.query.assigneeName || route.query.assignee || route.query.projectId) {
    applyDashboardFilter()
  } else {
    refreshList()
  }

  // Listen for undo-restore events from batch delete
  window.addEventListener('trackflow:issues-restored', handleIssuesRestored)

  // Keyboard navigation for preview mode
  document.addEventListener('keydown', handleKeyboardNav)
})

onUnmounted(() => {
  window.removeEventListener('trackflow:issues-restored', handleIssuesRestored)
  document.removeEventListener('keydown', handleKeyboardNav)
})

function handleIssuesRestored() {
  refreshList()
}

function applyDashboardFilter() {
  // 清除已选中的保存查询，防止 buildFilters() 中 queryId 覆盖 dashboard 过滤条件
  activeQueryId.value = null
  activeQueryObj.value = null
  filterProject.value = undefined
  searchKeyword.value = ''

  const filters: Record<string, any> = {}
  const chips: any[] = []

  if (route.query.statusId) {
    const statusIds = String(route.query.statusId).split(',')
    filters.statusId = String(route.query.statusId)

    // Build chip with status names
    const statusNames = statusIds.map(id => {
      const s = statusCache.value.find(st => st.id === id)
      return s?.name || id
    })
    chips.push({
      fieldKey: 'status',
      operator: 'any_of',
      values: statusIds,
      valueLabels: statusNames
    })
  }

  // statusCode: 单个状态代码（如 'testing'）
  if (route.query.statusCode) {
    const code = String(route.query.statusCode)
    const matchedStatus = statusCache.value.find(st => st.code === code)
    if (matchedStatus) {
      filters.statusId = matchedStatus.id
      chips.push({
        fieldKey: 'status',
        operator: 'any_of',
        values: [matchedStatus.id],
        valueLabels: [matchedStatus.name]
      })
    }
  }

  // statusName: 按状态英文名匹配（从报表图表下钻时使用）
  if (route.query.statusName) {
    const name = String(route.query.statusName)
    const matchedStatus = statusCache.value.find(st => st.name === name)
    if (matchedStatus) {
      filters.statusId = matchedStatus.id
      chips.push({
        fieldKey: 'status',
        operator: 'any_of',
        values: [matchedStatus.id],
        valueLabels: [matchedStatus.name]
      })
    }
  }

  // statusCategory: 状态分类（如 'open', 'in_progress', 'done'）
  if (route.query.statusCategory) {
    const category = String(route.query.statusCategory)
    const matchedStatuses = statusCache.value.filter(st => st.category === category)
    if (matchedStatuses.length > 0) {
      const ids = matchedStatuses.map(s => s.id)
      filters.statusId = ids.join(',')
      chips.push({
        fieldKey: 'status',
        operator: 'any_of',
        values: ids,
        valueLabels: matchedStatuses.map(s => s.name)
      })
    }
  }

  // reportedByMe: 我报告的未解决工单
  if (route.query.reportedByMe) {
    filters.reportedByMe = 'true'
    chips.push({
      fieldKey: 'reporter',
      operator: 'equals',
      values: ['me'],
      valueLabels: ['我']
    })
  }

  if (route.query.overdue) {
    filters.overdue = 'true'
    // No chip needed — displayed in label
  }

  if (route.query.dueSoon) {
    filters.dueSoon = 'true'
  }

  if (route.query.sprint) {
    filters.sprintId = String(route.query.sprint)
    chips.push({
      fieldKey: 'sprint',
      operator: 'equals',
      values: [String(route.query.sprint)],
      valueLabels: ['Sprint']
    })
  }

  // priority: 优先级（如 'Normal', 'High'）
  if (route.query.priority) {
    const priority = String(route.query.priority)
    filters.priority = priority
    chips.push({
      fieldKey: 'priority',
      operator: 'equals',
      values: [priority],
      valueLabels: [priority]
    })
  }

  // issueType: 工单类型（如 'Bug', 'Task', 'Feature'）
  if (route.query.issueType) {
    const issueType = String(route.query.issueType)
    filters.issueType = issueType
    chips.push({
      fieldKey: 'type',
      operator: 'equals',
      values: [issueType],
      valueLabels: [issueType]
    })
  }

  // assigneeName: 按负责人名称筛选
  if (route.query.assigneeName) {
    const name = String(route.query.assigneeName)
    filters.assigneeName = name
    chips.push({
      fieldKey: 'assignee',
      operator: 'equals',
      values: [name],
      valueLabels: [name]
    })
  }

  // assignee=unassigned: 筛选未分配负责人的工单
  if (route.query.assignee === 'unassigned') {
    filters.assigneeId = 'none'
    chips.push({
      fieldKey: 'assignee',
      operator: 'equals',
      values: ['none'],
      valueLabels: ['未分配']
    })
  }

  // project (key) or projectId: 项目筛选（从 Sprint/报表页面跳转时带入）
  if (route.query.project) {
    const queryProject = String(route.query.project)
    const matched = projectList.value.find(p => p.key === queryProject || p.id === queryProject)
    if (matched) {
      filterProject.value = matched.id
      activeProjectId.value = matched.id
    }
  } else if (route.query.projectId) {
    const pid = String(route.query.projectId)
    filterProject.value = pid
    activeProjectId.value = pid
  }

  // Set display label
  if (route.query.label) {
    activeQueryName.value = String(route.query.label)
  }

  initialFilterChips.value = chips
  globalFilterParams.value = filters
  refreshList()
}
</script>

<style scoped>
.issue-page { display: flex; height: 100%; }

/* Left panel */
.query-panel { background: var(--tf-bg-surface); overflow-y: auto; overflow-x: hidden; flex-shrink: 0; display: flex; flex-direction: column; transition: width 0.2s ease; }
.panel-resizer { width: 4px; flex-shrink: 0; cursor: col-resize; background: transparent; position: relative; z-index: 2; transition: background 0.15s; }
.panel-resizer:hover, .panel-resizer:active { background: var(--tf-accent, #58a6ff); }
.panel-resizer::after { content: ''; position: absolute; top: 0; bottom: 0; left: -2px; right: -2px; }
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

/* Icon picker */
.icon-picker { display: flex; flex-wrap: wrap; gap: 6px; }
.icon-option { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; font-size: 16px; border-radius: 6px; cursor: pointer; border: 1px solid var(--tf-border); transition: all 0.15s; }
.icon-option:hover { background: var(--tf-bg-hover); transform: scale(1.1); }
.icon-option.selected { background: var(--tf-accent-bg); border-color: var(--tf-accent); }
.icon-preview { margin-top: 8px; font-size: 12px; color: var(--tf-text-secondary); }

/* Group action button */
.group-action-btn { margin-left: auto; opacity: 0; transition: opacity 0.15s; }
.group-header:hover .group-action-btn { opacity: 1; }

/* Query delete button */
.query-delete-btn { font-size: 10px; color: var(--tf-text-quaternary); cursor: pointer; padding: 2px 4px; border-radius: 3px; opacity: 0; transition: opacity 0.15s, color 0.15s, background 0.15s; flex-shrink: 0; }
.query-item:hover .query-delete-btn { opacity: 1; }
.query-delete-btn:hover { color: var(--tf-danger); background: rgba(248, 81, 73, 0.1); }

/* Query action button (⋯) */
.query-action-btn { font-size: 14px; color: var(--tf-text-quaternary); cursor: pointer; padding: 2px 4px; border-radius: 3px; opacity: 0; transition: opacity 0.15s, color 0.15s, background 0.15s; flex-shrink: 0; line-height: 1; }
.query-item:hover .query-action-btn { opacity: 1; }
.query-action-btn:hover { color: var(--tf-text-primary); background: var(--tf-bg-hover); }

/* Context menu delete option */
.query-ctx-delete { color: var(--tf-danger) !important; }
.query-ctx-delete:hover { background: rgba(248, 81, 73, 0.08) !important; }

/* Create query modal */
.query-preview-filters { display: flex; flex-wrap: wrap; gap: 6px; }
.preview-chip { padding: 2px 8px; background: var(--tf-bg-surface); border: 1px solid var(--tf-border); border-radius: 3px; font-size: 12px; color: var(--tf-text-secondary); }
.preview-empty { font-size: 12px; color: var(--tf-text-tertiary); }

/* Edit query modal */
.form-help-text { font-size: 12px; color: var(--tf-text-tertiary); margin-left: 8px; }

/* Manage queries modal */
.manage-queries-content { display: flex; flex-direction: column; gap: 12px; }
.manage-queries-hint { font-size: 12px; color: var(--tf-text-tertiary); margin: 0; }
.manage-queries-search { margin-bottom: 4px; }
.manage-queries-list { max-height: 400px; overflow-y: auto; display: flex; flex-direction: column; gap: 2px; }
.manage-query-item {
  display: flex; align-items: center; gap: 8px; padding: 8px 12px;
  border-radius: 6px; cursor: pointer; transition: background 100ms;
}
.manage-query-item:hover { background: var(--tf-bg-hover); }
.manage-query-star { font-size: 16px; color: var(--tf-text-tertiary); transition: color 100ms; flex-shrink: 0; }
.manage-query-star.favorited { color: var(--tf-accent); }
.manage-query-icon { font-size: 14px; flex-shrink: 0; }
.manage-query-name { font-size: 13px; color: var(--tf-text-primary); flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.manage-query-owner { font-size: 11px; color: var(--tf-text-tertiary); flex-shrink: 0; padding: 1px 6px; background: var(--tf-bg-surface); border-radius: 3px; }
.manage-queries-empty { text-align: center; padding: 24px; font-size: 13px; color: var(--tf-text-tertiary); }

/* Right area */
.issue-list-area { flex: 1; display: flex; flex-direction: column; min-width: 0; overflow: hidden; }

.filter-bar { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid var(--tf-border); flex-shrink: 0; }
.filter-left { display: flex; align-items: center; gap: 12px; }
.current-query-name { font-size: 14px; font-weight: 500; color: var(--tf-text-primary); }
.issue-total-badge { font-size: 12px; color: var(--tf-text-tertiary); }

/* Hide resolved toggle */
.hide-resolved-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 10px;
  border: 1px solid var(--tf-border);
  border-radius: var(--tf-radius-md, 6px);
  background: transparent;
  color: var(--tf-text-tertiary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.hide-resolved-toggle:hover {
  border-color: var(--tf-accent);
  color: var(--tf-text-secondary);
  background: var(--tf-bg-hover);
}
.hide-resolved-toggle.active {
  border-color: var(--tf-accent);
  background: var(--tf-accent-bg);
  color: var(--tf-accent);
}
.hide-resolved-toggle .toggle-label { font-size: 12px; }
.filter-right { display: flex; gap: 8px; align-items: center; }

/* Inline create */
.inline-create { padding: 8px 16px; background: var(--tf-bg-surface); border-bottom: 1px solid var(--tf-border); flex-shrink: 0; }
.inline-create-row { display: flex; align-items: center; gap: 8px; }
.inline-title-input { flex: 1; }

/* Table */
.issue-table { flex: 1; min-height: 0; overflow: hidden; }
.issue-table :deep(.arco-table) { font-size: 13px; }
.issue-table :deep(.arco-scrollbar-container.arco-table-content) { overflow-y: auto !important; }
.issue-table :deep(.arco-table-th) { font-size: 11px; color: var(--tf-text-tertiary); text-transform: uppercase; letter-spacing: 0.5px; background: var(--tf-bg-surface); }
.issue-table :deep(.arco-table-tr) { cursor: pointer; transition: background 0.15s; }
.issue-table :deep(.arco-table-tr:hover .arco-table-td) { background: var(--tf-bg-hover); }
.issue-table :deep(.arco-table-td) { padding: 8px 12px; }
.issue-table :deep(.arco-table-col-resize-handle) { width: 3px; background: transparent; transition: background 0.15s; }
.issue-table :deep(.arco-table-col-resize-handle:hover),
.issue-table :deep(.arco-table-col-resize-handle.active) { background: var(--tf-accent); }

.issue-key { color: var(--tf-accent); font-weight: 500; font-size: 12px; }
.issue-title-text { color: var(--tf-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; display: block; }

/* Resolved issue styling (YouTrack: strikethrough key + gray text) */
.issue-table :deep(.issue-resolved) .issue-key { text-decoration: line-through; color: var(--tf-text-tertiary); }
.issue-table :deep(.issue-resolved) .issue-title-text { color: var(--tf-text-tertiary); }
.issue-table :deep(.issue-resolved) .editable-cell { color: var(--tf-text-tertiary); }
.issue-table :deep(.issue-resolved) .readonly-cell { color: var(--tf-text-tertiary); }
.issue-table :deep(.issue-resolved) .type-label { color: var(--tf-text-tertiary); }
.issue-table :deep(.issue-resolved) .reporter-name { color: var(--tf-text-tertiary); }
.issue-table :deep(.issue-resolved) .time-ago { color: var(--tf-text-quaternary); }
.issue-table :deep(.issue-resolved) .cf-cell { color: var(--tf-text-tertiary); }
.type-label { font-size: 12px; color: var(--tf-text-secondary); }
.reporter-name { font-size: 12px; color: var(--tf-text-secondary); }

/* Editable cells */
.editable-cell { display: inline-flex; align-items: center; gap: 4px; cursor: pointer; padding: 2px 6px; border-radius: 3px; transition: background 0.15s; font-size: 12px; color: var(--tf-text-secondary); }
.editable-cell:hover { background: var(--tf-bg-hover); }
.readonly-cell { display: inline-flex; align-items: center; gap: 4px; padding: 2px 6px; font-size: 12px; color: var(--tf-text-secondary); cursor: default; }
.cell-spinner { font-size: 12px; color: var(--tf-text-tertiary); animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

.status-badge { padding: 2px 8px; border-radius: 3px; font-size: 11px; color: #fff; font-weight: 500; }
.priority-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; }
.priority-critical { background: var(--tf-danger); }
.priority-high { background: var(--tf-warning); }
.priority-normal { background: var(--tf-accent); }
.priority-low { background: var(--tf-text-tertiary); }
.time-ago { font-size: 11px; color: var(--tf-text-tertiary); }
.due-date-cell { font-size: 11px; color: var(--tf-text-tertiary); }
.due-date-cell.due-overdue { color: var(--tf-danger); font-weight: 500; }
.due-date-cell.due-due-soon { color: var(--tf-warning); font-weight: 500; }
.issue-table :deep(.issue-resolved) .due-date-cell { color: var(--tf-text-quaternary); font-weight: 400; }
.cf-cell { font-size: 12px; color: var(--tf-text-secondary); }
.cf-badge { display: inline-block; padding: 1px 6px; border-radius: 3px; font-size: 11px; font-weight: 500; line-height: 1.4; }

/* Child progress cell */
.child-progress-cell { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; }
.child-progress-bar { width: 40px; height: 4px; border-radius: 2px; background: var(--tf-border); overflow: hidden; }
.child-progress-fill { display: block; height: 100%; border-radius: 2px; background: var(--tf-accent); transition: width 0.2s ease; }
.child-progress-text { color: var(--tf-text-secondary); font-variant-numeric: tabular-nums; }

/* Inline dropdowns */
.inline-dropdown { background: var(--tf-bg-elevated); border: 1px solid var(--tf-border); border-radius: 6px; padding: 4px; min-width: 150px; max-height: 240px; overflow-y: auto; }
.member-dropdown { min-width: 200px; }
.dropdown-search { padding: 4px; margin-bottom: 4px; }
.dropdown-item { display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-radius: 4px; cursor: pointer; font-size: 13px; color: var(--tf-text-primary); transition: background 0.15s; }
.dropdown-item:hover { background: var(--tf-bg-hover); }
.dropdown-group-label { padding: 6px 8px 2px; font-size: 11px; color: var(--tf-text-tertiary); text-transform: uppercase; letter-spacing: 0.5px; font-weight: 500; }
.dropdown-loading { display: flex; justify-content: center; padding: 12px; }
.dropdown-empty { padding: 12px; text-align: center; font-size: 12px; color: var(--tf-text-tertiary); }
.status-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.member-avatar { width: 24px; height: 24px; border-radius: 50%; background: var(--tf-accent-bg); color: var(--tf-accent); display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 500; flex-shrink: 0; }
.unassigned-icon { width: 24px; text-align: center; color: var(--tf-text-tertiary); }

/* Empty state */
.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 48px 24px; gap: 8px; }
.empty-icon { font-size: 36px; color: var(--tf-text-quaternary); }
.empty-title { font-size: 14px; font-weight: 500; color: var(--tf-text-primary); margin: 0; }
.empty-desc { font-size: 12px; color: var(--tf-text-tertiary); margin: 0 0 8px; }

/* Pagination */
.pagination-bar { display: flex; justify-content: center; padding: 12px 16px; border-top: 1px solid var(--tf-border); flex-shrink: 0; }

/* Preview mode active row highlight */
.issue-table :deep(.issue-previewing .arco-table-td) { background: var(--tf-accent-bg); }
.issue-table :deep(.issue-previewing) { position: relative; }
.issue-table :deep(.issue-previewing)::after {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--tf-accent);
  border-radius: 0 2px 2px 0;
}

/* Preview mode toggle dropdown active item */
.doption-active { color: var(--tf-accent) !important; font-weight: 500; }
.doption-active::before { content: '✓ '; }
</style>
