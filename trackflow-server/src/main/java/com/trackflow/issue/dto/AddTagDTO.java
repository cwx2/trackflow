package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddTagDTO {
    @NotNull(message = "标签ID不能为空")
    private Long tagId;
}
