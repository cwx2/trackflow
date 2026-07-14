package com.trackflow.report.vo;

import lombok.Data;
import java.util.List;

@Data
public class BurndownVO {
    private List<String> dates;
    private List<Double> ideal;
    private List<Long> actual;
    private String sprintName;
    private int totalIssues;
}
