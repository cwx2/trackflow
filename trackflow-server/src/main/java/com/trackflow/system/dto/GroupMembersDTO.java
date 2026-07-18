package com.trackflow.system.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 用户组成员管理 DTO
 */
@Data
public class GroupMembersDTO {

    @NotEmpty(message = "用户 ID 列表不能为空")
    private List<Long> userIds;
}
