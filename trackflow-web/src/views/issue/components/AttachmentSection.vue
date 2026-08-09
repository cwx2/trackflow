<template>
  <section class="att-section">
    <!-- Header -->
    <div class="att-header" @click="toggleCollapse">
      <span class="att-collapse-arrow">{{ collapsed ? '▸' : '▾' }}</span>
      <h3 class="att-title">附件 {{ attachments.length }}</h3>
      <div class="att-header-actions" v-if="!readonly" @click.stop>
        <button class="att-action-btn" @click="$emit('upload')" title="上传附件">
          <icon-upload :size="14" />
        </button>
        <a-dropdown trigger="click" position="br">
          <button class="att-action-btn" title="更多操作">
            <icon-more :size="14" />
          </button>
          <template #content>
            <a-doption @click="toggleViewMode">
              <template #icon><icon-apps v-if="viewMode === 'list'" /><icon-list v-else /></template>
              {{ viewMode === 'grid' ? '以列表形式查看' : '以缩略图形式查看' }}
            </a-doption>
            <a-doption @click="sortBy('date-desc')">
              <template #icon><icon-sort-descending /></template>
              按日期排序：最新在前
            </a-doption>
            <a-doption @click="sortBy('date-asc')">
              <template #icon><icon-sort-ascending /></template>
              按日期排序：最早在前
            </a-doption>
            <a-doption @click="sortBy('type')">
              <template #icon><icon-file /></template>
              按类型排序
            </a-doption>
            <a-doption @click="downloadAll" :disabled="attachments.length === 0">
              <template #icon><icon-download /></template>
              下载全部
            </a-doption>
            <a-doption class="danger-opt" @click="confirmDeleteAll" :disabled="attachments.length === 0">
              <template #icon><icon-delete /></template>
              全部删除
            </a-doption>
          </template>
        </a-dropdown>
      </div>
    </div>

    <!-- Content (collapsible) -->
    <div v-show="!collapsed" class="att-body">
      <!-- Grid View (Thumbnails) -->
      <div v-if="viewMode === 'grid' && sortedAttachments.length > 0" class="att-grid">
        <div
          v-for="att in sortedAttachments"
          :key="att.id"
          class="att-card"
          :class="{ 'att-card--private': att.isPrivate }"
          @click="handleClick(att)"
        >
          <!-- Image thumbnail -->
          <div v-if="isImage(att)" class="att-thumb">
            <img
              v-if="getImageSrc(att.filePath)"
              :src="getImageSrc(att.filePath)"
              :alt="att.fileName"
              class="att-thumb-img"
            />
            <div v-else class="att-thumb-loading">
              <a-spin :size="16" />
            </div>
            <!-- Hover overlay: pointer-events: none on container, auto on buttons -->
            <div class="att-overlay">
              <button class="att-ov-btn" @click.stop="download(att)" title="下载">
                <icon-download :size="14" />
              </button>
              <button v-if="!readonly" class="att-ov-btn att-ov-btn--danger" @click.stop="confirmDelete(att)" title="删除">
                <icon-delete :size="14" />
              </button>
            </div>
          </div>
          <!-- Non-image file icon -->
          <div v-else class="att-thumb att-thumb--file">
            <!-- Video thumbnail with play overlay -->
            <template v-if="isVideo(att)">
              <span class="att-file-icon">{{ getFileIcon(att.fileName) }}</span>
              <div class="att-video-play-overlay">
                <!-- 保留 SVG：Arco 无等效图标，此处为自定义半透明圆形背景+三角播放按钮，组件无法复现 -->
                <svg class="att-play-icon" viewBox="0 0 24 24" width="28" height="28">
                  <circle cx="12" cy="12" r="11" fill="rgba(0,0,0,0.6)" stroke="rgba(255,255,255,0.8)" stroke-width="1.5" />
                  <polygon points="10,8 10,16 17,12" fill="rgba(255,255,255,0.9)" />
                </svg>
              </div>
            </template>
            <!-- Other file types -->
            <template v-else>
              <span class="att-file-icon">{{ getFileIcon(att.fileName) }}</span>
              <!-- Previewable indicator -->
              <span v-if="isPreviewable(att)" class="att-preview-badge" title="点击预览">
                <icon-eye :size="12" />
              </span>
            </template>
            <div class="att-overlay">
              <button class="att-ov-btn" @click.stop="download(att)" title="下载">
                <icon-download :size="14" />
              </button>
              <button v-if="!readonly" class="att-ov-btn att-ov-btn--danger" @click.stop="confirmDelete(att)" title="删除">
                <icon-delete :size="14" />
              </button>
            </div>
          </div>
          <!-- File info -->
          <div class="att-info">
            <span class="att-fname" :title="att.fileName">{{ att.fileName }}</span>
            <span class="att-fsize">{{ formatSize(att.fileSize) }}</span>
          </div>
          <!-- Private badge -->
          <span v-if="att.isPrivate" class="att-private-badge" :title="att.visibleToGroupNames?.join(', ') || '私有'">
            <icon-lock :size="10" />
          </span>
        </div>
      </div>

      <!-- List View -->
      <div v-else-if="viewMode === 'list' && sortedAttachments.length > 0" class="att-list">
        <div
          v-for="att in sortedAttachments"
          :key="att.id"
          class="att-list-item"
          :class="{ 'att-list-item--private': att.isPrivate }"
          @click="handleClick(att)"
        >
          <span class="att-list-icon">{{ getFileIcon(att.fileName) }}</span>
          <span class="att-list-name" :title="att.fileName">{{ att.fileName }}</span>
          <span v-if="att.isPrivate" class="att-list-private">
            <icon-lock :size="10" />
          </span>
          <span class="att-list-size">{{ formatSize(att.fileSize) }}</span>
          <span class="att-list-date">{{ formatDate(att.createdAt) }}</span>
          <span class="att-list-user">{{ att.uploadedBy }}</span>
          <div class="att-list-actions" @click.stop>
            <button class="att-ov-btn" @click="download(att)" title="下载">
              <icon-download :size="13" />
            </button>
            <button v-if="!readonly" class="att-ov-btn att-ov-btn--danger" @click="confirmDelete(att)" title="删除">
              <icon-delete :size="13" />
            </button>
          </div>
        </div>
      </div>

      <!-- Image Lightbox: Arco ImagePreviewGroup with srcList for multi-image navigation -->
      <a-image-preview-group
        v-model:visible="imagePreviewVisible"
        v-model:current="imagePreviewCurrent"
        infinite
        :src-list="imagePreviewSrcList"
        :actions-layout="['fullScreen', 'zoomIn', 'zoomOut', 'originalSize', 'rotateLeft', 'rotateRight']"
        esc-to-close
        keyboard
        wheel-zoom
        closable
      />

      <!-- Drop zone -->
      <div
        v-if="!readonly"
        class="att-dropzone"
        :class="{ 'att-dropzone--active': isDragOver }"
        @dragover.prevent="onDragOver"
        @dragleave="onDragLeave"
        @drop.prevent="onDrop"
        @click="$emit('upload')"
      >
        <icon-upload class="att-dropzone-icon" />
        <span class="att-dropzone-text">
          将文件拖拽至此上传，或<span class="att-dropzone-link">点击选择</span>
        </span>
        <span class="att-dropzone-hint">支持图片、文档、压缩包等，单文件最大 50MB</span>
      </div>

      <!-- Empty state (readonly) -->
      <p v-if="attachments.length === 0 && readonly" class="att-empty">暂无附件</p>
    </div>

    <!-- Preview Modal -->
    <AttachmentPreviewModal
      v-model:visible="previewVisible"
      :attachment="previewAttachment"
      @download="download"
    />
  </section>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import {
  IconUpload, IconMore, IconDownload, IconDelete, IconLock,
  IconApps, IconList, IconFile, IconSortDescending, IconSortAscending
} from '@arco-design/web-vue/es/icon'
import { useAttachmentThumbnails, useAuthenticatedFile } from '@/composables/useAuthenticatedFile'
import AttachmentPreviewModal from './AttachmentPreviewModal.vue'

