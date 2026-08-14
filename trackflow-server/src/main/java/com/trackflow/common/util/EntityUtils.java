package com.trackflow.common.util;

import com.trackflow.common.exception.BusinessException;

/**
 * 实体校验工具类
 *
 * 统一"从 Mapper 查出实体，若为 null 则抛 404 异常"的模式，
 * 消除各 Service 中手写 if (entity == null) throw new BusinessException(...) 的重复。
 *
 * 用法：
 * <pre>
 *   // 旧写法（散落在各 Service）
 *   CustomFieldDefinition field = definitionMapper.selectById(fieldId);
 *   if (field == null) {
 *       throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "自定义字段不存在");
 *   }
 *
 *   // 新写法（一行）
 *   CustomFieldDefinition field = EntityUtils.requireFound(
 *       definitionMapper.selectById(fieldId), "自定义字段", fieldId);
 * </pre>
 */
public final class EntityUtils {

    private EntityUtils() {
    }

    /**
     * 断言实体非 null，否则抛出 notFound 异常。
     *
     * @param entity     Mapper 查询结果（可能为 null）
     * @param entityName 实体名称，如"工单"、"自定义字段"、"仪表盘"
     * @param id         实体 ID（用于拼接错误信息）
     * @param <T>        实体类型
     * @return 非 null 的实体对象
     * @throws BusinessException RESOURCE_NOT_FOUND 当 entity 为 null 时
     */
    public static <T> T requireFound(T entity, String entityName, Object id) {
        if (entity == null) {
            throw BusinessException.notFound(entityName, id);
        }
        return entity;
    }

    /**
     * 断言实体非 null，否则抛出 notFound 异常（不带 ID，仅描述）。
     *
     * @param entity     Mapper 查询结果（可能为 null）
     * @param message    完整错误信息，如"工单不存在"
     * @param <T>        实体类型
     * @return 非 null 的实体对象
     * @throws BusinessException RESOURCE_NOT_FOUND 当 entity 为 null 时
     */
    public static <T> T requireFound(T entity, String message) {
        if (entity == null) {
            throw BusinessException.notFound(message);
        }
        return entity;
    }
}
