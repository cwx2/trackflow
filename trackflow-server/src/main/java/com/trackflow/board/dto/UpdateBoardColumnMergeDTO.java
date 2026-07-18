package com.trackflow.board.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 更新看板列合并配置 DTO。
 * 全量替换：提交的 mergeGroups 代表最终状态。
 */
@Data
public class UpdateBoardColumnMergeDTO {
    /** 乐观锁版本号（从 GET 响应中获取，用于并发冲突检测） */
    private Integer configVersion;

    /** 合并组列表（空列表 = 取消所有合并） */
    @NotNull(message = "合并组列表不能为 null")
    @Valid
    private List<MergeGroupItem> mergeGroups;

    @Data
    public static class MergeGroupItem {
        /** 合并组标识（前端生成的 UUID 或自定义字符串） */
        @NotNull(message = "合并组 ID 不能为空")
        private String mergeGroupId;

        /** 合并后的列标题 */
        @NotNull(message = "合并标题不能为空")
        private String mergeTitle;

        /** 该组中合并的状态 ID 列表（至少 2 个才有意义） */
        @NotNull(message = "状态 ID 列表不能为空")
        private List<Long> statusIds;
    }
}
