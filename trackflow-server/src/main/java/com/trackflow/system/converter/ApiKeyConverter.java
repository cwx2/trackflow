package com.trackflow.system.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.system.entity.ApiKey;
import com.trackflow.system.vo.ApiKeyVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ApiKeyConverter extends BaseConverter {
    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "userId", expression = "java(longToString(entity.getUserId()))")
    ApiKeyVO toVO(ApiKey entity);
    List<ApiKeyVO> toVOList(List<ApiKey> entities);
}