export interface AttachmentItem {
  id: string
  fileName: string
  filePath: string
  fileSize: number
  contentType?: string
  createdAt: string
  uploadedBy: string
  isPrivate?: boolean
  visibleToGroupNames?: string[]
}

const props = defineProps<{
  attachments: AttachmentItem[]
  readonly?: boolean
}>()

const emit = defineEmits<{
  'upload': []
  'upload-private': []
  'upload-files': [files: File[]]
  'delete': [id: string]
  'delete-all': []
}>()

// ========== State ==========
const collapsed = ref(false)
const viewMode = ref<'grid' | 'list'>(
  (localStorage.getItem('tf-att-view') as 'grid' | 'list') || 'grid'
)
const sortMode = ref<'date-desc' | 'date-asc' | 'type'>(
  (localStorage.getItem('tf-att-sort') as any) || 'date-desc'
)

// ========== Authenticated File Loading ==========
const { thumbnailMap, loadThumbnails } = useAttachmentThumbnails()
const { downloadFile } = useAuthenticatedFile()

// 当附件列表变化时，加载图片缩略图
watch(() => props.attachments, (atts) => {
  const imageFilePaths = atts
    .filter(a => isImage(a))
    .map(a => a.filePath)
  if (imageFilePaths.length > 0) {
    loadThumbnails(imageFilePaths)
  }
}, { immediate: true })

