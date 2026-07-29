<template>
  <div class="cf-manage">
    <div class="cf-header">
      <h2 class="page-title">自定义字段管理</h2>
      <a-button v-if="activeTab === 'list'" type="primary" size="small" @click="openCreate">
        <template #icon><icon-plus /></template>
        创建自定义字段
      </a-button>
    </div>

    <!-- 双标签页 -->
    <a-tabs v-model:active-key="activeTab" size="small" class="cf-tabs">
      <a-tab-pane key="list" title="Fields List">
        <!-- 字段列表 + 详情侧边栏 -->
        <div class="cf-body">
          <div class="cf-table" :class="{ 'has-detail': !!selectedField }">
            <!-- 搜索框 + 批量操作工具栏 -->
            <div class="cf-toolbar">
              <a-input-search
                v-model="searchKeyword"
                placeholder="搜索字段名称..."
                size="small"
                allow-clear
                style="width: 220px"
              />
              <div v-if="selectedKeys.length > 0" class="batch-toolbar">
                <span class="batch-count">已选 {{ selectedKeys.length }} 项</span>
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
              </div>
            </div>
            <a-table
              :data="filteredFieldList"
              :loading="loading"
              :pagination="pagination"
              row-key="id"
              size="small"
              :row-selection="rowSelection"
              v-model:selected-keys="selectedKeys"
              @page-change="onPageChange"
              @row-click="onRowClick"
            >
              <template #columns>
                <a-table-column title="字段名称" data-index="name">
                  <template #cell="{ record }">
                    <span class="clickable-name">{{ record.name }}</span>
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
                <a-table-column title="选项值" :width="220">
                  <template #cell="{ record }">
                    <template v-if="record.fieldFormat === 'list' && record.options && record.options.length > 0">
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
                <a-table-column title="必填" data-index="isRequired" :width="60" align="center">
                  <template #cell="{ record }">
                    <icon-check v-if="record.isRequired" style="color: var(--tf-success)" />
                  </template>
                </a-table-column>
                <a-table-column title="全局" data-index="isForAll" :width="60" align="center">
                  <template #cell="{ record }">
                    <icon-check v-if="record.isForAll" style="color: var(--tf-accent)" />
                  </template>
                </a-table-column>
                <a-table-column title="私有" data-index="isPrivate" :width="60" align="center">
                  <template #cell="{ record }">
                    <icon-lock v-if="record.isPrivate" style="color: var(--tf-warning)" />
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
                <a-table-column title="列表可见" :width="80" align="center">
                  <template #cell="{ record }">
                    <icon-eye v-if="!record.isHiddenInList" style="color: var(--tf-success)" />
                    <icon-eye-invisible v-else style="color: var(--tf-text-quaternary)" />
                  </template>
                </a-table-column>
                <a-table-column title="操作" :width="120" align="center">
                  <template #cell="{ record }">
                    <a-button type="text" size="mini" @click.stop="openEdit(record)">编辑</a-button>
                    <a-button type="text" size="mini" status="danger" @click.stop="confirmDelete(record)">删除</a-button>
                  </template>
                </a-table-column>
              </template>
            </a-table>
          </div>

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
      <a-tab-pane key="projects" title="Fields in Projects">
        <FieldsInProjects ref="fieldsInProjectsRef" @field-created="loadList" />
      </a-tab-pane>
    </a-tabs>

    <!-- 创建/编辑抽屉 -->
    <a-drawer
      :visible="drawerVisible"
      :title="editingId ? '编辑自定义字段' : '创建自定义字段'"
      :width="480"
      @cancel="drawerVisible = false"
      @ok="handleSave"
      :ok-loading="saving"
      unmount-on-close
    >
      <a-form :model="form" layout="vertical" size="small">
        <a-form-item label="字段名称" required>
          <a-input v-model="form.name" placeholder="例如: 到期版本" :max-length="256" />
        </a-form-item>

        <a-form-item label="字段类型" required>
          <a-select v-model="form.fieldFormat" :disabled="!!editingId" placeholder="选择字段类型">
            <a-option v-for="t in fieldTypeOptions" :key="t.value" :value="t.value">{{ t.label }}</a-option>
          </a-select>
        </a-form-item>

        <a-form-item label="必填">
          <a-switch v-model="form.isRequired" />
        </a-form-item>

        <a-form-item label="全局可用">
          <a-switch v-model="form.isForAll" />
          <div class="form-help">开启后所有项目均可使用此字段</div>
        </a-form-item>

        <a-form-item label="隐藏于工单列表">
          <a-switch v-model="form.isHiddenInList" />
          <div class="form-help">开启后，此字段默认不出现在工单列表的列选择器中（用户仍可通过个人设置手动添加）</div>
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

        <!-- string 类型额外配置 -->
        <template v-if="form.fieldFormat === 'string'">
          <a-form-item label="最小长度">
            <a-input-number v-model="form.minLength" :min="0" />
          </a-form-item>
          <a-form-item label="最大长度">
            <a-input-number v-model="form.maxLength" :min="0" />
          </a-form-item>
          <a-form-item label="正则验证">
            <a-input v-model="form.regexp" placeholder="可选正则表达式" />
          </a-form-item>
        </template>

        <!-- text 类型额外配置 -->
        <template v-if="form.fieldFormat === 'text'">
          <a-form-item label="最大长度">
            <a-input-number v-model="form.maxLength" :min="0" placeholder="0 表示不限制" />
            <div class="form-help">支持 Markdown 格式的多行文本</div>
          </a-form-item>
        </template>

        <!-- list 类型选项管理 -->
        <template v-if="form.fieldFormat === 'list'">
          <a-form-item label="多值选择">
            <a-switch v-model="form.isMulti" :disabled="isMultiDisabled" />
            <div class="form-help">
              <template v-if="isMultiDisabled">
                该字段已被工单使用，无法切换单选/多选模式
              </template>
              <template v-else>
                开启后允许选择多个选项值（如影响版本、标签等）
              </template>
            </div>
          </a-form-item>

          <!-- 值集来源选择（仅创建模式显示） -->
          <a-form-item v-if="!editingId" label="值集来源">
            <a-radio-group v-model="valueSetSource" type="button" size="small">
              <a-radio value="new">新建值集</a-radio>
              <a-radio value="copy">从已有字段复制</a-radio>
            </a-radio-group>
          </a-form-item>

          <!-- 从已有字段复制：选择源字段 -->
          <a-form-item v-if="valueSetSource === 'copy' && !editingId" label="选择源字段">
            <a-select
              v-model="form.copyOptionsFromFieldId"
              placeholder="选择一个枚举类型字段"
              allow-clear
              @change="onSourceFieldChange"
            >
              <a-option v-for="f in enumFieldList" :key="f.id" :value="f.id">
                {{ f.name }}（{{ (f.options || []).filter(o => !o.isArchived).length }} 个选项）
              </a-option>
            </a-select>
            <div class="form-help">选择后将复制该字段的所有选项作为独立副本，后续修改互不影响</div>
          </a-form-item>

          <!-- 编辑模式下的"从其他字段复制值"操作 -->
          <a-form-item v-if="editingId" label="从其他字段复制值">
            <div class="copy-from-row">
              <a-select
                v-model="copyFromFieldId"
                placeholder="选择源字段追加选项"
                allow-clear
                style="flex: 1"
              >
                <a-option v-for="f in enumFieldList.filter(x => x.id !== editingId)" :key="f.id" :value="f.id">
                  {{ f.name }}（{{ (f.options || []).filter(o => !o.isArchived).length }} 个选项）
                </a-option>
              </a-select>
              <a-button type="outline" size="small" :disabled="!copyFromFieldId" @click="handleCopyFrom">
                复制
              </a-button>
            </div>
            <div class="form-help">将源字段的选项追加到当前选项列表中（跳过同名选项）</div>
          </a-form-item>

          <!-- 选项预览（从字段复制后） -->
          <a-form-item v-if="valueSetSource === 'copy' && !editingId && previewOptions.length > 0" label="选项预览">
            <div class="options-preview">
              <a-tag v-for="opt in previewOptions" :key="opt.value" size="small" :color="opt.color || undefined">
                {{ opt.value }}
              </a-tag>
            </div>
          </a-form-item>

          <a-form-item :label="valueSetSource === 'copy' && !editingId ? '调整选项（可修改复制后的选项）' : '选项列表'">
            <!-- 排序工具栏 -->
            <div v-if="form.options.length > 1" class="options-sort-toolbar">
              <a-button size="mini" type="text" @click="sortOptionsByName('asc')">
                <template #icon><icon-sort-ascending /></template>
                按名称升序
              </a-button>
              <a-button size="mini" type="text" @click="sortOptionsByName('desc')">
                <template #icon><icon-sort-descending /></template>
                按名称降序
              </a-button>
              <div v-if="editingId && archivedOptionCount > 0" class="options-archive-toggle">
                <a-switch v-model="showArchivedInDrawer" size="small" />
                <span class="archive-toggle-label">显示已归档 ({{ archivedOptionCount }})</span>
              </div>
            </div>
            <div class="options-list">
              <div
                v-for="(opt, idx) in visibleFormOptions"
                :key="opt.id || `new-${idx}`"
                class="option-item"
              >
                <div
                  class="option-row"
                  :class="{
                    'option-row--dragging': optionDragIndex === idx,
                    'option-row--drop-above': optionDropIndex === idx && optionDropPosition === 'above',
                    'option-row--drop-below': optionDropIndex === idx && optionDropPosition === 'below',
                    'option-row--archived': opt.isArchived
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
                  <a-input v-model="opt.value" placeholder="选项值" size="mini" style="flex:1" :disabled="opt.isArchived" />
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
                  <a-checkbox v-if="!opt.isArchived" v-model="opt.isDefault" size="small">默认</a-checkbox>
                  <!-- 选项使用统计（仅编辑模式且有 optionId 时显示） -->
                  <span
                    v-if="editingId && opt.id && optionUsageMap[opt.id] !== undefined"
                    class="option-usage-count"
                    :class="{ 'option-unused': optionUsageMap[opt.id] === 0 }"
                    :title="optionUsageMap[opt.id] > 0 ? `被 ${optionUsageMap[opt.id]} 个工单引用` : '未被任何工单使用'"
                  >
                    {{ optionUsageMap[opt.id] > 0 ? optionUsageMap[opt.id] : '未使用' }}
                  </span>
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
                  <!-- 删除按钮（仅非归档选项显示） -->
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
          </a-form-item>
        </template>

        <!-- 项目关联 -->
        <a-form-item :label="form.isForAll ? '附加到项目（可选）' : '关联项目'">
          <CheckboxGroupEnhanced
            v-model="form.projectIds"
            :options="projectCheckboxOptions"
            :search-threshold="8"
            :scroll-threshold="10"
          />
          <div v-if="form.isForAll" class="form-help">
            全局字段已对所有项目生效。此处选择的项目将额外创建项目级配置记录，便于后续设置条件显示、角色权限等。
          </div>
          <div v-else class="form-help">
            不选则字段不关联任何项目，可在项目字段管理中手动添加。
          </div>
        </a-form-item>

        <!-- Issue 类型关联 -->
        <a-form-item label="适用 Issue 类型">
          <CheckboxGroupEnhanced
            v-model="form.issueTypes"
            :options="issueTypeCheckboxOptions"
            :search-threshold="8"
            :scroll-threshold="10"
          />
          <div class="form-help">不选则适用所有类型</div>
        </a-form-item>
      </a-form>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { IconPlus, IconDelete, IconCheck, IconEye, IconEyeInvisible, IconClose, IconSortAscending, IconSortDescending, IconApps, IconFolder, IconLock, IconUnlock, IconInfoCircle } from '@arco-design/web-vue/es/icon'
