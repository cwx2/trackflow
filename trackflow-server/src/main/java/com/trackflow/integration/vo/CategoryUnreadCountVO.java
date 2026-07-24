package com.trackflow.integration.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 各分类未读通知计数 VO（用于标签页 badge 展示）
 */
@Data
@NoArgsConstructor
public class CategoryUnreadCountVO {

    /** 全部未读数 */
    private long all;

    /** @提及未读数 */
    private long mention;

    /** 订阅更新未读数 */
    private long subscription;

    /** 系统通知未读数 */
    private long system;
}
