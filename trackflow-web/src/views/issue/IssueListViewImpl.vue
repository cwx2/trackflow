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
        :width="440"
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
          <a-form-item label="查询条件">
            <QueryInput
              v-model="createQueryForm.queryText"
              placeholder="输入查询条件... (如 状态: 未关闭  负责人: 我)"
              :status-list="statusCache"
              :project-list="projectList"
              :project-id="activeProjectId"
            />
          </a-form-item>
          <a-form-item label="固定到面板顶部">
            <a-switch v-model="createQueryForm.pinned" />
          </a-form-item>
          <a-form-item label="共享">
            <a-switch v-model="createQueryForm.shared" />
            <span class="form-help-text">共享后其他项目成员也能看到此查询</span>
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
              <IssuePriorityBadge :priority="p.value" :color="p.color" mode="dot" :show-label="true" />
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
                <IssuePriorityBadge :priority="record.priority" mode="dot" :show-label="true" />
                <icon-loading v-if="isCellEditing(record.id, 'priority')" class="cell-spinner" />
              </span>
              <template #content>
                <div class="inline-dropdown">
                  <div v-for="p in priorityOptions" :key="p.value" class="dropdown-item" @click="selectPriority(record, p.value)">
                    <IssuePriorityBadge :priority="p.value" :color="p.color" mode="dot" :show-label="true" />
                  </div>
                </div>
              </template>
            </a-trigger>
            <span v-else class="readonly-cell">
              <IssuePriorityBadge :priority="record.priority" mode="dot" :show-label="true" />
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
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { projectApi, issueApi, sprintApi, customFieldApi } from '@/api'
import type { IssueVO, IssueStatusVO, ProjectMemberVO, SprintVO, CustomFieldValueVO } from '@/api/types'
import type { TableData } from '@arco-design/web-vue'
import { useAuthStore } from '@/stores/auth'
import { localizeStatusName, queryFieldKeyToLabel } from '@/utils/fieldLabels'
import { DEFAULT_PRIORITY_OPTIONS, DEFAULT_PRIORITY_COLOR } from '@/composables/usePriorityOptions'
import { DEFAULT_ISSUE_TYPE_OPTIONS, DEFAULT_ISSUE_TYPE_COLOR } from './composables/useIssueTypeOptions'
import { extractVersion, showActionFeedback } from '@/utils/transition'
import { ERROR_CODES } from '@/api/error-codes'
import { IssuePriorityBadge, UserAvatar } from '@/components/base'
import {
  useIssueList, useSelection, useInlineEdit, useBatchOps, usePermission,
  useColumnConfig, useViewSettings, useManualOrder, useDrafts,
  useQueryPanel, useKeyboardNav, useContextMenu, useIssueExport,
  useDashboardFilter, useProjectTagPanel, useTableConfig
} from './composables'
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

const router = useRouter()
const route = useRoute()

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
  isTreeMode, isListLayout, isTableLayout,
  setLayout, setDensity, setStructure
} = useViewSettings()

const {
  isManualSorted, isOwnerOrder, manualOrderData,
  loadManualOrder, saveOrder: saveManualOrder, discardOrder: discardManualOrder, reset: resetManualOrder
} = useManualOrder()

// Auth & permissions
const authStore = useAuthStore()
const canCreateIssueGlobal = authStore.canCreateIssue

const canBatchOps = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  if (authStore.permissionsLoaded) return authStore.hasGlobalPermission('nav:batch_ops')
  return true
})

const canViewSprintGlobal = computed(() => {
  if (authStore.hasGlobalPermission('system:admin')) return true
  if (authStore.permissionsLoaded) return authStore.hasGlobalPermission('nav:sprint_view') || authStore.hasGlobalPermission('nav:sprint_manage')
  return true
})

// Shared state (declared early for composable dependencies)
const activeProjectId = ref<string | null>(null)
const filterBarRef = ref<InstanceType<typeof FilterBar> | null>(null)
const filterProject = ref<string | undefined>(undefined)
const searchKeyword = ref('')
const globalFilterParams = ref<Record<string, any>>({})
const initialFilterChips = ref<any[]>([])
const statusCache = ref<IssueStatusVO[]>([])
const sprintOptionsCache = reactive<Record<string, SprintVO[]>>({})

// Priority & issue type options
const priorityOptions = ref(DEFAULT_PRIORITY_OPTIONS.map(o => ({ ...o })))
const issueTypeOptions = ref(DEFAULT_ISSUE_TYPE_OPTIONS.map(o => ({ ...o })))

