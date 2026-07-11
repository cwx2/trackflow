package com.trackflow.query.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ExecuteQueryDTO {

    private List<Map<String, Object>> filters;
    private List<String> columns;
    private List<Map<String, String>> sortCriteria;
    private String groupBy;
    private Integer page;
    private Integer pageSize;
}
