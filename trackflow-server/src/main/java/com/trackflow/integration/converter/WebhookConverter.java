package com.trackflow.integration.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.integration.entity.Webhook;
import com.trackflow.integration.entity.WebhookLog;
import com.trackflow.integration.vo.WebhookLogVO;
import com.trackflow.integration.vo.WebhookVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface WebhookConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "projectId", expression = "java(longToString(entity.getProjectId()))")
    WebhookVO toVO(Webhook entity);

    List<WebhookVO> toVOList(List<Webhook> entities);

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "webhookId", expression = "java(longToString(entity.getWebhookId()))")
    WebhookLogVO toLogVO(WebhookLog entity);

    List<WebhookLogVO> toLogVOList(List<WebhookLog> entities);
}
