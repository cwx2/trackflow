<template>
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
    <button class="toolbar-add-btn" @click="emit('toggle-node-panel')">
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
    <button class="toolbar-run-btn" :class="{ running: isRunning }" @click="emit('run')">
      <span class="run-icon">▶</span>
      <span>{{ isRunning ? '运行中...' : '试运行' }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const props = defineProps<{
  zoomPercent: number
  minimapOpen: boolean
  debugMode: boolean
  isRunning: boolean
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
  'toggle-node-panel': []
  'toggle-debug': []
  'run': []
}>()

const zoomMenuOpen = ref(false)
const zoomBtnRef = ref<HTMLElement | null>(null)

function handleOutsideClick(e: MouseEvent) {
  if (zoomBtnRef.value && !zoomBtnRef.value.contains(e.target as Node)) {
    zoomMenuOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleOutsideClick)
})

onUnmounted(() => {
  document.removeEventListener('click', handleOutsideClick)
})
</script>

<style scoped>
/* ── 工具条容器 ── */
.bottom-toolbar {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 20;
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
.toolbar-add-btn:hover {
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
</style>
