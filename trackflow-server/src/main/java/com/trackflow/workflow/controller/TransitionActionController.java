package com.trackflow.workflow.controller;

import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.workflow.converter.TransitionActionConverter;
import com.trackflow.workflow.dto.CreateTransitionActionDTO;
import com.trackflow.workflow.dto.UpdateTransitionActionDTO;
import com.trackflow.workflow.entity.TransitionAction;
import com.trackflow.workflow.service.TransitionActionService;
import com.trackflow.workflow.vo.TransitionActionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 转换动作管理 REST API
 */
@RestController
@RequestMapping("/api/v1/transition-actions")
@RequiredArgsConstructor
public class TransitionActionController {

    private final TransitionActionService transitionActionService;
    private final TransitionActionConverter transitionActionConverter;

    /**
     * 列表查询
     */
    @GetMapping
    @PreAuthorize("@perm.check(#projectId, 'workflow:manage')")
    public R<List<TransitionActionVO>> list(
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "issueType", required = false) String issueType,
            @RequestParam(value = "oldStatusId", required = false) Long oldStatusId,
            @RequestParam(value = "newStatusId", required = false) Long newStatusId) {

        List<TransitionAction> actions = transitionActionService.list(
                projectId, issueType, oldStatusId, newStatusId);
        return R.ok(transitionActionConverter.toVOList(actions));
    }

    /**
     * 创建转换动作
     */
    @PostMapping
    @PreAuthorize("@perm.check(#dto.projectId, 'workflow:manage')")
    public R<TransitionActionVO> create(@RequestBody @Valid CreateTransitionActionDTO dto) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        TransitionAction action = transitionActionService.create(dto, currentUserId);
        return R.ok(transitionActionConverter.toVO(action));
    }

    /**
     * 更新转换动作
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.check(@transitionActionService.getProjectId(#id), 'workflow:manage')")
    public R<TransitionActionVO> update(@PathVariable Long id,
                                        @RequestBody @Valid UpdateTransitionActionDTO dto) {
        TransitionAction action = transitionActionService.update(id, dto);
        return R.ok(transitionActionConverter.toVO(action));
    }

    /**
     * 删除转换动作
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.check(@transitionActionService.getProjectId(#id), 'workflow:manage')")
    public R<Void> delete(@PathVariable Long id) {
        transitionActionService.delete(id);
        return R.ok();
    }

    /**
     * 切换启用状态
     */
    @PatchMapping("/{id}/toggle")
    @PreAuthorize("@perm.check(@transitionActionService.getProjectId(#id), 'workflow:manage')")
    public R<Void> toggleEnabled(@PathVariable Long id) {
        transitionActionService.toggleEnabled(id);
        return R.ok();
    }
}
