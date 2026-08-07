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
        <a-input v-model="panelSearch" placeholder="过滤已保存的查询..." size="small" allow-clear>
          <template #prefix><icon-search /></template>
        </a-input>
      </div>

      <!-- Drafts section (YouTrack style) — 仅对有 issue:create 权限的用户显示 -->
      <div v-if="canCreateIssueGlobal && (hasDrafts || true)" class="query-group drafts-group">
        <div class="group-header" @click="toggleGroup('drafts')">
          <span class="group-arrow">{{ expandedGroups.has('drafts') ? '▾' : '▸' }}</span>
          <span class="group-title">草稿</span>
          <span v-if="draftCount > 0" class="draft-count-badge">{{ draftCount }}</span>
          <a-button
            v-if="canCreateIssueGlobal"
            type="text" size="mini" class="group-action-btn"
            title="新建工单"
            @click.stop="openDraftCreate"
          >
            <template #icon><icon-plus :size="12" /></template>
          </a-button>
        </div>
        <div v-if="expandedGroups.has('drafts')" class="group-items">
          <div v-if="draftList.length === 0" class="empty-drafts">
            <span class="empty-icon">📝</span>
            <span class="empty-text">暂无草稿</span>
            <span class="empty-hint">取消创建工单时，已填写的内容会自动保存为草稿</span>
          </div>
          <a-dropdown
            v-for="d in draftList"
            :key="d.id"
            trigger="contextMenu"
            position="br"
          >
            <div
              class="query-item draft-item"
              :class="{ 'draft-recovered': recoveredDraftId === d.id }"
              @click="openDraft(d)"
            >
              <span class="query-icon">📄</span>
              <span class="query-name draft-name">{{ d.title || '无标题草稿' }}</span>
              <span class="draft-time">{{ formatDraftTime(d.updatedAt) }}</span>
              <span v-if="recoveredDraftId === d.id" class="draft-recovered-badge">刚恢复</span>
            </div>
            <template #content>
              <a-doption @click="openDraft(d)">
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
            <a-link type="text" @click="handleDeleteAllDrafts" class="delete-all-link">删除所有草稿</a-link>
          </div>
        </div>
      </div>

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
            @click="selectAllProjects"
          >
            <span class="query-name">所有项目</span>
          </div>
          <template v-if="favoriteProjects.length > 0">
            <div
              v-for="p in favoriteProjects"
              :key="p.id"
              class="query-item"
              :class="{ active: activeProjectId === p.id }"
              @click="selectProject(p)"
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

      <!-- Manage Projects (Favorites) Modal -->
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

      <!-- Tags section (YouTrack style) -->
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
            @click="selectTag(tag)"
          >
            <span class="tag-color-dot" :style="{ backgroundColor: tag.color }"></span>
            <span class="query-name">{{ tag.name }}</span>
            <span class="query-count">{{ formatCount(tag.count) }}</span>
          </div>
          <div v-if="favoriteTags.length === 0" class="empty-queries">
            <span>暂无收藏标签</span>
          </div>
        </div>
      </div>

      <!-- Manage Tags Modal -->
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

      <div class="query-group">
        <div class="group-header" @click="toggleGroup('saved')">
          <span class="group-arrow">{{ expandedGroups.has('saved') ? '\u25BE' : '\u25B8' }}</span>
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
          <div v-if="panelLoadFailed && filteredQueries.length === 0" class="empty-queries panel-error">
            <icon-exclamation-circle-fill style="color: var(--color-warning-6); margin-right: 4px;" />
            加载失败
            <a-link :hoverable="false" style="margin-left: 8px; font-size: 12px;" @click="loadPanel()">重试</a-link>
          </div>
          <div v-else-if="filteredQueries.length === 0" class="empty-queries">暂无保存的搜索</div>
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
        <a-form :model="renameQueryForm" layout="vertical">
          <a-form-item label="新名称" field="name" :rules="[{ required: true, message: '请输入名称' }]">
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
      <div v-if="selectedCount === 0" class="filter-bar">
        <div class="filter-left">
          <!-- Breadcrumb filter navigation (YouTrack style) -->
          <nav class="breadcrumb-nav" aria-label="筛选导航">
            <span
              class="breadcrumb-item"
              :class="{ clickable: activeProjectId !== null || activeQueryId !== null }"
              @click="selectAllProjects"
            >所有工单</span>
            <template v-if="activeProjectId && !activeQueryId">
              <span class="breadcrumb-separator"><icon-right /></span>
              <span class="breadcrumb-item current">{{ activeProjectName }}</span>
            </template>
            <template v-if="activeQueryId">
              <template v-if="activeQueryProjectName">
                <span class="breadcrumb-separator"><icon-right /></span>
                <span
                  class="breadcrumb-item clickable"
                  @click="navigateToQueryProject"
                >{{ activeQueryProjectName }}</span>
              </template>
              <span class="breadcrumb-separator"><icon-right /></span>
              <span class="breadcrumb-item current">{{ activeQueryName }}</span>
            </template>
          </nav>
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
              <span class="priority-badge" :style="{ background: p.color }"></span>{{ p.label }}
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
        :scroll="{ x: tableMinWidth }"
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
          <span class="issue-title-text">{{ record.title }}</span>
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
                      <span class="member-avatar">{{ m.displayName?.charAt(0) }}</span><span>{{ m.displayName }}</span>
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
                      <span class="status-dot" :style="{ background: st.color }"></span><span>{{ st.transitionName || localizeStatusName(st.name) }}</span>
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
                <span class="priority-badge" :style="{ background: getPriorityColorForRecord(record.priority) }"></span>
                {{ localizePriority(record.priority) }}
                <icon-loading v-if="isCellEditing(record.id, 'priority')" class="cell-spinner" />
              </span>
              <template #content>
                <div class="inline-dropdown">
                  <div v-for="p in priorityOptions" :key="p.value" class="dropdown-item" @click="selectPriority(record, p.value)">
                    <span class="priority-badge" :style="{ background: p.color }"></span><span>{{ p.label }}</span>
                  </div>
                </div>
              </template>
            </a-trigger>
            <span v-else class="readonly-cell">
              <span class="priority-badge" :style="{ background: getPriorityColorForRecord(record.priority) }"></span>
              {{ localizePriority(record.priority) }}
            </span>
          </div>
        </template>
        <template #updatedAt="{ record }"><span class="time-ago">{{ formatTime(record.updatedAt) }}</span></template>
        <template #issueType="{ record }"><span class="type-label"><span class="type-color-dot" :style="{ background: getIssueTypeColorForRecord(record.issueType) }"></span>{{ getIssueTypeLabelForRecord(record.issueType) }}</span></template>
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
                :style="getCustomFieldDetail(record, column.dataIndex)!.colors?.[idx] ? { background: getCustomFieldDetail(record, column.dataIndex)!.colors![idx]!, color: '#fff' } : {}"
              >{{ dv }}</span>
            </span>
            <span
              v-else-if="getCustomFieldDetail(record, column.dataIndex)!.color"
              class="cf-cell cf-badge"
              :style="{ background: getCustomFieldDetail(record, column.dataIndex)!.color!, color: '#fff' }"
            >{{ getCustomFieldDetail(record, column.dataIndex)!.displayValue || '\u2014' }}</span>
            <span v-else class="cf-cell">{{ getCustomFieldDetail(record, column.dataIndex)!.displayValue || '\u2014' }}</span>
          </template>
          <span v-else class="cf-cell">&#x2014;</span>
        </template>

        <!-- empty -->
        <template #empty>
          <div v-if="loadError" class="empty-state error-state">
            <icon-close-circle class="empty-icon error-icon" />
            <p class="empty-title">加载失败</p>
            <p class="empty-desc">无法获取工单列表，请检查网络连接或稍后重试</p>
            <a-button type="primary" size="small" @click="refreshList">
              <template #icon><icon-refresh /></template>
              重试
            </a-button>
          </div>
          <div v-else class="empty-state">
            <icon-search class="empty-icon" />
            <p class="empty-title">暂无工单</p>
            <p class="empty-desc">尝试调整筛选条件或创建新的工单</p>
            <a-button v-if="canCreateIssueGlobal" type="primary" size="small" @click="toggleInlineCreate">创建工单</a-button>
          </div>
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
        <a-pagination v-model:current="currentPage" :total="totalIssues" :page-size="pageSize" size="small" show-total @change="goPage" />
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
            <span>{{ localizeStatusName(st.name) }}</span>
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
import { ref, reactive, computed, onMounted, onUnmounted, watch, h, nextTick } from 'vue'
import { useRouter, useRoute, onBeforeRouteLeave } from 'vue-router'
import { IconPlus, IconSearch, IconLoading, IconEdit, IconPenFill, IconShareExternal, IconPushpin, IconDelete, IconLock, IconCheckCircle, IconEye, IconLayout, IconExpand, IconDownload, IconFile, IconCode, IconCopy, IconLink, IconCalendar, IconRight, IconSettings, IconMinusCircle, IconExclamationCircleFill } from '@arco-design/web-vue/es/icon'
import { Message, Modal } from '@arco-design/web-vue'
import axios from 'axios'
import { projectApi, issueApi, queryApi, sprintApi, tagApi, customFieldApi } from '@/api'
import type { IssueVO, IssueStatusVO, ProjectMemberVO, SprintVO, CustomFieldValueVO } from '@/api/types'
import type { TagPanelItemVO, AvailableTagVO } from '@/api/tag'
import type { TableData } from '@arco-design/web-vue'
import { useAuthStore } from '@/stores/auth'
import { localizeStatusName, localizePriority, priorityLabelMap, priorityReverseLabelMap, queryFieldKeyToLabel, queryFieldLabelToKey } from '@/utils/fieldLabels'
import { extractVersion, showActionFeedback } from '@/utils/transition'
import { ERROR_CODES } from '@/api/error-codes'
import { useIssueList, useSelection, useInlineEdit, useBatchOps, usePermission, useColumnConfig, useViewSettings, useManualOrder, useDrafts } from './composables'
import { loadPriorityOptions } from './composables/usePriorityOptions'
import { loadIssueTypeOptions } from './composables/useIssueTypeOptions'
import type { IssueDraft } from './composables'
import { useIssueProjectSubscription } from '@/composables/useWebSocket'
import { useNavBadge } from '@/composables/useNavBadge'
import type { IssueRealtimeEvent } from '@/composables/useWebSocket'
import { consumeSessionRecoveryDraft } from '@/utils/sessionEvents'
import BatchActionToolbar from './components/BatchActionToolbar.vue'
import RecentIssuesPanel from './components/RecentIssuesPanel.vue'
import DraggableColumnHeader from './components/DraggableColumnHeader.vue'
import IssueCreatePanel from './IssueCreatePanel.vue'
import IssuePreviewDrawer from '../board/IssuePreviewDrawer.vue'
import ColumnConfigPopover from './components/ColumnConfigPopover.vue'
import FilterBar from './components/FilterBar.vue'
import QueryInput from './components/QueryInput.vue'
import ApplyCommandDialog from './components/ApplyCommandDialog.vue'
import KeyboardShortcutsHelp from './components/KeyboardShortcutsHelp.vue'
import ViewSettingsMenu from './components/ViewSettingsMenu.vue'
import IssueListLayout from './components/IssueListLayout.vue'

