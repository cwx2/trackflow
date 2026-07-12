package com.trackflow.sprint.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.sprint.entity.Sprint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface SprintMapper extends BaseMapper<Sprint> {

    /**
     * 查询项目的 Sprint 列表，带工单统计数据
     */
    List<Map<String, Object>> selectSprintsWithStats(@Param("projectId") Long projectId);
}
