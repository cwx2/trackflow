package com.trackflow.sprint.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.trackflow.sprint.vo.BurndownVO;
import com.trackflow.sprint.vo.CompletionPreviewVO;
import com.trackflow.sprint.vo.CreationPreviewVO;
import com.trackflow.sprint.vo.DeletionPreviewVO;
import com.trackflow.sprint.vo.SprintVO;
import com.trackflow.project.entity.Project;
import com.trackflow.project.service.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintMapper sprintMapper;
    private final IssueMapper issueMapper;
    private final IssueActivityMapper activityMapper;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    /**
     * 查询项目的 Sprint 列表（带工单统计 + 状态推导）。
     * MyBatis resultMap 直接映射为 SprintVO，然后根据日期推导状态一致性。
     */
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

    @Transactional
    public Sprint create(Long projectId, CreateSprintDTO dto) {
        // 归档项目不允许创建 Sprint
        projectService.assertProjectActive(projectId);

        Sprint sprint = new Sprint();
        sprint.setProjectId(projectId);
        sprint.setName(dto.getName());
        sprint.setGoal(dto.getGoal());
        sprint.setStartDate(dto.getStartDate());
        sprint.setEndDate(dto.getEndDate());
        sprint.setStatus(SprintStatus.PLANNED);
        sprintMapper.insert(sprint);

        Long currentUserId = SecurityUtils.getCurrentUserId();

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
        // 查找当前活跃 Sprint
        Sprint activeSprint = sprintMapper.selectOne(
                new LambdaQueryWrapper<Sprint>()
                        .eq(Sprint::getProjectId, projectId)
                        .eq(Sprint::getStatus, SprintStatus.ACTIVE)
        );
        if (activeSprint == null) {
            log.warn("项目 {} 没有活跃 Sprint，跳过移入未完成工单", projectId);
            return;
        }

        // 查找活跃 Sprint 中未关闭的工单
        List<Long> openIssueIds = sprintMapper.selectOpenIssueIds(activeSprint.getId());
        if (openIssueIds.isEmpty()) {
            log.info("活跃 Sprint {} 中无未完成工单，跳过", activeSprint.getName());
            return;
        }

        // 批量更新 sprint_id
        issueMapper.update(null,
                new LambdaUpdateWrapper<Issue>()
                        .in(Issue::getId, openIssueIds)
                        .set(Issue::getSprintId, newSprint.getId())
        );

        // 为每个被移动的工单记录活动日志
        LocalDateTime now = LocalDateTime.now();
        for (Long issueId : openIssueIds) {
            IssueActivity activity = new IssueActivity();
            activity.setIssueId(issueId);
            activity.setUserId(currentUserId);
            activity.setAction("updated");
            activity.setFieldName("sprint");
            activity.setOldValue(activeSprint.getName());
            activity.setNewValue(newSprint.getName());
            activity.setCreatedAt(now);
            activityMapper.insert(activity);
        }

        log.info("已将 {} 个未完成工单从 Sprint '{}' 移入新 Sprint '{}'",
                openIssueIds.size(), activeSprint.getName(), newSprint.getName());
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
     * - 是否存在活跃 Sprint 及其未完成工单数
     * - 是否已设置默认 Sprint
     */
    public CreationPreviewVO getCreationPreview(Long projectId) {
        CreationPreviewVO vo = new CreationPreviewVO();

        // 查找活跃 Sprint
        Sprint activeSprint = sprintMapper.selectOne(
                new LambdaQueryWrapper<Sprint>()
                        .eq(Sprint::getProjectId, projectId)
                        .eq(Sprint::getStatus, SprintStatus.ACTIVE)
        );

        if (activeSprint != null) {
            vo.setActiveSprintId(String.valueOf(activeSprint.getId()));
            vo.setActiveSprintName(activeSprint.getName());
            List<Long> openIssueIds = sprintMapper.selectOpenIssueIds(activeSprint.getId());
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

        boolean isCompleted = sprint.getStatus() == SprintStatus.COMPLETED;

        // 已完成 Sprint 只允许修改 name 和 goal（用于归档标注），不允许修改日期
        if (isCompleted && (dto.getStartDate() != null || dto.getEndDate() != null)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已完成的迭代不允许修改日期");
        }

        if (dto.getName() != null) {
            String trimmed = dto.getName().trim();
            if (trimmed.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "迭代名称不能为空");
            }
            sprint.setName(trimmed);
        }
        if (dto.getGoal() != null) {
            sprint.setGoal(dto.getGoal());
        }
        if (dto.getStartDate() != null) {
            sprint.setStartDate(dto.getStartDate());
        }
        if (dto.getEndDate() != null) {
            sprint.setEndDate(dto.getEndDate());
        }

        // 日期合理性校验：如果两个日期都存在，开始必须早于结束
        LocalDate start = sprint.getStartDate();
        LocalDate end = sprint.getEndDate();
        if (start != null && end != null && !start.isBefore(end)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "开始日期必须早于结束日期");
        }

        sprintMapper.updateById(sprint);
        return sprint;
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

        sprint.setStatus(SprintStatus.ACTIVE);
        sprintMapper.updateById(sprint);
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
            if ("next_sprint".equals(dto.getMoveOption())) {
                if (dto.getTargetSprintId() == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择目标迭代");
                }
                // 验证目标 Sprint 存在且属于同一项目
                Sprint targetSprint = sprintMapper.selectById(dto.getTargetSprintId());
                if (targetSprint == null || !targetSprint.getProjectId().equals(sprint.getProjectId())) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "目标迭代不存在或不属于当前项目");
                }
                if (targetSprint.getStatus() == SprintStatus.COMPLETED) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "目标迭代已完成，无法移入");
                }
                newSprintId = dto.getTargetSprintId();
            }
            // "backlog" 时 newSprintId 保持 null

            // 批量更新工单的 sprint_id（使用 LambdaUpdateWrapper 以支持 set null）
            List<Long> openIssueIds = openIssues.stream().map(Issue::getId).collect(Collectors.toList());
            issueMapper.update(null,
                    new LambdaUpdateWrapper<Issue>()
                            .in(Issue::getId, openIssueIds)
                            .set(Issue::getSprintId, newSprintId)
            );
        }

        // 完成 Sprint
        sprint.setStatus(SprintStatus.COMPLETED);
        sprintMapper.updateById(sprint);
        return sprint;
    }

    /**
     * 获取 Sprint 完成预览信息：未完成工单列表 + 可迁移的目标 Sprint
     */
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
     */
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
            if ("next_sprint".equals(dto.getMoveOption())) {
                if (dto.getTargetSprintId() == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择目标迭代");
                }
                // 验证目标 Sprint 存在且属于同一项目
                Sprint targetSprint = sprintMapper.selectById(dto.getTargetSprintId());
                if (targetSprint == null || !targetSprint.getProjectId().equals(sprint.getProjectId())) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "目标迭代不存在或不属于当前项目");
                }
                if (targetSprint.getStatus() == SprintStatus.COMPLETED) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "目标迭代已完成，无法移入");
                }
                newSprintId = dto.getTargetSprintId();
            }
            // "backlog" 时 newSprintId 保持 null

            // 批量更新工单的 sprint_id
            List<Long> issueIds = issues.stream().map(Issue::getId).collect(Collectors.toList());
            issueMapper.update(null,
                    new LambdaUpdateWrapper<Issue>()
                            .in(Issue::getId, issueIds)
                            .set(Issue::getSprintId, newSprintId)
            );
        }

        sprintMapper.deleteById(id);
    }

    /**
     * 获取 Sprint 燃尽图数据。
     * <p>
     * 算法：遍历 Sprint 日期范围的每一天，计算该天结束时剩余未关闭工单数。
     * 使用 issue.resolved_at 来判断工单何时被关闭（resolved_at 的日期 ≤ 当天 → 已关闭）。
     */
    public BurndownVO getBurndownData(Long sprintId) {
        Sprint sprint = getById(sprintId);

        BurndownVO vo = new BurndownVO();
        vo.setSprintId(String.valueOf(sprint.getId()));
        vo.setSprintName(sprint.getName());

        // 没有日期范围时返回空结构
        if (sprint.getStartDate() == null || sprint.getEndDate() == null) {
            vo.setDates(List.of());
            vo.setIdealLine(List.of());
            vo.setActualLine(List.of());
            vo.setTodayIndex(-1);
            vo.setTotalIssues(0);
            vo.setVelocity(0.0);
            vo.setForecastDate(null);
            return vo;
        }

        // 查询 Sprint 中所有未删除的工单
        List<Issue> issues = issueMapper.selectList(new LambdaQueryWrapper<Issue>()
                .eq(Issue::getSprintId, sprintId)
                .isNull(Issue::getDeletedAt));

        int totalIssues = issues.size();
        vo.setTotalIssues(totalIssues);

        LocalDate sprintStart = sprint.getStartDate();
        LocalDate sprintEnd = sprint.getEndDate();
        LocalDate today = LocalDate.now();

        long totalDays = sprintStart.until(sprintEnd).getDays();
        if (totalDays <= 0) totalDays = 1;

        // 按解决日期分组统计每天关闭的工单数
        Map<LocalDate, Long> resolvedByDay = issues.stream()
                .filter(i -> i.getResolvedAt() != null)
                .collect(Collectors.groupingBy(
                        i -> i.getResolvedAt().toLocalDate(),
                        Collectors.counting()
                ));

        List<String> dates = new ArrayList<>();
        List<Double> idealLine = new ArrayList<>();
        List<Integer> actualLine = new ArrayList<>();

        long remaining = totalIssues;
        double idealRemaining = totalIssues;
        double idealDecrement = (double) totalIssues / totalDays;
        int todayIndex = -1;

        // 实际线只计算到 today（未来的天不绘制实际值）
        LocalDate endForActual = today.isBefore(sprintEnd) ? today : sprintEnd;

        LocalDate current = sprintStart;
        int dayIndex = 0;
        while (!current.isAfter(sprintEnd)) {
            dates.add(current.toString());
            idealLine.add(Math.max(0, Math.round(idealRemaining * 10.0) / 10.0));
            idealRemaining -= idealDecrement;

            if (!current.isAfter(endForActual)) {
                remaining -= resolvedByDay.getOrDefault(current, 0L);
                actualLine.add((int) Math.max(0, remaining));
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
        vo.setTodayIndex(todayIndex);

        // 计算日均完成速率（velocity）
        int daysElapsed = todayIndex >= 0 ? todayIndex + 1 : (int) totalDays;
        int issuesDone = totalIssues - (int) remaining;
        double velocity = daysElapsed > 0 ? (double) issuesDone / daysElapsed : 0.0;
        vo.setVelocity(Math.round(velocity * 100.0) / 100.0);

        // 预测完成日期
        if (velocity > 0 && remaining > 0) {
            long daysNeeded = (long) Math.ceil(remaining / velocity);
            LocalDate forecast = today.plusDays(daysNeeded);
            vo.setForecastDate(forecast.toString());
        } else if (remaining == 0) {
            vo.setForecastDate(today.toString());
        } else {
            vo.setForecastDate(null);
        }

        return vo;
    }
}