const router = useRouter()
const route = useRoute()

// Composables
const {
  issues, totalIssues, currentPage, pageSize, loading, loadError,
  sortState, loadIssues, goPage, updateLocalIssue, removeLocalIssue
} = useIssueList()

const {
  selectedIds, selectedCount, selectedIssues,
  toggle, toggleAll, clearSelection
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

// Manual order (drag sorting)
const {
  isManualSorted, isOwnerOrder, manualOrderData,
  loadManualOrder, saveOrder: saveManualOrder, discardOrder: discardManualOrder, reset: resetManualOrder
} = useManualOrder()

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
const expandedGroups = reactive(new Set<string>(['saved', 'projects', 'drafts', 'tags']))
const panelSearch = ref('')

// Tags panel state
const favoriteTags = ref<TagPanelItemVO[]>([])
const activeTagId = ref<string | null>(null)
const showManageTagsModal = ref(false)
const availableTags = ref<AvailableTagVO[]>([])

// Projects favorite panel state
const favoriteProjects = computed(() => projectList.value.filter(p => p.favorited))
const showManageProjectsModal = ref(false)
const manageProjectSearch = ref('')
const manageProjectsLoading = ref(false)
const allProjectsForManage = ref<any[]>([])

const filteredManageProjects = computed(() => {
  const list = allProjectsForManage.value
  if (!manageProjectSearch.value) return list
  const kw = manageProjectSearch.value.toLowerCase()
  return list.filter((p: any) => p.name.toLowerCase().includes(kw) || p.key.toLowerCase().includes(kw))
})

async function openManageProjectsModal() {
  showManageProjectsModal.value = true
  manageProjectSearch.value = ''
  manageProjectsLoading.value = true
  try {
    // Load all accessible projects (no pagination limit), and merge favorite status
    const res = await projectApi.list({ pageSize: 100 })
    const projects = res.data?.list || []
    // Merge favorited status from projectList (which was loaded with populateFavoriteStatus)
    const favoriteIds = new Set(projectList.value.filter(p => p.favorited).map(p => p.id))
    allProjectsForManage.value = projects.map((p: any) => ({
      ...p,
      favorited: favoriteIds.has(p.id)
    }))
  } catch {
    allProjectsForManage.value = []
  } finally {
    manageProjectsLoading.value = false
  }
}

async function toggleProjectFavorite(p: any) {
  try {
    const res = await projectApi.toggleFavorite(p.id)
    const newFavorited = res.data?.favorited ?? !p.favorited
    p.favorited = newFavorited
    // Sync favorited status back to projectList (used by favoriteProjects computed)
    const inList = projectList.value.find(pr => pr.id === p.id)
    if (inList) {
      inList.favorited = newFavorited
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  }
}

// ===== Breadcrumb navigation computed =====
const activeProjectName = computed(() => {
  if (!activeProjectId.value) return ''
  const p = projectList.value.find(pr => pr.id === activeProjectId.value)
  return p?.name || ''
})
const activeQueryProjectName = computed(() => {
  // If the active saved query has a project scope, show it in breadcrumb
  if (!activeQueryObj.value) return ''
  const filtersRaw = activeQueryObj.value.filters
  if (!filtersRaw) return ''
  let filters: any[]
  if (typeof filtersRaw === 'string') {
    try { filters = JSON.parse(filtersRaw) } catch { return '' }
  } else {
    filters = filtersRaw
  }
  if (!Array.isArray(filters)) return ''
  const projectFilter = filters.find((f: any) => f.field === 'project')
  if (!projectFilter || !projectFilter.value || projectFilter.value.length === 0) return ''
  const projId = projectFilter.value[0]
  const p = projectList.value.find(pr => pr.id === projId)
  return p?.name || ''
})
function navigateToQueryProject() {
  // Navigate from saved query breadcrumb to the query's project
  if (!activeQueryObj.value) return
  const filtersRaw = activeQueryObj.value.filters
  if (!filtersRaw) return
  let filters: any[]
  if (typeof filtersRaw === 'string') {
    try { filters = JSON.parse(filtersRaw) } catch { return }
  } else {
    filters = filtersRaw
  }
  if (!Array.isArray(filters)) return
  const projectFilter = filters.find((f: any) => f.field === 'project')
  if (!projectFilter || !projectFilter.value || projectFilter.value.length === 0) return
  const projId = projectFilter.value[0]
  const p = projectList.value.find(pr => pr.id === projId)
  if (p) {
    selectProject(p)
  }
}

// ===== WebSocket 实时更新 =====
// 新变更通知指示器（当有对列表外的更新时提示用户）
const hasNewUpdates = ref(false)

// 订阅当前项目的 Issue 变更事件
useIssueProjectSubscription(
  () => activeProjectId.value,
  (event: IssueRealtimeEvent) => {
    // 忽略自己的操作（已在本地更新）
    const currentUserId = authStore.user?.id
    if (currentUserId && String(event.operatorId) === String(currentUserId)) return

    if (event.action === 'FIELD_UPDATED') {
      // 更新列表中对应工单的字段
      const idx = issues.value.findIndex(i => String(i.id) === String(event.issueId))
      if (idx !== -1) {
        // 在列表中找到 → 就地更新字段（YouTrack 行为：不改变排序和筛选位置）
        const patch: Partial<IssueVO> = {}
        for (const [key, value] of Object.entries(event.changes)) {
          ;(patch as any)[key] = value
        }
        updateLocalIssue(String(event.issueId), patch)
      } else {
        // 不在当前列表中 → 不添加（YouTrack 行为：新匹配的不自动加入）
        hasNewUpdates.value = true
      }
    } else if (event.action === 'CREATED') {
      // 新工单创建：不自动加入列表，只显示通知提示
      hasNewUpdates.value = true
    } else if (event.action === 'DELETED') {
      // 工单被删除：从列表中移除
      const idx = issues.value.findIndex(i => String(i.id) === String(event.issueId))
      if (idx !== -1) {
        issues.value.splice(idx, 1)
        totalIssues.value = Math.max(0, totalIssues.value - 1)
      }
    }
  }
)
// ===== End WebSocket =====

// Manual order computed (depends on activeProjectId and activeQueryId)
const isDraggable = computed(() => {
  return isListLayout.value && (!!activeProjectId.value || !!activeQueryId.value)
})
const sortedIssueIds = computed(() => manualOrderData.value?.issueIds || [])

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
        if (f.field === 'type') return getIssueTypeLabelForRecord(v)
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
        if (f.field === 'type') return getIssueTypeLabelForRecord(v)
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
    const entry = issueTypeOptions.value.find(o => o.label === v || o.value === v)
    return entry ? entry.value : v
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
  } catch { /* localStorage 读取容错 */ }
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
  loadPanel() // 刷新面板计数以匹配 hideResolved 状态
}

/** Resolve date keyword (今天/昨天/etc) to ISO date string */
function resolveDateKeyword(value: string): string | null {
  const today = new Date()
  const fmt = (d: Date) => d.toISOString().split('T')[0]
  switch (value) {
    case 'today': case '今天': return fmt(today)
    case 'yesterday': case '昨天': return fmt(new Date(today.getTime() - 86400000))
    default:
      // If value looks like a date (yyyy-MM-dd), return as-is
      if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return value
      return null
  }
}

