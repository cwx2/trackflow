package com.trackflow.customfield.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.mapper.CustomFieldDefinitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自定义字段排序辅助类
 * <p>
 * 为 QueryExecutor（SavedQuery 路径）和 IssueService（标准列表路径）
 * 提供统一的自定义字段排序能力。通过标量子查询实现 ORDER BY，
 * 不引入 JOIN 从而避免对分页的影响。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomFieldSortHelper {

    private final CustomFieldDefinitionMapper customFieldDefinitionMapper;

    /**
     * 支持排序的自定义字段类型
     */
    private static final Set<String> SORTABLE_FIELD_FORMATS = Set.of(
            "string", "int", "float", "date", "datetime", "list", "user", "state", "ownedField", "version"
    );

    /**
     * 自定义字段排序 key 格式：cf_ + 纯数字 ID
     */
    private static final Pattern CF_SORT_KEY_PATTERN = Pattern.compile("^cf_(\\d+)$");

    /**
     * 判断排序字段是否为自定义字段格式（cf_数字ID）。
     *
     * @param sortField 排序字段名
     * @return true 如果是自定义字段排序 key
     */
    public boolean isCustomFieldSortKey(String sortField) {
        return sortField != null && CF_SORT_KEY_PATTERN.matcher(sortField).matches();
    }

    /**
     * 将自定义字段排序应用到 QueryWrapper。
     * <p>
     * 校验字段存在性和可排序性后，使用标量子查询构建 ORDER BY 表达式。
     * uk_cf_value_single 唯一索引保证单值字段子查询最多返回一行。
     *
     * @param wrapper   查询包装器（issue 表的查询）
     * @param sortField 排序字段 key（格式：cf_{数字ID}）
     * @param asc       是否升序
     * @return true 如果成功应用了排序，false 如果字段不存在或不支持排序
     */
    public boolean applyCustomFieldSort(QueryWrapper<?> wrapper, String sortField, boolean asc) {
        Matcher matcher = CF_SORT_KEY_PATTERN.matcher(sortField);
        if (!matcher.matches()) {
            return false;
        }

        String cfIdStr = matcher.group(1);
        Long cfId = Long.parseLong(cfIdStr);

        // 校验字段存在性和可排序性
        CustomFieldDefinition fieldDef = customFieldDefinitionMapper.selectById(cfId);
        if (fieldDef == null) {
            log.warn("自定义字段排序被跳过: 字段不存在 cf_{}", cfIdStr);
            return false;
        }
        if (Boolean.TRUE.equals(fieldDef.getIsMulti())) {
            log.warn("自定义字段排序被跳过: 多值字段不支持排序 cf_{}", cfIdStr);
            return false;
        }
        if (!SORTABLE_FIELD_FORMATS.contains(fieldDef.getFieldFormat())) {
            log.warn("自定义字段排序被跳过: 字段类型 {} 不支持排序 cf_{}", fieldDef.getFieldFormat(), cfIdStr);
            return false;
        }

        // 构建排序表达式并应用
        String orderExpr = buildOrderExpression(cfId, fieldDef.getFieldFormat());
        wrapper.orderBy(true, asc, orderExpr);
        return true;
    }

    /**
     * 根据自定义字段类型构建用于 ORDER BY 的标量子查询表达式。
     * <p>
     * 安全说明：cfId 为 Long 类型，来源经正则校验为纯数字，无 SQL 注入风险。
     * 所有 SQL 使用固定模板 + 数字 ID，不拼接任何用户输入字符串。
     *
     * @param cfId        自定义字段 ID
     * @param fieldFormat 字段类型
     * @return SQL 标量子查询表达式
     */
    private String buildOrderExpression(Long cfId, String fieldFormat) {
        return switch (fieldFormat) {
            case "int" ->
                    // 转为整数排序，无值的 issue 排在末尾
                    "(SELECT cfv.value::BIGINT FROM custom_field_value cfv " +
                    "WHERE cfv.issue_id = issue.id AND cfv.custom_field_id = " + cfId +
                    " AND cfv.is_multi = false AND cfv.value IS NOT NULL AND cfv.value != '' LIMIT 1)";
            case "float" ->
                    // 转为浮点数排序
                    "(SELECT cfv.value::DOUBLE PRECISION FROM custom_field_value cfv " +
                    "WHERE cfv.issue_id = issue.id AND cfv.custom_field_id = " + cfId +
                    " AND cfv.is_multi = false AND cfv.value IS NOT NULL AND cfv.value != '' LIMIT 1)";
            case "list", "state", "ownedField", "version" ->
                    // 按选项的 position 排序（管理员定义的选项顺序）
                    "(SELECT cfo.position FROM custom_field_value cfv " +
                    "JOIN custom_field_option cfo ON cfo.id = cfv.value::BIGINT " +
                    "WHERE cfv.issue_id = issue.id AND cfv.custom_field_id = " + cfId +
                    " AND cfv.is_multi = false AND cfv.value IS NOT NULL AND cfv.value != '' LIMIT 1)";
            case "user" ->
                    // 按用户显示名排序
                    "(SELECT u.display_name FROM custom_field_value cfv " +
                    "JOIN sys_user u ON u.id = cfv.value::BIGINT " +
                    "WHERE cfv.issue_id = issue.id AND cfv.custom_field_id = " + cfId +
                    " AND cfv.is_multi = false AND cfv.value IS NOT NULL AND cfv.value != '' LIMIT 1)";
            default ->
                    // string, date, datetime: 直接按字符串值排序（ISO 日期格式天然支持字符串排序）
                    "(SELECT cfv.value FROM custom_field_value cfv " +
                    "WHERE cfv.issue_id = issue.id AND cfv.custom_field_id = " + cfId +
                    " AND cfv.is_multi = false AND cfv.value IS NOT NULL AND cfv.value != '' LIMIT 1)";
        };
    }
}
