<template>
  <AdminPageLayout title="自定义字段管理">
    <template #actions>
      <a-button v-if="activeTab === 'list'" type="primary" size="small" @click="openCreate">
        <template #icon><icon-plus /></template>
        创建自定义字段
      </a-button>
    </template>

    <!-- 双标签页 -->
    <a-tabs v-model:active-key="activeTab" size="small" class="cf-tabs">
      <a-tab-pane key="list" title="字段列表">
        <!-- 字段列表 + 详情侧边栏 -->
        <div class="cf-body">
          <AdminDataTable
            :class="{ 'has-detail': !!selectedField }"
            :data="filteredFieldList"
            :loading="loading"
            :total="pagination.total"
            v-model:current="pagination.current"
            v-model:page-size="pagination.pageSize"
            v-model:search-keyword="searchKeyword"
            v-model:selected-keys="selectedKeys"
            :selectable="true"
            :page-size-options="[20, 50, 100]"
            search-placeholder="搜索字段名称..."
            empty-title="暂无自定义字段"
            empty-description="点击右上角「创建自定义字段」添加第一个字段"
            @page-change="onPageChange"
            @page-size-change="onPageSizeChange"
            @search="onSearch"
            @row-click="onRowClick"
          >
            <!-- 筛选器 -->
            <template #toolbar-filters>
              <a-select
                v-model="filterFieldFormat"
                placeholder="按类型筛选"
                size="small"
                allow-clear
                style="width: 160px"
                @change="onFilterChange"
              >
                <a-option value="">全部类型</a-option>
                <a-option v-for="t in fieldTypeOptions" :key="t.value" :value="t.value">{{ t.label }}</a-option>
              </a-select>
            </template>

            <!-- 批量操作 -->
            <template #toolbar-batch="{ count }">
              <span class="batch-count">已选 {{ count }} 项</span>
              <a-button size="mini" type="outline" @click="batchToggleAutoAttach(true)">
                <template #icon><icon-check /></template>
                启用 Auto-attach
              </a-button>
              <a-button size="mini" type="outline" @click="batchToggleAutoAttach(false)">
                禁用 Auto-attach
              </a-button>
              <a-button size="mini" type="outline" @click="batchTogglePrivate(true)">
                <template #icon><icon-lock /></template>
                设为私有
              </a-button>
              <a-button size="mini" type="outline" @click="batchTogglePrivate(false)">
                <template #icon><icon-unlock /></template>
                取消私有
              </a-button>
              <a-button size="mini" type="outline" @click="batchToggleHidden(true)">
                <template #icon><icon-eye-invisible /></template>
                隐藏于列表
              </a-button>
              <a-button size="mini" type="outline" @click="batchToggleHidden(false)">
                <template #icon><icon-eye /></template>
                显示于列表
              </a-button>
              <a-button size="mini" type="outline" status="danger" @click="batchDeleteConfirm">
                <template #icon><icon-delete /></template>
                批量删除
              </a-button>
              <a-button size="mini" type="text" @click="selectedKeys = []">
                取消选择
              </a-button>
            </template>

            <!-- 表格列 -->
            <template #columns>
              <a-table-column title="字段名称" data-index="name" :width="180">
                <template #cell="{ record }">
                  <span class="clickable-name">{{ record.name }}</span>
                  <a-tag v-if="record.isBuiltIn" size="small" color="arcoblue" style="margin-left: 6px">内置</a-tag>
                </template>
              </a-table-column>
              <a-table-column title="类型" data-index="fieldFormat" :width="100">
                <template #cell="{ record }">
                  <a-tag size="small">{{ formatTypeLabel(record.fieldFormat) }}</a-tag>
                </template>
              </a-table-column>
              <a-table-column title="默认值" :width="120">
                <template #cell="{ record }">
                  <span v-if="record.defaultValue" class="default-value-cell">{{ record.defaultValue }}</span>
                  <span v-else class="text-muted">—</span>
                </template>
              </a-table-column>
              <a-table-column title="选项值" :width="260">
                <template #cell="{ record }">
                  <template v-if="(record.fieldFormat === 'list' || record.fieldFormat === 'state' || record.fieldFormat === 'ownedField' || record.fieldFormat === 'version' || record.fieldFormat === 'build') && record.options && record.options.length > 0">
                    <div class="options-inline">
                      <template v-for="(opt, idx) in record.options.filter(o => !o.isArchived).slice(0, MAX_INLINE_OPTIONS)" :key="opt.id">
                        <span
                          class="option-inline-tag"
                          :style="opt.color ? { background: opt.color + '26', color: opt.color, borderColor: opt.color + '66' } : {}"
                        >{{ opt.value }}</span>
                      </template>
                      <span
                        v-if="record.options.filter(o => !o.isArchived).length > MAX_INLINE_OPTIONS"
                        class="option-more-tag"
                      >+{{ record.options.filter(o => !o.isArchived).length - MAX_INLINE_OPTIONS }}</span>
                    </div>
                  </template>
                  <span v-else class="text-muted">—</span>
                </template>
              </a-table-column>
              <a-table-column title="属性" :width="140">
                <template #cell="{ record }">
                  <div class="field-badges">
                    <span v-if="record.isRequired" class="field-badge field-badge--required">必填</span>
                    <span v-if="record.isForAll" class="field-badge field-badge--global">全局</span>
                    <span v-if="record.isPrivate" class="field-badge field-badge--private">私有</span>
                    <span v-if="record.isHiddenInList" class="field-badge field-badge--hidden">隐藏</span>
                  </div>
                </template>
              </a-table-column>
              <a-table-column title="使用项目" :width="140">
                <template #cell="{ record }">
                  <a-tooltip v-if="record.isForAll" :content="`全局字段，适用于系统全部 ${projectList.length} 个项目`">
                    <span class="project-usage-tag project-usage-global">
                      <icon-apps size="12" />
                      全部 {{ projectList.length }} 个
                    </span>
                  </a-tooltip>
                  <a-tooltip
                    v-else-if="(record.projectIds || []).length > 0"
                    :content="getProjectNamesText(record.projectIds)"
                  >
                    <span class="project-usage-tag project-usage-specific">
                      <icon-folder size="12" />
                      {{ (record.projectIds || []).length }} 个项目
                    </span>
                  </a-tooltip>
                  <span v-else class="text-muted text-xs">未使用</span>
                </template>
              </a-table-column>
              <a-table-column title="适用类型" :width="140">
                <template #cell="{ record }">
                  <span v-if="!record.issueTypes || record.issueTypes.length === 0" class="text-muted">所有类型</span>
                  <span v-else>{{ record.issueTypes.map(t => localizeIssueType(t)).join(', ') }}</span>
                </template>
              </a-table-column>
              <a-table-column title="操作" :width="120" align="center">
                <template #cell="{ record }">
                  <a-button type="text" size="mini" @click.stop="openEdit(record)">编辑</a-button>
                  <a-tooltip v-if="record.isBuiltIn" content="内置字段不可删除">
                    <a-button type="text" size="mini" status="danger" disabled>删除</a-button>
                  </a-tooltip>
                  <a-button v-else type="text" size="mini" status="danger" @click.stop="confirmDelete(record)">删除</a-button>
                </template>
              </a-table-column>
            </template>
          </AdminDataTable>

          <!-- 字段详情侧边栏 -->
          <div v-if="selectedField" class="cf-detail-sidebar">
            <div class="detail-header">
              <h3 class="detail-title">{{ selectedField.name }}</h3>
              <a-button type="text" size="mini" @click="selectedField = null">
                <icon-close />
              </a-button>
            </div>
            <div class="detail-body">
              <div class="detail-row">
                <span class="detail-label">类型</span>
                <span class="detail-value">{{ formatTypeLabel(selectedField.fieldFormat) }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">必填</span>
                <span class="detail-value">{{ selectedField.isRequired ? '是' : '否' }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">全局可用</span>
                <span class="detail-value">{{ selectedField.isForAll ? '是' : '否' }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">私有字段</span>
                <span class="detail-value" :style="selectedField.isPrivate ? 'color: var(--tf-warning)' : ''">
                  {{ selectedField.isPrivate ? '是（仅授权用户可见）' : '否' }}
                </span>
              </div>
              <div v-if="selectedField.aliases" class="detail-row">
                <span class="detail-label">别名</span>
                <span class="detail-value">{{ selectedField.aliases }}</span>
              </div>
              <div v-if="selectedField.isMulti" class="detail-row">
                <span class="detail-label">多值</span>
                <span class="detail-value">是</span>
              </div>
              <div v-if="selectedField.defaultValue" class="detail-row">
                <span class="detail-label">默认值</span>
                <span class="detail-value">{{ selectedField.defaultValue }}</span>
              </div>
              <div v-if="!selectedField.isForAll" class="detail-row">
                <span class="detail-label">适用项目</span>
                <span class="detail-value">{{ (selectedField.projectIds || []).length }} 个</span>
              </div>
              <div v-if="!selectedField.isForAll && (selectedField.projectIds || []).length > 0" class="detail-projects">
                <span
                  v-for="pid in selectedField.projectIds"
                  :key="pid"
                  class="detail-project-tag"
                >{{ getProjectName(pid) }}</span>
              </div>
              <div v-if="selectedField.isForAll" class="detail-row">
                <span class="detail-label">适用项目</span>
                <span class="detail-value">全部 {{ projectList.length }} 个（全局）</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">适用类型</span>
                <span class="detail-value">
                  {{ (!selectedField.issueTypes || selectedField.issueTypes.length === 0) ? '所有' : selectedField.issueTypes.map(t => localizeIssueType(t)).join(', ') }}
                </span>
              </div>
              <div v-if="selectedField.options && selectedField.options.length > 0" class="detail-section">
                <span class="detail-label">选项列表</span>
                <div class="detail-options-header">
                  <a-switch
                    v-model="showArchivedInDetail"
                    size="small"
                  />
                  <span class="detail-options-toggle-label">显示已归档</span>
                </div>
                <div class="detail-options">
                  <a-tag
                    v-for="opt in detailFilteredOptions"
                    :key="opt.id"
                    size="small"
                    :color="opt.color || undefined"
                    :class="{ 'option-archived-tag': opt.isArchived }"
                  >{{ opt.value }}<template v-if="opt.isArchived"> (归档)</template></a-tag>
                </div>
              </div>
              <div v-if="detailUsage" class="detail-section">
                <span class="detail-label">使用统计</span>
                <div class="detail-stats">
                  <span>{{ detailUsage.issueCount }} 个工单</span>
                  <span>{{ detailUsage.valueCount }} 条值</span>
                  <span>{{ detailUsage.projectCount }} 个项目<template v-if="detailUsage.isForAll">（全局）</template></span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </a-tab-pane>
      <a-tab-pane key="projects" title="项目中的字段">
        <FieldsInProjects ref="fieldsInProjectsRef" @field-created="loadList" />
      </a-tab-pane>
    </a-tabs>

    <!-- 创建/编辑抽屉 -->
    <a-drawer
      :visible="drawerVisible"
      :title="editingId ? '编辑自定义字段' : '创建自定义字段'"
      :width="640"
      @cancel="drawerVisible = false"
      @ok="handleSave"
      :ok-loading="saving"
      unmount-on-close
    >
      <div class="drawer-sections">
        <!-- ▌ 基础信息 -->
        <div class="form-section">
          <div class="form-section-title">基础信息</div>
          <a-form :model="form" layout="vertical" size="small">
            <a-form-item label="字段名称" required>
              <a-input v-model="form.name" placeholder="例如: 到期版本" :max-length="256" />
            </a-form-item>

            <a-form-item label="字段类型" required>
              <template v-if="editingId">
                <div class="field-type-edit-row">
                  <a-tag size="small">{{ formatTypeLabel(form.fieldFormat) }}</a-tag>
                  <a-button
                    type="text"
                    size="mini"
                    :loading="loadingConversions"
                    @click="openConvertTypeModal"
                  >
                    转换类型
                  </a-button>
                </div>
              </template>
              <template v-else>
                <a-select v-model="form.fieldFormat" placeholder="选择字段类型">
                  <a-option v-for="t in fieldTypeOptions" :key="t.value" :value="t.value">{{ t.label }}</a-option>
                </a-select>
              </template>
            </a-form-item>

            <a-form-item label="字段别名">
              <a-input-tag
                v-model="form.aliases"
                placeholder="输入别名后按回车添加"
                :max-tag-count="10"
                allow-clear
              />
              <div class="form-help">设置一个或多个别名，用户可在搜索和命令中使用别名代替字段名。例如：Assignee 字段的别名可以是 "for"、"分配给"。</div>
            </a-form-item>

            <a-form-item label="默认值">
              <DefaultValueInput
                v-model="form.defaultValue"
                :field-format="form.fieldFormat"
              />
            </a-form-item>
          </a-form>
        </div>

        <!-- ▌ 字段行为 -->
        <div class="form-section">
          <div class="form-section-title">字段行为</div>
          <div class="behavior-section">
            <div class="behavior-item">
              <div class="behavior-item-main">
                <a-switch v-model="form.isRequired" size="small" />
                <span class="behavior-label">必填</span>
              </div>
              <span class="behavior-desc">需要用户明确填写此字段，否则无法提交工单</span>
            </div>
            <div class="behavior-item">
              <div class="behavior-item-main">
                <a-switch v-model="form.isForAll" size="small" />
                <span class="behavior-label">全局可用</span>
              </div>
              <span class="behavior-desc">开启后，所有项目均可使用此字段</span>
            </div>
            <div class="behavior-item">
              <div class="behavior-item-main">
                <a-switch v-model="form.isHiddenInList" size="small" />
                <span class="behavior-label">隐藏于工单列表</span>
              </div>
              <span class="behavior-desc">默认不出现在工单列表的列选择器中（用户可通过个人设置手动添加）</span>
            </div>
          </div>
        </div>

        <!-- ▌ 类型特有配置 -->
        <div v-if="form.fieldFormat === 'string'" class="form-section">
          <div class="form-section-title">文本校验</div>
          <a-form :model="form" layout="vertical" size="small">
            <a-form-item label="最小长度">
              <a-input-number v-model="form.minLength" :min="0" />
            </a-form-item>
            <a-form-item label="最大长度">
              <a-input-number v-model="form.maxLength" :min="0" />
            </a-form-item>
            <a-form-item label="正则验证">
              <a-input v-model="form.regexp" placeholder="可选正则表达式" />
            </a-form-item>
          </a-form>
        </div>

        <div v-if="form.fieldFormat === 'text'" class="form-section">
          <div class="form-section-title">文本配置</div>
          <a-form :model="form" layout="vertical" size="small">
            <a-form-item label="最大长度">
              <a-input-number v-model="form.maxLength" :min="0" placeholder="0 表示不限制" />
              <div class="form-help">支持 Markdown 格式的多行文本</div>
            </a-form-item>
          </a-form>
        </div>

        <!-- ▌ 选项值集 -->
        <div v-if="form.fieldFormat === 'list' || form.fieldFormat === 'state' || form.fieldFormat === 'ownedField' || form.fieldFormat === 'version' || form.fieldFormat === 'build'" class="form-section">
          <div class="form-section-title">
            <span>选项值集</span>
            <a-button type="text" size="mini" @click="form.options.push({ value: '', isDefault: false })">
              <template #icon><icon-plus /></template>
              添加
            </a-button>
          </div>

          <!-- 多值选择 -->
          <div v-if="form.fieldFormat === 'list' || form.fieldFormat === 'ownedField' || form.fieldFormat === 'version' || form.fieldFormat === 'build'" class="behavior-item" style="margin-bottom: 12px;">
            <div class="behavior-item-main">
              <a-switch v-model="form.isMulti" size="small" :disabled="isMultiDisabled" />
              <span class="behavior-label">多值选择</span>
            </div>
            <span class="behavior-desc">
              <template v-if="isMultiDisabled">该字段已被工单使用，无法切换单选/多选模式</template>
              <template v-else>开启后允许选择多个选项值（如影响版本、标签等）</template>
            </span>
          </div>

          <!-- 值集来源选择（仅创建模式显示） -->
          <div v-if="!editingId" style="margin-bottom: 12px;">
            <a-radio-group v-model="valueSetSource" type="button" size="small">
              <a-radio value="new">新建值集</a-radio>
              <a-radio value="copy">从已有字段复制</a-radio>
            </a-radio-group>
          </div>

          <!-- 从已有字段复制：选择源字段（仅创建模式） -->
          <div v-if="valueSetSource === 'copy' && !editingId" style="margin-bottom: 12px;">
            <a-select
              v-model="form.copyOptionsFromFieldId"
              placeholder="选择一个枚举类型字段"
              allow-clear
              size="small"
              @change="onSourceFieldChange"
            >
              <a-option v-for="f in enumFieldList" :key="f.id" :value="f.id">
                {{ f.name }}（{{ (f.options || []).filter(o => !o.isArchived).length }} 个选项）
              </a-option>
            </a-select>
            <div class="form-help">选择后将复制该字段的所有选项作为独立副本，后续修改互不影响</div>
          </div>

          <!-- 选项预览（从字段复制后） -->
          <div v-if="valueSetSource === 'copy' && !editingId && previewOptions.length > 0" class="options-preview" style="margin-bottom: 12px;">
            <a-tag v-for="opt in previewOptions" :key="opt.value" size="small" :color="opt.color || undefined">
              {{ opt.value }}
            </a-tag>
          </div>

          <!-- 排序工具栏 -->
          <div v-if="form.options.length > 1" class="options-sort-toolbar">
            <a-button size="mini" type="text" @click="sortOptionsByName('asc')">
              <template #icon><icon-sort-ascending /></template>
              升序
            </a-button>
            <a-button size="mini" type="text" @click="sortOptionsByName('desc')">
              <template #icon><icon-sort-descending /></template>
              降序
            </a-button>
            <a-button v-if="form.fieldFormat === 'version'" size="mini" type="text" @click="sortOptionsByReleaseDate">
              <template #icon><icon-sort-descending /></template>
              按发布日期
            </a-button>
            <a-button v-if="form.fieldFormat === 'build'" size="mini" type="text" @click="sortOptionsByAssembleDate">
              <template #icon><icon-sort-descending /></template>
              按构建日期
            </a-button>
            <div v-if="form.fieldFormat === 'version'" class="options-archive-toggle">
              <a-switch v-model="showReleasedInDrawer" size="small" />
              <span class="archive-toggle-label">显示已发布</span>
            </div>
            <div v-if="editingId && archivedOptionCount > 0" class="options-archive-toggle">
              <a-switch v-model="showArchivedInDrawer" size="small" />
              <span class="archive-toggle-label">显示已归档 ({{ archivedOptionCount }})</span>
            </div>
          </div>

          <!-- 选项列表（两层布局：主行 + 属性行） -->
          <div class="options-list">
            <div
              v-for="(opt, idx) in visibleFormOptions"
              :key="opt.id || `new-${idx}`"
              class="option-item"
            >
              <!-- 主行：拖拽 + 颜色 + 输入框 + 描述按钮 + 删除 -->
              <div
                class="option-main-row"
                :class="{
                  'option-main-row--dragging': optionDragIndex === idx,
                  'option-main-row--drop-above': optionDropIndex === idx && optionDropPosition === 'above',
                  'option-main-row--drop-below': optionDropIndex === idx && optionDropPosition === 'below',
                  'option-main-row--archived': opt.isArchived
                }"
                :draggable="!opt.isArchived && form.options.filter(o => !o.isArchived).length > 1"
                @dragstart="onOptionDragStart($event, idx)"
                @dragover="onOptionDragOver($event, idx)"
                @dragleave="onOptionDragLeave"
                @drop="onOptionDrop($event, idx)"
                @dragend="onOptionDragEnd"
              >
                <span v-if="!opt.isArchived && form.options.filter(o => !o.isArchived).length > 1" class="option-drag-handle" title="拖拽排序">⠿</span>
                <span v-else-if="opt.isArchived" class="option-archived-icon" title="已归档">📦</span>
                <span v-else class="option-drag-handle" style="visibility: hidden">⠿</span>
                <a-trigger v-if="!opt.isArchived" trigger="click" :popup-translate="[0, 4]">
                  <span
                    class="color-swatch"
                    :style="{ background: opt.color || 'transparent', border: opt.color ? 'none' : '1px dashed var(--tf-border)' }"
                    title="设置颜色"
                  ></span>
                  <template #content>
                    <div class="color-palette">
                      <span
                        v-for="c in presetColors"
                        :key="c"
                        class="color-palette-item"
                        :class="{ active: opt.color === c }"
                        :style="{ background: c }"
                        @click="opt.color = c"
                      ></span>
                      <span
                        class="color-palette-item color-palette-clear"
                        :class="{ active: !opt.color }"
                        @click="opt.color = undefined"
                        title="无颜色"
                      >✕</span>
                    </div>
                  </template>
                </a-trigger>
                <span v-else class="color-swatch color-swatch--archived" :style="{ background: opt.color || 'var(--tf-bg-surface)' }"></span>
                <a-input v-model="opt.value" placeholder="选项值" size="mini" class="option-name-input" :disabled="opt.isArchived" />
                <!-- 描述按钮 -->
                <a-button
                  v-if="!opt.isArchived"
                  type="text"
                  size="mini"
                  :class="{ 'desc-btn-active': opt.description }"
                  :title="opt.description ? `描述: ${opt.description}` : '添加描述'"
                  @click.stop="toggleDescriptionRow(idx)"
                >
                  <icon-info-circle />
                </a-button>
                <!-- ownedField 类型显示 owner 用户选择器 -->
                <a-select
                  v-if="!opt.isArchived && form.fieldFormat === 'ownedField'"
                  v-model="opt.ownerUserId"
                  placeholder="Owner"
                  allow-clear
                  size="mini"
                  style="width: 120px"
                >
                  <a-option v-for="u in ownerUserList" :key="u.id" :value="u.id">{{ u.displayName }}</a-option>
                </a-select>
                <!-- version 类型显示发布日期 -->
                <a-date-picker
                  v-if="!opt.isArchived && form.fieldFormat === 'version'"
                  v-model="opt.releaseDate"
                  placeholder="发布日期"
                  size="mini"
                  style="width: 130px"
                  allow-clear
                />
                <!-- build 类型显示构建日期 -->
                <a-date-picker
                  v-if="!opt.isArchived && form.fieldFormat === 'build'"
                  v-model="opt.assembleDate"
                  placeholder="构建日期"
                  size="mini"
                  style="width: 130px"
                  allow-clear
                />
                <!-- 归档/取消归档按钮 -->
                <a-button
                  v-if="editingId && opt.id && !opt.isArchived"
                  type="text" size="mini"
                  title="归档（隐藏选项但保留已有数据）"
                  @click.stop="handleArchiveOption(opt)"
                >
                  <icon-eye-invisible />
                </a-button>
                <a-button
                  v-if="editingId && opt.id && opt.isArchived"
                  type="text" size="mini" status="success"
                  title="取消归档（恢复选项可选）"
                  @click.stop="handleUnarchiveOption(opt)"
                >
                  <icon-eye />
                </a-button>
                <!-- 删除按钮 -->
                <a-button
                  v-if="!opt.isArchived"
                  type="text" size="mini" status="danger"
                  :disabled="editingId && opt.id && optionUsageMap[opt.id] !== undefined && optionUsageMap[opt.id] > 0"
                  :title="editingId && opt.id && optionUsageMap[opt.id] > 0 ? `该选项被 ${optionUsageMap[opt.id]} 个工单使用，无法删除` : '删除选项'"
                  @click="handleDeleteOption(opt, idx)"
                >
                  <icon-delete />
                </a-button>
              </div>
              <!-- 属性行：默认/已解决/已发布/使用统计 -->
              <div v-if="!opt.isArchived" class="option-attr-row">
                <span
                  class="option-attr-tag"
                  :class="{ active: opt.isDefault }"
                  @click="opt.isDefault = !opt.isDefault"
                >
                  <icon-star-fill v-if="opt.isDefault" /><icon-star v-else />
                  默认
                </span>
                <span
                  v-if="form.fieldFormat === 'state'"
                  class="option-attr-tag"
                  :class="{ active: opt.isResolved }"
                  @click="opt.isResolved = !opt.isResolved"
                >
                  <icon-check-circle-fill v-if="opt.isResolved" /><icon-check-circle v-else />
                  已解决
                </span>
                <span
                  v-if="form.fieldFormat === 'version'"
                  class="option-attr-tag"
                  :class="{ active: opt.isReleased }"
                  @click="opt.isReleased = !opt.isReleased"
                >
                  <icon-check-circle-fill v-if="opt.isReleased" /><icon-check-circle v-else />
                  已发布
                </span>
                <span
                  v-if="editingId && opt.id && optionUsageMap[opt.id] !== undefined"
                  class="option-usage-badge"
                  :class="{ unused: optionUsageMap[opt.id] === 0 }"
                >
                  {{ optionUsageMap[opt.id] > 0 ? `${optionUsageMap[opt.id]} 个工单` : '未使用' }}
                </span>
              </div>
              <!-- 描述输入行 -->
              <div v-if="expandedDescriptionIdx === idx && !opt.isArchived" class="option-desc-row">
                <a-textarea
                  v-model="opt.description"
                  placeholder="输入选项描述，将在下拉选择时以 tooltip 形式展示"
                  :auto-size="{ minRows: 1, maxRows: 3 }"
                  :max-length="1024"
                  show-word-limit
                  size="mini"
                />
              </div>
            </div>
            <a-button type="dashed" size="mini" long @click="form.options.push({ value: '', isDefault: false })">
              <template #icon><icon-plus /></template>
              添加选项
            </a-button>
          </div>

          <!-- 高级操作（可折叠卡片） -->
          <div v-if="editingId" class="options-advanced-ops">
            <div class="advanced-op-card">
              <div class="advanced-op-header" @click="showCopyFromPanel = !showCopyFromPanel">
                <span class="advanced-op-title">从其他字段复制选项</span>
                <a-button type="text" size="mini">
                  {{ showCopyFromPanel ? '收起' : '展开' }}
                </a-button>
              </div>
              <div v-if="showCopyFromPanel" class="advanced-op-body">
                <p class="advanced-op-desc">将源字段的选项追加到当前选项列表中（跳过同名选项）</p>
                <div class="advanced-op-controls">
                  <a-select
                    :model-value="copyFromFieldId ?? undefined"
                    @update:model-value="(v: any) => { copyFromFieldId = (v as string) ?? null }"
                    placeholder="选择源字段"
                    allow-clear
                    size="small"
                    style="flex: 1"
                  >
                    <a-option v-for="f in enumFieldList.filter(x => x.id !== editingId)" :key="f.id" :value="f.id">
                      {{ f.name }}（{{ (f.options || []).filter(o => !o.isArchived).length }} 个选项）
                    </a-option>
                  </a-select>
                  <a-button size="small" type="outline" :disabled="!copyFromFieldId" @click="handleCopyFrom">
                    追加选项
                  </a-button>
                </div>
              </div>
            </div>
            <div class="advanced-op-card">
              <div class="advanced-op-header" @click="showMergePanel = !showMergePanel">
                <span class="advanced-op-title">合并其他字段值集</span>
                <a-button type="text" size="mini">
                  {{ showMergePanel ? '收起' : '展开' }}
                </a-button>
              </div>
              <div v-if="showMergePanel" class="advanced-op-body">
                <p class="advanced-op-desc">将源字段的所有活跃选项合并到当前值集（已归档和同名选项跳过，操作立即生效）</p>
                <div class="advanced-op-controls">
                  <a-select
                    :model-value="mergeFromFieldId ?? undefined"
                    @update:model-value="(v: any) => { mergeFromFieldId = (v as string) ?? null }"
                    placeholder="选择源字段"
                    allow-clear
                    size="small"
                    style="flex: 1"
                  >
                    <a-option v-for="f in enumFieldList.filter(x => x.id !== editingId)" :key="f.id" :value="f.id">
                      {{ f.name }}（{{ (f.options || []).filter(o => !o.isArchived).length }} 个选项）
                    </a-option>
                  </a-select>
                  <a-button size="small" status="warning" type="outline" :disabled="!mergeFromFieldId" :loading="merging" @click="handleMergeFrom">
                    立即合并
                  </a-button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- ▌ 关联设置 -->
        <div class="form-section">
          <div class="form-section-title">关联设置</div>
          <a-form :model="form" layout="vertical" size="small">
            <a-form-item :label="form.isForAll ? '附加到项目（可选）' : '关联项目'">
              <a-select
                v-model="form.projectIds"
                multiple
                allow-clear
                allow-search
                :placeholder="form.isForAll ? '全局字段已对所有项目生效，此处可选择附加配置项目' : '不选则不关联任何项目（可在项目字段管理中手动添加）'"
                :max-tag-count="3"
                :scrollbar="true"
              >
                <a-option
                  v-for="p in projectList"
                  :key="p.id"
                  :value="p.id"
                  :label="p.name"
                >
                  <span class="select-option-key">{{ p.key }}</span>
                  {{ p.name }}
                </a-option>
              </a-select>
              <div v-if="form.isForAll" class="form-help">
                全局字段已对所有项目生效。此处选择的项目将额外创建项目级配置记录，便于后续设置条件显示、角色权限等。
              </div>
              <div v-else class="form-help">
                不选则字段不关联任何项目，可在项目字段管理中手动添加。
              </div>
            </a-form-item>

            <a-form-item label="适用 Issue 类型">
              <a-select
                v-model="form.issueTypes"
                multiple
                allow-clear
                placeholder="不选则适用所有类型"
                :max-tag-count="5"
              >
                <a-option
                  v-for="t in issueTypeOptions"
                  :key="t.value"
                  :value="t.value"
                >
                  {{ t.label }}
                </a-option>
              </a-select>
              <div class="form-help">不选则适用所有类型</div>
            </a-form-item>
          </a-form>
        </div>
      </div>
    </a-drawer>

    <!-- 类型转换确认对话框 -->
    <a-modal
      :visible="convertModalVisible"
      title="修改字段类型"
      :ok-loading="converting"
      :ok-text="convertTargetFormat ? '确认转换' : '选择目标类型'"
      :ok-button-props="{ disabled: !convertTargetFormat }"
      @ok="handleConvertType"
      @cancel="convertModalVisible = false"
      :width="520"
      unmount-on-close
    >
      <div class="convert-type-content">
        <!-- 不允许转换的提示 -->
        <template v-if="conversionData && !conversionData.conversionAllowed">
          <a-alert type="warning" :title="conversionData.blockedReason || '当前字段不允许转换类型'" />
        </template>

        <!-- 允许转换 -->
        <template v-else-if="conversionData">
          <div class="convert-info">
            <span class="convert-info-label">当前类型：</span>
            <a-tag size="small">{{ formatTypeLabel(conversionData.currentFormat) }}</a-tag>
          </div>

          <div v-if="conversionData.availableTargets.length === 0" class="convert-no-targets">
            <a-empty description="当前类型没有可用的转换目标" />
          </div>

          <template v-else>
            <div class="convert-info" style="margin-top: 12px; margin-bottom: 8px;">
              <span class="convert-info-label">选择目标类型：</span>
            </div>
            <div class="convert-targets">
              <div
                v-for="target in conversionData.availableTargets"
                :key="target.format"
                class="convert-target-item"
                :class="{ 'convert-target-item--selected': convertTargetFormat === target.format }"
                @click="selectConvertTarget(target)"
              >
                <div class="convert-target-name">{{ target.displayName }}</div>
                <div v-if="target.warning" class="convert-target-warning">
                  <icon-exclamation-circle-fill style="color: var(--tf-warning); margin-right: 4px; font-size: 12px;" />
                  {{ target.warning }}
                </div>
              </div>
            </div>

            <!-- 额外选项（如 periodUnit） -->
            <div v-if="selectedConvertTarget?.requiresOptions && selectedConvertTarget.options?.length" class="convert-options">
              <div class="convert-info" style="margin-top: 12px; margin-bottom: 8px;">
                <span class="convert-info-label">转换选项：</span>
              </div>
              <a-radio-group v-model="convertPeriodUnit" direction="vertical" size="small">
                <a-radio v-for="opt in selectedConvertTarget.options" :key="opt" :value="opt">
                  {{ periodUnitLabel(opt) }}
                </a-radio>
              </a-radio-group>
            </div>

            <!-- 全局影响提示 -->
            <a-alert
              v-if="convertTargetFormat"
              type="warning"
              style="margin-top: 16px"
            >
              <template #title>此操作对所有使用该字段的项目生效</template>
              字段类型是全局属性，转换将影响所有关联项目中该字段的已有值。请确认后再操作。
            </a-alert>
          </template>
        </template>
      </div>
    </a-modal>
  </AdminPageLayout>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { IconPlus, IconDelete, IconCheck, IconEye, IconEyeInvisible, IconClose, IconSortAscending, IconSortDescending, IconApps, IconFolder, IconLock, IconUnlock, IconInfoCircle, IconExclamationCircleFill } from '@arco-design/web-vue/es/icon'
import { Message, Modal } from '@arco-design/web-vue'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { customFieldApi, projectApi, workflowApi, userApi } from '@/api'
import type { CustomFieldDefinitionVO, CustomFieldUsageVO, OptionUsageItemVO, UserVO, AvailableConversionsVO, ConversionOptionVO } from '@/api/types'
import { localizeIssueType } from '@/utils/fieldLabels'
import FieldsInProjects from './FieldsInProjects.vue'
import DefaultValueInput from './components/DefaultValueInput.vue'
import { AdminPageLayout, AdminDataTable } from '@/components/admin'


const activeTab = ref('list')
const fieldList = ref<CustomFieldDefinitionVO[]>([])
const loading = ref(false)
const pagination = reactive({ current: 1, pageSize: 20, total: 0, showPageSize: true, pageSizeOptions: [20, 50, 100] })
const projectList = ref<any[]>([])
const issueTypeOptions = ref<Array<{ value: string; label: string }>>([])

/** 搜索关键词 */
const searchKeyword = ref('')

/** 类型筛选 */
const filterFieldFormat = ref('')

/** 枚举字段内联展示最大选项数 */
const MAX_INLINE_OPTIONS = 5

/** 前端过滤后的字段列表 */
const filteredFieldList = computed(() => {
  if (!searchKeyword.value.trim()) return fieldList.value
  const kw = searchKeyword.value.trim().toLowerCase()
  return fieldList.value.filter(f => f.name.toLowerCase().includes(kw))
})
const selectedKeys = ref<string[]>([])



// Detail sidebar state
const selectedField = ref<CustomFieldDefinitionVO | null>(null)
const detailUsage = ref<CustomFieldUsageVO | null>(null)
const fieldsInProjectsRef = ref<InstanceType<typeof FieldsInProjects> | null>(null)
const showArchivedInDetail = ref(false)
const showArchivedInDrawer = ref(false)
/** 当前展开描述输入的选项索引（-1 表示全部收起） */
const expandedDescriptionIdx = ref(-1)
/** 高级操作面板折叠状态 */
const showCopyFromPanel = ref(false)
const showMergePanel = ref(false)

/** 侧边栏选项过滤 */
const detailFilteredOptions = computed(() => {
  if (!selectedField.value?.options) return []
  if (showArchivedInDetail.value) return selectedField.value.options
  return selectedField.value.options.filter(o => !o.isArchived)
})

/** 版本类型"显示已发布"开关，默认显示 */
const showReleasedInDrawer = ref(true)

/** 编辑抽屉中可见的选项（归档的根据开关显示/隐藏，版本已发布的根据开关显示/隐藏） */
const visibleFormOptions = computed(() => {
  let opts = form.options
  if (!showArchivedInDrawer.value) {
    opts = opts.filter(o => !o.isArchived)
  }
  if (form.fieldFormat === 'version' && !showReleasedInDrawer.value) {
    opts = opts.filter(o => !o.isReleased)
  }
  return opts
})

/** 编辑抽屉中归档选项数量（用于显示提示） */
const archivedOptionCount = computed(() => {
  return form.options.filter(o => o.isArchived).length
})

// Drawer state
const drawerVisible = ref(false)
const editingId = ref<string | null>(null)
const saving = ref(false)
/** 编辑模式下字段已有数据时禁止切换 isMulti */
const isMultiDisabled = ref(false)
/** 编辑模式下每个选项的引用计数映射 {optionId: issueCount} */
const optionUsageMap = ref<Record<string, number>>({})

// Value set source (for list type create mode)
const valueSetSource = ref<'new' | 'copy'>('new')
const enumFieldList = ref<CustomFieldDefinitionVO[]>([])
const previewOptions = ref<Array<{ value: string; color?: string }>>([])
const copyFromFieldId = ref<string | null>(null)
const mergeFromFieldId = ref<string | null>(null)
const merging = ref(false)

// ========== 类型转换状态 ==========
const convertModalVisible = ref(false)
const loadingConversions = ref(false)
const converting = ref(false)
const conversionData = ref<AvailableConversionsVO | null>(null)
const convertTargetFormat = ref<string>('')
const convertPeriodUnit = ref<string>('MINUTES')
const selectedConvertTarget = ref<ConversionOptionVO | null>(null)

const form = reactive({
  name: '',
  fieldFormat: 'string' as string,
  isRequired: false,
  isForAll: false,
  isMulti: false,
  isHiddenInList: false,
  aliases: [] as string[],
  defaultValue: '',
  minLength: 0,
  maxLength: 0,
  regexp: '',
  options: [] as Array<{ id?: string; value: string; isDefault: boolean; color?: string; isArchived?: boolean; isResolved?: boolean; description?: string; ownerUserId?: string; releaseDate?: string; isReleased?: boolean; assembleDate?: string }>,
  projectIds: [] as string[],
  issueTypes: [] as string[],
  copyOptionsFromFieldId: undefined as string | undefined
})

/** 预定义颜色方案（14 种） */
const presetColors = [
  '#4CAF50', '#2196F3', '#9C27B0', '#FF9800',
  '#F44336', '#00BCD4', '#607D8B', '#E91E63',
  '#8BC34A', '#3F51B5', '#FF5722', '#009688',
  '#795548', '#FFC107'
]

const fieldTypeOptions = [
  { value: 'string', label: '文本(单行)' },
  { value: 'text', label: '文本(多行/Markdown)' },
  { value: 'int', label: '整数' },
  { value: 'float', label: '小数' },
  { value: 'date', label: '日期' },
  { value: 'datetime', label: '日期时间' },
  { value: 'bool', label: '布尔' },
  { value: 'list', label: '列表(枚举)' },
  { value: 'ownedField', label: '子系统(Owned Field)' },
  { value: 'state', label: '状态(State)' },
  { value: 'user', label: '用户' },
  { value: 'period', label: '时间周期' },
  { value: 'version', label: '版本(Version)' },
  { value: 'build', label: '构建号(Build)' },
  { value: 'group', label: '用户组(Group)' }
]

function formatTypeLabel(format: string) {
  return fieldTypeOptions.find(t => t.value === format)?.label || format
}

/** 根据项目 ID 获取项目名称 */
function getProjectName(projectId: string): string {
  const proj = projectList.value.find(p => p.id === projectId)
  return proj ? proj.name : projectId
}

/** 生成 tooltip 文本：逗号分隔的项目名列表 */
function getProjectNamesText(projectIds?: string[]): string {
  if (!projectIds || projectIds.length === 0) return ''
  return projectIds.map(id => getProjectName(id)).join('、')
}

async function loadList() {
  loading.value = true
  try {
    const params: { page: number; pageSize: number; fieldFormat?: string } = {
      page: pagination.current,
      pageSize: pagination.pageSize
    }
    if (filterFieldFormat.value) {
      params.fieldFormat = filterFieldFormat.value
    }
    const res = await customFieldApi.list(params)
    fieldList.value = res.data?.list || []
    pagination.total = res.data?.pagination?.total || 0
  } catch {
    fieldList.value = []
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  pagination.current = 1
  loadList()
}

async function loadProjects() {
  try {
    const res = await projectApi.list({ pageSize: 100 })
    projectList.value = res.data?.list || []
  } catch {
    projectList.value = []
  }
}

async function loadIssueTypes() {
  try {
    const res = await workflowApi.listIssueTypes()
    const types = res.data || []
    issueTypeOptions.value = types.map(t => ({ value: t, label: localizeIssueType(t) }))
  } catch {
    // Fallback to basic types if API fails
    issueTypeOptions.value = [
      { value: 'Bug', label: '缺陷' },
      { value: 'Task', label: '任务' },
      { value: 'Feature', label: '需求' },
      { value: 'Epic', label: '史诗' },
    ]
  }
}

function onPageChange(page: number) {
  pagination.current = page
  loadList()
}

function onPageSizeChange(size: number) {
  pagination.pageSize = size
  pagination.current = 1
  loadList()
}

function onSearch(_keyword: string) {
  // 搜索关键词变化时重置到第 1 页（filteredFieldList 是前端计算属性，不需要重新请求）
  pagination.current = 1
}

async function onRowClick(record: CustomFieldDefinitionVO) {
  selectedField.value = record
  detailUsage.value = null
  try {
    const res = await customFieldApi.getUsage(record.id)
    detailUsage.value = res.data || null
  } catch {
    // silently ignore
  }
}

function resetForm() {
  form.name = ''
  form.fieldFormat = 'string'
  form.isRequired = false
  form.isForAll = false
  form.isMulti = false
  form.isHiddenInList = false
  form.aliases = []
  form.defaultValue = ''
  form.minLength = 0
  form.maxLength = 0
  form.regexp = ''
  form.options = []
  form.projectIds = []
  form.issueTypes = []
  form.copyOptionsFromFieldId = undefined
  valueSetSource.value = 'new'
  previewOptions.value = []
  copyFromFieldId.value = null
  mergeFromFieldId.value = null
  optionUsageMap.value = {}
  showArchivedInDrawer.value = false
  showReleasedInDrawer.value = true
  expandedDescriptionIdx.value = -1
  showCopyFromPanel.value = false
  showMergePanel.value = false
}

// Reset defaultValue when field format changes during creation
watch(() => form.fieldFormat, (newFormat, oldFormat) => {
  if (!editingId.value && oldFormat && newFormat !== oldFormat) {
    form.defaultValue = ''
  }
})

async function loadEnumFields() {
  try {
    const res = await customFieldApi.listEnumFields()
    enumFieldList.value = res.data || []
  } catch {
    enumFieldList.value = []
  }
}

function onSourceFieldChange(fieldId: string | null) {
  if (!fieldId) {
    previewOptions.value = []
    form.options = []
    form.copyOptionsFromFieldId = undefined
    return
  }
  form.copyOptionsFromFieldId = fieldId
  const sourceField = enumFieldList.value.find(f => f.id === fieldId)
  if (sourceField && sourceField.options) {
    const activeOptions = sourceField.options.filter(o => !o.isArchived)
    previewOptions.value = activeOptions.map(o => ({ value: o.value, color: o.color }))
    // 预填充到 form.options 以便用户可以在保存前调整
    form.options = activeOptions.map(o => ({
      value: o.value,
      isDefault: o.isDefault || false,
      color: o.color || undefined
    }))
  }
}

function handleCopyFrom() {
  if (!copyFromFieldId.value) return
  const sourceField = enumFieldList.value.find(f => f.id === copyFromFieldId.value)
  if (!sourceField || !sourceField.options) return

  const existingValues = new Set(form.options.map(o => o.value))
  const activeOptions = sourceField.options.filter(o => !o.isArchived)
  let addedCount = 0
  for (const opt of activeOptions) {
    if (!existingValues.has(opt.value)) {
      form.options.push({
        value: opt.value,
        isDefault: false,
        color: opt.color || undefined
      })
      addedCount++
    }
  }
  if (addedCount > 0) {
    Message.success(`已追加 ${addedCount} 个选项`)
  } else {
    Message.info('所有选项已存在，无需追加')
  }
  copyFromFieldId.value = null
}

async function handleMergeFrom() {
  if (!mergeFromFieldId.value || !editingId.value) return
  merging.value = true
  try {
    const res = await customFieldApi.mergeOptions(editingId.value, mergeFromFieldId.value)
    const result = res.data
    if (result && result.addedCount > 0) {
      Message.success(`合并完成：新增 ${result.addedCount} 个选项，跳过 ${result.skippedCount} 个重复项`)
      // 重新加载字段详情以刷新选项列表
      const detailRes = await customFieldApi.getDetail(editingId.value)
      if (detailRes.data?.options) {
        form.options = detailRes.data.options.map(o => ({
          id: o.id,
          value: o.value,
          isDefault: o.isDefault,
          color: o.color || undefined,
          isArchived: o.isArchived || false,
          isResolved: o.isResolved || false,
          description: o.description || undefined,
          ownerUserId: o.ownerUserId || undefined
        }))
      }
      // 刷新列表
      loadList()
    } else if (result) {
      Message.info(`所有选项均已存在，跳过 ${result.skippedCount} 个重复项`)
    }
    mergeFromFieldId.value = null
  } catch (e: any) {
    Message.error(e.response?.data?.message || '合并操作失败')
  } finally {
    merging.value = false
  }
}

function openCreate() {
  editingId.value = null
  isMultiDisabled.value = false
  resetForm()
  loadEnumFields()
  drawerVisible.value = true
}

function openEdit(record: CustomFieldDefinitionVO) {
  editingId.value = record.id
  form.name = record.name
  form.fieldFormat = record.fieldFormat
  form.isRequired = record.isRequired
  form.isForAll = record.isForAll
  form.isMulti = record.isMulti || false
  form.isHiddenInList = record.isHiddenInList || false
  // 解析 aliases：后端存储为逗号分隔字符串，前端转为数组
  form.aliases = record.aliases ? record.aliases.split(',').map(s => s.trim()).filter(Boolean) : []
  form.defaultValue = record.defaultValue || ''
  form.minLength = record.minLength
  form.maxLength = record.maxLength
  form.regexp = record.regexp || ''
  form.options = (record.options || [])
    .map(o => ({ id: o.id, value: o.value, isDefault: o.isDefault, color: o.color || undefined, isArchived: o.isArchived || false, isResolved: o.isResolved || false, description: o.description || undefined, ownerUserId: o.ownerUserId || undefined, releaseDate: o.releaseDate || undefined, isReleased: o.isReleased || false }))
  form.projectIds = record.projectIds || []
  form.issueTypes = record.issueTypes || []
  form.copyOptionsFromFieldId = undefined
  copyFromFieldId.value = null
  mergeFromFieldId.value = null
  // 检查字段是否有数据——有则禁止切换 isMulti
  isMultiDisabled.value = false
  if (record.fieldFormat === 'list' || record.fieldFormat === 'state' || record.fieldFormat === 'ownedField' || record.fieldFormat === 'version') {
    loadEnumFields()
    customFieldApi.getUsage(record.id).then(res => {
      if (res.data && res.data.valueCount > 0) {
        isMultiDisabled.value = true
      }
    }).catch(() => { /* 查询失败时允许操作，后端兜底 */ })
    // 加载逐选项使用统计
    customFieldApi.getOptionUsage(record.id).then(res => {
      if (res.data) {
        const map: Record<string, number> = {}
        for (const item of res.data) {
          map[item.optionId] = item.issueCount
        }
        optionUsageMap.value = map
      }
    }).catch(() => { optionUsageMap.value = {} })
  }
  drawerVisible.value = true
}

/** 切换选项描述输入行的展开/收起 */
function toggleDescriptionRow(idx: number) {
  if (expandedDescriptionIdx.value === idx) {
    expandedDescriptionIdx.value = -1
  } else {
    expandedDescriptionIdx.value = idx
  }
}

function handleDeleteOption(opt: { id?: string; value: string; isDefault: boolean; color?: string; isArchived?: boolean; description?: string }, idx: number) {
  // 如果选项有 ID 且有引用，阻止删除并提示
  if (editingId.value && opt.id && optionUsageMap.value[opt.id] !== undefined && optionUsageMap.value[opt.id] > 0) {
    Modal.warning({
      title: '无法删除',
      content: `该选项"${opt.value}"被 ${optionUsageMap.value[opt.id]} 个工单使用，无法直接删除。请先归档该选项或将引用迁移到其他选项。`,
      okText: '我知道了'
    })
    return
  }
  // 从 visibleFormOptions 中的 index 映射到 form.options 中的 index
  const realIdx = form.options.indexOf(opt as any)
  if (realIdx >= 0) {
    form.options.splice(realIdx, 1)
  } else {
    form.options.splice(idx, 1)
  }
}

async function handleArchiveOption(opt: { id?: string; value: string; isDefault: boolean; color?: string; isArchived?: boolean; description?: string }) {
  if (!editingId.value || !opt.id) return
  try {
    await customFieldApi.archiveOption(editingId.value, opt.id, true)
    opt.isArchived = true
    opt.isDefault = false
    Message.success(`选项"${opt.value}"已归档`)
    // 刷新列表中的数据
    loadList()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '归档失败')
  }
}

async function handleUnarchiveOption(opt: { id?: string; value: string; isDefault: boolean; color?: string; isArchived?: boolean; description?: string }) {
  if (!editingId.value || !opt.id) return
  try {
    await customFieldApi.archiveOption(editingId.value, opt.id, false)
    opt.isArchived = false
    Message.success(`选项"${opt.value}"已恢复`)
    // 刷新列表中的数据
    loadList()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '恢复失败')
  }
}

// ========== 选项拖拽排序 ==========

const optionDragIndex = ref<number | null>(null)
const optionDropIndex = ref<number | null>(null)
const optionDropPosition = ref<'above' | 'below' | null>(null)

function onOptionDragStart(event: DragEvent, index: number) {
  optionDragIndex.value = index
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', String(index))
  }
}

