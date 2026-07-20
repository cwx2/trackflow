package com.trackflow.issuetemplate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.issuetemplate.entity.IssueTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单模板 Mapper
 */
@Mapper
public interface IssueTemplateMapper extends BaseMapper<IssueTemplate> {
}
