package com.trackflow.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.model.PageResult;
import com.trackflow.system.dto.UpdateMyProfileDTO;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.vo.UserDataExportVO;
import com.trackflow.system.vo.UserProfileVO;
import com.trackflow.system.vo.UserPublicProfileVO;
import com.trackflow.system.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户 VO 组装器 — 负责将 UserService 的数据查询结果组装为 VO。
 * <p>
 * 将原 UserService 中直接返回 VO 的方法代理至此类，遵循分层规范：
 * - UserService 负责业务逻辑（CRUD、认证、权限）
 * - UserVOAssembler 负责 VO 展示数据的组装
 * <p>
 * Controller 层注入此类来获取 VO，而非直接调用 Service 的 VO 方法。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
public class UserVOAssembler {

    private final UserService userService;

    /**
     * 分页查询用户列表（含全局角色信息）
     */
    public PageResult<UserVO> listUsersWithRoles(Page<SysUser> page, String keyword, String username,
                                                  String displayName, String email, Long orgId,
                                                  String status, String banStatus, Long roleId) {
        return userService.assembleUsersWithRoles(page, keyword, username, displayName, email, orgId, status, banStatus, roleId);
    }

    /**
     * 获取用户完整档案
     */
    public UserProfileVO getUserProfile(Long userId) {
        return userService.assembleUserProfile(userId);
    }

    /**
     * 获取用户公开资料（权限分级）
     */
    public UserPublicProfileVO getUserPublicProfile(Long targetUserId, Long requesterId) {
        return userService.assembleUserPublicProfile(targetUserId, requesterId);
    }

    /**
     * 更新当前用户个人资料并返回更新后的档案
     */
    public UserProfileVO updateMyProfile(Long userId, UpdateMyProfileDTO dto) {
        return userService.assembleUpdateMyProfile(userId, dto);
    }

    /**
     * 导出用户所有个人数据
     */
    public UserDataExportVO exportUserData(Long userId) {
        return userService.assembleExportUserData(userId);
    }
}
