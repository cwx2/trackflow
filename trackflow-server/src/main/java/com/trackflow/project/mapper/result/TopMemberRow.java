package com.trackflow.project.mapper.result;

import lombok.Data;

/**
 * 项目 Top N 成员结果行。
 * <p>
 * 对应 SQL: selectTopMembersByProjects — 使用窗口函数获取每个项目最早加入的前 N 名成员。
 */
@Data
public class TopMemberRow {
    private Long projectId;
    private Long userId;
    private String displayName;
}
