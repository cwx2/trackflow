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
      const listTypeFormats = new Set(['list', 'ownedField', 'version', 'state'])
      for (const field of newFields) {
        if (!(field.id in newValues)) {
          const effectiveDefault = field.effectiveDefaultValue ?? field.defaultValue
          if (effectiveDefault) {
            // 对于列表类型字段，默认值必须是有效的数字 ID，否则忽略
            if (listTypeFormats.has(field.fieldFormat)) {
              if (/^\d+$/.test(effectiveDefault)) {
                newValues[field.id] = effectiveDefault
              }
              // 非数字默认值（如文本名称 "Normal"）忽略，避免后端验证失败
            } else {
              newValues[field.id] = effectiveDefault
            }
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

  /**
   * 当源字段值变化时，级联清除依赖字段中不再有效的值。
   * 对标 YouTrack: "If a user selects a value in the dependent field that does not match
   * the current filtering conditions, the system automatically clears the value."
   */
  function cascadeClearOnSourceChange(sourceFieldId: string, newSourceValue: string) {
    for (const field of allFields.value) {
      if (field.filterFieldId !== sourceFieldId || !field.filterRules) continue

      try {
        const rules: { whenValue: string; showOnly: string[] }[] = JSON.parse(field.filterRules)
        if (!rules || rules.length === 0) continue

        if (!newSourceValue) continue // 源字段清空时不做限制

        const matchedRule = rules.find(r => r.whenValue === newSourceValue)
        if (!matchedRule || !matchedRule.showOnly || matchedRule.showOnly.length === 0) continue

        const allowedSet = new Set(matchedRule.showOnly)
        const currentValue = values.value[field.id]

        if (!currentValue) continue

        // 多值字段：逐个检查
        if (field.isMulti && currentValue.includes(',')) {
          const currentIds = currentValue.split(',').filter(s => s.trim())
          const validIds = currentIds.filter(id => allowedSet.has(id))
          if (validIds.length < currentIds.length) {
            values.value[field.id] = validIds.join(',')
          }
        } else {
          // 单值字段
          if (!allowedSet.has(currentValue)) {
            values.value[field.id] = ''
          }
        }
      } catch {
        // filterRules 解析失败 → 跳过
      }
    }
  }

  // 监听 values 变化，触发级联清除（用 deep watch + 防止无限循环）
  let cascadeGuard = false
  let prevValues: Record<string, string> = {}
  watch(values, (newVals) => {
    if (cascadeGuard) return

    // 找出变化的字段
    for (const fieldId of Object.keys(newVals)) {
      if (newVals[fieldId] !== prevValues[fieldId]) {
        // 检查该字段是否是某个字段的 filterFieldId
        const hasDependents = allFields.value.some(f => f.filterFieldId === fieldId)
        if (hasDependents) {
          cascadeGuard = true
          cascadeClearOnSourceChange(fieldId, newVals[fieldId] || '')
          cascadeGuard = false
        }
      }
    }
    prevValues = { ...newVals }
  }, { deep: true })

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
