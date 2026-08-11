<template>
  <a-modal
    :visible="renameVisible"
    title="重命名节点"
    ok-text="保存名称"
    @update:visible="emit('update:renameVisible', $event)"
    @ok="emit('rename')"
  >
    <a-form-item label="节点名称" required>
      <a-input :model-value="renameTitle" :max-length="80" placeholder="请输入节点名称"
        @update:model-value="emit('update:renameTitle', $event)" @press-enter="emit('rename')" />
    </a-form-item>
    <p class="node-action-hint">仅修改画布中的显示名称，不会改变节点类型、配置或数据流。</p>
  </a-modal>

  <a-modal
    :visible="deleteVisible"
    title="删除节点"
    ok-text="删除节点"
    :ok-button-props="{ status: 'danger' }"
    @update:visible="emit('update:deleteVisible', $event)"
    @ok="emit('delete')"
  >
    <a-alert type="warning" :show-icon="true">
      将删除“{{ targetTitle || '此节点' }}”以及与它相连的线。此操作在保存前可通过工具栏“上一步”撤销。
    </a-alert>
  </a-modal>

  <a-modal
    :visible="helpVisible"
    :title="`${definition?.meta?.title || targetTitle || '节点'}使用说明`"
    :footer="false"
    width="560px"
    @update:visible="emit('update:helpVisible', $event)"
  >
    <p class="node-help-description">{{ definition?.meta?.description || targetDescription || '该节点暂无补充说明。' }}</p>
    <section v-if="definition?.inputPorts?.length" class="node-help-section">
      <h4>输入</h4>
      <div v-for="port in definition.inputPorts" :key="port.name" class="node-help-port">
        <strong>{{ port.label || port.name }}</strong>
        <a-tag size="small">{{ port.valueType }}</a-tag>
        <a-tag v-if="port.required" size="small" color="red">必填</a-tag>
        <span>{{ port.description || '未提供说明' }}</span>
      </div>
    </section>
    <section v-if="definition?.outputPorts?.length" class="node-help-section">
      <h4>输出</h4>
      <div v-for="port in definition.outputPorts" :key="port.name" class="node-help-port">
        <strong>{{ port.label || port.name }}</strong>
        <a-tag size="small">{{ port.valueType }}</a-tag>
        <span>{{ port.description || '未提供说明' }}</span>
      </div>
    </section>
    <section v-if="definition?.configFields?.length" class="node-help-section">
      <h4>配置项</h4>
      <div v-for="field in definition.configFields" :key="field.key" class="node-help-port">
        <strong>{{ field.label }}</strong>
        <span>{{ field.description || field.placeholder || '在右侧配置面板中设置。' }}</span>
      </div>
    </section>
  </a-modal>
</template>

<script setup lang="ts">
defineProps<{
  renameVisible: boolean
  renameTitle: string
  deleteVisible: boolean
  helpVisible: boolean
  targetTitle: string
  targetDescription?: string
  definition?: any
}>()

const emit = defineEmits<{
  'update:renameVisible': [visible: boolean]
  'update:renameTitle': [title: string]
  'update:deleteVisible': [visible: boolean]
  'update:helpVisible': [visible: boolean]
  rename: []
  delete: []
}>()
</script>

<style scoped>
.node-action-hint, .node-help-description {
  margin: 10px 0 0;
  color: var(--tf-text-secondary);
  font-size: 13px;
}
.node-help-section { margin-top: 18px; }
.node-help-section h4 { margin: 0 0 8px; color: var(--tf-text-primary); font-size: 13px; }
.node-help-port {
  display: grid;
  grid-template-columns: minmax(120px, auto) auto auto 1fr;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-top: 1px solid var(--tf-border);
  color: var(--tf-text-secondary);
  font-size: 12px;
}
.node-help-port strong { color: var(--tf-text-primary); }
</style>
