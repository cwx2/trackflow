<template>
  <div class="issue-page">
    <!-- Left query panel (YouTrack style) -->
    <QueryPanel
      ref="queryPanelRef"
      :can-create-issue="canCreateIssueGlobal"
      :active-project-id="activeProjectId"
      :active-query-id="activeQueryId"
      :active-tag-id="activeTagId"
      :status-cache="statusCache"
      :issue-type-options="issueTypeOptions"
      :priority-options="priorityOptions"
      :hide-resolved="hideResolved"
      :recovered-draft-id="recoveredDraftId"
      @select-query="selectQuery"
      @select-project="selectProject"
      @select-all-projects="selectAllProjects"
      @select-tag="selectTag"
      @open-draft="openDraft"
      @open-draft-create="openDraftCreate"
      @refresh-list="refreshList"
    />

    <!-- Resizable divider -->
    <div
      class="panel-resizer"
      title="拖动以调整宽度，双击以展开/折叠"
      @mousedown="queryPanelRef?.startPanelResize($event)"
      @dblclick="queryPanelRef?.togglePanelCollapse()"
    ></div>

    <!-- Right issue list area -->
    <section class="issue-list-area">
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

      <!-- Unified filter+toolbar bar -->
      <div v-if="selectedCount === 0" class="filter-bar">
        <!-- Search/Filter bar (YouTrack style with mode toggle) -->
        <FilterBar
          ref="filterBarRef"
          :project-id="activeProjectId"
          :status-list="statusCache"
          :project-list="(projectList as any)"
          :initial-filters="initialFilterChips"
          :active-query-name="(activeQueryId || isDashboardFilterActive) ? activeQueryName : null"
          :is-owned-query="activeQueryId ? activeQueryOwned : false"
          :readonly-filter-labels="activeQueryReadonlyLabels"
          :query-filters="activeQueryParsedFilters"
          @search="onGlobalSearch"
          @filter="onGlobalFilter"
          @clear-query="onClearQuery"
          @chip-click="onQueryChipClick"
        />
        <div class="filter-right">
          <a-select v-model="filterProject" placeholder="所有项目" size="small" style="width: 120px" allow-clear @change="onFilterChange">
            <a-option v-for="p in projectList" :key="p.id" :value="p.id">{{ p.key }}</a-option>
          </a-select>
          <a-button v-if="canCreateIssueGlobal" size="small" @click="toggleInlineCreate">
            {{ showInlineCreate ? '取消' : '快速创建' }}
          </a-button>
          <a-button v-if="canCreateIssueGlobal" type="primary" size="small" @click="showCreatePanel = true">创建工单</a-button>
          <a-dropdown trigger="click" position="br" @select="handleExport">
            <a-tooltip content="导出数据" position="bottom" mini>
              <a-button size="small" type="text" :loading="exportLoading">
                <template #icon><icon-download /></template>
              </a-button>
            </a-tooltip>
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
            <a-tooltip content="预览模式" position="bottom" mini>
              <a-button size="small" type="text">
                <template #icon><icon-eye /></template>
              </a-button>
            </a-tooltip>
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

      <!-- Recent Issues Panel (YouTrack style) -->
      <RecentIssuesPanel />

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
            <a-option v-for="t in issueTypeOptions" :key="t.value" :value="t.value">{{ t.label }}</a-option>
          </a-select>
          <a-select v-model="quickForm.priority" size="small" style="width: 80px">
            <a-option v-for="p in priorityOptions" :key="p.value" :value="p.value">
              <IssuePriorityBadge :priority="p.value" :color="p.color ?? undefined" mode="dot" :show-label="true" />
            </a-option>
          </a-select>
          <a-button type="primary" size="small" :loading="quickCreating" :disabled="!quickForm.projectId || !quickForm.title" @click="quickCreate">
            创建
          </a-button>
        </div>
      </div>

      <!-- Issue table (Table layout mode) -->
      <!-- Real-time update notification -->
      <div v-if="hasNewUpdates" class="realtime-update-bar" @click="onRefreshForUpdates">
        <span class="realtime-update-text">有新的工单变更，点击刷新</span>
        <icon-loading v-if="loading" class="realtime-update-icon" />
      </div>

      <!-- Search scope hint (shown when keyword is active) -->
      <div v-if="searchKeyword" class="search-scope-hint">
        <icon-search class="search-scope-icon" />
        <span>搜索范围：标题、描述、工单编号、负责人</span>
        <span class="search-result-count" v-if="!loading">{{ totalIssues }} 条结果</span>
      </div>

      <div v-if="isTableLayout" class="issue-table-wrapper">
      <a-table
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
        :scroll="{ x: tableMinWidth, y: '100%' }"
        @row-click="onRowClick"
        @row-dblclick="onRowDblClick"
        @row-contextmenu="onTableRowContextMenu"
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
          <router-link :to="`/issues/${record.issueKey}`" class="issue-key" @click.stop>{{ record.issueKey }}</router-link>
        </template>
        <template #title-cell="{ record }">
          <span v-if="searchKeyword" class="issue-title-text" v-html="highlightKeyword(record.title, searchKeyword)"></span>
          <span v-else class="issue-title-text">{{ record.title }}</span>
          <span v-if="searchKeyword && record.matchContext" class="issue-match-context">
            <span class="match-source-label">{{ record.matchSource === 'issueKey' ? '编号' : '描述' }}</span>
            <span v-html="highlightKeyword(record.matchContext, searchKeyword)"></span>
          </span>
          <template v-if="record.tags && record.tags.length > 0">
            <span
              v-for="tag in record.tags.slice(0, 3)"
              :key="tag.id"
              class="issue-tag-badge"
              :style="{ background: tag.color || '#6b7280' }"
              :title="tag.name"
            >{{ tag.name }}</span>
            <span v-if="record.tags.length > 3" class="issue-tag-overflow" :title="record.tags.slice(3).map((t: any) => t.name).join(', ')">+{{ record.tags.length - 3 }}</span>
          </template>
        </template>
        <template #project="{ record }">
          <span class="project-badge" :title="record.projectName || record.projectKey || ''">{{ record.projectKey || '\u2014' }}</span>
        </template>
        <template #assignee="{ record }">
          <div @click.stop>
            <a-trigger v-if="canEditIssue(record)" v-model:popup-visible="assigneeDropdowns[record.id]" trigger="click" position="bl" :popup-offset="4">
              <span class="editable-cell" :class="{ 'unassigned-cell': !record.assigneeName }" @click="openAssigneeEdit(record)">
                <template v-if="record.assigneeName">{{ record.assigneeName }}</template>
                <span v-else class="unassigned-label">未分配</span>
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
                      <UserAvatar :name="m.displayName || '?'" :size="20" /><span>{{ m.displayName }}</span>
                    </div>
                  </template>
                </div>
              </template>
            </a-trigger>
            <span v-else class="readonly-cell" :class="{ 'unassigned-cell': !record.assigneeName }">
              <template v-if="record.assigneeName">{{ record.assigneeName }}</template>
              <span v-else class="unassigned-label">未分配</span>
            </span>
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
                      <span class="status-dot" :style="{ background: st.color }"></span><span>{{ st.transitionName || st.displayName || st.name }}</span>
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
              <span class="editable-cell" :class="{ 'sprint-completed': record.sprintStatus === 'completed' }" @click="openSprintEdit(record)">
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
            <span v-else class="readonly-cell" :class="{ 'sprint-completed': record.sprintStatus === 'completed' }">{{ getSprintName(record.sprintId, record.sprintName) || '\u2014' }}</span>
          </div>
        </template>
        <template #priority="{ record }">
          <div @click.stop>
            <a-trigger v-if="canEditIssue(record)" v-model:popup-visible="priorityDropdowns[record.id]" trigger="click" position="bl" :popup-offset="4">
              <span class="editable-cell" @click="priorityDropdowns[record.id] = true">
                <IssuePriorityBadge :priority="record.priority" :color="record.priorityColor" mode="dot" :show-label="true" />
                <icon-loading v-if="isCellEditing(record.id, 'priority')" class="cell-spinner" />
              </span>
              <template #content>
                <div class="inline-dropdown">
                  <div v-for="p in priorityOptions" :key="p.value" class="dropdown-item" @click="selectPriority(record, p.value)">
                    <IssuePriorityBadge :priority="p.value" :color="p.color ?? undefined" mode="dot" :show-label="true" />
                  </div>
                </div>
              </template>
            </a-trigger>
            <span v-else class="readonly-cell">
              <IssuePriorityBadge :priority="record.priority" :color="record.priorityColor" mode="dot" :show-label="true" />
            </span>
          </div>
        </template>
        <template #updatedAt="{ record }"><span class="time-ago">{{ formatTime(record.updatedAt) }}</span></template>
        <template #issueType="{ record }"><span class="type-label"><span class="type-color-dot" :style="{ background: record.issueTypeColor || getIssueTypeColorForRecord(record.issueType) }"></span>{{ getIssueTypeLabelForRecord(record.issueType) }}</span></template>
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

        <!-- Time tracking columns -->
        <template #estimatedHours="{ record }">
          <span class="time-ago">{{ formatHoursCell(record.estimatedHours) }}</span>
        </template>
        <template #spentHours="{ record }">
          <span class="time-ago" :class="getSpentHoursClass(record)">{{ formatHoursCell(record.spentHours) }}</span>
        </template>
        <template #remaining="{ record }">
          <span class="time-ago" :class="getRemainingClass(record)">{{ formatRemainingCell(record) }}</span>
        </template>

        <!-- Custom field columns (cf_ prefix) -->
        <template #customFieldCell="{ record, column }">
          <template v-if="getCustomFieldDetail(record, column.dataIndex)">
            <span v-if="getCustomFieldDetail(record, column.dataIndex)!.isMulti" class="cf-cell cf-multi">
              <span
                v-for="(dv, idx) in getCustomFieldDetail(record, column.dataIndex)!.displayValues"
                :key="idx"
                class="cf-tag"
                :style="getCustomFieldDetail(record, column.dataIndex)!.colors?.[idx] ? { background: getCustomFieldDetail(record, column.dataIndex)!.colors![idx]!, color: 'var(--tf-text-on-accent)' } : {}"
              >{{ dv }}</span>
            </span>
            <span
              v-else-if="getCustomFieldDetail(record, column.dataIndex)!.color"
              class="cf-cell cf-badge"
              :style="{ background: getCustomFieldDetail(record, column.dataIndex)!.color!, color: 'var(--tf-text-on-accent)' }"
            >{{ getCustomFieldDetail(record, column.dataIndex)!.displayValue || '\u2014' }}</span>
            <span v-else class="cf-cell">{{ getCustomFieldDetail(record, column.dataIndex)!.displayValue || '\u2014' }}</span>
          </template>
          <span v-else class="cf-cell">&#x2014;</span>
        </template>

        <!-- empty -->
        <template #empty>
          <EmptyState
            v-if="loadError"
            type="error"
            icon="close-circle"
            title="加载失败"
            description="无法获取工单列表，请检查网络连接或稍后重试"
          >
            <template #action>
              <a-button type="primary" size="small" @click="refreshList">
                <template #icon><icon-refresh /></template>
                重试
              </a-button>
            </template>
          </EmptyState>
          <EmptyState
            v-else
            icon="search"
            title="暂无工单"
            description="尝试调整筛选条件或创建新的工单"
          >
            <template #action>
              <a-button v-if="canCreateIssueGlobal" type="primary" size="small" @click="toggleInlineCreate">创建工单</a-button>
            </template>
          </EmptyState>
        </template>
      </a-table>
      </div>

      <!-- Issue list layout (List layout mode) -->
      <IssueListLayout
        v-if="isListLayout"
        :issues="issues"
        :density="density"
        :structure="structure"
        :loading="loading"
        :error="loadError"
        :sort-state="sortState"
        :active-issue-id="previewIssueId"
        :focused-issue-id="focusedIssueId"
        :selected-ids="selectedIds"
        :show-checkbox="canBatchOps"
        :draggable="isDraggable"
        :is-manual-sorted="isManualSorted"
        :is-owner-order="isOwnerOrder"
        :sorted-issue-ids="sortedIssueIds"
        :sprint-options-cache="sprintOptionsCache"
        :sprint-loading-ids="listSprintLoadingIds"
        :can-edit-issue="canEditIssue"
        :badge-fields-map="badgeFieldsMap"
        @item-click="onListItemClick"
        @item-dblclick="onListItemDblClick"
        @item-contextmenu="onListItemContextMenu"
        @sort-change="onListSortChange"
        @select="onListItemSelect"
        @order-change="onManualOrderChange"
        @discard-order="onDiscardManualOrder"
        @sprint-edit="onListSprintEdit"
        @sprint-select="onListSprintSelect"
        @retry="refreshList"
      />

      <!-- Pagination -->
      <div class="pagination-bar" v-if="totalIssues > 0">
        <a-pagination v-model:current="currentPage" :total="totalIssues" :page-size="pageSize" size="small" show-total show-page-size :page-size-options="[20, 50, 100, 200]" @change="goPage" @page-size-change="changePageSize" />
      </div>
    </section>

    <!-- Create issue panel -->
    <IssueCreatePanel ref="createPanelRef" v-model:visible="showCreatePanel" :project-id="activeProjectId || undefined" :draft-id="activeDraftId" @created="onCreatePanelCreated" @cancel-with-data="onCreatePanelCancel" @expand-to-fullscreen="onCreatePanelExpand" />

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

    <!-- Keyboard Shortcuts Help Panel (?) -->
    <KeyboardShortcutsHelp
      v-model:visible="showShortcutsHelp"
    />

    <!-- Issue Row Context Menu (Right-click) -->
    <div
      v-if="contextMenu.visible"
      class="issue-context-menu"
      :style="{ top: contextMenu.y + 'px', left: contextMenu.x + 'px' }"
      @click.stop
    >
      <!-- Copy issue key -->
      <div class="ctx-menu-item" @click="ctxCopyIssueKey">
        <icon-copy class="ctx-menu-icon" />
        <span>复制工单 ID</span>
        <span class="ctx-menu-hint">{{ contextMenu.issue?.issueKey }}</span>
      </div>
      <!-- Copy link -->
      <div class="ctx-menu-item" @click="ctxCopyLink">
        <icon-link class="ctx-menu-icon" />
        <span>复制工单链接</span>
      </div>
      <!-- Open in new tab -->
      <div class="ctx-menu-item" @click="ctxOpenNewTab">
        <icon-expand class="ctx-menu-icon" />
        <span>在新标签页打开</span>
      </div>

      <!-- Separator -->
      <div class="ctx-menu-separator"></div>

      <!-- Status transitions (only for editable issues) -->
      <template v-if="contextMenu.issue && canEditIssue(contextMenu.issue)">
        <div v-if="ctxTransitionsLoading" class="ctx-menu-item ctx-menu-loading">
          <a-spin :size="14" />
          <span>加载状态...</span>
        </div>
        <template v-else-if="ctxTransitions.length > 0">
          <div class="ctx-menu-label">变更状态</div>
          <div
            v-for="st in ctxTransitions"
            :key="st.id"
            class="ctx-menu-item"
            @click="ctxSetStatus(st)"
          >
            <span class="ctx-status-dot" :style="{ background: st.color }"></span>
            <span>{{ st.transitionName || st.displayName || st.name }}</span>
          </div>
          <div class="ctx-menu-separator"></div>
        </template>

        <!-- Move to Sprint -->
        <div class="ctx-menu-item ctx-menu-has-sub" @mouseenter="ctxLoadSprints" @click="ctxMoveSprint">
          <icon-calendar class="ctx-menu-icon" />
          <span>移至 Sprint</span>
          <icon-right class="ctx-menu-arrow" />
          <!-- Sprint sub-menu -->
          <div v-if="ctxSprintSubVisible" class="ctx-menu-submenu">
            <div v-if="ctxSprintsLoading" class="ctx-menu-item ctx-menu-loading">
              <a-spin :size="14" />
              <span>加载 Sprint...</span>
            </div>
            <template v-else>
              <div class="ctx-menu-item" @click.stop="ctxSelectSprint(null)">
                <span>无 Sprint</span>
              </div>
              <template v-for="group in ctxSprintGroups" :key="group.label">
                <div class="ctx-menu-group-label">{{ group.label }}</div>
                <div
                  v-for="s in group.items"
                  :key="s.id"
                  class="ctx-menu-item"
                  @click.stop="ctxSelectSprint(s)"
                >
                  <span>{{ s.name }}</span>
                </div>
              </template>
              <div v-if="ctxSprintGroups.length === 0" class="ctx-menu-empty">无可用 Sprint</div>
            </template>
          </div>
        </div>
      </template>
    </div>

    <!-- Context menu backdrop -->
    <div v-if="contextMenu.visible" class="ctx-menu-backdrop" @click="closeContextMenu" @contextmenu.prevent="closeContextMenu"></div>
  </div>
