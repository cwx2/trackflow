package com.trackflow.query.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class UpdateQueryDTO {

    @Size(max = 200, message = "查询名称不能超过200字符")
    private String name;

    private Boolean shared;
    private Boolean pinned;
    private String folder;
    private List<Map<String, Object>> filters;
    private List<String> columns;
    private List<Map<String, String>> sortCriteria;
    private String groupBy;
    private Integer sortOrder;

    @Size(max = 50, message = "图标不能超过50字符")
    private String icon;
}