function onOptionDragOver(event: DragEvent, index: number) {
  event.preventDefault()
  if (optionDragIndex.value === null || optionDragIndex.value === index) {
    optionDropIndex.value = null
    optionDropPosition.value = null
    return
  }
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const midY = rect.top + rect.height / 2
  optionDropIndex.value = index
  optionDropPosition.value = event.clientY < midY ? 'above' : 'below'
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function onOptionDragLeave() {
  optionDropIndex.value = null
  optionDropPosition.value = null
}

function onOptionDrop(event: DragEvent, targetIndex: number) {
  event.preventDefault()
  if (optionDragIndex.value === null || optionDragIndex.value === targetIndex) {
    resetOptionDragState()
    return
  }
  const items = [...form.options]
  const [draggedItem] = items.splice(optionDragIndex.value, 1)

  let insertAt = targetIndex
  if (optionDragIndex.value < targetIndex) {
    insertAt = optionDropPosition.value === 'above' ? targetIndex - 1 : targetIndex
  } else {
    insertAt = optionDropPosition.value === 'above' ? targetIndex : targetIndex + 1
  }
  items.splice(insertAt, 0, draggedItem)
  form.options = items

  // 如果在编辑模式且所有选项都有 ID，调用后端排序 API 即时保存
  if (editingId.value && items.every(o => o.id)) {
    customFieldApi.reorderOptions(editingId.value, items.map(o => o.id!)).catch(() => {
      // 静默失败，保存时会再次同步
    })
  }

  resetOptionDragState()
}

function onOptionDragEnd() {
  resetOptionDragState()
}

function resetOptionDragState() {
  optionDragIndex.value = null
  optionDropIndex.value = null
  optionDropPosition.value = null
}

// ========== 选项按名称排序 ==========

function sortOptionsByName(direction: 'asc' | 'desc') {
  const sorted = [...form.options].sort((a, b) => {
    const cmp = a.value.localeCompare(b.value, undefined, { sensitivity: 'base' })
    return direction === 'asc' ? cmp : -cmp
  })
  form.options = sorted

  // 编辑模式下即时保存排序
  if (editingId.value && sorted.every(o => o.id)) {
    customFieldApi.reorderOptions(editingId.value, sorted.map(o => o.id!)).catch(() => {
      // 静默失败
    })
  }
}

// ========== 版本选项按发布日期排序 ==========

function sortOptionsByReleaseDate() {
  const sorted = [...form.options].sort((a, b) => {
    // 无日期排最后
    if (!a.releaseDate && !b.releaseDate) return 0
    if (!a.releaseDate) return 1
    if (!b.releaseDate) return -1
    return a.releaseDate.localeCompare(b.releaseDate)
  })
  form.options = sorted

  // 编辑模式下即时保存排序
  if (editingId.value && sorted.every(o => o.id)) {
    customFieldApi.reorderOptions(editingId.value, sorted.map(o => o.id!)).catch(() => {
      // 静默失败
    })
  }
}

function sortOptionsByAssembleDate() {
  const sorted = [...form.options].sort((a, b) => {
    if (!a.assembleDate && !b.assembleDate) return 0
    if (!a.assembleDate) return 1
    if (!b.assembleDate) return -1
    return b.assembleDate.localeCompare(a.assembleDate) // 最新构建排前面
  })
  form.options = sorted

  // 编辑模式下即时保存排序
  if (editingId.value && sorted.every(o => o.id)) {
    customFieldApi.reorderOptions(editingId.value, sorted.map(o => o.id!)).catch(() => {
      // 静默失败
    })
  }
}

// ========== 类型转换方法 ==========

async function openConvertTypeModal() {
  if (!editingId.value) return
  loadingConversions.value = true
  convertTargetFormat.value = ''
  convertPeriodUnit.value = 'MINUTES'
  selectedConvertTarget.value = null
  conversionData.value = null
  try {
    const res = await customFieldApi.getAvailableConversions(editingId.value)
    conversionData.value = res.data || null
    convertModalVisible.value = true
  } catch (e: any) {
    Message.error(e.response?.data?.message || '获取可用转换选项失败')
  } finally {
    loadingConversions.value = false
  }
}

function selectConvertTarget(target: ConversionOptionVO) {
  convertTargetFormat.value = target.format
  selectedConvertTarget.value = target
  // 如果需要额外选项，重置为第一个选项
  if (target.requiresOptions && target.options?.length) {
    convertPeriodUnit.value = target.options[0]
  }
}

function periodUnitLabel(unit: string): string {
  switch (unit) {
    case 'MINUTES': return '分钟 — 将数值视为分钟数'
    case 'HOURS': return '小时 — 将数值视为小时数（×60）'
    case 'DAYS': return '天 — 将数值视为天数（×480，按8小时/天）'
    default: return unit
  }
}

async function handleConvertType() {
  if (!editingId.value || !convertTargetFormat.value) return
  converting.value = true
  try {
    const payload: { targetFormat: string; periodUnit?: string } = {
      targetFormat: convertTargetFormat.value
    }
    if (selectedConvertTarget.value?.requiresOptions) {
      payload.periodUnit = convertPeriodUnit.value
    }
    const res = await customFieldApi.convertType(editingId.value, payload)
    const result = res.data
    convertModalVisible.value = false
    drawerVisible.value = false

    if (result) {
      let successMsg = `类型转换成功：${formatTypeLabel(result.fromFormat)} → ${formatTypeLabel(result.toFormat)}`
      if (result.affectedIssueCount > 0) {
        successMsg += `，影响 ${result.affectedIssueCount} 个工单`
      }
      if (result.failedValueCount > 0) {
        Message.warning(`${successMsg}。${result.failedValueCount} 个值转换失败。`)
      } else {
        Message.success(successMsg)
      }
    } else {
      Message.success('类型转换成功')
    }
    // 刷新字段列表
    loadList()
    // 更新侧边栏
    if (selectedField.value && selectedField.value.id === editingId.value) {
      selectedField.value = null
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '类型转换失败')
  } finally {
    converting.value = false
  }
}

async function handleSave() {
  if (!form.name.trim()) {
    Message.warning('请输入字段名称')
    return
  }
  if (!form.fieldFormat) {
    Message.warning('请选择字段类型')
    return
  }

  // 将别名数组转换为逗号分隔字符串
  const aliasesStr = form.aliases.length > 0 ? form.aliases.join(',') : undefined

  saving.value = true
  try {
    if (editingId.value) {
      await customFieldApi.update(editingId.value, {
        name: form.name,
        isRequired: form.isRequired,
        isForAll: form.isForAll,
        isHiddenInList: form.isHiddenInList,
        aliases: aliasesStr,
        defaultValue: form.defaultValue || undefined,
        minLength: form.minLength,
        maxLength: form.maxLength,
        regexp: form.regexp || undefined,
        isMulti: (form.fieldFormat === 'list' || form.fieldFormat === 'ownedField' || form.fieldFormat === 'version' || form.fieldFormat === 'build') ? form.isMulti : undefined,
        options: (form.fieldFormat === 'list' || form.fieldFormat === 'state' || form.fieldFormat === 'ownedField' || form.fieldFormat === 'version' || form.fieldFormat === 'build')
          ? form.options.filter(o => !o.isArchived).map(o => ({ id: o.id, value: o.value, isDefault: o.isDefault, color: o.color || undefined, isResolved: form.fieldFormat === 'state' ? o.isResolved : undefined, ownerUserId: form.fieldFormat === 'ownedField' ? o.ownerUserId || undefined : undefined, releaseDate: form.fieldFormat === 'version' ? o.releaseDate || undefined : undefined, isReleased: form.fieldFormat === 'version' ? o.isReleased : undefined, assembleDate: form.fieldFormat === 'build' ? o.assembleDate || undefined : undefined }))
          : undefined,
        projectIds: form.projectIds,
        issueTypes: form.issueTypes
      })
      Message.success('更新成功')
    } else {
      await customFieldApi.create({
        name: form.name,
        fieldFormat: form.fieldFormat,
        isRequired: form.isRequired,
        isForAll: form.isForAll,
        isHiddenInList: form.isHiddenInList,
        aliases: aliasesStr,
        defaultValue: form.defaultValue || undefined,
        minLength: form.minLength,
        maxLength: form.maxLength,
        regexp: form.regexp || undefined,
        isMulti: (form.fieldFormat === 'list' || form.fieldFormat === 'ownedField' || form.fieldFormat === 'version' || form.fieldFormat === 'build') ? form.isMulti : undefined,
        options: (form.fieldFormat === 'list' || form.fieldFormat === 'state' || form.fieldFormat === 'ownedField' || form.fieldFormat === 'version' || form.fieldFormat === 'build')
          ? form.options.map(o => ({ value: o.value, isDefault: o.isDefault, color: o.color || undefined, isResolved: form.fieldFormat === 'state' ? o.isResolved : undefined, ownerUserId: form.fieldFormat === 'ownedField' ? o.ownerUserId || undefined : undefined, releaseDate: form.fieldFormat === 'version' ? o.releaseDate || undefined : undefined, isReleased: form.fieldFormat === 'version' ? o.isReleased : undefined, assembleDate: form.fieldFormat === 'build' ? o.assembleDate || undefined : undefined }))
          : undefined,
        projectIds: form.projectIds,
        issueTypes: form.issueTypes
      })
      Message.success('创建成功')
    }
    drawerVisible.value = false
    loadList()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function confirmDelete(record: CustomFieldDefinitionVO) {
  try {
    const res = await customFieldApi.getUsage(record.id)
    const usage = res.data
    if (!usage) return

    const hasValues = usage.issueCount > 0
    const hasConditionRefs = usage.conditionRefCount > 0

    if (!hasValues && !hasConditionRefs) {
      // 无引用——简单确认
      const projectInfo = usage.isForAll
        ? '（全局字段）'
        : ''
      const { confirmDelete } = useConfirmDelete()
      confirmDelete({
        itemName: `自定义字段「${record.name}」${projectInfo}`,
        confirmText: '删除字段',
        onConfirm: () => handleDelete(record.id)
      })
    } else {
      // 有工单引用或条件依赖——危险确认
      const projectInfo = usage.isForAll
        ? `（全局字段，覆盖所有 ${usage.projectCount} 个项目）`
        : ''
      const parts: string[] = []
      if (hasValues) {
        parts.push(`被 ${usage.issueCount} 个工单使用（共 ${usage.valueCount} 条值记录），删除后这些数据将永久丢失`)
      }
      if (hasConditionRefs) {
        parts.push(`被 ${usage.conditionRefCount} 条字段配置作为条件源引用，删除后相关条件规则将失效，被隐藏的字段将变为始终显示`)
      }
      const { confirmDangerDelete } = useConfirmDelete()
      confirmDangerDelete({
        itemName: `字段「${record.name}」${projectInfo}`,
        impactDescription: parts.join('；'),
        confirmText: hasValues ? `确认删除（影响 ${usage.issueCount} 个工单）` : '确认删除',
        onConfirm: () => handleDelete(record.id)
      })
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '获取使用情况失败')
  }
}

async function handleDelete(id: string) {
  try {
    await customFieldApi.delete(id, true)
    Message.success('删除成功')
    loadList()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '删除失败')
  }
}

// ========== 批量操作方法 ==========

async function batchToggleAutoAttach(enabled: boolean) {
  if (selectedKeys.value.length === 0) return
  try {
    await customFieldApi.batchUpdate({
      ids: selectedKeys.value,
      field: 'isAutoAttach',
      value: enabled
    })
    Message.success(`已${enabled ? '启用' : '禁用'} ${selectedKeys.value.length} 个字段的 Auto-attach`)
    selectedKeys.value = []
    loadList()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '批量操作失败')
  }
}

async function batchToggleHidden(hidden: boolean) {
  if (selectedKeys.value.length === 0) return
  try {
    await customFieldApi.batchUpdate({
      ids: selectedKeys.value,
      field: 'isHiddenInList',
      value: hidden
    })
    Message.success(`已${hidden ? '隐藏' : '显示'} ${selectedKeys.value.length} 个字段`)
    selectedKeys.value = []
    loadList()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '批量操作失败')
  }
}