</template>

<script setup lang="ts">

import { ref, reactive, computed, onMounted, onActivated, onUnmounted, watch, h, nextTick, provide } from 'vue'
import { useRouter, useRoute, onBeforeRouteLeave } from 'vue-router'
import { IconLoading, IconCheckCircle, IconEye, IconLayout, IconExpand, IconDownload, IconFile, IconCode, IconCopy, IconLink, IconCalendar, IconRight } from '@arco-design/web-vue/es/icon'
import { Message, Modal } from '@arco-design/web-vue'
import { projectApi, issueApi, sprintApi, customFieldApi } from '@/api'
import type { IssueVO, IssueStatusVO, ProjectMemberVO, SprintVO, CustomFieldValueVO } from '@/api/types'
import type { TableData } from '@arco-design/web-vue'
import { useAuthStore } from '@/stores/auth'
import { localizeStatusName, queryFieldKeyToLabel } from '@/utils/fieldLabels'
import { highlightKeyword } from '@/utils/highlight'
import { DEFAULT_PRIORITY_OPTIONS, DEFAULT_PRIORITY_COLOR } from '@/composables/usePriorityOptions'
import { DEFAULT_ISSUE_TYPE_OPTIONS, DEFAULT_ISSUE_TYPE_COLOR } from './composables/useIssueTypeOptions'
import { extractVersion, showActionFeedback } from '@/utils/transition'
import { ERROR_CODES } from '@/api/error-codes'
import { IssuePriorityBadge, UserAvatar, EmptyState } from '@/components/base'
import {
  useIssueList, useSelection, useInlineEdit, useBatchOps, usePermission,
  useColumnConfig, useViewSettings, useManualOrder, useDrafts,
  useKeyboardNav, useContextMenu, useIssueExport,
  useDashboardFilter, useTableConfig
} from './composables'
import { loadPriorityOptions } from './composables/usePriorityOptions'
import { loadIssueTypeOptions } from './composables/useIssueTypeOptions'
import type { IssueDraft } from './composables'
import { usePermission as useProjectPermission } from '@/composables/usePermission'
import { useIssueProjectSubscription } from '@/composables/useWebSocket'
import { useNavBadge } from '@/composables/useNavBadge'
import type { IssueRealtimeEvent } from '@/composables/useWebSocket'
import { consumeSessionRecoveryDraft } from '@/utils/sessionEvents'
import BatchActionToolbar from '@/components/BatchActionToolbar.vue'
import RecentIssuesPanel from './components/RecentIssuesPanel.vue'
import DraggableColumnHeader from './components/DraggableColumnHeader.vue'
import IssueCreatePanel from '@/components/IssueCreatePanel.vue'
import IssuePreviewDrawer from '../board/components/IssuePreviewDrawer.vue'
import ColumnConfigPopover from './components/ColumnConfigPopover.vue'
import FilterBar from './components/FilterBar.vue'
import ApplyCommandDialog from './components/ApplyCommandDialog.vue'
import KeyboardShortcutsHelp from './components/KeyboardShortcutsHelp.vue'
import ViewSettingsMenu from './components/ViewSettingsMenu.vue'
import IssueListLayout from './components/IssueListLayout.vue'
import QueryPanel from './components/QueryPanel.vue'

const router = useRouter()
const route = useRoute()

// ===== QueryPanel ref =====
const queryPanelRef = ref<InstanceType<typeof QueryPanel> | null>(null)

// ===== Core composables =====
const {
  issues, totalIssues, currentPage, pageSize, loading, loadError,
  sortState, loadIssues, goPage, changePageSize, updateLocalIssue, removeLocalIssue
} = useIssueList()

const {
  selectedIds, selectedCount, selectedIssues,
  toggle, toggleAll, clearSelection
} = useSelection(issues)

