package com.trackflow.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

/**
 * 用户同步服务：从 Keycloak JWT 同步用户信息到本地数据库
 *
 * 最小权限原则：新用户首次登录仅创建 sys_user 记录，不自动分配任何全局角色。
 * 唯一例外：系统中无任何用户时（全新安装），首位用户自动成为系统管理员。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSyncService {

    private final SysUserMapper userMapper;
    private final UserRoleMapper userRoleMapper;

    /** 系统管理员角色 ID */
    private static final Long SYSTEM_ADMIN_ROLE_ID = 1L;

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
        String displayName = jwt.getClaimAsString("name");
        String email = jwt.getClaimAsString("email");

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
                    user.setDisplayName(displayName != null ? displayName : user.getDisplayName());
                    user.setEmail(email != null ? email : user.getEmail());
                    user.setLastLoginAt(LocalDateTime.now());
                    userMapper.updateById(user);
                    return user;
                }
            }

            // 真正的首次登录：创建用户
            user = new SysUser();
            user.setKeycloakId(keycloakId);
            user.setUsername(username != null ? username : keycloakId);
            user.setDisplayName(displayName != null ? displayName : username);
            user.setEmail(email);
            user.setStatus("active");
            user.setLastLoginAt(LocalDateTime.now());
            userMapper.insert(user);

            // 仅当系统中没有任何管理员时（全新安装），首位用户自动成为管理员
            if (isFirstAdmin()) {
                UserRole userRole = new UserRole();
                userRole.setUserId(user.getId());
                userRole.setRoleId(SYSTEM_ADMIN_ROLE_ID);
                userRoleMapper.insert(userRole);
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

            // 更新基本信息
            user.setDisplayName(displayName != null ? displayName : user.getDisplayName());
            user.setEmail(email != null ? email : user.getEmail());
            user.setLastLoginAt(LocalDateTime.now());
            userMapper.updateById(user);
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
}
