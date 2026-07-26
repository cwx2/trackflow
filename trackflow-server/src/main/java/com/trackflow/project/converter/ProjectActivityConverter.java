package com.trackflow.project.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.project.entity.ProjectActivity;
import com.trackflow.project.vo.ProjectActivityVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 项目活动日志转换器
 * <p>
 * 将 {@link ProjectActivity} 实体转换为 {@link ProjectActivityVO}，
 * 由 MapStruct 编译时生成实现类。
 * userName 和 targetUserName 需要在 Service 层填充，此处 ignore。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Mapper(componentModel = "spring")
public interface ProjectActivityConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "projectId", expression = "java(longToString(entity.getProjectId()))")
    @Mapping(target = "userId", expression = "java(longToString(entity.getUserId()))")
    @Mapping(target = "targetUserId", expression = "java(longToString(entity.getTargetUserId()))")
    @Mapping(target = "userName", ignore = true)
    @Mapping(target = "targetUserName", ignore = true)
    ProjectActivityVO toVO(ProjectActivity entity);

    List<ProjectActivityVO> toVOList(List<ProjectActivity> entities);
}
