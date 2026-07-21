package com.trackflow.issue.dto;

import lombok.Data;

import java.util.List;

/**
 * 更新附件可见性 DTO
 */
@Data
public class UpdateAttachmentVisibilityDTO {

    /**
     * 可见组 ID 列表。null 或空列表表示公开（移除可见性限制）
     */
    private List<Long> visibleToGroupIds;
}
