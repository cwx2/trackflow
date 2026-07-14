package com.trackflow.report.vo;

import lombok.Data;
import java.util.List;

@Data
public class StatusDistributionVO {
    private List<StatusItem> items;
    private int total;

    @Data
    public static class StatusItem {
        private String name;
        private long value;
        private String color;
        private String category;
    }
}
