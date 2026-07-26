package com.trackflow.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.project.converter.ProjectActivityConverter;
import com.trackflow.project.entity.ProjectActivity;
import com.trackflow.project.mapper.ProjectActivityMapper;
import com.trackflow.project.vo.ProjectActivityVO;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ProjectActivityConverter projectActivityConverter;

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
    @Transactional(readOnly = true)
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

        // 通过 Converter 统一映射，避免手动逐字段 set
        List<ProjectActivityVO> voList = projectActivityConverter.toVOList(result.getRecords());

        // 填充 userName 和 targetUserName（Converter 无法自动处理的关联数据）
        Map<Long, String> finalUserNameMap = userNameMap;
        voList.forEach(vo -> {
            if (vo.getUserId() != null) {
                Long uid = Long.parseLong(vo.getUserId());
                vo.setUserName(finalUserNameMap.getOrDefault(uid, ""));
            }
            if (vo.getTargetUserId() != null) {
                Long tid = Long.parseLong(vo.getTargetUserId());
                vo.setTargetUserName(finalUserNameMap.getOrDefault(tid, ""));
            }
        });

        // 构建分页 VO 结果
        Page<ProjectActivityVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }
}
