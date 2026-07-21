package com.trackflow.project.vo;

import lombok.Data;

/**
 * 项目回收站设置 VO
 */
@Data
public class ProjectTrashSettingsVO {

    /** 回收站保留天数，0 表示永久保留 */
    private Integer trashRetentionDays;
}