async function batchTogglePrivate(isPrivate: boolean) {
  if (selectedKeys.value.length === 0) return
  try {
    await customFieldApi.batchUpdate({
      ids: selectedKeys.value,
      field: 'isPrivate',
      value: isPrivate
    })
    Message.success(`已将 ${selectedKeys.value.length} 个字段${isPrivate ? '设为私有' : '取消私有'}`)
    selectedKeys.value = []
    loadList()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '批量操作失败')
  }
}

function batchDeleteConfirm() {
  if (selectedKeys.value.length === 0) return
  const { confirmDangerDelete } = useConfirmDelete()
  confirmDangerDelete({
    itemName: `选中的 ${selectedKeys.value.length} 个自定义字段`,
    impactDescription: '关联的工单字段值将被永久清除',
    confirmText: `删除 ${selectedKeys.value.length} 个字段`,
    onConfirm: handleBatchDelete
  })
}

async function handleBatchDelete() {
  try {
    await customFieldApi.batchDelete(selectedKeys.value)
    Message.success(`已删除 ${selectedKeys.value.length} 个字段`)
    selectedKeys.value = []
    loadList()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '批量删除失败')
  }
}

// ========== 用户列表（用于 ownedField 类型的 owner 选择器）==========
const ownerUserList = ref<UserVO[]>([])

