package com.trackflow.issue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.issue.entity.Issue;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IssueMapper extends BaseMapper<Issue> {
}
