package com.trackflow.project.vo;

import lombok.Data;

/**
 * 项目复制概要统计 VO — 展示源项目中各模块可复制的数据量
 */
@Data
public class ProjectCopySummaryVO {

    /** 工作流转换规则数 */
    private Integer workflow;

    /** 标签数 */
    private Integer tags;

    /** 自定义字段数 */
    private Integer customFields;

    /** 看板列配置数 */
    private Integer board;

    /** 转换动作数 */
    private Integer actions;

    /** 成员数 */
    private Integer members;

    /** 共享查询数 */
    private Integer queries;
}
