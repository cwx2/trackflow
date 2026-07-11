package com.trackflow.customfield.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CustomFieldDefinitionVO {
    private String id;
    private String name;
    private String fieldFormat;
    private Boolean isRequired;
    private Boolean isForAll;
    private String defaultValue;
    private Integer minLength;
    private Integer maxLength;
    private String regexp;
    private Integer position;
    private List<CustomFieldOptionVO> options;
    private List<String> projectIds;
    private List<String> issueTypes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
