package com.trackflow.sprint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateSprintDTO {

    @NotBlank(message = "Sprint名称不能为空")
    @Size(max = 200)
    private String name;

    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;
}
