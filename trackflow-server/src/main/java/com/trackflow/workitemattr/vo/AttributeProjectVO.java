package com.trackflow.workitemattr.vo;

import lombok.Data;

/**
 * 属性分配项目信息 VO
 * 用于属性详情中展示"分配的项目"列表
 */
@Data
public class AttributeProjectVO {
    /** 项目 ID */
    private String id;
    /** 项目标识 */
    private String key;
    /** 项目名称 */
    private String name;
    /** 项目是否已删除/归档（对应项目不存在时为 true） */
    private Boolean deleted;
}
