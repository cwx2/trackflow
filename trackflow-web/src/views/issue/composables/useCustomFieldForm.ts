import { ref, computed, watch, type Ref } from 'vue'
import { customFieldApi } from '@/api'
import type { CustomFieldDefinitionVO } from '@/api/types'

/**
 * 自定义字段表单 composable
 * 用于 Issue 创建/编辑表单中动态渲染自定义字段输入控件
 * 支持条件显示：根据条件源字段的当前值动态显示/隐藏字段
 */
export function useCustomFieldForm(
  projectId: Ref<string | undefined>,
  issueType: Ref<string>
) {
  const allFields = ref<CustomFieldDefinitionVO[]>([])
  const values = ref<Record<string, string>>({})
  const loading = ref(false)

  /**
   * 根据条件过滤后的可见字段列表
   * 条件逻辑：如果字段有 conditionFieldId，则只有当条件源字段的当前值
   * 在 conditionValues 列表中时才显示
   */
  const fields = computed<CustomFieldDefinitionVO[]>(() => {
    return allFields.value.filter(cf => {
      if (!cf.conditionFieldId || !cf.conditionValues || cf.conditionValues.length === 0) {
        return true // 无条件，始终显示
      }
      // 查找条件源字段的当前值
      const condValue = values.value[cf.conditionFieldId]
      if (!condValue) return false // 条件源字段无值 → 隐藏

      // 对于单值字段，值是 option ID 字符串
      return cf.conditionValues.includes(condValue)
    })
  })

  /**
   * 加载当前项目+类型适用的自定义字段
   */
  async function fetchFields() {
    if (!projectId.value) {
      allFields.value = []
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

      // 为新字段填充默认值（优先使用项目级覆盖）
      for (const field of newFields) {
        if (!(field.id in newValues)) {
          const effectiveDefault = field.effectiveDefaultValue ?? field.defaultValue
          if (effectiveDefault) {
            newValues[field.id] = effectiveDefault
          }
        }
      }

      allFields.value = newFields
      values.value = newValues
    } catch {
      allFields.value = []
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
   * 注意：隐藏字段的已有值不会被清除（保护数据），但新建时不提交
   */
  function getPayload(): Record<string, string> {
    const payload: Record<string, string> = {}
    const visibleFieldIds = new Set(fields.value.map(f => f.id))
    for (const [key, val] of Object.entries(values.value)) {
      if (val !== undefined && val !== '' && visibleFieldIds.has(key)) {
        payload[key] = val
      }
    }
    return payload
  }

  /**
   * 客户端必填校验（只校验可见字段）
   * 使用 effectiveIsRequired（项目级覆盖 > 全局）
   */
  function validateRequired(): string[] {
    const errors: string[] = []
    for (const field of fields.value) {
      const isRequired = field.effectiveIsRequired ?? field.isRequired
      if (isRequired && (!values.value[field.id] || values.value[field.id].trim() === '')) {
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
    allFields,
    values,
    loading,
    fetchFields,
    setValues,
    getPayload,
    validateRequired
  }
}
