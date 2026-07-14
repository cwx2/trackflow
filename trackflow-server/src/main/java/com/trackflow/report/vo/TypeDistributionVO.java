package com.trackflow.report.vo;

import lombok.Data;
import java.util.List;

@Data
public class TypeDistributionVO {
    private List<TypeItem> items;
    private int total;

    @Data
    public static class TypeItem {
        private String name;
        private long value;
        private String color;
    }
}
