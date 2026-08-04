package com.trackflow.system.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.vo.UserSummaryVO;
import com.trackflow.system.vo.UserVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "orgId", expression = "java(longToString(entity.getOrgId()))")
    @Mapping(target = "bannedByName", ignore = true)
    UserVO toVO(SysUser entity);

    List<UserVO> toVOList(List<SysUser> entities);

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    UserSummaryVO toSummaryVO(SysUser entity);
}
