package com.trackflow.common.converter;

import java.util.List;

/**
 * 基础类型转换接口
 * <p>
 * 提供通用的类型转换方法（如 Long ↔ String），供各模块的 MapStruct Converter 继承使用。
 * 各模块的具体 Converter 应继承此接口并添加 {@code @Mapper} 注解。
 */
public interface BaseConverter {

    /**
     * Long 转 String（用于 ID 序列化）
     *
     * @param id Long 类型 ID
     * @return 字符串形式的 ID，若输入为 null 则返回 null
     */
    default String longToString(Long id) {
        return id == null ? null : id.toString();
    }

    /**
     * String 转 Long（用于 ID 反序列化）
     *
     * @param id 字符串形式的 ID
     * @return Long 类型 ID，若输入为 null 或空白则返回 null
     */
    default Long stringToLong(String id) {
        return id == null || id.isBlank() ? null : Long.parseLong(id);
    }

    /**
     * List<Long> 转 List<String>（用于 VO 中 ID 列表的序列化）
     *
     * @param ids Long 列表
     * @return String 列表，若输入为 null 则返回 null
     */
    default List<String> longListToStringList(List<Long> ids) {
        if (ids == null) {
            return null;
        }
        return ids.stream().map(String::valueOf).toList();
    }
}