const { isCellEditing, executeEdit } = useInlineEdit(issues)
const { batchTransitStatus, batchAssign, batchUpdateSprint, batchUpdatePriority, batchTagAdd, batchTagRemove, batchAddLink, batchDelete } = useBatchOps()
const { loadPermissions, canEditIssue, canDeleteIssue } = usePermission(issues)

const {
  layout, density, structure,
  isListLayout, isTableLayout,
  setLayout, setDensity, setStructure
} = useViewSettings()

const {
  isManualSorted, isOwnerOrder, manualOrderData,
  loadManualOrder, saveOrder: saveManualOrder, discardOrder: discardManualOrder, reset: resetManualOrder
} = useManualOrder()

// Shared state (declared early for composable dependencies)
const activeProjectId = ref<string | null>(null)

// Auth & permissions
const authStore = useAuthStore()

// 项目级权限：当选中特定项目时，加载该项目的权限用于精确控制 UI
const {
  canCreateIssue: projectCanCreate,
  hasPermission: hasActiveProjectPermission
} = useProjectPermission(() => activeProjectId.value || undefined)

// 用 computed 保持响应式，权限刷新后视图自动更新；deny-by-default（未加载时隐藏操作入口）
// 当选中特定项目时，使用项目级权限；未选中项目时使用全局导航权限
const canCreateIssueGlobal = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  if (!authStore.permissionsLoaded) return false
  if (activeProjectId.value) {
    // 选中特定项目：按该项目的 issue:create 权限判断
    return projectCanCreate.value
  }
  // 未选中项目（全部项目视图）：使用全局导航权限
  return authStore.canCreateIssue
})

const canBatchOps = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  if (!authStore.permissionsLoaded) return false
  if (activeProjectId.value) {
    // 选中特定项目：按该项目的具体写权限判断
    return hasActiveProjectPermission('issue:edit')
      || hasActiveProjectPermission('issue:delete')
      || hasActiveProjectPermission('issue:assign')
      || hasActiveProjectPermission('issue:change_status')
  }
  // 未选中项目：使用全局导航权限
  return authStore.hasGlobalPermission('nav:batch_ops')
})

const canViewSprintGlobal = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  if (!authStore.permissionsLoaded) return false
  if (activeProjectId.value) {
    return hasActiveProjectPermission('sprint:view')
  }
  return authStore.hasGlobalPermission('nav:sprint_view') || authStore.hasGlobalPermission('nav:sprint_manage')
})
const filterBarRef = ref<InstanceType<typeof FilterBar> | null>(null)
const filterProject = ref<string | undefined>(undefined)
const searchKeyword = ref('')
provide('searchKeyword', searchKeyword)
const globalFilterParams = ref<Record<string, any>>({})
const initialFilterChips = ref<any[]>([])
const statusCache = ref<IssueStatusVO[]>([])
const sprintOptionsCache = reactive<Record<string, SprintVO[]>>({})

// Priority & issue type options
const priorityOptions = ref<{ value: string; label: string; color: string | null; description: string | null; isDefault: boolean }[]>(DEFAULT_PRIORITY_OPTIONS.map(o => ({ ...o, description: null, isDefault: false })))
const issueTypeOptions = ref<{ value: string; label: string; color: string | null; description: string | null; isDefault: boolean }[]>(DEFAULT_ISSUE_TYPE_OPTIONS.map(o => ({ ...o, description: null, isDefault: false })))

// Column config
const {
  visibleColumns, toggleColumn, reorderColumn,
  standardColumns, customFieldColumns, isVisible: isColumnVisible, resetToDefault: resetColumns
} = useColumnConfig(activeProjectId as any)

// ===== 从 QueryPanel 暴露的共享状态（通过 ref 访问）=====
// activeQueryId / activeQueryName / activeQueryObj 仍需在父级维护，供 FilterBar / breadcrumb / selectQuery 等使用
const activeQueryId = ref<string | null>(null)
const activeQueryName = ref('所有工单')
const activeQueryObj = ref<any | null>(null)
const activeTagId = ref<string | null>(null)
// projectList 供 FilterBar / useDashboardFilter 使用，从 queryPanelRef 同步
// 注意：Vue 3 template ref proxy 会自动 unwrap 被 defineExpose 暴露的 Ref，
// 因此直接访问 .projectList 即可获取数组值，无需再 .value
const projectList = computed<Array<{ id: string; name: string; key: string; favorited?: boolean }>>(
  () => {
    const panel = queryPanelRef.value as any
    if (!panel) return []
    const list = panel.projectList
    // 兼容：如果拿到的仍是 Ref 对象（极端时序下未被代理 unwrap），取 .value
    return Array.isArray(list) ? list : (list?.value ?? [])
  }
)

// Hide resolved toggle（仍在父级，同时传给 QueryPanel 和 useIssueExport）
const HIDE_RESOLVED_KEY = 'trackflow:hide-resolved'
const hideResolved = ref(localStorage.getItem(HIDE_RESOLVED_KEY) === 'true')

function toggleHideResolved() {
  hideResolved.value = !hideResolved.value
  localStorage.setItem(HIDE_RESOLVED_KEY, String(hideResolved.value))
  currentPage.value = 1
  refreshList()
  queryPanelRef.value?.loadPanel()
}

// ===== Export composable =====
const { exportLoading, handleExport, onBatchExport } = useIssueExport({
  activeProjectId, filterProject, hideResolved, globalFilterParams, searchKeyword, selectedIds
})

// ===== Dashboard Filter composable =====
const { applyDashboardFilter, hasDashboardFilterParams } = useDashboardFilter({
  activeQueryId, activeQueryObj, activeProjectId, filterProject,
  searchKeyword, globalFilterParams, initialFilterChips, activeQueryName,
  statusCache, sprintOptionsCache, projectList, filterBarRef, refreshList
})

// ===== Preview mode =====
const PREVIEW_MODE_KEY = 'trackflow:preview-mode'
type PreviewMode = 'sidebar' | 'off'
const previewMode = ref<PreviewMode>((localStorage.getItem(PREVIEW_MODE_KEY) as PreviewMode) || 'off')
const previewVisible = ref(false)
const previewIssueId = ref<string | null>(null)
const activeIssueIndex = ref<number>(-1)

function setPreviewMode(mode: PreviewMode) {
  previewMode.value = mode
  localStorage.setItem(PREVIEW_MODE_KEY, mode)
  if (mode === 'off') { previewVisible.value = false; previewIssueId.value = null; activeIssueIndex.value = -1 }
}
function openPreview(issue: IssueVO, index: number) { previewIssueId.value = issue.id; previewVisible.value = true; activeIssueIndex.value = index }
function closePreview() { previewVisible.value = false; activeIssueIndex.value = -1 }
function onPreviewVisibleChange(val: boolean) { previewVisible.value = val; if (!val) activeIssueIndex.value = -1 }
function onPreviewGoDetail(issueId: string) { previewVisible.value = false; const query: Record<string, string> = {}; if (activeQueryId.value && activeQueryName.value && activeQueryName.value !== '所有工单') { query.fromQuery = activeQueryId.value; query.fromQueryName = activeQueryName.value }; router.push({ name: 'IssueDetail', params: { id: issueId }, query }) }

// ===== Keyboard Nav composable =====
const showCommandDialog = ref(false)
const showShortcutsHelp = ref(false)
const showCreatePanel = ref(false)

const {
  focusedIndex, focusedIssueId, handleKeyboardNav,
} = useKeyboardNav({
  issues, canCreateIssueGlobal, canBatchOps,
  previewMode, previewVisible, previewIssueId, activeIssueIndex,
  showCommandDialog, showCreatePanel, showShortcutsHelp, selectedCount,
  toggle, toggleAll, openPreview, closePreview, onPreviewGoDetail,
  buildDetailRoute: buildIssueDetailRoute
})

// ===== Context Menu composable =====
const {
  contextMenu, ctxTransitions, ctxTransitionsLoading,
  ctxSprintsLoading, ctxSprintSubVisible, ctxSprintGroups,
  closeContextMenu,
  onListItemContextMenu, onTableRowContextMenu,
  ctxCopyIssueKey, ctxCopyLink, ctxOpenNewTab,
  ctxSetStatus, ctxLoadSprints, ctxMoveSprint, ctxSelectSprint,
  onGlobalKeydownCtx
} = useContextMenu({ refreshList, updateLocalIssue, sprintOptionsCache, canEditIssue })

// ===== Table Config composable =====
const {
  tableMinWidth, tableColumns, rowSelection,
  onColumnResize, onHeaderSort, onHeaderRemove, onHeaderDragDrop,
  getColumnSortDir, isColumnFixed, isColumnSortable,
  getStatusName, getStatusColor, getSprintName,
  getDueDateStatus, getDueDateTooltip, getRowClass,
  formatHoursCell, formatRemainingCell, getSpentHoursClass, getRemainingClass
} = useTableConfig({
  visibleColumns, canBatchOps, statusCache, sortState,
  previewMode, previewVisible, previewIssueId, focusedIssueId,
  sprintOptionsCache, toggleColumn, reorderColumn
})

// ===== Drafts =====
const { saveDraft, deleteDraft } = useDrafts()
const activeDraftId = ref<string | null>(null)
const recoveredDraftId = ref<string | null>(null)

function openDraftCreate() { activeDraftId.value = null; showCreatePanel.value = true }
function openDraft(draft: IssueDraft) { activeDraftId.value = draft.id; showCreatePanel.value = true }
function onCreatePanelCancel(formData: any) {
  if (formData && (formData.title?.trim() || formData.description?.trim())) { saveDraft(formData, activeDraftId.value || undefined); Message.info('已保存为草稿') }
  activeDraftId.value = null
}
function onCreatePanelCreated() { if (activeDraftId.value) { deleteDraft(activeDraftId.value); activeDraftId.value = null }; refreshList() }
function onCreatePanelExpand(formData: any) {
  showCreatePanel.value = false; activeDraftId.value = null
  if (formData && (formData.title?.trim() || formData.description?.trim())) { const draftId = saveDraft(formData); if (draftId) { router.push({ name: 'IssueCreate', query: { draftId } }); return } }
  router.push({ name: 'IssueCreate' })
}
const createPanelRef = ref<InstanceType<typeof IssueCreatePanel> | null>(null)


// ===== Inline Edit State =====
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

