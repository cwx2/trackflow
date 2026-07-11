package com.trackflow.project.dto;

import com.trackflow.common.model.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectQuery extends PageQuery {
    private String keyword;
    private String status;
}
