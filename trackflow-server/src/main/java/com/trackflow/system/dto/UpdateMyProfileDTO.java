package com.trackflow.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户编辑个人资料 DTO
 * 对应 PATCH /api/v1/auth/me/profile
 */
@Data
public class UpdateMyProfileDTO {

    @NotBlank(message = "全名不能为空")
    @Size(max = 100, message = "全名不能超过100字")
    private String displayName;

    @Size(max = 50, message = "时区不能超过50字")
    private String timezone;

    @Size(max = 10, message = "语言不能超过10字")
    private String language;

    @Size(max = 20, message = "日期格式不能超过20字")
    private String dateFormat;

    @Size(max = 10, message = "每周第一天不能超过10字")
    private String firstDayOfWeek;
}
