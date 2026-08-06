package com.trackflow.workflow.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.common.converter.BaseConverter;
import com.trackflow.workflow.entity.TransitionAction;
import com.trackflow.workflow.vo.TransitionActionVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * TransitionAction 实体到 VO 的转换器。
 * <p>
 * 注意：MapStruct 抽象类生成的实现调用无参构造器，
 * 因此依赖注入只能使用 setter 或字段注入，无法使用构造器注入。
 * 此处保留 @Autowired 字段注入作为 MapStruct 规范用法。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Mapper(componentModel = "spring")
public abstract class TransitionActionConverter implements BaseConverter {

    @Autowired
    protected ObjectMapper objectMapper;

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "projectId", expression = "java(longToString(entity.getProjectId()))")
    @Mapping(target = "oldStatusId", expression = "java(longToString(entity.getOldStatusId()))")
    @Mapping(target = "newStatusId", expression = "java(longToString(entity.getNewStatusId()))")
    @Mapping(target = "createdBy", expression = "java(longToString(entity.getCreatedBy()))")
    @Mapping(target = "actionConfig", expression = "java(parseActionConfig(entity.getActionConfig()))")
    @Mapping(target = "oldStatusName", ignore = true)
    @Mapping(target = "newStatusName", ignore = true)
    @Mapping(target = "pathValid", ignore = true)
    public abstract TransitionActionVO toVO(TransitionAction entity);

    public abstract List<TransitionActionVO> toVOList(List<TransitionAction> entities);

    /**
     * 将 action_config JSON 字符串解析为 Map
     */
    @Named("parseActionConfig")
    protected Map<String, Object> parseActionConfig(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }
}
