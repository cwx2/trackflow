package com.trackflow.timeentry.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.timeentry.entity.TimeEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface TimeEntryMapper extends BaseMapper<TimeEntry> {

    /**
     * 查询工时条目，JOIN issue 表获取 issueKey
     */
    List<Map<String, Object>> selectEntriesWithIssueKey(
            @Param("userId") Long userId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );
}