function onGlobalSearch(keyword: string) {
  // Detect structured query syntax (e.g. "状态: Testing  负责人: 我")
  // If any known field label followed by ":" is found, parse as structured query
  const normalized = keyword.replace(/：/g, ':')
  const allFieldLabels = Object.keys(queryFieldLabelToKey)
  const hasStructuredSyntax = allFieldLabels.some(label => {
    const idx = normalized.indexOf(`${label}:`)
    if (idx < 0) return false
    // Must be at start or after whitespace
    return idx === 0 || normalized[idx - 1] === ' '
  })

  if (hasStructuredSyntax) {
    // Parse structured query using existing queryTextToFilters
    const parsedFilters = queryTextToFilters(keyword)
    if (parsedFilters.length > 0) {
      // Convert structured filters to globalFilterParams format (same mapping as FilterBar.emitFilters)
      const filterParams: Record<string, any> = {}
      let remainingKeyword = ''

      for (const f of parsedFilters) {
        const fieldKey = f.field
        const op = f.operator
        const values: string[] = f.value || []
        const isNegative = op === 'neq' || op === 'not_in'

        switch (fieldKey) {
          case 'status':
            if (op === 'open') {
              // Find all non-closed status IDs
              const openIds = statusCache.value.filter(s => !s.isClosed).map(s => s.id)
              if (openIds.length) filterParams.statusId = openIds.join(',')
            } else if (op === 'closed') {
              const closedIds = statusCache.value.filter(s => s.isClosed).map(s => s.id)
              if (closedIds.length) filterParams.statusId = closedIds.join(',')
            } else {
              // Resolve status name/code to IDs
              const statusIds = values.map(v => {
                const st = statusCache.value.find(s =>
                  s.code === v || s.name === v || localizeStatusName(s.name) === v
                )
                return st?.id || v
              })
              if (!isNegative) filterParams.statusId = statusIds.join(',')
              else filterParams.statusIdNot = statusIds.join(',')
            }
            break
          case 'priority':
            if (op === 'between' && values.length >= 2) {
              // Resolve range using priorityOptions order (position-based)
              const opts = priorityOptions.value
              const findIdx = (v: string) => opts.findIndex(o =>
                o.value.toLowerCase() === v.toLowerCase() || o.label === v
              )
              let startIdx = findIdx(values[0])
              let endIdx = findIdx(values[1])
              if (startIdx >= 0 && endIdx >= 0) {
                // Auto-swap if reversed
                if (startIdx > endIdx) [startIdx, endIdx] = [endIdx, startIdx]
                const rangeValues = opts.slice(startIdx, endIdx + 1).map(o => o.value)
                filterParams.priority = rangeValues.join(',')
              } else {
                // Fallback: send raw values as-is
                filterParams.priority = values.join(',')
              }
            } else if (!isNegative) {
              filterParams.priority = values.join(',')
            } else {
              filterParams.priorityNot = values.join(',')
            }
            break
          case 'type':
            if (!isNegative) filterParams.issueType = values.join(',')
            else filterParams.issueTypeNot = values.join(',')
            break
          case 'assignee':
            if (values.includes('${currentUser}')) {
              filterParams.assignedToMe = 'true'
            } else if (!isNegative) {
              filterParams.assigneeId = values.join(',')
            } else {
              filterParams.assigneeIdNot = values.join(',')
            }
            break
          case 'reporter':
            if (values.includes('${currentUser}')) {
              filterParams.reportedByMe = 'true'
            }
            break
          case 'sprint':
            if (!isNegative) filterParams.sprintId = values.join(',')
            else filterParams.sprintIdNot = values.join(',')
            break
          case 'project': {
            const p = projectList.value.find(pr => pr.key === values[0] || pr.name === values[0])
            if (p) filterParams.projectId = p.id
            else filterParams.projectId = values[0]
            break
          }
          case 'keyword':
            remainingKeyword = values.join(' ')
            break
          case 'createdAt':
            if (op === 'between' && values.length >= 2) {
              filterParams.createdAfter = values[0]
              filterParams.createdBefore = values[1]
            }
            break
          case 'updatedAt':
            if (op === 'between' && values.length >= 2) {
              filterParams.updatedAfter = values[0]
              filterParams.updatedBefore = values[1]
            }
            break
          case 'resolvedAt':
            if (op === 'between' && values.length >= 2) {
              filterParams.resolvedAfter = values[0]
              filterParams.resolvedBefore = values[1]
            }
            break
          case 'dueDate':
            if (op === 'between' && values.length >= 2) {
              filterParams.dueAfter = values[0]
              filterParams.dueBefore = values[1]
            } else if (values.length > 0) {
              // Single date value — map to due on that date
              const dateVal = resolveDateKeyword(values[0])
              if (dateVal) {
                filterParams.dueAfter = dateVal
                filterParams.dueBefore = dateVal
              }
            }
            break
          default:
            // Unknown field — treat as keyword fragment
            break
        }
      }

      searchKeyword.value = remainingKeyword
      globalFilterParams.value = filterParams
      currentPage.value = 1
      refreshList()
      return
    }
  }

  // Fallback: plain keyword search (original behavior)
  searchKeyword.value = keyword
  globalFilterParams.value = {}
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

  // Persist user's preference to show all issues
  localStorage.setItem('tf_last_active_query_all', 'true')
  localStorage.removeItem('tf_last_active_query_id')

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
const createPanelRef = ref<InstanceType<typeof IssueCreatePanel> | null>(null)
const quickCreating = ref(false)
const quickForm = reactive({
  projectId: undefined as string | undefined,
  title: '',
  issueType: 'Task',
  priority: 'Normal'
})

// Drafts
const { draftList, draftCount, hasDrafts, saveDraft, deleteDraft, deleteAllDrafts, getDraft } = useDrafts()
const activeDraftId = ref<string | null>(null)
// 刚从会话恢复的草稿 ID，用于高亮提示（3 秒后自动清除）
const recoveredDraftId = ref<string | null>(null)

function formatDraftTime(timestamp: number): string {
  const now = Date.now()
  const diff = now - timestamp
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`
  return new Date(timestamp).toLocaleDateString()
}

function openDraftCreate() {
  activeDraftId.value = null
  showCreatePanel.value = true
}

function openDraft(draft: IssueDraft) {
  activeDraftId.value = draft.id
  showCreatePanel.value = true
}

function handleDeleteDraft(draftId: string) {
  deleteDraft(draftId)
  Message.success('草稿已删除')
}

function handleDeleteAllDrafts() {
  Modal.confirm({
    title: '删除所有草稿',
    content: `确定要删除全部 ${draftCount.value} 个草稿吗？此操作不可撤销。`,
    okText: '全部删除',
    cancelText: '取消',
    okButtonProps: { status: 'danger' },
    onOk: () => {
      deleteAllDrafts()
      Message.success('所有草稿已删除')
    }
  })
}

/**
 * 创建面板关闭时自动保存草稿
 * IssueCreatePanel 的 @cancel 事件会调用此方法
 */
function onCreatePanelCancel(formData: any) {
  if (formData && (formData.title?.trim() || formData.description?.trim())) {
    saveDraft(formData, activeDraftId.value || undefined)
    Message.info('已保存为草稿')
  }
  activeDraftId.value = null
}

function onCreatePanelCreated() {
  // 如果从草稿创建成功，删除该草稿
  if (activeDraftId.value) {
    deleteDraft(activeDraftId.value)
    activeDraftId.value = null
  }
  refreshList()
}

/**
 * 用户点击创建面板的「全屏」按钮
 * 保存草稿后跳转到全屏创建页面，已填写内容通过草稿恢复
 */
function onCreatePanelExpand(formData: any) {
  // 关闭弹窗
  showCreatePanel.value = false
  activeDraftId.value = null
  // 如果有内容，先保存为草稿
  if (formData && (formData.title?.trim() || formData.description?.trim())) {
    const draftId = saveDraft(formData)
    if (draftId) {
      router.push({ name: 'IssueCreate', query: { draftId } })
      return
    }
  }
  router.push({ name: 'IssueCreate' })
}

// Apply Command dialog
const showCommandDialog = ref(false)

// Keyboard shortcuts help panel
const showShortcutsHelp = ref(false)

// ===== Keyboard focused index (J/K navigation in list) =====
/** Index of the keyboard-focused row in the current issues list (-1 = no focus) */
const focusedIndex = ref<number>(-1)

/** ID of the keyboard-focused issue (derived from focusedIndex) */
const focusedIssueId = computed<string | null>(() => {
  if (focusedIndex.value >= 0 && focusedIndex.value < issues.value.length) {
    return issues.value[focusedIndex.value].id
  }
  return null
})

/** Scroll the focused row into view if needed */
function scrollFocusedIntoView() {
  if (focusedIndex.value < 0) return
  const issue = issues.value[focusedIndex.value]
  if (!issue) return
  // List layout: IssueListItem has data-id attribute
  // Table layout: Arco table renders <tr data-row-key="id">
  const el = (
    document.querySelector(`[data-id="${issue.id}"]`) ||
    document.querySelector(`[data-row-key="${issue.id}"]`)
  ) as HTMLElement | null
  el?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
}

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
  } catch { /* localStorage 读取容错 */ }
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

  // Don't intercept when focus is in an input field (typing)
  const tag = (e.target as HTMLElement)?.tagName?.toLowerCase()
  const isEditing = tag === 'input' || tag === 'textarea' || tag === 'select'
  // Also skip if target is a contenteditable element (Tiptap editor)
  const isContentEditable = (e.target as HTMLElement)?.isContentEditable

  // ===== Preview mode sidebar: ArrowUp/Down navigate preview =====
  if (previewMode.value === 'sidebar' && previewVisible.value && !isEditing && !isContentEditable) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      navigateIssue(1)
      return
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      navigateIssue(-1)
      return
    } else if (e.key === 'Enter') {
      e.preventDefault()
      if (previewIssueId.value) {
        onPreviewGoDetail(previewIssueId.value)
      }
      return
    } else if (e.key === 'Escape') {
      e.preventDefault()
      closePreview()
      return
    }
  }

  // ===== Global shortcuts (not in input) =====
  if (isEditing || isContentEditable) return

  // Escape: close any open panel/modal
  if (e.key === 'Escape') {
    if (showShortcutsHelp.value) {
      showShortcutsHelp.value = false
    } else if (previewVisible.value) {
      closePreview()
    } else if (showCommandDialog.value) {
      showCommandDialog.value = false
    } else if (focusedIndex.value >= 0) {
      focusedIndex.value = -1
    }
    return
  }

  // ? — toggle shortcuts help panel
  if (e.key === '?' || (e.key === '/' && e.shiftKey)) {
    e.preventDefault()
    showShortcutsHelp.value = !showShortcutsHelp.value
    return
  }

  // Don't trigger list shortcuts when any modal/dialog is open
  if (showCommandDialog.value || showCreatePanel.value || showShortcutsHelp.value) return

  // N — create new issue
  if (e.key === 'n' || e.key === 'N') {
    if (canCreateIssueGlobal) {
      e.preventDefault()
      showCreatePanel.value = true
    }
    return
  }

  // J / ArrowDown — move focus down
  if (e.key === 'j' || e.key === 'J' || e.key === 'ArrowDown') {
    e.preventDefault()
    if (issues.value.length === 0) return
    if (focusedIndex.value < issues.value.length - 1) {
      focusedIndex.value++
    } else {
      focusedIndex.value = 0 // wrap to top
    }
    scrollFocusedIntoView()
    return
  }

  // K / ArrowUp — move focus up
  if (e.key === 'k' || e.key === 'K' || e.key === 'ArrowUp') {
    e.preventDefault()
    if (issues.value.length === 0) return
    if (focusedIndex.value > 0) {
      focusedIndex.value--
    } else {
      focusedIndex.value = issues.value.length - 1 // wrap to bottom
    }
    scrollFocusedIntoView()
    return
  }

  // Enter — open focused issue
  if (e.key === 'Enter' && focusedIndex.value >= 0) {
    e.preventDefault()
    const issue = issues.value[focusedIndex.value]
    if (issue) {
      if (previewMode.value === 'sidebar') {
        openPreview(issue, focusedIndex.value)
      } else {
        router.push(`/issues/${issue.issueKey}`)
      }
    }
    return
  }

  // Space — select/deselect focused issue
  if (e.key === ' ' && focusedIndex.value >= 0 && canBatchOps.value) {
    e.preventDefault()
    const issue = issues.value[focusedIndex.value]
    if (issue) {
      toggle(issue.id)
    }
    return
  }

  // Ctrl+A — select all
  if ((e.ctrlKey || e.metaKey) && (e.key === 'a' || e.key === 'A') && canBatchOps.value) {
    e.preventDefault()
    toggleAll()
    return
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

// Badge fields map (projectId → badge field configs)
import type { BadgeFieldConfig, BadgeColorRule } from './components/badgeTypes'
const badgeFieldsMap = reactive<Record<string, BadgeFieldConfig[]>>({})
const badgeFieldsLoadedProjects = new Set<string>()

/**
 * 加载项目的徽章字段配置。
 * 从项目自定义字段中筛选 showAsBadge=true 且类型为整数的字段。
 */
async function loadBadgeFields(projectIds: string[]) {
  const toLoad = projectIds.filter(pid => pid && !badgeFieldsLoadedProjects.has(pid))
  if (toLoad.length === 0) return
  for (const pid of toLoad) {
    badgeFieldsLoadedProjects.add(pid)
    try {
      const res = await customFieldApi.listByProject(pid)
      const fields = (res.data || []).filter(
        (f: any) => f.showAsBadge && (f.fieldFormat === 'int' || f.fieldFormat === 'integer')
      )
      if (fields.length > 0) {
        badgeFieldsMap[pid] = fields.slice(0, 2).map((f: any) => {
          let colorRules: BadgeColorRule[] | null = null
          if (f.badgeColorRules) {
            try { colorRules = JSON.parse(f.badgeColorRules) } catch { /* ignore */ }
          }
          return { fieldId: f.id, fieldName: f.name, colorRules }
        })
      }
    } catch { /* non-critical, silently ignore */ }
  }
}

/** 在工单加载后自动加载相关项目的徽章字段配置 */
function loadBadgeFieldsForIssues() {
  const projectIds = [...new Set(issues.value.map(i => i.projectId).filter(Boolean))]
  if (projectIds.length > 0) loadBadgeFields(projectIds)
}
const priorityOptions = ref([
  { value: 'Show-stopper', label: '阻塞', color: '#b91c1c' },
  { value: 'Critical', label: '紧急', color: '#ef4444' },
  { value: 'High', label: '高', color: '#f59e0b' },
  { value: 'Normal', label: '普通', color: '#6366f1' },
  { value: 'Low', label: '低', color: '#64748b' }
])

// 工单类型选项（从自定义字段系统动态加载）
const issueTypeOptions = ref([
  { value: 'Bug', label: '缺陷', color: '#ef4444' },
  { value: 'Task', label: '任务', color: '#6366f1' },
  { value: 'Feature', label: '需求', color: '#22c55e' },
  { value: 'Epic', label: '史诗', color: '#a855f7' },
  { value: 'Story', label: '故事', color: '#3b82f6' },
])

// Load priority and issue type options from custom field system when project changes
watch(activeProjectId, async (projectId) => {
  if (projectId) {
    const loaded = await loadPriorityOptions(projectId)
    priorityOptions.value = loaded.map(o => ({ value: o.value, label: o.label, color: o.color || '#6366f1' }))
    const loadedTypes = await loadIssueTypeOptions(projectId)
    issueTypeOptions.value = loadedTypes.map(o => ({ value: o.value, label: o.label, color: o.color || '#6366f1' }))
  }
}, { immediate: true })

/** 根据优先级值从动态选项中获取颜色 */
function getPriorityColorForRecord(priority: string | null | undefined): string {
  const p = priority || 'Normal'
  const opt = priorityOptions.value.find(o => o.value === p || o.value.toLowerCase() === p.toLowerCase())
  return opt?.color || '#6366f1'
}

/** 根据工单类型值从动态选项中获取颜色 */
function getIssueTypeColorForRecord(issueType: string | null | undefined): string {
  const t = issueType || 'Task'
  const opt = issueTypeOptions.value.find(o => o.value === t || o.value.toLowerCase() === t.toLowerCase())
  return opt?.color || '#6366f1'
}

/** 根据工单类型值从动态选项中获取中文标签 */
function getIssueTypeLabelForRecord(issueType: string | null | undefined): string {
  if (!issueType) return '未知'
  const opt = issueTypeOptions.value.find(o => o.value === issueType || o.value.toLowerCase() === issueType.toLowerCase())
  return opt?.label || issueType
}

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
  dueDate: 100,
  estimatedHours: 100,
  spentHours: 100,
  remaining: 100
}

const columnWidths = reactive<Record<string, number>>(loadColumnWidths())

function loadColumnWidths(): Record<string, number> {
  try {
    const stored = localStorage.getItem(COLUMN_WIDTH_STORAGE_KEY)
    if (stored) return { ...DEFAULT_COLUMN_WIDTHS, ...JSON.parse(stored) }
  } catch { /* JSON 解析容错，失败降级为默认列宽 */ }
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

/** 根据 column.dataIndex（格式 "cf_{fieldId}"）从 customFieldDetails 中查找对应字段详情 */
function getCustomFieldDetail(record: any, dataIndex: string): CustomFieldValueVO | undefined {
  if (!record.customFieldDetails || !dataIndex?.startsWith('cf_')) return undefined
  const fieldId = dataIndex.substring(3) // 去掉 "cf_" 前缀
  return record.customFieldDetails.find((d: CustomFieldValueVO) => d.customFieldId === fieldId)
}

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
  toggle(issue.id)
}

// ============================================================
// Context Menu (Right-click)
// ============================================================
const contextMenu = reactive<{
  visible: boolean
  x: number
  y: number
  issue: IssueVO | null
}>({
  visible: false,
  x: 0,
  y: 0,
  issue: null
})

const ctxTransitions = ref<IssueStatusVO[]>([])
const ctxTransitionsLoading = ref(false)
const ctxSprintsLoading = ref(false)
const ctxSprintSubVisible = ref(false)
const ctxSprintGroupsData = ref<{ label: string, items: SprintVO[] }[]>([])
const ctxSprintGroups = computed(() => ctxSprintGroupsData.value)

function openContextMenu(issue: IssueVO, event: MouseEvent) {
  contextMenu.issue = issue
  contextMenu.visible = true
  // Calculate position to keep menu inside viewport
  const menuWidth = 220
  const menuHeight = 300
  const vw = window.innerWidth
  const vh = window.innerHeight
  contextMenu.x = event.clientX + menuWidth > vw ? event.clientX - menuWidth : event.clientX
  contextMenu.y = event.clientY + menuHeight > vh ? event.clientY - menuHeight : event.clientY
  ctxTransitions.value = []
  ctxTransitionsLoading.value = false
  ctxSprintSubVisible.value = false
  // Preload transitions
  loadCtxTransitions(issue)
}

function closeContextMenu() {
  contextMenu.visible = false
  contextMenu.issue = null
  ctxSprintSubVisible.value = false
}

async function loadCtxTransitions(issue: IssueVO) {
  ctxTransitionsLoading.value = true
  try {
    const res = await issueApi.getAvailableTransitions(issue.id)
    if (res.code === 0) {
      ctxTransitions.value = (res.data || []).filter((t: IssueStatusVO) => t.id !== issue.statusId)
    }
  } catch (e) {
    console.error('[IssueList] 加载右键菜单可用转换失败:', e)
  } finally {
    ctxTransitionsLoading.value = false
  }
}

// List layout context menu handler
function onListItemContextMenu({ issue, event }: { issue: IssueVO, event: MouseEvent }) {
  openContextMenu(issue, event)
}

// Table context menu handler — identify row from DOM
function onTableContextMenu(event: MouseEvent) {
  // Walk up the DOM tree to find the table row element
  let target = event.target as HTMLElement | null
  while (target && !target.classList.contains('arco-table-tr')) {
    target = target.parentElement
  }
  if (!target) return
  // Get issue id from data-issue-id attribute (set via row-props)
  const issueId = target.getAttribute('data-issue-id')
  if (!issueId) return
  const issue = issues.value.find(i => i.id === issueId)
  if (!issue) return
  openContextMenu(issue, event)
}

// Table row-contextmenu event from Arco Design
function onTableRowContextMenu(record: IssueWithDesc, event: MouseEvent) {
  event.preventDefault()
  openContextMenu(record, event)
}

function ctxCopyIssueKey() {
  if (!contextMenu.issue) return
  navigator.clipboard.writeText(contextMenu.issue.issueKey || '')
  Message.success(`已复制工单 ID: ${contextMenu.issue.issueKey}`)
  closeContextMenu()
}

function ctxCopyLink() {
  if (!contextMenu.issue) return
  const url = `${window.location.origin}/issues/${contextMenu.issue.issueKey}`
  navigator.clipboard.writeText(url)
  Message.success('已复制工单链接')
  closeContextMenu()
}

function ctxOpenNewTab() {
  if (!contextMenu.issue) return
  window.open(`/issues/${contextMenu.issue.issueKey}`, '_blank')
  closeContextMenu()
}

async function ctxSetStatus(st: IssueStatusVO) {
  if (!contextMenu.issue) return
  const issue = contextMenu.issue
  closeContextMenu()
  try {
    await issueApi.transitStatus(issue.id, st.id, undefined, issue.version)
    Message.success(`状态已更新为 ${localizeStatusName(st.name)}`)
    await refreshList()
    useNavBadge().refresh() // 状态变更后刷新导航栏 badge
  } catch (e: any) {
    Message.error(e?.response?.data?.message || '状态变更失败')
  }
}

async function ctxLoadSprints() {
  if (!contextMenu.issue) return
  ctxSprintSubVisible.value = true
  if (ctxSprintGroupsData.value.length > 0) return // already loaded
  ctxSprintsLoading.value = true
  try {
    const projectId = contextMenu.issue.projectId
    if (!sprintOptionsCache[projectId]) {
      const res = await sprintApi.listByProject(projectId)
      if (res.code === 0) {
        sprintOptionsCache[projectId] = res.data?.list || []
      }
    }
    const sprints = sprintOptionsCache[projectId] || []
    const active = sprints.filter((s: SprintVO) => s.status === 'active')
    const planned = sprints.filter((s: SprintVO) => s.status === 'planned')
    const groups: { label: string, items: SprintVO[] }[] = []
    if (active.length) groups.push({ label: '进行中', items: active })
    if (planned.length) groups.push({ label: '计划中', items: planned })
    ctxSprintGroupsData.value = groups
  } catch (e) {
    console.error('[IssueList] 加载右键菜单 Sprint 列表失败:', e)
  } finally {
    ctxSprintsLoading.value = false
  }
}

function ctxMoveSprint() {
  // Clicking the parent just toggles the sub menu
  ctxSprintSubVisible.value = !ctxSprintSubVisible.value
  if (ctxSprintSubVisible.value) ctxLoadSprints()
}

async function ctxSelectSprint(sprint: SprintVO | null) {
  if (!contextMenu.issue) return
  const issue = contextMenu.issue
  closeContextMenu()
  try {
    await issueApi.update(issue.id, { sprintId: sprint?.id || null, version: issue.version })
    Message.success(sprint ? `已移至 Sprint: ${sprint.name}` : '已移出 Sprint')
    await refreshList()
  } catch (e: any) {
    Message.error(e?.response?.data?.message || 'Sprint 更新失败')
  }
}

// Close context menu on scroll or escape key
function onGlobalKeydownCtx(e: KeyboardEvent) {
  if (e.key === 'Escape') closeContextMenu()
}

// Manual order handlers
async function onManualOrderChange(issueIds: string[]) {
  await saveManualOrder(issueIds)
}

async function onDiscardManualOrder() {
  await discardManualOrder()
  refreshList()
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
  // Keyboard focus (J/K navigation in table mode)
  if (focusedIssueId.value && record.id === focusedIssueId.value) {
    classes.push('issue-keyboard-focused')
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
  if (status.requireComment) {
    // Show a modal to collect comment before transitioning
    let commentText = ''
    Modal.confirm({
      title: '状态变更 — 请填写理由',
      content: () => h('div', { style: 'display:flex;flex-direction:column;gap:8px' }, [
        h('div', { style: 'display:flex;align-items:center;gap:6px' }, [
          h('span', { style: 'color:var(--color-text-3);font-size:13px' }, '目标状态：'),
          h('span', { style: `background:${status.color};color:#fff;padding:2px 8px;border-radius:3px;font-size:12px` }, localizeStatusName(status.name))
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
      onOk: () => {
        performStatusTransition(issue, status, commentText.trim())
      }
    })
  } else {
    performStatusTransition(issue, status, undefined)
  }
}

