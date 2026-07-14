package com.trackflow.report.vo;

import lombok.Data;
import java.util.List;

@Data
public class WorkloadVO {
    private List<WorkloadItem> items;
    private int total;

    @Data
    public static class WorkloadItem {
        private String name;
        private long value;
        private long done;
        private long inProgress;
    }
}
