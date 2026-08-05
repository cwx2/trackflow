package com.trackflow.customfield.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.handler.CustomFieldHandlerRegistry;
import com.trackflow.customfield.handler.CustomFieldTypeHandler;
import com.trackflow.customfield.mapper.CustomFieldDefinitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自定义字段排序辅助类 — 通过 {@link CustomFieldHandlerRegistry} 委托给各类型 Handler 获取排序表达式。
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
    private final CustomFieldHandlerRegistry handlerRegistry;

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
     * 校验字段存在性和可排序性后，使用 Handler 提供的标量子查询构建 ORDER BY 表达式。
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

        // 通过 HandlerRegistry 检查字段类型是否支持排序
        var handler = handlerRegistry.getHandler(fieldDef.getFieldFormat());
        if (handler.isEmpty() || !handler.get().isSortable()) {
            log.warn("自定义字段排序被跳过: 字段类型 {} 不支持排序 cf_{}", fieldDef.getFieldFormat(), cfIdStr);
            return false;
        }

        // 委托 Handler 构建排序表达式
        String orderExpr = handler.get().toSortExpression(cfId);
        wrapper.orderBy(true, asc, orderExpr);
        return true;
    }
}