/** 执行状态转换，处理各种警告（描述为空、WIP 超限、关闭确认、字段校验） */
async function performStatusTransition(issue: IssueVO, status: IssueStatusVO, comment?: string, forceFlags?: { force?: boolean; forceWip?: boolean; forceDescEmpty?: boolean }) {
  const oldStatusId = issue.statusId
  // 乐观更新
  issue.statusId = status.id

  try {
    const res = await issueApi.transitStatus(issue.id, status.id, comment, issue.version, forceFlags?.force, forceFlags?.forceWip, forceFlags?.forceDescEmpty)

    if (res.code === 0) {
      // 检查是否为字段校验失败（状态转换被阻止）
      const actionResult = res.data?.actionResult
      if (actionResult?.outcome === 'FIELD_VALIDATION_FAILED') {
        // 回滚乐观更新
        issue.statusId = oldStatusId
        // 显示警告消息，提供跳转详情页按钮引导用户填写字段
        Modal.warning({
          title: '字段校验',
          content: actionResult.warningMessage || `请先填写「${actionResult.requiredFieldName}」字段`,
          okText: '打开详情填写',
          cancelText: '知道了',
          hideCancel: false,
          onOk: () => {
            router.push(`/issues/${issue.issueKey}`)
          }
        })
        return
      }

      // 成功：同步版本号
      if (res.data != null) {
        const version = extractVersion(res.data)
        if (version != null) {
          issue.version = version
        } else {
          issue.version = (issue.version || 0) + 1
        }
        showActionFeedback(res.data)
      }
      onInlineEditSuccess(issue, 'statusId', status.id)
      return
    }

    // 错误码处理
    issue.statusId = oldStatusId // 回滚

    if (res.code === ERROR_CODES.DESCRIPTION_EMPTY_WARNING) {
      // 描述为空警告
      Modal.warning({
        title: '工单描述为空',
        content: res.message,
        okText: '继续变更',
        cancelText: '取消',
        hideCancel: false,
        onOk: () => performStatusTransition(issue, status, comment, { ...forceFlags, forceDescEmpty: true })
      })
    } else if (res.code === ERROR_CODES.WIP_LIMIT_EXCEEDED) {
      // WIP 超限
      Modal.warning({
        title: 'WIP 限制',
        content: res.message,
        okText: '继续移入',
        cancelText: '取消',
        hideCancel: false,
        onOk: () => performStatusTransition(issue, status, comment, { ...forceFlags, forceWip: true })
      })
    } else if (res.code === ERROR_CODES.CLOSE_CONFIRMATION_REQUIRED) {
      // 关闭确认
      Modal.warning({
        title: '确认关闭',
        content: res.message,
        okText: '强制关闭',
        cancelText: '取消',
        hideCancel: false,
        onOk: () => performStatusTransition(issue, status, comment, { ...forceFlags, force: true })
      })
    } else {
      Message.error({ content: res.message || '状态变更失败', duration: 3000 })
    }
  } catch (e: any) {
    issue.statusId = oldStatusId
    Message.error({ content: e.response?.data?.message || '状态变更失败', duration: 3000 })
  }
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
    () => ({ assigneeId: member?.userId || undefined, assigneeName: member?.displayName || undefined }),
    onInlineEditSuccess
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
      sprintOptionsCache[issue.projectId] = res.data?.list || []
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
  executeEdit(issue.id, 'sprintId', sprint?.id || null, (_signal) => issueApi.update(issue.id, { sprintId: sprint?.id || null, version: issue.version }), undefined, onInlineEditSuccess)
}

