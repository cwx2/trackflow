package com.trackflow.project.dto;

import com.trackflow.common.model.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectQuery extends PageQuery {
    private String keyword;
    private String status;

    @Override
    protected Set<String> allowedSortFields() {
        return Set.of(
                "id", "name", "key", "status", "created_at", "updated_at"
        );
    }
}
