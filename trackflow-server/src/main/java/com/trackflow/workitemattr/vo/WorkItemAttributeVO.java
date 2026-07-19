package com.trackflow.workitemattr.vo;

import lombok.Data;

import java.util.List;

/**
 * 工作项属性 VO（包含值列表和项目分配）
 */
@Data
public class WorkItemAttributeVO {
    private String id;
    private String name;
    private Boolean isBuiltin;
    private Integer position;
    private String createdAt;
    private String updatedAt;

    /** 可选值列表 */
    private List<AttributeValueVO> values;

    /** 分配的项目 ID 列表（向后兼容） */
    private List<String> projectIds;

    /** 分配的项目详细信息列表 */
    private List<AttributeProjectVO> projects;

    /** 使用量（有多少条工时记录引用了此属性） */
    private Integer usageCount;
}