// ========== 列表模式 Sprint 内联编辑 ==========
// 用于追踪哪些 issue 的 sprint 选项正在加载（Set<issueId>）
const listSprintLoadingIds = reactive<Set<string>>(new Set())

/**
 * 列表模式：用户点击了 Sprint 字段，加载该项目的 Sprint 选项
 */
async function onListSprintEdit(issue: IssueVO) {
  if (!issue.projectId) return
  // 已有缓存（包括空数组）则直接跳过
  if (sprintOptionsCache[issue.projectId] !== undefined) return
  listSprintLoadingIds.add(issue.id)
  try {
    const res = await sprintApi.listByProject(issue.projectId, { _silent403: true })
    sprintOptionsCache[issue.projectId] = res.data?.list || []
  } catch {
    sprintOptionsCache[issue.projectId] = []
  } finally {
    listSprintLoadingIds.delete(issue.id)
  }
}

/**
/**
 * 列表模式：用户选择了某个 Sprint（或"无 Sprint"）
 */
function onListSprintSelect(issue: IssueVO, sprint: SprintVO | null) {
  const newSprintId = sprint?.id ?? null
  const newSprintName = sprint?.name ?? null
  executeEdit(
    issue.id,
    'sprintId',
    newSprintId,
    (_signal) => issueApi.update(issue.id, { sprintId: newSprintId, version: issue.version }),
    // 乐观更新：同时更新 sprintId 和 sprintName
    (_iss) => ({
      sprintId: newSprintId ?? undefined,
      sprintName: newSprintName ?? undefined
    }),
    onInlineEditSuccess
  )
}

