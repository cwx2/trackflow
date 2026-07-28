package com.trackflow.issue.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

import java.util.List;

/**
 * 工单添加标签 DTO - 支持单个或批量添加
 *
 * <p>支持两种传参方式：
 * <ul>
 *   <li>单个标签：{"tagId": "1002"}</li>
 *   <li>批量标签：{"tagIds": ["1002", "1005", "1008"]}</li>
 * </ul>
 * 两者至少传一个，如果同时传入则 tagIds 优先。
 */
@Data
public class AddTagDTO {

    /**
     * 单个标签 ID（向后兼容）
     */
    private Long tagId;

    /**
     * 标签 ID 列表（批量添加）
     */
    private List<Long> tagIds;

    @AssertTrue(message = "tagId 或 tagIds 至少传入一个")
    private boolean isValid() {
        return tagId != null || (tagIds != null && !tagIds.isEmpty());
    }

    /**
     * 获取最终的标签 ID 列表（合并 tagId 和 tagIds）
     */
    public List<Long> getEffectiveTagIds() {
        if (tagIds != null && !tagIds.isEmpty()) {
            return tagIds;
        }
        if (tagId != null) {
            return List.of(tagId);
        }
        return List.of();
    }
}