async function loadOwnerUsers() {
  try {
    const res = await userApi.list({ pageSize: 500 })
    ownerUserList.value = res.data?.list || []
  } catch (e) {
    console.error('[CustomField] 加载用户列表失败:', e)
  }
}

onMounted(() => {
  loadList()
  loadProjects()
  loadIssueTypes()
  loadOwnerUsers()
})
</script>

<style scoped>
.cf-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  /* Override Arco Tabs' overflow:hidden which breaks position:sticky context.
     overflow:clip provides clipping without creating a scroll container. */
  overflow: clip;
}

.cf-tabs :deep(.arco-tabs-nav) {
  flex-shrink: 0;
}

.cf-tabs :deep(.arco-tabs-content) {
  flex: 1;
  min-height: 0;
  overflow: clip;
}

.cf-tabs :deep(.arco-tabs-content-list) {
  height: 100%;
}

.cf-tabs :deep(.arco-tabs-content .arco-tabs-content-item) {
  overflow: visible;
}

.cf-tabs :deep(.arco-tabs-pane) {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: visible;
}

.cf-body {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.has-detail {
  flex: 1;
}

.batch-count {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-accent);
  margin-right: 4px;
}

.clickable-name {
  cursor: pointer;
  color: var(--tf-accent);
}
.clickable-name:hover {
  text-decoration: underline;
}

