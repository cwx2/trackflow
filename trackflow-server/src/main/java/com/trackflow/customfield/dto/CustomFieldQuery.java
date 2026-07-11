package com.trackflow.customfield.dto;

import com.trackflow.common.model.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CustomFieldQuery extends PageQuery {

    private String fieldFormat;
    private String keyword;
}
