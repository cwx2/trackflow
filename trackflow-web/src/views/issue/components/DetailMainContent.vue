<template>
  <main class="detail-main">
    <!-- Issue Key + Type -->
    <div class="sticky-header">
      <div class="identity-row">
        <span class="iss-key">{{ issueKey }}</span>
        <span class="type-badge" :class="'t-' + issueType.toLowerCase()">{{ localizeIssueType(issueType) }}</span>
        <a-tooltip content="复制 ID 和摘要" position="right" mini>
          <button class="copy-id-btn" @click="onCopyIdAndSummary" aria-label="复制 ID 和摘要">
            <icon-copy />
          </button>
        </a-tooltip>
      </div>

      <!-- Title + Action Icons -->
      <div class="title-row">
        <h1 v-if="!editingTitle" class="iss-title" :class="{ editable: !readonly }" @click="!readonly && startEditTitle()">{{ title }}</h1>
        <input
          v-else
          ref="titleInput"
          v-model="localTitle"
          class="title-edit-input"
          @keyup.enter="commitTitle"
          @keyup.escape="editingTitle = false"
          @blur="commitTitle"
        />
        <div class="title-actions" v-if="!editingTitle && !editingDesc && !readonly">
          <a-tooltip content="编辑描述" position="bottom" mini>
            <button class="action-icon" @click="startEditDesc"><icon-edit /></button>
          </a-tooltip>
          <a-tooltip content="上传附件" position="bottom" mini>
            <button class="action-icon" @click="$emit('upload')"><icon-attachment /></button>
          </a-tooltip>
          <a-tooltip content="添加链接" position="bottom" mini>
            <button class="action-icon" @click="$emit('add-link')"><icon-link /></button>
          </a-tooltip>
          <a-dropdown trigger="click" position="br">
            <a-tooltip content="更多操作" position="bottom" mini>
              <button class="action-icon"><icon-more /></button>
            </a-tooltip>
            <template #content>
              <a-doption @click="onCopyIdAndSummary">
                <template #icon><icon-copy /></template>
                复制 ID 和摘要
              </a-doption>
              <a-doption @click="handlePrint">
                <template #icon><icon-printer /></template>
                打印工单
              </a-doption>
              <a-doption v-if="showAddTime" @click="$emit('add-time')">
                <template #icon><icon-clock-circle /></template>
                添加工时记录
              </a-doption>
              <a-doption @click="$emit('upload')">
                <template #icon><icon-upload /></template>
                上传附件
              </a-doption>
              <a-doption @click="$emit('upload-private')">
                <template #icon><icon-lock /></template>
                上传私有附件
              </a-doption>
              <a-doption @click="handleFindSimilar">
                <template #icon><icon-search /></template>
                查找相似工单
              </a-doption>
              <a-doption @click="$emit('clone')">
                <template #icon><icon-branch /></template>
                克隆工单
              </a-doption>
              <a-doption v-if="!readonly" @click="$emit('create-subtask')">
                <template #icon><icon-plus /></template>
                创建子工单
              </a-doption>
              <a-doption v-if="canMove" @click="$emit('move')">
                <template #icon><icon-swap /></template>
                移动到项目...
              </a-doption>
              <a-doption v-if="canDelete" class="danger-option" @click="$emit('delete')">
                <template #icon><icon-delete /></template>
                删除工单
              </a-doption>
            </template>
          </a-dropdown>
        </div>
      </div>

      <!-- Tags -->
      <div class="tag-row" v-if="tags.length > 0 || !readonly">
        <span
          v-for="tag in tags"
          :key="tag.id"
          class="tag-chip"
          :style="{ background: tag.color + '20', color: tag.color, borderColor: tag.color + '55' }"
        >
          {{ tag.name }}
          <span v-if="!readonly" class="tag-x" @click="$emit('remove-tag', tag.id)">&times;</span>
        </span>
        <div v-if="!readonly" class="tag-add-wrap">
          <button class="add-tag" @click="showTagPicker = !showTagPicker">+ 标签</button>
          <div class="tag-picker" v-if="showTagPicker">
            <input
              ref="tagSearchRef"
              v-model="tagSearch"
              class="tag-search-input"
              placeholder="搜索或创建标签..."
              @keyup.enter="createNewTag"
              @keyup.escape="cancelTagPicker"
            />
            <div class="tag-picker-list">
              <div
                v-for="t in filteredAvailableTags"
                :key="t.id"
                class="tag-picker-item"
                :class="{ selected: selectedTagIds.has(t.id) }"
                @click="toggleTagSelection(t)"
              >
                <span class="tag-picker-check">{{ selectedTagIds.has(t.id) ? '✓' : '' }}</span>
                <span class="tag-picker-dot" :style="{ background: t.color }"></span>
                {{ t.name }}
              </div>
              <div v-if="filteredAvailableTags.length === 0 && tagSearch" class="tag-picker-item create" @click="createNewTag">
                + 创建 "{{ tagSearch }}"
              </div>
              <div v-if="filteredAvailableTags.length === 0 && !tagSearch" class="tag-picker-empty">
                暂无可用标签
              </div>
            </div>
            <div class="tag-picker-actions" v-if="selectedTagIds.size > 0">
              <button class="tag-picker-confirm" @click="confirmTagSelection">添加 {{ selectedTagIds.size }} 个标签</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Description -->
    <div v-if="!editingDesc" class="description" :class="{ editable: !readonly }" @click="!readonly && startEditDesc()">
      <div v-if="description" v-html="descHtml" class="desc-rendered"></div>
      <p v-else class="desc-empty">{{ readonly ? '暂无描述' : '点击添加描述...' }}</p>
    </div>
    <RichEditor
      v-else
      :model-value="description"
      placeholder="输入描述内容..."
      @save="commitDesc"
      @cancel="editingDesc = false"
      @upload="() => $emit('upload')"
    />

    <!-- 子任务 -->
    <ChildIssuesList
      v-if="children && children.length > 0"
      :children="children"
      :progress="childProgress"
      :show-progress="true"
    />

    <!-- 关联 Issue -->
    <section class="section" v-if="links.length > 0">
      <div class="section-head">
        <h3>关联 ISSUE</h3>
        <button v-if="!readonly" class="section-link" @click="$emit('add-link')">添加</button>
      </div>
      <!-- 按类型分组显示 -->
      <div class="link-groups">
        <div v-for="group in linkGroups" :key="group.type" class="link-group">
          <div class="link-group-header" @click="group.expanded = !group.expanded">
            <span class="link-group-arrow">{{ group.expanded ? '▾' : '▸' }}</span>
            <span class="link-group-type">{{ group.type }}</span>
            <span class="link-group-count">{{ group.items.length }}</span>
          </div>
          <div v-if="group.expanded" class="link-group-items">
            <div v-for="link in group.items" :key="link.id" class="link-item" :class="{ 'link-blocked': link.isUnresolvedBlocker }">
              <span v-if="link.isUnresolvedBlocker" class="link-block-icon" title="未解决的阻塞">⛔</span>
              <IssuePriorityBadge
                v-if="link.priority"
                :priority="link.priority"
                :color="link.priorityColor"
                mode="block"
              />
              <router-link :to="`/issues/${link.issueKey}`" class="link-key-ref">{{ link.issueKey }}</router-link>
              <span class="link-title-text">{{ link.issueTitle }}</span>
              <span class="link-status" :style="{ color: link.statusColor }">{{ link.statusName }}</span>
              <button v-if="!readonly" class="link-delete-btn" title="删除关联" @click.stop="$emit('delete-link', link.id)">
                <icon-close />
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 附件 -->
    <AttachmentSection
      :attachments="attachments"
      :readonly="readonly"
      @upload="$emit('upload')"
      @upload-private="$emit('upload-private')"
      @upload-files="(files: File[]) => $emit('upload-files', files)"
      @delete="(id: string) => $emit('delete-attachment', id)"
      @delete-all="$emit('delete-all-attachments')"
    />

    <!-- Activity slot -->
    <slot name="activity"></slot>
  </main>
