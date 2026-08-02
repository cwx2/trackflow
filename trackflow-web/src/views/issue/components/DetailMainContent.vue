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
          <button class="action-icon" title="编辑" @click="startEditDesc">&#9998;</button>
          <button class="action-icon" title="附件" @click="$emit('upload')">&#128206;</button>
          <button class="action-icon" title="链接" @click="$emit('add-link')">&#128279;</button>
          <a-dropdown trigger="click" position="br">
            <button class="action-icon" title="更多操作">&#8943;</button>
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
              <router-link :to="`/issues/${link.issueKey}`" class="link-key-ref">{{ link.issueKey }}</router-link>
              <span class="link-title-text">{{ link.issueTitle }}</span>
              <span class="link-status" :style="{ color: link.statusColor }">{{ link.statusName }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 附件 -->
    <section class="section">
      <div class="section-head">
        <h3>附件</h3>
        <div v-if="!readonly" class="section-actions">
          <button class="section-link" @click="$emit('upload')">上传</button>
          <button class="section-link" @click="$emit('upload-private')">私有上传</button>
        </div>
      </div>
      <!-- 已有附件列表 -->
      <div v-if="attachments.length > 0" class="att-grid">
        <div v-for="att in attachments" :key="att.id" class="att-chip" :class="{ 'att-private': att.isPrivate }">
          <svg v-if="att.isPrivate" class="att-lock-icon" viewBox="0 0 16 16" width="12" height="12" :title="att.visibleToGroupNames?.join(', ') || '私有'">
            <path fill="currentColor" d="M4 6V4a4 4 0 1 1 8 0v2h1a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h1zm2 0h4V4a2 2 0 1 0-4 0v2z"/>
          </svg>
          <span class="att-name">{{ att.fileName }}</span>
          <span class="att-sz">{{ att.sizeText }}</span>
        </div>
      </div>
      <!-- 拖拽上传区域 -->
      <div
        v-if="!readonly"
        class="att-dropzone"
        :class="{ 'att-dropzone--active': isDragOver }"
        @dragover.prevent="onDragOver"
        @dragleave="onDragLeave"
        @drop.prevent="onDrop"
        @click="$emit('upload')"
      >
        <div class="att-dropzone-content">
          <icon-upload class="att-dropzone-icon" />
          <span class="att-dropzone-text">
            将文件拖拽至此上传，或<span class="att-dropzone-link">点击选择</span>
          </span>
          <span class="att-dropzone-hint">支持图片、文档、压缩包等，单文件最大 50MB</span>
        </div>
      </div>
      <p v-else-if="attachments.length === 0" class="empty-hint">暂无附件</p>
    </section>

    <!-- Activity slot -->
    <slot name="activity"></slot>
  </main>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { Message } from '@arco-design/web-vue'
import { IconCopy, IconDelete, IconBranch, IconSwap, IconPrinter, IconClockCircle, IconUpload, IconLock, IconSearch, IconPlus } from '@arco-design/web-vue/es/icon'
import { renderMarkdown } from '@/utils/markdown'
import { localizeIssueType } from '@/utils/fieldLabels'
import RichEditor from './RichEditor.vue'
import ChildIssuesList from './ChildIssuesList.vue'
import type { ChildIssueVO, ChildProgressVO } from '@/api/types'

export interface TagItem { id: string; name: string; color: string }
export interface LinkItem { id: string; typeLabel: string; issueId: string; issueKey: string; issueTitle: string; statusName: string; statusColor: string; isUnresolvedBlocker?: boolean }
export interface AttachItem { id: string; fileName: string; sizeText: string; isPrivate?: boolean; visibleToGroupNames?: string[] }

const props = defineProps<{
  issueKey: string
  issueType: string
  title: string
  description: string
  tags: TagItem[]
  availableTags?: TagItem[]
  links: LinkItem[]
  attachments: AttachItem[]
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
  'upload': []
  'upload-private': []
  'upload-files': [files: File[]]
  'copy-id': []
  'clone': []
  'move': []
  'delete': []
  'add-time': []
  'find-similar': []
  'create-subtask': []
}>()

// ========== 拖拽上传 ==========
const isDragOver = ref(false)
let dragLeaveTimer: ReturnType<typeof setTimeout> | null = null

function onDragOver() {
  if (dragLeaveTimer) {
    clearTimeout(dragLeaveTimer)
    dragLeaveTimer = null
  }
  isDragOver.value = true
}

function onDragLeave() {
  // 使用 timer 防止在子元素间移动时闪烁
  dragLeaveTimer = setTimeout(() => {
    isDragOver.value = false
  }, 50)
}

function onDrop(e: DragEvent) {
  isDragOver.value = false
  if (dragLeaveTimer) {
    clearTimeout(dragLeaveTimer)
    dragLeaveTimer = null
  }
  
  const files = e.dataTransfer?.files
  if (files && files.length > 0) {
    emit('upload-files', Array.from(files))
  }
}

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

function selectTag(tag: TagItem) {
  emit('add-tag', tag)
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
  navigator.clipboard.writeText(text)
  Message.success('已复制 ID 和摘要')
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
  overflow-y: auto;
  padding: 0 24px 48px;
  min-width: 0;
}

.sticky-header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: var(--tf-bg-body);
  padding: 16px 0 8px;
  border-bottom: 1px solid var(--tf-border-light);
  margin-bottom: 16px;
}

