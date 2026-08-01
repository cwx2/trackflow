package com.trackflow.automation.execution.dto;

import lombok.Data;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ExecuteWorkflowDTO {
    private Map<String, Object> inputs = new LinkedHashMap<>();
}
