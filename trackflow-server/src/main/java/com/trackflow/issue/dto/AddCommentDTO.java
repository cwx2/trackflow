package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AddCommentDTO {
    @NotBlank(message = "评论内容不能为空")
    private String content;

    /**
     * 可见性限制的组 ID 列表。为 null 或空列表表示全体可见。
     */
    private List<Long> visibleToGroupIds;
}
