package com.trackflow.project.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 添加用户组到项目团队的请求参数
 */
@Data
public class AddGroupMemberDTO {

    @NotNull(message = "用户组ID不能为空")
    private Long groupId;

    @NotNull(message = "角色ID不能为空")
    private Long roleId;
}
