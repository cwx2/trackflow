<template>
  <div class="bottom-toolbar-wrap" ref="wrapRef">
    <!-- 节点选择弹层（在工具栏内部管理，从工具栏正上方弹出） -->
    <Transition name="node-popover">
      <div
        v-if="addNodePanelOpen"
        class="add-node-popover"
        @mousedown.stop
      >
        <!-- 搜索框 -->
        <div class="node-search-wrap">
          <a-input
            v-model="nodeSearchKeyword"
            placeholder="搜索节点、插件、工作流"
            size="small"
            allow-clear
            class="node-search-input"
          >
            <template #prefix><span class="search-icon">🔍</span></template>
          </a-input>
        </div>

        <!-- 节点列表（搜索 or 分类） -->
        <div class="panel-scroll">
          <template v-if="nodeSearchKeyword">
            <div class="panel-section">
              <div v-if="filteredNodes.length === 0" class="no-search-result">无匹配节点</div>
              <div class="node-grid">
                <div
                  v-for="node in filteredNodes"
                  :key="node.type"
                  class="node-grid-item"
                  :style="{ '--node-color': node.color }"
                  @mousedown="(e) => onNodeMouseDown(e, node)"
                >
                  <div class="node-grid-icon">{{ node.icon }}</div>
                  <div class="node-grid-name">{{ node.label }}</div>
                </div>
              </div>
            </div>
          </template>
          <template v-else>
            <div
              v-for="category in nodeCategories"
              :key="category.name"
              class="panel-section"
            >
              <div class="section-title">{{ category.name }}</div>
              <div class="node-grid">
                <div
                  v-for="node in category.nodes"
                  :key="node.type"
                  class="node-grid-item"
                  :style="{ '--node-color': node.color }"
                  @mousedown="(e) => onNodeMouseDown(e, node)"
                >
                  <div class="node-grid-icon">{{ node.icon }}</div>
                  <div class="node-grid-name">{{ node.label }}</div>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </Transition>

    <!-- 工具栏主体 -->
    <div class="bottom-toolbar">
      <!-- 1. 缩放下拉 -->
      <div class="toolbar-zoom" @click="zoomMenuOpen = !zoomMenuOpen" ref="zoomBtnRef">
        <span class="toolbar-zoom-val">{{ zoomPercent }}%</span>
        <span class="toolbar-arrow">∨</span>
        <!-- 下拉菜单 -->
        <div v-if="zoomMenuOpen" class="zoom-dropdown" @click.stop>
          <button class="zoom-menu-item" @click="emit('zoom-out'); zoomMenuOpen=false">缩小</button>
          <button class="zoom-menu-item" @click="emit('zoom-in'); zoomMenuOpen=false">放大</button>
          <button class="zoom-menu-item" @click="emit('fit'); zoomMenuOpen=false">自适应</button>
          <div class="zoom-menu-divider" />
          <button
            v-for="p in [50,75,100,125,150,200]"
            :key="p"
            class="zoom-menu-item"
            :class="{ active: zoomPercent === p }"
            @click="emit('zoom-to', p); zoomMenuOpen=false"
          >
            缩放到 {{ p }}%
          </button>
        </div>
      </div>

      <!-- 分隔线 -->
      <div class="toolbar-divider" />

      <!-- 2. 注释 -->
      <a-tooltip content="注释" position="top" mini>
        <button class="toolbar-icon-btn" @click="emit('add-comment')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
        </button>
      </a-tooltip>

      <!-- 3. 优化布局 -->
      <a-tooltip content="优化布局" position="top" mini>
        <button class="toolbar-icon-btn" @click="emit('auto-layout')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/>
            <rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/>
          </svg>
        </button>
      </a-tooltip>

      <!-- 4. 导出为图片 -->
      <a-tooltip content="导出为图片" position="top" mini>
        <button class="toolbar-icon-btn" @click="emit('export-image')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/>
            <polyline points="21 15 16 10 5 21"/>
          </svg>
        </button>
      </a-tooltip>

      <!-- 5. 缩略图 -->
      <a-tooltip content="缩略图" position="top" mini>
        <button class="toolbar-icon-btn" :class="{ active: minimapOpen }" @click="emit('toggle-minimap')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="2" y="7" width="20" height="15" rx="2"/>
            <path d="M16 2l4 5H4l4-5z" fill="currentColor" stroke="none" opacity="0.4"/>
            <rect x="5" y="10" width="6" height="5" rx="1" opacity="0.6"/>
          </svg>
        </button>
      </a-tooltip>

      <!-- 分隔线 -->
      <div class="toolbar-divider" />

      <!-- 6. + 添加节点 -->
      <button
        class="toolbar-add-btn"
        :class="{ active: addNodePanelOpen }"
        @click="toggleAddNodePanel"
      >
        <span style="font-size:14px;line-height:1;">+</span>
        <span>添加节点</span>
      </button>

      <!-- 分隔线 -->
      <div class="toolbar-divider" />

      <!-- 7. 调试 -->
      <a-tooltip content="调试" position="top" mini>
        <button class="toolbar-icon-btn" :class="{ active: debugMode }" @click="emit('toggle-debug')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z"/>
            <line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
        </button>
      </a-tooltip>

      <!-- 8. 试运行 -->
      <button
        class="toolbar-run-btn"
        :class="{ running: isRunning }"
        :aria-label="isRunning ? '取消当前执行' : '试运行工作流'"
        @click="onRunButtonClick"
      >
        <span class="run-icon">{{ isRunning ? '■' : '▶' }}</span>
        <span>{{ isRunning ? '停止运行' : '试运行' }}</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'