/* 属性标签组合 */
.field-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.field-badge {
  display: inline-block;
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
  line-height: 1.4;
}

.field-badge--required {
  background: color-mix(in srgb, var(--tf-danger) 12%, transparent);
  color: var(--tf-danger);
}

.field-badge--global {
  background: color-mix(in srgb, var(--tf-accent) 12%, transparent);
  color: var(--tf-accent);
}

.field-badge--private {
  background: color-mix(in srgb, var(--tf-warning) 12%, transparent);
  color: var(--tf-warning);
}

.field-badge--hidden {
  background: color-mix(in srgb, var(--tf-text-tertiary) 12%, transparent);
  color: var(--tf-text-tertiary);
}

/* 默认值单元格 */
.default-value-cell {
  font-size: 12px;
  color: var(--tf-text-secondary);
  background: var(--tf-bg-elevated);
  border-radius: 3px;
  padding: 1px 5px;
  border: 1px solid var(--tf-border);
}

/* 枚举选项内联展示 */
.options-inline {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  align-items: center;
}

.option-inline-tag {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  border: 1px solid var(--tf-border);
  background: var(--tf-bg-elevated);
  color: var(--tf-text-secondary);
  white-space: nowrap;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.option-more-tag {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  border: 1px dashed var(--tf-border);
  color: var(--tf-text-tertiary);
  white-space: nowrap;
}

/* Detail sidebar */
.cf-detail-sidebar {
  width: 280px;
  flex-shrink: 0;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--tf-border);
}