// Badge fields
import type { BadgeFieldConfig, BadgeColorRule } from './components/badgeTypes'
const badgeFieldsMap = reactive<Record<string, BadgeFieldConfig[]>>({})
const badgeFieldsLoadedProjects = new Set<string>()

async function loadBadgeFields(projectIds: string[]) {
  const toLoad = projectIds.filter(pid => pid && !badgeFieldsLoadedProjects.has(pid))
  if (toLoad.length === 0) return
  for (const pid of toLoad) {
    badgeFieldsLoadedProjects.add(pid)
    try {
      const res = await customFieldApi.listByProject(pid)
      const fields = (res.data || []).filter((f: any) => f.showAsBadge && (f.fieldFormat === 'int' || f.fieldFormat === 'integer'))
      if (fields.length > 0) {
        badgeFieldsMap[pid] = fields.slice(0, 2).map((f: any) => {
          let colorRules: BadgeColorRule[] | null = null
          if (f.badgeColorRules) { try { colorRules = JSON.parse(f.badgeColorRules) } catch { /* ignore */ } }
          return { fieldId: f.id, fieldName: f.name, colorRules }
        })
      }
    } catch { /* non-critical */ }
  }
}
function loadBadgeFieldsForIssues() {
  const projectIds = [...new Set(issues.value.map(i => i.projectId).filter(Boolean))]
  if (projectIds.length > 0) loadBadgeFields(projectIds)
}

// Load priority/type options when project changes
watch(activeProjectId, async (projectId) => {
  if (projectId) {
    const loaded = await loadPriorityOptions(projectId)
    priorityOptions.value = loaded.map(o => ({ value: o.value, label: o.label, color: o.color || DEFAULT_PRIORITY_COLOR, description: o.description ?? null, isDefault: o.isDefault ?? false }))
    const loadedTypes = await loadIssueTypeOptions(projectId)
    issueTypeOptions.value = loadedTypes.map(o => ({ value: o.value, label: o.label, color: o.color || DEFAULT_ISSUE_TYPE_COLOR, description: o.description ?? null, isDefault: o.isDefault ?? false }))
  }
}, { immediate: true })

function getIssueTypeColorForRecord(issueType: string | null | undefined): string {
  const t = issueType || '任务'
  const opt = issueTypeOptions.value.find(o => o.value === t || o.value.toLowerCase() === t.toLowerCase())
  return opt?.color || DEFAULT_ISSUE_TYPE_COLOR
}
function getIssueTypeLabelForRecord(issueType: string | null | undefined): string {
  if (!issueType) return '未知'
  const opt = issueTypeOptions.value.find(o => o.value === issueType || o.value.toLowerCase() === issueType.toLowerCase())
  return opt?.label || issueType
}

// ===== Inline Edit Handlers =====
async function openStatusEdit(issue: IssueVO) {
  if (isCellEditing(issue.id, 'statusId')) return
  statusDropdowns[issue.id] = true
  transitionsLoading[issue.id] = true
  try { const res = await issueApi.getAvailableTransitions(issue.id); availableTransitions[issue.id] = res.data || [] }
  catch { availableTransitions[issue.id] = []; Message.error({ content: '获取可用状态失败', duration: 3000 }); statusDropdowns[issue.id] = false }
  finally { transitionsLoading[issue.id] = false }
}

function selectStatus(issue: IssueVO, status: IssueStatusVO) {
  statusDropdowns[issue.id] = false
  if (status.requireComment) {
    let commentText = ''
    Modal.confirm({
      title: '状态变更 — 请填写理由',
      content: () => h('div', { style: 'display:flex;flex-direction:column;gap:8px' }, [
        h('div', { style: 'display:flex;align-items:center;gap:6px' }, [
          h('span', { style: 'color:var(--color-text-3);font-size:13px' }, '目标状态：'),
          h('span', { style: `background:${status.color};color: var(--tf-text-on-accent);padding:2px 8px;border-radius:3px;font-size:12px` }, status.displayName || status.name)
        ]),
        h('textarea', { placeholder: '请说明退回/变更的原因（必填）', style: 'width:100%;min-height:80px;margin-top:8px;padding:8px;border:1px solid var(--color-border-2);border-radius:4px;resize:vertical;font-size:13px;background:var(--color-bg-2);color:var(--color-text-1)', onInput: (e: Event) => { commentText = (e.target as HTMLTextAreaElement).value } })
      ]),
      okText: '确认变更', cancelText: '取消', width: 480,
      onBeforeOk: () => { if (!commentText.trim()) { Message.warning('请填写变更理由'); return false }; return true },
      onOk: () => { performStatusTransition(issue, status, commentText.trim()) }
    })
  } else { performStatusTransition(issue, status, undefined) }
}

async function performStatusTransition(issue: IssueVO, status: IssueStatusVO, comment?: string, forceFlags?: { force?: boolean; forceWip?: boolean; forceDescEmpty?: boolean }) {
  const oldStatusId = issue.statusId
  issue.statusId = status.id
  try {
    const res = await issueApi.transitStatus(issue.id, status.id, comment, issue.version, forceFlags?.force, forceFlags?.forceWip, forceFlags?.forceDescEmpty)
    if (res.code === 0) {
      const actionResult = res.data?.actionResult
      if (actionResult?.outcome === 'FIELD_VALIDATION_FAILED') {
        issue.statusId = oldStatusId
        Modal.warning({ title: '字段校验', content: actionResult.warningMessage || `请先填写「${actionResult.requiredFieldName}」字段`, okText: '打开详情填写', cancelText: '知道了', hideCancel: false, onOk: () => { router.push(`/issues/${issue.issueKey}`) } })
        return
      }
      if (res.data != null) { const version = extractVersion(res.data); if (version != null) issue.version = version; else issue.version = (issue.version || 0) + 1; showActionFeedback(res.data) }
      onInlineEditSuccess(issue, 'statusId', status.id)
      return
    }
    issue.statusId = oldStatusId
    if (res.code === ERROR_CODES.DESCRIPTION_EMPTY_WARNING) { Modal.warning({ title: '工单描述为空', content: res.message, okText: '继续变更', cancelText: '取消', hideCancel: false, onOk: () => performStatusTransition(issue, status, comment, { ...forceFlags, forceDescEmpty: true }) }) }
    else if (res.code === ERROR_CODES.WIP_LIMIT_EXCEEDED) { Modal.warning({ title: 'WIP 限制', content: res.message, okText: '继续移入', cancelText: '取消', hideCancel: false, onOk: () => performStatusTransition(issue, status, comment, { ...forceFlags, forceWip: true }) }) }
    else if (res.code === ERROR_CODES.CLOSE_CONFIRMATION_REQUIRED) { Modal.warning({ title: '确认关闭', content: res.message, okText: '强制关闭', cancelText: '取消', hideCancel: false, onOk: () => performStatusTransition(issue, status, comment, { ...forceFlags, force: true }) }) }
    else { Message.error({ content: res.message || '状态变更失败', duration: 3000 }) }
  } catch (e: any) { issue.statusId = oldStatusId; Message.error({ content: e.response?.data?.message || '状态变更失败', duration: 3000 }) }
}

async function openAssigneeEdit(issue: IssueVO) {
  if (isCellEditing(issue.id, 'assigneeId')) return
  assigneeDropdowns[issue.id] = true; assigneeSearch.value = ''; assigneeOptionsLoading.value = true
  try { const res = await projectApi.listAssignableMembers(issue.projectId); assigneeOptions.value = res.data || [] }
  catch { assigneeOptions.value = [] }
  finally { assigneeOptionsLoading.value = false }
}
const filteredAssigneeOptions = computed(() => {
  if (!assigneeSearch.value) return assigneeOptions.value
  const kw = assigneeSearch.value.toLowerCase()
  return assigneeOptions.value.filter(m => m.displayName?.toLowerCase().includes(kw))
})
function selectAssignee(issue: IssueVO, member: ProjectMemberVO | null) {
  Object.keys(assigneeDropdowns).forEach(k => { assigneeDropdowns[k] = false })
  executeEdit(issue.id, 'assigneeId', member?.userId || null, (_signal) => issueApi.assign(issue.id, member?.userId || ''), () => ({ assigneeId: member?.userId || undefined, assigneeName: member?.displayName || undefined }), onInlineEditSuccess)
}

async function openSprintEdit(issue: IssueVO) {
  if (isCellEditing(issue.id, 'sprintId')) return
  sprintDropdowns[issue.id] = true
  if (!sprintOptionsCache[issue.projectId]) {
    sprintOptionsLoading[issue.id] = true
    try { const res = await sprintApi.listByProject(issue.projectId, { _silent403: true }); sprintOptionsCache[issue.projectId] = res.data?.list || [] }
    catch { sprintOptionsCache[issue.projectId] = [] }
    finally { sprintOptionsLoading[issue.id] = false }
  }
}
function getSprintGroups(projectId: string) {
  const sprints = sprintOptionsCache[projectId] || []
  const groups: { label: string; items: SprintVO[] }[] = []
  const active = sprints.filter(s => s.status?.toLowerCase() === 'active')
  const planned = sprints.filter(s => s.status?.toLowerCase() === 'planned')
  const completed = sprints.filter(s => s.status?.toLowerCase() === 'completed')
  if (active.length) groups.push({ label: '进行中', items: active })
  if (planned.length) groups.push({ label: '计划中', items: planned })
  if (completed.length) groups.push({ label: '已完成', items: completed })
  return groups
}
function selectSprint(issue: IssueVO, sprint: SprintVO | null) { sprintDropdowns[issue.id] = false; const sprintVal = sprint?.id || '0'; executeEdit(issue.id, 'sprintId', sprint?.id || null, (_signal) => issueApi.update(issue.id, { sprintId: sprintVal, version: issue.version }), undefined, onInlineEditSuccess) }
function selectPriority(issue: IssueVO, priority: string) { priorityDropdowns[issue.id] = false; executeEdit(issue.id, 'priority', priority, (_signal) => issueApi.update(issue.id, { priority, version: issue.version }), undefined, onInlineEditSuccess) }

