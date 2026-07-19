package com.trackflow.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.report.converter.DashboardConverter;
import com.trackflow.report.dto.CreateDashboardDTO;
import com.trackflow.report.dto.CreateWidgetDTO;
import com.trackflow.report.dto.UpdateDashboardDTO;
import com.trackflow.report.dto.UpdateLayoutDTO;
import com.trackflow.report.dto.UpdateWidgetDTO;
import com.trackflow.report.entity.Dashboard;
import com.trackflow.report.entity.DashboardWidget;
import com.trackflow.report.entity.WidgetType;
import com.trackflow.report.mapper.DashboardMapper;
import com.trackflow.report.mapper.DashboardWidgetMapper;
import com.trackflow.report.vo.DashboardDetailVO;
import com.trackflow.report.vo.DashboardListVO;
import com.trackflow.report.vo.DashboardWidgetVO;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.entity.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报表仪表盘 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomDashboardService {

    private final DashboardMapper dashboardMapper;
    private final DashboardWidgetMapper widgetMapper;
    private final DashboardConverter dashboardConverter;
    private final SysUserMapper sysUserMapper;

    /**
     * 获取仪表盘列表（当前用户拥有的 + 共享的）
     */
    public List<DashboardListVO> list(Long userId) {
        LambdaQueryWrapper<Dashboard> wrapper = new LambdaQueryWrapper<Dashboard>()
                .eq(Dashboard::getOwnerId, userId)
                .or()
                .eq(Dashboard::getShared, true)
                .orderByDesc(Dashboard::getUpdatedAt);

        List<Dashboard> dashboards = dashboardMapper.selectList(wrapper);
        List<DashboardListVO> voList = dashboardConverter.toListVOList(dashboards);

        // 填充 owner 姓名 + widget 数量
        if (!voList.isEmpty()) {
            Set<Long> ownerIds = dashboards.stream().map(Dashboard::getOwnerId).collect(Collectors.toSet());
            Map<Long, String> ownerNames = getOwnerNameMap(ownerIds);

            List<Long> dashboardIds = dashboards.stream().map(Dashboard::getId).collect(Collectors.toList());
            Map<Long, Long> widgetCounts = getWidgetCountMap(dashboardIds);

            for (int i = 0; i < voList.size(); i++) {
                Dashboard entity = dashboards.get(i);
                DashboardListVO vo = voList.get(i);
                vo.setOwnerName(ownerNames.getOrDefault(entity.getOwnerId(), ""));
                vo.setWidgetCount(widgetCounts.getOrDefault(entity.getId(), 0L).intValue());
            }
        }

        return voList;
    }

    /**
     * 获取仪表盘详情（含所有 Widget）
     */
    public DashboardDetailVO getDetail(Long dashboardId, Long userId) {
        Dashboard dashboard = dashboardMapper.selectById(dashboardId);
        if (dashboard == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "仪表盘不存在");
        }

        // 权限检查：owner 或共享仪表盘可查看
        if (!dashboard.getOwnerId().equals(userId) && !Boolean.TRUE.equals(dashboard.getShared())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该仪表盘");
        }

        DashboardDetailVO vo = dashboardConverter.toDetailVO(dashboard);
        vo.setLayoutVersion(dashboard.getLayoutVersion() != null ? dashboard.getLayoutVersion() : 0);

        // 填充 owner 名称
        SysUser owner = sysUserMapper.selectById(dashboard.getOwnerId());
        vo.setOwnerName(owner != null ? owner.getDisplayName() : "");

        // 查询 widgets
        LambdaQueryWrapper<DashboardWidget> widgetWrapper = new LambdaQueryWrapper<DashboardWidget>()
                .eq(DashboardWidget::getDashboardId, dashboardId)
                .orderByAsc(DashboardWidget::getSortOrder)
                .orderByAsc(DashboardWidget::getPositionY)
                .orderByAsc(DashboardWidget::getPositionX);
        List<DashboardWidget> widgets = widgetMapper.selectList(widgetWrapper);
        vo.setWidgets(dashboardConverter.toWidgetVOList(widgets));

        return vo;
    }

    /**
     * 创建仪表盘
     */
    @Transactional
    public DashboardDetailVO create(CreateDashboardDTO dto, Long userId) {
        Dashboard dashboard = new Dashboard();
        dashboard.setName(dto.getName());
        dashboard.setDescription(dto.getDescription());
        dashboard.setOwnerId(userId);
        dashboard.setShared(dto.getShared() != null ? dto.getShared() : false);
        dashboard.setLayout("{}");

        dashboardMapper.insert(dashboard);
        log.info("Dashboard created: id={}, name={}, owner={}", dashboard.getId(), dashboard.getName(), userId);

        DashboardDetailVO vo = dashboardConverter.toDetailVO(dashboard);
        vo.setLayoutVersion(0);
        SysUser owner = sysUserMapper.selectById(userId);
        vo.setOwnerName(owner != null ? owner.getDisplayName() : "");
        vo.setWidgets(List.of());
        return vo;
    }

    /**
     * 更新仪表盘（名称/描述/共享）
     */
    @Transactional
    public DashboardDetailVO update(Long dashboardId, UpdateDashboardDTO dto, Long userId) {
        Dashboard dashboard = dashboardMapper.selectById(dashboardId);
        if (dashboard == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "仪表盘不存在");
        }
        if (!dashboard.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只有仪表盘创建者可以修改");
        }

        if (dto.getName() != null) {
            dashboard.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            dashboard.setDescription(dto.getDescription());
        }
        if (dto.getShared() != null) {
            dashboard.setShared(dto.getShared());
        }

        dashboardMapper.updateById(dashboard);
        return getDetail(dashboardId, userId);
    }

    /**
     * 删除仪表盘（只有 owner 可删除，级联删除 widget）
     */
    @Transactional
    public void delete(Long dashboardId, Long userId) {
        Dashboard dashboard = dashboardMapper.selectById(dashboardId);
        if (dashboard == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "仪表盘不存在");
        }
        if (!dashboard.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只有仪表盘创建者可以删除");
        }

        // 先删 widgets（DB 有 ON DELETE CASCADE，但显式删除更清晰）
        widgetMapper.delete(new LambdaQueryWrapper<DashboardWidget>()
                .eq(DashboardWidget::getDashboardId, dashboardId));
        dashboardMapper.deleteById(dashboardId);

        log.info("Dashboard deleted: id={}, owner={}", dashboardId, userId);
    }

    /**
     * 添加 Widget 到仪表盘
     */
    @Transactional
    public DashboardWidgetVO addWidget(Long dashboardId, CreateWidgetDTO dto, Long userId) {
        Dashboard dashboard = dashboardMapper.selectById(dashboardId);
        if (dashboard == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "仪表盘不存在");
        }
        if (!dashboard.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只有仪表盘创建者可以添加微件");
        }

        // 校验 widgetType 合法性
        if (!WidgetType.isValid(dto.getWidgetType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的微件类型: " + dto.getWidgetType() + "，允许值: " + WidgetType.allowedValues());
        }

        // 计算 sortOrder（当前最大 + 1）
        Long maxSort = widgetMapper.selectCount(new LambdaQueryWrapper<DashboardWidget>()
                .eq(DashboardWidget::getDashboardId, dashboardId));

        DashboardWidget widget = new DashboardWidget();
        widget.setDashboardId(dashboardId);
        widget.setWidgetType(dto.getWidgetType());
        widget.setTitle(dto.getTitle());
        widget.setConfig(dto.getConfig() != null ? dto.getConfig() : "{}");
        widget.setReportId(dto.getReportId());
        widget.setPositionX(dto.getPositionX() != null ? dto.getPositionX() : 0);
        widget.setPositionY(dto.getPositionY() != null ? dto.getPositionY() : maxSort.intValue() * 3);
        widget.setWidth(dto.getWidth() != null ? dto.getWidth() : 4);
        widget.setHeight(dto.getHeight() != null ? dto.getHeight() : 3);
        widget.setSortOrder(maxSort.intValue());

        widgetMapper.insert(widget);
        log.info("Widget added: id={}, dashboard={}, type={}", widget.getId(), dashboardId, dto.getWidgetType());

        return dashboardConverter.toWidgetVO(widget);
    }

    /**
     * 更新 Widget（配置/位置/大小）
     */
    @Transactional
    public DashboardWidgetVO updateWidget(Long dashboardId, Long widgetId, UpdateWidgetDTO dto, Long userId) {
        assertDashboardOwner(dashboardId, userId);

        DashboardWidget widget = widgetMapper.selectById(widgetId);
        if (widget == null || !widget.getDashboardId().equals(dashboardId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "微件不存在");
        }

        if (dto.getTitle() != null) widget.setTitle(dto.getTitle());
        if (dto.getConfig() != null) widget.setConfig(dto.getConfig());
        if (dto.getReportId() != null) {
            widget.setReportId(dto.getReportId());
        } else if (Boolean.TRUE.equals(dto.getClearReportId())) {
            widget.setReportId(null);
        }
        if (dto.getPositionX() != null) widget.setPositionX(dto.getPositionX());
        if (dto.getPositionY() != null) widget.setPositionY(dto.getPositionY());
        if (dto.getWidth() != null) widget.setWidth(dto.getWidth());
        if (dto.getHeight() != null) widget.setHeight(dto.getHeight());

        widgetMapper.updateById(widget);
        return dashboardConverter.toWidgetVO(widget);
    }

    /**
     * 删除 Widget
     */
    @Transactional
    public void deleteWidget(Long dashboardId, Long widgetId, Long userId) {
        assertDashboardOwner(dashboardId, userId);

        DashboardWidget widget = widgetMapper.selectById(widgetId);
        if (widget == null || !widget.getDashboardId().equals(dashboardId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "微件不存在");
        }

        widgetMapper.deleteById(widgetId);
        log.info("Widget deleted: id={}, dashboard={}", widgetId, dashboardId);
    }

    /**
     * 批量更新 Widget 位置（拖拽后保存布局）
     * 使用乐观锁防止并发覆盖，使用批量更新替代逐条 SQL。
     */
    @Transactional
    public void updateLayout(Long dashboardId, UpdateLayoutDTO dto, Long userId) {
        // 1. 权限校验 + 乐观锁校验
        Dashboard dashboard = dashboardMapper.selectById(dashboardId);
        if (dashboard == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "仪表盘不存在");
        }
        if (!dashboard.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只有仪表盘创建者可以操作");
        }

        // 乐观锁：前端传入的版本号必须与当前一致
        Integer currentVersion = dashboard.getLayoutVersion() != null ? dashboard.getLayoutVersion() : 0;
        if (!currentVersion.equals(dto.getVersion())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "布局已被其他操作修改，请刷新页面后重试");
        }

        // 2. 批量更新 widget 位置（Db.updateBatchById 一次批量执行，避免 N 次独立 SQL）
        List<DashboardWidget> updates = dto.getItems().stream().map(item -> {
            DashboardWidget w = new DashboardWidget();
            w.setId(item.getWidgetId());
            w.setPositionX(item.getPositionX());
            w.setPositionY(item.getPositionY());
            w.setWidth(item.getWidth());
            w.setHeight(item.getHeight());
            return w;
        }).toList();

        if (!updates.isEmpty()) {
            Db.updateBatchById(updates);
        }

        // 3. 版本号 +1
        LambdaUpdateWrapper<Dashboard> versionUpdate = new LambdaUpdateWrapper<Dashboard>()
                .eq(Dashboard::getId, dashboardId)
                .set(Dashboard::getLayoutVersion, currentVersion + 1);
        dashboardMapper.update(null, versionUpdate);

        log.info("Dashboard layout updated: dashboardId={}, items={}, version={}->{}",
                dashboardId, dto.getItems().size(), currentVersion, currentVersion + 1);
    }

    // ─── 私有方法 ────────────────────────────────────────

    private void assertDashboardOwner(Long dashboardId, Long userId) {
        Dashboard dashboard = dashboardMapper.selectById(dashboardId);
        if (dashboard == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "仪表盘不存在");
        }
        if (!dashboard.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只有仪表盘创建者可以操作");
        }
    }

    private Map<Long, String> getOwnerNameMap(Set<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
        return users.stream().collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName, (a, b) -> a));
    }

    private Map<Long, Long> getWidgetCountMap(List<Long> dashboardIds) {
        if (dashboardIds.isEmpty()) return Map.of();
        // 单条 GROUP BY 查询替代逐 ID 循环，避免 N+1
        List<Map<String, Object>> results = widgetMapper.selectMaps(
                new QueryWrapper<DashboardWidget>()
                        .select("dashboard_id", "COUNT(*) as cnt")
                        .in("dashboard_id", dashboardIds)
                        .groupBy("dashboard_id")
        );
        return results.stream().collect(Collectors.toMap(
                m -> ((Number) m.get("dashboard_id")).longValue(),
                m -> ((Number) m.get("cnt")).longValue()
        ));
    }
}