/**
 * 获取图片的已认证 Blob URL（用于 <img> 标签）
 * 如果尚未加载完成，返回空字符串（不渲染图片）
 */
function getImageSrc(filePath: string): string {
  return thumbnailMap.value[filePath] || ''
}

// ========== Drag & Drop ==========
const isDragOver = ref(false)
let dragLeaveTimer: ReturnType<typeof setTimeout> | null = null

function onDragOver() {
  if (dragLeaveTimer) { clearTimeout(dragLeaveTimer); dragLeaveTimer = null }
  isDragOver.value = true
}
function onDragLeave() {
  dragLeaveTimer = setTimeout(() => { isDragOver.value = false }, 50)
}
function onDrop(e: DragEvent) {
  isDragOver.value = false
  if (dragLeaveTimer) { clearTimeout(dragLeaveTimer); dragLeaveTimer = null }
  const files = e.dataTransfer?.files
  if (files && files.length > 0) emit('upload-files', Array.from(files))
}

// ========== View & Sort ==========
function toggleCollapse() { collapsed.value = !collapsed.value }

function toggleViewMode() {
  viewMode.value = viewMode.value === 'grid' ? 'list' : 'grid'
  localStorage.setItem('tf-att-view', viewMode.value)
}

function sortBy(mode: 'date-desc' | 'date-asc' | 'type') {
  sortMode.value = mode
  localStorage.setItem('tf-att-sort', mode)
}

const sortedAttachments = computed(() => {
  const list = [...props.attachments]
  switch (sortMode.value) {
    case 'date-desc':
      return list.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    case 'date-asc':
      return list.sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
    case 'type':
      return list.sort((a, b) => getExtension(a.fileName).localeCompare(getExtension(b.fileName)))
    default:
      return list
  }
})

// ========== File helpers ==========
const IMAGE_EXTS = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'])
const VIDEO_EXTS = new Set(['mp4', 'webm', 'mov', 'avi', 'mkv'])
const PDF_EXTS = new Set(['pdf'])
const SVG_EXTS = new Set(['svg'])
const TEXT_EXTS = new Set([
  'txt', 'md', 'json', 'xml', 'csv', 'js', 'ts', 'jsx', 'tsx',
  'java', 'py', 'sql', 'html', 'css', 'scss', 'less', 'yaml', 'yml',
  'sh', 'bash', 'bat', 'ps1', 'rb', 'go', 'rs', 'c', 'cpp', 'h',
  'properties', 'ini', 'toml', 'env', 'log', 'conf', 'cfg'
])

function isImage(att: AttachmentItem): boolean {
  if (att.contentType?.startsWith('image/') && !att.contentType.includes('svg')) return true
  return IMAGE_EXTS.has(getExtension(att.fileName))
}

function isVideo(att: AttachmentItem): boolean {
  if (att.contentType?.startsWith('video/')) return true
  return VIDEO_EXTS.has(getExtension(att.fileName))
}

function isPdf(att: AttachmentItem): boolean {
  if (att.contentType === 'application/pdf') return true
  return PDF_EXTS.has(getExtension(att.fileName))
}

function isSvg(att: AttachmentItem): boolean {
  if (att.contentType === 'image/svg+xml') return true
  return SVG_EXTS.has(getExtension(att.fileName))
}

function isText(att: AttachmentItem): boolean {
  if (att.contentType?.startsWith('text/')) return true
  return TEXT_EXTS.has(getExtension(att.fileName))
}

function isPreviewable(att: AttachmentItem): boolean {
  return isVideo(att) || isPdf(att) || isSvg(att) || isText(att)
}

function getExtension(name: string): string {
  const idx = name.lastIndexOf('.')
  return idx > -1 ? name.substring(idx + 1).toLowerCase() : ''
}

function getFileUrl(filePath: string): string {
  return `/api/v1/files/${filePath}`
}

