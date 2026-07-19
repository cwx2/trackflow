<template>
  <section class="activity-stream">
    <div class="stream-toolbar">
      <h3 class="stream-title">活动</h3>
      <div class="filter-group">
        <button
          v-for="f in filters"
          :key="f.key"
          :class="['filter-btn', { active: current === f.key }]"
          @click="current = f.key"
        >{{ f.label }}</button>
      </div>
      <div class="settings-dropdown-wrap">
        <button class="settings-btn" @click="showSettings = !showSettings">
          活动的设置 ▾
        </button>
        <div class="settings-dropdown" v-if="showSettings" @mouseleave="showSettings = false">
          <div class="dropdown-item" :class="{ checked: ascending }" @click="ascending = true; showSettings = false">
            <span class="check-mark">{{ ascending ? '✓' : '' }}</span>
            排序: 旧→新
          </div>
          <div class="dropdown-item" :class="{ checked: !ascending }" @click="ascending = false; showSettings = false">
            <span class="check-mark">{{ !ascending ? '✓' : '' }}</span>
            排序: 新→旧
          </div>
          <div class="dropdown-divider"></div>
          <div class="dropdown-item" :class="{ checked: expandComments }" @click="expandComments = !expandComments">
            <span class="check-mark">{{ expandComments ? '✓' : '' }}</span>
            展开注释
          </div>
        </div>
      </div>
    </div>

    <div class="stream-list">
      <div v-for="item in sorted" :key="item.id" class="stream-item" @mouseenter="hoveredId = item.id" @mouseleave="hoveredId = ''">
        <div class="avatar" :style="{ background: avatarBg(item.user) }">
          {{ initial(item.user) }}
        </div>
        <div class="item-body">
          <div class="item-head">
            <strong>{{ item.user }}</strong>
            <span class="item-time">{{ item.timeAgo }}</span>
            <span v-if="item.type === 'comment' && item.isEdited" class="edited-badge">已编辑</span>
            <!-- Comment actions -->
            <div v-if="item.type === 'comment' && canModifyComment(item) && hoveredId === item.id && editingCommentId !== item.commentId" class="comment-actions">
              <button class="action-btn" title="编辑评论" @click="startEdit(item)">✎</button>
              <button class="action-btn action-btn-danger" title="删除评论" @click="confirmDelete(item)">✕</button>
            </div>
          </div>
          <!-- Editing mode -->
          <div v-if="item.type === 'comment' && editingCommentId === item.commentId" class="comment-edit">
            <div class="edit-area">
              <EditorContent :editor="editEditor" />
            </div>
            <div class="edit-actions">
              <button class="btn-cancel" @click="cancelEdit">取消</button>
              <button class="btn-save" :disabled="editEmpty" @click="saveEdit">保存修改</button>
            </div>
          </div>
          <!-- Normal display -->
          <template v-else>
            <!-- 评论 -->
            <div v-if="item.type === 'comment'" class="comment-text" :class="{ collapsed: !expandComments }" v-html="item.html"></div>
            <div v-else class="change-text">
              <template v-if="item.action === 'created'">创建了此工单</template>
              <template v-else-if="item.action === 'deleted'">删除了此工单</template>
              <template v-else-if="item.action === 'restored'">恢复了此工单</template>
              <template v-else-if="item.action === 'action_rule_executed'">
                <span class="system-event-badge">⚡</span> 规则「<span class="val-new">{{ item.to }}</span>」自动执行
              </template>
              <template v-else-if="item.action === 'time_logged'">
                <span class="time-badge">⏱</span> 记录了工时: <span class="val-new">{{ item.to }}</span>
              </template>
              <template v-else-if="item.action === 'time_removed'">
                <span class="time-badge">⏱</span> 删除了工时: <span class="val-old">{{ item.from }}</span>
              </template>
              <template v-else-if="item.action === 'time_updated'">
                <span class="time-badge">⏱</span> 修改了工时: <span class="val-new">{{ item.to }}</span>
              </template>
              <template v-else-if="item.action === 'attachment_added'">
                添加了附件: <span class="val-new">{{ item.to }}</span>
              </template>
              <template v-else-if="item.action === 'attachment_removed'">
                删除了附件: <span class="val-old">{{ item.from }}</span>
              </template>
              <template v-else-if="item.action === 'moved_to_project'">
                移动到项目：<span class="val-old">{{ item.from || '未知' }}</span> → <span class="val-new">{{ item.to || '未知' }}</span>
              </template>
              <template v-else-if="item.action === 'link_added'">
                添加了关联: <span class="val-new">{{ item.to }}</span>
              </template>
              <template v-else-if="item.action === 'link_removed'">
                移除了关联: <span class="val-old">{{ item.from }}</span>
              </template>
              <template v-else-if="item.field">
                修改了{{ item.field }}：<span class="val-old">{{ item.from || '未设置' }}</span> → <span class="val-new">{{ item.to || '未设置' }}</span>
              </template>
              <template v-else>{{ localizeAction(item.action) }}</template>
            </div>
          </template>
        </div>
      </div>
      <p v-if="sorted.length === 0" class="empty">暂无活动</p>
    </div>

    <!-- Delete confirmation modal -->
    <a-modal
      v-model:visible="deleteModalVisible"
      title="删除评论"
      :ok-text="'删除评论'"
      :cancel-text="'取消'"
      :ok-button-props="{ status: 'danger' }"
      @ok="doDelete"
      @cancel="deleteModalVisible = false"
    >
      <p>确定要删除这条评论吗？此操作无法撤销。</p>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onBeforeUnmount, nextTick } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import Placeholder from '@tiptap/extension-placeholder'
