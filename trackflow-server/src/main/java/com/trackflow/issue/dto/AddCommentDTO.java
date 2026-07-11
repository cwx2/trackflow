package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddCommentDTO {
    @NotBlank(message = "评论内容不能为空")
    private String content;
}