interface NodeItem {
  type: string
  label: string
  icon: string
  color: string
  desc: string
  category: string
}

interface NodeCategory {
  name: string
  nodes: NodeItem[]
}

const props = defineProps<{
  zoomPercent: number
  minimapOpen: boolean
  debugMode: boolean
  isRunning: boolean
  nodeCategories: NodeCategory[]
  allNodes: NodeItem[]
}>()

const emit = defineEmits<{
  'zoom-in': []
  'zoom-out': []
  'fit': []
  'zoom-to': [percent: number]
  'add-comment': []
  'auto-layout': []
  'export-image': []
  'toggle-minimap': []
  'toggle-debug': []
  'run': []
  'cancel': []
  'drag-start': [event: MouseEvent, node: NodeItem]
}>()

// 弹层状态（自包含）
const addNodePanelOpen = ref(false)
const nodeSearchKeyword = ref('')
const zoomMenuOpen = ref(false)
const zoomBtnRef = ref<HTMLElement | null>(null)
const wrapRef = ref<HTMLElement | null>(null)

const filteredNodes = computed(() => {
  const kw = nodeSearchKeyword.value.trim().toLowerCase()
  if (!kw) return props.allNodes
  return props.allNodes.filter(n =>
    n.label.toLowerCase().includes(kw) ||
    n.desc.toLowerCase().includes(kw) ||
    n.category.toLowerCase().includes(kw)
  )
})

function toggleAddNodePanel() {
  addNodePanelOpen.value = !addNodePanelOpen.value
  if (addNodePanelOpen.value) {
    nodeSearchKeyword.value = ''
  }
}

function onNodeMouseDown(e: MouseEvent, node: NodeItem) {
  addNodePanelOpen.value = false
  emit('drag-start', e, node)
}

// 点击外部关闭弹层
function handleOutsideClick(e: MouseEvent) {
  const wrap = wrapRef.value
  if (wrap && !wrap.contains(e.target as Node)) {
    addNodePanelOpen.value = false
  }
  // 缩放菜单
  if (zoomBtnRef.value && !zoomBtnRef.value.contains(e.target as Node)) {
    zoomMenuOpen.value = false
  }
}

function onRunButtonClick() {
  if (props.isRunning) emit('cancel')
  else emit('run')
}

onMounted(() => {
  document.addEventListener('mousedown', handleOutsideClick)
})

onUnmounted(() => {
  document.removeEventListener('mousedown', handleOutsideClick)
})
</script>

<style scoped>
/* 外层包裹，让弹层相对于工具栏定位 */
.bottom-toolbar-wrap {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 20;
  display: flex;
  flex-direction: column;
  align-items: center;
  pointer-events: none;
}

/* ── 工具条容器 ── */
.bottom-toolbar {
  display: flex;
  align-items: center;
  gap: 2px;
  background: var(--wf-toolbar-bg);
  border: 1px solid var(--wf-toolbar-border);
  border-radius: 24px;
  padding: 5px 10px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.5);
  white-space: nowrap;
  pointer-events: all;
  color: var(--wf-toolbar-text);
  user-select: none;
}

.toolbar-divider {
  width: 1px;
  height: 16px;
  background: var(--wf-toolbar-border);
  margin: 0 6px;
  flex-shrink: 0;
}

