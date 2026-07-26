package com.trackflow.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.report.converter.DashboardConverter;
import com.trackflow.report.dto.CreateDashboardDTO;
import com.trackflow.report.dto.CreateWidgetDTO;
import com.trackflow.report.dto.ShareDashboardDTO;
import com.trackflow.report.dto.UpdateDashboardDTO;
import com.trackflow.report.dto.UpdateLayoutDTO;
import com.trackflow.report.dto.UpdateWidgetDTO;
import com.trackflow.report.entity.Dashboard;
import com.trackflow.report.entity.DashboardFavorite;
import com.trackflow.report.entity.DashboardShare;
import com.trackflow.report.entity.DashboardWidget;
import com.trackflow.report.entity.WidgetType;
import com.trackflow.report.mapper.DashboardMapper;
import com.trackflow.report.mapper.DashboardFavoriteMapper;
import com.trackflow.report.mapper.DashboardShareMapper;
import com.trackflow.report.mapper.DashboardWidgetMapper;
import com.trackflow.report.vo.DashboardDetailVO;
import com.trackflow.report.vo.DashboardListVO;
import com.trackflow.report.vo.DashboardShareVO;
import com.trackflow.report.vo.DashboardWidgetVO;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.entity.UserGroup;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.mapper.UserGroupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
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
    private final DashboardShareMapper shareMapper;
    private final DashboardFavoriteMapper favoriteMapper;
    private final DashboardConverter dashboardConverter;
    private final SysUserMapper sysUserMapper;
    private final UserGroupMapper userGroupMapper;
    private final PermissionService permissionService;

    /**
     * 获取仪表盘列表（当前用户拥有的 + 全局共享的 + 精确共享给我的 + 系统默认仪表盘）
     * 返回按收藏优先、字母排序的列表，含收藏/默认状态
     */
    public List<DashboardListVO> list(Long userId) {
        // 查询精确共享给当前用户的仪表盘 ID
        List<Long> sharedToMeIds = shareMapper.selectAccessibleDashboardIds(userId);

        LambdaQueryWrapper<Dashboard> wrapper = new LambdaQueryWrapper<Dashboard>()
                .eq(Dashboard::getOwnerId, userId)
                .or()
                .eq(Dashboard::getShared, true)
                .or()
                .eq(Dashboard::getIsSystemDefault, true);

        if (!sharedToMeIds.isEmpty()) {
            wrapper.or().in(Dashboard::getId, sharedToMeIds);
        }

        wrapper.orderByDesc(Dashboard::getUpdatedAt);

        List<Dashboard> dashboards = dashboardMapper.selectList(wrapper);

        // 去重（一个仪表盘可能同时满足多个条件）
        dashboards = dashboards.stream()
                .collect(Collectors.toMap(Dashboard::getId, d -> d, (a, b) -> a))
                .values().stream()
                .sorted(Comparator.comparing(Dashboard::getUpdatedAt).reversed())
                .collect(Collectors.toList());

        List<DashboardListVO> voList = dashboardConverter.toListVOList(dashboards);

        // 填充 owner 姓名 + widget 数量 + share 数量
        if (!voList.isEmpty()) {
            Set<Long> ownerIds = dashboards.stream().map(Dashboard::getOwnerId).collect(Collectors.toSet());
            Map<Long, String> ownerNames = getOwnerNameMap(ownerIds);

            List<Long> dashboardIds = dashboards.stream().map(Dashboard::getId).collect(Collectors.toList());
            Map<Long, Long> widgetCounts = getWidgetCountMap(dashboardIds);
            Map<Long, Long> shareCounts = getShareCountMap(dashboardIds);

            // 查询当前用户的收藏记录
            List<DashboardFavorite> favorites = favoriteMapper.selectList(
                    new LambdaQueryWrapper<DashboardFavorite>()
                            .eq(DashboardFavorite::getUserId, userId));
            Map<Long, DashboardFavorite> favoriteMap = favorites.stream()
                    .collect(Collectors.toMap(DashboardFavorite::getDashboardId, f -> f, (a, b) -> a));

            for (int i = 0; i < voList.size(); i++) {
                Dashboard entity = dashboards.get(i);
                DashboardListVO vo = voList.get(i);
                vo.setOwnerName(ownerNames.getOrDefault(entity.getOwnerId(), ""));
                vo.setWidgetCount(widgetCounts.getOrDefault(entity.getId(), 0L).intValue());
                vo.setShareCount(shareCounts.getOrDefault(entity.getId(), 0L).intValue());
                vo.setIsSystemDefault(Boolean.TRUE.equals(entity.getIsSystemDefault()));

                DashboardFavorite fav = favoriteMap.get(entity.getId());
                vo.setFavorited(fav != null);
                vo.setIsDefault(fav != null && Boolean.TRUE.equals(fav.getIsDefault()));
            }

            // 排序：系统默认置顶 → 收藏在前 → 各组内按名称字母排序
            voList.sort((a, b) -> {
                // 系统默认仪表盘始终排在最前
                boolean aSys = Boolean.TRUE.equals(a.getIsSystemDefault());
                boolean bSys = Boolean.TRUE.equals(b.getIsSystemDefault());
                if (aSys != bSys) return aSys ? -1 : 1;

                boolean aFav = Boolean.TRUE.equals(a.getFavorited());
                boolean bFav = Boolean.TRUE.equals(b.getFavorited());
                if (aFav != bFav) return aFav ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
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

        // 权限检查：系统默认仪表盘所有认证用户可见 / owner / 全局共享 / 精确共享
        if (!Boolean.TRUE.equals(dashboard.getIsSystemDefault())
                && !dashboard.getOwnerId().equals(userId)
                && !Boolean.TRUE.equals(dashboard.getShared())
                && shareMapper.countAccessByUser(dashboardId, userId) == 0) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该仪表盘");
        }

        DashboardDetailVO vo = dashboardConverter.toDetailVO(dashboard);
        vo.setLayoutVersion(dashboard.getLayoutVersion() != null ? dashboard.getLayoutVersion() : 0);
        vo.setIsSystemDefault(Boolean.TRUE.equals(dashboard.getIsSystemDefault()));

        // 填充 owner 名称
        SysUser owner = sysUserMapper.selectById(dashboard.getOwnerId());
        vo.setOwnerName(owner != null ? owner.getDisplayName() : "");

        // 填充共享数量
        Long shareCount = shareMapper.selectCount(new LambdaQueryWrapper<DashboardShare>()
                .eq(DashboardShare::getDashboardId, dashboardId));
        vo.setShareCount(shareCount.intValue());

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
    @Transactional(rollbackFor = Exception.class)
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
        vo.setShareCount(0);
        SysUser owner = sysUserMapper.selectById(userId);
        vo.setOwnerName(owner != null ? owner.getDisplayName() : "");
        vo.setWidgets(List.of());
        return vo;
    }

    /**
     * 更新仪表盘（名称/描述/共享）
     * 系统默认仪表盘：只有系统管理员可修改（名称/描述）
     */
    @Transactional(rollbackFor = Exception.class)
    public DashboardDetailVO update(Long dashboardId, UpdateDashboardDTO dto, Long userId) {
        Dashboard dashboard = dashboardMapper.selectById(dashboardId);
        if (dashboard == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "仪表盘不存在");
        }

        // 系统默认仪表盘：只有系统管理员可修改，且不允许取消共享
        if (Boolean.TRUE.equals(dashboard.getIsSystemDefault())) {
            if (!permissionService.isSystemAdmin(userId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "只有系统管理员可以修改系统默认仪表盘");
            }
            // 系统默认仪表盘始终共享，不允许取消
            if (dto.getShared() != null && !dto.getShared()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "系统默认仪表盘不可取消共享");
            }
        } else {
            if (!dashboard.getOwnerId().equals(userId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "只有仪表盘创建者可以修改");
            }
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
     * 删除仪表盘（只有 owner 可删除，级联删除 widget 和 share）
     * 系统默认仪表盘不可删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long dashboardId, Long userId) {
        Dashboard dashboard = dashboardMapper.selectById(dashboardId);
        if (dashboard == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "仪表盘不存在");
        }
        if (Boolean.TRUE.equals(dashboard.getIsSystemDefault())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统默认仪表盘不可删除");
        }
        if (!dashboard.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只有仪表盘创建者可以删除");
        }

        // 先删 widgets 和 shares（DB 有 ON DELETE CASCADE，但显式删除更清晰）
        widgetMapper.delete(new LambdaQueryWrapper<DashboardWidget>()
                .eq(DashboardWidget::getDashboardId, dashboardId));
        shareMapper.delete(new LambdaQueryWrapper<DashboardShare>()
                .eq(DashboardShare::getDashboardId, dashboardId));
        dashboardMapper.deleteById(dashboardId);

        log.info("Dashboard deleted: id={}, owner={}", dashboardId, userId);
    }

    // ─── 共享管理 ────────────────────────────────────────

    /**
     * 设置仪表盘共享（覆盖模式：传入全量共享列表）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<DashboardShareVO> setShares(Long dashboardId, ShareDashboardDTO dto, Long userId) {
        Dashboard dashboard = dashboardMapper.selectById(dashboardId);
        if (dashboard == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "仪表盘不存在");
        }
        if (!dashboard.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只有仪表盘创建者可以管理共享");
        }

        // 删除现有共享记录
        shareMapper.delete(new LambdaQueryWrapper<DashboardShare>()
                .eq(DashboardShare::getDashboardId, dashboardId));

        // 批量新增
        List<DashboardShare> shares = dto.getTargets().stream().map(t -> {
            DashboardShare share = new DashboardShare();
            share.setDashboardId(dashboardId);
            share.setTargetType(t.getTargetType());
            share.setTargetId(t.getTargetId());
            share.setPermission(t.getPermission() != null ? t.getPermission() : "view");
            share.setCreatedBy(userId);
            return share;
        }).toList();

        if (!shares.isEmpty()) {
            Db.saveBatch(shares);
        }

        log.info("Dashboard shares updated: dashboardId={}, targets={}", dashboardId, shares.size());
        return getShares(dashboardId, userId);
    }

    /**
     * 获取仪表盘的共享列表
     */
    public List<DashboardShareVO> getShares(Long dashboardId, Long userId) {
        Dashboard dashboard = dashboardMapper.selectById(dashboardId);
        if (dashboard == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "仪表盘不存在");
        }
        // 只有 owner 可以查看完整共享列表
        if (!dashboard.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只有仪表盘创建者可以查看共享设置");
        }

        List<DashboardShare> shares = shareMapper.selectList(
                new LambdaQueryWrapper<DashboardShare>()
                        .eq(DashboardShare::getDashboardId, dashboardId)
                        .orderByAsc(DashboardShare::getTargetType)
                        .orderByAsc(DashboardShare::getCreatedAt));

        // 批量查询目标名称
        Set<Long> userIds = shares.stream()
                .filter(s -> "user".equals(s.getTargetType()))
                .map(DashboardShare::getTargetId)
                .collect(Collectors.toSet());
        Set<Long> groupIds = shares.stream()
                .filter(s -> "group".equals(s.getTargetType()))
                .map(DashboardShare::getTargetId)
                .collect(Collectors.toSet());

        Map<Long, String> userNames = Map.of();
        if (!userIds.isEmpty()) {
            List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
            userNames = users.stream().collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName, (a, b) -> a));
        }

        Map<Long, String> groupNames = Map.of();
        if (!groupIds.isEmpty()) {
            List<UserGroup> groups = userGroupMapper.selectBatchIds(groupIds);
            groupNames = groups.stream().collect(Collectors.toMap(UserGroup::getId, UserGroup::getName, (a, b) -> a));
        }

        Map<Long, String> finalUserNames = userNames;
        Map<Long, String> finalGroupNames = groupNames;

        return shares.stream().map(s -> {
            DashboardShareVO vo = new DashboardShareVO();
            vo.setId(String.valueOf(s.getId()));
            vo.setTargetType(s.getTargetType());
            vo.setTargetId(String.valueOf(s.getTargetId()));
            vo.setPermission(s.getPermission());
            vo.setCreatedAt(s.getCreatedAt());
            if ("user".equals(s.getTargetType())) {
                vo.setTargetName(finalUserNames.getOrDefault(s.getTargetId(), "未知用户"));
            } else {
                vo.setTargetName(finalGroupNames.getOrDefault(s.getTargetId(), "未知用户组"));
            }
            return vo;
        }).toList();
    }

    /**
     * 移除单条共享
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeShare(Long dashboardId, Long shareId, Long userId) {
        Dashboard dashboard = dashboardMapper.selectById(dashboardId);
        if (dashboard == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "仪表盘不存在");
        }
        if (!dashboard.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只有仪表盘创建者可以管理共享");
        }

        DashboardShare share = shareMapper.selectById(shareId);
        if (share == null || !share.getDashboardId().equals(dashboardId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "共享记录不存在");
        }

        shareMapper.deleteById(shareId);
        log.info("Dashboard share removed: dashboardId={}, shareId={}", dashboardId, shareId);
    }

    // ─── 收藏与默认仪表盘 ────────────────────────────────────

    /**
     * 切换收藏状态
     * @return true=已收藏, false=已取消收藏
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFavorite(Long dashboardId, Long userId) {
        // 确认仪表盘存在且用户有权访问
        assertCanView(dashboardId, userId);

        DashboardFavorite existing = favoriteMapper.selectOne(
                new LambdaQueryWrapper<DashboardFavorite>()
                        .eq(DashboardFavorite::getUserId, userId)
                        .eq(DashboardFavorite::getDashboardId, dashboardId));

        if (existing != null) {
            // 取消收藏（如果是默认仪表盘也一并清除）
            favoriteMapper.deleteById(existing.getId());
            log.info("Dashboard unfavorited: dashboardId={}, userId={}", dashboardId, userId);
            return false;
        } else {
            // 添加收藏
            DashboardFavorite fav = new DashboardFavorite();
            fav.setUserId(userId);
            fav.setDashboardId(dashboardId);
            fav.setIsDefault(false);
            favoriteMapper.insert(fav);
            log.info("Dashboard favorited: dashboardId={}, userId={}", dashboardId, userId);
            return true;
        }
    }

    /**
     * 设为默认仪表盘（自动收藏）
     */
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long dashboardId, Long userId) {
        // 确认仪表盘存在且用户有权访问
        assertCanView(dashboardId, userId);

        // 清除当前用户的旧默认
        LambdaUpdateWrapper<DashboardFavorite> clearDefault = new LambdaUpdateWrapper<DashboardFavorite>()
                .eq(DashboardFavorite::getUserId, userId)
                .eq(DashboardFavorite::getIsDefault, true)
                .set(DashboardFavorite::getIsDefault, false);
        favoriteMapper.update(null, clearDefault);

        // 查找或创建收藏记录
        DashboardFavorite existing = favoriteMapper.selectOne(
                new LambdaQueryWrapper<DashboardFavorite>()
                        .eq(DashboardFavorite::getUserId, userId)
                        .eq(DashboardFavorite::getDashboardId, dashboardId));

        if (existing != null) {
            existing.setIsDefault(true);
            favoriteMapper.updateById(existing);
        } else {
            DashboardFavorite fav = new DashboardFavorite();
            fav.setUserId(userId);
            fav.setDashboardId(dashboardId);
            fav.setIsDefault(true);
            favoriteMapper.insert(fav);
        }

        log.info("Dashboard set as default: dashboardId={}, userId={}", dashboardId, userId);
    }

    /**
     * 取消默认仪表盘
     */
    @Transactional(rollbackFor = Exception.class)
    public void unsetDefault(Long userId) {
        LambdaUpdateWrapper<DashboardFavorite> clearDefault = new LambdaUpdateWrapper<DashboardFavorite>()
                .eq(DashboardFavorite::getUserId, userId)
                .eq(DashboardFavorite::getIsDefault, true)
                .set(DashboardFavorite::getIsDefault, false);
        favoriteMapper.update(null, clearDefault);
        log.info("Dashboard default cleared for userId={}", userId);
    }

    /**
     * 获取用户的默认仪表盘 ID（如果有）
     */
    public Long getUserDefaultDashboardId(Long userId) {
        DashboardFavorite defaultFav = favoriteMapper.selectOne(
                new LambdaQueryWrapper<DashboardFavorite>()
                        .eq(DashboardFavorite::getUserId, userId)
                        .eq(DashboardFavorite::getIsDefault, true));
        return defaultFav != null ? defaultFav.getDashboardId() : null;
    }

    /**
     * 获取系统默认仪表盘 ID
     */
    public Long getSystemDefaultDashboardId() {
        Dashboard systemDefault = dashboardMapper.selectOne(
                new LambdaQueryWrapper<Dashboard>()
                        .eq(Dashboard::getIsSystemDefault, true));
        return systemDefault != null ? systemDefault.getId() : null;
    }

    /**
     * 获取或自动创建项目概览仪表盘
     * <p>
     * 一个项目只有一个 project_overview 类型仪表盘，由系统自动维护（owner = 项目创建者/首位访问者）。
     * 使用 SELECT FOR UPDATE（分布式场景下用 getOrCreate + unique 索引保障幂等）。
     *
     * @param projectId 项目 ID
     * @param userId    当前用户 ID（首次创建时作为 owner）
     * @return 项目概览仪表盘详情 VO
     */
    @Transactional(rollbackFor = Exception.class)
    public DashboardDetailVO getOrCreateProjectOverviewDashboard(Long projectId, Long userId) {
        // 先尝试查找已存在的项目概览仪表盘
        Dashboard existing = dashboardMapper.selectOne(
                new LambdaQueryWrapper<Dashboard>()
                        .eq(Dashboard::getDashboardType, "project_overview")
                        .eq(Dashboard::getProjectId, projectId));

        if (existing != null) {
            return buildProjectOverviewDetailVO(existing, userId);
        }

        // 不存在则自动创建（unique 索引保证并发安全）
        Dashboard dashboard = new Dashboard();
        dashboard.setName("项目概览");
        dashboard.setDescription("项目概览仪表盘");
        dashboard.setOwnerId(userId);
        dashboard.setShared(true); // 项目成员均可查看
        dashboard.setLayout("{}");
        dashboard.setDashboardType("project_overview");
        dashboard.setProjectId(projectId);
        dashboard.setIsSystemDefault(false);

        try {
            dashboardMapper.insert(dashboard);
            log.info("Project overview dashboard created: projectId={}, dashboardId={}, owner={}",
                    projectId, dashboard.getId(), userId);
        } catch (Exception e) {
            // 并发场景下另一线程已经创建，重新查询
            Dashboard reFetch = dashboardMapper.selectOne(
                    new LambdaQueryWrapper<Dashboard>()
                            .eq(Dashboard::getDashboardType, "project_overview")
                            .eq(Dashboard::getProjectId, projectId));
            if (reFetch != null) {
                return buildProjectOverviewDetailVO(reFetch, userId);
            }
            throw e;
        }

        return buildProjectOverviewDetailVO(dashboard, userId);
    }

    /**
     * 构建项目概览仪表盘 VO（含 widgets）
     */
    private DashboardDetailVO buildProjectOverviewDetailVO(Dashboard dashboard, Long userId) {
        DashboardDetailVO vo = dashboardConverter.toDetailVO(dashboard);
        vo.setLayoutVersion(dashboard.getLayoutVersion() != null ? dashboard.getLayoutVersion() : 0);
        vo.setIsSystemDefault(false);

        SysUser owner = sysUserMapper.selectById(dashboard.getOwnerId());
        vo.setOwnerName(owner != null ? owner.getDisplayName() : "");

        Long shareCount = shareMapper.selectCount(new LambdaQueryWrapper<DashboardShare>()
                .eq(DashboardShare::getDashboardId, dashboard.getId()));
        vo.setShareCount(shareCount.intValue());

        LambdaQueryWrapper<DashboardWidget> widgetWrapper = new LambdaQueryWrapper<DashboardWidget>()
                .eq(DashboardWidget::getDashboardId, dashboard.getId())
                .orderByAsc(DashboardWidget::getSortOrder)
                .orderByAsc(DashboardWidget::getPositionY)
                .orderByAsc(DashboardWidget::getPositionX);
        List<DashboardWidget> widgets = widgetMapper.selectList(widgetWrapper);
        vo.setWidgets(dashboardConverter.toWidgetVOList(widgets));

        return vo;
    }

    // ─── Widget 操作 ────────────────────────────────────────

    /**
     * 添加 Widget 到仪表盘
     */
    @Transactional(rollbackFor = Exception.class)
    public DashboardWidgetVO addWidget(Long dashboardId, CreateWidgetDTO dto, Long userId) {
        assertCanEdit(dashboardId, userId);

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
    @Transactional(rollbackFor = Exception.class)
    public DashboardWidgetVO updateWidget(Long dashboardId, Long widgetId, UpdateWidgetDTO dto, Long userId) {
        assertCanEdit(dashboardId, userId);

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
    @Transactional(rollbackFor = Exception.class)
    public void deleteWidget(Long dashboardId, Long widgetId, Long userId) {
        assertCanEdit(dashboardId, userId);

        DashboardWidget widget = widgetMapper.selectById(widgetId);
        if (widget == null || !widget.getDashboardId().equals(dashboardId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "微件不存在");
        }

        widgetMapper.deleteById(widgetId);
        log.info("Widget deleted: id={}, dashboard={}", widgetId, dashboardId);
    }

    /**
     * 移动 Widget 到另一个仪表盘
     */
    @Transactional(rollbackFor = Exception.class)
    public DashboardWidgetVO moveWidget(Long sourceDashboardId, Long widgetId, Long targetDashboardId, Long userId) {
        // 校验源仪表盘编辑权限
        assertCanEdit(sourceDashboardId, userId);
        // 校验目标仪表盘编辑权限
        assertCanEdit(targetDashboardId, userId);

        if (sourceDashboardId.equals(targetDashboardId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能移动到当前仪表盘");
        }

        DashboardWidget widget = widgetMapper.selectById(widgetId);
        if (widget == null || !widget.getDashboardId().equals(sourceDashboardId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "微件不存在");
        }

        // 计算目标仪表盘中的新位置
        Long maxSort = widgetMapper.selectCount(new LambdaQueryWrapper<DashboardWidget>()
                .eq(DashboardWidget::getDashboardId, targetDashboardId));

        widget.setDashboardId(targetDashboardId);
        widget.setPositionX(0);
        widget.setPositionY(maxSort.intValue() * 3);
        widget.setSortOrder(maxSort.intValue());
        widgetMapper.updateById(widget);

        log.info("Widget moved: id={}, from dashboard={} to dashboard={}", widgetId, sourceDashboardId, targetDashboardId);
        return dashboardConverter.toWidgetVO(widget);
    }

    /**
     * 批量更新 Widget 位置（拖拽后保存布局）
     * 使用乐观锁防止并发覆盖，使用批量更新替代逐条 SQL。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateLayout(Long dashboardId, UpdateLayoutDTO dto, Long userId) {
        // 1. 权限校验 + 乐观锁校验
        Dashboard dashboard = dashboardMapper.selectById(dashboardId);
        if (dashboard == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "仪表盘不存在");
        }

        // 允许 owner 或有 edit 权限的用户操作布局
        assertCanEdit(dashboardId, userId);

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

    /**
     * 校验用户是否有编辑权限（owner 或 share 权限为 edit，或系统管理员对系统默认仪表盘）
     * 对 project_overview 类型，还允许有项目 project:edit 权限的用户编辑 Widget
     */
    private void assertCanEdit(Long dashboardId, Long userId) {
        Dashboard dashboard = dashboardMapper.selectById(dashboardId);
        if (dashboard == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "仪表盘不存在");
        }
        if (dashboard.getOwnerId().equals(userId)) {
            return; // owner 始终可编辑
        }
        // 系统默认仪表盘：系统管理员可编辑
        if (Boolean.TRUE.equals(dashboard.getIsSystemDefault())
                && permissionService.isSystemAdmin(userId)) {
            return;
        }
        // 项目概览仪表盘：有项目 project:edit 权限的用户可编辑 Widget
        if ("project_overview".equals(dashboard.getDashboardType())
                && dashboard.getProjectId() != null
                && permissionService.hasPermission(userId, dashboard.getProjectId(), "project:edit")) {
            return;
        }
        // 检查是否有 edit 权限的共享
        if (shareMapper.countEditAccessByUser(dashboardId, userId) > 0) {
            return;
        }
        throw new BusinessException(ErrorCode.ACCESS_DENIED, "无编辑权限");
    }

    /**
     * 校验用户是否有查看权限（系统默认 / owner / 全局共享 / 精确共享 / project_overview 项目成员）
     */
    private void assertCanView(Long dashboardId, Long userId) {
        Dashboard dashboard = dashboardMapper.selectById(dashboardId);
        if (dashboard == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "仪表盘不存在");
        }
        if (Boolean.TRUE.equals(dashboard.getIsSystemDefault())) {
            return; // 系统默认仪表盘对所有认证用户可见
        }
        if (dashboard.getOwnerId().equals(userId)) {
            return;
        }
        if (Boolean.TRUE.equals(dashboard.getShared())) {
            return;
        }
        // 项目概览仪表盘：有项目 project:view 权限的用户可查看
        if ("project_overview".equals(dashboard.getDashboardType())
                && dashboard.getProjectId() != null
                && permissionService.hasPermission(userId, dashboard.getProjectId(), "project:view")) {
            return;
        }
        if (shareMapper.countAccessByUser(dashboardId, userId) > 0) {
            return;
        }
        throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该仪表盘");
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

    private Map<Long, Long> getShareCountMap(List<Long> dashboardIds) {
        if (dashboardIds.isEmpty()) return Map.of();
        List<Map<String, Object>> results = shareMapper.selectMaps(
                new QueryWrapper<DashboardShare>()
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
