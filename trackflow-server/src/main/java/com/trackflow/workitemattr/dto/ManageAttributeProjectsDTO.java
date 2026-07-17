package com.trackflow.workitemattr.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 管理属性-项目分配
 */
@Data
public class ManageAttributeProjectsDTO {

    @NotEmpty(message = "项目列表不能为空")
    private List<Long> projectIds;
}
