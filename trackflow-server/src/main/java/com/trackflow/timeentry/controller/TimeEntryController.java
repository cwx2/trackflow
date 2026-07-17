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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
    public R<TimeEntryVO> update(@PathVariable("id") Long id, @Valid @RequestBody UpdateTimeEntryDTO dto) {
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
    public R<Void> delete(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        timeEntryService.delete(id, userId);
        return R.ok();
    }

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

        if (!targetUserId.equals(currentUserId)) {
            // 校验当前用户是否有权查看他人工时
            if (!canViewOthersTime(currentUserId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权查看他人工时记录");
            }
        }

        // 向下兼容：如果前端传了 workType (name) 但没传 activityId，尝试按名称匹配
        Long resolvedActivityId = activityId;
        if (resolvedActivityId == null && workType != null && !workType.isBlank()) {
            resolvedActivityId = workItemAttributeService.findWorkTypeValueIdByName(workType);
        }

        return R.ok(timeEntryService.listByUserAndDateRange(targetUserId, startDate, endDate, projectId, resolvedActivityId));
    }

    /**
     * 查询某 Issue 的工时记录（校验项目成员权限）
     */
    @GetMapping("/issue/{issueId}")
    @PreAuthorize("isAuthenticated()")
    public R<List<TimeEntryVO>> listByIssue(@PathVariable("issueId") Long issueId) {
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
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
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

    /**
     * 检查当前用户是否有权编辑/删除他人工时
     * 返回布尔值供前端判断是否对他人工时显示编辑/删除按钮
     */
    @GetMapping("/can-edit-others")
    @PreAuthorize("isAuthenticated()")
    public R<Boolean> checkCanEditOthers() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return R.ok(canEditOthersTime(currentUserId));
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
     * 判断用户是否有权编辑/删除他人工时
     * 系统管理员（system:admin）或在任何项目中拥有 time:edit_all 的用户
     */
    private boolean canEditOthersTime(Long userId) {
        return permissionService.hasPermissionInAnyProject(userId, PERM_EDIT_ALL);
    }

    /**
     * 检查当前用户是否有权为他人记录工时
     * 返回布尔值供前端判断是否显示用户选择器
     */
    @GetMapping("/can-log-for-others")
    @PreAuthorize("isAuthenticated()")
    public R<Boolean> checkCanLogForOthers() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return R.ok(permissionService.hasPermissionInAnyProject(currentUserId, PERM_LOG_FOR_OTHERS));
    }

    /**
     * 构建 TimeEntryVO（含属性值）
     */
    private TimeEntryVO buildEntryVO(TimeEntry entry) {
        TimeEntryVO vo = new TimeEntryVO();
        vo.setId(String.valueOf(entry.getId()));
        vo.setIssueId(String.valueOf(entry.getIssueId()));
        vo.setProjectId(String.valueOf(entry.getProjectId()));
        vo.setUserId(String.valueOf(entry.getUserId()));
        vo.setWorkDate(entry.getWorkDate().toString());
        vo.setDuration(entry.getDuration());
        vo.setStartTime(entry.getStartTime());
        vo.setDescription(entry.getDescription());

        // loggedBy 信息
        if (entry.getLoggedBy() != null) {
            vo.setLoggedBy(String.valueOf(entry.getLoggedBy()));
            if (!entry.getLoggedBy().equals(entry.getUserId())) {
                // 代录场景：查询操作人姓名
                vo.setLoggedByName(timeEntryService.getUserDisplayNamePublic(entry.getLoggedBy()));
            }
        }

        // 加载属性值
        Map<String, Map<String, String>> attrValues = workItemAttributeService.getTimeEntryAttributeValues(entry.getId());
        if (!attrValues.isEmpty()) {
            vo.setAttributeValues(attrValues.entrySet().stream().map(e -> {
                Map<String, String> item = new java.util.HashMap<>(e.getValue());
                item.put("attributeId", e.getKey());
                return item;
            }).toList());

            // Extract Work type specifically (attribute id "1")
            Map<String, String> workTypeInfo = attrValues.get("1");
            if (workTypeInfo != null) {
                vo.setWorkType(workTypeInfo.get("valueName"));
                vo.setWorkTypeId(workTypeInfo.get("valueId"));
                vo.setWorkTypeColor(workTypeInfo.get("valueColor"));
            }
        }

        return vo;
    }
}