// List layout Sprint inline edit
const listSprintLoadingIds = reactive<Set<string>>(new Set())
async function onListSprintEdit(issue: IssueVO) {
  if (!issue.projectId) return
  if (sprintOptionsCache[issue.projectId] !== undefined) return
  listSprintLoadingIds.add(issue.id)
  try { const res = await sprintApi.listByProject(issue.projectId, { _silent403: true }); sprintOptionsCache[issue.projectId] = res.data?.list || [] }
  catch { sprintOptionsCache[issue.projectId] = [] }
  finally { listSprintLoadingIds.delete(issue.id) }
}
function onListSprintSelect(issue: IssueVO, sprint: SprintVO | null) {
  const newSprintId = sprint?.id ?? null; const newSprintName = sprint?.name ?? null
  const apiSprintId = sprint?.id || '0'
  executeEdit(issue.id, 'sprintId', newSprintId, (_signal) => issueApi.update(issue.id, { sprintId: apiSprintId, version: issue.version }), (_iss) => ({ sprintId: newSprintId ?? undefined, sprintName: newSprintName ?? undefined }), onInlineEditSuccess)
}

// ===== Batch Operations =====
async function onBatchState(statusId: string) { const result = await batchTransitStatus(selectedIssues.value, statusId); if (result.succeeded > 0) { selectedIssues.value.forEach(issue => { if (!result.failures.find(f => f.issueId === issue.id)) updateLocalIssue(issue.id, { statusId }) }); useNavBadge().refresh() }; clearSelection() }
async function onBatchAssign(assigneeId: string | null) { await batchAssign(selectedIssues.value, assigneeId || ''); refreshList(); clearSelection() }
async function onBatchSprint(sprintId: string | null) { const result = await batchUpdateSprint(selectedIssues.value, sprintId); if (result.succeeded > 0) selectedIssues.value.forEach(issue => { if (!result.failures.find(f => f.issueId === issue.id)) updateLocalIssue(issue.id, { sprintId: sprintId || undefined }) }); clearSelection() }
async function onBatchPriority(priority: string) { const result = await batchUpdatePriority(selectedIssues.value, priority); if (result.succeeded > 0) selectedIssues.value.forEach(issue => { if (!result.failures.find(f => f.issueId === issue.id)) updateLocalIssue(issue.id, { priority }) }); clearSelection() }
async function onBatchTagAdd(tagId: string) { const result = await batchTagAdd(selectedIssues.value, tagId); if (result.succeeded > 0) refreshList(); clearSelection() }
async function onBatchTagRemove(tagId: string) { const result = await batchTagRemove(selectedIssues.value, tagId); if (result.succeeded > 0) refreshList(); clearSelection() }
async function onBatchLink(linkType: string, targetIssueId: string) { const result = await batchAddLink(selectedIssues.value, linkType, targetIssueId); if (result.succeeded > 0) refreshList(); clearSelection() }
async function onBatchDelete() { const result = await batchDelete(selectedIssues.value); if (result.succeeded > 0) refreshList(); clearSelection() }
function onCommandExecuted() { refreshList(); clearSelection() }

// ===== Filter match check =====
function checkIssueMatchesFilter(issue: IssueVO): boolean {
  const fp = globalFilterParams.value
  if (fp.assigneeId === 'none' && issue.assigneeId) return false
  if (fp.assigneeId && fp.assigneeId !== 'none' && issue.assigneeId !== fp.assigneeId) return false
  if (fp.statusId) { const statusIds = String(fp.statusId).split(','); if (!statusIds.includes(String(issue.statusId))) return false }
  if (fp.sprintId) { if (fp.sprintId === 'none' && issue.sprintId) return false; if (fp.sprintId !== 'none' && issue.sprintId !== fp.sprintId) return false }
  if (fp.priority && issue.priority !== fp.priority) return false
  if (fp.issueType && issue.issueType !== fp.issueType) return false
  if ((fp.hideResolved === 'true' || hideResolved.value) && statusCache.value.find(s => s.id === issue.statusId)?.isClosed) return false
  return true
}
function onInlineEditSuccess(issue: IssueVO, field: string, _newValue: any) {
  if (!checkIssueMatchesFilter(issue)) removeLocalIssue(issue.id)
  if (field === 'statusId') useNavBadge().refresh()
}

// ===== Helpers =====
const selectedKeysArray = computed(() => [...selectedIds.value])
function getCustomFieldDetail(record: any, dataIndex: string): CustomFieldValueVO | undefined {
  if (!record.customFieldDetails || !dataIndex?.startsWith('cf_')) return undefined
  const fieldId = dataIndex.substring(3)
  return record.customFieldDetails.find((d: CustomFieldValueVO) => d.customFieldId === fieldId)
}
function formatTime(dt: string) { if (!dt) return ''; const d = new Date(dt); const now = new Date(); const diff = now.getTime() - d.getTime(); const mins = Math.floor(diff / 60000); if (mins < 60) return `${mins}分钟前`; const hours = Math.floor(mins / 60); if (hours < 24) return `${hours}小时前`; const days = Math.floor(hours / 24); if (days < 30) return `${days}天前`; return d.toLocaleDateString('zh-CN') }

// ===== Quick Create =====
const showInlineCreate = ref(false)
const quickCreating = ref(false)
const quickForm = reactive({ projectId: undefined as string | undefined, title: '', issueType: '任务', priority: '普通' })
const QUICK_CREATE_PROJECT_KEY = 'trackflow:quick-create-project'

function resolveQuickCreateProject(): string | undefined {
  if (activeProjectId.value) return activeProjectId.value
  if (filterProject.value) return filterProject.value
  const lastUsed = localStorage.getItem(QUICK_CREATE_PROJECT_KEY)
  if (lastUsed && projectList.value.some(p => p.id === lastUsed)) return lastUsed
  if (projectList.value.length === 1) return projectList.value[0].id
  return undefined
}
function toggleInlineCreate() { showInlineCreate.value = !showInlineCreate.value; if (showInlineCreate.value) quickForm.projectId = resolveQuickCreateProject() }
async function quickCreate() {
  if (!quickForm.projectId) { Message.warning('请先选择项目'); return }
  if (!quickForm.title.trim()) { Message.warning('请输入工单标题'); return }
  quickCreating.value = true
  try { await issueApi.create({ projectId: quickForm.projectId, title: quickForm.title.trim(), issueType: quickForm.issueType, priority: quickForm.priority }); Message.success('工单创建成功'); localStorage.setItem(QUICK_CREATE_PROJECT_KEY, quickForm.projectId); quickForm.title = ''; refreshList() }
  catch (e: any) { Message.error(e.response?.data?.message || '创建失败') }
  finally { quickCreating.value = false }
}


// ===== WebSocket realtime updates =====
const hasNewUpdates = ref(false)
useIssueProjectSubscription(
  () => activeProjectId.value,
  (event: IssueRealtimeEvent) => {
    // 过滤自己的操作：优先用数据库 userId（精确），未同步时降级用 Keycloak id（不可靠，保守不过滤）
    const myUserId = authStore.user?.userId
    if (myUserId && String(event.operatorId) === String(myUserId)) return

    if (event.action === 'FIELD_UPDATED') {
      const idx = issues.value.findIndex(i => String(i.id) === String(event.issueId))
      if (idx !== -1) {
        // 局部更新：只修改变更的字段，避免全量刷新导致列表闪烁
        const patch: Partial<IssueVO> = {}
        for (const [key, value] of Object.entries(event.changes)) {
          ;(patch as any)[key] = value
        }
        updateLocalIssue(String(event.issueId), patch)
      } else {
        // 不在当前视图中（可能被筛选条件过滤），提示有新内容
        hasNewUpdates.value = true
      }
    } else if (event.action === 'TAG_CHANGED') {
      // 标签变更需要重新拉取（标签数组结构复杂，不做局部 patch）
      hasNewUpdates.value = true
    } else if (event.action === 'CREATED') {
      hasNewUpdates.value = true
    } else if (event.action === 'DELETED') {
      const idx = issues.value.findIndex(i => String(i.id) === String(event.issueId))
      if (idx !== -1) {
        issues.value.splice(idx, 1)
        totalIssues.value = Math.max(0, totalIssues.value - 1)
      }
    }
  }
)
function onRefreshForUpdates() { hasNewUpdates.value = false; refreshList() }

// ===== Navigation & Selection Computed =====
const isDraggable = computed(() => isListLayout.value && (!!activeProjectId.value || !!activeQueryId.value))
const sortedIssueIds = computed(() => manualOrderData.value?.issueIds || [])

/**
 * Whether a dashboard/external filter context is active (came from Sprint/Dashboard/Report drill-down)
 * Distinct from saved queries: no activeQueryId, but globalFilterParams is non-empty and
 * the activeQueryName has been overridden to a descriptive label (not default "所有工单").
 */
const isDashboardFilterActive = computed(() => {
  if (activeQueryId.value) return false
  return Object.keys(globalFilterParams.value).length > 0 && activeQueryName.value !== '所有工单'
})

const activeQueryOwned = computed(() => {
  if (!activeQueryObj.value) return false
  const currentUserId = String(authStore.user?.userId || authStore.user?.id || '')
  return activeQueryObj.value.userId === currentUserId && !activeQueryObj.value.shared
})
const activeProjectName = computed(() => { if (!activeProjectId.value) return ''; const p = projectList.value.find(pr => pr.id === activeProjectId.value); return p?.name || '' })

const activeQueryProjectName = computed(() => {
  if (!activeQueryObj.value) return ''
  const filtersRaw = activeQueryObj.value.filters
  if (!filtersRaw) return ''
  let filters: any[]
  if (typeof filtersRaw === 'string') { try { filters = JSON.parse(filtersRaw) } catch { return '' } } else { filters = filtersRaw }
  if (!Array.isArray(filters)) return ''
  const projectFilter = filters.find((f: any) => f.field === 'project')
  if (!projectFilter || !projectFilter.value || projectFilter.value.length === 0) return ''
  const p = projectList.value.find(pr => pr.id === projectFilter.value[0])
  return p?.name || ''
})
function navigateToQueryProject() {
  if (!activeQueryObj.value) return
  const filtersRaw = activeQueryObj.value.filters; if (!filtersRaw) return
  let filters: any[]; if (typeof filtersRaw === 'string') { try { filters = JSON.parse(filtersRaw) } catch { return } } else { filters = filtersRaw }
  if (!Array.isArray(filters)) return
  const projectFilter = filters.find((f: any) => f.field === 'project')
  if (!projectFilter || !projectFilter.value || projectFilter.value.length === 0) return
  const p = projectList.value.find(pr => pr.id === projectFilter.value[0])
  if (p) selectProject(p)
}