// Inline edit - Priority
function selectPriority(issue: IssueVO, priority: string) {
  priorityDropdowns[issue.id] = false
  executeEdit(issue.id, 'priority', priority, (_signal) => issueApi.update(issue.id, { priority, version: issue.version }), undefined, onInlineEditSuccess)
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
    useNavBadge().refresh() // 批量状态变更后刷新导航栏 badge
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
/**
 * 将小时数（BigDecimal/number）格式化为 "Xh Ym" 格式
 * 例如：2.5 → "2h 30m"，0.75 → "45m"，0 → "—"
 */
function formatHoursCell(hours: number | null | undefined): string {
  if (hours == null || hours <= 0) return '\u2014'
  const totalMins = Math.round(hours * 60)
  const h = Math.floor(totalMins / 60)
  const m = totalMins % 60
  if (h === 0) return `${m}m`
  if (m === 0) return `${h}h`
  return `${h}h ${m}m`
}

/**
 * 计算剩余工时 = estimatedHours - spentHours，并格式化
 */
function formatRemainingCell(record: any): string {
  const estimated = record.estimatedHours ?? 0
  if (estimated <= 0) return '\u2014'
  const spent = record.spentHours ?? 0
  const remaining = estimated - spent
  if (remaining <= 0) return '0h'
  const totalMins = Math.round(remaining * 60)
  const h = Math.floor(totalMins / 60)
  const m = totalMins % 60
  if (h === 0) return `${m}m`
  if (m === 0) return `${h}h`
  return `${h}h ${m}m`
}

/** 已用工时超出预估时高亮红色 */
function getSpentHoursClass(record: any): string {
  const estimated = record.estimatedHours ?? 0
  const spent = record.spentHours ?? 0
  if (estimated > 0 && spent > estimated) return 'time-over-budget'
  return ''
}

/** 剩余工时为负（超出）时高亮红色 */
function getRemainingClass(record: any): string {
  const estimated = record.estimatedHours ?? 0
  const spent = record.spentHours ?? 0
  if (estimated > 0 && spent > estimated) return 'time-over-budget'
  return ''
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
function refreshList() { loadIssues(buildFilters()).then(() => { loadPermissions(); preloadSprintNames(); loadBadgeFieldsForIssues() }) }

/**
 * 检查工单是否仍满足当前筛选条件
 * 用于行内编辑后决定是否从列表中移除工单
 */
function checkIssueMatchesFilter(issue: IssueVO): boolean {
  const fp = globalFilterParams.value

  // 检查负责人筛选
  // assigneeId = 'none' 表示筛选"未分配"的工单
  if (fp.assigneeId === 'none' && issue.assigneeId) {
    return false // 工单已分配，不满足"未分配"条件
  }
  // assigneeId 为具体 ID 时，检查是否匹配
  if (fp.assigneeId && fp.assigneeId !== 'none' && issue.assigneeId !== fp.assigneeId) {
    return false
  }

  // 检查状态筛选
  if (fp.statusId) {
    const statusIds = String(fp.statusId).split(',')
    if (!statusIds.includes(String(issue.statusId))) {
      return false
    }
  }

  // 检查 Sprint 筛选
  if (fp.sprintId) {
    if (fp.sprintId === 'none' && issue.sprintId) {
      return false // 工单已有 Sprint，不满足"无 Sprint"条件
    }
    if (fp.sprintId !== 'none' && issue.sprintId !== fp.sprintId) {
      return false
    }
  }

  // 检查优先级筛选
  if (fp.priority && issue.priority !== fp.priority) {
    return false
  }

  // 检查工单类型筛选
  if (fp.issueType && issue.issueType !== fp.issueType) {
    return false
  }

  // 检查隐藏已解决
  if (fp.hideResolved === 'true' || hideResolved.value) {
    const status = statusCache.value.find(s => s.id === issue.statusId)
    if (status?.isClosed) {
      return false
    }
  }

  return true
}

/**
 * 行内编辑成功后的回调
 * 检查工单是否仍满足筛选条件，不满足则从列表中移除
 * 如果是状态变更，还会刷新导航栏 badge
 */
function onInlineEditSuccess(issue: IssueVO, field: string, _newValue: any) {
  if (!checkIssueMatchesFilter(issue)) {
    removeLocalIssue(issue.id)
  }
  // 状态变更后刷新导航栏 badge（更新待测试工单数等）
  if (field === 'statusId') {
    useNavBadge().refresh()
  }
}

function onRefreshForUpdates() {
  hasNewUpdates.value = false
  refreshList()
}

/** 预加载当前列表中涉及到的 sprint 名称 */
async function preloadSprintNames() {
  // 用户无 sprint:view 权限时跳过，避免触发 403
  if (!canViewSprintGlobal.value) return
  const projectIds = [...new Set(issues.value.map(i => i.projectId).filter(Boolean))]
  const toLoad = projectIds.filter(pid => !sprintOptionsCache[pid])
  await Promise.all(toLoad.map(async (pid) => {
    try {
      const res = await sprintApi.listByProject(pid, { _silent403: true })
      sprintOptionsCache[pid] = res.data?.list || []
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
            valueLabels.push(getIssueTypeLabelForRecord(resolvedValue))
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

/**
 * 解析 Saved Query 的 sortCriteria JSON 并应用到 sortState。
 * sortCriteria 格式: [{"field":"priority","direction":"desc"}]
 * 如果没有有效的排序配置，清除 sortState（回退到后端默认排序）。
 */
function applySavedQuerySort(q: any) {
  if (!q.sortCriteria) {
    sortState.value = { field: null, direction: null }
    return
  }
  try {
    const criteria = typeof q.sortCriteria === 'string' ? JSON.parse(q.sortCriteria) : q.sortCriteria
    if (Array.isArray(criteria) && criteria.length > 0 && criteria[0].field) {
      const dir = criteria[0].direction === 'desc' ? 'desc' : 'asc'
      sortState.value = { field: criteria[0].field, direction: dir }
    } else {
      sortState.value = { field: null, direction: null }
    }
  } catch {
    sortState.value = { field: null, direction: null }
  }
}

function selectQuery(q: any) {
  activeQueryId.value = q.id; activeQueryName.value = q.name; activeQueryObj.value = q; activeProjectId.value = null; activeTagId.value = null; filterProject.value = undefined; searchKeyword.value = ''; globalFilterParams.value = {}; currentPage.value = 1
  const { project, ...rest } = route.query
  router.replace({ query: rest })

  // Apply saved query's sort criteria to sortState
  applySavedQuerySort(q)

  // Persist user's query preference
  localStorage.setItem('tf_last_active_query_id', q.id)
  localStorage.removeItem('tf_last_active_query_all')

  // YouTrack style: clicking a saved query only shows the query name chip in search bar
  // Does NOT expand filter conditions — user must click the chip to see/edit conditions
  filterBarRef.value?.clearAll()

  refreshList()
}
function selectAllProjects() {
  if (activeProjectId.value === null && activeTagId.value === null && activeQueryId.value === null && searchKeyword.value === '') return // Already showing all
  activeProjectId.value = null; activeQueryId.value = null; activeTagId.value = null; activeQueryName.value = '所有工单'; activeQueryObj.value = null; filterProject.value = undefined; searchKeyword.value = ''; globalFilterParams.value = {}; currentPage.value = 1
  sortState.value = { field: null, direction: null }
  const { project, ...rest } = route.query
  router.replace({ query: rest })

  // Clear FilterBar UI (has its own internal searchKeyword state)
  filterBarRef.value?.clearAll()

  // Persist user's preference to show all issues
  localStorage.setItem('tf_last_active_query_all', 'true')
  localStorage.removeItem('tf_last_active_query_id')

  refreshList()
  loadPanel()
  loadTags()
}
function selectProject(p: any) {
  if (activeProjectId.value === p.id) {
    // Toggle off: clicking active project clears the filter
    selectAllProjects()
    return
  }
  // Select project
  activeProjectId.value = p.id; activeQueryId.value = null; activeTagId.value = null; activeQueryName.value = p.name; activeQueryObj.value = null; filterProject.value = p.id; globalFilterParams.value = {}; currentPage.value = 1
  router.replace({ query: { ...route.query, project: p.key } })
  refreshList()
  loadPanel()
  loadTags()
}

watch(currentPage, () => refreshList())
watch(sortState, () => refreshList(), { deep: true })

// Reset keyboard focus when issues list changes (pagination, filter, sort)
watch(issues, () => { focusedIndex.value = -1 })

// Load manual order when context changes
watch([activeProjectId, activeQueryId], () => {
  if (activeProjectId.value) {
    loadManualOrder({ type: 'project', id: activeProjectId.value })
  } else if (activeQueryId.value) {
    loadManualOrder({ type: 'query', id: activeQueryId.value })
  } else {
    resetManualOrder()
  }
})

// Init
const panelLoadFailed = ref(false)

async function loadPanel() {
  try {
    panelLoadFailed.value = false
    const res = await queryApi.getPanel(activeProjectId.value || undefined, hideResolved.value)
    const data = res.data || {}
    savedQueries.value = [...(data.pinned || []), ...(data.queries || [])]
  } catch (error: any) {
    // 会话过期导致的请求取消，静默处理（handleSessionExpired 会处理跳转）
    if (axios.isCancel(error)) return

    panelLoadFailed.value = true
    // Keep previous data if available; only clear if this is the first load
    if (savedQueries.value.length === 0 || savedQueries.value[0]?.id === '1') {
      savedQueries.value = []
    }
    // For 429 specifically, schedule an auto-retry after the retry-after period
    if (error?.response?.status === 429) {
      const retryAfter = parseInt(error.response.headers?.['retry-after'] || '60', 10)
      setTimeout(() => loadPanel(), Math.min(retryAfter, 120) * 1000)
    }
  }
}
async function loadProjects() {
  try { const res = await projectApi.list({ pageSize: 50 }); projectList.value = res.data?.list || [] }
  catch (e) {
    // 会话过期导致的请求取消，静默处理
    if (axios.isCancel(e)) return
    projectList.value = []
  }
}
async function loadTags() {
  try {
    const res = await tagApi.getFavoritePanel(activeProjectId.value || undefined)
    favoriteTags.value = res.data || []
  } catch (e) {
    // 会话过期导致的请求取消，静默处理
    if (axios.isCancel(e)) return
    favoriteTags.value = []
  }
}
function selectTag(tag: TagPanelItemVO) {
  if (activeTagId.value === tag.id) {
    // Deselect tag — back to all
    activeTagId.value = null
    activeQueryId.value = null
    activeQueryName.value = '所有工单'
    activeQueryObj.value = null
    globalFilterParams.value = {}
    currentPage.value = 1
    refreshList()
    return
  }
  activeTagId.value = tag.id
  activeQueryId.value = null
  activeQueryName.value = tag.name
  activeQueryObj.value = null
  activeProjectId.value = null
  filterProject.value = undefined
  currentPage.value = 1
  // Set global filter to filter by tag
  globalFilterParams.value = { tagId: tag.id }
  refreshList()
}
async function openManageTagsModal() {
  showManageTagsModal.value = true
  try {
    const res = await tagApi.listAvailableTags(activeProjectId.value || undefined)
    availableTags.value = res.data || []
  } catch {
    availableTags.value = []
  }
}
async function toggleTagFavorite(tag: AvailableTagVO) {
  try {
    if (tag.favorited) {
      await tagApi.removeFavorite(tag.id)
      tag.favorited = false
    } else {
      await tagApi.addFavorite(tag.id)
      tag.favorited = true
    }
    // Reload tags panel
    await loadTags()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  }
}
async function loadStatuses() {
  try { const res = await issueApi.listStatuses(); statusCache.value = res.data || [] }
  catch (e) {
    // 会话过期导致的请求取消，静默处理
    if (axios.isCancel(e)) return
    statusCache.value = []
  }
}

/**
 * Auto-select the default saved query (first pinned query, typically "分配给我")
 * when the user navigates to the Issues list without explicit URL params.
 * This provides a YouTrack-like "open and see my tasks" experience.
 */

onMounted(async () => {
  // 检查是否有会话过期时保存的恢复草稿 — 保存为正式草稿但不自动打开模态框
  // YouTrack 标准行为：草稿列在侧边栏，用户手动点击才打开创建面板
  const recoveryDraft = consumeSessionRecoveryDraft()
  if (recoveryDraft && recoveryDraft.formData) {
    const formData = recoveryDraft.formData
    // 只有表单有实质内容时才恢复
    if (formData.title?.trim() || formData.description?.trim()) {
      // 保存为正式草稿（持久化到 localStorage）
      const savedId = saveDraft(formData)
      if (savedId) {
        // 展开草稿分组，让用户看到恢复的草稿
        expandedGroups.add('drafts')
        // 设置刚恢复的草稿 ID，用于视觉高亮提示
        recoveredDraftId.value = savedId
        // 延迟显示提示（等页面渲染完成）
        setTimeout(() => {
          Message.info({
            content: '已恢复上次会话过期时的工单草稿，点击左侧草稿区继续编辑',
            duration: 5000
          })
          // 高亮效果 3 秒后自动移除
          setTimeout(() => {
            recoveredDraftId.value = null
          }, 3000)
        }, 500)
      }
    }
  }

  await loadPanel()
  await loadProjects()
  await loadTags()
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

  // Handle dashboard filter params (statusId, statusCode, statusCategory, status, label, sprint, keyword, etc.)
  if (route.query.statusId || route.query.statusCode || route.query.statusCategory || route.query.statusName || route.query.status || route.query.overdue || route.query.dueSoon || route.query.sprint || route.query.reportedByMe || route.query.assignedToMe || route.query.priority || route.query.issueType || route.query.assigneeName || route.query.assignee || route.query.projectId || route.query.keyword) {
    applyDashboardFilter()
  } else if (!route.query.project && !activeProjectId.value) {
    // Restore user's last query preference, or show all issues on first visit (YouTrack standard)
    // Check localStorage for user's last selected query preference
    const lastQueryId = localStorage.getItem('tf_last_active_query_id')
    const lastQueryIsAll = localStorage.getItem('tf_last_active_query_all') === 'true'

    if (lastQueryIsAll) {
      // User explicitly chose "所有工单" last time, respect that
      refreshList()
    } else if (lastQueryId && savedQueries.value.length > 0) {
      // Restore user's last selected query
      const matched = savedQueries.value.find((q: any) => q.id === lastQueryId)
      if (matched) {
        selectQuery(matched)
      } else {
        // Last selected query no longer exists — show all issues
        localStorage.removeItem('tf_last_active_query_id')
        refreshList()
      }
    } else {
      // First visit or no preference — show all issues (YouTrack standard: unfiltered list on first visit)
      refreshList()
    }
  } else {
    refreshList()
  }

  // Listen for undo-restore events from batch delete
  window.addEventListener('trackflow:issues-restored', handleIssuesRestored)

  // Keyboard navigation for preview mode
  document.addEventListener('keydown', handleKeyboardNav)
  // Context menu escape key
  document.addEventListener('keydown', onGlobalKeydownCtx)
})

onUnmounted(() => {
  window.removeEventListener('trackflow:issues-restored', handleIssuesRestored)
  document.removeEventListener('keydown', handleKeyboardNav)
  document.removeEventListener('keydown', onGlobalKeydownCtx)
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

  // status: 通用状态筛选参数（支持英文名、本地化名、状态代码）
  // 这是最用户友好的参数，如 ?status=Code Review 或 ?status=代码审查
  if (route.query.status) {
    const statusParam = String(route.query.status)
    // 尝试按多种方式匹配状态
    const matchedStatus = statusCache.value.find(st =>
      st.name === statusParam ||
      localizeStatusName(st.name) === statusParam ||
      st.code === statusParam
    )
    if (matchedStatus) {
      filters.statusId = matchedStatus.id
      chips.push({
        fieldKey: 'status',
        operator: 'any_of',
        values: [matchedStatus.id],
        valueLabels: [localizeStatusName(matchedStatus.name)]
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

  // assignedToMe: 分配给我的工单
  if (route.query.assignedToMe) {
    filters.assignedToMe = 'true'
    chips.push({
      fieldKey: 'assignee',
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
    // Resolve sprint display name from label param (only when no statusCategory, since
    // combined labels like "Sprint Name - 已完成工单" are meant for the page title, not the chip)
    let sprintDisplayName = ''
    if (route.query.label && !route.query.statusCategory) {
      sprintDisplayName = String(route.query.label)
    }
    if (!sprintDisplayName) {
      const sprintId = String(route.query.sprint)
      for (const sprints of Object.values(sprintOptionsCache)) {
        const matched = sprints.find(s => s.id === sprintId)
        if (matched) { sprintDisplayName = matched.name; break }
      }
    }
    if (!sprintDisplayName) {
      sprintDisplayName = `Sprint #${route.query.sprint}`
    }
    chips.push({
      fieldKey: 'sprint',
      operator: 'equals',
      values: [String(route.query.sprint)],
      valueLabels: [sprintDisplayName]
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
  // assignee={userId}: 筛选某个用户负责的工单
  if (route.query.assignee === 'unassigned') {
    filters.assigneeId = 'none'
    chips.push({
      fieldKey: 'assignee',
      operator: 'equals',
      values: ['none'],
      valueLabels: ['未分配']
    })
  } else if (route.query.assignee) {
    const assigneeUserId = String(route.query.assignee)
    filters.assigneeId = assigneeUserId
  }

  // keyword: 关键字搜索（支持 URL 分享和书签）
  if (route.query.keyword) {
    const keyword = String(route.query.keyword)
    searchKeyword.value = keyword
    // 设置 FilterBar 中的搜索关键字显示
    // 使用 nextTick 确保 FilterBar 已挂载
    nextTick(() => {
      filterBarRef.value?.setSearchKeyword(keyword)
    })
  }

  // project (key) or projectId: 项目筛选（从 Sprint/报表页面跳转时带入）
  // 两个参数都支持项目 Key 和数字 ID 两种格式
  const projectParam = route.query.project || route.query.projectId
  if (projectParam) {
    const queryProject = String(projectParam)
    const matched = projectList.value.find(p => p.key === queryProject || p.id === queryProject)
    if (matched) {
      filterProject.value = matched.id
      activeProjectId.value = matched.id
    }
  }

  // Set display label
  if (route.query.label) {
    activeQueryName.value = String(route.query.label)
  }

  initialFilterChips.value = chips
  globalFilterParams.value = filters
  refreshList()
}

// 路由守卫：离开时检查创建面板是否有未保存数据
onBeforeRouteLeave((_to, _from, next) => {
  const panel = createPanelRef.value
  // 如果用户已经在处理丢弃流程（isDiscarding 为 true），直接放行
  // 避免在丢弃确认框显示期间重复弹出「未保存更改」对话框
  if (panel?.isDiscarding) {
    next()
    return
  }
  if (showCreatePanel.value && panel && panel.isDirty) {
    // 先暂停 beforeunload 监听器，避免 SPA 内部导航时触发浏览器级别的空内容弹窗
    // 让应用内的 Modal.confirm 独占处理用户确认
    panel.suspendBeforeUnload?.()

    Modal.confirm({
      title: '有未保存的更改',
      content: '创建工单表单中有未保存的内容，确定要离开吗？',
      okText: '放弃更改',
      cancelText: '继续编辑',
      simple: false,
      onOk: () => { next() },
      onCancel: () => {
        // 用户取消导航，恢复 beforeunload 监听器（仍需防止浏览器关闭/刷新丢失数据）
        panel.resumeBeforeUnload?.()
        next(false)
      }
    })
  } else {
    next()
  }
})
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

/* Tags section */
.tag-color-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; margin-right: 6px; }
.manage-tags-content { max-height: 360px; overflow-y: auto; }
.manage-tags-list { display: flex; flex-direction: column; gap: 2px; }
.manage-tag-item { display: flex; align-items: center; padding: 8px 12px; border-radius: 4px; cursor: pointer; transition: background 0.15s; }
.manage-tag-item:hover { background: var(--tf-bg-hover); }
.manage-tag-name { flex: 1; font-size: 13px; color: var(--tf-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.manage-tag-action { font-size: 12px; color: var(--tf-accent); flex-shrink: 0; margin-left: 8px; }

/* Drafts section */
.drafts-group { border-bottom: 1px solid var(--tf-border); padding-bottom: 4px; margin-bottom: 4px; }
.draft-count-badge { font-size: 10px; color: var(--tf-text-tertiary); background: var(--tf-bg-elevated); padding: 1px 5px; border-radius: 8px; margin-left: 4px; }
.draft-item { position: relative; }
.draft-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.draft-time { font-size: 10px; color: var(--tf-text-quaternary); flex-shrink: 0; margin-left: 4px; }

/* Draft recovered highlight animation */
.draft-recovered {
  background: var(--tf-accent-bg) !important;
  animation: draft-pulse 1.5s ease-in-out infinite;
}
@keyframes draft-pulse {
  0%, 100% { background: var(--tf-accent-bg); }
  50% { background: var(--tf-bg-hover); }
}
.draft-recovered-badge {
  font-size: 9px;
  color: var(--tf-accent);
  background: rgba(var(--accent-rgb, 88,166,255), 0.15);
  padding: 1px 4px;
  border-radius: 3px;
  margin-left: 4px;
  flex-shrink: 0;
}

.empty-drafts { padding: 12px 8px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 4px; }
.empty-drafts .empty-icon { font-size: 20px; opacity: 0.5; }
.empty-drafts .empty-text { font-size: 12px; color: var(--tf-text-tertiary); }
.empty-drafts .empty-hint { font-size: 11px; color: var(--tf-text-quaternary); line-height: 1.4; }
.drafts-actions { padding: 4px 8px; text-align: center; }
.delete-all-link { font-size: 11px; color: var(--tf-text-tertiary); }
.delete-all-link:hover { color: var(--color-danger-light-4); }

/* Icon picker */
.icon-picker { display: flex; flex-wrap: wrap; gap: 6px; }
.icon-option { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; font-size: 16px; border-radius: 6px; cursor: pointer; border: 1px solid var(--tf-border); transition: all 0.15s; }
.icon-option:hover { background: var(--tf-bg-hover); transform: scale(1.1); }
.icon-option.selected { background: var(--tf-accent-bg); border-color: var(--tf-accent); }
.icon-preview { margin-top: 8px; font-size: 12px; color: var(--tf-text-secondary); }

/* Group action button */
.group-action-btn { margin-left: auto; opacity: 0; transition: opacity 0.15s; }
.group-actions { margin-left: auto; display: flex; gap: 2px; opacity: 0; transition: opacity 0.15s; }
.group-actions .group-action-btn { margin-left: 0; opacity: 1; }
.group-header:hover .group-action-btn { opacity: 1; }
.group-header:hover .group-actions { opacity: 1; }

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

/* Manage projects (favorites) modal */
.manage-projects-content { display: flex; flex-direction: column; gap: 12px; }
.manage-projects-hint { font-size: 12px; color: var(--tf-text-tertiary); margin: 0; }
.manage-projects-search { margin-bottom: 4px; }
.manage-projects-loading { display: flex; justify-content: center; padding: 32px; }
.manage-projects-list { max-height: 400px; overflow-y: auto; display: flex; flex-direction: column; gap: 2px; }
.manage-project-item {
  display: flex; align-items: center; gap: 8px; padding: 8px 12px;
  border-radius: 6px; cursor: pointer; transition: background 100ms;
}
.manage-project-item:hover { background: var(--tf-bg-hover); }
.manage-project-star { font-size: 16px; color: var(--tf-text-tertiary); transition: color 100ms; flex-shrink: 0; }
.manage-project-star.favorited { color: var(--tf-accent); }
.manage-project-info { flex: 1; display: flex; flex-direction: column; gap: 1px; overflow: hidden; }
.manage-project-name { font-size: 13px; color: var(--tf-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.manage-project-key { font-size: 11px; color: var(--tf-text-tertiary); font-family: monospace; }
.manage-project-action { font-size: 12px; color: var(--tf-accent); flex-shrink: 0; white-space: nowrap; }
.manage-projects-empty { text-align: center; padding: 24px; font-size: 13px; color: var(--tf-text-tertiary); }

/* Right area */
.issue-list-area { flex: 1; display: flex; flex-direction: column; min-width: 0; overflow: hidden; }

.filter-bar { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid var(--tf-border); flex-shrink: 0; }
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

.issue-key { color: var(--tf-accent); font-weight: 500; font-size: 12px; text-decoration: none; cursor: pointer; }
.issue-key:hover { text-decoration: underline; }
.issue-title-text { color: var(--tf-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* Tag badges (inline in title cell) */
.issue-tag-badge {
  display: inline-block;
  max-width: 72px;
  font-size: 11px;
  color: #fff;
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

/* Editable cells */
.editable-cell { display: inline-flex; align-items: center; gap: 4px; cursor: pointer; padding: 2px 6px; border-radius: 3px; transition: background 0.15s; font-size: 12px; color: var(--tf-text-secondary); }
.editable-cell:hover { background: var(--tf-bg-hover); }
.readonly-cell { display: inline-flex; align-items: center; gap: 4px; padding: 2px 6px; font-size: 12px; color: var(--tf-text-secondary); cursor: default; }
/* 已完成迭代的 Sprint 名称视觉弱化 */
.sprint-completed { color: var(--tf-text-tertiary); text-decoration: line-through; opacity: 0.7; }
/* 未分配工单视觉高亮 */
.unassigned-cell { color: rgb(var(--warning-6)); }
.unassigned-cell:hover { background: rgba(var(--warning-6), 0.08); }
.unassigned-label { font-style: italic; color: rgb(var(--warning-6)); opacity: 0.9; }
.cell-spinner { font-size: 12px; color: var(--tf-text-tertiary); animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

.status-badge { padding: 2px 8px; border-radius: 3px; font-size: 11px; color: #fff; font-weight: 500; }
.priority-dot { display: inline-block; width: 8px; height: 8px; min-width: 8px; min-height: 8px; flex-shrink: 0; border-radius: 50%; }
.priority-critical { background: var(--tf-danger); }
.priority-high { background: var(--tf-warning); }
.priority-normal { background: var(--tf-accent); }
.priority-low { background: var(--tf-text-tertiary); }
.time-ago { font-size: 11px; color: var(--tf-text-tertiary); }
.time-over-budget { color: var(--tf-danger, #f85149); font-weight: 500; }
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
.member-avatar { width: 24px; height: 24px; border-radius: 50%; background: var(--tf-accent-bg); color: var(--tf-accent); display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 500; flex-shrink: 0; }
.unassigned-icon { width: 24px; text-align: center; color: var(--tf-text-tertiary); }

/* Empty state */
.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 48px 24px; gap: 8px; }
.empty-icon { font-size: 36px; color: var(--tf-text-quaternary); }
.empty-title { font-size: 14px; font-weight: 500; color: var(--tf-text-primary); margin: 0; }
.empty-desc { font-size: 12px; color: var(--tf-text-tertiary); margin: 0 0 8px; }
.error-state .error-icon { color: var(--color-danger-6, #f53f3f); }

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
  outline: 2px solid var(--tf-accent, #58a6ff);
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
  background: var(--color-primary-light-1, rgba(88, 166, 255, 0.1));
  border: 1px solid var(--tf-accent, #58a6ff);
  border-radius: 4px;
  margin: 0 0 8px;
  cursor: pointer;
  transition: background 0.15s;
}
.realtime-update-bar:hover {
  background: var(--color-primary-light-2, rgba(88, 166, 255, 0.15));
}
.realtime-update-text {
  font-size: 13px;
  color: var(--tf-accent, #58a6ff);
  font-weight: 500;
}
.realtime-update-icon {
  color: var(--tf-accent, #58a6ff);
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
  background: var(--tf-bg-elevated, #2a2d33);
  border: 1px solid var(--tf-border, rgba(255,255,255,0.1));
  border-radius: 6px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.3);
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
  background: var(--tf-bg-elevated, #2a2d33);
  border: 1px solid var(--tf-border, rgba(255,255,255,0.1));
  border-radius: 6px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.3);
  padding: 4px 0;
  z-index: 1001;
}

/* Table wrapper for context menu */
.issue-table-wrapper {
  flex: 1;
  overflow: auto;
  display: flex;
  flex-direction: column;
}
</style>
