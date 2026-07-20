package com.trackflow.customfield.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 选项值排序 DTO。
 * 传入按新顺序排列的选项 ID 列表，后端更新各选项的 position 字段。
 */
@Data
public class ReorderOptionsDTO {

    /** 按新顺序排列的选项 ID 列表 */
    @NotEmpty(message = "选项 ID 列表不能为空")
    private List<Long> optionIds;
}
