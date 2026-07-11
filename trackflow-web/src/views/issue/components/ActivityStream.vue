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
      <div v-for="item in sorted" :key="item.id" class="stream-item">
        <div class="avatar" :style="{ background: avatarBg(item.user) }">
          {{ initial(item.user) }}
        </div>
        <div class="item-body">
          <div class="item-head">
            <strong>{{ item.user }}</strong>
            <span class="item-time">{{ item.timeAgo }}</span>
          </div>
          <!-- 评论 -->
          <div v-if="item.type === 'comment'" class="comment-text" :class="{ collapsed: !expandComments }" v-html="item.html"></div>
          <div v-else class="change-text">
            <template v-if="item.action === 'created'">创建了该 Issue</template>
            <template v-else-if="item.field">
              {{ item.field }}:
              <span class="val-old">{{ item.from || '空' }}</span>
              →
              <span class="val-new">{{ item.to || '空' }}</span>
            </template>
            <template v-else>{{ item.action }}</template>
          </div>
        </div>
      </div>
      <p v-if="sorted.length === 0" class="empty">暂无活动</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

export interface ActivityItem {
  id: string
  type: 'comment' | 'change'
  user: string
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
}>()

const filters = [
  { key: 'all', label: '全部' },
  { key: 'comments', label: '评论' },
  { key: 'changes', label: '变更' }
]
const current = ref('all')
const ascending = ref(true)
const showSettings = ref(false)
const expandComments = ref(true)

const filtered = computed(() => {
  if (current.value === 'comments') return props.items.filter(i => i.type === 'comment')
  if (current.value === 'changes') return props.items.filter(i => i.type === 'change')
  return props.items
})

const sorted = computed(() => {
  const arr = [...filtered.value]
  arr.sort((a, b) => ascending.value ? a.ts - b.ts : b.ts - a.ts)
  return arr
})

function initial(name: string) { return name ? name[0].toUpperCase() : 'U' }
function avatarBg(name: string) {
  const c = ['#5c6bc0','#26a69a','#ef5350','#ab47bc','#42a5f5','#ff7043','#66bb6a']
  return c[(name || '').charCodeAt(0) % c.length]
}
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

.change-text { font-size: 12px; color: var(--tf-text-tertiary); margin-top: 4px; }
.val-old { text-decoration: line-through; color: var(--tf-text-muted); }
.val-new { color: var(--tf-accent); font-weight: 500; }

.empty { color: var(--tf-text-muted); font-size: 12px; text-align: center; padding: 24px 0; }
</style>
