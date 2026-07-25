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
import com.trackflow.issue.mapper.result.BurndownRow;
import com.trackflow.issue.mapper.result.IssueCreatedAtRow;
import com.trackflow.issue.mapper.result.IssueEstimatedHoursRow;
import com.trackflow.sprint.dto.CompleteSprintDTO;
import com.trackflow.sprint.dto.CreateSprintDTO;
import com.trackflow.sprint.dto.DeleteSprintDTO;
import com.trackflow.sprint.dto.UpdateSprintDTO;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.entity.SprintStatus;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.sprint.vo.BurndownVO;
import com.trackflow.sprint.vo.CompletionPreviewVO;
import com.trackflow.sprint.vo.CreationPreviewVO;
import com.trackflow.sprint.vo.DeletionPreviewVO;
import com.trackflow.sprint.vo.SprintAssigneeDistributionVO;
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
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintMapper sprintMapper;
    private final IssueMapper issueMapper;
    private final IssueActivityMapper activityMapper;
    private final ProjectService projectService;
    private final ProjectActivityService projectActivityService;
    private final ApplicationEventPublisher eventPublisher;

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
     * 获取 Sprint 中按负责人分组的工单分布统计。
     * 技术负责人用于评估 Sprint 工作分配均衡性。
     * 使用 readOnly 事务确保 Sprint 信息和分布统计在同一个快照中获取。
     */
    @Transactional(readOnly = true)
    public SprintAssigneeDistributionVO getAssigneeDistribution(Long sprintId) {
        Sprint sprint = getById(sprintId);
        List<Map<String, Object>> rows = sprintMapper.selectAssigneeDistribution(sprintId);

        SprintAssigneeDistributionVO result = new SprintAssigneeDistributionVO();
        result.setSprintId(String.valueOf(sprint.getId()));
        result.setSprintName(sprint.getName());

        List<SprintAssigneeDistributionVO.AssigneeItem> assignees = new ArrayList<>();
        int totalIssues = 0;
        int unassignedCount = 0;
        double totalEstimatedHours = 0;
        double unassignedEstimatedHours = 0;

        for (Map<String, Object> row : rows) {
            Object userIdObj = row.get("user_id");
            String displayName = (String) row.get("display_name");
            int issueCount = ((Number) row.get("issue_count")).intValue();
            int doneCount = ((Number) row.get("done_count")).intValue();
            int inProgressCount = ((Number) row.get("in_progress_count")).intValue();
            int todoCount = ((Number) row.get("todo_count")).intValue();
            double estimatedHoursTotal = ((Number) row.get("estimated_hours_total")).doubleValue();

            totalIssues += issueCount;
            totalEstimatedHours += estimatedHoursTotal;

            if (userIdObj == null) {
                // 未分配负责人的统计
                unassignedCount = issueCount;
                unassignedEstimatedHours = estimatedHoursTotal;
            } else {
                SprintAssigneeDistributionVO.AssigneeItem item = new SprintAssigneeDistributionVO.AssigneeItem();
                item.setUserId(String.valueOf(userIdObj));
                item.setDisplayName(displayName != null ? displayName : "未知用户");
                item.setIssueCount(issueCount);
                item.setDoneCount(doneCount);
                item.setInProgressCount(inProgressCount);
                item.setTodoCount(todoCount);
                item.setEstimatedHoursTotal(estimatedHoursTotal);
                assignees.add(item);
            }
        }

        result.setTotalIssues(totalIssues);
        result.setUnassignedCount(unassignedCount);
        result.setTotalEstimatedHours(totalEstimatedHours);
        result.setUnassignedEstimatedHours(unassignedEstimatedHours);
        result.setAssignees(assignees);
        return result;
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
        // 查找源 Sprint：优先查找活跃 Sprint，若无则查找最近完成的 Sprint
        // 对标 YouTrack 行为：Sprint 自动完成后工单保留在该 Sprint 中，
        // 创建新 Sprint 时用户可选择"迁移未完成工单"
        Sprint sourceSprint = sprintMapper.selectOne(
                new LambdaQueryWrapper<Sprint>()
                        .eq(Sprint::getProjectId, projectId)
                        .eq(Sprint::getStatus, SprintStatus.ACTIVE)
        );
        if (sourceSprint == null) {
            // 无活跃 Sprint，尝试查找最近完成的 Sprint（自动完成后工单仍在其中）
            sourceSprint = sprintMapper.selectOne(
                    new LambdaQueryWrapper<Sprint>()
                            .eq(Sprint::getProjectId, projectId)
                            .eq(Sprint::getStatus, SprintStatus.COMPLETED)
                            .ne(Sprint::getId, newSprint.getId())
                            .orderByDesc(Sprint::getUpdatedAt)
                            .last("LIMIT 1")
            );
        }
        if (sourceSprint == null) {
            log.warn("项目 {} 没有活跃或已完成的 Sprint，跳过移入未完成工单", projectId);
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

        // 查找活跃 Sprint
        Sprint sourceSprint = sprintMapper.selectOne(
                new LambdaQueryWrapper<Sprint>()
                        .eq(Sprint::getProjectId, projectId)
                        .eq(Sprint::getStatus, SprintStatus.ACTIVE)
        );

        // 如果无活跃 Sprint，尝试查找最近完成的 Sprint（其中可能有未关闭工单）
        if (sourceSprint == null) {
            sourceSprint = sprintMapper.selectOne(
                    new LambdaQueryWrapper<Sprint>()
                            .eq(Sprint::getProjectId, projectId)
                            .eq(Sprint::getStatus, SprintStatus.COMPLETED)
                            .orderByDesc(Sprint::getUpdatedAt)
                            .last("LIMIT 1")
            );
        }

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
        if (dto.getStartDate() != null) {
            String oldDate = sprint.getStartDate() != null ? sprint.getStartDate().toString() : "";
            if (!dto.getStartDate().equals(sprint.getStartDate())) {
                changes.add(buildFieldChange("start_date", oldDate, dto.getStartDate().toString()));
            }
            sprint.setStartDate(dto.getStartDate());
        }
        if (dto.getEndDate() != null) {
            String oldDate = sprint.getEndDate() != null ? sprint.getEndDate().toString() : "";
            if (!dto.getEndDate().equals(sprint.getEndDate())) {
                changes.add(buildFieldChange("end_date", oldDate, dto.getEndDate().toString()));
            }
            sprint.setEndDate(dto.getEndDate());
        }

        // 日期合理性校验：如果两个日期都存在，开始必须早于结束
        validateDateRange(sprint.getStartDate(), sprint.getEndDate());

        // 日期重叠检测：仅在日期有变更且未确认时触发
        boolean dateChanged = (dto.getStartDate() != null || dto.getEndDate() != null);
        if (dateChanged && !Boolean.TRUE.equals(dto.getConfirmOverlap())) {
            checkDateOverlap(sprint.getProjectId(), sprint.getStartDate(), sprint.getEndDate(), id);
        }

        sprintMapper.updateById(sprint);

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
        sprintMapper.updateById(sprint);

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
    public Sprint complete(Long id, CompleteSprintDTO dto) {
        Sprint sprint = getById(id);
        // 归档项目不允许操作 Sprint
        projectService.assertProjectActive(sprint.getProjectId());

        if (sprint.getStatus() != SprintStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有进行中的迭代才能完成");
        }

        // 查找未关闭工单（JOIN issue_status 判断 is_closed）
        List<Issue> openIssues = findOpenIssuesInSprint(id);

        // 有未完成工单时必须传入处理方式
        if (!openIssues.isEmpty()) {
            if (dto == null || dto.getMoveOption() == null || dto.getMoveOption().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "该迭代中仍有 " + openIssues.size() + " 个未完成工单，请选择处理方式");
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

        // 通知项目成员 Sprint 已完成（已完成工单数 = 总工单 - 未关闭工单）— 事务提交后触发
        long totalIssuesInSprint = issueMapper.selectCount(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getSprintId, id)
                        .isNull(Issue::getDeletedAt)
        );
        int completedIssues = (int) (totalIssuesInSprint - openIssues.size());
        eventPublisher.publishEvent(new SprintNotificationEvent.Completed(sprint, Math.max(completedIssues, 0), completeUserId));

        // 失效 Dashboard 缓存 — 事务提交后触发
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(sprint.getProjectId(), "sprint_completed"));

        return sprint;
    }

    /**
     * 归档 Sprint：将已完成的 Sprint 归档，从主列表中隐藏但保留数据。
     * 只有 completed 状态的 Sprint 才能归档。
     *
     * @param id Sprint ID
     * @return 归档后的 Sprint
     * @throws BusinessException 当 Sprint 不是 completed 状态时
     */
    @Transactional(rollbackFor = Exception.class)
    public Sprint archive(Long id) {
        Sprint sprint = getById(id);
        projectService.assertProjectActive(sprint.getProjectId());

        if (sprint.getStatus() != SprintStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有已完成的迭代才能归档");
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
     * 获取 Sprint 燃尽图数据。
     * <p>
     * 算法：遍历 Sprint 日期范围的每一天，计算该天结束时剩余未关闭工单数。
     * 使用 issue.resolved_at 来判断工单何时被关闭（resolved_at 的日期 ≤ 当天 → 已关闭）。
     * <p>
     * 使用 readOnly 事务确保多步查询在同一个数据库快照中执行，避免并发修改导致数据不一致。
     */
    @Transactional(readOnly = true)
    public BurndownVO getBurndownData(Long sprintId) {
        return getBurndownData(sprintId, "issue_count");
    }

    /**
     * 获取 Sprint 燃尽图数据。
     *
     * @param sprintId Sprint ID
     * @param mode     计算模式: "issue_count"（工单数）或 "estimation"（预估工时）
     */
    @Transactional(readOnly = true)
    public BurndownVO getBurndownData(Long sprintId, String mode) {
        Sprint sprint = getById(sprintId);

        BurndownVO vo = new BurndownVO();
        vo.setSprintId(String.valueOf(sprint.getId()));
        vo.setSprintName(sprint.getName());
        vo.setMode(mode);

        // 设置快照工时信息
        if (sprint.getStartScopeHours() != null) {
            vo.setStartScopeHours(sprint.getStartScopeHours().doubleValue());
        }

        if (sprint.getStartDate() == null || sprint.getEndDate() == null) {
            return buildEmptyBurndown(vo);
        }

        LocalDate sprintStart = sprint.getStartDate();
        LocalDate sprintEnd = sprint.getEndDate();
        LocalDate today = LocalDate.now();
        long totalDays = Math.max(1, sprintStart.until(sprintEnd).getDays());

        if ("estimation".equals(mode)) {
            // 估时模式：基于 estimated_hours 计算燃尽
            calculateEstimationBurndown(vo, sprint, sprintStart, sprintEnd, today, totalDays);
        } else {
            // 工单数模式：原有逻辑
            BurndownRawData rawData = loadBurndownRawData(sprintId);
            ScopeTimeline scopeTimeline = buildScopeChangeTimeline(rawData, sprintStart);
            calculateDailyMetrics(vo, scopeTimeline, rawData, sprintStart, sprintEnd, today, totalDays);
            calculateVelocityAndForecast(vo, scopeTimeline.startScope, today, totalDays);
            vo.setTotalIssues(rawData.currentIssues.size());
            vo.setStartScopeIssues((int) scopeTimeline.startScope);
        }

        return vo;
    }

    // ==================== 燃尽图私有辅助方法 ====================

    /**
     * 构建空燃尽图响应（Sprint 缺少日期范围时使用）。
     */
    private BurndownVO buildEmptyBurndown(BurndownVO vo) {
        vo.setDates(List.of());
        vo.setIdealLine(List.of());
        vo.setActualLine(List.of());
        vo.setScopeLine(List.of());
        vo.setTodayIndex(-1);
        vo.setTotalIssues(0);
        vo.setStartScopeIssues(0);
        vo.setVelocity(0.0);
        vo.setForecastDate(null);
        return vo;
    }

    /**
     * 燃尽图原始数据聚合结构。
     */
    private record BurndownIssueData(Long id, LocalDateTime createdAt, LocalDateTime resolvedAt, BigDecimal estimatedHours) {}

    private record BurndownRawData(
            List<BurndownIssueData> currentIssues,
            Set<Long> currentIssueIds,
            Map<Long, LocalDateTime> movedInMap,
            List<IssueActivity> movedOutActivities,
            Map<Long, LocalDateTime> movedOutCreatedAtMap,
            Map<Long, BigDecimal> issueEstimatedHoursMap
    ) {}

    /**
     * 加载燃尽图计算所需的全部原始数据（Sprint 工单投影 + 活动记录 + 估时数据）。
     */
    private BurndownRawData loadBurndownRawData(Long sprintId) {
        List<BurndownRow> projections = issueMapper.selectBurndownProjection(sprintId);
        List<Long> currentIssueIdList = projections.stream().map(BurndownRow::getId).toList();

        // 批量查询当前工单的估时（消除 N+1）
        Map<Long, BigDecimal> currentEstimatedHoursMap = new HashMap<>();
        if (!currentIssueIdList.isEmpty()) {
            List<IssueEstimatedHoursRow> hoursRows = issueMapper.selectEstimatedHoursByIds(currentIssueIdList);
            for (IssueEstimatedHoursRow row : hoursRows) {
                currentEstimatedHoursMap.put(row.getId(), row.getEstimatedHours());
            }
        }

        List<BurndownIssueData> currentIssues = projections.stream()
                .map(row -> new BurndownIssueData(
                        row.getId(), row.getCreatedAt(), row.getResolvedAt(),
                        currentEstimatedHoursMap.get(row.getId())
                ))
                .toList();

        Set<Long> currentIssueIds = currentIssues.stream()
                .map(BurndownIssueData::id)
                .collect(Collectors.toSet());

        String sprintIdStr = String.valueOf(sprintId);
        List<IssueActivity> movedInActivities = activityMapper.selectMovedInBySprint(sprintIdStr);
        List<IssueActivity> movedOutActivities = activityMapper.selectMovedOutBySprint(sprintIdStr);

        Map<Long, LocalDateTime> movedInMap = movedInActivities.stream()
                .collect(Collectors.toMap(
                        IssueActivity::getIssueId,
                        IssueActivity::getCreatedAt,
                        (a, b) -> b.isAfter(a) ? b : a
                ));

        Map<Long, LocalDateTime> movedOutCreatedAtMap = loadMovedOutCreatedAtMap(
                movedOutActivities, currentIssueIds, movedInMap);

        // 批量查询已移出工单的估时（用于估时模式 scope 追踪）
        // 已移出工单不在 currentIssueIds 中，需要单独查询其估时
        Map<Long, BigDecimal> issueEstimatedHoursMap = new HashMap<>(currentEstimatedHoursMap);
        List<Long> movedOutOnlyIds = movedOutActivities.stream()
                .map(IssueActivity::getIssueId)
                .filter(id -> !currentIssueIds.contains(id))
                .distinct()
                .toList();
        if (!movedOutOnlyIds.isEmpty()) {
            List<IssueEstimatedHoursRow> movedOutHoursRows = issueMapper.selectEstimatedHoursByIds(movedOutOnlyIds);
            for (IssueEstimatedHoursRow row : movedOutHoursRows) {
                issueEstimatedHoursMap.put(row.getId(), row.getEstimatedHours());
            }
        }

        return new BurndownRawData(currentIssues, currentIssueIds, movedInMap,
                movedOutActivities, movedOutCreatedAtMap, issueEstimatedHoursMap);
    }

    /**
     * 批量获取已移出工单的 createdAt（消除 N+1 查询）。
     */
    private Map<Long, LocalDateTime> loadMovedOutCreatedAtMap(
            List<IssueActivity> movedOutActivities,
            Set<Long> currentIssueIds,
            Map<Long, LocalDateTime> movedInMap) {

        List<Long> idsNeedingCreatedAt = movedOutActivities.stream()
                .filter(a -> !currentIssueIds.contains(a.getIssueId()))
                .filter(a -> !movedInMap.containsKey(a.getIssueId()))
                .map(IssueActivity::getIssueId)
                .distinct()
                .toList();

        if (idsNeedingCreatedAt.isEmpty()) {
            return Map.of();
        }

        Map<Long, LocalDateTime> result = new HashMap<>();
        List<IssueCreatedAtRow> createdAtRows = issueMapper.selectCreatedAtByIds(idsNeedingCreatedAt);
        for (IssueCreatedAtRow row : createdAtRows) {
            result.put(row.getId(), row.getCreatedAt());
        }
        return result;
    }

    /**
     * Scope 变化时间线聚合结构。
     */
    private record ScopeTimeline(
            Map<LocalDate, Long> addedByDay,
            Map<LocalDate, Long> removedByDay,
            Map<LocalDate, Long> resolvedByDay,
            long startScope
    ) {}

    /**
     * 估时模式的 Scope 变化时间线聚合结构（以工时 double 为单位）。
     */
    private record EstimationScopeTimeline(
            Map<LocalDate, Double> hoursAddedByDay,
            Map<LocalDate, Double> hoursRemovedByDay,
            Map<LocalDate, Double> resolvedHoursByDay,
            double startScopeHours
    ) {}

    /**
     * 构建每日估时 scope 变化时间线（追踪工单加入/移出 Sprint 带来的工时变化）。
     * <p>
     * 对标 YouTrack "Remaining effort" 线：Scope 线在工单加入时上升，移出时下降。
     * 与 issue_count 模式的 buildScopeChangeTimeline() 逻辑对称，但统计单位为工时而非工单数。
     */
    private EstimationScopeTimeline buildEstimationScopeTimeline(BurndownRawData rawData,
                                                                  LocalDate sprintStart) {
        Map<LocalDate, Double> hoursAddedByDay = new LinkedHashMap<>();
        Map<LocalDate, Double> hoursRemovedByDay = new LinkedHashMap<>();

        // 辅助方法：安全获取工单工时（为 null 时返回 0.0）
        // 当前 Sprint 中工单的加入时间及对应工时
        for (BurndownIssueData issue : rawData.currentIssues) {
            LocalDateTime movedInAt = rawData.movedInMap.get(issue.id());
            LocalDate enteredDate = (movedInAt != null)
                    ? movedInAt.toLocalDate()
                    : issue.createdAt().toLocalDate();
            double hours = issue.estimatedHours() != null ? issue.estimatedHours().doubleValue() : 0.0;
            hoursAddedByDay.merge(enteredDate, hours, Double::sum);
        }

        // 已移出 Sprint 的工单：记录移出时间及工时（从 issueEstimatedHoursMap 获取）
        for (IssueActivity movedOut : rawData.movedOutActivities) {
            Long issueId = movedOut.getIssueId();
            BigDecimal estimatedBd = rawData.issueEstimatedHoursMap.get(issueId);
            double hours = estimatedBd != null ? estimatedBd.doubleValue() : 0.0;

            hoursRemovedByDay.merge(movedOut.getCreatedAt().toLocalDate(), hours, Double::sum);

            // 若该工单当前不在 Sprint 中，还需补记其加入时间对应的工时
            if (!rawData.currentIssueIds.contains(issueId)) {
                LocalDateTime correspondingMovedIn = rawData.movedInMap.get(issueId);
                LocalDate addedDate;
                if (correspondingMovedIn != null) {
                    addedDate = correspondingMovedIn.toLocalDate();
                } else {
                    LocalDateTime createdAt = rawData.movedOutCreatedAtMap.get(issueId);
                    addedDate = createdAt != null ? createdAt.toLocalDate() : null;
                }
                if (addedDate != null) {
                    hoursAddedByDay.merge(addedDate, hours, Double::sum);
                }
            }
        }

        // 按解决日期分组统计已完成工时
        Map<LocalDate, Double> resolvedHoursByDay = rawData.currentIssues.stream()
                .filter(issue -> issue.resolvedAt() != null && issue.estimatedHours() != null)
                .collect(Collectors.groupingBy(
                        issue -> issue.resolvedAt().toLocalDate(),
                        Collectors.summingDouble(issue -> issue.estimatedHours().doubleValue())
                ));

        // 计算 Sprint 开始时的 scope（工时）
        double startScopeHours = 0.0;
        for (Map.Entry<LocalDate, Double> entry : hoursAddedByDay.entrySet()) {
            if (!entry.getKey().isAfter(sprintStart)) {
                startScopeHours += entry.getValue();
            }
        }
        for (Map.Entry<LocalDate, Double> entry : hoursRemovedByDay.entrySet()) {
            if (!entry.getKey().isAfter(sprintStart)) {
                startScopeHours -= entry.getValue();
            }
        }
        startScopeHours = Math.max(0.0, startScopeHours);

        return new EstimationScopeTimeline(hoursAddedByDay, hoursRemovedByDay,
                resolvedHoursByDay, startScopeHours);
    }

    /**
     * 构建每日 scope 变化时间线（工单加入/移出/解决的每日统计）。
     */
    private ScopeTimeline buildScopeChangeTimeline(BurndownRawData rawData, LocalDate sprintStart) {
        Map<LocalDate, Long> addedByDay = new LinkedHashMap<>();
        Map<LocalDate, Long> removedByDay = new LinkedHashMap<>();

        // 当前 Sprint 中工单的加入时间
        for (BurndownIssueData issue : rawData.currentIssues) {
            LocalDateTime movedInAt = rawData.movedInMap.get(issue.id());
            LocalDate enteredDate = (movedInAt != null)
                    ? movedInAt.toLocalDate()
                    : issue.createdAt().toLocalDate();
            addedByDay.merge(enteredDate, 1L, Long::sum);
        }

        // 已移出 Sprint 的工单
        for (IssueActivity movedOut : rawData.movedOutActivities) {
            removedByDay.merge(movedOut.getCreatedAt().toLocalDate(), 1L, Long::sum);

            LocalDateTime correspondingMovedIn = rawData.movedInMap.get(movedOut.getIssueId());
            if (correspondingMovedIn != null) {
                if (!rawData.currentIssueIds.contains(movedOut.getIssueId())) {
                    addedByDay.merge(correspondingMovedIn.toLocalDate(), 1L, Long::sum);
                }
            } else {
                LocalDateTime createdAt = rawData.movedOutCreatedAtMap.get(movedOut.getIssueId());
                if (createdAt != null) {
                    addedByDay.merge(createdAt.toLocalDate(), 1L, Long::sum);
                }
            }
        }

        // 按解决日期分组统计
        Map<LocalDate, Long> resolvedByDay = rawData.currentIssues.stream()
                .filter(issue -> issue.resolvedAt() != null)
                .collect(Collectors.groupingBy(
                        issue -> issue.resolvedAt().toLocalDate(),
                        Collectors.counting()
                ));

        // 计算 Sprint 开始时的 scope
        long startScope = calculateStartScope(addedByDay, removedByDay, sprintStart);

        return new ScopeTimeline(addedByDay, removedByDay, resolvedByDay, startScope);
    }

    /**
     * 计算 Sprint 开始时的实际范围（开始日期之前或当天加入的工单数 - 移出的工单数）。
     */
    private long calculateStartScope(Map<LocalDate, Long> addedByDay,
                                     Map<LocalDate, Long> removedByDay,
                                     LocalDate sprintStart) {
        long startScope = 0;
        for (Map.Entry<LocalDate, Long> entry : addedByDay.entrySet()) {
            if (!entry.getKey().isAfter(sprintStart)) {
                startScope += entry.getValue();
            }
        }
        for (Map.Entry<LocalDate, Long> entry : removedByDay.entrySet()) {
            if (!entry.getKey().isAfter(sprintStart)) {
                startScope -= entry.getValue();
            }
        }
        return Math.max(0, startScope);
    }

    /**
     * 计算每日的 ideal/actual/scope 指标并填充到 VO 中。
     */
    private void calculateDailyMetrics(BurndownVO vo, ScopeTimeline timeline,
                                       BurndownRawData rawData,
                                       LocalDate sprintStart, LocalDate sprintEnd,
                                       LocalDate today, long totalDays) {
        double idealDecrement = timeline.startScope > 0 ? (double) timeline.startScope / totalDays : 0.0;

        List<String> dates = new ArrayList<>();
        List<Double> idealLine = new ArrayList<>();
        List<Integer> actualLine = new ArrayList<>();
        List<Integer> scopeLine = new ArrayList<>();

        long scope = timeline.startScope;
        long resolved = calculateResolvedBeforeStart(timeline.resolvedByDay, sprintStart);
        double idealRemaining = timeline.startScope;
        int todayIndex = -1;

        LocalDate endForActual = today.isBefore(sprintEnd) ? today : sprintEnd;
        LocalDate current = sprintStart;
        int dayIndex = 0;

        while (!current.isAfter(sprintEnd)) {
            dates.add(current.toString());
            idealLine.add(Math.max(0, Math.round(idealRemaining * 10.0) / 10.0));
            idealRemaining -= idealDecrement;

            if (!current.isAfter(endForActual)) {
                if (current.isAfter(sprintStart)) {
                    scope += timeline.addedByDay.getOrDefault(current, 0L);
                    scope -= timeline.removedByDay.getOrDefault(current, 0L);
                }
                resolved += timeline.resolvedByDay.getOrDefault(current, 0L);
                scopeLine.add((int) Math.max(0, scope));
                actualLine.add((int) Math.max(0, scope - resolved));
            }

            if (current.isEqual(today)) {
                todayIndex = dayIndex;
            }
            current = current.plusDays(1);
            dayIndex++;
        }

        vo.setDates(dates);
        vo.setIdealLine(idealLine);
        vo.setActualLine(actualLine);
        vo.setScopeLine(scopeLine);
        vo.setTodayIndex(todayIndex);
    }

    /**
     * 统计 Sprint 开始前已完成的工单数。
     */
    private long calculateResolvedBeforeStart(Map<LocalDate, Long> resolvedByDay, LocalDate sprintStart) {
        return resolvedByDay.entrySet().stream()
                .filter(entry -> entry.getKey().isBefore(sprintStart))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    /**
     * 计算日均完成速率和预测完成日期。
     */
    private void calculateVelocityAndForecast(BurndownVO vo, long startScope,
                                              LocalDate today, long totalDays) {
        int todayIndex = vo.getTodayIndex();
        int daysElapsed = todayIndex >= 0 ? todayIndex + 1 : (int) totalDays;

        // 从 actualLine 反推已完成数：scope - actual = resolved
        long resolved = 0;
        long scope = startScope;
        if (!vo.getActualLine().isEmpty() && !vo.getScopeLine().isEmpty()) {
            int lastIdx = vo.getActualLine().size() - 1;
            scope = vo.getScopeLine().get(lastIdx);
            long actual = vo.getActualLine().get(lastIdx);
            resolved = scope - actual;
        }

        double velocity = daysElapsed > 0 ? (double) resolved / daysElapsed : 0.0;
        vo.setVelocity(Math.round(velocity * 100.0) / 100.0);

        long remaining = scope - resolved;
        if (velocity > 0 && remaining > 0) {
            long daysNeeded = (long) Math.ceil(remaining / velocity);
            vo.setForecastDate(today.plusDays(daysNeeded).toString());
        } else if (remaining <= 0) {
            vo.setForecastDate(today.toString());
        } else {
            vo.setForecastDate(null);
        }
    }

    /**
     * 估时模式燃尽图：基于 estimated_hours 计算理想线/实际线/范围线。
     * <p>
     * 理想线从 startScopeHours（快照）线性递减到 0。
     * 实际线为每天结束时未完成工单的 estimated_hours 总和。
     * 范围线为每天的所有工单（含已完成）的 estimated_hours 总和。
     */
    private void calculateEstimationBurndown(BurndownVO vo, Sprint sprint,
                                             LocalDate sprintStart, LocalDate sprintEnd,
                                             LocalDate today, long totalDays) {
        Long sprintId = sprint.getId();

        // 加载原始数据（含活动记录、工时 Map）
        BurndownRawData rawData = loadBurndownRawData(sprintId);

        // 构建估时 scope 变化时间线
        EstimationScopeTimeline timeline = buildEstimationScopeTimeline(rawData, sprintStart);

        // 理想线起点优先使用快照值（Sprint 激活时的工时总量），以保证理想线固定不变
        double startHours;
        if (sprint.getStartScopeHours() != null && sprint.getStartScopeHours().compareTo(BigDecimal.ZERO) > 0) {
            startHours = sprint.getStartScopeHours().doubleValue();
        } else {
            // 回退：使用动态时间线计算的 Sprint 开始时工时
            startHours = timeline.startScopeHours();
        }

        // 计算每日指标
        double idealDecrement = startHours > 0 ? startHours / totalDays : 0.0;
        List<String> dates = new ArrayList<>();
        List<Double> idealLine = new ArrayList<>();
        List<Integer> actualLine = new ArrayList<>();
        List<Integer> scopeLine = new ArrayList<>();

        double idealRemaining = startHours;
        double scope = timeline.startScopeHours();
        double resolvedHours = calculateResolvedHoursBeforeStart(timeline.resolvedHoursByDay(), sprintStart);
        int todayIndex = -1;
        LocalDate endForActual = today.isBefore(sprintEnd) ? today : sprintEnd;
        LocalDate current = sprintStart;
        int dayIndex = 0;

        while (!current.isAfter(sprintEnd)) {
            dates.add(current.toString());
            idealLine.add(Math.max(0, Math.round(idealRemaining * 10.0) / 10.0));
            idealRemaining -= idealDecrement;

            if (!current.isAfter(endForActual)) {
                // 动态更新当日 scope：当天加入的工时 - 当天移出的工时（首日 scope 已在 timeline 中初始化）
                if (current.isAfter(sprintStart)) {
                    scope += timeline.hoursAddedByDay().getOrDefault(current, 0.0);
                    scope -= timeline.hoursRemovedByDay().getOrDefault(current, 0.0);
                    scope = Math.max(0.0, scope);
                }
                resolvedHours += timeline.resolvedHoursByDay().getOrDefault(current, 0.0);
                scopeLine.add((int) Math.round(scope));
                actualLine.add((int) Math.round(Math.max(0, scope - resolvedHours)));
            }

            if (current.isEqual(today)) {
                todayIndex = dayIndex;
            }
            current = current.plusDays(1);
            dayIndex++;
        }

        vo.setDates(dates);
        vo.setIdealLine(idealLine);
        vo.setActualLine(actualLine);
        vo.setScopeLine(scopeLine);
        vo.setTodayIndex(todayIndex);
        vo.setTotalIssues(rawData.currentIssues().size());
        vo.setStartScopeIssues(sprint.getStartScopeIssues() != null
                ? sprint.getStartScopeIssues()
                : rawData.currentIssues().size());
        vo.setStartScopeHours(startHours);

        // 计算速率和预测（基于工时）
        int daysElapsed = todayIndex >= 0 ? todayIndex + 1 : (int) totalDays;
        double hoursVelocity = daysElapsed > 0 ? resolvedHours / daysElapsed : 0.0;
        vo.setVelocity(Math.round(hoursVelocity * 100.0) / 100.0);

        // 最新 scope 和实际剩余用于预测
        double latestScope = scopeLine.isEmpty() ? scope : scopeLine.get(scopeLine.size() - 1);
        double remainingHours = Math.max(0.0, latestScope - resolvedHours);
        if (hoursVelocity > 0 && remainingHours > 0) {
            long daysNeeded = (long) Math.ceil(remainingHours / hoursVelocity);
            vo.setForecastDate(today.plusDays(daysNeeded).toString());
        } else if (remainingHours <= 0) {
            vo.setForecastDate(today.toString());
        } else {
            vo.setForecastDate(null);
        }
    }

    /**
     * 统计 Sprint 开始前已完成的工时总量。
     */
    private double calculateResolvedHoursBeforeStart(Map<LocalDate, Double> resolvedHoursByDay,
                                                      LocalDate sprintStart) {
        return resolvedHoursByDay.entrySet().stream()
                .filter(entry -> entry.getKey().isBefore(sprintStart))
                .mapToDouble(Map.Entry::getValue)
                .sum();
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
