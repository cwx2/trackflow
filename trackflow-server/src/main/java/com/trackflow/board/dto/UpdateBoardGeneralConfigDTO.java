package com.trackflow.board.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 更新看板基本设置 DTO。
 */
@Data
public class UpdateBoardGeneralConfigDTO {

    /** 看板名称（可为空，为空时前端使用默认名） */
    @Size(max = 100, message = "看板名称不能超过 100 个字符")
    private String name;

    /** 可查看看板的角色代码列表 */
    @NotNull(message = "查看权限角色列表不能为空")
    @Size(min = 1, message = "至少需要一个角色可以查看看板")
    private List<String> canViewRoles;

    /** 可编辑看板设置的角色代码列表 */
    @NotNull(message = "编辑权限角色列表不能为空")
    @Size(min = 1, message = "至少需要一个角色可以编辑看板设置")
    private List<String> canEditRoles;
}
