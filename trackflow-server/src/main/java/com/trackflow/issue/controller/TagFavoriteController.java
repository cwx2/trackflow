package com.trackflow.issue.controller;

import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.dto.ReorderTagFavoritesDTO;
import com.trackflow.issue.service.IssueTagService;
import com.trackflow.issue.vo.TagFavoriteManagementVO;
import com.trackflow.issue.vo.TagPanelItemVO;
import com.trackflow.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标签收藏控制器 — 支持侧边栏 Tags 分区
 *
 * @author TrackFlow
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/tag-favorites")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TagFavoriteController {

    private final IssueTagService tagService;
    private final ProjectService projectService;

    /**
     * 获取当前用户的标签收藏面板数据（含匹配工单数量）
     */
    @GetMapping
    public R<List<TagPanelItemVO>> getPanel(
            @RequestParam(value = "projectId", required = false) Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<TagPanelItemVO> items = tagService.getFavoriteTagsPanel(userId, projectId);
        return R.ok(items);
    }

    /**
     * 添加标签到收藏
     */
    @PostMapping("/{tagId}")
    public R<Void> addFavorite(@PathVariable Long tagId) {
        Long userId = SecurityUtils.getCurrentUserId();
        tagService.addTagFavorite(userId, tagId);
        return R.ok();
    }

    /**
     * 从收藏中移除标签
     */
    @DeleteMapping("/{tagId}")
    public R<Void> removeFavorite(@PathVariable Long tagId) {
        Long userId = SecurityUtils.getCurrentUserId();
        tagService.removeTagFavorite(userId, tagId);
        return R.ok();
    }

    /**
     * 重新排序收藏标签（拖拽排序）
     */
    @PutMapping("/reorder")
    public R<Void> reorder(@Valid @RequestBody ReorderTagFavoritesDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        tagService.reorderTagFavorites(userId, dto.getTagIds());
        return R.ok();
    }

    /**
     * 获取所有可收藏的标签列表（管理收藏面板用）
     * 返回用户可访问项目下的所有标签，标记是否已收藏
     */
    @GetMapping("/available")
    public R<List<TagFavoriteManagementVO>> listAvailable(
            @RequestParam(value = "projectId", required = false) Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<Long> accessibleProjectIds;
        if (projectId != null) {
            accessibleProjectIds = List.of(projectId);
        } else {
            accessibleProjectIds = projectService.getAccessibleProjectIds(userId);
        }
        return R.ok(tagService.listAllTagsForFavoriteManagement(userId, accessibleProjectIds));
    }
}