import { localizeAction } from '@/utils/fieldLabels'

export interface ActivityItem {
  id: string
  type: 'comment' | 'change'
  user: string
  userId?: string
  commentId?: string
  isEdited?: boolean
  rawContent?: string
  timeAgo: string
  html?: string
  action?: string
  field?: string
  from?: string
  to?: string
  ts: number
}

const props = defineProps<{
  items: ActivityItem[]
  currentUserId?: string
  canManageComments?: boolean
}>()

const emit = defineEmits<{
  editComment: [commentId: string, content: string]
  deleteComment: [commentId: string]
}>()

const filters = [
  { key: 'all', label: '全部' },
  { key: 'comments', label: '评论' },
  { key: 'time', label: '花费时间' },
  { key: 'changes', label: '变更' }
]
const current = ref('all')
const ascending = ref(true)
const showSettings = ref(false)
const expandComments = ref(true)
const hoveredId = ref('')

// Edit state
const editingCommentId = ref<string | null>(null)

const editEditor = useEditor({
  content: '',
  extensions: [
    StarterKit,
    Link.configure({ openOnClick: false }),
    Placeholder.configure({ placeholder: '编辑评论...' }),
  ],
  editorProps: { attributes: { class: 'tiptap-comment' } },
})

const editEmpty = computed(() => !editEditor.value || editEditor.value.isEmpty)

// Delete state
const deleteModalVisible = ref(false)
const deletingCommentId = ref<string | null>(null)

const filtered = computed(() => {
  if (current.value === 'comments') return props.items.filter(i => i.type === 'comment')
  if (current.value === 'time') return props.items.filter(i => i.action === 'time_logged' || i.action === 'time_removed' || i.action === 'time_updated')
  if (current.value === 'changes') return props.items.filter(i => i.type === 'change' && i.action !== 'time_logged' && i.action !== 'time_removed' && i.action !== 'time_updated')
  return props.items
})

const sorted = computed(() => {
  const arr = [...filtered.value]
  arr.sort((a, b) => ascending.value ? a.ts - b.ts : b.ts - a.ts)
  return arr
})

function canModifyComment(item: ActivityItem): boolean {
  if (!props.currentUserId) return false
  // Author can always edit/delete their own comment
  if (item.userId === props.currentUserId) return true
  // Users with manage_comments permission can edit/delete anyone's
  return !!props.canManageComments
}

function startEdit(item: ActivityItem) {
  editingCommentId.value = item.commentId || null
  nextTick(() => {
    if (editEditor.value && item.rawContent) {
      // If rawContent starts with '<', it's HTML; otherwise set as paragraph
      const isHtml = item.rawContent.trim().startsWith('<')
      if (isHtml) {
        editEditor.value.commands.setContent(item.rawContent)
      } else {
        editEditor.value.commands.setContent(`<p>${item.rawContent}</p>`)
      }
      editEditor.value.commands.focus('end')
    }
  })
}

function cancelEdit() {
  editingCommentId.value = null
  editEditor.value?.commands.clearContent()
}

function saveEdit() {
  if (!editingCommentId.value || !editEditor.value || editEditor.value.isEmpty) return
  const html = editEditor.value.getHTML()
  emit('editComment', editingCommentId.value, html)
  editingCommentId.value = null
  editEditor.value.commands.clearContent()
}

function confirmDelete(item: ActivityItem) {
  deletingCommentId.value = item.commentId || null
  deleteModalVisible.value = true
}

function doDelete() {
  if (deletingCommentId.value) {
    emit('deleteComment', deletingCommentId.value)
  }
  deleteModalVisible.value = false
  deletingCommentId.value = null
}

function initial(name: string) { return name ? name[0].toUpperCase() : 'U' }
function avatarBg(name: string) {
  const c = ['#5c6bc0','#26a69a','#ef5350','#ab47bc','#42a5f5','#ff7043','#66bb6a']
  return c[(name || '').charCodeAt(0) % c.length]
}

onBeforeUnmount(() => { editEditor.value?.destroy() })
</script>

<style scoped>
.activity-stream {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--tf-border-light);
}

.stream-toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.stream-title { font-size: 11px; font-weight: 700; color: var(--tf-text-tertiary); margin: 0; text-transform: uppercase; letter-spacing: 0.5px; }
.filter-group { display: flex; gap: 4px; }
.filter-btn {
  font-size: 11px; padding: 2px 8px; border-radius: 3px;
  background: none; border: 1px solid transparent;
  color: var(--tf-text-tertiary); cursor: pointer;
  transition: color 150ms, background 150ms, border-color 150ms;
}
.filter-btn:hover { color: var(--tf-text-primary); }
.filter-btn.active { background: var(--tf-bg-elevated); color: var(--tf-text-primary); border-color: var(--tf-border); }