</template>

<script setup lang="ts">
import { copyToClipboard } from '@/utils/clipboard'
import { ref, computed, nextTick } from 'vue'

import { IconCopy, IconDelete, IconBranch, IconSwap, IconPrinter, IconClockCircle, IconUpload, IconLock, IconSearch, IconPlus, IconEdit, IconLink, IconMore, IconAttachment, IconClose } from '@arco-design/web-vue/es/icon'
import { renderMarkdown } from '@/utils/markdown'
import { localizeIssueType } from '@/utils/fieldLabels'
import { IssuePriorityBadge } from '@/components/base'
import RichEditor from '@/components/RichEditor.vue'
import ChildIssuesList from './ChildIssuesList.vue'
import AttachmentSection from './AttachmentSection.vue'
import type { AttachmentItem } from './AttachmentSection.vue'
import type { ChildIssueVO, ChildProgressVO } from '@/api/types'

export interface TagItem { id: string; name: string; color: string }
export interface LinkItem { id: string; typeLabel: string; issueId: string; issueKey: string; issueTitle: string; statusName: string; statusColor: string; isUnresolvedBlocker?: boolean; priority?: string; priorityColor?: string; priorityOrder?: number }

const props = defineProps<{
  issueKey: string
  issueType: string
  title: string
  description: string
  tags: TagItem[]
  availableTags?: TagItem[]
  links: LinkItem[]
  attachments: AttachmentItem[]
  readonly?: boolean
  canDelete?: boolean
  canMove?: boolean
  showAddTime?: boolean
  children?: ChildIssueVO[]
  childProgress?: ChildProgressVO | null
}>()