.identity-row { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.iss-key { font-size: 12px; color: var(--tf-text-tertiary); }
.type-badge { font-size: 11px; padding: 2px 6px; border-radius: 3px; font-weight: 600; }
.t-bug { background: #d32f2f; color: #fff; }
.t-task { background: #1976d2; color: #fff; }
.t-feature { background: #388e3c; color: #fff; }
.t-epic { background: #7b1fa2; color: #fff; }
.t-story { background: #f57c00; color: #fff; }

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
.danger-option :deep(.arco-dropdown-option-content) { color: #f85149; }
.danger-option :deep(.arco-icon) { color: #f85149; }

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
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
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
  background: var(--tf-accent); color: #fff; border: none; cursor: pointer;
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
  background: rgba(248, 81, 73, 0.06);
  border-left: 2px solid #f85149;
  padding-left: 6px;
}
.link-block-icon { font-size: 11px; flex-shrink: 0; }
.link-key-ref { color: var(--tf-accent); font-weight: 500; text-decoration: none; flex-shrink: 0; }
.link-key-ref:hover { text-decoration: underline; }
.link-title-text { color: var(--tf-text-secondary); flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.link-status { font-size: 11px; font-weight: 500; flex-shrink: 0; margin-left: auto; }

/* Attachments */
.att-grid { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.att-chip {
  display: inline-flex; align-items: center; gap: 8px; padding: 8px 12px;
  background: var(--tf-bg-elevated); border-radius: 3px; font-size: 12px; color: var(--tf-text-secondary);
  border: 1px solid var(--tf-border);
  transition: background 150ms, border-color 150ms;
}
.att-chip:hover { border-color: var(--tf-accent); }
.att-private { border-color: var(--color-warning-light, #d29922); background: rgba(210, 153, 34, 0.05); }
.att-private:hover { border-color: var(--color-warning-light, #d29922); }
.att-lock-icon { color: var(--color-warning-light, #d29922); flex-shrink: 0; }
.att-sz { color: var(--tf-text-muted); font-size: 11px; }

/* 拖拽上传区域 */
.att-dropzone {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  border: 2px dashed var(--tf-border);
  border-radius: 6px;
  background: var(--tf-bg-surface);
  cursor: pointer;
  transition: border-color 200ms, background 200ms;
}
.att-dropzone:hover {
  border-color: var(--tf-accent);
  background: var(--tf-bg-hover);
}
.att-dropzone--active {
  border-color: var(--tf-accent);
  background: rgba(var(--tf-accent-rgb, 88, 166, 255), 0.08);
  border-style: solid;
}
.att-dropzone-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  text-align: center;
}
.att-dropzone-icon {
  font-size: 24px;
  color: var(--tf-text-muted);
  transition: color 200ms;
}
.att-dropzone:hover .att-dropzone-icon,
.att-dropzone--active .att-dropzone-icon {
  color: var(--tf-accent);
}
.att-dropzone-text {
  font-size: 13px;
  color: var(--tf-text-secondary);
}
.att-dropzone-link {
  color: var(--tf-accent);
}
.att-dropzone-hint {
  font-size: 11px;
  color: var(--tf-text-muted);
}

.section-actions { display: flex; gap: 12px; }
.empty-hint { font-size: 11px; color: var(--tf-text-muted); margin: 0; }
</style>
