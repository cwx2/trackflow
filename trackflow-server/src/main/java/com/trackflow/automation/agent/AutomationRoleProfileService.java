package com.trackflow.automation.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.agent.dto.AutomationRoleProfileDTO;
import com.trackflow.automation.agent.entity.AutomationRoleProfile;
import com.trackflow.automation.agent.mapper.AutomationRoleProfileMapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AutomationRoleProfileService {
    private static final Set<String> PROVIDERS = Set.of("cli", "http", "openai_compatible");
    private final AutomationRoleProfileMapper mapper;
    private final ObjectMapper objectMapper;

    public List<AutomationRoleProfile> list() {
        return mapper.selectList(new LambdaQueryWrapper<AutomationRoleProfile>()
                .orderByAsc(AutomationRoleProfile::getName));
    }

    public AutomationRoleProfile get(Long id) {
        AutomationRoleProfile profile = mapper.selectById(id);
        if (profile == null) {
            throw BusinessException.notFound("Agent 角色", id);
        }
        return profile;
    }

    @Transactional
    public AutomationRoleProfile create(AutomationRoleProfileDTO dto) {
        AutomationRoleProfile profile = new AutomationRoleProfile();
        apply(profile, dto);
        profile.setCreatedBy(SecurityUtils.getCurrentUserId());
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        mapper.insert(profile);
        return profile;
    }

    @Transactional
    public AutomationRoleProfile update(Long id, AutomationRoleProfileDTO dto) {
        AutomationRoleProfile profile = get(id);
        apply(profile, dto);
        profile.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(profile);
        return mapper.selectById(id);
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        mapper.deleteById(id);
    }

    private void apply(AutomationRoleProfile profile, AutomationRoleProfileDTO dto) {
        if (!PROVIDERS.contains(dto.getProviderType())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "不支持的 Agent provider: " + dto.getProviderType());
        }
        profile.setName(dto.getName());
        profile.setDescription(dto.getDescription());
        profile.setProviderType(dto.getProviderType());
        profile.setModel(dto.getModel());
        profile.setSystemPrompt(dto.getSystemPrompt() != null ? dto.getSystemPrompt() : "");
        profile.setToolPolicy(validJson(dto.getToolPolicy()));
        profile.setOutputSchema(validJson(dto.getOutputSchema()));
        profile.setWorkspacePolicy(validJson(dto.getWorkspacePolicy()));
        profile.setEnabled(dto.getEnabled() == null || dto.getEnabled());
    }

    private String validJson(String value) {
        String json = value == null || value.isBlank() ? "{}" : value;
        try {
            objectMapper.readTree(json);
            return json;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "角色策略必须是合法 JSON: " + exception.getMessage());
        }
    }
}
