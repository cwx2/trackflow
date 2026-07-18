package com.trackflow.board.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 看板设置批量保存 DTO。
 * <p>
 * 将所有看板配置（列设置、卡片、泳道、列合并、基本设置）合并为一次请求，
 * 统一做乐观锁版本检查，避免 5 个独立请求的并发控制问题。
 */
@Data
public class SaveBoardSettingsDTO {

    /** 乐观锁版本号（从 GET 响应的 configVersion 获取，用于并发冲突检测）。null 表示跳过检查（向后兼容）。 */
    private Integer configVersion;

    /** 列配置（必填） */
    @NotNull(message = "列配置不能为空")
    @Valid
    private UpdateBoardColumnsDTO columns;

    /** 卡片配置（必填） */
    @NotNull(message = "卡片配置不能为空")
    @Valid
    private UpdateBoardCardConfigDTO cardConfig;

    /** 泳道配置（必填） */
    @NotNull(message = "泳道配置不能为空")
    @Valid
    private UpdateBoardSwimlaneConfigDTO swimlaneConfig;

    /** 列合并配置（必填） */
    @NotNull(message = "列合并配置不能为空")
    @Valid
    private UpdateBoardColumnMergeDTO columnMerges;

    /** 基本设置（必填） */
    @NotNull(message = "基本设置不能为空")
    @Valid
    private UpdateBoardGeneralConfigDTO generalConfig;
}
