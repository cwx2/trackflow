package com.trackflow.board.vo;

import lombok.Data;

/**
 * 克隆看板结果 VO。
 * 返回新创建的看板（项目）信息，用于前端跳转。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Data
public class CloneBoardResultVO {

    /** 新项目（看板）ID */
    private String projectId;

    /** 新看板名称 */
    private String name;

    /** 新项目 Key */
    private String projectKey;
}
