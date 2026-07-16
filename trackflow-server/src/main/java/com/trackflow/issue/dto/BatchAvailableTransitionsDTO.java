package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量获取可用状态转换请求 DTO
 */
@Data
public class BatchAvailableTransitionsDTO {

    /**
     * 要查询可用转换的 Issue ID 列表
     */
    @NotEmpty(message = "工单列表不能为空")
    @Size(max = 50, message = "批量查询最多支持 50 个工单")
    private List<Long> issueIds;
}
