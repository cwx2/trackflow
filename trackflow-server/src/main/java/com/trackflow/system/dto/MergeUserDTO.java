package com.trackflow.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 合并用户请求 DTO
 * <p>
 * 将源用户（sourceUserId）的所有数据合并到目标用户。
 * 合并后源用户将被标记为已合并状态，不再可用。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Data
public class MergeUserDTO {

    @NotNull(message = "源用户ID不能为空")
    private Long sourceUserId;
}
