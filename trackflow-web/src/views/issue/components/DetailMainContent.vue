<template>
  <main class="detail-main">
    <!-- Issue Key + Type -->
    <div class="sticky-header">
      <div class="identity-row">
        <span class="iss-key">{{ issueKey }}</span>
        <span class="type-badge" :class="'t-' + issueType.toLowerCase()">{{ issueType }}</span>
      </div>

      <!-- Title + Action Icons -->
      <div class="title-row">
        <h1 v-if="!editingTitle" class="iss-title" @dblclick="startEditTitle">{{ title }}</h1>
        <input
          v-else
          ref="titleInput"
          v-model="localTitle"
          class="title-edit-input"
          @keyup.enter="commitTitle"
          @keyup.escape="editingTitle = false"
          @blur="commitTitle"
        />
        <div class="title-actions" v-if="!editingTitle && !editingDesc">
          <button class="action-icon" title="编辑" @click="startEditDesc">&#9998;</button>
          <button class="action-icon" title="附件" @click="$emit('upload')">&#128206;</button>
          <button class="action-icon" title="链接" @click="$emit('add-link')">&#128279;</button>
          <button class="action-icon" title="更多">&#8943;</button>
        </div>
      </div>

      <!-- Tags -->
      <div class="tag-row" v-if="tags.length > 0 || true">
        <span
          v-for="tag in tags"
          :key="tag.id"
          class="tag-chip"
          :style="{ background: tag.color + '20', color: tag.color, borderColor: tag.color + '55' }"
        >
          {{ tag.name }}
          <span class="tag-x" @click="$emit('remove-tag', tag.id)">&times;</span>
        </span>
        <div class="tag-add-wrap">
          <button class="add-tag" @click="showTagPicker = !showTagPicker">+ 标签</button>
          <div class="tag-picker" v-if="showTagPicker">
            <input
              ref="tagSearchRef"
              v-model="tagSearch"
              class="tag-search-input"
              placeholder="搜索或创建标签..."
              @keyup.enter="createNewTag"
              @keyup.escape="showTagPicker = false"
            />
            <div class="tag-picker-list">
              <div
                v-for="t in filteredAvailableTags"
                :key="t.id"
                class="tag-picker-item"
                @click="selectTag(t)"
              >
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
          </div>
        </div>
      </div>
    </div>

    <!-- Description -->
    <div v-if="!editingDesc" class="description">
      <div v-if="description" v-html="descHtml" class="desc-rendered"></div>
      <p v-else class="desc-empty">暂无描述</p>
    </div>
    <RichEditor
      v-else
      :model-value="description"
      placeholder="输入描述内容..."
      @save="commitDesc"
      @cancel="editingDesc = false"
      @upload="() => $emit('upload')"
    />

    <!-- 关联 Issue -->
    <section class="section" v-if="links.length > 0">
      <div class="section-head">
        <h3>关联 ISSUE</h3>
        <button class="section-link" @click="$emit('add-link')">添加</button>
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
            <div v-for="link in group.items" :key="link.id" class="link-item">
              <router-link :to="`/issues/${link.issueId}`" class="link-key-ref">{{ link.issueKey }}</router-link>
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
        <button class="section-link" @click="$emit('upload')">上传</button>
      </div>
      <div v-if="attachments.length > 0" class="att-grid">
        <div v-for="att in attachments" :key="att.id" class="att-chip">
          <span class="att-name">{{ att.fileName }}</span>
          <span class="att-sz">{{ att.sizeText }}</span>
        </div>
      </div>
      <p v-else class="empty-hint">暂无附件</p>
    </section>

    <!-- Activity slot -->
    <slot name="activity"></slot>
  </main>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { renderMarkdown } from '@/utils/markdown'
import RichEditor from './RichEditor.vue'

export interface TagItem { id: string; name: string; color: string }
export interface LinkItem { id: string; typeLabel: string; issueId: string; issueKey: string; issueTitle: string; statusName: string; statusColor: string }
export interface AttachItem { id: string; fileName: string; sizeText: string }

const props = defineProps<{
  issueKey: string
  issueType: string
  title: string
  description: string
  tags: TagItem[]
  availableTags?: TagItem[]
  links: LinkItem[]
  attachments: AttachItem[]
}>()

const emit = defineEmits<{
  'update-title': [val: string]
  'update-desc': [val: string]
  'remove-tag': [id: string]
  'add-tag': [tag: TagItem]
  'create-tag': [name: string]
  'add-link': []
  'upload': []
}>()

const editingTitle = ref(false)
const localTitle = ref('')
const titleInput = ref<HTMLInputElement>()

const editingDesc = ref(false)
const descHtml = computed(() => renderMarkdown(props.description))

// Tag picker
const showTagPicker = ref(false)
const tagSearch = ref('')
const tagSearchRef = ref<HTMLInputElement>()

const filteredAvailableTags = computed(() => {
  const existingIds = new Set(props.tags.map(t => t.id))
  const available = (props.availableTags || []).filter(t => !existingIds.has(t.id))
  if (!tagSearch.value) return available
  const kw = tagSearch.value.toLowerCase()
  return available.filter(t => t.name.toLowerCase().includes(kw))
})

function selectTag(tag: TagItem) {
  emit('add-tag', tag)
  showTagPicker.value = false
  tagSearch.value = ''
}

function createNewTag() {
  if (!tagSearch.value.trim()) return
  emit('create-tag', tagSearch.value.trim())
  showTagPicker.value = false
  tagSearch.value = ''
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

/* Title */
.title-row {
  display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 8px;
}
.iss-title {
  font-size: 20px; font-weight: 700; color: var(--tf-text-primary);
  margin: 0; padding: 0; line-height: 1.3; flex: 1;
  letter-spacing: -0.3px;
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
.tag-picker-item.create { color: var(--tf-accent); font-weight: 500; }
.tag-picker-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.tag-picker-empty { padding: 12px; text-align: center; font-size: 11px; color: var(--tf-text-muted); }

/* Description */
.description { min-height: 32px; margin-bottom: 24px; padding: 0; }
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
.link-key-ref { color: var(--tf-accent); font-weight: 500; text-decoration: none; flex-shrink: 0; }
.link-key-ref:hover { text-decoration: underline; }
.link-title-text { color: var(--tf-text-secondary); flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.link-status { font-size: 11px; font-weight: 500; flex-shrink: 0; margin-left: auto; }

/* Attachments */
.att-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.att-chip {
  display: inline-flex; align-items: center; gap: 8px; padding: 8px 12px;
  background: var(--tf-bg-elevated); border-radius: 3px; font-size: 12px; color: var(--tf-text-secondary);
  border: 1px solid var(--tf-border);
  transition: background 150ms, border-color 150ms;
}
.att-chip:hover { border-color: var(--tf-accent); }
.att-sz { color: var(--tf-text-muted); font-size: 11px; }
.empty-hint { font-size: 11px; color: var(--tf-text-muted); margin: 0; }
</style>