const emit = defineEmits<{
  'update-title': [val: string]
  'update-desc': [val: string]
  'remove-tag': [id: string]
  'add-tag': [tag: TagItem]
  'add-tags': [tags: TagItem[]]
  'create-tag': [name: string]
  'add-link': []
  'delete-link': [linkId: string]
  'upload': []
  'upload-private': []
  'upload-files': [files: File[]]
  'delete-attachment': [id: string]
  'delete-all-attachments': []
  'copy-id': []
  'clone': []
  'move': []
  'delete': []
  'add-time': []
  'find-similar': []
  'create-subtask': []
}>()

// ========== Title editing ==========
const editingTitle = ref(false)
const localTitle = ref('')
const titleInput = ref<HTMLInputElement>()

const editingDesc = ref(false)
const descHtml = computed(() => renderMarkdown(props.description))

// Tag picker
const showTagPicker = ref(false)
const tagSearch = ref('')
const tagSearchRef = ref<HTMLInputElement>()
const selectedTagIds = ref<Set<string>>(new Set())

const filteredAvailableTags = computed(() => {
  const existingIds = new Set(props.tags.map(t => t.id))
  const available = (props.availableTags || []).filter(t => !existingIds.has(t.id))
  if (!tagSearch.value) return available
  const kw = tagSearch.value.toLowerCase()
  return available.filter(t => t.name.toLowerCase().includes(kw))
})

function toggleTagSelection(tag: TagItem) {
  if (selectedTagIds.value.has(tag.id)) {
    selectedTagIds.value.delete(tag.id)
  } else {
    selectedTagIds.value.add(tag.id)
  }
  // Force reactivity
  selectedTagIds.value = new Set(selectedTagIds.value)
}

function confirmTagSelection() {
  if (selectedTagIds.value.size === 0) {
    showTagPicker.value = false
    return
  }
  const allAvailable = (props.availableTags || [])
  const selectedTags = allAvailable.filter(t => selectedTagIds.value.has(t.id))
  if (selectedTags.length === 1) {
    emit('add-tag', selectedTags[0])
  } else if (selectedTags.length > 1) {
    emit('add-tags', selectedTags)
  }
  showTagPicker.value = false
  tagSearch.value = ''
  selectedTagIds.value = new Set()
}

