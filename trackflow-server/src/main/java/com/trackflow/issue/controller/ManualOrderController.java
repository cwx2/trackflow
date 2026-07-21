package com.trackflow.issue.controller;

import com.trackflow.common.model.R;
import com.trackflow.issue.dto.ManualOrderDTO;
import com.trackflow.issue.dto.MoveOrderDTO;
import com.trackflow.issue.service.ManualOrderService;
import com.trackflow.issue.vo.ManualOrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 工单手动排序控制器 - 管理拖拽排序
 *
 * @author TrackFlow
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/manual-orders")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ManualOrderController {

    private final ManualOrderService manualOrderService;

    /**
     * 获取指定上下文的手动排序
     */
    @GetMapping
    public R<ManualOrderVO> getOrder(
            @RequestParam String contextType,
            @RequestParam Long contextId) {
        ManualOrderVO result = manualOrderService.getOrder(contextType, contextId);
        return R.ok(result);
    }

    /**
     * 保存完整的手动排序列表
     */
    @PostMapping
    public R<ManualOrderVO> saveOrder(@Valid @RequestBody ManualOrderDTO dto) {
        ManualOrderVO result = manualOrderService.saveOrder(dto);
        return R.ok(result);
    }

    /**
     * 移动单个工单到指定位置（前端拖拽单个 item 时使用）
     */
    @PutMapping("/move")
    public R<ManualOrderVO> moveItem(@Valid @RequestBody MoveOrderDTO dto) {
        ManualOrderVO result = manualOrderService.moveItem(dto);
        return R.ok(result);
    }

    /**
     * 丢弃手动排序（恢复默认排序）
     */
    @DeleteMapping
    public R<Void> discardOrder(
            @RequestParam String contextType,
            @RequestParam Long contextId) {
        manualOrderService.discardOrder(contextType, contextId);
        return R.ok();
    }
}
