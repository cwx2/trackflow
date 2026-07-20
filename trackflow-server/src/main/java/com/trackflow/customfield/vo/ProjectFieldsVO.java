package com.trackflow.customfield.vo;

import lombok.Data;

import java.util.List;

/**
 * "Fields in Projects" 矩阵视图 — 按项目分组展示其关联的自定义字段。
 */
@Data
public class ProjectFieldsVO {
    /** 项目 ID */
    private String projectId;
    /** 项目名称 */
    private String projectName;
    /** 项目标识（key） */
    private String projectKey;
    /** 该项目关联的自定义字段列表（含全局字段） */
    private List<FieldSummaryVO> fields;

    @Data
    public static class FieldSummaryVO {
        /** 字段 ID */
        private String id;
        /** 字段名称 */
        private String name;
        /** 字段类型 */
        private String fieldFormat;
        /** 是否全局字段 */
        private Boolean isForAll;
        /** 是否必填（全局级别） */
        private Boolean isRequired;
        /** 是否多值 */
        private Boolean isMulti;

        // === 项目级覆盖属性 ===

        /** 项目级必填性覆盖（null=继承全局，true/false=覆盖） */
        private Boolean projectIsRequired;
        /** 项目级默认值覆盖（null=继承全局） */
        private String projectDefaultValue;
        /** 字段在项目中的排序位置 */
        private Integer position;
        /** 是否存在项目级覆盖（便于前端快速判断） */
        private Boolean hasOverride;
    }
}
