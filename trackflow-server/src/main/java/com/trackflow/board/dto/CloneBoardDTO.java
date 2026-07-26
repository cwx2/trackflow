package com.trackflow.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 克隆看板请求 DTO。
 * <p>
 * 克隆看板会以当前项目为模板创建新项目，并将所有看板配置
 * （列、卡片字段、泳道、图表、基本设置）复制到新项目中。
 * 访问权限重置为默认（仅克隆者可查看和编辑）。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Data
public class CloneBoardDTO {

    /**
     * 原看板所在项目 ID（源项目）。
     */
    @jakarta.validation.constraints.NotNull(message = "源项目ID不能为空")
    private Long sourceProjectId;

    /**
     * 新看板名称（即新项目名称）。
     * 默认为原项目名 + " (Copy)"。
     */
    @NotBlank(message = "新看板名称不能为空")
    @Size(max = 100, message = "名称不能超过100字")
    private String newName;

    /**
     * 新项目标识（Key），需唯一。
     * 默认由前端基于原项目 Key 自动生成。
     */
    @NotBlank(message = "项目标识不能为空")
    @Size(min = 1, max = 10, message = "项目标识长度为1-10位")
    @Pattern(regexp = "^[A-Z0-9]+$", message = "项目标识只能包含大写字母和数字")
    private String newKey;
}