function cancelTagPicker() {
  showTagPicker.value = false
  tagSearch.value = ''
  selectedTagIds.value = new Set()
}

function createNewTag() {
  if (!tagSearch.value.trim()) return
  emit('create-tag', tagSearch.value.trim())
  showTagPicker.value = false
  tagSearch.value = ''
}

// --- Show More menu actions ---
function onCopyIdAndSummary() {
  const text = `${props.issueKey} ${props.title}`
  copyToClipboard(text, { successMessage: '已复制 ID 和摘要' })
}

function handlePrint() {
  window.print()
}

function handleFindSimilar() {
  // Open list page in new tab with current issue title as search keyword
  const keywords = props.title.split(/\s+/).slice(0, 5).join(' ')
  const url = `/issues?keyword=${encodeURIComponent(keywords)}`
  window.open(url, '_blank')
}

function startEditTitle() {
  localTitle.value = props.title
  editingTitle.value = true
  nextTick(() => titleInput.value?.focus())
}

// Link groups
const linkGroups = computed(() => {
  const map = new Map<string, { type: string; expanded: boolean; items: LinkItem[] }>()
  for (const link of props.links) {
    if (!map.has(link.typeLabel)) {
      map.set(link.typeLabel, { type: link.typeLabel, expanded: true, items: [] })
    }
    map.get(link.typeLabel)!.items.push(link)
  }
  return Array.from(map.values())
})
function commitTitle() {
  if (!editingTitle.value) return
  const v = localTitle.value.trim()
  if (v && v !== props.title) emit('update-title', v)
  editingTitle.value = false
}

function startEditDesc() {
  editingDesc.value = true
}
function commitDesc(content: string) {
  emit('update-desc', content)
  editingDesc.value = false
}
</script>

<style scoped>
.detail-main {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 24px 24px;
  min-width: 0;
}

.sticky-header {
  position: sticky;
  top: 0;
  z-index: 5;
  background: var(--tf-bg-body);
  padding: 16px 0 8px;
  border-bottom: 1px solid var(--tf-border-light);
  margin-bottom: 16px;
}

.identity-row { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.iss-key { font-size: 12px; color: var(--tf-text-tertiary); }
.type-badge { font-size: 11px; padding: 2px 6px; border-radius: 3px; font-weight: 600; }
.t-bug { background: var(--tf-type-bug); color: var(--tf-text-on-accent); }
.t-task { background: var(--tf-type-task); color: var(--tf-text-on-accent); }
.t-feature { background: var(--tf-type-feature); color: var(--tf-text-on-accent); }
.t-epic { background: var(--tf-type-epic); color: var(--tf-text-on-accent); }
.t-story { background: var(--tf-type-story); color: var(--tf-text-on-accent); }

/* Copy ID button */
.copy-id-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
  cursor: pointer;
  border-radius: 3px;
  opacity: 0;
  transition: opacity 150ms, color 150ms, background 150ms;
  font-size: 13px;
}
.identity-row:hover .copy-id-btn {
  opacity: 1;
}
.copy-id-btn:hover {
  color: var(--tf-text-primary);
  background: var(--tf-bg-hover);
}

