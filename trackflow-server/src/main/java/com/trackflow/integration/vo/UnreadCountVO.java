package com.trackflow.integration.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 未读通知计数 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnreadCountVO {

    /** 未读通知总数 */
    private long count;
}