.detail-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
}

.detail-body {
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.detail-label {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  flex-shrink: 0;
}

.detail-value {
  font-size: 12px;
  color: var(--tf-text-primary);
  text-align: right;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 8px;
  border-top: 1px solid var(--tf-border);
}

.detail-options {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.detail-options-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.detail-options-toggle-label {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.option-archived-tag {
  opacity: 0.5;
  text-decoration: line-through;
}

.detail-stats {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  color: var(--tf-text-secondary);
}

.text-muted {
  color: var(--tf-text-tertiary);
  font-size: 12px;
}

.text-xs {
  font-size: 11px;
}

/* 使用项目 tag 样式 */
.project-usage-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 7px;
  border-radius: 3px;
  font-size: 12px;
  cursor: default;
}

.project-usage-global {
  background: color-mix(in srgb, var(--tf-accent) 12%, transparent);
  color: var(--tf-accent);
}

.project-usage-specific {
  background: color-mix(in srgb, var(--tf-success) 12%, transparent);
  color: var(--tf-success);
}

/* 侧边栏项目标签列表 */
.detail-projects {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding-left: 0;
}

.detail-project-tag {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  background: var(--tf-bg-elevated);
  color: var(--tf-text-secondary);
  border: 1px solid var(--tf-border);
}

.form-help {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-top: 4px;
}

.select-option-key {
  display: inline-block;
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-surface);
  border-radius: 3px;
  padding: 0 4px;
  margin-right: 6px;
}

.options-sort-toolbar {
  display: flex;
  gap: 4px;
  margin-bottom: 8px;
  align-items: center;
}

.options-archive-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

.archive-toggle-label {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.options-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.option-item {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 6px 0;
  border-bottom: 1px solid var(--tf-border-light, color-mix(in srgb, var(--tf-border) 50%, transparent));
}
.option-item:last-child { border-bottom: none; }

.option-main-row {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 4px;
  border-radius: 4px;
  transition: background 150ms, box-shadow 150ms;
}

.option-main-row[draggable="true"] {
  cursor: grab;
}

.option-main-row[draggable="true"]:active {
  cursor: grabbing;
}

.option-main-row--dragging {
  opacity: 0.4;
  background: var(--tf-bg-surface);
}

.option-main-row--drop-above {
  box-shadow: 0 -2px 0 0 var(--tf-accent);
}

.option-main-row--drop-below {
  box-shadow: 0 2px 0 0 var(--tf-accent);
}

.option-main-row--archived {
  opacity: 0.6;
  background: var(--tf-bg-body);
  border: 1px dashed var(--tf-border);
}

.option-main-row--archived .option-drag-handle {
  visibility: hidden;
}

.option-name-input {
  flex: 1;
  min-width: 0;
}

.option-attr-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 0 0 28px;
  min-height: 22px;
}

.option-attr-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  cursor: pointer;
  font-size: 11px;
  border-radius: 3px;
  padding: 1px 6px;
  color: var(--tf-text-tertiary);
  border: 1px solid var(--tf-border);
  background: transparent;
  transition: all 150ms;
  user-select: none;
  line-height: 1.4;
}
.option-attr-tag:hover {
  border-color: var(--tf-text-secondary);
  color: var(--tf-text-secondary);
}
.option-attr-tag.active {
  color: var(--tf-accent);
  border-color: var(--tf-accent);
  background: color-mix(in srgb, var(--tf-accent) 10%, transparent);
}

