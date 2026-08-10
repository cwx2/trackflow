<template>
  <a-modal
    v-model:visible="modalVisible"
    :title="attachment?.fileName || '文件预览'"
    :width="modalWidth"
    :footer="false"
    class="att-preview-modal"
    :modal-style="{ maxWidth: '90vw' }"
    unmount-on-close
    @close="handleClose"
  >
    <div class="att-preview-body">
      <!-- Loading -->
      <div v-if="loading" class="att-preview-loading">
        <a-spin :size="32" />
        <span class="att-preview-loading-text">加载中…</span>
      </div>

      <!-- Error -->
      <div v-else-if="error" class="att-preview-error">
        <icon-close-circle :size="32" />
        <span>{{ error }}</span>
        <a-button size="small" @click="retryLoad">重试</a-button>
      </div>

      <!-- Video preview -->
      <video
        v-else-if="previewType === 'video'"
        ref="videoRef"
        :src="blobUrl!"
        controls
        autoplay
        class="att-preview-video"
      />

      <!-- PDF preview -->
      <iframe
        v-else-if="previewType === 'pdf'"
        :src="blobUrl!"
        class="att-preview-pdf"
      />

      <!-- SVG preview -->
      <img
        v-else-if="previewType === 'svg'"
        :src="blobUrl!"
        :alt="attachment?.fileName"
        class="att-preview-svg"
      />

      <!-- Text/Code preview -->
      <div v-else-if="previewType === 'text'" class="att-preview-text-wrapper">
        <pre class="att-preview-code"><code>{{ textContent }}</code></pre>
      </div>
    </div>

    <!-- Footer with download button -->
    <div class="att-preview-footer">
      <span class="att-preview-meta">
        {{ attachment?.fileName }} · {{ formatSize(attachment?.fileSize || 0) }}
      </span>
      <a-button type="primary" size="small" @click="handleDownload">
        <template #icon><icon-download /></template>
        下载
      </a-button>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { IconDownload, IconCloseCircle } from '@arco-design/web-vue/es/icon'
import { useAuthenticatedFile } from '@/composables/useAuthenticatedFile'
import type { AttachmentItem } from './AttachmentSection.vue'

const props = defineProps<{
  visible: boolean
  attachment: AttachmentItem | null
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  'download': [att: AttachmentItem]
}>()

const { getAuthenticatedBlobUrl } = useAuthenticatedFile()

// ========== State ==========
const modalVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const loading = ref(false)
const error = ref('')
const blobUrl = ref<string | null>(null)
const textContent = ref('')
const videoRef = ref<HTMLVideoElement | null>(null)

// ========== File Type Detection ==========
const VIDEO_EXTS = new Set(['mp4', 'webm', 'mov', 'avi', 'mkv'])
const PDF_EXTS = new Set(['pdf'])
const SVG_EXTS = new Set(['svg'])
const TEXT_EXTS = new Set([
  'txt', 'md', 'json', 'xml', 'csv', 'js', 'ts', 'jsx', 'tsx',
  'java', 'py', 'sql', 'html', 'css', 'scss', 'less', 'yaml', 'yml',
  'sh', 'bash', 'bat', 'ps1', 'rb', 'go', 'rs', 'c', 'cpp', 'h',
  'properties', 'ini', 'toml', 'env', 'log', 'conf', 'cfg'
])

// Max text file size for preview (1MB)
const MAX_TEXT_SIZE = 1 * 1024 * 1024

function getExtension(name: string): string {
  const idx = name.lastIndexOf('.')
  return idx > -1 ? name.substring(idx + 1).toLowerCase() : ''
}

const previewType = computed<'video' | 'pdf' | 'svg' | 'text' | null>(() => {
  if (!props.attachment) return null
  const ext = getExtension(props.attachment.fileName)
  if (VIDEO_EXTS.has(ext)) return 'video'
  if (PDF_EXTS.has(ext)) return 'pdf'
  if (SVG_EXTS.has(ext)) return 'svg'
  if (TEXT_EXTS.has(ext)) return 'text'
  return null
})

