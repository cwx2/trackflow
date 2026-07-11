package com.trackflow.query.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreateQueryDTO {

    @NotBlank(message = "查询名称不能为空")
    @Size(max = 200, message = "查询名称不能超过200字符")
    private String name;

    private Long projectId;
    private Boolean shared;
    private Boolean pinned;
    private String folder;
    private List<Map<String, Object>> filters;
    private List<String> columns;
    private List<Map<String, String>> sortCriteria;
    private String groupBy;
}
