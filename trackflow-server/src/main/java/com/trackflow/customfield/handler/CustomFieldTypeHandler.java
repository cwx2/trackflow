package com.trackflow.customfield.handler;

import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.service.CustomFieldValidationEngine.FieldValidationError;

import java.util.List;
import java.util.Map;

/**
 * 自定义字段类型处理器接口 — 策略模式核心抽象。
 * <p>
 * 每种字段类型（string/int/date/list/user 等）实现此接口，
 * 通过 Spring 自动发现机制注册到 {@link CustomFieldHandlerRegistry}。
 * 新增字段类型只需创建新的 @Component 实现，无需修改任何现有 Service。
 *
 * @author TrackFlow
 * @since 1.0
 */
public interface CustomFieldTypeHandler {

    /**
     * 本处理器负责的 fieldFormat 标识（如 "int"、"string"、"list"）。
     */
    String fieldFormat();

    /**
     * 校验字段值。
     *
     * @param field     字段定义
     * @param value     待校验值（已保证非 null 且非空）
     * @param context   校验上下文（含 projectId 等信息）
     * @return 错误列表（空 = 通过）
     */
    List<FieldValidationError> validate(CustomFieldDefinition field, String value, ValidationContext context);

    /**
     * 将存储的原始值转换为展示值（批量版，使用预加载 Map）。
     * <p>
     * 对于 list/user 类型，展示值需要通过预加载的映射表解析（选项名/用户名）。
     * 对于 string/int/date 等直接值类型，直接返回 rawValue。
     *
     * @param rawValue      存储原始值
     * @param field         字段定义
     * @param displayContext 展示上下文（含预加载的 optionTextMap/userNameMap）
     * @return 展示值，null 表示无法解析
     */
    String toDisplayValue(String rawValue, CustomFieldDefinition field, DisplayContext displayContext);

    /**
     * 构建 ORDER BY 子句中的 SQL 标量子查询表达式。
     * <p>
     * 不同类型需要不同的 CAST 或 JOIN 逻辑。
     * 默认返回直接字符串排序表达式。
     *
     * @param cfId 自定义字段 ID（Long 类型，来源经正则校验，无注入风险）
     * @return SQL 标量子查询表达式
     */
    default String toSortExpression(Long cfId) {
        return "(SELECT cfv.value FROM custom_field_value cfv " +
                "WHERE cfv.issue_id = issue.id AND cfv.custom_field_id = " + cfId +
                " AND cfv.is_multi = false AND cfv.value IS NOT NULL AND cfv.value != '' LIMIT 1)";
    }

    /**
     * 字段类型的中文显示名称（用于 UI 展示）。
     */
    String typeLabel();

    /**
     * 是否支持排序（默认 true，bool/period/text 等可覆盖返回 false）。
     */
    default boolean isSortable() {
        return true;
    }
}
