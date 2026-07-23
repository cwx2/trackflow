package com.trackflow.workitemattr.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 管理项目中属性值可见性的请求体
 * 对标 YouTrack：项目管理员可以独立管理本项目的属性值子集
 */
@Data
public class ManageProjectAttributeValuesDTO {

    /**
     * 启用的值 ID 列表（项目中可见的值）
     */
    @NotNull(message = "值 ID 列表不能为空")
    private List<Long> valueIds;
}