function getFileIcon(name: string): string {
  const ext = getExtension(name)
  const icons: Record<string, string> = {
    pdf: '📄', doc: '📝', docx: '📝', xls: '📊', xlsx: '📊',
    ppt: '📑', pptx: '📑', zip: '📦', rar: '📦', '7z': '📦',
    tar: '📦', gz: '📦', txt: '📃', md: '📃', json: '📃',
    html: '🌐', css: '🎨', js: '⚙️', ts: '⚙️', java: '⚙️',
    py: '🐍', sql: '🗃️', csv: '📊', xml: '📃',
    mp4: '🎬', avi: '🎬', mov: '🎬', mp3: '🎵', wav: '🎵',
    svg: '🖼️', ico: '🖼️',
  }
  return icons[ext] || '📎'
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

function formatDate(dt: string): string {
  if (!dt) return ''
  return new Date(dt).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

// ========== Image Lightbox (multi-image preview) ==========
const imagePreviewVisible = ref(false)
const imagePreviewCurrent = ref(0)

/** All image attachments in current sort order */
const imageAttachments = computed(() => sortedAttachments.value.filter(a => isImage(a)))

/** Only images with loaded blob URLs (for srcList alignment) */
const imageAttachmentsLoaded = computed(() =>
  imageAttachments.value.filter(a => !!getImageSrc(a.filePath))
)

/** srcList for Arco ImagePreviewGroup — authenticated blob URLs */
const imagePreviewSrcList = computed(() =>
  imageAttachmentsLoaded.value.map(a => getImageSrc(a.filePath))
)

function openImagePreview(att: AttachmentItem) {
  const src = getImageSrc(att.filePath)
  if (!src) return
  const idx = imageAttachmentsLoaded.value.findIndex(a => a.id === att.id)
  if (idx < 0) return
  imagePreviewCurrent.value = idx
  imagePreviewVisible.value = true
}

// ========== Actions ==========
const previewVisible = ref(false)
const previewAttachment = ref<AttachmentItem | null>(null)

function handleClick(att: AttachmentItem) {
  if (isImage(att)) {
    openImagePreview(att)
    return
  }
  if (isPreviewable(att)) {
    openPreview(att)
  } else {
    download(att)
  }
}

function openPreview(att: AttachmentItem) {
  previewAttachment.value = att
  previewVisible.value = true
}

function download(att: AttachmentItem) {
  downloadFile(att.filePath, att.fileName).catch(() => {
    // fallback: 如果 blob 下载失败，尝试直接链接（可能也会失败）
    const a = document.createElement('a')
    a.href = getFileUrl(att.filePath)
    a.download = att.fileName
    a.click()
  })
}

function downloadAll() {
  // Download each file individually (batch zip endpoint can be added later)
  for (const att of props.attachments) {
    download(att)
  }
}

function confirmDelete(att: AttachmentItem) {
  const { confirmDelete: showConfirm } = useConfirmDelete()
  showConfirm({
    itemName: `附件「${att.fileName}」`,
    onConfirm: () => emit('delete', att.id)
  })
}

function confirmDeleteAll() {
  const { confirmDangerDelete } = useConfirmDelete()
  confirmDangerDelete({
    itemName: `全部 ${props.attachments.length} 个附件`,
    impactDescription: '删除后无法恢复',
    confirmText: '全部删除',
    onConfirm: () => emit('delete-all')
  })
}
</script>

<style scoped>
.att-section {
  margin-bottom: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--tf-border-light);
}

/* Header */
.att-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
  cursor: pointer;
  user-select: none;
}
.att-collapse-arrow {
  font-size: 10px;
  color: var(--tf-text-muted);
  width: 12px;
  flex-shrink: 0;
}
.att-title {
  font-size: 11px;
  font-weight: 700;
  color: var(--tf-text-tertiary);
  margin: 0;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  flex: 1;
}
.att-header-actions {
  display: flex;
  gap: 4px;
  cursor: default;
}
.att-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: var(--tf-text-tertiary);
  border-radius: 3px;
  cursor: pointer;
  transition: color 150ms, background 150ms;
}
.att-action-btn:hover {
  color: var(--tf-text-primary);
  background: var(--tf-bg-hover);
}

/* Grid view */
.att-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.att-card {
  position: relative;
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  overflow: hidden;
  background: var(--tf-bg-elevated);
  cursor: pointer;
  transition: border-color 150ms, box-shadow 150ms;
}
.att-card:hover {
  border-color: var(--tf-accent);
}
.att-card--private {
  border-color: var(--color-warning-light, #d29922);
}

/* Thumbnail area */
.att-thumb {
  position: relative;
  width: 100%;
  height: 100px;
  overflow: hidden;
  background: var(--tf-bg-surface);
  display: flex;
  align-items: center;
  justify-content: center;
}
.att-thumb :deep(.arco-image) {
  width: 100%;
  height: 100%;
}
.att-thumb :deep(.arco-image img) {
  object-fit: cover;
  width: 100%;
  height: 100%;
}
.att-thumb-img {
  object-fit: cover;
  width: 100%;
  height: 100%;
  display: block;
}
.att-thumb--file {
  background: var(--tf-bg-surface);
}
.att-thumb-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  background: var(--tf-bg-surface);
}
.att-file-icon {
  font-size: 32px;
  line-height: 1;
}

