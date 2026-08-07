package com.trackflow.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 添加项目到组织 DTO
 */
@Data
public class AddProjectsToOrgDTO {

    @NotNull(message = "项目ID列表不能为空")
    private List<Long> projectIds;
}
