package com.trackflow.timeentry.controller;

import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.service.IssueService;
import com.trackflow.timeentry.dto.CreateTimeEntryDTO;
import com.trackflow.timeentry.dto.StartTimerDTO;
import com.trackflow.timeentry.dto.StopTimerDTO;
import com.trackflow.timeentry.dto.UpdateTimeEntryDTO;
import com.trackflow.timeentry.entity.TimeEntry;
import com.trackflow.timeentry.service.TimeEntryService;
import com.trackflow.timeentry.vo.GroupTimeSummaryVO;
import com.trackflow.timeentry.vo.ProjectTimeSummaryVO;
import com.trackflow.timeentry.vo.TimeEntryVO;
import com.trackflow.timeentry.vo.TimeEntryUserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 工时记录控制器 - 提供工时 CRUD、计时器管理和权限检查 API
 *
 * @author TrackFlow
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/time-entries")
@RequiredArgsConstructor
public class TimeEntryController {

    private static final String PERM_VIEW_OTHERS = "time:view_others";
    private static final String PERM_EDIT_ALL = "time:edit_all";
    private static final String PERM_LOG_FOR_OTHERS = "time:log_for_others";

    private final TimeEntryService timeEntryService;
    private final IssueService issueService;
    private final PermissionService permissionService;

    // ========== 计时器 API ==========

    /**
     * 启动计时器（创建 ongoing 工时记录）
     */
    @PostMapping("/start")
    @PreAuthorize("isAuthenticated()")
    public R<TimeEntryVO> startTimer(@Valid @RequestBody StartTimerDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        TimeEntry entry = timeEntryService.startTimer(userId, dto);
        TimeEntryVO vo = timeEntryService.buildEntryVO(entry);
        vo.setOngoing(true);
        return R.ok(vo);
    }

    /**
     * 停止计时器
     */
    @PostMapping("/{id}/stop")
    @PreAuthorize("isAuthenticated()")
    public R<TimeEntryVO> stopTimer(@PathVariable("id") Long id, @RequestBody(required = false) StopTimerDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        TimeEntry entry = timeEntryService.stopTimer(userId, id, dto);
        return R.ok(timeEntryService.buildEntryVO(entry));
    }

    /**
     * 获取当前用户的活跃计时器
     */
    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public R<TimeEntryVO> getActiveTimer() {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(timeEntryService.getActiveTimerVO(userId));
    }

    // ========== CRUD API ==========

    /**
     * 创建工时记录
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public R<TimeEntryVO> create(@Valid @RequestBody CreateTimeEntryDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        TimeEntry entry = timeEntryService.create(userId, dto);
        return R.ok(timeEntryService.buildEntryVO(entry));
    }

    /**
     * 更新工时记录
     */
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<TimeEntryVO> update(@PathVariable("id") Long id, @Valid @RequestBody UpdateTimeEntryDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        TimeEntry entry = timeEntryService.update(id, userId, dto);
        return R.ok(timeEntryService.buildEntryVO(entry));
    }

    /**
     * 删除工时记录
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> delete(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        timeEntryService.delete(id, userId);
        return R.ok();
    }

    // ========== 查询 API ==========

    /**
     * 查询用户在日期范围内的工时记录
     * 管理员（拥有 time:view_others 权限）可查看他人工时
     * 支持按项目和工作类型（activityId）筛选
     *
     * @param activityId 工作类型属性值 ID（对应 work_item_attribute_value.id）
     * @param workType   已废弃参数，为向下兼容保留但不再使用（前端应迁移到 activityId）
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<List<TimeEntryVO>> list(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "activityId", required = false) Long activityId,
            @RequestParam(value = "workType", required = false) String workType) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long targetUserId = (userId != null) ? userId : currentUserId;

        if (!targetUserId.equals(currentUserId) && !canViewOthersTime(currentUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权查看他人工时记录");
        }

        Long resolvedActivityId = timeEntryService.resolveActivityId(activityId, workType);
        return R.ok(timeEntryService.listByUserAndDateRange(targetUserId, currentUserId, startDate, endDate, projectId, resolvedActivityId));
    }

    /**
     * 查询某 Issue 的工时记录（校验项目成员权限）
     * ongoing 记录仅对其所有者可见
     */
    @GetMapping("/issue/{issueId}")
    @PreAuthorize("isAuthenticated()")
    public R<List<TimeEntryVO>> listByIssue(@PathVariable("issueId") Long issueId) {
        issueService.getByIdWithAccessCheck(issueId);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return R.ok(timeEntryService.listByIssue(issueId, currentUserId));
    }

    /**
     * 汇总用户在日期范围内的总工时
     */
    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public R<Integer> summary(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long targetUserId = (userId != null) ? userId : currentUserId;

        if (!targetUserId.equals(currentUserId) && !canViewOthersTime(currentUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权查看他人工时记录");
        }
        return R.ok(timeEntryService.sumByUserAndDateRange(targetUserId, currentUserId, startDate, endDate));
    }

    /**
     * 按工作组汇总工时（工作群组视图）
     * 查询指定用户组（或所有组）的成员工时，按成员分组展示
     *
     * @param groupId   用户组 ID（可选，不传则返回所有组的概览）
     * @param startDate 开始日期
     * @param endDate   结束日期
     */
    @GetMapping("/by-group")
    @PreAuthorize("isAuthenticated()")
    public R<List<GroupTimeSummaryVO>> listByGroup(
            @RequestParam(value = "groupId", required = false) Long groupId,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return R.ok(timeEntryService.listByGroup(groupId, currentUserId, startDate, endDate));
    }

    /**
     * 按项目汇总工时（项目视图概览）
     */
    @GetMapping("/by-project")
    @PreAuthorize("isAuthenticated()")
    public R<List<ProjectTimeSummaryVO>> listByProject(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return R.ok(timeEntryService.listByProjectForUser(currentUserId, startDate, endDate));
    }

    /**
     * 查询指定项目在日期范围内的工时明细（项目视图详情）
     */
    @GetMapping("/by-project/{projectId}")
    @PreAuthorize("@perm.check(#projectId, 'project:view')")
    public R<List<TimeEntryVO>> listByProjectDetail(
            @PathVariable("projectId") Long projectId,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return R.ok(timeEntryService.listByProject(projectId, startDate, endDate, currentUserId));
    }

    // ========== 权限检查 API ==========

    /**
     * 获取可选择的用户列表（用于人员视图的用户选择器）
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
     */
    @GetMapping("/can-view-others")
    @PreAuthorize("isAuthenticated()")
    public R<Boolean> checkCanViewOthers() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return R.ok(canViewOthersTime(currentUserId));
    }

    /**
     * 检查当前用户是否有权编辑/删除他人工时
     */
    @GetMapping("/can-edit-others")
    @PreAuthorize("isAuthenticated()")
    public R<Boolean> checkCanEditOthers() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return R.ok(canEditOthersTime(currentUserId));
    }

    /**
     * 检查当前用户是否有权为他人记录工时
     */
    @GetMapping("/can-log-for-others")
    @PreAuthorize("isAuthenticated()")
    public R<Boolean> checkCanLogForOthers() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return R.ok(permissionService.hasPermissionInAnyProject(currentUserId, PERM_LOG_FOR_OTHERS));
    }

    // ========== 内部方法 ==========

    private boolean canViewOthersTime(Long userId) {
        return permissionService.hasPermissionInAnyProject(userId, PERM_VIEW_OTHERS);
    }

    private boolean canEditOthersTime(Long userId) {
        return permissionService.hasPermissionInAnyProject(userId, PERM_EDIT_ALL);
    }
}
