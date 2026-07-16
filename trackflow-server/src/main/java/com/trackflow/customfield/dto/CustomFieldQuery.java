package com.trackflow.customfield.dto;

import com.trackflow.common.model.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class CustomFieldQuery extends PageQuery {

    private String fieldFormat;
    private String keyword;

    @Override
    protected Set<String> allowedSortFields() {
        return Set.of(
                "id", "name", "field_format", "position", "is_required",
                "created_at", "updated_at"
        );
    }
}