.option-usage-badge {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-left: auto;
}
.option-usage-badge.unused {
  color: var(--tf-text-quaternary);
  font-style: italic;
}

.option-archived-icon {
  font-size: 12px;
  width: 18px;
  text-align: center;
  flex-shrink: 0;
}

.option-drag-handle {
  cursor: grab;
  color: var(--tf-text-quaternary);
  font-size: 14px;
  user-select: none;
  line-height: 1;
  padding: 2px;
  border-radius: 3px;
  transition: color 150ms, background 150ms;
}

.option-drag-handle:hover {
  color: var(--tf-text-secondary);
  background: var(--tf-bg-hover);
}

.option-usage-count {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  white-space: nowrap;
}

.option-usage-count:not(.option-unused) {
  color: var(--tf-accent);
  font-weight: 500;
}

.option-unused {
  color: var(--tf-text-quaternary);
  font-style: italic;
}

.color-swatch {
  display: inline-block;
  width: 20px;
  height: 20px;
  border-radius: 3px;
  cursor: pointer;
  flex-shrink: 0;
  transition: transform 150ms;
}
.color-swatch:hover {
  transform: scale(1.15);
}

.color-palette {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 6px;
  padding: 8px;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  box-shadow: var(--tf-shadow);
}

.color-palette-item {
  width: 24px;
  height: 24px;
  border-radius: 3px;
  cursor: pointer;
  transition: transform 100ms;
  position: relative;
}
.color-palette-item:hover {
  transform: scale(1.2);
}
.color-palette-item.active::after {
  content: '✓';
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--tf-text-on-accent);
  font-size: 12px;
  font-weight: 700;
  text-shadow: 0 1px 1px rgba(0, 0, 0, 0.3);
}
.color-palette-clear {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--tf-text-tertiary);
  border: 1px dashed var(--tf-border);
}
.color-palette-clear.active {
  border-color: var(--tf-accent);
  color: var(--tf-accent);
}