const activeQueryReadonlyLabels = computed<string[]>(() => {
  // When dashboard filter is active (no saved query but filters from URL), show chip labels
  if (!activeQueryObj.value && isDashboardFilterActive.value && initialFilterChips.value.length > 0) {
    return initialFilterChips.value.map((c: any) => {
      const labels = c.valueLabels?.join(', ') || c.values?.join(', ') || ''
      return labels
    }).filter((l: string) => l && l.trim())
  }
  if (!activeQueryObj.value?.filters) return []
  let filters: any[]; if (typeof activeQueryObj.value.filters === 'string') { try { filters = JSON.parse(activeQueryObj.value.filters) } catch { return [] } } else { filters = activeQueryObj.value.filters }
  if (!Array.isArray(filters)) return []
  const operatorLabels: Record<string, string> = { eq: '=', neq: '≠', in: '∈', not_in: '∉', contains: '包含', open: '未关闭' }
  // Build status code/id → displayName map for readable labels
  const statusCodeMap: Record<string, string> = {}
  const statusIdMap: Record<string, string> = {}
  for (const s of statusCache.value) {
    if (s.code) statusCodeMap[s.code] = s.displayName || s.name
    if (s.id) statusIdMap[s.id] = s.displayName || s.name
  }
  return filters.map((f: any) => {
    const fieldLabel = queryFieldKeyToLabel[f.field] || f.field; const op = f.operator
    if (op === 'open') return `${fieldLabel}: 未关闭`
    let values: string
    if (Array.isArray(f.value)) {
      values = f.value.map((v: string) => {
        if (v === '${currentUser}') return '我'
        if (f.field === 'type') return getIssueTypeLabelForRecord(v)
        if (f.field === 'status') return statusCodeMap[v] || statusIdMap[v] || localizeStatusName(v) || v
        if (f.field === 'priority') return v
        return v
      }).join(', ')
    } else { values = String(f.value || '') }
    const opLabel = (op && op !== 'eq') ? ` ${operatorLabels[op] || op}` : ':'
    return `${fieldLabel}${opLabel} ${values}`
  }).filter(l => l && l.trim())
})

/**
 * 将 Saved Query 的筛选条件解析为 FilterBar 可消费的 InitialFilter[] 格式。
 * 当用户在 Saved Query 激活状态下切换到筛选模式时，FilterBar 使用此数据预填 chips。
 */
const activeQueryParsedFilters = computed(() => {
  if (!activeQueryObj.value?.filters) return []
  let filters: any[]
  if (typeof activeQueryObj.value.filters === 'string') {
    try { filters = JSON.parse(activeQueryObj.value.filters) } catch { return [] }
  } else {
    filters = activeQueryObj.value.filters
  }
  if (!Array.isArray(filters)) return []

  const result: Array<{ fieldKey: string; operator: string; values: string[]; valueLabels?: string[] }> = []

  for (const f of filters) {
    const rawValues = Array.isArray(f.value) ? f.value : (f.value ? [String(f.value)] : [])

    // Map saved query field names to FilterBar field keys
    let fieldKey = f.field
    if (fieldKey === 'type') fieldKey = 'issueType'

    // Map saved query operators to FilterBar operators
    let operator = 'is'
    switch (f.operator) {
      case 'eq': operator = 'is'; break
      case 'neq': operator = 'is_not'; break
      case 'in': operator = rawValues.length > 1 ? 'any_of' : 'is'; break
      case 'not_in': operator = 'none_of'; break
      case 'open':
        // "open" means status is not closed — filter by non-closed statuses
        {
          const openStatuses = statusCache.value.filter(s => !s.isClosed)
          if (openStatuses.length > 0) {
            result.push({
              fieldKey: 'status',
              operator: 'any_of',
              values: openStatuses.map(s => s.id),
              valueLabels: openStatuses.map(s => s.displayName || s.name)
            })
          }
        }
        continue  // skip the default push below
      default: operator = 'is'
    }

    // Map values: handle special markers like '${currentUser}'
    const values = rawValues.map((v: string) => {
      if (v === '${currentUser}') return 'me'
      return v
    })

    // Build human-readable labels
    const valueLabels = rawValues.map((v: string) => {
      if (v === '${currentUser}') return '我'
      if (fieldKey === 'status') {
        const s = statusCache.value.find(st => st.id === v || st.code === v)
        return s ? (s.displayName || s.name) : v
      }
      if (fieldKey === 'priority') return v
      if (fieldKey === 'issueType') return getIssueTypeLabelForRecord(v)
      if (fieldKey === 'project') {
        const p = projectList.value.find(pr => pr.id === v)
        return p ? `${p.key} - ${p.name}` : v
      }
      return v
    })

    result.push({ fieldKey, operator, values, valueLabels })
  }

  return result
})

// ===== Coordination Functions =====
function buildFilters() {
  const filters: Record<string, any> = {}
  if (activeProjectId.value) filters.projectId = activeProjectId.value
  if (filterProject.value) filters.projectId = filterProject.value
  if (activeQueryId.value) filters.queryId = activeQueryId.value
  if (searchKeyword.value.trim()) filters.keyword = searchKeyword.value.trim()
  if (hideResolved.value) filters.hideResolved = 'true'
  Object.assign(filters, globalFilterParams.value)
  return filters
}
function refreshList() { loadIssues(buildFilters()).then(() => { loadPermissions(); preloadSprintNames(); loadBadgeFieldsForIssues() }) }

async function preloadSprintNames() {
  if (!canViewSprintGlobal.value) return
  const projectIds = [...new Set(issues.value.map(i => i.projectId).filter(Boolean))]
  const toLoad = projectIds.filter(pid => !sprintOptionsCache[pid])
  await Promise.all(toLoad.map(async (pid) => { try { const res = await sprintApi.listByProject(pid, { _silent403: true }); sprintOptionsCache[pid] = res.data?.list || [] } catch { sprintOptionsCache[pid] = [] } }))
}

// ===== Search & Filter =====
function onGlobalSearch(keyword: string) {
  searchKeyword.value = keyword; globalFilterParams.value = {}; currentPage.value = 1
  // Sync keyword to URL (preserve project param)
  const newQuery: Record<string, string> = {}
  if (route.query.project) newQuery.project = String(route.query.project)
  if (keyword.trim()) newQuery.keyword = keyword.trim()
  skipRouteQueryWatch = true
  router.replace({ query: newQuery })
  nextTick(() => { skipRouteQueryWatch = false })
  refreshList()
}
function onGlobalFilter(filters: Record<string, any>) {
  searchKeyword.value = ''; globalFilterParams.value = filters
  if (activeQueryId.value) { activeQueryId.value = null; activeQueryName.value = '所有工单'; activeQueryObj.value = null }
  currentPage.value = 1
  // Sync filter params to URL (preserve project param)
  syncFiltersToUrl(filters)
  refreshList()
}

/**
 * Sync the current filter params to URL query string.
 * This enables page refresh persistence and URL sharing.
 */
function syncFiltersToUrl(filters: Record<string, any>) {
  const newQuery: Record<string, string> = {}
  if (route.query.project) newQuery.project = String(route.query.project)
  // Write all non-empty filter values to URL
  for (const [key, value] of Object.entries(filters)) {
    if (value != null && value !== '' && value !== undefined) {
      newQuery[key] = String(value)
    }
  }
  skipRouteQueryWatch = true
  router.replace({ query: newQuery })
  nextTick(() => { skipRouteQueryWatch = false })
}
function onClearQuery() {
  activeQueryId.value = null; activeQueryName.value = '所有工单'; activeQueryObj.value = null; searchKeyword.value = ''; globalFilterParams.value = {}; initialFilterChips.value = []; currentPage.value = 1
  skipRouteQueryWatch = true
  const { project } = route.query; router.replace({ query: project ? { project } : {} })
  nextTick(() => { skipRouteQueryWatch = false })
  filterBarRef.value?.clearAll()
  localStorage.setItem('tf_last_active_query_all', 'true'); localStorage.removeItem('tf_last_active_query_id')
  refreshList()
}
function onQueryChipClick() {
  // 编辑查询的能力已封装在 QueryPanel 内部，通过暴露的 ref 暂不处理（chip click 保持原样）
}

function selectQuery(q: any) {
  activeQueryId.value = q.id; activeQueryName.value = q.name; activeQueryObj.value = q; activeProjectId.value = null; activeTagId.value = null; filterProject.value = undefined; searchKeyword.value = ''; globalFilterParams.value = {}; currentPage.value = 1
  skipRouteQueryWatch = true
  const { project, ...rest } = route.query; router.replace({ query: rest })
  nextTick(() => { skipRouteQueryWatch = false })
  // Apply saved query sort criteria
  if (q.sortCriteria) { try { const criteria = typeof q.sortCriteria === 'string' ? JSON.parse(q.sortCriteria) : q.sortCriteria; if (Array.isArray(criteria) && criteria.length > 0 && criteria[0].field) { sortState.value = { field: criteria[0].field, direction: criteria[0].direction === 'desc' ? 'desc' : 'asc' } } else { sortState.value = { field: null, direction: null } } } catch { sortState.value = { field: null, direction: null } } } else { sortState.value = { field: null, direction: null } }
  localStorage.setItem('tf_last_active_query_id', q.id); localStorage.removeItem('tf_last_active_query_all')
  filterBarRef.value?.clearAll(); refreshList()
}
function selectAllProjects() {
  if (activeProjectId.value === null && activeTagId.value === null && activeQueryId.value === null && searchKeyword.value === '' && Object.keys(globalFilterParams.value).length === 0 && Object.keys(route.query).length === 0) return
  activeProjectId.value = null; activeQueryId.value = null; activeTagId.value = null; activeQueryName.value = '所有工单'; activeQueryObj.value = null; filterProject.value = undefined; searchKeyword.value = ''; globalFilterParams.value = {}; initialFilterChips.value = []; currentPage.value = 1
  skipRouteQueryWatch = true
  sortState.value = { field: null, direction: null }; router.replace({ query: {} })
  nextTick(() => { skipRouteQueryWatch = false })
  filterBarRef.value?.clearAll(); localStorage.setItem('tf_last_active_query_all', 'true'); localStorage.removeItem('tf_last_active_query_id')
  refreshList(); queryPanelRef.value?.loadPanel(); queryPanelRef.value?.loadTags()
}
function selectProject(p: any) {
  if (activeProjectId.value === p.id) { selectAllProjects(); return }
  activeProjectId.value = p.id; activeQueryId.value = null; activeTagId.value = null; activeQueryName.value = p.name; activeQueryObj.value = null; filterProject.value = p.id; globalFilterParams.value = {}; currentPage.value = 1
  skipRouteQueryWatch = true
  router.replace({ query: { ...route.query, project: p.key } })
  nextTick(() => { skipRouteQueryWatch = false })
  refreshList(); queryPanelRef.value?.loadPanel(); queryPanelRef.value?.loadTags()
}
function selectTag(tag: any) {
  activeTagId.value = tag.id || null
  activeProjectId.value = null
  filterProject.value = undefined
  currentPage.value = 1
  globalFilterParams.value = tag.id ? { tagId: tag.id } : {}
  syncFiltersToUrl(globalFilterParams.value)
  refreshList()
}
function onFilterChange() {
  currentPage.value = 1
  activeQueryId.value = null
  activeQueryObj.value = null
  activeTagId.value = null
  globalFilterParams.value = {}
  skipRouteQueryWatch = true
  if (filterProject.value) { const matched = projectList.value.find(p => p.id === filterProject.value); activeProjectId.value = filterProject.value; activeQueryName.value = matched?.name || '所有工单'; router.replace({ query: { project: matched?.key || filterProject.value } }) }
  else { activeProjectId.value = null; activeQueryName.value = '所有工单'; router.replace({ query: {} }) }
  nextTick(() => { skipRouteQueryWatch = false })
  localStorage.removeItem('tf_last_active_query_id')
  refreshList(); queryPanelRef.value?.loadPanel(); queryPanelRef.value?.loadTags()
}

