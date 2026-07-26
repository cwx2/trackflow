<template>
  <a-trigger
    v-model:popup-visible="popupVisible"
    trigger="click"
    position="bl"
    :popup-offset="4"
    :unmount-on-close="false"
    class="board-selector-trigger"
  >
    <!-- 触发器：当前看板名称按钮 -->
    <span class="board-selector-btn-wrapper">
      <button
        class="board-selector-btn"
        :class="{ 'board-selector-btn--active': popupVisible }"
        :title="currentBoardName || '选择看板'"
      >
        <span class="board-selector-btn__name">{{ currentBoardName || '选择看板' }}</span>
        <svg class="board-selector-btn__arrow" :class="{ 'board-selector-btn__arrow--open': popupVisible }" width="12" height="12" viewBox="0 0 12 12" fill="none">
          <path d="M3 4.5L6 7.5L9 4.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
    </span>

    <!-- 下拉面板 -->
    <template #content>
      <div class="board-selector-panel">
        <!-- 搜索 -->
        <div class="board-selector-search">
          <a-input-search
            v-model="searchQuery"
            placeholder="搜索看板..."
            size="small"
            allow-clear
          />
        </div>

        <!-- 列表 -->
        <div class="board-selector-list" role="listbox">
          <!-- 收藏区域 -->
          <template v-if="favoriteBoards.length > 0">
            <div class="board-selector-group-label">收藏</div>
            <div
              v-for="board in favoriteBoards"
              :key="board.projectId"
              class="board-selector-item"
              :class="{ 'board-selector-item--active': board.projectId === modelValue }"
              role="option"
              :aria-selected="board.projectId === modelValue"
              @click="selectBoard(board.projectId)"
            >
              <button
                class="board-selector-star board-selector-star--filled"
                title="取消收藏"
                @click.stop="toggleFavorite(board)"
              >★</button>
              <div class="board-selector-item__content">
                <span class="board-selector-item__name">{{ board.name }}</span>
                <span class="board-selector-item__meta">{{ board.projectKey }}</span>
              </div>
              <span v-if="board.ownerName" class="board-selector-item__owner">{{ board.ownerName }}</span>
              <!-- 克隆按钮 -->
              <button
                class="board-selector-clone-btn"
                title="克隆此看板"
                @click.stop="openCloneModal(board)"
              >
                <icon-copy />
              </button>
            </div>
          </template>

          <!-- 分隔线 -->
          <div v-if="favoriteBoards.length > 0 && otherBoards.length > 0" class="board-selector-divider"></div>

          <!-- 其他看板 -->
          <template v-if="otherBoards.length > 0">
            <div v-if="favoriteBoards.length > 0" class="board-selector-group-label">所有看板</div>
            <div
              v-for="board in otherBoards"
              :key="board.projectId"
              class="board-selector-item"
              :class="{ 'board-selector-item--active': board.projectId === modelValue }"
              role="option"
              :aria-selected="board.projectId === modelValue"
              @click="selectBoard(board.projectId)"
            >
              <button
                class="board-selector-star"
                title="收藏"
                @click.stop="toggleFavorite(board)"
              >☆</button>
              <div class="board-selector-item__content">
                <span class="board-selector-item__name">{{ board.name }}</span>
                <span class="board-selector-item__meta">{{ board.projectKey }}</span>
              </div>
              <span v-if="board.ownerName" class="board-selector-item__owner">{{ board.ownerName }}</span>
              <!-- 克隆按钮 -->
              <button
                class="board-selector-clone-btn"
                title="克隆此看板"
                @click.stop="openCloneModal(board)"
              >
                <icon-copy />
              </button>
            </div>
          </template>

          <!-- 空状态 -->
          <div v-if="filteredBoards.length === 0 && !loading" class="board-selector-empty">
            <template v-if="searchQuery">
              <span>未找到匹配的看板</span>
            </template>
            <template v-else>
              <span>暂无可访问的看板</span>
            </template>
          </div>

          <!-- 加载中 -->
          <div v-if="loading" class="board-selector-loading">
            <a-spin :size="16" />
            <span>加载中...</span>
          </div>
        </div>
      </div>
    </template>
  </a-trigger>

  <!-- 克隆看板弹窗 -->
  <CloneBoardModal
    v-if="cloneTarget"
    v-model="showCloneModal"
    :source-project-id="cloneTarget.projectId"
    :source-board-name="cloneTarget.name"
    :source-project-key="cloneTarget.projectKey"
    @cloned="onBoardCloned"
  />
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { boardApi } from '@/api'
import type { BoardListItemVO } from '@/api/types'
import CloneBoardModal from './CloneBoardModal.vue'

