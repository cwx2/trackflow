package com.trackflow.report.converter;

import com.trackflow.common.converter.BaseConverter;
import com.trackflow.report.entity.Dashboard;
import com.trackflow.report.entity.DashboardWidget;
import com.trackflow.report.vo.DashboardDetailVO;
import com.trackflow.report.vo.DashboardListVO;
import com.trackflow.report.vo.DashboardWidgetVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DashboardConverter extends BaseConverter {

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "ownerId", expression = "java(longToString(entity.getOwnerId()))")
    @Mapping(target = "ownerName", ignore = true)
    @Mapping(target = "widgetCount", ignore = true)
    DashboardListVO toListVO(Dashboard entity);

    List<DashboardListVO> toListVOList(List<Dashboard> entities);

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "ownerId", expression = "java(longToString(entity.getOwnerId()))")
    @Mapping(target = "ownerName", ignore = true)
    @Mapping(target = "widgets", ignore = true)
    DashboardDetailVO toDetailVO(Dashboard entity);

    @Mapping(target = "id", expression = "java(longToString(entity.getId()))")
    @Mapping(target = "dashboardId", expression = "java(longToString(entity.getDashboardId()))")
    @Mapping(target = "reportId", expression = "java(longToString(entity.getReportId()))")
    DashboardWidgetVO toWidgetVO(DashboardWidget entity);

    List<DashboardWidgetVO> toWidgetVOList(List<DashboardWidget> entities);
}
