package com.trackflow.timeentry.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.timeentry.dto.CreateTimeEntryDTO;
import com.trackflow.timeentry.dto.UpdateTimeEntryDTO;
import com.trackflow.timeentry.entity.TimeEntry;
import com.trackflow.timeentry.mapper.TimeEntryMapper;
import com.trackflow.timeentry.vo.TimeEntryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TimeEntryService {

    private final TimeEntryMapper timeEntryMapper;

    /**
     * 创建工时记录
     */
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
        return entry;
    }

    /**
     * 更新工时记录
     */
    public TimeEntry update(Long id, Long userId, UpdateTimeEntryDTO dto) {
        TimeEntry entry = timeEntryMapper.selectById(id);
        if (entry == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工时记录不存在");
        }
        if (!entry.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权修改他人工时记录");
        }

        if (dto.getIssueId() != null) entry.setIssueId(dto.getIssueId());
        if (dto.getWorkDate() != null) entry.setWorkDate(LocalDate.parse(dto.getWorkDate()));
        if (dto.getDuration() != null) entry.setDuration(dto.getDuration());
        if (dto.getStartTime() != null) entry.setStartTime(dto.getStartTime());
        if (dto.getWorkType() != null) entry.setWorkType(dto.getWorkType());
        if (dto.getDescription() != null) entry.setDescription(dto.getDescription());
        entry.setUpdatedAt(LocalDateTime.now());

        timeEntryMapper.updateById(entry);
        return entry;
    }

    /**
     * 删除工时记录
     */
    public void delete(Long id, Long userId) {
        TimeEntry entry = timeEntryMapper.selectById(id);
        if (entry == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工时记录不存在");
        }
        if (!entry.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权删除他人工时记录");
        }
        timeEntryMapper.deleteById(id);
    }

    /**
     * 查询用户在日期范围内的工时记录（带 issueKey）
     */
    public List<TimeEntryVO> listByUserAndDateRange(Long userId, String startDate, String endDate) {
        List<Map<String, Object>> rows = timeEntryMapper.selectEntriesWithIssueKey(userId, startDate, endDate);

        return rows.stream().map(row -> {
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
        }).toList();
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
}
