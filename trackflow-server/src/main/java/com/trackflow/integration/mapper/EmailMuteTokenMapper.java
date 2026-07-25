package com.trackflow.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.integration.entity.EmailMuteToken;
import org.apache.ibatis.annotations.Mapper;

/**
 * 邮件静音 token Mapper
 */
@Mapper
public interface EmailMuteTokenMapper extends BaseMapper<EmailMuteToken> {
}
