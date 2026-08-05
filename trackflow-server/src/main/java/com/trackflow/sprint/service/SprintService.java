package com.trackflow.sprint.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.sprint.dto.CompleteSprintDTO;
import com.trackflow.sprint.dto.CreateSprintDTO;
import com.trackflow.sprint.dto.DeleteSprintDTO;
import com.trackflow.sprint.dto.UpdateSprintDTO;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.entity.SprintStatus;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.sprint.vo.CompletionPreviewVO;
import com.trackflow.sprint.vo.CreationPreviewVO;
import com.trackflow.sprint.vo.DeletionPreviewVO;
import com.trackflow.sprint.vo.SprintCompleteResultVO;
import com.trackflow.sprint.vo.SprintOverlapWarningVO;
import com.trackflow.sprint.vo.SprintVO;
import com.trackflow.project.service.ProjectActivityService;
import com.trackflow.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trackflow.common.event.SprintNotificationEvent;
import com.trackflow.common.event.ReportCacheInvalidationEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintMapper sprintMapper;
    private final IssueMapper issueMapper;
    private final IssueActivityMapper activityMapper;
    private final com.trackflow.issue.mapper.IssueSprintMapper issueSprintMapper;
    private final ProjectService projectService;
    private final ProjectActivityService projectActivityService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.trackflow.sprint.converter.SprintConverter sprintConverter;

    /**
     * 查询项目的 Sprint 列表（带工单统计 + 状态推导）。
     * MyBatis resultMap 直接映射为 SprintVO，然后根据日期推导状态一致性。
     * 使用 readOnly 事务确保多步查询在同一个数据库快照中执行，避免并发修改导致数据不一致。
     */
    @Transactional(readOnly = true)
    public List<SprintVO> listByProjectWithStats(Long projectId) {
        List<SprintVO> sprints = sprintMapper.selectSprintsWithStats(projectId);
        LocalDate today = LocalDate.now();
        for (SprintVO sprint : sprints) {
            computeStatusHint(sprint, today);
        }
        return sprints;
    }

    /**
     * 查询项目的 Sprint 列表（带工单统计 + 状态推导 + 分页）。
     * 支持 page/pageSize 参数，默认 page=1, pageSize=20。
     * 使用 readOnly 事务确保多步查询在同一个数据库快照中执行，避免并发修改导致数据不一致。
     *
     * @param projectId 项目 ID
     * @param page      页码（从 1 开始）
     * @param pageSize  每页数量
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public com.trackflow.common.model.PageResult<SprintVO> listByProjectWithStatsPage(Long projectId, int page, int pageSize) {
        // 安全限制
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(Math.max(1, pageSize), 200);
        int offset = (safePage - 1) * safePageSize;

        long total = sprintMapper.countByProjectId(projectId);
        List<SprintVO> sprints = sprintMapper.selectSprintsWithStatsPage(projectId, offset, safePageSize);
        LocalDate today = LocalDate.now();
        for (SprintVO sprint : sprints) {
            computeStatusHint(sprint, today);
        }
        return new com.trackflow.common.model.PageResult<>(sprints, total, safePage, safePageSize);
    }

    /**
     * 跨项目查询 Sprint 列表（带工单统计 + 项目信息 + 分页）。
     * 仅查询当前用户有权限访问的项目中的 Sprint。
     *
     * @param projectId 可选的项目 ID 过滤（null 表示全部可访问项目）
     * @param page      页码（从 1 开始）
     * @param pageSize  每页数量
     */
    @Transactional(readOnly = true)
    public com.trackflow.common.model.PageResult<SprintVO> listAllWithStats(Long projectId, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(Math.max(1, pageSize), 200);
        int offset = (safePage - 1) * safePageSize;

        Long currentUserId = SecurityUtils.getCurrentUserId();
        List<Long> projectIds;

        if (projectId != null) {
            // 指定了项目 — 返回该项目的 Sprint（权限由 Controller 层校验）
            projectIds = List.of(projectId);
        } else {
            // 获取当前用户可访问的项目 ID 列表
            projectIds = projectService.getAccessibleProjectIds(currentUserId);
            // null 表示系统管理员无限制 — 传空列表表示查所有
            if (projectIds == null) {
                projectIds = List.of();
            }
        }

        long total = sprintMapper.countByProjectIds(projectIds);
        List<SprintVO> sprints = sprintMapper.selectSprintsWithStatsMultiProject(projectIds, offset, safePageSize);
        LocalDate today = LocalDate.now();
        for (SprintVO sprint : sprints) {
            computeStatusHint(sprint, today);
        }
        return new com.trackflow.common.model.PageResult<>(sprints, total, safePage, safePageSize);
    }

    /**
     * 根据 Sprint 的 status 和日期范围，推导状态是否合理并设置提示信息。
     */
    private void computeStatusHint(SprintVO sprint, LocalDate today) {
        String status = sprint.getStatus();
        LocalDate startDate = sprint.getStartDate();
        LocalDate endDate = sprint.getEndDate();

        if (SprintStatus.ACTIVE.getValue().equals(status)) {
            if (startDate != null && startDate.isAfter(today)) {
                sprint.setStatusHint("开始日期尚未到达，Sprint 不应处于进行中状态");
            } else if (endDate != null && endDate.isBefore(today)) {
                sprint.setOverdue(true);
                sprint.setStatusHint("已超过结束日期，建议尽快完成迭代");
            }
        } else if (SprintStatus.PLANNED.getValue().equals(status)) {
            if (endDate != null && endDate.isBefore(today)) {
                sprint.setStatusHint("已超过计划结束日期且尚未开始");
            } else if (startDate != null && !startDate.isAfter(today)) {
                sprint.setStatusHint("已到开始日期，可以激活此迭代");
            }
        }
    }

    public List<Sprint> listByProject(Long projectId) {
        return sprintMapper.selectList(
                new LambdaQueryWrapper<Sprint>()
                        .eq(Sprint::getProjectId, projectId)
                        .orderByDesc(Sprint::getCreatedAt)
        );
    }

    public Sprint getById(Long id) {
        Sprint sprint = sprintMapper.selectById(id);
        if (sprint == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Sprint not found");
        return sprint;
    }

    /**
     * 查询单个 Sprint，带工单统计数据（与列表接口统计逻辑一致）。
     * 供 Controller 的 getById 端点使用。
     * 使用 readOnly 事务确保统计数据与 Sprint 基本信息在同一个快照中获取。
     */
    @Transactional(readOnly = true)
    public SprintVO getByIdWithStats(Long id) {
        SprintVO vo = sprintMapper.selectSprintWithStats(id);
        if (vo == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Sprint not found");
        computeStatusHint(vo, LocalDate.now());
        return vo;
    }

    /**
     * 检查同项目内 Sprint 名称唯一性（大小写不敏感）。
     * 参考 OpenProject: validates :name, uniqueness: { scope: [:project_id], case_sensitive: false }
     *
     * @param projectId 项目 ID
     * @param name      Sprint 名称
     * @param excludeId 排除的 Sprint ID（更新时排除自身），创建时传 null
     */
    private void checkNameUniqueness(Long projectId, String name, Long excludeId) {
        String trimmedName = name.trim();
        LambdaQueryWrapper<Sprint> wrapper = new LambdaQueryWrapper<Sprint>()
                .eq(Sprint::getProjectId, projectId)
                .apply("LOWER(name) = LOWER({0})", trimmedName);
        if (excludeId != null) {
            wrapper.ne(Sprint::getId, excludeId);
        }
        Long count = sprintMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该项目已存在同名迭代：" + trimmedName);
        }
    }

    /**
     * 校验日期范围合理性：如果两个日期都存在，开始日期必须严格早于结束日期。
     * 参考 OpenProject: validates :finish_date, comparison: { greater_than_or_equal_to: :start_date }
     */
    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && !startDate.isBefore(endDate)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "开始日期必须早于结束日期");
        }
    }

    /**
     * 检测 Sprint 日期是否与同项目已有的计划中/进行中 Sprint 日期重叠。
     * 已完成的 Sprint 不参与重叠检测。
     * <p>
     * 参考 YouTrack：允许创建重叠 Sprint，但 YouTrack 文档明确警告重叠会影响"当前 Sprint"检测。
     * TrackFlow 采用"警告但允许"策略：检测到重叠时抛出 SPRINT_DATE_OVERLAP 异常（code=40913），
     * 前端显示确认弹窗后带 confirmOverlap=true 重新提交。
     *
     * @param projectId 项目 ID
     * @param startDate 新 Sprint 的开始日期
     * @param endDate   新 Sprint 的结束日期
     * @param excludeId 排除的 Sprint ID（编辑时排除自身），创建时传 null
     */
    private void checkDateOverlap(Long projectId, LocalDate startDate, LocalDate endDate, Long excludeId) {
        // 只有两个日期都存在时才进行重叠检测
        if (startDate == null || endDate == null) {
            return;
        }

        // 查询同项目中 planned 或 active 状态的 Sprint（排除自身）
        LambdaQueryWrapper<Sprint> wrapper = new LambdaQueryWrapper<Sprint>()
                .eq(Sprint::getProjectId, projectId)
                .in(Sprint::getStatus, SprintStatus.PLANNED, SprintStatus.ACTIVE)
                .isNotNull(Sprint::getStartDate)
                .isNotNull(Sprint::getEndDate);
        if (excludeId != null) {
            wrapper.ne(Sprint::getId, excludeId);
        }
        List<Sprint> existingSprints = sprintMapper.selectList(wrapper);

        // 检测日期范围重叠：两个区间 [A_start, A_end] 和 [B_start, B_end] 重叠条件：
        // A_start <= B_end AND A_end >= B_start
        List<SprintOverlapWarningVO.OverlappingSprint> overlapping = existingSprints.stream()
                .filter(existing -> !startDate.isAfter(existing.getEndDate()) && !endDate.isBefore(existing.getStartDate()))
                .map(existing -> new SprintOverlapWarningVO.OverlappingSprint(
                        existing.getName(),
                        existing.getStartDate().toString(),
                        existing.getEndDate().toString(),
                        existing.getStatus().getValue()
                ))
                .toList();

        if (!overlapping.isEmpty()) {
            SprintOverlapWarningVO warningVO = new SprintOverlapWarningVO();
            warningVO.setOverlappingSprints(overlapping);

            // 构造用户友好的警告消息
            String sprintNames = overlapping.stream()
                    .map(s -> String.format("'%s' (%s ~ %s)", s.getName(), s.getStartDate(), s.getEndDate()))
                    .collect(Collectors.joining("、"));
            String message = String.format("新迭代的日期与 %s 存在重叠。重叠的迭代可能影响「当前 Sprint」的自动检测和工单归属。", sprintNames);

            throw new BusinessException(ErrorCode.SPRINT_DATE_OVERLAP, message, warningVO);
        }
    }

    @Transactional
    public Sprint create(Long projectId, CreateSprintDTO dto) {
        // 归档项目不允许创建 Sprint
        projectService.assertProjectActive(projectId);

        // 检查名称在项目内唯一
        checkNameUniqueness(projectId, dto.getName(), null);

        Sprint sprint = new Sprint();
        sprint.setProjectId(projectId);
        sprint.setName(dto.getName());
        sprint.setGoal(dto.getGoal());
        sprint.setStartDate(dto.getStartDate());
        sprint.setEndDate(dto.getEndDate());
        sprint.setStatus(SprintStatus.PLANNED);

        // 日期合理性校验（与 update 保持一致）
        validateDateRange(sprint.getStartDate(), sprint.getEndDate());

        // 日期重叠检测：未确认时抛异常，前端确认后带 confirmOverlap=true 跳过
        if (!Boolean.TRUE.equals(dto.getConfirmOverlap())) {
            checkDateOverlap(projectId, sprint.getStartDate(), sprint.getEndDate(), null);
        }

        sprintMapper.insert(sprint);

        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 记录项目活动日志
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sprint_id", sprint.getId());
        detail.put("sprint_name", sprint.getName());
        if (sprint.getStartDate() != null) detail.put("start_date", sprint.getStartDate().toString());
        if (sprint.getEndDate() != null) detail.put("end_date", sprint.getEndDate().toString());
        projectActivityService.log(projectId, currentUserId, "create_sprint", null, detail);

        // 选项1：将当前活跃 Sprint 的未完成工单移入新 Sprint
        if (Boolean.TRUE.equals(dto.getMoveUnresolvedIssues())) {
            moveUnresolvedIssuesToNewSprint(projectId, sprint, currentUserId);
        }

        // 选项2：将新 Sprint 设为项目默认 Sprint
        if (Boolean.TRUE.equals(dto.getSetAsDefault())) {
            setProjectDefaultSprint(projectId, sprint.getId());
        }

        return sprint;
    }

    /**
     * 将项目当前活跃 Sprint 中的未完成工单批量移入新 Sprint，并记录活动日志。
     */
    private void moveUnresolvedIssuesToNewSprint(Long projectId, Sprint newSprint, Long currentUserId) {
        Sprint sourceSprint = findSourceSprintForUnresolved(projectId, newSprint.getId());
        if (sourceSprint == null) {
            log.warn("项目 {} 没有含未完成工单的 Sprint，跳过移入未完成工单", projectId);
            return;
        }

        // 查找源 Sprint 中未关闭的工单
        List<Long> openIssueIds = sprintMapper.selectOpenIssueIds(sourceSprint.getId());
        if (openIssueIds.isEmpty()) {
            log.info("Sprint '{}' 中无未完成工单，跳过", sourceSprint.getName());
            return;
        }

        // 批量更新 sprint_id + updatedBy/updatedAt
        LocalDateTime now = LocalDateTime.now();
        issueMapper.update(null,
                new LambdaUpdateWrapper<Issue>()
                        .in(Issue::getId, openIssueIds)
                        .set(Issue::getSprintId, newSprint.getId())
                        .set(Issue::getUpdatedBy, currentUserId)
                        .set(Issue::getUpdatedAt, now)
        );

        // 同步 issue_sprint 关联表：旧 Sprint 关联替换为新 Sprint
        for (Long issueId : openIssueIds) {
            issueSprintMapper.deleteByIssueIdAndSprintId(issueId, sourceSprint.getId());
            com.trackflow.issue.entity.IssueSprint rel = new com.trackflow.issue.entity.IssueSprint();
            rel.setIssueId(issueId);
            rel.setSprintId(newSprint.getId());
            rel.setCreatedAt(now);
            issueSprintMapper.insert(rel);
        }

        // 批量记录活动日志
        String oldId = String.valueOf(sourceSprint.getId());
        String newId = String.valueOf(newSprint.getId());
        String oldName = sourceSprint.getName();
        String newName = newSprint.getName();
        List<IssueActivity> activities = openIssueIds.stream().map(issueId -> {
            IssueActivity activity = new IssueActivity();
            activity.setIssueId(issueId);
            activity.setUserId(currentUserId);
            activity.setAction("updated");
            activity.setFieldName("sprint");
            activity.setOldValue(oldId);
            activity.setNewValue(newId);
            activity.setOldDisplayValue(oldName);
            activity.setNewDisplayValue(newName);
            activity.setCreatedAt(now);
            return activity;
        }).toList();
        Db.saveBatch(activities);

        log.info("已将 {} 个未完成工单从 Sprint '{}' 移入新 Sprint '{}'",
                openIssueIds.size(), sourceSprint.getName(), newSprint.getName());
    }

    /**
     * 将指定 Sprint 设为项目的默认 Sprint（新建工单自动归属）。
     * 存储在 project.settings JSONB 的 defaultSprintId 字段中。
     */
    private void setProjectDefaultSprint(Long projectId, Long sprintId) {
        projectService.updateProjectSetting(projectId, "defaultSprintId", sprintId);
    }

    /**
     * 获取创建 Sprint 的预览信息：
     * - 是否存在活跃 Sprint 或最近完成的 Sprint 及其未完成工单数
     * - 是否已设置默认 Sprint
     * <p>
     * 对标 YouTrack 行为：自动完成的 Sprint 中未关闭工单仍保留在该 Sprint 中，
     * 因此创建新 Sprint 时需要展示这些信息供用户决策是否迁移。
     * <p>
     * 使用 readOnly 事务确保预览数据在同一个快照中获取。
     */
    @Transactional(readOnly = true)
    public CreationPreviewVO getCreationPreview(Long projectId) {
        CreationPreviewVO vo = new CreationPreviewVO();

        // 查找含未完成工单的源 Sprint（优先级：活跃 > 最近计划中 > 最近完成）
        Sprint sourceSprint = findSourceSprintForUnresolved(projectId, null);

        if (sourceSprint != null) {
            vo.setActiveSprintId(String.valueOf(sourceSprint.getId()));
            vo.setActiveSprintName(sourceSprint.getName());
            List<Long> openIssueIds = sprintMapper.selectOpenIssueIds(sourceSprint.getId());
            vo.setUnresolvedIssueCount(openIssueIds.size());
        }

        // 检查是否已有默认 Sprint
        Long defaultSprintId = projectService.getProjectSettingAsLong(projectId, "defaultSprintId");
        if (defaultSprintId != null) {
            Sprint defaultSprint = sprintMapper.selectById(defaultSprintId);
            if (defaultSprint != null && defaultSprint.getStatus() != SprintStatus.COMPLETED) {
                vo.setHasDefaultSprint(true);
                vo.setDefaultSprintName(defaultSprint.getName());
            }
        }

        return vo;
    }

    /**
     * 查找项目中应作为"未完成工单迁移源"的 Sprint。
     * 对标 YouTrack "Add unresolved issues from current sprint" 行为。
     * <p>
     * 优先级：
     * 1. 活跃（ACTIVE）Sprint — 当前正在进行的迭代
     * 2. 最近的计划中（PLANNED）Sprint — 按开始日期倒序
     * 3. 最近已完成（COMPLETED）Sprint — 按结束日期倒序
     * <p>
     * 每个级别只返回含未完成工单的 Sprint；若该级别的候选 Sprint 无未完成工单则跳过，
     * 继续检查下一优先级。
     *
     * @param projectId    项目 ID
     * @param excludeSprintId 排除的 Sprint ID（新建 Sprint 时排除自身，可为 null）
     * @return 含未完成工单的源 Sprint，或 null（无匹配）
     */
    private Sprint findSourceSprintForUnresolved(Long projectId, Long excludeSprintId) {
        // 1. 活跃 Sprint（每个项目最多一个）
        Sprint active = sprintMapper.selectOne(
                new LambdaQueryWrapper<Sprint>()
                        .eq(Sprint::getProjectId, projectId)
                        .eq(Sprint::getStatus, SprintStatus.ACTIVE)
        );
        if (active != null && (excludeSprintId == null || !active.getId().equals(excludeSprintId))) {
            List<Long> openIds = sprintMapper.selectOpenIssueIds(active.getId());
            if (!openIds.isEmpty()) {
                return active;
            }
        }

        // 2. 最近的计划中 Sprint（按开始日期倒序，开始日期为空的排最后）
        List<Sprint> plannedList = sprintMapper.selectList(
                new LambdaQueryWrapper<Sprint>()
                        .eq(Sprint::getProjectId, projectId)
                        .eq(Sprint::getStatus, SprintStatus.PLANNED)
                        .ne(excludeSprintId != null, Sprint::getId, excludeSprintId)
                        .orderByDesc(Sprint::getStartDate)
        );
        for (Sprint planned : plannedList) {
            List<Long> openIds = sprintMapper.selectOpenIssueIds(planned.getId());
            if (!openIds.isEmpty()) {
                return planned;
            }
        }

        // 3. 最近已完成 Sprint（按结束日期倒序，结束日期为空的排最后）
        List<Sprint> completedList = sprintMapper.selectList(
                new LambdaQueryWrapper<Sprint>()
                        .eq(Sprint::getProjectId, projectId)
                        .eq(Sprint::getStatus, SprintStatus.COMPLETED)
                        .ne(excludeSprintId != null, Sprint::getId, excludeSprintId)
                        .orderByDesc(Sprint::getEndDate)
        );
        for (Sprint completed : completedList) {
            List<Long> openIds = sprintMapper.selectOpenIssueIds(completed.getId());
            if (!openIds.isEmpty()) {
                return completed;
            }
        }

        return null;
    }

    @Transactional
    public Sprint update(Long id, UpdateSprintDTO dto) {
        Sprint sprint = getById(id);
        projectService.assertProjectActive(sprint.getProjectId());

        // YouTrack 标准：Sprint 属性（名称、目标、日期）可以随时自由编辑，无状态限制
        // 参考：https://www.jetbrains.com/help/youtrack/server/manage-sprints.html#edit-sprint-properties

        Long currentUserId = SecurityUtils.getCurrentUserId();
        List<Map<String, Object>> changes = new ArrayList<>();

        if (dto.getName() != null) {
            String trimmed = dto.getName().trim();
            if (trimmed.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "迭代名称不能为空");
            }
            if (!trimmed.equals(sprint.getName())) {
                // 检查新名称在项目内唯一（排除自身）
                checkNameUniqueness(sprint.getProjectId(), trimmed, id);
                changes.add(buildFieldChange("name", sprint.getName(), trimmed));
                sprint.setName(trimmed);
            }
        }
        if (dto.getGoal() != null) {
            String oldGoal = sprint.getGoal() != null ? sprint.getGoal() : "";
            if (!dto.getGoal().equals(oldGoal)) {
                changes.add(buildFieldChange("goal", oldGoal, dto.getGoal()));
            }
            sprint.setGoal(dto.getGoal());
        }
        // 开始日期：clearStartDate=true 时清空，否则若有新值则更新
        boolean startDateCleared = false;
        boolean startDateSet = false;
        if (Boolean.TRUE.equals(dto.getClearStartDate())) {
            // 显式清空开始日期（恢复为"未排期"状态，对标 YouTrack unscheduled sprint）
            if (sprint.getStartDate() != null) {
                changes.add(buildFieldChange("start_date", sprint.getStartDate().toString(), ""));
            }
            sprint.setStartDate(null);
            startDateCleared = true;
        } else if (dto.getStartDate() != null) {
            String oldDate = sprint.getStartDate() != null ? sprint.getStartDate().toString() : "";
            if (!dto.getStartDate().equals(sprint.getStartDate())) {
                changes.add(buildFieldChange("start_date", oldDate, dto.getStartDate().toString()));
            }
            sprint.setStartDate(dto.getStartDate());
            startDateSet = true;
        }

        // 结束日期：clearEndDate=true 时清空，否则若有新值则更新
        boolean endDateCleared = false;
        boolean endDateSet = false;
        if (Boolean.TRUE.equals(dto.getClearEndDate())) {
            // 显式清空结束日期
            if (sprint.getEndDate() != null) {
                changes.add(buildFieldChange("end_date", sprint.getEndDate().toString(), ""));
            }
            sprint.setEndDate(null);
            endDateCleared = true;
        } else if (dto.getEndDate() != null) {
            String oldDate = sprint.getEndDate() != null ? sprint.getEndDate().toString() : "";
            if (!dto.getEndDate().equals(sprint.getEndDate())) {
                changes.add(buildFieldChange("end_date", oldDate, dto.getEndDate().toString()));
            }
            sprint.setEndDate(dto.getEndDate());
            endDateSet = true;
        }

        // 日期合理性校验：如果两个日期都存在，开始必须早于结束
        validateDateRange(sprint.getStartDate(), sprint.getEndDate());

        // 状态-日期一致性校验（REQ-911）：已激活的 Sprint 不允许将开始日期修改为未来日期
        // 这会造成"进行中但开始日期未到"的状态矛盾
        if (sprint.getStatus() == SprintStatus.ACTIVE && sprint.getStartDate() != null) {
            LocalDate today = LocalDate.now();
            if (sprint.getStartDate().isAfter(today)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "进行中的迭代不能将开始日期修改为未来日期（" + sprint.getStartDate() + "）。" +
                        "如需调整日期，请先将迭代回退为计划中状态");
            }
        }

        // 日期重叠检测：仅在日期有变更（设置或清空）且未确认时触发
        boolean dateChanged = startDateCleared || startDateSet || endDateCleared || endDateSet;
        if (dateChanged && !Boolean.TRUE.equals(dto.getConfirmOverlap())) {
            checkDateOverlap(sprint.getProjectId(), sprint.getStartDate(), sprint.getEndDate(), id);
        }

        // 持久化：若有日期被清空，需要使用 LambdaUpdateWrapper 明确 SET NULL
        // （updateById 默认跳过 null 字段，无法将日期清空）
        if (startDateCleared || endDateCleared) {
            LambdaUpdateWrapper<Sprint> wrapper = new LambdaUpdateWrapper<Sprint>()
                    .eq(Sprint::getId, sprint.getId())
                    .set(Sprint::getName, sprint.getName())
                    .set(Sprint::getGoal, sprint.getGoal())
                    .set(Sprint::getStatus, sprint.getStatus())
                    .set(Sprint::getStartDate, sprint.getStartDate())
                    .set(Sprint::getEndDate, sprint.getEndDate());
            sprintMapper.update(null, wrapper);
        } else {
            sprintMapper.updateById(sprint);
        }

        // 记录项目活动日志（只在有实际变更时记录）
        if (!changes.isEmpty()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("sprint_id", sprint.getId());
            detail.put("sprint_name", sprint.getName());
            detail.put("changes", changes);
            projectActivityService.log(sprint.getProjectId(), currentUserId, "update_sprint", null, detail);
        }

        return sprint;
    }

    private Map<String, Object> buildFieldChange(String field, String oldValue, String newValue) {
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("field", field);
        change.put("old_value", oldValue);
        change.put("new_value", newValue);
        return change;
    }

    @Transactional
    public Sprint activate(Long id) {
        Sprint sprint = getById(id);
        // 归档项目不允许操作 Sprint
        projectService.assertProjectActive(sprint.getProjectId());

        // 只有计划中的 Sprint 才能激活
        if (sprint.getStatus() != SprintStatus.PLANNED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有计划中的迭代才能激活");
        }

        // 同项目同一时间只允许一个 active Sprint（Scrum 排他约束）
        Long activeCount = sprintMapper.selectCount(
                new LambdaQueryWrapper<Sprint>()
                        .eq(Sprint::getProjectId, sprint.getProjectId())
                        .eq(Sprint::getStatus, SprintStatus.ACTIVE)
        );
        if (activeCount > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "该项目已有一个活跃的迭代，请先完成当前迭代再激活新的");
        }

        // 日期合理性校验：开始日期不能在未来（容忍当天）
        LocalDate today = LocalDate.now();
        if (sprint.getStartDate() != null && sprint.getStartDate().isAfter(today)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "迭代开始日期（" + sprint.getStartDate() + "）还未到达，无法激活。请等到开始日期或修改日期后再激活");
        }

        // 结束日期校验：不允许激活已经过期的 Sprint
        if (sprint.getEndDate() != null && sprint.getEndDate().isBefore(today)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "迭代结束日期（" + sprint.getEndDate() + "）已过，无法激活一个已过期的迭代");
        }

        // 拍摄估算快照 — 记录 Sprint 激活时的范围作为燃尽图基线
        List<Issue> sprintIssues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getSprintId, id)
                        .isNull(Issue::getDeletedAt)
        );
        BigDecimal totalEstimatedHours = sprintIssues.stream()
                .map(Issue::getEstimatedHours)
                .filter(h -> h != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        sprint.setStartedAt(LocalDateTime.now());
        sprint.setStartScopeHours(totalEstimatedHours);
        sprint.setStartScopeIssues(sprintIssues.size());

        sprint.setStatus(SprintStatus.ACTIVE);
        try {
            sprintMapper.updateById(sprint);
        } catch (DuplicateKeyException e) {
            // 数据库层部分唯一索引触发（idx_sprint_project_active_unique）
            // 并发场景下两个事务都通过了应用层 selectCount 检查，但只有一个能成功 COMMIT
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "该项目已有一个活跃的迭代，请先完成当前迭代再激活新的（并发冲突）");
        }

        // 记录项目活动日志
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sprint_id", sprint.getId());
        detail.put("sprint_name", sprint.getName());
        projectActivityService.log(sprint.getProjectId(), currentUserId, "activate_sprint", null, detail);

        // 通知项目成员 Sprint 已激活 — 事务提交后触发
        eventPublisher.publishEvent(new SprintNotificationEvent.Activated(sprint, currentUserId));

        // 失效 Dashboard 缓存 — 事务提交后触发
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(sprint.getProjectId(), "sprint_activated"));

        return sprint;
    }

    @Transactional
    public SprintCompleteResultVO complete(Long id, CompleteSprintDTO dto) {
        Sprint sprint = getById(id);
        // 归档项目不允许操作 Sprint
        projectService.assertProjectActive(sprint.getProjectId());

        if (sprint.getStatus() != SprintStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有进行中的迭代才能完成");
        }

        // 查找未关闭工单（JOIN issue_status 判断 is_closed）
        List<Issue> openIssues = findOpenIssuesInSprint(id);

        // ⚠️ 先统计总工单数（必须在迁移操作之前，否则移走的工单不会被计入）
        long totalIssuesInSprint = issueMapper.selectCount(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getSprintId, id)
                        .isNull(Issue::getDeletedAt)
        );
        int completedIssues = (int) (totalIssuesInSprint - openIssues.size());
        int unresolvedIssues = openIssues.size();

        // 用于返回结果的变量
        String moveOption = null;
        String targetSprintName = null;

        // 有未完成工单时必须传入处理方式
        if (!openIssues.isEmpty()) {
            if (dto == null || dto.getMoveOption() == null || dto.getMoveOption().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "该迭代中仍有 " + openIssues.size() + " 个未完成工单，请选择处理方式");
            }

            moveOption = dto.getMoveOption();
            Long newSprintId = null;
            String newSprintName = null;
            if ("next_sprint".equals(dto.getMoveOption())) {
                if (dto.getTargetSprintId() == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择目标迭代");
                }
                // 验证目标 Sprint 存在且属于同一项目
                Sprint targetSprint = sprintMapper.selectById(dto.getTargetSprintId());
                if (targetSprint == null || !targetSprint.getProjectId().equals(sprint.getProjectId())) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "目标迭代不存在或不属于当前项目");
                }
                // 仅允许移入计划中或进行中的迭代（白名单校验）
                if (targetSprint.getStatus() != SprintStatus.PLANNED && targetSprint.getStatus() != SprintStatus.ACTIVE) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "目标迭代状态为「" + targetSprint.getStatus().getLabel() + "」，只能移入计划中或进行中的迭代");
                }
                newSprintId = dto.getTargetSprintId();
                newSprintName = targetSprint.getName();
                targetSprintName = newSprintName;
            }
            // "backlog" 时 newSprintId 和 newSprintName 保持 null

            // 批量更新工单的 sprint_id + updatedBy/updatedAt
            List<Long> openIssueIds = openIssues.stream().map(Issue::getId).collect(Collectors.toList());
            Long currentUserId = SecurityUtils.getCurrentUserId();
            LocalDateTime now = LocalDateTime.now();
            issueMapper.update(null,
                    new LambdaUpdateWrapper<Issue>()
                            .in(Issue::getId, openIssueIds)
                            .set(Issue::getSprintId, newSprintId)
                            .set(Issue::getUpdatedBy, currentUserId)
                            .set(Issue::getUpdatedAt, now)
            );

            // 同步 issue_sprint 关联表
            for (Long issueId : openIssueIds) {
                issueSprintMapper.deleteByIssueIdAndSprintId(issueId, sprint.getId());
                if (newSprintId != null) {
                    com.trackflow.issue.entity.IssueSprint rel = new com.trackflow.issue.entity.IssueSprint();
                    rel.setIssueId(issueId);
                    rel.setSprintId(newSprintId);
                    rel.setCreatedAt(now);
                    issueSprintMapper.insert(rel);
                }
            }

            // 批量记录活动日志：sprint 字段变更
            String oldSprintIdStr = String.valueOf(sprint.getId());
            String newSprintIdStr = newSprintId != null ? String.valueOf(newSprintId) : null;
            String oldSprintName = sprint.getName();
            String finalNewSprintName = newSprintName;
            List<IssueActivity> activities = openIssueIds.stream().map(issueId -> {
                IssueActivity activity = new IssueActivity();
                activity.setIssueId(issueId);
                activity.setUserId(currentUserId);
                activity.setAction("updated");
                activity.setFieldName("sprint");
                activity.setOldValue(oldSprintIdStr);
                activity.setNewValue(newSprintIdStr);
                activity.setOldDisplayValue(oldSprintName);
                activity.setNewDisplayValue(finalNewSprintName);
                activity.setCreatedAt(now);
                return activity;
            }).toList();
            Db.saveBatch(activities);
        }

        // 完成 Sprint
        sprint.setStatus(SprintStatus.COMPLETED);
        sprintMapper.updateById(sprint);

        // 清理默认 Sprint 设置（防止新工单被分配到已完成的 Sprint）
        clearDefaultSprintIfMatches(sprint.getProjectId(), sprint.getId());

        // 记录项目活动日志
        Long completeUserId = SecurityUtils.getCurrentUserId();
        Map<String, Object> completeDetail = new LinkedHashMap<>();
        completeDetail.put("sprint_id", sprint.getId());
        completeDetail.put("sprint_name", sprint.getName());
        int movedIssueCount = openIssues.size();
        if (movedIssueCount > 0) {
            completeDetail.put("unresolved_issues_count", movedIssueCount);
            completeDetail.put("move_option", dto != null ? dto.getMoveOption() : "none");
        }
        projectActivityService.log(sprint.getProjectId(), completeUserId, "complete_sprint", null, completeDetail);

        // 通知项目成员 Sprint 已完成 — 事务提交后触发
        // 注意：completedIssues 已在方法开头计算，确保在工单迁移前统计
        eventPublisher.publishEvent(new SprintNotificationEvent.Completed(sprint, Math.max(completedIssues, 0), completeUserId));

        // 失效 Dashboard 缓存 — 事务提交后触发
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(sprint.getProjectId(), "sprint_completed"));

        // 构建并返回完成结果（包含统计信息）
        return SprintCompleteResultVO.builder()
                .sprint(sprintConverter.toVO(sprint))
                .totalIssues((int) totalIssuesInSprint)
                .completedIssues(Math.max(completedIssues, 0))
                .unresolvedIssues(unresolvedIssues)
                .moveOption(moveOption)
                .targetSprintName(targetSprintName)
                .build();
    }

    /**
     * 归档 Sprint：将非归档状态的 Sprint 归档，从主列表中隐藏但保留数据。
     * 允许对 planned、active、completed 状态的 Sprint 执行归档操作。
     * 对标 YouTrack 标准：无状态限制，任何非归档 Sprint 均可归档。
     *
     * @param id Sprint ID
     * @return 归档后的 Sprint
     * @throws BusinessException 当 Sprint 已经是 archived 状态时
     */
    @Transactional(rollbackFor = Exception.class)
    public Sprint archive(Long id) {
        Sprint sprint = getById(id);
        projectService.assertProjectActive(sprint.getProjectId());

        if (sprint.getStatus() == SprintStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "迭代已经是归档状态");
        }

        sprint.setStatus(SprintStatus.ARCHIVED);
        sprintMapper.updateById(sprint);

        // 清理默认 Sprint 设置（防止新工单被分配到已归档的 Sprint）
        clearDefaultSprintIfMatches(sprint.getProjectId(), sprint.getId());

        // 记录项目活动日志
        Long userId = SecurityUtils.getCurrentUserId();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sprint_id", sprint.getId());
        detail.put("sprint_name", sprint.getName());
        projectActivityService.log(sprint.getProjectId(), userId, "archive_sprint", null, detail);

        log.info("Sprint 已归档: id={}, name={}, projectId={}", sprint.getId(), sprint.getName(), sprint.getProjectId());
        return sprint;
    }

    /**
     * 恢复 Sprint：将已归档的 Sprint 恢复为已完成状态。
     *
     * @param id Sprint ID
     * @return 恢复后的 Sprint
     * @throws BusinessException 当 Sprint 不是 archived 状态时
     */
    @Transactional(rollbackFor = Exception.class)
    public Sprint restore(Long id) {
        Sprint sprint = getById(id);
        projectService.assertProjectActive(sprint.getProjectId());

        if (sprint.getStatus() != SprintStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有已归档的迭代才能恢复");
        }

        sprint.setStatus(SprintStatus.COMPLETED);
        sprintMapper.updateById(sprint);

        // 记录项目活动日志
        Long userId = SecurityUtils.getCurrentUserId();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sprint_id", sprint.getId());
        detail.put("sprint_name", sprint.getName());
        projectActivityService.log(sprint.getProjectId(), userId, "restore_sprint", null, detail);

        log.info("Sprint 已恢复: id={}, name={}, projectId={}", sprint.getId(), sprint.getName(), sprint.getProjectId());
        return sprint;
    }

    /**
     * 回退 Sprint：将进行中的 Sprint 回退为计划中状态。
     * <p>
     * 用于处理「开始日期尚未到达，但 Sprint 已被激活」的异常数据情况。
     * 回退后用户可以修改日期或等待正确的开始时间再重新激活。
     *
     * @param id Sprint ID
     * @return 回退后的 Sprint
     * @throws BusinessException 当 Sprint 不是 active 状态时
     */
    @Transactional(rollbackFor = Exception.class)
    public Sprint revertToPlanned(Long id) {
        Sprint sprint = getById(id);
        projectService.assertProjectActive(sprint.getProjectId());

        if (sprint.getStatus() != SprintStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有进行中的迭代才能回退为计划中");
        }

        // 清除激活时记录的快照数据
        sprint.setStartedAt(null);
        sprint.setStartScopeHours(null);
        sprint.setStartScopeIssues(null);

        sprint.setStatus(SprintStatus.PLANNED);
        sprintMapper.updateById(sprint);

        // 记录项目活动日志
        Long userId = SecurityUtils.getCurrentUserId();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sprint_id", sprint.getId());
        detail.put("sprint_name", sprint.getName());
        detail.put("reason", "状态异常修复");
        projectActivityService.log(sprint.getProjectId(), userId, "revert_sprint_to_planned", null, detail);

        // 失效 Dashboard 缓存 — 事务提交后触发
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(sprint.getProjectId(), "sprint_reverted"));

        log.info("Sprint 已回退为计划中: id={}, name={}, projectId={}", sprint.getId(), sprint.getName(), sprint.getProjectId());
        return sprint;
    }

    /**
     * 获取 Sprint 完成预览信息：未完成工单列表 + 可迁移的目标 Sprint。
     * 使用 readOnly 事务确保未完成工单列表和可迁移目标在同一个快照中获取。
     */
    @Transactional(readOnly = true)
    public CompletionPreviewVO getCompletionPreview(Long sprintId) {
        Sprint sprint = getById(sprintId);
        projectService.assertProjectActive(sprint.getProjectId());

        CompletionPreviewVO vo = new CompletionPreviewVO();

        // 查询未关闭工单（typed resultMap 直接映射为 OpenIssueItem）
        List<CompletionPreviewVO.OpenIssueItem> openIssues = sprintMapper.selectOpenIssuesForCompletion(sprintId);
        vo.setOpenIssues(openIssues);

        // 查询同项目中可迁移的目标 Sprint（planned/active，排除自身）
        List<Sprint> candidateSprints = sprintMapper.selectList(
                new LambdaQueryWrapper<Sprint>()
                        .eq(Sprint::getProjectId, sprint.getProjectId())
                        .ne(Sprint::getId, sprintId)
                        .in(Sprint::getStatus, SprintStatus.PLANNED, SprintStatus.ACTIVE)
                        .orderByAsc(Sprint::getCreatedAt)
        );
        List<CompletionPreviewVO.TargetSprintItem> targetSprints = candidateSprints.stream().map(s -> {
            CompletionPreviewVO.TargetSprintItem item = new CompletionPreviewVO.TargetSprintItem();
            item.setId(String.valueOf(s.getId()));
            item.setName(s.getName());
            item.setStatus(s.getStatus().getValue());
            return item;
        }).collect(Collectors.toList());
        vo.setTargetSprints(targetSprints);

        return vo;
    }

    /**
     * 查找 Sprint 中未关闭的工单（is_closed = false）
     */
    private List<Issue> findOpenIssuesInSprint(Long sprintId) {
        List<Long> openIssueIds = sprintMapper.selectOpenIssueIds(sprintId);
        if (openIssueIds.isEmpty()) return List.of();
        return issueMapper.selectBatchIds(openIssueIds);
    }

    /**
     * 获取 Sprint 删除预览：展示受影响工单数量和可迁移目标。
     * 使用 readOnly 事务确保受影响工单数量和可迁移目标在同一个快照中获取。
     */
    @Transactional(readOnly = true)
    public DeletionPreviewVO getDeletionPreview(Long sprintId) {
        Sprint sprint = getById(sprintId);
        projectService.assertProjectActive(sprint.getProjectId());

        DeletionPreviewVO vo = new DeletionPreviewVO();
        vo.setSprintName(sprint.getName());

        // 日期范围描述
        if (sprint.getStartDate() != null && sprint.getEndDate() != null) {
            vo.setDateRange(sprint.getStartDate().getMonthValue() + "/" + sprint.getStartDate().getDayOfMonth()
                    + " - " + sprint.getEndDate().getMonthValue() + "/" + sprint.getEndDate().getDayOfMonth());
        }

        // 关联工单总数（包括已完成和未完成）
        Long issueCount = issueMapper.selectCount(
                new LambdaQueryWrapper<Issue>().eq(Issue::getSprintId, sprintId)
        );
        vo.setTotalIssues(issueCount.intValue());

        // 查询同项目中可迁移的目标 Sprint（planned/active，排除自身）
        List<Sprint> candidateSprints = sprintMapper.selectList(
                new LambdaQueryWrapper<Sprint>()
                        .eq(Sprint::getProjectId, sprint.getProjectId())
                        .ne(Sprint::getId, sprintId)
                        .in(Sprint::getStatus, SprintStatus.PLANNED, SprintStatus.ACTIVE)
                        .orderByAsc(Sprint::getCreatedAt)
        );
        List<DeletionPreviewVO.TargetSprintItem> targetSprints = candidateSprints.stream().map(s -> {
            DeletionPreviewVO.TargetSprintItem item = new DeletionPreviewVO.TargetSprintItem();
            item.setId(String.valueOf(s.getId()));
            item.setName(s.getName());
            item.setStatus(s.getStatus().getValue());
            return item;
        }).collect(Collectors.toList());
        vo.setTargetSprints(targetSprints);

        return vo;
    }

    @Transactional
    public void delete(Long id, DeleteSprintDTO dto) {
        Sprint sprint = getById(id);
        // 归档项目不允许删除 Sprint
        projectService.assertProjectActive(sprint.getProjectId());

        // 查询关联工单
        List<Issue> issues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>().eq(Issue::getSprintId, id)
        );

        // 有关联工单时必须处理
        if (!issues.isEmpty()) {
            if (dto == null || dto.getMoveOption() == null || dto.getMoveOption().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "该迭代包含 " + issues.size() + " 个工单，请选择处理方式");
            }

            Long newSprintId = null;
            String newSprintName = null;
            if ("next_sprint".equals(dto.getMoveOption())) {
                if (dto.getTargetSprintId() == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择目标迭代");
                }
                // 验证目标 Sprint 存在且属于同一项目
                Sprint targetSprint = sprintMapper.selectById(dto.getTargetSprintId());
                if (targetSprint == null || !targetSprint.getProjectId().equals(sprint.getProjectId())) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "目标迭代不存在或不属于当前项目");
                }
                // 仅允许移入计划中或进行中的迭代（白名单校验）
                if (targetSprint.getStatus() != SprintStatus.PLANNED && targetSprint.getStatus() != SprintStatus.ACTIVE) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "目标迭代状态为「" + targetSprint.getStatus().getLabel() + "」，只能移入计划中或进行中的迭代");
                }
                newSprintId = dto.getTargetSprintId();
                newSprintName = targetSprint.getName();
            }
            // "backlog" 时 newSprintId 和 newSprintName 保持 null

            // 批量更新工单的 sprint_id + updatedBy/updatedAt
            List<Long> issueIds = issues.stream().map(Issue::getId).collect(Collectors.toList());
            Long currentUserId = SecurityUtils.getCurrentUserId();
            LocalDateTime now = LocalDateTime.now();
            issueMapper.update(null,
                    new LambdaUpdateWrapper<Issue>()
                            .in(Issue::getId, issueIds)
                            .set(Issue::getSprintId, newSprintId)
                            .set(Issue::getUpdatedBy, currentUserId)
                            .set(Issue::getUpdatedAt, now)
            );

            // 同步 issue_sprint 关联表
            for (Long issueId : issueIds) {
                issueSprintMapper.deleteByIssueIdAndSprintId(issueId, sprint.getId());
                if (newSprintId != null) {
                    com.trackflow.issue.entity.IssueSprint rel = new com.trackflow.issue.entity.IssueSprint();
                    rel.setIssueId(issueId);
                    rel.setSprintId(newSprintId);
                    rel.setCreatedAt(now);
                    issueSprintMapper.insert(rel);
                }
            }

            // 批量记录活动日志：sprint 字段变更
            String oldSprintIdStr = String.valueOf(sprint.getId());
            String newSprintIdStr = newSprintId != null ? String.valueOf(newSprintId) : null;
            String oldSprintName = sprint.getName();
            String finalNewSprintName = newSprintName;
            List<IssueActivity> activities = issueIds.stream().map(issueId -> {
                IssueActivity activity = new IssueActivity();
                activity.setIssueId(issueId);
                activity.setUserId(currentUserId);
                activity.setAction("updated");
                activity.setFieldName("sprint");
                activity.setOldValue(oldSprintIdStr);
                activity.setNewValue(newSprintIdStr);
                activity.setOldDisplayValue(oldSprintName);
                activity.setNewDisplayValue(finalNewSprintName);
                activity.setCreatedAt(now);
                return activity;
            }).toList();
            Db.saveBatch(activities);
        }

        // 记录项目活动日志（在删除前记录，保留 Sprint 名称等信息）
        Long deleteUserId = SecurityUtils.getCurrentUserId();
        Map<String, Object> deleteDetail = new LinkedHashMap<>();
        deleteDetail.put("sprint_id", sprint.getId());
        deleteDetail.put("sprint_name", sprint.getName());
        deleteDetail.put("sprint_status", sprint.getStatus().getValue());
        if (!issues.isEmpty()) {
            deleteDetail.put("affected_issues_count", issues.size());
            deleteDetail.put("move_option", dto != null ? dto.getMoveOption() : "none");
        }
        projectActivityService.log(sprint.getProjectId(), deleteUserId, "delete_sprint", null, deleteDetail);

        // 清理默认 Sprint 设置（防止新工单被分配到已删除的 Sprint）
        clearDefaultSprintIfMatches(sprint.getProjectId(), sprint.getId());

        sprintMapper.deleteById(id);
    }

    /**
     * 如果指定 Sprint 是项目的默认 Sprint，清除该设置。
     * 在 Sprint 完成/归档/删除时调用，防止残留的 defaultSprintId 导致新工单被分配到不可用的 Sprint。
     */
    private void clearDefaultSprintIfMatches(Long projectId, Long sprintId) {
        Long currentDefault = projectService.getProjectSettingAsLong(projectId, "defaultSprintId");
        if (sprintId.equals(currentDefault)) {
            projectService.updateProjectSetting(projectId, "defaultSprintId", null);
            log.info("已清除项目 {} 的默认 Sprint 设置（Sprint {} 已不可用）", projectId, sprintId);
        }
    }
}