.settings-dropdown-wrap { margin-left: auto; position: relative; }
.settings-btn {
  font-size: 11px; background: none; border: none;
  color: var(--tf-accent); cursor: pointer;
  transition: opacity 150ms;
}
.settings-btn:hover { opacity: 0.8; }

.settings-dropdown {
  position: absolute; top: 100%; right: 0; z-index: 20;
  margin-top: 4px; min-width: 160px;
  background: var(--tf-bg-elevated); border: 1px solid var(--tf-border);
  border-radius: 6px; padding: 4px 0;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
}
.dropdown-item {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; font-size: 13px;
  color: var(--tf-text-primary); cursor: pointer;
  transition: background 150ms;
}
.dropdown-item:hover { background: var(--tf-bg-hover); }
.dropdown-item.checked { font-weight: 500; }
.check-mark { width: 16px; font-size: 12px; color: var(--tf-accent); }
.dropdown-divider { height: 1px; background: var(--tf-border-light); margin: 4px 0; }

.stream-item { display: flex; gap: 8px; padding: 12px 0; }
.stream-item + .stream-item { border-top: 1px solid var(--tf-border-light); }
.avatar {
  width: 28px; height: 28px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; color: #fff; font-weight: 600;
}
.item-body { flex: 1; min-width: 0; }
.item-head { display: flex; align-items: baseline; gap: 8px; }
.item-head strong { font-size: 12px; color: var(--tf-text-primary); font-weight: 600; }
.item-time { font-size: 11px; color: var(--tf-text-muted); }

.edited-badge {
  font-size: 10px;
  color: var(--tf-text-muted);
  font-style: italic;
}

.comment-actions {
  display: flex; gap: 4px; margin-left: auto;
}
.action-btn {
  font-size: 12px; padding: 2px 6px; border-radius: 3px;
  background: none; border: none; color: var(--tf-text-tertiary);
  cursor: pointer; transition: color 150ms, background 150ms;
}
.action-btn:hover { color: var(--tf-text-primary); background: var(--tf-bg-hover); }
.action-btn-danger:hover { color: var(--tf-error); background: rgba(248, 81, 73, 0.1); }

.comment-text { font-size: 13px; color: var(--tf-text-secondary); line-height: 1.5; margin-top: 4px; }
.comment-text.collapsed { max-height: 60px; overflow: hidden; position: relative; }
.comment-text.collapsed::after {
  content: '';
  position: absolute; bottom: 0; left: 0; right: 0; height: 24px;
  background: linear-gradient(transparent, var(--tf-bg-body));
  pointer-events: none;
}
.comment-text :deep(p) { margin: 4px 0; }
.comment-text :deep(code) { background: var(--tf-bg-code); padding: 0 3px; border-radius: 2px; font-size: 11px; }

/* Edit mode */
.comment-edit {
  margin-top: 8px;
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  overflow: hidden;
  background: var(--tf-bg-body);
}
.edit-area {
  min-height: 60px; max-height: 200px; overflow-y: auto; padding: 12px;
}
.edit-area :deep(.tiptap-comment) {
  outline: none; font-size: 13px; line-height: 1.5; color: var(--tf-text-primary);
}
.edit-area :deep(.tiptap-comment p) { margin: 4px 0; }
.edit-area :deep(.tiptap-comment code) { background: var(--tf-bg-code); padding: 0 3px; border-radius: 2px; font-size: 12px; }
.edit-area :deep(.tiptap-comment .is-empty::before) {
  content: attr(data-placeholder); color: var(--tf-text-muted);
  pointer-events: none; float: left; height: 0;
}
.edit-actions {
  display: flex; justify-content: flex-end; gap: 8px;
  padding: 8px; border-top: 1px solid var(--tf-border);
  background: var(--tf-bg-elevated);
}
.btn-cancel {
  font-size: 12px; padding: 4px 12px; border-radius: 3px; border: none;
  background: none; color: var(--tf-text-tertiary); cursor: pointer;
  transition: color 150ms;
}
.btn-cancel:hover { color: var(--tf-text-primary); }
.btn-save {
  font-size: 12px; padding: 4px 16px; border-radius: 3px; border: none;
  background: var(--tf-accent); color: #fff; font-weight: 500; cursor: pointer;
  transition: background 150ms;
}
.btn-save:disabled { opacity: 0.35; cursor: default; }
.btn-save:hover:not(:disabled) { background: var(--tf-accent-hover); }

.change-text { font-size: 12px; color: var(--tf-text-tertiary); margin-top: 4px; }
.val-old { text-decoration: line-through; color: var(--tf-text-muted); }
.val-new { color: var(--tf-accent); font-weight: 500; }
.time-badge { font-size: 13px; }
.system-event-badge { font-size: 13px; }

.empty { color: var(--tf-text-muted); font-size: 12px; text-align: center; padding: 24px 0; }
</style>