// Column config
const {
  visibleColumns, toggleColumn, reorderColumn,
  standardColumns, customFieldColumns, isVisible: isColumnVisible, resetToDefault: resetColumns
} = useColumnConfig(activeProjectId as any)

// ===== Project & Tag panel composable =====
const {
  projectList, favoriteProjects, showManageProjectsModal, manageProjectSearch,
  manageProjectsLoading, filteredManageProjects,
  loadProjects, openManageProjectsModal, toggleProjectFavorite,
  favoriteTags, activeTagId, showManageTagsModal, availableTags,
  loadTags, openManageTagsModal, toggleTagFavorite
} = useProjectTagPanel({ activeProjectId })

// Hide resolved toggle
const HIDE_RESOLVED_KEY = 'trackflow:hide-resolved'
const hideResolved = ref(localStorage.getItem(HIDE_RESOLVED_KEY) === 'true')

function toggleHideResolved() {
  hideResolved.value = !hideResolved.value
  localStorage.setItem(HIDE_RESOLVED_KEY, String(hideResolved.value))
  currentPage.value = 1
  refreshList()
  loadPanel()
}

// ===== Query Panel composable =====
const {
  savedQueries, activeQueryId, activeQueryName, activeQueryObj,
  expandedGroups, panelSearch, panelLoadFailed, panelWidth,
  filteredQueries,
  showCreateQueryModal, createQueryLoading, queryIconOptions, createQueryForm,
  showEditQueryModal, editQueryLoading, editQueryForm,
  showRenameQueryModal, renameQueryLoading, renameQueryForm,
  showManageQueriesModal, manageQuerySearch, filteredManageQueries,
  loadPanel, isOwnQuery, isOwnQueryById,
  openCreateQueryModal, handleCreateQuery, confirmDeleteQuery,
  openEditQueryModal, handleEditQuery, openRenameQueryModal, handleRenameQuery,
  toggleQueryShared, toggleQueryPinned,
  openManageQueriesModal, toggleFavorite, handleRemoveFavorite,
  triggerContextMenu, startPanelResize, togglePanelCollapse,
  selectTag: queryPanelSelectTag,
  filtersToQueryText, queryTextToFilters, resolveValueToId
} = useQueryPanel({
  statusCache,
  projectList,
  issueTypeOptions,
  priorityOptions,
  activeProjectId,
  hideResolved,
  refreshList,
  getIssueTypeLabelForRecord
})

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
function onPreviewGoDetail(issueId: string) { previewVisible.value = false; router.push({ name: 'IssueDetail', params: { id: issueId } }) }

// ===== Keyboard Nav composable =====
const showCommandDialog = ref(false)
const showShortcutsHelp = ref(false)
const showCreatePanel = ref(false)

const {
  focusedIndex, focusedIssueId, handleKeyboardNav, navigateIssue
} = useKeyboardNav({
  issues, canCreateIssueGlobal, canBatchOps,
  previewMode, previewVisible, previewIssueId, activeIssueIndex,
  showCommandDialog, showCreatePanel, showShortcutsHelp, selectedCount,
  toggle, toggleAll, openPreview, closePreview, onPreviewGoDetail
})

// ===== Context Menu composable =====
const {
  contextMenu, ctxTransitions, ctxTransitionsLoading,
  ctxSprintsLoading, ctxSprintSubVisible, ctxSprintGroups,
  openContextMenu, closeContextMenu,
  onListItemContextMenu, onTableRowContextMenu,
  ctxCopyIssueKey, ctxCopyLink, ctxOpenNewTab,
  ctxSetStatus, ctxLoadSprints, ctxMoveSprint, ctxSelectSprint,
  onGlobalKeydownCtx
} = useContextMenu({ refreshList, updateLocalIssue, sprintOptionsCache, canEditIssue })

// ===== Table Config composable =====
const {
  columnWidths, tableMinWidth, tableColumns, rowSelection,
  onColumnResize, onHeaderSort, onHeaderRemove, onHeaderDragDrop,
  getColumnSortDir, isColumnFixed, isColumnSortable,
  isResolved, getStatusName, getStatusColor, getSprintName,
  getDueDateStatus, getDueDateTooltip, getRowClass,
  formatHoursCell, formatRemainingCell, getSpentHoursClass, getRemainingClass
} = useTableConfig({
  visibleColumns, canBatchOps, statusCache, sortState,
  previewMode, previewVisible, previewIssueId, focusedIssueId,
  sprintOptionsCache, toggleColumn, reorderColumn
})