import { Message, Modal } from '@arco-design/web-vue'
import { customFieldApi, projectApi, workflowApi } from '@/api'
import type { CustomFieldDefinitionVO, CustomFieldUsageVO, OptionUsageItemVO } from '@/api/types'
import { localizeIssueType } from '@/utils/fieldLabels'
import FieldsInProjects from './FieldsInProjects.vue'
import DefaultValueInput from './components/DefaultValueInput.vue'
import CheckboxGroupEnhanced from './components/CheckboxGroupEnhanced.vue'
import type { CheckboxOption } from './components/CheckboxGroupEnhanced.vue'

const activeTab = ref('list')
const fieldList = ref<CustomFieldDefinitionVO[]>([])
const loading = ref(false)
const pagination = reactive({ current: 1, pageSize: 20, total: 0 })
const projectList = ref<any[]>([])
const issueTypeOptions = ref<Array<{ value: string; label: string }>>([])

/** 搜索关键词 */
const searchKeyword = ref('')

/** 枚举字段内联展示最大选项数 */
const MAX_INLINE_OPTIONS = 5

/** 前端过滤后的字段列表 */
const filteredFieldList = computed(() => {
  if (!searchKeyword.value.trim()) return fieldList.value
  const kw = searchKeyword.value.trim().toLowerCase()
  return fieldList.value.filter(f => f.name.toLowerCase().includes(kw))
})
const selectedKeys = ref<string[]>([])
const rowSelection = reactive({
  type: 'checkbox' as const,
  showCheckedAll: true
})

