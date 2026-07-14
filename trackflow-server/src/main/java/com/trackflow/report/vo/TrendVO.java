package com.trackflow.report.vo;

import lombok.Data;
import java.util.List;

@Data
public class TrendVO {
    private List<String> dates;
    private List<Long> created;
    private List<Long> resolved;
}
