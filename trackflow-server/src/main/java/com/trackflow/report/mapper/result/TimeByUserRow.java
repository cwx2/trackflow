package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 按人员分组工时查询结果行
 * SQL 列：user_id, user_name, total_minutes
 */
@Data
public class TimeByUserRow {
    private Long userId;
    private String userName;
    private Integer totalMinutes;
}
