package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UpdateCommentDTO {
    @NotBlank(message = "评论内容不能为空")
    private String content;

    /**
     * 可见性限制的组 ID 列表。为 null 表示不修改可见性；空列表表示移除限制（全体可见）。
     */
    private List<Long> visibleToGroupIds;
}
