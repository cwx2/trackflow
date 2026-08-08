package com.trackflow.query.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.converter.IssueConverter;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.service.IssueVOAssembler;
import com.trackflow.issue.vo.IssueVO;
import com.trackflow.project.service.ProjectService;
import com.trackflow.query.converter.SavedQueryConverter;
import com.trackflow.query.dto.BatchCountDTO;
import com.trackflow.query.dto.CreateQueryDTO;
import com.trackflow.query.dto.ExecuteQueryDTO;
import com.trackflow.query.dto.ReorderQueryItemDTO;
import com.trackflow.query.dto.UpdateQueryDTO;
import com.trackflow.query.service.SavedQueryService;
import com.trackflow.query.vo.QueryPanelItemVO;
import com.trackflow.query.vo.QueryPanelVO;
import com.trackflow.query.vo.SavedQueryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 保存查询接口 — 对应 YouTrack 左侧面板
 */
@RestController
@RequestMapping("/api/v1/queries")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class SavedQueryController {

    private final SavedQueryService savedQueryService;
    private final SavedQueryConverter savedQueryConverter;
    private final IssueConverter issueConverter;
    private final IssueVOAssembler issueVOAssembler;
    private final ProjectService projectService;

    /**
     * 获取查询面板（左侧面板数据 + 实时计数）
     * 只返回用户自己创建的 + 用户收藏的共享查询
     */
    @GetMapping("/panel")
    public R<QueryPanelVO> getPanel(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "hideResolved", required = false, defaultValue = "false") boolean hideResolved) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (projectId != null) {
            projectService.assertProjectAccessible(userId, projectId);
        }
        return R.ok(savedQueryService.getPanel(userId, projectId, hideResolved));
    }

    /**
     * 获取所有可用的共享查询（供"管理查询"面板使用）
     */
    @GetMapping("/available")
    public R<List<QueryPanelItemVO>> getAvailableQueries(
            @RequestParam(value = "projectId", required = false) Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(savedQueryService.getAvailableQueries(userId, projectId));
    }

    /**
     * 收藏查询（添加到面板）
     */
    @PostMapping("/{id}/favorite")
    public R<Void> addFavorite(@PathVariable("id") String id) {
        Long userId = SecurityUtils.getCurrentUserId();
        savedQueryService.addFavorite(userId, Long.parseLong(id));
        return R.ok();
    }

    /**
     * 取消收藏查询（从面板移除）
     */
    @DeleteMapping("/{id}/favorite")
    public R<Void> removeFavorite(@PathVariable("id") String id) {
        Long userId = SecurityUtils.getCurrentUserId();
        savedQueryService.removeFavorite(userId, Long.parseLong(id));
        return R.ok();
    }

    /**
     * 创建保存查询
     */
    @PostMapping
    public R<SavedQueryVO> create(@Valid @RequestBody CreateQueryDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (dto.getProjectId() != null) {
            projectService.assertProjectAccessible(userId, dto.getProjectId());
        }
        return R.ok(savedQueryConverter.toVO(savedQueryService.create(userId, dto)));
    }

    /**
     * 更新保存查询
     */
    @PutMapping("/{id}")
    public R<SavedQueryVO> update(@PathVariable("id") String id, @Valid @RequestBody UpdateQueryDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(savedQueryConverter.toVO(savedQueryService.update(Long.parseLong(id), userId, dto)));
    }

    /**
     * 删除保存查询
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") String id) {
        Long userId = SecurityUtils.getCurrentUserId();
        savedQueryService.delete(Long.parseLong(id), userId);
        return R.ok();
    }

    /**
     * 执行保存查询（返回匹配的 Issue 列表）
     * 查询结果会自动按用户所属项目过滤
     */
    @GetMapping("/{id}/results")
    public R<PageResult<IssueVO>> executeById(
            @PathVariable("id") String id,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
            @RequestParam(value = "hideResolved", required = false) String hideResolved,
            @RequestParam(value = "sort", required = false) String sort) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long queryId = Long.parseLong(id);
        Page<Issue> result = savedQueryService.executeByIdWithAccessCheck(queryId, page, pageSize, userId, "true".equals(hideResolved), sort);
        List<IssueVO> voList = issueConverter.toVOList(result.getRecords());
        issueVOAssembler.assemble(result.getRecords(), voList);
        return R.ok(new PageResult<>(voList, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize()));
    }

    /**
     * 即时执行查询（不保存）
     * 查询结果会自动按用户所属项目过滤
     */
    @PostMapping("/execute")
    public R<PageResult<IssueVO>> executeAdhoc(@RequestBody ExecuteQueryDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        Page<Issue> result = savedQueryService.executeAdhocWithAccessCheck(dto, userId);
        List<IssueVO> voList = issueConverter.toVOList(result.getRecords());
        issueVOAssembler.assemble(result.getRecords(), voList);
        return R.ok(new PageResult<>(voList, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize()));
    }

    /**
     * 批量获取查询计数（带项目成员过滤）
     */
    @PostMapping("/counts")
    public R<Map<String, Long>> batchCount(
            @Valid @RequestBody BatchCountDTO dto,
            @RequestParam(value = "projectId", required = false) Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (projectId != null) {
            projectService.assertProjectAccessible(userId, projectId);
        }
        return R.ok(savedQueryService.batchCountWithAccessCheck(dto.getQueryIds(), userId, projectId));
    }

    /**
     * 查询面板排序
     */
    @PutMapping("/reorder")
    public R<Void> reorder(@Valid @RequestBody List<ReorderQueryItemDTO> orders) {
        savedQueryService.reorder(orders);
        return R.ok();
    }
}
