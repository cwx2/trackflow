package com.trackflow.sprint.service;

import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.result.BurndownRow;
import com.trackflow.issue.mapper.result.DailyLoggedMinutesRow;
import com.trackflow.issue.mapper.result.IssueCreatedAtRow;
import com.trackflow.issue.mapper.result.IssueEstimatedHoursRow;
import com.trackflow.issue.mapper.result.IssueTypeRow;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.sprint.vo.BurndownVO;
import com.trackflow.sprint.vo.SprintAssigneeDistributionVO;
import com.trackflow.sprint.vo.SprintVelocityVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sprint 统计与图表服务 - 处理燃尽图、速度统计、负责人分布等数据分析功能。
 * <p>
 * 从 SprintService 拆分而来（REQ-235），将 Sprint 运维操作（CRUD/激活/完成）
 * 与 Sprint 数据分析（统计报表）分离为独立职责。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SprintStatsService {

    private final SprintMapper sprintMapper;
    private final IssueMapper issueMapper;
    private final IssueActivityMapper activityMapper;

    // ==================== 公共 API ====================

    /**
     * 获取 Sprint 中按负责人分组的工单分布统计。
     * 技术负责人用于评估 Sprint 工作分配均衡性。
     */
    @Transactional(readOnly = true)
    public SprintAssigneeDistributionVO getAssigneeDistribution(Long sprintId) {
        Sprint sprint = findSprintById(sprintId);
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
     * 获取 Sprint 燃尽图数据（默认工单数模式）。
     */
    @Transactional(readOnly = true)
    public BurndownVO getBurndownData(Long sprintId) {
        return getBurndownData(sprintId, "issue_count", null);
    }

    /**
     * 获取 Sprint 燃尽图数据。
     *
     * @param sprintId Sprint ID
     * @param mode     计算模式: "issue_count"（工单数）或 "estimation"（预估工时）
     */
    @Transactional(readOnly = true)
    public BurndownVO getBurndownData(Long sprintId, String mode) {
        return getBurndownData(sprintId, mode, null);
    }

    /**
     * 获取 Sprint 燃尽图数据（支持自定义估算字段）。
     *
     * @param sprintId          Sprint ID
     * @param mode              计算模式: "issue_count" / "estimation" / "work_items"
     * @param estimationFieldId estimation 模式下使用的自定义字段 ID（null 时 fallback 到 issue.estimated_hours）
     */
    @Transactional(readOnly = true)
    public BurndownVO getBurndownData(Long sprintId, String mode, Long estimationFieldId) {
        Sprint sprint = findSprintById(sprintId);

        BurndownVO vo = new BurndownVO();
        vo.setSprintId(String.valueOf(sprint.getId()));
        vo.setSprintName(sprint.getName());
        vo.setMode(mode);

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
            calculateEstimationBurndown(vo, sprint, sprintStart, sprintEnd, today, totalDays, estimationFieldId);
        } else if ("work_items".equals(mode)) {
            calculateWorkItemsBurndown(vo, sprint, sprintStart, sprintEnd, today, totalDays);
        } else {
            BurndownRawData rawData = loadBurndownRawData(sprintId);
            ScopeTimeline scopeTimeline = buildScopeChangeTimeline(rawData, sprintStart);
            calculateDailyMetrics(vo, scopeTimeline, rawData, sprintStart, sprintEnd, today, totalDays);
            calculateVelocityAndForecast(vo, scopeTimeline.startScope, today, totalDays);
            vo.setTotalIssues(rawData.currentIssues.size());
            vo.setStartScopeIssues((int) scopeTimeline.startScope);
        }

        return vo;
    }

    /**
     * 获取项目最近已完成 Sprint 的速率统计数据。
     * 用于 Sprint 规划页展示历史速率，帮助团队合理规划工作量。
     *
     * @param projectId 项目 ID
     * @param limit     最多取几个已完成 Sprint（通常 3~5）
     * @return Sprint 速率统计 VO
     */
    @Transactional(readOnly = true)
    public SprintVelocityVO getSprintVelocity(Long projectId, int limit) {
        List<SprintVelocityVO.SprintVelocityItem> items = sprintMapper.selectSprintVelocity(projectId, limit);
        List<SprintVelocityVO.SprintVelocityItem> ordered = new ArrayList<>(items);
        Collections.reverse(ordered);

        SprintVelocityVO vo = new SprintVelocityVO();
        vo.setSprintCount(ordered.size());
        vo.setSprints(ordered);

        if (ordered.isEmpty()) {
            vo.setAverageVelocity(0.0);
            vo.setLastVelocity(0.0);
        } else {
            double totalCompleted = ordered.stream()
                    .mapToDouble(SprintVelocityVO.SprintVelocityItem::getCompletedHours)
                    .sum();
            double avg = totalCompleted / ordered.size();
            vo.setAverageVelocity(Math.round(avg * 10.0) / 10.0);

            double last = ordered.get(ordered.size() - 1).getCompletedHours();
            vo.setLastVelocity(Math.round(last * 10.0) / 10.0);
        }

        return vo;
    }

    // ==================== 私有辅助方法 ====================

    private Sprint findSprintById(Long id) {
        Sprint sprint = sprintMapper.selectById(id);
        if (sprint == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Sprint not found: " + id);
        }
        return sprint;
    }

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

    // ==================== 燃尽图数据结构 ====================

    private record BurndownIssueData(Long id, LocalDateTime createdAt, LocalDateTime resolvedAt, BigDecimal estimatedHours) {}

    private record BurndownRawData(
            List<BurndownIssueData> currentIssues,
            Set<Long> currentIssueIds,
            Map<Long, LocalDateTime> movedInMap,
            List<IssueActivity> movedOutActivities,
            Map<Long, LocalDateTime> movedOutCreatedAtMap,
            Map<Long, BigDecimal> issueEstimatedHoursMap
    ) {}

    private record ScopeTimeline(
            Map<LocalDate, Long> addedByDay,
            Map<LocalDate, Long> removedByDay,
            Map<LocalDate, Long> resolvedByDay,
            long startScope
    ) {}

    private record EstimationScopeTimeline(
            Map<LocalDate, Double> hoursAddedByDay,
            Map<LocalDate, Double> hoursRemovedByDay,
            Map<LocalDate, Double> resolvedHoursByDay,
            double startScopeHours
    ) {}

    // ==================== 燃尽图数据加载 ====================

    private BurndownRawData loadBurndownRawData(Long sprintId) {
        List<BurndownRow> projections = issueMapper.selectBurndownProjection(sprintId);
        List<Long> currentIssueIdList = projections.stream().map(BurndownRow::getId).toList();

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

        List<Long> allActivityIssueIds = movedOutActivities.stream()
                .map(IssueActivity::getIssueId)
                .filter(id -> !currentIssueIds.contains(id))
                .distinct()
                .toList();
        Set<Long> epicLaneIds = buildEpicLaneIdSet(allActivityIssueIds);
        if (!epicLaneIds.isEmpty()) {
            movedInActivities = movedInActivities.stream()
                    .filter(a -> !epicLaneIds.contains(a.getIssueId()))
                    .toList();
            movedOutActivities = movedOutActivities.stream()
                    .filter(a -> !epicLaneIds.contains(a.getIssueId()))
                    .toList();
        }

        Map<Long, LocalDateTime> movedInMap = movedInActivities.stream()
                .collect(Collectors.toMap(
                        IssueActivity::getIssueId,
                        IssueActivity::getCreatedAt,
                        (a, b) -> b.isAfter(a) ? b : a
                ));

        Map<Long, LocalDateTime> movedOutCreatedAtMap = loadMovedOutCreatedAtMap(
                movedOutActivities, currentIssueIds, movedInMap);

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

    private Set<Long> buildEpicLaneIdSet(List<Long> issueIds) {
        if (issueIds.isEmpty()) {
            return Set.of();
        }
        List<IssueTypeRow> typeRows = issueMapper.selectTypeByIds(issueIds);
        return typeRows.stream()
                .filter(row -> "Epic".equals(row.getIssueType()) && row.getChildCount() > 0)
                .map(IssueTypeRow::getId)
                .collect(Collectors.toSet());
    }

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

    // ==================== 工单数模式计算 ====================

    private ScopeTimeline buildScopeChangeTimeline(BurndownRawData rawData, LocalDate sprintStart) {
        Map<LocalDate, Long> addedByDay = new LinkedHashMap<>();
        Map<LocalDate, Long> removedByDay = new LinkedHashMap<>();

        for (BurndownIssueData issue : rawData.currentIssues) {
            LocalDateTime movedInAt = rawData.movedInMap.get(issue.id());
            LocalDate enteredDate = (movedInAt != null)
                    ? movedInAt.toLocalDate()
                    : issue.createdAt().toLocalDate();
            addedByDay.merge(enteredDate, 1L, Long::sum);
        }

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

        Map<LocalDate, Long> resolvedByDay = rawData.currentIssues.stream()
                .filter(issue -> issue.resolvedAt() != null)
                .collect(Collectors.groupingBy(
                        issue -> issue.resolvedAt().toLocalDate(),
                        Collectors.counting()
                ));

        long startScope = calculateStartScope(addedByDay, removedByDay, sprintStart);
        return new ScopeTimeline(addedByDay, removedByDay, resolvedByDay, startScope);
    }

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

    private long calculateResolvedBeforeStart(Map<LocalDate, Long> resolvedByDay, LocalDate sprintStart) {
        return resolvedByDay.entrySet().stream()
                .filter(entry -> entry.getKey().isBefore(sprintStart))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    private void calculateVelocityAndForecast(BurndownVO vo, long startScope,
                                              LocalDate today, long totalDays) {
        int todayIndex = vo.getTodayIndex();
        int daysElapsed = todayIndex >= 0 ? todayIndex + 1 : (int) totalDays;

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

    // ==================== 估时模式计算 ====================

    private void calculateEstimationBurndown(BurndownVO vo, Sprint sprint,
                                             LocalDate sprintStart, LocalDate sprintEnd,
                                             LocalDate today, long totalDays,
                                             Long estimationFieldId) {
        Long sprintId = sprint.getId();
        BurndownRawData rawData = loadBurndownRawData(sprintId);

        BurndownRawData effectiveData = rawData;
        if (estimationFieldId != null) {
            effectiveData = overrideEstimatedHoursWithCustomField(rawData, estimationFieldId);
        }

        EstimationScopeTimeline timeline = buildEstimationScopeTimeline(effectiveData, sprintStart);

        double startHours;
        if (sprint.getStartScopeHours() != null && sprint.getStartScopeHours().compareTo(BigDecimal.ZERO) > 0) {
            startHours = sprint.getStartScopeHours().doubleValue();
        } else {
            startHours = timeline.startScopeHours();
        }

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
        vo.setTotalIssues(effectiveData.currentIssues().size());
        vo.setStartScopeIssues(sprint.getStartScopeIssues() != null
                ? sprint.getStartScopeIssues()
                : effectiveData.currentIssues().size());
        vo.setStartScopeHours(startHours);

        int daysElapsed = todayIndex >= 0 ? todayIndex + 1 : (int) totalDays;
        double hoursVelocity = daysElapsed > 0 ? resolvedHours / daysElapsed : 0.0;
        vo.setVelocity(Math.round(hoursVelocity * 100.0) / 100.0);

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

    private double calculateResolvedHoursBeforeStart(Map<LocalDate, Double> resolvedHoursByDay,
                                                      LocalDate sprintStart) {
        return resolvedHoursByDay.entrySet().stream()
                .filter(entry -> entry.getKey().isBefore(sprintStart))
                .mapToDouble(Map.Entry::getValue)
                .sum();
    }

    private BurndownRawData overrideEstimatedHoursWithCustomField(BurndownRawData rawData,
                                                                   Long estimationFieldId) {
        List<Long> allIssueIds = new ArrayList<>(rawData.currentIssueIds());
        rawData.movedOutActivities().forEach(a -> {
            if (!rawData.currentIssueIds().contains(a.getIssueId())) {
                allIssueIds.add(a.getIssueId());
            }
        });

        Map<Long, BigDecimal> customFieldMap = new HashMap<>();
        if (!allIssueIds.isEmpty()) {
            List<IssueEstimatedHoursRow> rows = issueMapper.selectCustomFieldValuesByIds(allIssueIds, estimationFieldId);
            for (IssueEstimatedHoursRow row : rows) {
                if (row.getEstimatedHours() != null) {
                    customFieldMap.put(row.getId(), row.getEstimatedHours());
                }
            }
        }

        List<BurndownIssueData> newCurrentIssues = rawData.currentIssues().stream()
                .map(issue -> new BurndownIssueData(
                        issue.id(),
                        issue.createdAt(),
                        issue.resolvedAt(),
                        customFieldMap.getOrDefault(issue.id(), BigDecimal.ZERO)
                ))
                .toList();

        Map<Long, BigDecimal> newEstimatedHoursMap = new HashMap<>(customFieldMap);

        return new BurndownRawData(
                newCurrentIssues,
                rawData.currentIssueIds(),
                rawData.movedInMap(),
                rawData.movedOutActivities(),
                rawData.movedOutCreatedAtMap(),
                newEstimatedHoursMap
        );
    }

    private EstimationScopeTimeline buildEstimationScopeTimeline(BurndownRawData rawData,
                                                                  LocalDate sprintStart) {
        Map<LocalDate, Double> hoursAddedByDay = new LinkedHashMap<>();
        Map<LocalDate, Double> hoursRemovedByDay = new LinkedHashMap<>();

        for (BurndownIssueData issue : rawData.currentIssues) {
            LocalDateTime movedInAt = rawData.movedInMap.get(issue.id());
            LocalDate enteredDate = (movedInAt != null)
                    ? movedInAt.toLocalDate()
                    : issue.createdAt().toLocalDate();
            double hours = issue.estimatedHours() != null ? issue.estimatedHours().doubleValue() : 0.0;
            hoursAddedByDay.merge(enteredDate, hours, Double::sum);
        }

        for (IssueActivity movedOut : rawData.movedOutActivities) {
            Long issueId = movedOut.getIssueId();
            BigDecimal estimatedBd = rawData.issueEstimatedHoursMap.get(issueId);
            double hours = estimatedBd != null ? estimatedBd.doubleValue() : 0.0;

            hoursRemovedByDay.merge(movedOut.getCreatedAt().toLocalDate(), hours, Double::sum);

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

        Map<LocalDate, Double> resolvedHoursByDay = rawData.currentIssues.stream()
                .filter(issue -> issue.resolvedAt() != null && issue.estimatedHours() != null)
                .collect(Collectors.groupingBy(
                        issue -> issue.resolvedAt().toLocalDate(),
                        Collectors.summingDouble(issue -> issue.estimatedHours().doubleValue())
                ));

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

    // ==================== work_items 模式计算 ====================

    private void calculateWorkItemsBurndown(BurndownVO vo, Sprint sprint,
                                            LocalDate sprintStart, LocalDate sprintEnd,
                                            LocalDate today, long totalDays) {
        Long sprintId = sprint.getId();

        BurndownRawData rawData = loadBurndownRawData(sprintId);
        double totalEstimatedMinutes = rawData.currentIssues().stream()
                .filter(i -> i.estimatedHours() != null)
                .mapToDouble(i -> i.estimatedHours().doubleValue() * 60.0)
                .sum();

        List<DailyLoggedMinutesRow> loggedRows =
                sprintMapper.selectSprintDailyLoggedMinutes(sprintId);

        Map<LocalDate, Integer> loggedByDay = new LinkedHashMap<>();
        int totalLoggedMinutes = 0;
        for (DailyLoggedMinutesRow row : loggedRows) {
            if (row.getWorkDate() != null) {
                int minutes = row.getTotalMinutes() != null ? row.getTotalMinutes() : 0;
                loggedByDay.put(row.getWorkDate(), minutes);
                totalLoggedMinutes += minutes;
            }
        }

        boolean hasEstimation = totalEstimatedMinutes > 0;
        double startMinutes = hasEstimation ? totalEstimatedMinutes : (double) totalLoggedMinutes;
        if (startMinutes == 0) {
            buildEmptyBurndown(vo);
            return;
        }

        double idealDecrement = startMinutes / totalDays;
        List<String> dates = new ArrayList<>();
        List<Double> idealLine = new ArrayList<>();
        List<Integer> actualLine = new ArrayList<>();
        List<Integer> scopeLine = new ArrayList<>();

        double idealRemaining = startMinutes;
        double cumulativeLogged = 0.0;
        int todayIndex = -1;
        LocalDate endForActual = today.isBefore(sprintEnd) ? today : sprintEnd;
        LocalDate current = sprintStart;
        int dayIndex = 0;

        while (!current.isAfter(sprintEnd)) {
            dates.add(current.toString());
            idealLine.add(Math.max(0.0, Math.round(idealRemaining * 10.0) / 10.0));
            idealRemaining -= idealDecrement;

            if (!current.isAfter(endForActual)) {
                cumulativeLogged += loggedByDay.getOrDefault(current, 0);
                double remaining = Math.max(0.0, startMinutes - cumulativeLogged);
                actualLine.add((int) Math.round(remaining));
                scopeLine.add((int) Math.round(startMinutes));
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
        vo.setStartScopeHours(startMinutes / 60.0);

        int daysElapsed = todayIndex >= 0 ? todayIndex + 1 : (int) totalDays;
        double minuteVelocity = daysElapsed > 0 ? cumulativeLogged / daysElapsed : 0.0;
        vo.setVelocity(Math.round(minuteVelocity / 60.0 * 100.0) / 100.0);

        double remainingMinutes = Math.max(0.0, startMinutes - cumulativeLogged);
        if (minuteVelocity > 0 && remainingMinutes > 0) {
            long daysNeeded = (long) Math.ceil(remainingMinutes / minuteVelocity);
            vo.setForecastDate(today.plusDays(daysNeeded).toString());
        } else if (remainingMinutes <= 0) {
            vo.setForecastDate(today.toString());
        } else {
            vo.setForecastDate(null);
        }
    }
}