// ===== Navigation helper: carry Saved Query context to detail page =====
function buildIssueDetailRoute(issueKey: string) {
  const query: Record<string, string> = {}
  if (activeQueryId.value && activeQueryName.value && activeQueryName.value !== '所有工单') {
    query.fromQuery = activeQueryId.value
    query.fromQueryName = activeQueryName.value
  }
  return { path: `/issues/${issueKey}`, query }
}

// ===== Table/List event handlers =====
function onRowClick(record: TableData) { if (previewMode.value === 'sidebar') { const index = issues.value.findIndex(i => i.id === record.id); openPreview(record as unknown as IssueVO, index) } else { router.push(buildIssueDetailRoute(record.issueKey as string)) } }
function onRowDblClick(record: TableData) { router.push(buildIssueDetailRoute(record.issueKey as string)) }
function onListItemClick(issue: IssueVO) { if (previewMode.value === 'sidebar') { const index = issues.value.findIndex(i => i.id === issue.id); openPreview(issue, index) } else { router.push(buildIssueDetailRoute(issue.issueKey)) } }
function onListItemDblClick(issue: IssueVO) { router.push(buildIssueDetailRoute(issue.issueKey)) }
function onListSortChange(field: string) { if (sortState.value.field !== field) { sortState.value = { field, direction: 'asc' } } else if (sortState.value.direction === 'asc') { sortState.value = { field, direction: 'desc' } } else { sortState.value = { field: null, direction: null } }; currentPage.value = 1; refreshList() }
function onListItemSelect(issue: IssueVO) { toggle(issue.id) }
function onSelectionChange(rowKeys: (string | number)[]) { selectedIds.value = new Set(rowKeys.map(String)) }
async function onManualOrderChange(issueIds: string[]) { await saveManualOrder(issueIds) }
async function onDiscardManualOrder() { await discardManualOrder(); refreshList() }

// ===== Status loading =====
async function loadStatuses() { try { const res = await issueApi.listStatuses(); statusCache.value = res.data || [] } catch { statusCache.value = [] } }

// ===== Watchers =====
watch(currentPage, () => refreshList())
watch(pageSize, () => refreshList())
watch(sortState, () => refreshList(), { deep: true })
watch(issues, () => { focusedIndex.value = -1 })
watch([activeProjectId, activeQueryId], () => { if (activeProjectId.value) loadManualOrder({ type: 'project', id: activeProjectId.value }); else if (activeQueryId.value) loadManualOrder({ type: 'query', id: activeQueryId.value }); else resetManualOrder() })

// Watch for popstate (browser back/forward) to restore filter state from URL
let skipRouteQueryWatch = false
watch(() => route.query, () => {
  // Skip changes triggered by our own router.replace calls
  if (skipRouteQueryWatch) return
  if (route.name !== 'Issues') return
  // Browser back/forward detected — restore state from URL
  if (hasDashboardFilterParams()) {
    applyDashboardFilter()
  } else if (!activeQueryId.value) {
    // URL cleared — reset to default view
    globalFilterParams.value = {}
    searchKeyword.value = ''
    filterBarRef.value?.clearAll()
    currentPage.value = 1
    refreshList()
  }
}, { deep: true })

// ===== Lifecycle =====
onMounted(async () => {
  const recoveryDraft = consumeSessionRecoveryDraft()
  if (recoveryDraft && recoveryDraft.formData) {
    const formData = recoveryDraft.formData
    if (formData.title?.trim() || formData.description?.trim()) {
      const savedId = saveDraft(formData)
      if (savedId) {
        recoveredDraftId.value = savedId
        setTimeout(() => { Message.info({ content: '已恢复上次会话过期时的工单草稿，点击左侧草稿区继续编辑', duration: 5000 }); setTimeout(() => { recoveredDraftId.value = null }, 3000) }, 500)
      }
    }
  }
  // 初始化面板数据（通过 QueryPanel ref 调用）
  await queryPanelRef.value?.loadPanel()
  await queryPanelRef.value?.loadProjects()
  await queryPanelRef.value?.loadTags()
  await loadStatuses()
  if (route.query.project) {
    const queryProject = String(route.query.project)
    const matched = projectList.value.find(p => p.key === queryProject || p.id === queryProject)
    if (matched) { activeProjectId.value = matched.id; activeQueryName.value = matched.name; filterProject.value = matched.id }
    else { activeProjectId.value = queryProject }
  }
  if (activeProjectId.value && projectList.value.length > 0 && !filterProject.value) {
    const matched = projectList.value.find(p => p.id === activeProjectId.value)
    if (matched) { activeQueryName.value = matched.name; filterProject.value = matched.id }
  }
  if (hasDashboardFilterParams()) { applyDashboardFilter() }
  else if (!route.query.project && !activeProjectId.value) {
    const lastQueryId = localStorage.getItem('tf_last_active_query_id')
    const lastQueryIsAll = localStorage.getItem('tf_last_active_query_all') === 'true'
    const savedQueriesRef = queryPanelRef.value?.savedQueries
    const savedQueriesList: any[] = savedQueriesRef ? (Array.isArray(savedQueriesRef) ? savedQueriesRef : (savedQueriesRef as any).value ?? []) : []
    if (lastQueryIsAll) { refreshList() }
    else if (lastQueryId && savedQueriesList.length > 0) {
      const matched = savedQueriesList.find((q: any) => q.id === lastQueryId)
      if (matched) selectQuery(matched)
      else { localStorage.removeItem('tf_last_active_query_id'); refreshList() }
    } else { refreshList() }
  } else { refreshList() }
  window.addEventListener('trackflow:issues-restored', handleIssuesRestored)
  document.addEventListener('keydown', handleKeyboardNav)
  document.addEventListener('keydown', onGlobalKeydownCtx)
})
onUnmounted(() => { window.removeEventListener('trackflow:issues-restored', handleIssuesRestored); document.removeEventListener('keydown', handleKeyboardNav); document.removeEventListener('keydown', onGlobalKeydownCtx) })

// KeepAlive 激活时刷新列表数据（保留筛选条件、分页、滚动位置）
onActivated(() => { refreshList() })

function handleIssuesRestored() { refreshList() }

// Route leave guard
onBeforeRouteLeave((_to, _from, next) => {
  const panel = createPanelRef.value
  if (panel?.isDiscarding) { next(); return }
  if (showCreatePanel.value && panel && panel.isDirty) {
    panel.suspendBeforeUnload?.()
    Modal.confirm({ title: '有未保存的更改', content: '创建工单表单中有未保存的内容，确定要离开吗？', okText: '放弃更改', cancelText: '继续编辑', simple: false, onOk: () => { next() }, onCancel: () => { panel.resumeBeforeUnload?.(); next(false) } })
  } else { next() }
})

</script>

<style scoped>
.issue-page { display: flex; height: 100%; }

/* Panel resizer (between QueryPanel and issue list) */
.panel-resizer { width: 4px; flex-shrink: 0; cursor: col-resize; background: transparent; position: relative; z-index: 2; transition: background 0.15s; }
.panel-resizer:hover, .panel-resizer:active { background: var(--tf-accent); }
.panel-resizer::after { content: ''; position: absolute; top: 0; bottom: 0; left: -2px; right: -2px; }

/* Right area */
.issue-list-area { flex: 1; display: flex; flex-direction: column; min-width: 0; overflow: hidden; }

.filter-bar { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--tf-border); flex-shrink: 0; }
.filter-left { display: flex; align-items: center; gap: 12px; }
.current-query-name { font-size: 14px; font-weight: 500; color: var(--tf-text-primary); }
.issue-total-badge { font-size: 12px; color: var(--tf-text-tertiary); }

/* Breadcrumb filter navigation */
.breadcrumb-nav {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
}
.breadcrumb-item {
  color: var(--tf-text-tertiary);
  font-weight: 400;
  white-space: nowrap;
  transition: color 150ms;
}
.breadcrumb-item.clickable {
  cursor: pointer;
  color: var(--tf-text-secondary);
}
.breadcrumb-item.clickable:hover {
  color: var(--color-primary-6, var(--tf-accent));
  text-decoration: underline;
}
.breadcrumb-item.current {
  color: var(--tf-text-primary);
  font-weight: 500;
}
.breadcrumb-separator {
  display: inline-flex;
  align-items: center;
  color: var(--tf-text-quaternary);
  font-size: 12px;
}

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
.filter-right { display: flex; gap: 8px; align-items: center; flex-shrink: 0; padding: 0 16px 0 8px; }

