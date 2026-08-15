package com.trackflow.timeentry.dto;

import com.trackflow.common.model.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Set;

/**
 * 工时记录列表查询条件封装
 * <p>
 * 供 TimeEntryController.list 使用，替代 6 个松散的 @RequestParam。
 * {@code startDate} / {@code endDate} 为必填，通过 @DateTimeFormat 支持 ISO 日期格式。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TimeEntryQuery extends PageQuery {

    /** 查询目标用户 ID（可选，不传则查当前用户） */
    private Long userId;

    /** 起始日期（含，必填） */
    @NotNull(message = "startDate 不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    /** 结束日期（含，必填） */
    @NotNull(message = "endDate 不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    /** 按项目过滤（可选） */
    private Long projectId;

    /** 工作类型属性值 ID（对应 work_item_attribute_value.id，可选） */
    private Long activityId;

    @Override
    protected Set<String> allowedSortFields() {
        return Set.of("id", "work_date", "created_at", "duration_minutes");
    }
}
