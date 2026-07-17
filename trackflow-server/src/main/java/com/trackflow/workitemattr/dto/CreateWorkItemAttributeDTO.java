package com.trackflow.workitemattr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建工作项属性
 */
@Data
public class CreateWorkItemAttributeDTO {

    @NotBlank(message = "属性名称不能为空")
    @Size(max = 100, message = "属性名称最多100字符")
    private String name;

    /** 初始值列表（可选，创建时可同时添加值） */
    private List<ValueItem> values;

    @Data
    public static class ValueItem {
        @NotBlank(message = "值名称不能为空")
        @Size(max = 100, message = "值名称最多100字符")
        private String name;
        private String color;
    }
}
