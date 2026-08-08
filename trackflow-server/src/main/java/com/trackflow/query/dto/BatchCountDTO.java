package com.trackflow.query.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量查询计数请求 DTO
 */
@Data
public class BatchCountDTO {

    @NotEmpty(message = "queryIds 不能为空")
    private List<Long> queryIds;
}
