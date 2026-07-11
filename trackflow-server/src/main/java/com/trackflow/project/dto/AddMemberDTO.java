package com.trackflow.project.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddMemberDTO {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "角色ID不能为空")
    private Long roleId;
}
