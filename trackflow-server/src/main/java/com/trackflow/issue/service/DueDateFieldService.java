package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.entity.CustomFieldProject;
import com.trackflow.customfield.mapper.CustomFieldDefinitionMapper;
import com.trackflow.customfield.mapper.CustomFieldProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 截止日期字段服务 - 将 Due Date 作为自定义字段系统中的内置日期字段管理
 * <p>
 * Due Date 字段使用固定 ID = 1000000000000000003（由 V252 迁移脚本种子化）。
 * 物理数据仍存储在 issue.due_date 列中，本字段注册仅用于项目级可配置性：
 * <ul>
 *   <li>项目管理员可从项目中移除 Due Date 字段（使其不可见）</li>
 *   <li>可设置项目级必填性</li>
 *   <li>可设置私有性和角色级可见性</li>
 * </ul>
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DueDateFieldService {

    /** Due Date 自定义字段的固定 ID（V252 迁移脚本中定义） */
    public static final long DUE_DATE_FIELD_ID = 1000000000000000003L;

    private final CustomFieldDefinitionMapper definitionMapper;
    private final CustomFieldProjectMapper projectMapper;

    /**
     * 检查指定项目是否附加了 Due Date 字段。
     * <p>
     * 只有当字段已附加且未被排除时，返回 true。
     *
     * @param projectId 项目 ID
     * @return true 表示该项目启用了 Due Date 字段
     */
    public boolean isEnabledForProject(Long projectId) {
        if (projectId == null) {
            return true; // 无项目上下文时默认允许（兼容全局场景）
        }
        CustomFieldProject mapping = projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, DUE_DATE_FIELD_ID)
                        .eq(CustomFieldProject::getProjectId, projectId));
        if (mapping == null) {
            return false; // 未附加到项目
        }
        return !Boolean.TRUE.equals(mapping.getIsExcluded());
    }

    /**
     * 获取项目中 Due Date 字段的配置。
     *
     * @param projectId 项目 ID
     * @return 项目字段配置，null 表示未附加
     */
    public CustomFieldProject getProjectMapping(Long projectId) {
        if (projectId == null) {
            return null;
        }
        return projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, DUE_DATE_FIELD_ID)
                        .eq(CustomFieldProject::getProjectId, projectId));
    }

    /**
     * 检查 Due Date 在指定项目中是否必填。
     * <p>
     * 判断逻辑：项目级 canBeEmpty=false 或项目级 isRequired=true 时为必填。
     *
     * @param projectId 项目 ID
     * @return true 表示必填
     */
    public boolean isRequiredForProject(Long projectId) {
        CustomFieldProject mapping = getProjectMapping(projectId);
        if (mapping == null) {
            return false;
        }
        // canBeEmpty=false 表示不能为空
        if (Boolean.FALSE.equals(mapping.getCanBeEmpty())) {
            return true;
        }
        // 项目级 isRequired 覆盖
        if (Boolean.TRUE.equals(mapping.getIsRequired())) {
            return true;
        }
        return false;
    }

    /**
     * 获取 Due Date 自定义字段定义。
     */
    public CustomFieldDefinition getDueDateFieldDefinition() {
        CustomFieldDefinition field = definitionMapper.selectById(DUE_DATE_FIELD_ID);
        if (field == null) {
            throw BusinessException.notFound("Due Date 自定义字段定义不存在，请确认 V252 迁移脚本已执行");
        }
        return field;
    }
}