// ===== Drafts =====
const { draftList, draftCount, hasDrafts, saveDraft, deleteDraft, deleteAllDrafts, getDraft } = useDrafts()
const activeDraftId = ref<string | null>(null)
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
function openDraftCreate() { activeDraftId.value = null; showCreatePanel.value = true }
function openDraft(draft: IssueDraft) { activeDraftId.value = draft.id; showCreatePanel.value = true }
function handleDeleteDraft(draftId: string) { deleteDraft(draftId); Message.success('草稿已删除') }
function handleDeleteAllDrafts() {
  const { confirmDangerDelete } = useConfirmDelete()
  confirmDangerDelete({ itemName: `全部 ${draftCount.value} 个草稿`, impactDescription: '删除后无法恢复', confirmText: '全部删除', onConfirm: () => { deleteAllDrafts(); Message.success('所有草稿已删除') } })
}
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
    priorityOptions.value = loaded.map(o => ({ value: o.value, label: o.label, color: o.color || DEFAULT_PRIORITY_COLOR }))
    const loadedTypes = await loadIssueTypeOptions(projectId)
    issueTypeOptions.value = loadedTypes.map(o => ({ value: o.value, label: o.label, color: o.color || DEFAULT_ISSUE_TYPE_COLOR }))
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
          h('span', { style: `background:${status.color};color: var(--tf-text-on-accent);padding:2px 8px;border-radius:3px;font-size:12px` }, localizeStatusName(status.name))
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
function selectSprint(issue: IssueVO, sprint: SprintVO | null) { sprintDropdowns[issue.id] = false; executeEdit(issue.id, 'sprintId', sprint?.id || null, (_signal) => issueApi.update(issue.id, { sprintId: sprint?.id || null, version: issue.version }), undefined, onInlineEditSuccess) }
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
  executeEdit(issue.id, 'sprintId', newSprintId, (_signal) => issueApi.update(issue.id, { sprintId: newSprintId, version: issue.version }), (_iss) => ({ sprintId: newSprintId ?? undefined, sprintName: newSprintName ?? undefined }), onInlineEditSuccess)
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
function formatCount(count: number) { if (count >= 10000) return Math.floor(count / 1000) + 'k+'; if (count >= 1000) return (count / 1000).toFixed(1) + 'k'; return String(count) }
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
    const currentUserId = authStore.user?.id
    if (currentUserId && String(event.operatorId) === String(currentUserId)) return
    if (event.action === 'FIELD_UPDATED') {
      const idx = issues.value.findIndex(i => String(i.id) === String(event.issueId))
      if (idx !== -1) { const patch: Partial<IssueVO> = {}; for (const [key, value] of Object.entries(event.changes)) { ;(patch as any)[key] = value }; updateLocalIssue(String(event.issueId), patch) }
      else { hasNewUpdates.value = true }
    } else if (event.action === 'CREATED') { hasNewUpdates.value = true }
    else if (event.action === 'DELETED') { const idx = issues.value.findIndex(i => String(i.id) === String(event.issueId)); if (idx !== -1) { issues.value.splice(idx, 1); totalIssues.value = Math.max(0, totalIssues.value - 1) } }
  }
)
function onRefreshForUpdates() { hasNewUpdates.value = false; refreshList() }

