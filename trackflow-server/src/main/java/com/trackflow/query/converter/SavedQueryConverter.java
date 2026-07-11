package com.trackflow.query.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.query.entity.SavedQuery;
import com.trackflow.query.vo.SavedQueryVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SavedQueryConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "projectId", expression = "java(longToString(entity.getProjectId()))")
    @Mapping(target = "userId", expression = "java(longToString(entity.getUserId()))")
    SavedQueryVO toVO(SavedQuery entity);

    List<SavedQueryVO> toVOList(List<SavedQuery> entities);
}
