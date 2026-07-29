package com.trackflow.customfield.vo;

import lombok.Data;

/**
 * 字段替换结果 VO
 */
@Data
public class ReplaceResultVO {

    /**
     * 项目 ID
     */
    private String projectId;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 原字段 ID
     */
    private String sourceFieldId;

    /**
     * 原字段名称
     */
    private String sourceFieldName;

    /**
     * 目标字段 ID
     */
    private String targetFieldId;

    /**
     * 目标字段名称
     */
    private String targetFieldName;

    /**
     * 迁移的 issue 数量
     */
    private int migratedIssueCount;

    /**
     * 合并的选项数量（仅 list 类型）
     */
    private int mergedOptionCount;

    /**
     * 成功消息
     */
    private String message;
}
