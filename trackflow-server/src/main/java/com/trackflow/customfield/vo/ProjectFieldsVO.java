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
        /** 是否必填 */
        private Boolean isRequired;
        /** 是否多值 */
        private Boolean isMulti;
    }
}
