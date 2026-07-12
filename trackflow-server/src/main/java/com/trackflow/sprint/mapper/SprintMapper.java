package com.trackflow.sprint.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.sprint.entity.Sprint;
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
}
