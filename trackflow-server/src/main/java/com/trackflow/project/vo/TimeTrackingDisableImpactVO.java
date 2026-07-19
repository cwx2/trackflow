package com.trackflow.project.vo;

import lombok.Data;

/**
 * 禁用项目时间追踪功能时的影响评估 VO
 */
@Data
public class TimeTrackingDisableImpactVO {

    /** 该项目当前的工时记录总数 */
    private int totalTimeEntries;

    /** 受影响的团队成员数 */
    private int affectedUsers;

    /** 正在进行的活跃计时器数量 */
    private int activeTimers;
}
