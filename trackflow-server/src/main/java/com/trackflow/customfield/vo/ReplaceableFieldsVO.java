package com.trackflow.customfield.vo;

import lombok.Data;

import java.util.List;

/**
 * 可用于替换的字段列表 VO
 */
@Data
public class ReplaceableFieldsVO {

    /**
     * 当前字段 ID
     */
    private String currentFieldId;

    /**
     * 当前字段名称
     */
    private String currentFieldName;

    /**
     * 当前字段类型
     */
    private String currentFieldFormat;

    /**
     * 可用于替换的目标字段列表（相同类型）
     */
    private List<ReplaceableField> availableFields;

    @Data
    public static class ReplaceableField {
        /**
         * 字段 ID
         */
        private String id;

        /**
         * 字段名称
         */
        private String name;

        /**
         * 字段类型
         */
        private String fieldFormat;

        /**
         * 字段使用的项目数量
         */
        private int projectCount;

        /**
         * 选项数量（仅 list 类型）
         */
        private int optionCount;
    }
}
