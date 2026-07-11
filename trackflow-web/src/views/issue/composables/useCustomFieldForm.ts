import { ref, watch, type Ref } from 'vue'
import { customFieldApi } from '@/api'
import type { CustomFieldDefinitionVO } from '@/api/types'

/**
 * 自定义字段表单 composable
 * 用于 Issue 创建/编辑表单中动态渲染自定义字段输入控件
 */
export function useCustomFieldForm(
  projectId: Ref<string | undefined>,
  issueType: Ref<string>
) {
  const fields = ref<CustomFieldDefinitionVO[]>([])
  const values = ref<Record<string, string>>({})
  const loading = ref(false)

  /**
   * 加载当前项目+类型适用的自定义字段
   */
  async function fetchFields() {
    if (!projectId.value) {
      fields.value = []
      return
    }
    loading.value = true
    try {
      const res = await customFieldApi.listByProject(projectId.value, issueType.value || undefined)
      const newFields = res.data || []

      // 保留仍然适用的字段值，清除不再适用的
      const newFieldIds = new Set(newFields.map(f => f.id))
      const newValues: Record<string, string> = {}
      for (const [key, val] of Object.entries(values.value)) {
        if (newFieldIds.has(key)) {
          newValues[key] = val
        }
      }

      // 为新字段填充默认值
      for (const field of newFields) {
        if (!(field.id in newValues) && field.defaultValue) {
          newValues[field.id] = field.defaultValue
        }
      }

      fields.value = newFields
      values.value = newValues
    } catch {
      fields.value = []
    } finally {
      loading.value = false
    }
  }

  /**
   * 设置已有的字段值（编辑模式）
   */
  function setValues(existingValues: Record<string, string>) {
    values.value = { ...existingValues }
  }

  /**
   * 获取用于提交的 customFields map（fieldId -> value）
   */
  function getPayload(): Record<string, string> {
    const payload: Record<string, string> = {}
    for (const [key, val] of Object.entries(values.value)) {
      if (val !== undefined && val !== '') {
        payload[key] = val
      }
    }
    return payload
  }

  /**
   * 客户端必填校验
   */
  function validateRequired(): string[] {
    const errors: string[] = []
    for (const field of fields.value) {
      if (field.isRequired && (!values.value[field.id] || values.value[field.id].trim() === '')) {
        errors.push(`${field.name} 为必填项`)
      }
    }
    return errors
  }

  // 当项目或类型变化时重新加载字段
  watch([projectId, issueType], () => {
    fetchFields()
  }, { immediate: true })

  return {
    fields,
    values,
    loading,
    fetchFields,
    setValues,
    getPayload,
    validateRequired
  }
}
