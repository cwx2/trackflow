package com.trackflow.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.project.entity.ProjectActivity;
import com.trackflow.project.mapper.ProjectActivityMapper;
import com.trackflow.project.vo.ProjectActivityVO;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 项目活动日志服务
 */
@Service
@RequiredArgsConstructor
public class ProjectActivityService {

    private final ProjectActivityMapper activityMapper;
    private final SysUserMapper userMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * 记录项目活动
     *
     * @param detailMap 扩展信息 Map，内部序列化为 JSON（避免手动拼接 JSON 字符串）
     */
    public void log(Long projectId, Long userId, String action, Long targetUserId, Map<String, Object> detailMap) {
        ProjectActivity activity = new ProjectActivity();
        activity.setProjectId(projectId);
        activity.setUserId(userId);
        activity.setAction(action);
        activity.setTargetUserId(targetUserId);
        if (detailMap != null && !detailMap.isEmpty()) {
            try {
                activity.setDetail(objectMapper.writeValueAsString(detailMap));
            } catch (Exception e) {
                // fallback: 不影响主流程
                activity.setDetail(null);
            }
        }
        activity.setCreatedAt(LocalDateTime.now());
        activityMapper.insert(activity);
    }

    /**
     * 查询项目活动日志（分页、按时间倒序）
     */
    public Page<ProjectActivityVO> listByProject(Long projectId, Page<ProjectActivity> page) {
        LambdaQueryWrapper<ProjectActivity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProjectActivity::getProjectId, projectId)
               .orderByDesc(ProjectActivity::getCreatedAt);

        Page<ProjectActivity> result = activityMapper.selectPage(page, wrapper);

        // 收集所有涉及的用户 ID 用于批量查询
        Set<Long> userIds = result.getRecords().stream()
                .flatMap(a -> {
                    var ids = new java.util.ArrayList<Long>();
                    if (a.getUserId() != null) ids.add(a.getUserId());
                    if (a.getTargetUserId() != null) ids.add(a.getTargetUserId());
                    return ids.stream();
                })
                .collect(Collectors.toSet());

        Map<Long, String> userNameMap = Map.of();
        if (!userIds.isEmpty()) {
            userNameMap = userMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(SysUser::getId, u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername()));
        }

        // 转换为 VO
        Map<Long, String> finalUserNameMap = userNameMap;
        List<ProjectActivityVO> voList = result.getRecords().stream().map(a -> {
            ProjectActivityVO vo = new ProjectActivityVO();
            vo.setId(a.getId() != null ? a.getId().toString() : null);
            vo.setProjectId(a.getProjectId() != null ? a.getProjectId().toString() : null);
            vo.setUserId(a.getUserId() != null ? a.getUserId().toString() : null);
            vo.setAction(a.getAction());
            vo.setTargetUserId(a.getTargetUserId() != null ? a.getTargetUserId().toString() : null);
            vo.setDetail(a.getDetail());
            vo.setCreatedAt(a.getCreatedAt());
            vo.setUserName(finalUserNameMap.getOrDefault(a.getUserId(), ""));
            vo.setTargetUserName(finalUserNameMap.getOrDefault(a.getTargetUserId(), ""));
            return vo;
        }).toList();

        // 构建分页 VO 结果
        Page<ProjectActivityVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }
}
