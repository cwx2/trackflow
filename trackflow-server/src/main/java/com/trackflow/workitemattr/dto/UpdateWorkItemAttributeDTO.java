package com.trackflow.workitemattr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 更新工作项属性
 */
@Data
public class UpdateWorkItemAttributeDTO {

    @Size(max = 100, message = "属性名称最多100字符")
    private String name;

    /** 值列表（全量更新：传入新的完整列表） */
    private List<ValueItem> values;

    @Data
    public static class ValueItem {
        private String id;  // 已有值传 ID，新增值不传
        @NotBlank(message = "值名称不能为空")
        @Size(max = 100, message = "值名称最多100字符")
        private String name;
        private String color;
    }
}
