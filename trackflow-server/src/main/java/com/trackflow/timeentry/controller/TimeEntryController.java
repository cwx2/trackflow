package com.trackflow.timeentry.controller;

import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.service.IssueService;
import com.trackflow.timeentry.dto.CreateTimeEntryDTO;
import com.trackflow.timeentry.dto.UpdateTimeEntryDTO;
import com.trackflow.timeentry.entity.TimeEntry;
import com.trackflow.timeentry.service.TimeEntryService;
import com.trackflow.timeentry.vo.ProjectTimeSummaryVO;
import com.trackflow.timeentry.vo.TimeEntryVO;
import com.trackflow.timeentry.vo.TimeEntryUserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/time-entries")
@RequiredArgsConstructor
public class TimeEntryController {

    private static final String PERM_VIEW_OTHERS = "time:view_others";

    private final TimeEntryService timeEntryService;
    private final IssueService issueService;
    private final PermissionService permissionService;
    private final com.trackflow.workitemattr.service.WorkItemAttributeService workItemAttributeService;

    /**
     * 创建工时记录
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public R<TimeEntryVO> create(@Valid @RequestBody CreateTimeEntryDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        TimeEntry entry = timeEntryService.create(userId, dto);
        TimeEntryVO vo = buildEntryVO(entry);
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
        TimeEntryVO vo = buildEntryVO(entry);
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
     * 管理员（拥有 time:view_others 权限）可查看他人工时
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<List<TimeEntryVO>> list(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long targetUserId = (userId != null) ? userId : currentUserId;

        if (!targetUserId.equals(currentUserId)) {
            // 校验当前用户是否有权查看他人工时
            if (!canViewOthersTime(currentUserId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权查看他人工时记录");
            }
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
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long targetUserId = (userId != null) ? userId : currentUserId;

        if (!targetUserId.equals(currentUserId)) {
            if (!canViewOthersTime(currentUserId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权查看他人工时记录");
            }
        }
        return R.ok(timeEntryService.sumByUserAndDateRange(targetUserId, startDate, endDate));
    }

    /**
     * 按项目汇总工时（项目视图概览）
     * 返回当前用户可见项目的工时聚合列表
     */
    @GetMapping("/by-project")
    @PreAuthorize("isAuthenticated()")
    public R<List<ProjectTimeSummaryVO>> listByProject(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return R.ok(timeEntryService.listByProjectForUser(currentUserId, startDate, endDate));
    }

    /**
     * 查询指定项目在日期范围内的工时明细（项目视图详情）
     */
    @GetMapping("/by-project/{projectId}")
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<List<TimeEntryVO>> listByProjectDetail(
            @PathVariable Long projectId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        return R.ok(timeEntryService.listByProject(projectId, startDate, endDate));
    }

    /**
     * 获取可选择的用户列表（用于人员视图的用户选择器）
     * 仅拥有 time:view_others 权限的用户可获取完整列表
     * 普通用户仅返回自身信息
     */
    @GetMapping("/users")
    @PreAuthorize("isAuthenticated()")
    public R<List<TimeEntryUserVO>> listSelectableUsers(
            @RequestParam(value = "keyword", required = false) String keyword) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean canViewOthers = canViewOthersTime(currentUserId);
        return R.ok(timeEntryService.listSelectableUsers(currentUserId, canViewOthers, keyword));
    }

    /**
     * 检查当前用户是否有权查看他人工时
     * 返回布尔值供前端判断是否显示用户选择器
     */
    @GetMapping("/can-view-others")
    @PreAuthorize("isAuthenticated()")
    public R<Boolean> checkCanViewOthers() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return R.ok(canViewOthersTime(currentUserId));
    }

    // ========== 内部方法 ==========

    /**
     * 判断用户是否有权查看他人工时
     * 系统管理员（system:admin）或在任何项目中拥有 time:view_others 的用户
     */
    private boolean canViewOthersTime(Long userId) {
        return permissionService.hasPermissionInAnyProject(userId, PERM_VIEW_OTHERS);
    }

    /**
     * 构建 TimeEntryVO（含属性值）
     */
    private TimeEntryVO buildEntryVO(TimeEntry entry) {
        TimeEntryVO vo = new TimeEntryVO();
        vo.setId(String.valueOf(entry.getId()));
        vo.setIssueId(String.valueOf(entry.getIssueId()));
        vo.setUserId(String.valueOf(entry.getUserId()));
        vo.setWorkDate(entry.getWorkDate().toString());
        vo.setDuration(entry.getDuration());
        vo.setStartTime(entry.getStartTime());
        vo.setWorkType(entry.getWorkType());
        vo.setDescription(entry.getDescription());

        // 加载属性值
        Map<String, Map<String, String>> attrValues = workItemAttributeService.getTimeEntryAttributeValues(entry.getId());
        if (!attrValues.isEmpty()) {
            vo.setAttributeValues(attrValues.entrySet().stream().map(e -> {
                Map<String, String> item = new java.util.HashMap<>(e.getValue());
                item.put("attributeId", e.getKey());
                return item;
            }).toList());
        }

        return vo;
    }
}
