package com.trackflow.automation.agent;

import com.trackflow.automation.agent.dto.AutomationRoleProfileDTO;
import com.trackflow.automation.agent.entity.AutomationRoleProfile;
import com.trackflow.common.model.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/automation/roles")
@PreAuthorize("@perm.checkGlobal('system:admin')")
public class AutomationRoleProfileController {
    private final AutomationRoleProfileService service;

    @GetMapping
    public R<List<AutomationRoleProfile>> list() { return R.ok(service.list()); }

    @GetMapping("/{id}")
    public R<AutomationRoleProfile> get(@PathVariable Long id) { return R.ok(service.get(id)); }

    @PostMapping
    public R<AutomationRoleProfile> create(@Valid @RequestBody AutomationRoleProfileDTO dto) {
        return R.ok(service.create(dto));
    }

    @PutMapping("/{id}")
    public R<AutomationRoleProfile> update(@PathVariable Long id,
                                           @Valid @RequestBody AutomationRoleProfileDTO dto) {
        return R.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
