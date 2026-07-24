package com.trackflow.integration.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 静音状态 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MuteStatusVO {

    /** 是否已静音 */
    private boolean muted;
}
