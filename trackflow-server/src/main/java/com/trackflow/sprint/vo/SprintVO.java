package com.trackflow.sprint.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SprintVO {
    private String id;
    private String projectId;
    private String name;
    private String goal;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime createdAt;
}
