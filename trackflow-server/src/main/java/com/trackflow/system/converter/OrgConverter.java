package com.trackflow.system.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.system.entity.Organization;
import com.trackflow.system.vo.OrgDetailVO;
import com.trackflow.system.vo.OrgVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrgConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "projectCount", ignore = true)
    OrgVO toVO(Organization entity);

    List<OrgVO> toVOList(List<Organization> entities);

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "projectCount", ignore = true)
    OrgDetailVO toDetailVO(Organization entity);
}
