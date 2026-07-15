package com.trackflow.sprint.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.sprint.dto.CompleteSprintDTO;
import com.trackflow.sprint.dto.CreateSprintDTO;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.sprint.vo.BurndownVO;
import com.trackflow.sprint.vo.CompletionPreviewVO;
import com.trackflow.sprint.vo.SprintVO;
import com.trackflow.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintMapper sprintMapper;
    private final IssueMapper issueMapper;
    private final ProjectService projectService;

    /**
     * 查询项目的 Sprint 列表（带工单统计）。
     * MyBatis resultMap 直接映射为 SprintVO，无需手动转换。
     */
    public List<SprintVO> listByProjectWithStats(Long projectId) {
        return sprintMapper.selectSprintsWithStats(projectId);
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
        sprint.setStatus("planned");
        sprintMapper.insert(sprint);
        return sprint;
    }

    @Transactional
    public Sprint activate(Long id) {
        Sprint sprint = getById(id);
        // 归档项目不允许操作 Sprint
        projectService.assertProjectActive(sprint.getProjectId());

        // 只有计划中的 Sprint 才能激活
        if (!"planned".equals(sprint.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有计划中的迭代才能激活");
        }

        // 同项目同一时间只允许一个 active Sprint（Scrum 排他约束）
        Long activeCount = sprintMapper.selectCount(
                new LambdaQueryWrapper<Sprint>()
                        .eq(Sprint::getProjectId, sprint.getProjectId())
                        .eq(Sprint::getStatus, "active")
        );
        if (activeCount > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "该项目已有一个活跃的迭代，请先完成当前迭代再激活新的");
        }

        sprint.setStatus("active");
        sprintMapper.updateById(sprint);
        return sprint;
    }

    @Transactional
    public Sprint complete(Long id, CompleteSprintDTO dto) {
        Sprint sprint = getById(id);
        // 归档项目不允许操作 Sprint
        projectService.assertProjectActive(sprint.getProjectId());

        if (!"active".equals(sprint.getStatus())) {
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
                if ("completed".equals(targetSprint.getStatus())) {
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
        sprint.setStatus("completed");
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
                        .in(Sprint::getStatus, "planned", "active")
                        .orderByAsc(Sprint::getCreatedAt)
        );
        List<CompletionPreviewVO.TargetSprintItem> targetSprints = candidateSprints.stream().map(s -> {
            CompletionPreviewVO.TargetSprintItem item = new CompletionPreviewVO.TargetSprintItem();
            item.setId(String.valueOf(s.getId()));
            item.setName(s.getName());
            item.setStatus(s.getStatus());
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

    @Transactional
    public void delete(Long id) {
        Sprint sprint = getById(id);
        // 归档项目不允许删除 Sprint
        projectService.assertProjectActive(sprint.getProjectId());

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
