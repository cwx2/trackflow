package com.trackflow.common.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 通知助手公共基类：提供跨模块共享的查询辅助方法。
 * <p>
 * 三个 NotificationHelper（Issue / Sprint / Project）共享以下功能：
 * - 获取用户显示名称（getUserDisplayName）
 * - 获取项目成员列表（getProjectMemberUserIds）
 * <p>
 * 子类通过构造器注入将 Mapper 传给基类。
 */
@Slf4j
public abstract class AbstractNotificationHelper {

    protected final SysUserMapper sysUserMapper;
    protected final ProjectMemberMapper projectMemberMapper;

    protected AbstractNotificationHelper(SysUserMapper sysUserMapper, ProjectMemberMapper projectMemberMapper) {
        this.sysUserMapper = sysUserMapper;
        this.projectMemberMapper = projectMemberMapper;
    }

    /**
     * 获取用户显示名称。
     * <p>
     * 注意：此方法每次调用都会查询 DB。在同一方法中多次需要同一用户名时，
     * 调用方应将结果缓存到局部变量中。
     *
     * @throws IllegalStateException 如果子类未提供 SysUserMapper
     */
    protected String getUserDisplayName(Long userId) {
        if (userId == null) {
            return "系统";
        }
        if (sysUserMapper == null) {
            throw new IllegalStateException("SysUserMapper not available in this helper");
        }
        try {
            SysUser user = sysUserMapper.selectById(userId);
            return user != null && user.getDisplayName() != null ? user.getDisplayName() : String.valueOf(userId);
        } catch (Exception e) {
            log.warn("[Notification] 查询用户显示名称失败: userId={}", userId);
            return String.valueOf(userId);
        }
    }

    /**
     * 获取项目所有成员的 userId 列表（去重）。
     *
     * @throws IllegalStateException 如果子类未提供 ProjectMemberMapper
     */
    protected List<Long> getProjectMemberUserIds(Long projectId) {
        if (projectMemberMapper == null) {
            throw new IllegalStateException("ProjectMemberMapper not available in this helper");
        }
        List<ProjectMember> members = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .select(ProjectMember::getUserId)
        );
        return members.stream()
                .map(ProjectMember::getUserId)
                .distinct()
                .toList();
    }
}
