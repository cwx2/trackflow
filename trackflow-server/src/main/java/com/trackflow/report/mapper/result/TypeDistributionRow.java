package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 类型分布查询结果行
 * SQL 列：type_name, cnt
 */
@Data
public class TypeDistributionRow {
    private String typeName;
    private Long cnt;
}