/* Hover overlay */
.att-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: var(--tf-overlay);
  opacity: 0;
  transition: opacity 150ms;
  pointer-events: none;
}
.att-card:hover .att-overlay,
.att-list-item:hover .att-list-actions {
  opacity: 1;
}
.att-ov-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 4px;
  border: none;
  background: var(--tf-fill-medium);
  color: var(--tf-text-on-accent);
  cursor: pointer;
  transition: background 150ms;
  pointer-events: auto;
}
.att-ov-btn:hover {
  background: var(--tf-fill-heavy);
}
.att-ov-btn--danger:hover {
  background: var(--tf-danger-strong);
}

/* File info under thumbnail */
.att-info {
  padding: 6px 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.att-fname {
  font-size: 11px;
  color: var(--tf-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.att-fsize {
  font-size: 10px;
  color: var(--tf-text-muted);
}
.att-private-badge {
  position: absolute;
  top: 4px;
  right: 4px;
  color: var(--color-warning-light, #d29922);
  background: var(--tf-bg-elevated);
  border-radius: 50%;
  padding: 2px;
  line-height: 1;
}

/* List view */
.att-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 12px;
}
.att-list-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 3px;
  cursor: pointer;
  transition: background 150ms;
  position: relative;
}
.att-list-item:hover {
  background: var(--tf-bg-hover);
}
.att-list-item--private {
  border-left: 2px solid var(--color-warning-light, #d29922);
  padding-left: 6px;
}
.att-list-icon {
  font-size: 14px;
  flex-shrink: 0;
  width: 20px;
  text-align: center;
}
.att-list-name {
  font-size: 12px;
  color: var(--tf-text-secondary);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.att-list-private {
  color: var(--color-warning-light, #d29922);
  flex-shrink: 0;
}
.att-list-size {
  font-size: 11px;
  color: var(--tf-text-muted);
  flex-shrink: 0;
  width: 60px;
  text-align: right;
}
.att-list-date {
  font-size: 11px;
  color: var(--tf-text-muted);
  flex-shrink: 0;
  width: 50px;
}
.att-list-user {
  font-size: 11px;
  color: var(--tf-text-muted);
  flex-shrink: 0;
  width: 60px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.att-list-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 150ms;
  flex-shrink: 0;
}

/* Dropzone */
.att-dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 16px;
  border: 2px dashed var(--tf-border);
  border-radius: 6px;
  background: var(--tf-bg-surface);
  cursor: pointer;
  transition: border-color 200ms, background 200ms;
  text-align: center;
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
.att-dropzone-icon {
  font-size: 20px;
  color: var(--tf-text-muted);
  transition: color 200ms;
}
.att-dropzone:hover .att-dropzone-icon,
.att-dropzone--active .att-dropzone-icon {
  color: var(--tf-accent);
}
.att-dropzone-text {
  font-size: 12px;
  color: var(--tf-text-secondary);
}
.att-dropzone-link {
  color: var(--tf-accent);
}
.att-dropzone-hint {
  font-size: 11px;
  color: var(--tf-text-muted);
}

/* Empty */
.att-empty {
  font-size: 11px;
  color: var(--tf-text-muted);
  margin: 0;
}

/* Danger option in dropdown */
.danger-opt :deep(.arco-dropdown-option-content) { color: var(--tf-danger); }
.danger-opt :deep(.arco-icon) { color: var(--tf-danger); }

/* Video play overlay in grid */
.att-video-play-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}
.att-play-icon {
  filter: drop-shadow(0 1px 3px rgba(0, 0, 0, 0.3));
  transition: transform 150ms;
}
.att-card:hover .att-play-icon {
  transform: scale(1.1);
}

/* Previewable badge (eye icon) */
.att-preview-badge {
  position: absolute;
  bottom: 6px;
  right: 6px;
  color: var(--tf-text-muted);
  opacity: 0.6;
  transition: opacity 150ms;
}
.att-card:hover .att-preview-badge {
  opacity: 1;
  color: var(--tf-accent);
}
</style>
