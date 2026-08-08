package com.trackflow.project.service;

import com.trackflow.project.dto.UpdateTimeTrackingSettingsDTO;
import com.trackflow.project.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 项目 VO 组装器 — 负责将 ProjectService 的数据查询结果组装为 VO。
 * <p>
 * 将原 ProjectService 中直接返回 VO 的方法代理至此类，遵循分层规范：
 * - ProjectService 负责业务逻辑（CRUD、成员管理、权限校验）
 * - ProjectVOAssembler 负责 VO 展示数据的组装
 * <p>
 * Controller 层注入此类来获取 VO，而非直接调用 Service 的 VO 方法。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
public class ProjectVOAssembler {

    private final ProjectService projectService;

    /**
     * 获取项目详情（含当前用户角色和成员统计）
     */
    public ProjectDetailVO getProjectDetail(Long projectId, Long currentUserId) {
        return projectService.assembleProjectDetail(projectId, currentUserId);
    }

    /**
     * 获取项目回收站保留策略
     */
    public ProjectTrashSettingsVO getTrashSettings(Long projectId) {
        return projectService.assembleTrashSettings(projectId);
    }

    /**
     * 获取项目成员列表（包含用户信息和角色）
     */
    public List<ProjectMemberVO> listMembers(Long projectId) {
        return projectService.assembleMembers(projectId);
    }

    /**
     * 按关键词搜索项目成员
     */
    public List<ProjectMemberVO> searchMembers(Long projectId, String keyword, int limit) {
        return projectService.assembleSearchMembers(projectId, keyword, limit);
    }

    /**
     * 获取项目中通过用户组获得访问权的组成员列表
     */
    public List<ProjectGroupMemberVO> listGroupMembers(Long projectId) {
        return projectService.assembleGroupMembers(projectId);
    }

    /**
     * 获取项目成员完整视图（含直接成员 + 组成员）
     */
    public ProjectMembersViewVO listMembersFullView(Long projectId) {
        return projectService.assembleMembersFullView(projectId);
    }

    /**
     * 获取可分配工单的成员列表
     */
    public List<ProjectMemberVO> listAssignableMembers(Long projectId) {
        return projectService.assembleAssignableMembers(projectId);
    }

    /**
     * 删除前预检查
     */
    public ProjectDeletePreCheckVO preCheckDelete(Long projectId) {
        return projectService.assembleDeletePreCheck(projectId);
    }

    /**
     * 获取项目概览统计数据
     */
    public ProjectStatisticsVO getProjectStatistics(Long projectId) {
        return projectService.assembleStatistics(projectId);
    }

    /**
     * 获取项目时间追踪设置
     */
    public ProjectTimeTrackingSettingsVO getTimeTrackingSettings(Long projectId) {
        return projectService.assembleTimeTrackingSettings(projectId);
    }

    /**
     * 更新项目时间追踪设置并返回最新值
     */
    public ProjectTimeTrackingSettingsVO updateTimeTrackingSettings(Long projectId, UpdateTimeTrackingSettingsDTO dto) {
        return projectService.assembleUpdateTimeTrackingSettings(projectId, dto);
    }

    /**
     * 获取禁用时间追踪的影响评估
     */
    public TimeTrackingDisableImpactVO getTimeTrackingDisableImpact(Long projectId) {
        return projectService.assembleTimeTrackingDisableImpact(projectId);
    }
}
