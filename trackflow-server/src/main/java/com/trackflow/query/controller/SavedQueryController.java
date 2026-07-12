package com.trackflow.query.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.converter.IssueConverter;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.vo.IssueVO;
import com.trackflow.query.converter.SavedQueryConverter;
import com.trackflow.query.dto.CreateQueryDTO;
import com.trackflow.query.dto.ExecuteQueryDTO;
import com.trackflow.query.dto.UpdateQueryDTO;
import com.trackflow.query.service.SavedQueryService;
import com.trackflow.query.vo.SavedQueryVO;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 保存查询接口 — 对应 YouTrack 左侧面板
 */
@RestController
@RequestMapping("/api/v1/queries")
@RequiredArgsConstructor
public class SavedQueryController {

    private final SavedQueryService savedQueryService;
    private final SavedQueryConverter savedQueryConverter;
    private final IssueConverter issueConverter;
    private final SysUserMapper sysUserMapper;

    /**
     * 获取查询面板（左侧面板数据 + 实时计数）
     */
    @GetMapping("/panel")
    public R<Map<String, Object>> getPanel(@RequestParam(required = false) Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(savedQueryService.getPanel(userId, projectId));
    }

    /**
     * 创建保存查询
     */
    @PostMapping
    public R<SavedQueryVO> create(@Valid @RequestBody CreateQueryDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(savedQueryConverter.toVO(savedQueryService.create(userId, dto)));
    }

    /**
     * 更新保存查询
     */
    @PutMapping("/{id}")
    public R<SavedQueryVO> update(@PathVariable Long id, @Valid @RequestBody UpdateQueryDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(savedQueryConverter.toVO(savedQueryService.update(id, userId, dto)));
    }

    /**
     * 删除保存查询
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        savedQueryService.delete(id, userId);
        return R.ok();
    }

    /**
     * 执行保存查询（返回匹配的 Issue 列表）
     */
    @GetMapping("/{id}/results")
    public R<PageResult<IssueVO>> executeById(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Page<Issue> result = savedQueryService.executeById(id, page, pageSize);
        List<IssueVO> voList = issueConverter.toVOList(result.getRecords());
        fillAssigneeNames(result.getRecords(), voList);
        PageResult<IssueVO> pageResult = new PageResult<>(
                voList, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
        return R.ok(pageResult);
    }

    /**
     * 即时执行查询（不保存）
     */
    @PostMapping("/execute")
    public R<PageResult<IssueVO>> executeAdhoc(@RequestBody ExecuteQueryDTO dto) {
        Page<Issue> result = savedQueryService.executeAdhoc(dto);
        List<IssueVO> voList = issueConverter.toVOList(result.getRecords());
        fillAssigneeNames(result.getRecords(), voList);
        PageResult<IssueVO> pageResult = new PageResult<>(
                voList, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
        return R.ok(pageResult);
    }

    /**
     * 批量填充 assigneeName（复用逻辑）
     */
    private void fillAssigneeNames(List<Issue> records, List<IssueVO> voList) {
        List<Long> assigneeIds = records.stream()
                .map(Issue::getAssigneeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!assigneeIds.isEmpty()) {
            Map<Long, String> userNameMap = sysUserMapper.selectBatchIds(assigneeIds).stream()
                    .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName, (a, b) -> a));
            for (int i = 0; i < records.size(); i++) {
                Issue issue = records.get(i);
                if (issue.getAssigneeId() != null) {
                    voList.get(i).setAssigneeName(userNameMap.get(issue.getAssigneeId()));
                }
            }
        }
    }

    /**
     * 批量获取查询计数
     */
    @PostMapping("/counts")
    public R<Map<Long, Long>> batchCount(@RequestBody Map<String, List<Long>> body) {
        List<Long> queryIds = body.get("queryIds");
        return R.ok(savedQueryService.batchCount(queryIds));
    }

    /**
     * 查询面板排序
     */
    @PutMapping("/reorder")
    public R<Void> reorder(@RequestBody List<Map<String, Object>> orders) {
        savedQueryService.reorder(orders);
        return R.ok();
    }
}
