package com.trackflow.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.project.entity.ProjectEnabledModule;
import com.trackflow.project.mapper.ProjectEnabledModuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 项目模块管理服务。
 * <p>
 * 参考 OpenProject 的 enabled_modules 机制：
 * - 每个项目可独立启用/禁用功能模块
 * - 权限检查时，只有启用模块下的权限才生效
 * - 新建项目默认启用所有模块（向后兼容）
 * <p>
 * 模块列表对应 sys_permission.category：
 * issue, sprint, time_tracking, report, integration, query, project
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectModuleService {

    private static final String CACHE_KEY_PREFIX = "project:modules:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    /**
     * 系统支持的所有可选模块。
     * <p>
     * "issue" 和 "project" 是基础模块，一般不建议禁用（但技术上允许）。
     * 前端可以在 UI 上标记哪些模块为"核心模块"不可禁用。
     */
    public static final List<String> ALL_MODULES = List.of(
            "issue",
            "sprint",
            "time_tracking",
            "report",
            "integration",
            "query",
            "project"
    );

    /**
     * 核心模块（项目必须启用，不允许禁用）
     */
    public static final Set<String> CORE_MODULES = Set.of("issue", "project");

    private final ProjectEnabledModuleMapper moduleMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 获取项目启用的模块集合（缓存优先）
     */
    public Set<String> getEnabledModules(Long projectId) {
        String cacheKey = CACHE_KEY_PREFIX + projectId;

        // 1. 缓存查询
        Set<String> cached = redisTemplate.opsForSet().members(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        // 2. 数据库查询
        Set<String> modules = moduleMapper.selectEnabledModuleNames(projectId);

        // 如果数据库中没有记录（老项目未初始化），视为全部启用
        if (modules == null || modules.isEmpty()) {
            modules = new HashSet<>(ALL_MODULES);
        }

        // 3. 写入缓存
        redisTemplate.opsForSet().add(cacheKey, modules.toArray(new String[0]));
        redisTemplate.expire(cacheKey, CACHE_TTL);

        return modules;
    }

    /**
     * 判断项目是否启用了指定模块
     */
    public boolean isModuleEnabled(Long projectId, String moduleName) {
        return getEnabledModules(projectId).contains(moduleName);
    }

    /**
     * 更新项目启用的模块列表。
     * <p>
     * 核心模块（issue、project）不可禁用。
     * 更新后清除模块缓存和相关权限缓存。
     *
     * @param projectId   项目 ID
     * @param moduleNames 新的启用模块列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateEnabledModules(Long projectId, List<String> moduleNames) {
        // 校验：核心模块不可禁用
        for (String core : CORE_MODULES) {
            if (!moduleNames.contains(core)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "核心模块 '" + core + "' 不可禁用");
            }
        }

        // 校验：不允许传入无效模块名
        for (String name : moduleNames) {
            if (!ALL_MODULES.contains(name)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "无效的模块名称: " + name);
            }
        }

        // 删除旧记录
        moduleMapper.delete(new LambdaQueryWrapper<ProjectEnabledModule>()
                .eq(ProjectEnabledModule::getProjectId, projectId));

        // 插入新记录
        for (String moduleName : moduleNames) {
            ProjectEnabledModule module = new ProjectEnabledModule();
            module.setProjectId(projectId);
            module.setModuleName(moduleName);
            module.setCreatedAt(LocalDateTime.now());
            moduleMapper.insert(module);
        }

        // 清除缓存
        invalidateCache(projectId);

        log.info("Project {} modules updated to: {}", projectId, moduleNames);
    }

    /**
     * 为新建项目初始化默认模块（全部启用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void initializeDefaultModules(Long projectId) {
        for (String moduleName : ALL_MODULES) {
            ProjectEnabledModule module = new ProjectEnabledModule();
            module.setProjectId(projectId);
            module.setModuleName(moduleName);
            module.setCreatedAt(LocalDateTime.now());
            moduleMapper.insert(module);
        }
        log.debug("Initialized default modules for project {}", projectId);
    }

    /**
     * 过滤权限集合，移除未启用模块的权限。
     * <p>
     * 这是核心逻辑——权限检查时调用此方法对角色权限取交集。
     * 全局权限（scope=global）不受模块过滤影响。
     *
     * @param projectId   项目 ID
     * @param permissions 角色拥有的权限集合
     * @param permissionCategoryMap 权限码→所属模块映射（从 sys_permission.category 加载）
     * @return 过滤后的权限集合
     */
    public Set<String> filterByEnabledModules(Long projectId, Set<String> permissions,
                                               Map<String, String> permissionCategoryMap) {
        Set<String> enabledModules = getEnabledModules(projectId);

        Set<String> filtered = new HashSet<>();
        for (String perm : permissions) {
            String category = permissionCategoryMap.get(perm);
            // 如果权限不在映射中（比如 system:admin），则不过滤
            if (category == null || enabledModules.contains(category)) {
                filtered.add(perm);
            }
        }
        return filtered;
    }

    /**
     * 清除指定项目的模块缓存
     */
    public void invalidateCache(Long projectId) {
        String cacheKey = CACHE_KEY_PREFIX + projectId;
        redisTemplate.delete(cacheKey);
    }
}
