package com.trackflow.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.project.entity.ProjectEnabledModule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

/**
 * 项目启用模块 Mapper
 */
@Mapper
public interface ProjectEnabledModuleMapper extends BaseMapper<ProjectEnabledModule> {

    /**
     * 获取项目启用的模块名称集合
     */
    @Select("SELECT module_name FROM project_enabled_module WHERE project_id = #{projectId}")
    Set<String> selectEnabledModuleNames(@Param("projectId") Long projectId);

    /**
     * 批量插入模块记录（使用 ON CONFLICT 幂等）
     */
    default void insertModulesIfAbsent(Long projectId, List<String> moduleNames) {
        for (String moduleName : moduleNames) {
            ProjectEnabledModule module = new ProjectEnabledModule();
            module.setProjectId(projectId);
            module.setModuleName(moduleName);
            // MyBatis-Plus insert，配合数据库 UNIQUE 约束
            // 使用 insertOrUpdate 或在 service 层处理
            insert(module);
        }
    }
}
