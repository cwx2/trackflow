package com.trackflow.timeentry.controller;

import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.service.IssueService;
import com.trackflow.timeentry.dto.CreateTimeEntryDTO;
import com.trackflow.timeentry.dto.UpdateTimeEntryDTO;
import com.trackflow.timeentry.entity.TimeEntry;
import com.trackflow.timeentry.service.TimeEntryService;
import com.trackflow.timeentry.vo.TimeEntryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/time-entries")
@RequiredArgsConstructor
public class TimeEntryController {

    private final TimeEntryService timeEntryService;
    private final IssueService issueService;

    /**
     * 创建工时记录
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public R<TimeEntryVO> create(@Valid @RequestBody CreateTimeEntryDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        TimeEntry entry = timeEntryService.create(userId, dto);
        // Return simplified VO
        TimeEntryVO vo = new TimeEntryVO();
        vo.setId(String.valueOf(entry.getId()));
        vo.setIssueId(String.valueOf(entry.getIssueId()));
        vo.setUserId(String.valueOf(entry.getUserId()));
        vo.setWorkDate(entry.getWorkDate().toString());
        vo.setDuration(entry.getDuration());
        vo.setStartTime(entry.getStartTime());
        vo.setWorkType(entry.getWorkType());
        vo.setDescription(entry.getDescription());
        return R.ok(vo);
    }

    /**
     * 更新工时记录
     */
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<TimeEntryVO> update(@PathVariable Long id, @Valid @RequestBody UpdateTimeEntryDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        TimeEntry entry = timeEntryService.update(id, userId, dto);
        TimeEntryVO vo = new TimeEntryVO();
        vo.setId(String.valueOf(entry.getId()));
        vo.setIssueId(String.valueOf(entry.getIssueId()));
        vo.setUserId(String.valueOf(entry.getUserId()));
        vo.setWorkDate(entry.getWorkDate().toString());
        vo.setDuration(entry.getDuration());
        vo.setStartTime(entry.getStartTime());
        vo.setWorkType(entry.getWorkType());
        vo.setDescription(entry.getDescription());
        return R.ok(vo);
    }

    /**
     * 删除工时记录
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        timeEntryService.delete(id, userId);
        return R.ok();
    }

    /**
     * 查询用户在日期范围内的工时记录
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<List<TimeEntryVO>> list(
            @RequestParam(required = false) Long userId,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        // 普通用户只能查自己的工时
        Long targetUserId = (userId != null) ? userId : currentUserId;
        if (!targetUserId.equals(currentUserId)) {
            // TODO: 后续添加管理员权限允许查看他人
            targetUserId = currentUserId;
        }
        return R.ok(timeEntryService.listByUserAndDateRange(targetUserId, startDate, endDate));
    }

    /**
     * 查询某 Issue 的工时记录（校验项目成员权限）
     */
    @GetMapping("/issue/{issueId}")
    @PreAuthorize("isAuthenticated()")
    public R<List<TimeEntryVO>> listByIssue(@PathVariable Long issueId) {
        // 校验当前用户是否有权访问该工单所属的项目
        issueService.getByIdWithAccessCheck(issueId);
        return R.ok(timeEntryService.listByIssue(issueId));
    }

    /**
     * 汇总用户在日期范围内的总工时
     */
    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public R<Integer> summary(
            @RequestParam(required = false) Long userId,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long targetUserId = (userId != null) ? userId : currentUserId;
        if (!targetUserId.equals(currentUserId)) {
            targetUserId = currentUserId;
        }
        return R.ok(timeEntryService.sumByUserAndDateRange(targetUserId, startDate, endDate));
    }
}
