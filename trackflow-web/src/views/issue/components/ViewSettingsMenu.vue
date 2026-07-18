<template>
  <a-dropdown trigger="click" position="br" :popup-max-height="false">
    <a-button size="small" type="text" title="视图设置">
      <template #icon><icon-settings /></template>
    </a-button>
    <template #content>
      <div class="view-settings-menu">
        <!-- Layout section -->
        <div class="settings-section">
          <div class="settings-section-title">布局</div>
          <div class="settings-options">
            <div
              class="settings-option"
              :class="{ active: layout === 'table' }"
              @click="setLayout('table')"
            >
              <icon-list class="option-icon" />
              <span>表格</span>
            </div>
            <div
              class="settings-option"
              :class="{ active: layout === 'list' }"
              @click="setLayout('list')"
            >
              <icon-menu class="option-icon" />
              <span>列表</span>
            </div>
          </div>
        </div>

        <!-- Density section (only visible in list mode) -->
        <div v-if="layout === 'list'" class="settings-section">
          <div class="settings-section-title">密度</div>
          <div class="settings-density">
            <div
              v-for="d in densityOptions"
              :key="d.value"
              class="density-option"
              :class="{ active: density === d.value }"
              @click="setDensity(d.value)"
            >
              <span class="density-label">{{ d.label }}</span>
              <span class="density-desc">{{ d.desc }}</span>
            </div>
          </div>
        </div>

        <!-- Structure section -->
        <div class="settings-section">
          <div class="settings-section-title">结构</div>
          <div class="settings-options">
            <div
              class="settings-option"
              :class="{ active: structure === 'flat' }"
              @click="setStructure('flat')"
            >
              <icon-minus class="option-icon" />
              <span>平铺</span>
            </div>
            <div
              class="settings-option"
              :class="{ active: structure === 'tree' }"
              @click="setStructure('tree')"
            >
              <icon-branch class="option-icon" />
              <span>层级</span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </a-dropdown>
</template>

<script setup lang="ts">
import { IconSettings, IconList, IconMenu, IconMinus, IconBranch } from '@arco-design/web-vue/es/icon'
import type { LayoutMode, DensityLevel, StructureMode } from '../composables'

const props = defineProps<{
  layout: LayoutMode
  density: DensityLevel
  structure: StructureMode
}>()

const emit = defineEmits<{
  (e: 'update:layout', value: LayoutMode): void
  (e: 'update:density', value: DensityLevel): void
  (e: 'update:structure', value: StructureMode): void
}>()

const densityOptions = [
  { value: 'S' as DensityLevel, label: 'S', desc: '单行' },
  { value: 'M' as DensityLevel, label: 'M', desc: '紧凑' },
  { value: 'L' as DensityLevel, label: 'L', desc: '详细' }
]

function setLayout(mode: LayoutMode) {
  emit('update:layout', mode)
}

function setDensity(level: DensityLevel) {
  emit('update:density', level)
}

function setStructure(mode: StructureMode) {
  emit('update:structure', mode)
}
</script>

<style scoped>
.view-settings-menu {
  padding: 8px 0;
  min-width: 200px;
}

.settings-section {
  padding: 4px 12px;
}

.settings-section + .settings-section {
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px solid var(--tf-border-secondary, var(--color-neutral-3));
}

.settings-section-title {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-text-tertiary, var(--color-text-3));
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}

.settings-options {
  display: flex;
  gap: 4px;
}

.settings-option {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: var(--tf-text-secondary, var(--color-text-2));
  transition: background 100ms, color 100ms;
  flex: 1;
}

.settings-option:hover {
  background: var(--tf-bg-hover, var(--color-fill-2));
}

.settings-option.active {
  background: var(--tf-bg-active, var(--color-primary-light-1));
  color: var(--tf-accent, var(--color-primary-6));
  font-weight: 500;
}

.option-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.settings-density {
  display: flex;
  gap: 4px;
}

.density-option {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  flex: 1;
  transition: background 100ms, color 100ms;
}

.density-option:hover {
  background: var(--tf-bg-hover, var(--color-fill-2));
}

.density-option.active {
  background: var(--tf-bg-active, var(--color-primary-light-1));
  color: var(--tf-accent, var(--color-primary-6));
}

.density-label {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.2;
}

.density-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary, var(--color-text-3));
  margin-top: 2px;
}

.density-option.active .density-desc {
  color: var(--tf-accent, var(--color-primary-6));
  opacity: 0.8;
}
</style>