/* Title */
.title-row {
  display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 8px;
}
.iss-title {
  font-size: 20px; font-weight: 700; color: var(--tf-text-primary);
  margin: 0; padding: 0; line-height: 1.3; flex: 1;
  letter-spacing: -0.3px;
}
.iss-title.editable {
  cursor: text;
  border-radius: 3px;
  padding: 2px 4px;
  margin: -2px -4px;
  transition: background 150ms, border-color 150ms;
  border-bottom: 1px solid transparent;
}
.iss-title.editable:hover {
  background: var(--tf-bg-hover);
  border-bottom-color: var(--tf-text-muted, #6b7280);
  border-bottom-style: dashed;
}
.title-edit-input {
  font-size: 20px; font-weight: 600; width: 100%; flex: 1;
  background: var(--tf-bg-elevated); border: 1px solid var(--tf-accent);
  border-radius: 3px; padding: 4px 8px; color: var(--tf-text-primary); outline: none;
}
.title-actions { display: flex; align-items: center; gap: 4px; flex-shrink: 0; padding-top: 4px; }
.action-icon {
  background: none; border: none; color: var(--tf-text-tertiary);
  cursor: pointer; padding: 4px; border-radius: 3px; display: flex; align-items: center;
  font-size: 15px;
  transition: color 150ms, background 150ms;
}
.action-icon:hover { color: var(--tf-text-primary); background: var(--tf-bg-hover); }

/* More actions dropdown */
.danger-option :deep(.arco-dropdown-option-content) { color: var(--tf-danger); }
.danger-option :deep(.arco-icon) { color: var(--tf-danger); }

/* Tags */
.tag-row { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; align-items: center; }
.tag-chip {
  font-size: 11px; padding: 2px 8px; border-radius: 3px; border: 1px solid;
  display: inline-flex; align-items: center; gap: 4px;
  transition: opacity 150ms;
}
.tag-x { cursor: pointer; opacity: .5; font-size: 12px; transition: opacity 150ms; }
.tag-x:hover { opacity: 1; }

.tag-add-wrap { position: relative; }
.add-tag {
  font-size: 11px; padding: 2px 8px; border-radius: 3px;
  background: none; border: 1px dashed var(--tf-border); color: var(--tf-text-muted); cursor: pointer;
  transition: border-color 150ms, color 150ms;
}
.add-tag:hover { border-color: var(--tf-accent); color: var(--tf-accent); }

.tag-picker {
  position: absolute; top: 100%; left: 0; z-index: 20;
  margin-top: 4px; width: 220px;
  background: var(--tf-bg-elevated); border: 1px solid var(--tf-border);
  border-radius: 6px; overflow: hidden;
  box-shadow: var(--tf-shadow-xl);
}
.tag-search-input {
  width: 100%; padding: 8px 12px; border: none; outline: none;
  background: var(--tf-bg-surface); color: var(--tf-text-primary);
  font-size: 12px; border-bottom: 1px solid var(--tf-border);
}
.tag-search-input::placeholder { color: var(--tf-text-muted); }
.tag-picker-list { max-height: 180px; overflow-y: auto; padding: 4px 0; }
.tag-picker-item {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 12px; cursor: pointer; font-size: 12px;
  color: var(--tf-text-primary);
  transition: background 150ms;
}
.tag-picker-item:hover { background: var(--tf-bg-hover); }
.tag-picker-item.selected { background: var(--tf-bg-active); }
.tag-picker-item.create { color: var(--tf-accent); font-weight: 500; }
.tag-picker-check { width: 14px; font-size: 11px; color: var(--tf-accent); font-weight: 600; text-align: center; flex-shrink: 0; }
.tag-picker-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.tag-picker-empty { padding: 12px; text-align: center; font-size: 11px; color: var(--tf-text-muted); }
.tag-picker-actions {
  padding: 6px 8px; border-top: 1px solid var(--tf-border);
  display: flex; justify-content: flex-end;
}
.tag-picker-confirm {
  padding: 4px 10px; border-radius: 4px; font-size: 11px; font-weight: 500;
  background: var(--tf-accent); color: var(--tf-text-on-accent); border: none; cursor: pointer;
  transition: opacity 150ms;
}
.tag-picker-confirm:hover { opacity: 0.85; }

/* Description */
.description { min-height: 32px; margin-bottom: 24px; padding: 0; }
.description.editable {
  cursor: text;
  border-radius: 4px;
  padding: 8px;
  margin: -8px;
  margin-bottom: 16px;
  transition: background 150ms;
}
.description.editable:hover { background: var(--tf-bg-hover); }
.desc-rendered { font-size: 13px; line-height: 1.6; color: var(--tf-text-secondary); }
.desc-rendered :deep(h1), .desc-rendered :deep(h2), .desc-rendered :deep(h3) { color: var(--tf-text-primary); margin-top: 16px; margin-bottom: 8px; }
.desc-rendered :deep(h1) { font-size: 1.3em; }
.desc-rendered :deep(h2) { font-size: 1.15em; }
.desc-rendered :deep(h3) { font-size: 1.05em; }
.desc-rendered :deep(a) { color: var(--tf-text-link); }
.desc-rendered :deep(code) { background: var(--tf-bg-code); padding: 1px 4px; border-radius: 2px; font-size: 12px; }
.desc-rendered :deep(pre) { background: var(--tf-bg-code); padding: 12px 16px; border-radius: 4px; overflow-x: auto; margin: 8px 0; border: 1px solid var(--tf-border); }
.desc-rendered :deep(pre code) { background: none; padding: 0; }
.desc-rendered :deep(img) { max-width: 100%; border-radius: 3px; }
.desc-rendered :deep(blockquote) { border-left: 3px solid var(--tf-accent); padding-left: 12px; color: var(--tf-text-tertiary); margin: 8px 0; }
.desc-rendered :deep(ul), .desc-rendered :deep(ol) { padding-left: 20px; }
.desc-rendered :deep(p) { margin: 4px 0; }
.desc-empty { color: var(--tf-text-muted); font-style: italic; margin: 0; font-size: 13px; }

/* Sections */
.section { margin-bottom: 24px; padding-top: 16px; border-top: 1px solid var(--tf-border-light); }
.section:first-of-type { border-top: none; }
.section-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.section-head h3 { font-size: 11px; font-weight: 700; color: var(--tf-text-tertiary); margin: 0; letter-spacing: 0.5px; text-transform: uppercase; }
.section-link {
  font-size: 11px; color: var(--tf-accent); background: none; border: none; cursor: pointer;
  transition: opacity 150ms;
}
.section-link:hover { text-decoration: underline; }

/* Links */
.link-groups { display: flex; flex-direction: column; gap: 4px; }
.link-group-header {
  display: flex; align-items: center; gap: 6px; padding: 4px 8px;
  cursor: pointer; border-radius: 3px;
  transition: background 150ms;
}
.link-group-header:hover { background: var(--tf-bg-hover); }
.link-group-arrow { font-size: 10px; color: var(--tf-text-muted); width: 12px; }
.link-group-type { font-size: 12px; color: var(--tf-text-secondary); font-weight: 500; }
.link-group-count { font-size: 11px; color: var(--tf-text-muted); }
.link-group-items { padding-left: 20px; }
.link-item {
  display: flex; align-items: center; gap: 8px; padding: 6px 8px;
  font-size: 12px; border-radius: 3px;
  transition: background 150ms;
}
.link-item:hover { background: var(--tf-bg-hover); }
.link-item.link-blocked {
  background: var(--tf-danger-bg);
  border-left: 2px solid var(--tf-danger);
  padding-left: 6px;
}
.link-block-icon { font-size: 11px; flex-shrink: 0; }
.link-key-ref { color: var(--tf-accent); font-weight: 500; text-decoration: none; flex-shrink: 0; }
.link-key-ref:hover { text-decoration: underline; }
.link-title-text { color: var(--tf-text-secondary); flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.link-status { font-size: 11px; font-weight: 500; flex-shrink: 0; margin-left: auto; }
.link-delete-btn {
  display: flex; align-items: center; justify-content: center;
  width: 20px; height: 20px; border: none; background: none;
  color: var(--tf-text-tertiary); cursor: pointer; border-radius: 3px;
  flex-shrink: 0; padding: 0; margin-left: 4px;
  opacity: 0; pointer-events: none;
  transition: opacity 150ms, color 150ms, background 150ms;
}
.link-delete-btn:hover { color: var(--tf-danger); background: var(--tf-danger-bg); }
.link-item:hover .link-delete-btn { opacity: 1; pointer-events: auto; }

/* Attachments: now in AttachmentSection.vue */

.section-actions { display: flex; gap: 12px; }
.empty-hint { font-size: 11px; color: var(--tf-text-muted); margin: 0; }
</style>
