package com.trackflow.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.constant.UserStatus;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.entity.UserRole;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 用户同步服务：从 Keycloak JWT 同步用户信息到本地数据库
 * <p>
 * 最小权限原则：新用户首次登录仅创建 sys_user 记录，不自动分配任何全局角色。
 * 唯一例外：系统中无任何用户时（全新安装），首位用户自动成为系统管理员。
 * <p>
 * Keycloak 角色映射：JWT 中的 realm_access.roles 包含 tf_admin/tf_user 角色，
 * 用户首次创建时会根据 Keycloak 角色自动映射到 TrackFlow 内部角色。
 * 已有用户登录时，如果 Keycloak 新增了 tf_admin 角色但本地没有 system_admin，也会补充分配。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSyncService {

    private final SysUserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionService permissionService;

    /** 系统管理员角色 ID */
    private static final Long SYSTEM_ADMIN_ROLE_ID = 1L;

    /** Keycloak realm role: 系统管理员 */
    private static final String KC_ROLE_ADMIN = "tf_admin";

    /**
     * Keycloak realm role: 普通用户
     * 预留：后续可用于新用户首次登录时自动映射为 TrackFlow 默认角色
     */
    private static final String KC_ROLE_USER = "tf_user";

    /**
     * 同步 JWT 用户信息到本地数据库
     * - 首次登录：创建用户（不分配角色，除非是系统首位用户）
     * - 再次登录：更新基本信息
     * - 用户被禁用：抛出异常
     *
     * @return 本地用户记录
     */
    @Transactional
    public SysUser syncFromJwt(Jwt jwt) {
        String keycloakId = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        String givenName = jwt.getClaimAsString("given_name");
        String familyName = jwt.getClaimAsString("family_name");
        String displayName = buildDisplayName(givenName, familyName, jwt.getClaimAsString("name"), username);
        String email = jwt.getClaimAsString("email");
        List<String> keycloakRoles = extractRealmRoles(jwt);

        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getKeycloakId, keycloakId)
        );

        if (user == null) {
            // keycloak_id 不匹配时，尝试按 username 查找（处理预置数据 keycloak_id 占位符的情况）
            if (username != null) {
                user = userMapper.selectOne(
                        new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)
                );
                if (user != null) {
                    // 检查是否被禁用
                    if ("disabled".equals(user.getStatus())) {
                        throw new BusinessException(ErrorCode.USER_DISABLED);
                    }
                    // 找到了匹配的用户，更新 keycloak_id
                    log.info("Matched existing user by username '{}', updating keycloakId from '{}' to '{}'",
                            username, user.getKeycloakId(), keycloakId);
                    user.setKeycloakId(keycloakId);
                    // 保护规则：如果数据库中已有 CJK 姓名，不要被 JWT 中的非 CJK 名覆盖
                    if (displayName != null) {
                        boolean dbHasCjk = containsCjk(user.getDisplayName());
                        boolean jwtHasCjk = containsCjk(displayName);
                        if (jwtHasCjk || !dbHasCjk) {
                            user.setDisplayName(displayName);
                        }
                    }
                    user.setEmail(email != null ? email : user.getEmail());
                    user.setLastLoginAt(LocalDateTime.now());
                    userMapper.updateById(user);
                    // 同步 Keycloak 角色到本地（补充缺失的 system_admin）
                    syncKeycloakRolesToLocal(user, keycloakRoles);
                    return user;
                }
            }

            // 真正的首次登录：创建用户
            user = new SysUser();
            user.setKeycloakId(keycloakId);
            user.setUsername(username != null ? username : keycloakId);
            user.setDisplayName(displayName != null ? displayName : username);
            user.setEmail(email);
            user.setStatus(UserStatus.ACTIVE);
            user.setLastLoginAt(LocalDateTime.now());
            userMapper.insert(user);

            // 优先使用 Keycloak 角色映射
            if (keycloakRoles.contains(KC_ROLE_ADMIN)) {
                assignSystemAdminIfMissing(user);
                log.info("New user synced from Keycloak: {} ({}), assigned system_admin (Keycloak tf_admin role)",
                        username, keycloakId);
            } else if (isFirstAdmin()) {
                // 仅当系统中没有任何管理员时（全新安装），首位用户自动成为管理员
                assignSystemAdminIfMissing(user);
                log.info("First user synced from Keycloak: {} ({}), auto-assigned system_admin role (initial setup)",
                        username, keycloakId);
            } else {
                log.info("New user synced from Keycloak: {} ({}), no role assigned (least privilege)",
                        username, keycloakId);
            }
        } else {
            // 检查是否被禁用
            if ("disabled".equals(user.getStatus())) {
                throw new BusinessException(ErrorCode.USER_DISABLED);
            }

            // 由于 Filter 层 syncCache 机制，此方法现在仅在缓存过期时被调用（约每 5 分钟一次/用户）。
            // 因此每次调用都执行 UPDATE 是可接受的（频率已从 ~2500/min 降至 ~50/min）。
            // 仅在 displayName/email 实际变化时更新对应字段。
            // 保护规则：如果数据库中已有 CJK 姓名，不要被 JWT 中的非 CJK 名覆盖
            // （处理 Keycloak 内部数据库与 realm JSON 不同步的情况）
            if (displayName != null && !displayName.equals(user.getDisplayName())) {
                boolean dbHasCjk = containsCjk(user.getDisplayName());
                boolean jwtHasCjk = containsCjk(displayName);
                // 只在以下情况更新：JWT 是 CJK 名，或数据库不是 CJK 名
                if (jwtHasCjk || !dbHasCjk) {
                    user.setDisplayName(displayName);
                } else {
                    log.debug("Preserving CJK display_name '{}' for user '{}', ignoring JWT name '{}'",
                            user.getDisplayName(), user.getUsername(), displayName);
                }
            }
            if (email != null && !email.equals(user.getEmail())) {
                user.setEmail(email);
            }
            user.setLastLoginAt(LocalDateTime.now());
            userMapper.updateById(user);

            // 同步 Keycloak 角色到本地（补充缺失的 system_admin）
            syncKeycloakRolesToLocal(user, keycloakRoles);
        }

        return user;
    }

    /**
     * 判断当前系统是否还没有任何管理员用户（全新安装场景）。
     * 查询 user_role 表中是否存在 role_id = 1 的记录。
     */
    private boolean isFirstAdmin() {
        Long count = userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, SYSTEM_ADMIN_ROLE_ID)
        );
        return count == 0;
    }

    /**
     * 从 JWT 中提取 Keycloak realm_access.roles 列表
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return Collections.emptyList();
        }
        Object roles = realmAccess.get("roles");
        if (roles instanceof List<?>) {
            return (List<String>) roles;
        }
        return Collections.emptyList();
    }

    /**
     * 仅同步 Keycloak 角色到本地（不更新用户基本信息）。
     * 供 UserSyncFilter 快速路径中检测到角色变化时调用，避免完整 syncFromJwt 的开销。
     *
     * @param userId        本地用户 ID
     * @param keycloakRoles 当前 JWT 中的 realm_access.roles 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncKeycloakRolesOnly(Long userId, List<String> keycloakRoles) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || "disabled".equals(user.getStatus())) {
            return;
        }
        syncKeycloakRolesToLocal(user, keycloakRoles);
    }

    /**
     * 同步 Keycloak 角色到本地 TrackFlow 角色。
     * 策略：双向同步——Keycloak 作为权限 Single Source of Truth。
     * - Keycloak 有 tf_admin 但本地缺少 system_admin 时补上
     * - Keycloak 无 tf_admin 但本地有 system_admin 时撤销（含安全保护）
     */
    private void syncKeycloakRolesToLocal(SysUser user, List<String> keycloakRoles) {
        if (keycloakRoles.contains(KC_ROLE_ADMIN)) {
            assignSystemAdminIfMissing(user);
        } else {
            // 反向同步：Keycloak 无 tf_admin 则考虑撤销本地 system_admin
            revokeSystemAdminIfNoLongerInKeycloak(user);
        }
    }

    /**
     * 撤销用户的 system_admin 角色（当 Keycloak 不再包含 tf_admin 时）。
     * 安全保护：确保系统中至少保留一个 system_admin，避免无管理员状态。
     * 仅撤销来源为 keycloak 的角色分配，保留管理员手动分配的角色。
     */
    private void revokeSystemAdminIfNoLongerInKeycloak(SysUser user) {
        // 仅检查来源为 keycloak 的 system_admin 记录
        Long userAdminCount = userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, user.getId())
                        .eq(UserRole::getRoleId, SYSTEM_ADMIN_ROLE_ID)
                        .eq(UserRole::getSource, UserRole.SOURCE_KEYCLOAK)
        );
        if (userAdminCount == 0) {
            return; // 没有 keycloak 来源的 system_admin，无需操作（可能有 manual 来源的，保留不动）
        }

        // 安全保护：确保不会移除最后一个管理员
        Long totalAdmins = userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getRoleId, SYSTEM_ADMIN_ROLE_ID)
        );
        if (totalAdmins <= 1) {
            log.warn("Skipping admin role revocation for user '{}': " +
                    "last system admin cannot be removed via Keycloak sync",
                    user.getUsername());
            return;
        }

        // 仅撤销 keycloak 来源的角色，保留 manual 来源的
        userRoleMapper.delete(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, user.getId())
                        .eq(UserRole::getRoleId, SYSTEM_ADMIN_ROLE_ID)
                        .eq(UserRole::getSource, UserRole.SOURCE_KEYCLOAK)
        );

        // 失效权限缓存
        permissionService.invalidateCache(user.getId());

        log.info("Revoked keycloak-synced system_admin from user '{}' (Keycloak tf_admin role removed, manual assignments preserved)",
                user.getUsername());
    }

    /**
     * 构建用户显示名称。
     * <p>
     * 规则：
     * 1. 如果 givenName 或 familyName 包含 CJK 字符（中日韩），使用"姓+名"（无空格）
     * 2. 否则使用西方顺序"givenName + 空格 + familyName"
     * 3. 如果 givenName 和 familyName 都为空，回退到 Keycloak name claim 或 username
     *
     * @param givenName  名（Keycloak given_name / firstName）
     * @param familyName 姓（Keycloak family_name / lastName）
     * @param nameClaim  Keycloak name claim（可能已拼接好）
     * @param username   用户名（最后兜底）
     * @return 正确格式的显示名称
     */
    public static String buildDisplayName(String givenName, String familyName, String nameClaim, String username) {
        if (givenName != null && !givenName.isBlank() && familyName != null && !familyName.isBlank()) {
            if (containsCjk(givenName) || containsCjk(familyName)) {
                // CJK 姓名：姓 + 名（无空格）
                return familyName + givenName;
            } else {
                // 西方姓名：名 + 空格 + 姓
                return givenName + " " + familyName;
            }
        }
        // 只有一个字段有值
        if (givenName != null && !givenName.isBlank()) {
            return givenName;
        }
        if (familyName != null && !familyName.isBlank()) {
            return familyName;
        }
        // 都为空，回退到 name claim 或 username
        if (nameClaim != null && !nameClaim.isBlank()) {
            return nameClaim;
        }
        return username;
    }

    /**
     * 判断字符串是否包含 CJK（中日韩）统一表意文字
     */
    private static boolean containsCjk(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    /**
     * 如果用户本地没有 system_admin 角色，则分配（来源标记为 keycloak）
     */
    private void assignSystemAdminIfMissing(SysUser user) {
        Long count = userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, user.getId())
                        .eq(UserRole::getRoleId, SYSTEM_ADMIN_ROLE_ID)
        );
        if (count == 0) {
            UserRole userRole = new UserRole();
            userRole.setUserId(user.getId());
            userRole.setRoleId(SYSTEM_ADMIN_ROLE_ID);
            userRole.setSource(UserRole.SOURCE_KEYCLOAK);
            userRoleMapper.insert(userRole);
            // 失效权限缓存，确保新角色立即生效
            permissionService.invalidateCache(user.getId());
            log.info("Assigned system_admin role to user '{}' (synced from Keycloak tf_admin, source=keycloak)",
                    user.getUsername());
        }
    }
}