/* Inline create */
.inline-create { padding: 8px 16px; background: var(--tf-bg-surface); border-bottom: 1px solid var(--tf-border); flex-shrink: 0; }
.inline-create-row { display: flex; align-items: center; gap: 8px; }
.inline-title-input { flex: 1; }

/* Table */
.issue-table { flex: 1; min-height: 0; overflow: hidden; }
.issue-table :deep(.arco-table) { font-size: 13px; }
.issue-table :deep(.arco-scrollbar-container.arco-table-content) { overflow-y: auto !important; }
.issue-table :deep(.arco-table-th) { font-size: 11px; text-transform: uppercase; letter-spacing: 0.5px; }
.issue-table :deep(.arco-table-tr) { cursor: pointer; transition: background 0.15s; }
.issue-table :deep(.arco-table-td) { padding: 8px 12px; }
.issue-table :deep(.arco-table-col-resize-handle) { width: 3px; background: transparent; transition: background 0.15s; }
.issue-table :deep(.arco-table-col-resize-handle:hover),
.issue-table :deep(.arco-table-col-resize-handle.active) { background: var(--tf-accent); }

.issue-key { color: var(--tf-accent); font-weight: 500; font-size: 12px; text-decoration: none; cursor: pointer; }
.issue-key:hover { text-decoration: underline; }
.issue-title-text { color: var(--tf-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.issue-title-text :deep(.search-highlight) { background: var(--tf-highlight-bg, rgba(255, 200, 50, 0.35)); color: inherit; border-radius: 2px; padding: 0 1px; }
.issue-match-context { display: block; font-size: 11px; color: var(--tf-text-quaternary, var(--tf-text-tertiary)); margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; line-height: 1.3; }
.issue-match-context .match-source-label { display: inline-block; font-size: 10px; color: var(--tf-text-quaternary, var(--tf-text-tertiary)); background: var(--tf-bg-hover, rgba(255,255,255,0.05)); border-radius: 2px; padding: 0 4px; margin-right: 4px; vertical-align: baseline; }
.issue-match-context :deep(.search-highlight) { background: var(--tf-highlight-bg, rgba(255, 200, 50, 0.35)); color: inherit; border-radius: 2px; padding: 0 1px; }

/* Tag badges (inline in title cell) */
.issue-tag-badge {
  display: inline-block;
  max-width: 72px;
  font-size: 11px;
  color: var(--tf-text-on-accent);
  padding: 1px 5px;
  border-radius: 3px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
  margin-left: 4px;
  line-height: 1.4;
}
.issue-tag-overflow {
  font-size: 10px;
  color: var(--tf-text-tertiary, var(--color-text-3));
  margin-left: 2px;
  font-weight: 500;
  vertical-align: middle;
}

/* Resolved issue styling (YouTrack: strikethrough key + gray text) */
.issue-table :deep(.issue-resolved) .issue-key { text-decoration: line-through; color: var(--tf-text-tertiary); }
.issue-table :deep(.issue-resolved) .issue-key:hover { text-decoration: line-through underline; }
.issue-table :deep(.issue-resolved) .issue-title-text { color: var(--tf-text-tertiary); }
.issue-table :deep(.issue-resolved) .editable-cell { color: var(--tf-text-tertiary); }
.issue-table :deep(.issue-resolved) .readonly-cell { color: var(--tf-text-tertiary); }
.issue-table :deep(.issue-resolved) .type-label { color: var(--tf-text-tertiary); }
.issue-table :deep(.issue-resolved) .reporter-name { color: var(--tf-text-tertiary); }
.issue-table :deep(.issue-resolved) .time-ago { color: var(--tf-text-quaternary); }
.issue-table :deep(.issue-resolved) .cf-cell { color: var(--tf-text-tertiary); }
.type-label { font-size: 12px; color: var(--tf-text-secondary); display: inline-flex; align-items: center; gap: 4px; }
.type-color-dot { width: 8px; height: 8px; border-radius: 2px; flex-shrink: 0; }
.reporter-name { font-size: 12px; color: var(--tf-text-secondary); }

/* Project badge (cross-project column) */
.project-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-text-on-accent);
  background: var(--tf-accent, #58a6ff);
  padding: 2px 6px;
  border-radius: 3px;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}

/* Editable cells */
.editable-cell { display: inline-flex; align-items: center; gap: 4px; cursor: pointer; padding: 2px 6px; border-radius: 3px; transition: background 0.15s; font-size: 12px; color: var(--tf-text-secondary); }
.editable-cell:hover { background: var(--tf-bg-hover); }
.readonly-cell { display: inline-flex; align-items: center; gap: 4px; padding: 2px 6px; font-size: 12px; color: var(--tf-text-secondary); cursor: default; }
/* 已完成迭代的 Sprint 名称视觉弱化 */
.sprint-completed { color: var(--tf-text-tertiary); text-decoration: line-through; opacity: 0.7; }
/* 未分配工单视觉高亮 */
.unassigned-cell { color: rgb(var(--warning-6)); }
.unassigned-cell:hover { background: var(--tf-warning-bg); }
.unassigned-label { font-style: italic; color: rgb(var(--warning-6)); opacity: 0.9; }
.cell-spinner { font-size: 12px; color: var(--tf-text-tertiary); animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

.status-badge { padding: 2px 8px; border-radius: 3px; font-size: 11px; color: var(--tf-text-on-accent); font-weight: 500; }
.time-ago { font-size: 11px; color: var(--tf-text-tertiary); }
.time-over-budget { color: var(--tf-danger); font-weight: 500; }
.due-date-cell { font-size: 11px; color: var(--tf-text-tertiary); }
.due-date-cell.due-overdue { color: var(--tf-danger); font-weight: 500; }
.due-date-cell.due-due-soon { color: var(--tf-warning); font-weight: 500; }
.issue-table :deep(.issue-resolved) .due-date-cell { color: var(--tf-text-quaternary); font-weight: 400; }
.cf-cell { font-size: 12px; color: var(--tf-text-secondary); }
.cf-badge { display: inline-block; padding: 1px 6px; border-radius: 3px; font-size: 11px; font-weight: 500; line-height: 1.4; }
.cf-multi { display: inline-flex; flex-wrap: wrap; gap: 3px; }
.cf-tag { display: inline-block; padding: 1px 6px; border-radius: 3px; font-size: 11px; font-weight: 500; line-height: 1.4; background: var(--tf-bg-elevated); color: var(--tf-text-secondary); }

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
.unassigned-icon { width: 24px; text-align: center; color: var(--tf-text-tertiary); }

/* Empty state */
.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 48px 24px; gap: 8px; }
.empty-icon { font-size: 36px; color: var(--tf-text-quaternary); }
.empty-title { font-size: 14px; font-weight: 500; color: var(--tf-text-primary); margin: 0; }
.empty-desc { font-size: 12px; color: var(--tf-text-tertiary); margin: 0 0 8px; }
.error-state .error-icon { color: var(--tf-danger); }

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

/* Keyboard focus row highlight (J/K navigation in table mode) */
.issue-table :deep(.issue-keyboard-focused .arco-table-td) {
  background: var(--tf-bg-hover, var(--color-fill-1));
}
.issue-table :deep(.issue-keyboard-focused) {
  outline: 2px solid var(--tf-accent);
  outline-offset: -2px;
}

/* Preview mode toggle dropdown active item */
.doption-active { color: var(--tf-accent) !important; font-weight: 500; }
.doption-active::before { content: '✓ '; }

/* Real-time update notification bar */
.realtime-update-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 8px 16px;
  background: var(--tf-accent-subtle);
  border: 1px solid var(--tf-accent);
  border-radius: 4px;
  margin: 0 0 8px;
  cursor: pointer;
  transition: background 0.15s;
}
.realtime-update-bar:hover {
  background: var(--tf-accent);
}
.realtime-update-text {
  font-size: 13px;
  color: var(--tf-accent);
  font-weight: 500;
}
.realtime-update-icon {
  color: var(--tf-accent);
}

/* Search scope hint */
.search-scope-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-surface, var(--color-bg-2));
  border-radius: 4px;
  border: 1px solid var(--tf-border-subtle, var(--color-border));
}
.search-scope-icon {
  font-size: 13px;
  color: var(--tf-text-quaternary);
  flex-shrink: 0;
}
.search-result-count {
  margin-left: auto;
  color: var(--tf-text-secondary);
  font-weight: 500;
}

/* Context menu */
.ctx-menu-backdrop {
  position: fixed;
  inset: 0;
  z-index: 999;
  background: transparent;
}
.issue-context-menu {
  position: fixed;
  z-index: 1000;
  min-width: 220px;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border, rgba(255,255,255,0.1));
  border-radius: 6px;
  box-shadow: var(--tf-shadow-lg);
  padding: 4px 0;
  user-select: none;
}
.ctx-menu-label {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  padding: 4px 12px 2px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.ctx-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
  font-size: 13px;
  color: var(--tf-text-primary);
  cursor: pointer;
  transition: background 0.12s;
  position: relative;
}
.ctx-menu-item:hover {
  background: var(--tf-bg-hover);
}
.ctx-menu-item.ctx-menu-loading {
  cursor: default;
  color: var(--tf-text-tertiary);
}
.ctx-menu-item.ctx-menu-loading:hover {
  background: transparent;
}
.ctx-menu-item.ctx-menu-has-sub {
  justify-content: flex-start;
}
.ctx-menu-icon {
  font-size: 14px;
  color: var(--tf-text-secondary);
  flex-shrink: 0;
}
.ctx-menu-hint {
  margin-left: auto;
  font-size: 11px;
  color: var(--tf-text-tertiary);
}
.ctx-menu-arrow {
  margin-left: auto;
  font-size: 11px;
  color: var(--tf-text-tertiary);
}
.ctx-menu-separator {
  height: 1px;
  background: var(--tf-border, rgba(255,255,255,0.08));
  margin: 4px 0;
}
.ctx-status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
.ctx-menu-empty {
  padding: 6px 12px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}
.ctx-menu-group-label {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  padding: 4px 12px 2px;
  font-weight: 500;
}

/* Context submenu */
.ctx-menu-submenu {
  position: absolute;
  left: 100%;
  top: 0;
  min-width: 200px;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border, rgba(255,255,255,0.1));
  border-radius: 6px;
  box-shadow: var(--tf-shadow-lg);
  padding: 4px 0;
  z-index: 1001;
}

/* Table wrapper for context menu */
.issue-table-wrapper {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
</style>