const props = defineProps<{
  modelValue: string | undefined
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const popupVisible = ref(false)
const searchQuery = ref('')
const boards = ref<BoardListItemVO[]>([])
const loading = ref(false)

// 克隆看板状态
const showCloneModal = ref(false)
const cloneTarget = ref<BoardListItemVO | null>(null)

/** 当前选中看板的名称 */
const currentBoardName = computed(() => {
  if (!props.modelValue) return ''
  const board = boards.value.find(b => b.projectId === props.modelValue)
  return board?.name || ''
})

/** 根据搜索过滤的看板列表 */
const filteredBoards = computed(() => {
  if (!searchQuery.value) return boards.value
  const query = searchQuery.value.toLowerCase()
  return boards.value.filter(b =>
    b.name.toLowerCase().includes(query) ||
    b.projectKey.toLowerCase().includes(query) ||
    b.projectName.toLowerCase().includes(query) ||
    (b.ownerName && b.ownerName.toLowerCase().includes(query))
  )
})

/** 收藏的看板 */
const favoriteBoards = computed(() => filteredBoards.value.filter(b => b.favorite))

/** 非收藏的看板 */
const otherBoards = computed(() => filteredBoards.value.filter(b => !b.favorite))

/** 加载看板列表 */
async function loadBoards() {
  loading.value = true
  try {
    const res = await boardApi.listBoards()
    if (res.code === 0) {
      boards.value = res.data
    }
  } catch {
    // 静默失败，用户可重新打开面板
  } finally {
    loading.value = false
  }
}

/** 选择看板 */
function selectBoard(projectId: string) {
  emit('update:modelValue', projectId)
  popupVisible.value = false
  searchQuery.value = ''
}

/** 切换收藏状态 */
async function toggleFavorite(board: BoardListItemVO) {
  const wasFavorite = board.favorite
  // 乐观更新
  board.favorite = !wasFavorite
  try {
    if (wasFavorite) {
      await boardApi.removeFavorite(board.projectId)
    } else {
      await boardApi.addFavorite(board.projectId)
    }
    // 重新排序
    sortBoards()
  } catch {
    // 回滚
    board.favorite = wasFavorite
  }
}

/** 打开克隆看板弹窗 */
function openCloneModal(board: BoardListItemVO) {
  cloneTarget.value = board
  showCloneModal.value = true
  // 关闭下拉面板
  popupVisible.value = false
}

/** 克隆成功后重新加载看板列表 */
function onBoardCloned(newProjectId: string) {
  loadBoards()
}

/** 排序看板列表（收藏在前） */
function sortBoards() {
  boards.value.sort((a, b) => {
    if (a.favorite && !b.favorite) return -1
    if (!a.favorite && b.favorite) return 1
    return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' })
  })
}

// 面板打开时加载数据
watch(popupVisible, (visible) => {
  if (visible) {
    loadBoards()
  } else {
    searchQuery.value = ''
  }
})

// 初始加载（获取当前看板名称用）
onMounted(() => {
  loadBoards()
})
</script>

<style scoped>
.board-selector-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid var(--color-border-2, #3a3d42);
  border-radius: 6px;
  background: var(--color-bg-2, #22252a);
  color: var(--color-text-1, #e6edf3);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 150ms;
  max-width: 220px;
  height: 28px;
}

.board-selector-btn:hover {
  border-color: var(--color-border-3, #4a4d52);
  background: var(--color-bg-3, #2a2d33);
}

.board-selector-btn--active {
  border-color: rgb(var(--primary-6, 64 128 255));
  background: var(--color-bg-3, #2a2d33);
}

.board-selector-btn__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.board-selector-btn__arrow {
  flex-shrink: 0;
  transition: transform 200ms;
  opacity: 0.6;
}

.board-selector-btn__arrow--open {
  transform: rotate(180deg);
}

/* Panel */
.board-selector-panel {
  width: 320px;
  max-height: 420px;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-popup, #2a2d33);
  border: 1px solid var(--color-border-2, #3a3d42);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
  overflow: hidden;
}

.board-selector-search {
  padding: 8px 12px;
  border-bottom: 1px solid var(--color-border-1, #333639);
}

.board-selector-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}

.board-selector-group-label {
  padding: 6px 12px 4px;
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-3, #6b7280);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.board-selector-divider {
  margin: 4px 12px;
  height: 1px;
  background: var(--color-border-1, #333639);
}

.board-selector-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  cursor: pointer;
  transition: background 100ms;
}

.board-selector-item:hover {
  background: var(--color-fill-2, #333639);
}

.board-selector-item--active {
  background: var(--color-fill-2, #333639);
}

.board-selector-item--active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: rgb(var(--primary-6, 64 128 255));
  border-radius: 0 2px 2px 0;
}

.board-selector-item {
  position: relative;
}

.board-selector-star {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: none;
  font-size: 14px;
  color: var(--color-text-4, #4b5563);
  cursor: pointer;
  border-radius: 4px;
  transition: all 100ms;
}

.board-selector-star:hover {
  color: var(--color-warning-6, #d29922);
  background: var(--color-fill-2, #333639);
}

.board-selector-star--filled {
  color: var(--color-warning-6, #d29922);
}

.board-selector-item__content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.board-selector-item__name {
  font-size: 13px;
  color: var(--color-text-1, #e6edf3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.board-selector-item__meta {
  font-size: 11px;
  color: var(--color-text-3, #6b7280);
}

.board-selector-item__owner {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--color-text-3, #6b7280);
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.board-selector-empty {
  padding: 24px 12px;
  text-align: center;
  color: var(--color-text-3, #6b7280);
  font-size: 13px;
}

.board-selector-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px;
  color: var(--color-text-3, #6b7280);
  font-size: 12px;
}

/* 克隆按钮：默认隐藏，hover 时显示 */
.board-selector-clone-btn {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  display: none;
  align-items: center;
  justify-content: center;
  border: none;
  background: none;
  font-size: 13px;
  color: var(--color-text-4, #4b5563);
  cursor: pointer;
  border-radius: 4px;
  transition: all 100ms;
  padding: 0;
}

.board-selector-item:hover .board-selector-clone-btn {
  display: flex;
}

.board-selector-clone-btn:hover {
  color: var(--color-text-1, #e6edf3);
  background: var(--color-fill-3, #3a3d42);
}
</style>
