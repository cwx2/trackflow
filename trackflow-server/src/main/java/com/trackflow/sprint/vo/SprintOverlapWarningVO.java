package com.trackflow.sprint.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Sprint 日期重叠警告信息 VO。
 * 当创建/编辑 Sprint 检测到日期重叠时，通过 BusinessException.data 返回给前端。
 */
@Data
public class SprintOverlapWarningVO {

    private List<OverlappingSprint> overlappingSprints;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverlappingSprint {
        private String name;
        private String startDate;
        private String endDate;
        private String status;
    }
}