/** 项目列表转换为 CheckboxGroupEnhanced 的选项格式 */
const projectCheckboxOptions = computed<CheckboxOption[]>(() =>
  projectList.value.map(p => ({
    value: p.id as string,
    label: p.name as string,
    extra: p.key as string,
    searchKeywords: [p.key as string]
  }))
)

/** Issue 类型列表转换为 CheckboxGroupEnhanced 的选项格式 */
const issueTypeCheckboxOptions = computed<CheckboxOption[]>(() =>
  issueTypeOptions.value.map(t => ({
    value: t.value,
    label: t.label
  }))
)

// Detail sidebar state
const selectedField = ref<CustomFieldDefinitionVO | null>(null)
const detailUsage = ref<CustomFieldUsageVO | null>(null)
const fieldsInProjectsRef = ref<InstanceType<typeof FieldsInProjects> | null>(null)
const showArchivedInDetail = ref(false)
const showArchivedInDrawer = ref(false)
/** 当前展开描述输入的选项索引（-1 表示全部收起） */
const expandedDescriptionIdx = ref(-1)

/** 侧边栏选项过滤 */
const detailFilteredOptions = computed(() => {
  if (!selectedField.value?.options) return []
  if (showArchivedInDetail.value) return selectedField.value.options
  return selectedField.value.options.filter(o => !o.isArchived)
})

