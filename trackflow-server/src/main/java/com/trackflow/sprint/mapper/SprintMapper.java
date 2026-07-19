package com.trackflow.sprint.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.vo.CompletionPreviewVO;
import com.trackflow.sprint.vo.SprintVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SprintMapper extends BaseMapper<Sprint> {

    /**
     * 查询项目的 Sprint 列表，带工单统计数据。
     * 使用 resultMap 直接映射为 SprintVO（Long→String ID 转换在 XML 中处理）。
     */
    List<SprintVO> selectSprintsWithStats(@Param("projectId") Long projectId);

    /**
     * 查询单个 Sprint，带工单统计数据。
     * 复用列表查询的统计逻辑，确保数据一致性。
     */
    SprintVO selectSprintWithStats(@Param("sprintId") Long sprintId);

    /**
     * 查询 Sprint 中未关闭的工单 ID 列表（通过 JOIN issue_status 判断 is_closed = false）
     */
    List<Long> selectOpenIssueIds(@Param("sprintId") Long sprintId);

    /**
     * 查询 Sprint 中未关闭的工单详细信息（用于完成预览）
     */
    List<CompletionPreviewVO.OpenIssueItem> selectOpenIssuesForCompletion(@Param("sprintId") Long sprintId);
}
