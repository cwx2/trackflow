package com.trackflow.report.vo;

import lombok.Data;
import java.util.List;

@Data
public class PriorityDistributionVO {
    private List<String> labels;
    private List<Long> data;
    private List<String> colors;
    private int total;
}
