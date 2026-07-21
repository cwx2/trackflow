package com.trackflow.system.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户组简要信息 VO（用于下拉选择器）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupSimpleVO {
    private String id;
    private String name;
}