/** 编辑抽屉中可见的选项（归档的根据开关显示/隐藏） */
const visibleFormOptions = computed(() => {
  if (showArchivedInDrawer.value) return form.options
  return form.options.filter(o => !o.isArchived)
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
  options: [] as Array<{ id?: string; value: string; isDefault: boolean; color?: string; isArchived?: boolean }>,
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
  { value: 'user', label: '用户' },
  { value: 'period', label: '时间周期' }
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
    const res = await customFieldApi.list({ page: pagination.current, pageSize: pagination.pageSize })
    fieldList.value = res.data?.list || []
    pagination.total = res.data?.pagination?.total || 0
  } catch {
    fieldList.value = []
  } finally {
    loading.value = false
  }
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
  optionUsageMap.value = {}
  showArchivedInDrawer.value = false
  expandedDescriptionIdx.value = -1
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
    .map(o => ({ id: o.id, value: o.value, isDefault: o.isDefault, color: o.color || undefined, isArchived: o.isArchived || false }))
  form.projectIds = record.projectIds || []
  form.issueTypes = record.issueTypes || []
  form.copyOptionsFromFieldId = undefined
  copyFromFieldId.value = null
  // 检查字段是否有数据——有则禁止切换 isMulti
  isMultiDisabled.value = false
  if (record.fieldFormat === 'list') {
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
        isMulti: form.fieldFormat === 'list' ? form.isMulti : undefined,
        options: form.fieldFormat === 'list'
          ? form.options.filter(o => !o.isArchived).map(o => ({ id: o.id, value: o.value, isDefault: o.isDefault, color: o.color || undefined }))
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
        isMulti: form.fieldFormat === 'list' ? form.isMulti : undefined,
        options: form.fieldFormat === 'list'
          ? form.options.map(o => ({ value: o.value, isDefault: o.isDefault, color: o.color || undefined }))
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
        ? `该字段为全局字段，当前适用于所有 ${usage.projectCount} 个项目。`
        : ''
      Modal.warning({
        title: '确认删除',
        content: `${projectInfo}确定要删除自定义字段「${record.name}」？此操作不可撤销。`,
        okText: '删除字段',
        cancelText: '取消',
        hideCancel: false,
        onOk: () => handleDelete(record.id)
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
      Modal.error({
        title: '⚠️ 删除将导致数据丢失',
        content: `字段「${record.name}」${projectInfo}当前${parts.join('；')}。此操作不可撤销。`,
        okText: hasValues ? `确认删除（影响 ${usage.issueCount} 个工单）` : '确认删除',
        cancelText: '取消',
        hideCancel: false,
        onOk: () => handleDelete(record.id)
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
  Modal.warning({
    title: '确认批量删除',
    content: `确定要删除选中的 ${selectedKeys.value.length} 个自定义字段？此操作不可撤销，关联的工单字段值将被永久清除。`,
    okText: `删除 ${selectedKeys.value.length} 个字段`,
    cancelText: '取消',
    hideCancel: false,
    onOk: handleBatchDelete
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

onMounted(() => {
  loadList()
  loadProjects()
  loadIssueTypes()
})
</script>

<style scoped>
.cf-manage {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
}

.cf-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
}

.cf-tabs {
  flex: 1;
}

.cf-body {
  display: flex;
  gap: 16px;
}

.cf-table {
  background: var(--tf-bg-surface);
  border-radius: 6px;
  border: 1px solid var(--tf-border);
  flex: 1;
  min-width: 0;
}
.cf-table.has-detail {
  flex: 1;
}

.cf-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--tf-border);
  flex-wrap: wrap;
}

.batch-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
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
  max-height: 600px;
  overflow-y: auto;
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
  gap: 8px;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px;
  border-radius: 4px;
  transition: background 150ms, box-shadow 150ms;
}

.option-row[draggable="true"] {
  cursor: grab;
}

.option-row[draggable="true"]:active {
  cursor: grabbing;
}

.option-row--dragging {
  opacity: 0.4;
  background: var(--tf-bg-surface);
}

.option-row--drop-above {
  box-shadow: 0 -2px 0 0 var(--tf-accent);
}

.option-row--drop-below {
  box-shadow: 0 2px 0 0 var(--tf-accent);
}

.option-row--archived {
  opacity: 0.6;
  background: var(--tf-bg-body);
  border: 1px dashed var(--tf-border);
}

.option-row--archived .option-drag-handle {
  visibility: hidden;
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
  min-width: 36px;
  text-align: center;
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--tf-bg-surface);
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
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
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
  color: #fff;
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

.option-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.option-desc-row {
  margin-left: 26px;
  padding: 4px 4px 4px 0;
}

.option-desc-row :deep(.arco-textarea) {
  font-size: 12px;
}

.desc-btn-active {
  color: var(--tf-accent) !important;
}
</style>