// ===== Navigation & Selection Computed =====
const isDraggable = computed(() => isListLayout.value && (!!activeProjectId.value || !!activeQueryId.value))
const sortedIssueIds = computed(() => manualOrderData.value?.issueIds || [])
const activeQueryOwned = computed(() => { if (!activeQueryObj.value) return false; return isOwnQuery(activeQueryObj.value) })
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
  if (!activeQueryObj.value?.filters) return []
  let filters: any[]; if (typeof activeQueryObj.value.filters === 'string') { try { filters = JSON.parse(activeQueryObj.value.filters) } catch { return [] } } else { filters = activeQueryObj.value.filters }
  if (!Array.isArray(filters)) return []
  const operatorLabels: Record<string, string> = { eq: '=', neq: '≠', in: '∈', not_in: '∉', contains: '包含', open: '未关闭' }
  return filters.map((f: any) => {
    const fieldLabel = queryFieldKeyToLabel[f.field] || f.field; const op = f.operator
    if (op === 'open') return `${fieldLabel}: 未关闭`
    let values: string
    if (Array.isArray(f.value)) { values = f.value.map((v: string) => { if (v === '${currentUser}') return '我'; if (f.field === 'type') return getIssueTypeLabelForRecord(v); return v }).join(', ') } else { values = String(f.value || '') }
    const opLabel = (op && op !== 'eq') ? ` ${operatorLabels[op] || op}` : ':'
    return `${fieldLabel}${opLabel} ${values}`
  }).filter(l => l && l.trim())
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
function onGlobalSearch(keyword: string) { searchKeyword.value = keyword; globalFilterParams.value = {}; currentPage.value = 1; refreshList() }
function onGlobalFilter(filters: Record<string, any>) {
  searchKeyword.value = ''; globalFilterParams.value = filters
  if (activeQueryId.value) { activeQueryId.value = null; activeQueryName.value = '所有工单'; activeQueryObj.value = null }
  currentPage.value = 1; refreshList()
}
function onClearQuery() {
  activeQueryId.value = null; activeQueryName.value = '所有工单'; activeQueryObj.value = null; searchKeyword.value = ''; globalFilterParams.value = {}; currentPage.value = 1
  const { project, ...rest } = route.query; router.replace({ query: project ? { project } : {} })
  filterBarRef.value?.clearAll()
  localStorage.setItem('tf_last_active_query_all', 'true'); localStorage.removeItem('tf_last_active_query_id')
  refreshList()
}
function onQueryChipClick() { if (activeQueryObj.value && isOwnQuery(activeQueryObj.value)) openEditQueryModal(activeQueryObj.value) }

function selectQuery(q: any) {
  activeQueryId.value = q.id; activeQueryName.value = q.name; activeQueryObj.value = q; activeProjectId.value = null; activeTagId.value = null; filterProject.value = undefined; searchKeyword.value = ''; globalFilterParams.value = {}; currentPage.value = 1
  const { project, ...rest } = route.query; router.replace({ query: rest })
  // Apply saved query sort criteria
  if (q.sortCriteria) { try { const criteria = typeof q.sortCriteria === 'string' ? JSON.parse(q.sortCriteria) : q.sortCriteria; if (Array.isArray(criteria) && criteria.length > 0 && criteria[0].field) { sortState.value = { field: criteria[0].field, direction: criteria[0].direction === 'desc' ? 'desc' : 'asc' } } else { sortState.value = { field: null, direction: null } } } catch { sortState.value = { field: null, direction: null } } } else { sortState.value = { field: null, direction: null } }
  localStorage.setItem('tf_last_active_query_id', q.id); localStorage.removeItem('tf_last_active_query_all')
  filterBarRef.value?.clearAll(); refreshList()
}
function selectAllProjects() {
  if (activeProjectId.value === null && activeTagId.value === null && activeQueryId.value === null && searchKeyword.value === '') return
  activeProjectId.value = null; activeQueryId.value = null; activeTagId.value = null; activeQueryName.value = '所有工单'; activeQueryObj.value = null; filterProject.value = undefined; searchKeyword.value = ''; globalFilterParams.value = {}; currentPage.value = 1
  sortState.value = { field: null, direction: null }; const { project, ...rest } = route.query; router.replace({ query: rest })
  filterBarRef.value?.clearAll(); localStorage.setItem('tf_last_active_query_all', 'true'); localStorage.removeItem('tf_last_active_query_id')
  refreshList(); loadPanel(); loadTags()
}
function selectProject(p: any) {
  if (activeProjectId.value === p.id) { selectAllProjects(); return }
  activeProjectId.value = p.id; activeQueryId.value = null; activeTagId.value = null; activeQueryName.value = p.name; activeQueryObj.value = null; filterProject.value = p.id; globalFilterParams.value = {}; currentPage.value = 1
  router.replace({ query: { ...route.query, project: p.key } }); refreshList(); loadPanel(); loadTags()
}
function selectTag(tag: any) { queryPanelSelectTag(tag); activeProjectId.value = null; filterProject.value = undefined; currentPage.value = 1; globalFilterParams.value = tag.id ? { tagId: tag.id } : {}; refreshList() }
function onFilterChange() {
  currentPage.value = 1
  if (filterProject.value) { const matched = projectList.value.find(p => p.id === filterProject.value); activeProjectId.value = filterProject.value; activeQueryName.value = matched?.name || '所有工单'; router.replace({ query: { ...route.query, project: matched?.key || filterProject.value } }) }
  else { activeProjectId.value = null; activeQueryName.value = '所有工单'; const { project, ...rest } = route.query; router.replace({ query: rest }) }
  refreshList()
}

