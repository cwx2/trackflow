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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSyncService {

    private final SysUserMapper userMapper;
    private final UserRoleMapper userRoleMapper;

    /**
     * 内部系统：新用户自动分配系统管理员角色
     */
    private static final Long DEFAULT_ROLE_ID = 1L;

    /**
     * 同步 JWT 用户信息到本地数据库
     * - 首次登录：创建用户
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
            // 首次登录：创建用户
            user = new SysUser();
            user.setKeycloakId(keycloakId);
            user.setUsername(username != null ? username : keycloakId);
            user.setDisplayName(displayName != null ? displayName : username);
            user.setEmail(email);
            user.setStatus("active");
            user.setLastLoginAt(LocalDateTime.now());
            userMapper.insert(user);

            // 内部系统：自动分配系统管理员角色，确保新用户有完整操作权限
            UserRole userRole = new UserRole();
            userRole.setUserId(user.getId());
            userRole.setRoleId(DEFAULT_ROLE_ID);
            userRoleMapper.insert(userRole);

            log.info("New user synced from Keycloak: {} ({}), assigned system_admin role", username, keycloakId);
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
}
