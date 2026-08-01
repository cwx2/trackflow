package com.trackflow.automation.workitem;

import com.trackflow.automation.workitem.entity.AutomationWorkItem;
import com.trackflow.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/automation/work-items")
@PreAuthorize("@perm.checkGlobal('system:admin')")
public class AutomationWorkItemController {
    private final AutomationWorkItemService service;

    @GetMapping
    public R<List<AutomationWorkItem>> list(@RequestParam(required = false) String state,
                                            @RequestParam(defaultValue = "100") int limit) {
        return R.ok(service.list(state, limit));
    }

    @PostMapping("/{id}/retry")
    public R<Void> retry(@PathVariable Long id) { service.retry(id); return R.ok(); }

    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) { service.cancelQueued(id); return R.ok(); }
}