// ===== Table/List event handlers =====
function onRowClick(record: TableData) { if (previewMode.value === 'sidebar') { const index = issues.value.findIndex(i => i.id === record.id); openPreview(record as unknown as IssueVO, index) } else { router.push(`/issues/${record.issueKey}`) } }
function onRowDblClick(record: TableData) { router.push(`/issues/${record.issueKey}`) }
function onListItemClick(issue: IssueVO) { if (previewMode.value === 'sidebar') { const index = issues.value.findIndex(i => i.id === issue.id); openPreview(issue, index) } else { router.push(`/issues/${issue.issueKey}`) } }
function onListItemDblClick(issue: IssueVO) { router.push(`/issues/${issue.issueKey}`) }
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

// ===== Lifecycle =====
onMounted(async () => {
  const recoveryDraft = consumeSessionRecoveryDraft()
  if (recoveryDraft && recoveryDraft.formData) {
    const formData = recoveryDraft.formData
    if (formData.title?.trim() || formData.description?.trim()) {
      const savedId = saveDraft(formData)
      if (savedId) { expandedGroups.add('drafts'); recoveredDraftId.value = savedId
        setTimeout(() => { Message.info({ content: '已恢复上次会话过期时的工单草稿，点击左侧草稿区继续编辑', duration: 5000 }); setTimeout(() => { recoveredDraftId.value = null }, 3000) }, 500)
      }
    }
  }
  await loadPanel(); await loadProjects(); await loadTags(); await loadStatuses()
  if (route.query.project) { const queryProject = String(route.query.project); const matched = projectList.value.find(p => p.key === queryProject || p.id === queryProject); if (matched) { activeProjectId.value = matched.id; activeQueryName.value = matched.name; filterProject.value = matched.id } else { activeProjectId.value = queryProject } }
  if (activeProjectId.value && projectList.value.length > 0 && !filterProject.value) { const matched = projectList.value.find(p => p.id === activeProjectId.value); if (matched) { activeQueryName.value = matched.name; filterProject.value = matched.id } }
  if (hasDashboardFilterParams()) { applyDashboardFilter() }
  else if (!route.query.project && !activeProjectId.value) {
    const lastQueryId = localStorage.getItem('tf_last_active_query_id'); const lastQueryIsAll = localStorage.getItem('tf_last_active_query_all') === 'true'
    if (lastQueryIsAll) { refreshList() } else if (lastQueryId && savedQueries.value.length > 0) { const matched = savedQueries.value.find((q: any) => q.id === lastQueryId); if (matched) selectQuery(matched); else { localStorage.removeItem('tf_last_active_query_id'); refreshList() } } else { refreshList() }
  } else { refreshList() }
  window.addEventListener('trackflow:issues-restored', handleIssuesRestored)
  document.addEventListener('keydown', handleKeyboardNav)
  document.addEventListener('keydown', onGlobalKeydownCtx)
})
onUnmounted(() => { window.removeEventListener('trackflow:issues-restored', handleIssuesRestored); document.removeEventListener('keydown', handleKeyboardNav); document.removeEventListener('keydown', onGlobalKeydownCtx) })
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

/* Left panel */
.query-panel { background: var(--tf-bg-surface); overflow-y: auto; overflow-x: hidden; flex-shrink: 0; display: flex; flex-direction: column; transition: width 0.2s ease; }
.panel-resizer { width: 4px; flex-shrink: 0; cursor: col-resize; background: transparent; position: relative; z-index: 2; transition: background 0.15s; }
.panel-resizer:hover, .panel-resizer:active { background: var(--tf-accent); }
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
.query-delete-btn:hover { color: var(--tf-danger); background: var(--tf-danger-bg); }

/* Query action button (⋯) */
.query-action-btn { font-size: 14px; color: var(--tf-text-quaternary); cursor: pointer; padding: 2px 4px; border-radius: 3px; opacity: 0; transition: opacity 0.15s, color 0.15s, background 0.15s; flex-shrink: 0; line-height: 1; }
.query-item:hover .query-action-btn { opacity: 1; }
.query-action-btn:hover { color: var(--tf-text-primary); background: var(--tf-bg-hover); }

/* Context menu delete option */
.query-ctx-delete { color: var(--tf-danger) !important; }
.query-ctx-delete:hover { background: var(--tf-danger-bg) !important; }

/* Create query modal */


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
