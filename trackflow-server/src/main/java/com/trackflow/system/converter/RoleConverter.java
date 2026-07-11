package com.trackflow.system.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.vo.RoleVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RoleConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    RoleVO toVO(SysRole entity);

    List<RoleVO> toVOList(List<SysRole> entities);
}
