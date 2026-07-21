package com.trackflow.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.issue.entity.IssueKeyHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单 Key 变更历史 Mapper - 用于旧 Issue Key 重定向查询
 *
 * @author TrackFlow
 * @since 1.0
 */
@Mapper
public interface IssueKeyHistoryMapper extends BaseMapper<IssueKeyHistory> {
}
