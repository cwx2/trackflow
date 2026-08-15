package com.trackflow.integration.dto;

import com.trackflow.common.model.PageQuery;
import com.trackflow.integration.entity.NotificationCategory;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

/**
 * 通知列表查询条件封装
 * <p>
 * 供 NotificationController.list 使用，替代 6 个松散的 @RequestParam。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationQuery extends PageQuery {

    /** 是否只查未读（默认 false） */
    private Boolean unreadOnly = false;

    /** 按通知分类过滤 */
    private NotificationCategory category;

    /** 按项目过滤 */
    private Long projectId;

    /** 按通知原因过滤（如 mention、assigned 等） */
    private String reason;

    @Override
    protected Set<String> allowedSortFields() {
        return Set.of("id", "created_at");
    }
}
