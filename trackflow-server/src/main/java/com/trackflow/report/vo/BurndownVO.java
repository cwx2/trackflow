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
    /** 计算模式: "issue_count" / "estimation" / "work_items" */
    private String mode;
}
