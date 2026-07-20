package com.trackflow.report.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * 仪表盘共享配置 DTO
 */
@Data
public class ShareDashboardDTO {

    @NotNull(message = "共享对象列表不能为空")
    @Valid
    private List<ShareTarget> targets;

    @Data
    public static class ShareTarget {
        /** 共享目标类型：user / group */
        @NotNull(message = "目标类型不能为空")
        @Pattern(regexp = "^(user|group)$", message = "目标类型只能是 user 或 group")
        private String targetType;

        /** 共享目标 ID */
        @NotNull(message = "目标 ID 不能为空")
        private Long targetId;

        /** 权限级别：view / edit，默认 view */
        @Pattern(regexp = "^(view|edit)$", message = "权限级别只能是 view 或 edit")
        private String permission = "view";
    }
}