.copy-from-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.options-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px;
  background: var(--tf-bg-body);
  border-radius: 4px;
  border: 1px solid var(--tf-border);
}

.option-desc-row {
  margin-left: 28px;
  padding: 4px 4px 4px 0;
}

.option-desc-row :deep(.arco-textarea) {
  font-size: 12px;
}

.desc-btn-active {
  color: var(--tf-accent) !important;
}

/* ========== 抽屉分区样式 ========== */

.drawer-sections {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.form-section {
  padding: 12px 0;
  border-bottom: 1px solid var(--tf-border-light, color-mix(in srgb, var(--tf-border) 50%, transparent));
}
.form-section:first-child {
  padding-top: 0;
}
.form-section:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.form-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  font-weight: 600;
  color: var(--tf-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 12px;
}

/* ========== 字段行为区块 ========== */

.behavior-section {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.behavior-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 0;
  border-bottom: 1px solid var(--tf-border-light, color-mix(in srgb, var(--tf-border) 50%, transparent));
}
.behavior-item:last-child { border-bottom: none; }

.behavior-item-main {
  display: flex;
  align-items: center;
  gap: 10px;
}

.behavior-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.behavior-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  padding-left: 38px;
  line-height: 1.4;
}

/* ========== 高级操作卡片 ========== */

.options-advanced-ops {
  margin-top: 12px;
  border-top: 1px solid var(--tf-border-light, color-mix(in srgb, var(--tf-border) 50%, transparent));
  padding-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.advanced-op-card {
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  overflow: hidden;
}

.advanced-op-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  background: var(--tf-bg-surface);
  cursor: pointer;
  transition: background 150ms;
}
.advanced-op-header:hover {
  background: var(--tf-bg-hover);
}

.advanced-op-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-secondary);
}

.advanced-op-body {
  padding: 8px 10px 10px;
}

.advanced-op-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin: 0 0 8px;
  line-height: 1.5;
}

.advanced-op-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-swatch--archived {
  opacity: 0.5;
}

/* ========== 类型转换相关样式 ========== */

.field-type-edit-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.convert-type-content {
  min-height: 100px;
}

.convert-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.convert-info-label {
  font-size: 13px;
  color: var(--tf-text-secondary);
  flex-shrink: 0;
}

.convert-no-targets {
  margin-top: 16px;
}

.convert-targets {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.convert-target-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 6px;
  border: 1px solid var(--tf-border);
  cursor: pointer;
  transition: border-color 150ms, background 150ms;
}

.convert-target-item:hover {
  background: var(--tf-bg-hover);
  border-color: var(--tf-text-tertiary);
}

.convert-target-item--selected {
  border-color: var(--tf-accent);
  background: color-mix(in srgb, var(--tf-accent) 8%, transparent);
}

.convert-target-item--selected:hover {
  border-color: var(--tf-accent);
  background: color-mix(in srgb, var(--tf-accent) 12%, transparent);
}

.convert-target-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.convert-target-warning {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  display: flex;
  align-items: center;
}

.convert-options {
  padding-left: 4px;
}
</style>
