<template>
  <a-tooltip :content="showTooltip ? name : undefined" :disabled="!showTooltip" position="top" :mini="true">
    <div
      class="user-avatar"
      :class="{ 'user-avatar--square': shape === 'square' }"
      :style="avatarStyle"
    >
      <img
        v-if="avatar && !imgError"
        :src="avatar"
        :alt="name"
        class="user-avatar__img"
        @error="imgError = true"
      />
      <span v-else class="user-avatar__initial">{{ initial }}</span>
    </div>
  </a-tooltip>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

export interface UserAvatarProps {
  /** 用户显示名（必须，用于生成首字母和颜色） */
  name: string
  /** 头像图片 URL（可选，有时显示图片，无时显示文字头像） */
  avatar?: string
  /** 头像尺寸（px），默认 28 */
  size?: number
  /** 是否在 hover 时显示用户名 tooltip，默认 false */
  showTooltip?: boolean
  /** 自定义颜色（可选，不传则自动哈希计算） */
  color?: string
  /** 形状，circle 圆形（默认）| square 方形 */
  shape?: 'circle' | 'square'
}

const props = withDefaults(defineProps<UserAvatarProps>(), {
  size: 28,
  showTooltip: false,
  shape: 'circle',
})

const imgError = ref(false)

// Reset error state when avatar URL changes
watch(() => props.avatar, () => {
  imgError.value = false
})

/** 统一的颜色调色板 */
const PALETTE = [
  '#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6',
  '#06b6d4', '#84cc16', '#f97316', '#ec4899', '#6366f1',
]

/** 根据名称生成稳定的背景颜色 */
function getAvatarColor(name: string): string {
  if (!name) return PALETTE[0]
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash)
  }
  return PALETTE[Math.abs(hash) % PALETTE.length]
}

/** 统一的首字母提取规则 */
function getInitial(name: string): string {
  if (!name) return '?'
  // 中文：取第一个汉字
  const cjk = name.match(/[\u4e00-\u9fff]/)
  if (cjk) return cjk[0]
  // 英文：取首字母大写
  return name.charAt(0).toUpperCase()
}

const initial = computed(() => getInitial(props.name))

const bgColor = computed(() => props.color || getAvatarColor(props.name))

const fontSize = computed(() => {
  if (props.size <= 20) return 10
  if (props.size <= 24) return 11
  if (props.size <= 28) return 12
  if (props.size <= 36) return 13
  if (props.size <= 48) return 16
  return Math.round(props.size * 0.38)
})

const avatarStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  fontSize: `${fontSize.value}px`,
  background: bgColor.value,
  borderRadius: props.shape === 'square' ? '4px' : '50%',
}))
</script>

<style scoped>
.user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
  color: #fff;
  font-weight: 600;
  user-select: none;
  vertical-align: middle;
}

.user-avatar__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-avatar__initial {
  line-height: 1;
}
</style>