const modalWidth = computed(() => {
  switch (previewType.value) {
    case 'video': return 'min(900px, 85vw)'
    case 'pdf': return 900
    case 'svg': return 640
    case 'text': return 720
    default: return 640
  }
})

// ========== Loading ==========
watch(() => [props.visible, props.attachment], ([visible, att]) => {
  if (visible && att) {
    loadPreview()
  } else {
    cleanup()
  }
}, { immediate: true })

async function loadPreview() {
  if (!props.attachment) return

  loading.value = true
  error.value = ''
  textContent.value = ''

  try {
    if (previewType.value === 'text') {
      // Text files: fetch as text content
      if (props.attachment.fileSize > MAX_TEXT_SIZE) {
        error.value = '文件过大，无法预览（超过 1MB）。请下载后查看。'
        return
      }
      const content = await fetchTextContent(props.attachment.filePath)
      if (content !== null) {
        textContent.value = content
      } else {
        error.value = '无法加载文件内容'
      }
    } else {
      // Video/PDF/SVG: fetch as blob URL
      const url = await getAuthenticatedBlobUrl(props.attachment.filePath)
      if (url) {
        blobUrl.value = url
      } else {
        error.value = '无法加载文件'
      }
    }
  } catch (e) {
    error.value = '加载失败，请重试'
    console.error('[AttachmentPreview] Load failed:', e)
  } finally {
    loading.value = false
  }
}

async function fetchTextContent(filePath: string): Promise<string | null> {
  const token = localStorage.getItem('tf_access_token')
  if (!token) return null

  try {
    const resp = await fetch(`/api/v1/files/${filePath}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    if (!resp.ok) return null
    return await resp.text()
  } catch {
    return null
  }
}

function retryLoad() {
  loadPreview()
}

// ========== Actions ==========
function handleDownload() {
  if (props.attachment) {
    emit('download', props.attachment)
  }
}

function handleClose() {
  cleanup()
}

function cleanup() {
  if (blobUrl.value) {
    URL.revokeObjectURL(blobUrl.value)
    blobUrl.value = null
  }
  textContent.value = ''
  error.value = ''
  loading.value = false
}

// ========== Helpers ==========
function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

onUnmounted(() => {
  cleanup()
})
</script>

<style scoped>
.att-preview-modal :deep(.arco-modal-body) {
  padding: 0;
}

.att-preview-body {
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Loading */
.att-preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px;
}
.att-preview-loading-text {
  font-size: 13px;
  color: var(--tf-text-muted);
}

/* Error */
.att-preview-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px;
  color: var(--tf-text-muted);
}
.att-preview-error :deep(.arco-icon) {
  color: var(--color-danger-light, #f85149);
}

/* Video */
.att-preview-video {
  width: 100%;
  max-height: 80vh;
  background: #000;
  display: block;
}

/* PDF */
.att-preview-pdf {
  width: 100%;
  height: 75vh;
  border: none;
  display: block;
}

/* SVG */
.att-preview-svg {
  max-width: 100%;
  max-height: 70vh;
  padding: 24px;
  display: block;
  margin: 0 auto;
  background: var(--tf-bg-surface);
}

/* Text/Code */
.att-preview-text-wrapper {
  width: 100%;
  max-height: 70vh;
  overflow: auto;
  background: var(--tf-bg-surface);
}
.att-preview-code {
  margin: 0;
  padding: 16px 20px;
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.6;
  color: var(--tf-text-primary);
  white-space: pre-wrap;
  word-break: break-all;
  tab-size: 4;
}

/* Footer */
.att-preview-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-top: 1px solid var(--tf-border-light);
  background: var(--tf-bg-elevated);
}
.att-preview-meta {
  font-size: 12px;
  color: var(--tf-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 60%;
}
</style>
