package com.trackflow.timeentry.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.timeentry.dto.CreateTimeEntryDTO;
import com.trackflow.timeentry.dto.UpdateTimeEntryDTO;
import com.trackflow.timeentry.entity.TimeEntry;
import com.trackflow.timeentry.mapper.TimeEntryMapper;
import com.trackflow.timeentry.vo.ProjectTimeSummaryVO;
import com.trackflow.timeentry.vo.TimeEntryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TimeEntryService {

    private final TimeEntryMapper timeEntryMapper;
    private final IssueActivityMapper activityMapper;
    private final IssueMapper issueMapper;

    /**
     * 创建工时记录
     */
    @Transactional
    public TimeEntry create(Long userId, CreateTimeEntryDTO dto) {
        TimeEntry entry = new TimeEntry();
        entry.setIssueId(dto.getIssueId());
        entry.setUserId(userId);
        entry.setWorkDate(LocalDate.parse(dto.getWorkDate()));
        entry.setDuration(dto.getDuration());
        entry.setStartTime(dto.getStartTime());
        entry.setWorkType(dto.getWorkType());
        entry.setDescription(dto.getDescription());
        entry.setCreatedAt(LocalDateTime.now());
        entry.setUpdatedAt(LocalDateTime.now());

        timeEntryMapper.insert(entry);

        // 记录活动：花费了 X 时间
        String durationStr = formatDuration(dto.getDuration());
        String detail = dto.getWorkType() != null ? durationStr + " | " + dto.getWorkType() : durationStr;
        if (dto.getDescription() != null && !dto.getDescription().isBlank()) {
            detail += " | " + dto.getDescription();
        }
        recordActivity(dto.getIssueId(), userId, "time_logged", "spent_time", null, detail);

        // 同步更新 issue.spent_hours
        refreshIssueSpentHours(dto.getIssueId());

        return entry;
    }

    /**
     * 更新工时记录
     */
    @Transactional
    public TimeEntry update(Long id, Long userId, UpdateTimeEntryDTO dto) {
        TimeEntry entry = timeEntryMapper.selectById(id);
        if (entry == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工时记录不存在");
        }
        if (!entry.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权修改他人工时记录");
        }

        Long oldIssueId = entry.getIssueId();

        if (dto.getIssueId() != null) entry.setIssueId(dto.getIssueId());
        if (dto.getWorkDate() != null) entry.setWorkDate(LocalDate.parse(dto.getWorkDate()));
        if (dto.getDuration() != null) entry.setDuration(dto.getDuration());
        if (dto.getStartTime() != null) entry.setStartTime(dto.getStartTime());
        if (dto.getWorkType() != null) entry.setWorkType(dto.getWorkType());
        if (dto.getDescription() != null) entry.setDescription(dto.getDescription());
        entry.setUpdatedAt(LocalDateTime.now());

        timeEntryMapper.updateById(entry);

        // 同步更新 issue.spent_hours
        refreshIssueSpentHours(entry.getIssueId());
        // 如果工时记录转移到了其他 Issue，旧 Issue 也需要刷新
        if (dto.getIssueId() != null && !oldIssueId.equals(dto.getIssueId())) {
            refreshIssueSpentHours(oldIssueId);
        }

        return entry;
    }

    /**
     * 删除工时记录
     */
    @Transactional
    public void delete(Long id, Long userId) {
        TimeEntry entry = timeEntryMapper.selectById(id);
        if (entry == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工时记录不存在");
        }
        if (!entry.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权删除他人工时记录");
        }

        Long issueId = entry.getIssueId();

        // 记录活动：删除了工时
        String durationStr = formatDuration(entry.getDuration());
        recordActivity(issueId, userId, "time_removed", "spent_time", durationStr, null);

        timeEntryMapper.deleteById(id);

        // 同步更新 issue.spent_hours
        refreshIssueSpentHours(issueId);
    }

    /**
     * 查询用户在日期范围内的工时记录（带 issueKey）
     */
    public List<TimeEntryVO> listByUserAndDateRange(Long userId, String startDate, String endDate) {
        List<Map<String, Object>> rows = timeEntryMapper.selectEntriesWithIssueKey(userId, startDate, endDate);
        return rows.stream().map(this::mapRowToVO).toList();
    }

    /**
     * 查询某 Issue 的所有工时记录
     */
    public List<TimeEntryVO> listByIssue(Long issueId) {
        List<TimeEntry> entries = timeEntryMapper.selectList(
                new QueryWrapper<TimeEntry>().eq("issue_id", issueId).orderByDesc("work_date", "created_at")
        );
        return entries.stream().map(e -> {
            TimeEntryVO vo = new TimeEntryVO();
            vo.setId(String.valueOf(e.getId()));
            vo.setIssueId(String.valueOf(e.getIssueId()));
            vo.setUserId(String.valueOf(e.getUserId()));
            vo.setWorkDate(e.getWorkDate() != null ? e.getWorkDate().toString() : null);
            vo.setDuration(e.getDuration());
            vo.setStartTime(e.getStartTime());
            vo.setWorkType(e.getWorkType());
            vo.setDescription(e.getDescription());
            if (e.getCreatedAt() != null) vo.setCreatedAt(e.getCreatedAt().toString());
            if (e.getUpdatedAt() != null) vo.setUpdatedAt(e.getUpdatedAt().toString());
            return vo;
        }).toList();
    }

    /**
     * 汇总用户在日期范围内的总工时（分钟）
     */
    public int sumByUserAndDateRange(Long userId, String startDate, String endDate) {
        QueryWrapper<TimeEntry> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .ge("work_date", startDate)
                .le("work_date", endDate);
        List<TimeEntry> entries = timeEntryMapper.selectList(wrapper.select("duration"));
        return entries.stream().mapToInt(TimeEntry::getDuration).sum();
    }

    /**
     * 按项目汇总工时（项目视图概览）：返回用户可见项目的工时聚合
     */
    public List<ProjectTimeSummaryVO> listByProjectForUser(Long userId, String startDate, String endDate) {
        List<Map<String, Object>> rows = timeEntryMapper.selectEntriesByProjectForUser(userId, startDate, endDate);

        // 按 project_id 分组
        Map<String, List<Map<String, Object>>> grouped = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> String.valueOf(row.get("project_id")),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<ProjectTimeSummaryVO> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            List<Map<String, Object>> projectRows = entry.getValue();
            Map<String, Object> first = projectRows.get(0);

            ProjectTimeSummaryVO vo = new ProjectTimeSummaryVO();
            vo.setProjectId(String.valueOf(first.get("project_id")));
            vo.setProjectName((String) first.get("project_name"));
            vo.setProjectKey((String) first.get("project_key"));

            List<TimeEntryVO> entries = projectRows.stream().map(this::mapRowToVO).toList();
            vo.setEntries(entries);
            vo.setTotalDuration(entries.stream().mapToInt(TimeEntryVO::getDuration).sum());

            result.add(vo);
        }
        return result;
    }

    /**
     * 查询指定项目在日期范围内的工时明细（项目视图详情）
     */
    public List<TimeEntryVO> listByProject(Long projectId, String startDate, String endDate) {
        List<Map<String, Object>> rows = timeEntryMapper.selectEntriesByProject(projectId, startDate, endDate);
        return rows.stream().map(row -> {
            TimeEntryVO vo = mapRowToVO(row);
            vo.setUserName((String) row.get("user_name"));
            return vo;
        }).toList();
    }

    // ========== 内部方法 ==========

    /**
     * 重新计算并更新 Issue 的 spent_hours 字段
     * spent_hours = SUM(time_entry.duration) / 60，保留 2 位小数
     */
    private void refreshIssueSpentHours(Long issueId) {
        Integer totalMinutes = timeEntryMapper.sumDurationByIssueId(issueId);
        BigDecimal spentHours = BigDecimal.valueOf(totalMinutes != null ? totalMinutes : 0)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        issueMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Issue>()
                        .eq(Issue::getId, issueId)
                        .set(Issue::getSpentHours, spentHours));
    }

    private TimeEntryVO mapRowToVO(Map<String, Object> row) {
        TimeEntryVO vo = new TimeEntryVO();
        vo.setId(String.valueOf(row.get("id")));
        vo.setIssueId(String.valueOf(row.get("issue_id")));
        vo.setIssueKey((String) row.get("issue_key"));
        vo.setIssueTitle((String) row.get("issue_title"));
        vo.setUserId(String.valueOf(row.get("user_id")));
        if (row.get("work_date") != null) vo.setWorkDate(row.get("work_date").toString());
        if (row.get("duration") != null) vo.setDuration((Integer) row.get("duration"));
        if (row.get("start_time") != null) vo.setStartTime((Integer) row.get("start_time"));
        vo.setWorkType((String) row.get("work_type"));
        vo.setDescription((String) row.get("description"));
        if (row.get("created_at") != null) vo.setCreatedAt(row.get("created_at").toString());
        if (row.get("updated_at") != null) vo.setUpdatedAt(row.get("updated_at").toString());
        return vo;
    }

    private void recordActivity(Long issueId, Long userId, String action, String fieldName, String oldValue, String newValue) {
        IssueActivity activity = new IssueActivity();
        activity.setIssueId(issueId);
        activity.setUserId(userId);
        activity.setAction(action);
        activity.setFieldName(fieldName);
        activity.setOldValue(oldValue);
        activity.setNewValue(newValue);
        activity.setCreatedAt(LocalDateTime.now());
        activityMapper.insert(activity);
    }

    private String formatDuration(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        if (h == 0) return m + "m";
        if (m == 0) return h + "h";
        return h + "h" + m + "m";
    }
}
