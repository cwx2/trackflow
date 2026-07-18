package com.trackflow.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.integration.entity.WebhookLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WebhookLogMapper extends BaseMapper<WebhookLog> {
}
