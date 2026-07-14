package com.trackflow.report.vo;

import lombok.Data;

@Data
public class OverviewVO {
    private long total;
    private long open;
    private long closed;
    private long unassigned;
    private long overdue;
    private long completionRate;
}
