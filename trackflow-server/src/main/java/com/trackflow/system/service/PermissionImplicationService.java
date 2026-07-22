package com.trackflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.system.entity.PermissionImplication;
import com.trackflow.system.entity.SysPermission;
import com.trackflow.system.mapper.PermissionImplicationMapper;
import com.trackflow.system.mapper.SysPermissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限隐含关系服务 - 处理权限的自动补全和级联移除
 * <p>
 * 核心逻辑：
 * - 添加上层权限时，自动包含其隐含的底层权限（传递闭包）
 * - 移除底层权限时，自动级联移除依赖它的上层权限
 * <p>
 * 安全校验：解析出的权限码必须在 sys_permission 表中实际存在，否则跳过并记录警告日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionImplicationService {

    private final PermissionImplicationMapper permissionImplicationMapper;
    private final SysPermissionMapper sysPermissionMapper;

    /**
     * 解析隐含权限：给定一组权限，返回包含所有传递性隐含权限的完整集合。
     * <p>
     * 例如：输入 {issue:manage_comments}，输出 {issue:comment, issue:view}
     * （因为 issue:manage_comments → issue:comment → issue:view）
     * <p>
     * 安全校验：解析出的权限码如果不在 sys_permission 表中，将被跳过并记录警告日志。
     *
     * @param permissions 输入权限集合
     * @return 所有被隐含的权限（不含输入本身）
     */
    public Set<String> resolveImpliedPermissions(Set<String> permissions) {
        List<PermissionImplication> allImplications = permissionImplicationMapper.selectList(null);
        Set<String> validPermissionCodes = loadValidPermissionCodes();

        // 构建 permission_code → Set<implied_code> 的映射
        Map<String, Set<String>> implicationMap = allImplications.stream()
                .collect(Collectors.groupingBy(
                        PermissionImplication::getPermissionCode,
                        Collectors.mapping(PermissionImplication::getImpliedCode, Collectors.toSet())
                ));

        // BFS 传递闭包
        Set<String> resolved = new HashSet<>();
        Set<String> queue = new HashSet<>(permissions);

        while (!queue.isEmpty()) {
            Set<String> nextQueue = new HashSet<>();
            for (String perm : queue) {
                Set<String> implied = implicationMap.get(perm);
                if (implied != null) {
                    for (String imp : implied) {
                        if (!resolved.contains(imp) && !permissions.contains(imp)) {
                            // 校验：隐含的权限码必须在 sys_permission 表中实际存在
                            if (!validPermissionCodes.contains(imp)) {
                                log.warn("Permission implication references non-existent permission code '{}' " +
                                        "(implied by '{}'), skipping. Please fix sys_permission_implication data.", imp, perm);
                                continue;
                            }
                            resolved.add(imp);
                            nextQueue.add(imp);
                        }
                    }
                }
            }
            queue = nextQueue;
        }

        return resolved;
    }

    /**
     * 解析依赖移除：给定被移除的权限和当前权限集，计算需要级联移除的上层权限。
     * <p>
     * 例如：移除 issue:view 时，所有依赖 issue:view 的权限（如 issue:create, comment:create 等）
     * 如果其隐含链中 issue:view 是唯一来源，则也必须被移除。
     *
     * @param removedPermissions 被移除的权限
     * @param currentPermissions 当前完整权限集（移除前）
     * @return 需要额外级联移除的权限
     */
    public Set<String> resolveDependentRemovals(Set<String> removedPermissions, Set<String> currentPermissions) {
        List<PermissionImplication> allImplications = permissionImplicationMapper.selectList(null);

        // 构建 implied_code → Set<permission_code> 的反向映射（谁依赖我）
        Map<String, Set<String>> reverseDependencyMap = allImplications.stream()
                .collect(Collectors.groupingBy(
                        PermissionImplication::getImpliedCode,
                        Collectors.mapping(PermissionImplication::getPermissionCode, Collectors.toSet())
                ));

        // 构建 permission_code → Set<implied_code> 的正向映射
        Map<String, Set<String>> implicationMap = allImplications.stream()
                .collect(Collectors.groupingBy(
                        PermissionImplication::getPermissionCode,
                        Collectors.mapping(PermissionImplication::getImpliedCode, Collectors.toSet())
                ));

        // 模拟移除后的权限集
        Set<String> remainingPermissions = new HashSet<>(currentPermissions);
        remainingPermissions.removeAll(removedPermissions);

        // BFS: 从被移除的权限出发，找到所有依赖它们的上层权限
        Set<String> dependentRemovals = new HashSet<>();
        Set<String> toCheck = new HashSet<>(removedPermissions);

        while (!toCheck.isEmpty()) {
            Set<String> nextCheck = new HashSet<>();
            for (String removed : toCheck) {
                Set<String> dependents = reverseDependencyMap.get(removed);
                if (dependents == null) continue;

                for (String dependent : dependents) {
                    if (!remainingPermissions.contains(dependent)) continue;
                    if (dependentRemovals.contains(dependent)) continue;

                    // 检查该上层权限的所有隐含需求是否仍然满足
                    Set<String> requiredImplied = implicationMap.get(dependent);
                    if (requiredImplied != null) {
                        boolean allSatisfied = requiredImplied.stream()
                                .allMatch(imp -> remainingPermissions.contains(imp) && !dependentRemovals.contains(imp));
                        if (!allSatisfied) {
                            dependentRemovals.add(dependent);
                            remainingPermissions.remove(dependent);
                            nextCheck.add(dependent);
                        }
                    }
                }
            }
            toCheck = nextCheck;
        }

        return dependentRemovals;
    }

    /**
     * 获取所有隐含关系记录
     *
     * @return 全部权限隐含关系
     */
    public List<PermissionImplication> getAllImplications() {
        return permissionImplicationMapper.selectList(null);
    }

    /**
     * 从 sys_permission 表加载所有有效的权限码集合。
     * 用于校验隐含关系中引用的权限码是否真实存在。
     *
     * @return 所有有效权限码的集合
     */
    private Set<String> loadValidPermissionCodes() {
        return sysPermissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>().select(SysPermission::getCode)
        ).stream()
                .map(SysPermission::getCode)
                .collect(Collectors.toSet());
    }
}