/* 缩放区 */
.toolbar-zoom {
  position: relative;
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 4px 10px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 12px;
  color: var(--wf-toolbar-text);
  transition: background 150ms;
}
.toolbar-zoom:hover { background: var(--wf-toolbar-hover); }
.toolbar-zoom-val   { font-weight: 500; min-width: 32px; text-align: right; }

/* 缩放下拉 */
.zoom-dropdown {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%);
  background: var(--wf-card-bg);
  border: 1px solid var(--wf-card-border);
  border-radius: 10px;
  padding: 4px 0;
  min-width: 130px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.5);
  z-index: 50;
}
.zoom-menu-item {
  display: block;
  width: 100%;
  padding: 7px 16px;
  background: none;
  border: none;
  text-align: left;
  font-size: 13px;
  color: var(--wf-toolbar-text);
  cursor: pointer;
  transition: background 150ms;
}
.zoom-menu-item:hover  { background: var(--wf-toolbar-hover); }
.zoom-menu-item.active { color: #79b8ff; font-weight: 600; }
.zoom-menu-divider {
  height: 1px;
  background: var(--wf-card-border);
  margin: 4px 0;
}

.toolbar-arrow { font-size: 9px; color: var(--wf-toolbar-muted); margin-left: 1px; }

/* 图标按钮 */
.toolbar-icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: none;
  border-radius: 7px;
  cursor: pointer;
  color: var(--wf-toolbar-text);
  transition: background 150ms, color 150ms;
  flex-shrink: 0;
}
.toolbar-icon-btn:hover  { background: var(--wf-toolbar-hover); }
.toolbar-icon-btn.active { background: var(--wf-toolbar-active); color: var(--wf-toolbar-active-text); }

/* 添加节点按钮 */
.toolbar-add-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 14px;
  border: none;
  background: rgba(139, 92, 246, 0.18);
  border-radius: 10px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  color: #a78bfa;
  transition: background 150ms, color 150ms;
  white-space: nowrap;
}
.toolbar-add-btn:hover,
.toolbar-add-btn.active {
  background: rgba(139, 92, 246, 0.32);
  color: #c4b5fd;
}

/* 试运行按钮 */
.toolbar-run-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  background: var(--wf-run-bg);
  color: #fff;
  border: none;
  border-radius: 16px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  transition: background 150ms;
  flex-shrink: 0;
}
.toolbar-run-btn:hover   { background: var(--wf-run-hover); }
.toolbar-run-btn.running { background: var(--wf-run-running); }
.run-icon { font-size: 11px; }

/* ── 节点选择弹层 ── */
.add-node-popover {
  width: 480px;
  max-height: 520px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  pointer-events: all;
  margin-bottom: 8px; /* 距工具栏间距 */
}

/* 弹出动画（从下往上） */
.node-popover-enter-active,
.node-popover-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}
.node-popover-enter-from,
.node-popover-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
.node-popover-enter-to,
.node-popover-leave-from {
  opacity: 1;
  transform: translateY(0);
}

/* 搜索框 */
.node-search-wrap {
  padding: 10px 12px 8px;
  border-bottom: 1px solid var(--tf-border);
  flex-shrink: 0;
}
.search-icon { font-size: 11px; }

/* 滚动区 */
.panel-scroll {
  overflow-y: auto;
  flex: 1;
  padding: 4px 0 8px;
}

/* 分类区块 */
.panel-section {
  padding: 8px 12px 4px;
}

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--tf-text-tertiary);
  letter-spacing: 0.4px;
  margin-bottom: 4px;
  padding: 4px 2px 2px;
}

/* 两列网格 */
.node-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2px;
}

/* 节点格子项：图标 + 名称横排 */
.node-grid-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 7px;
  cursor: pointer;
  user-select: none;
  transition: background 0.12s;
  position: relative;
  min-width: 0;
}
.node-grid-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 3px;
  background: var(--node-color, #6366f1);
  border-radius: 3px;
  opacity: 0;
  transition: opacity 0.12s;
}
.node-grid-item:hover { background: var(--tf-bg-hover); }
.node-grid-item:hover::before { opacity: 1; }

.node-grid-icon {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  background: color-mix(in srgb, var(--node-color, #6366f1) 15%, transparent);
  flex-shrink: 0;
}

.node-grid-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 无结果 */
.no-search-result {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  padding: 20px 10px;
  text-align: center;
}
</style>
