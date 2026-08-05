package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.automation.execution.AutomationActorRunner;
import com.trackflow.common.model.PageResult;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.result.ActivityRow;
import com.trackflow.issue.vo.IssueActivityVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单活动记录服务 - 负责记录和查询工单的所有操作历史
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueActivityService {

    private final IssueActivityMapper activityMapper;
    private final IssueMapper issueMapper;

    /**
     * 记录活动（简化版，无显示值）
     */
    public void recordActivity(Long issueId, Long userId, String action,
                               String fieldName, String oldValue, String newValue) {
        recordActivity(issueId, userId, action, fieldName, oldValue, newValue, null, null);
    }

    /**
     * 记录活动（完整版，含显示值）
     */
    public void recordActivity(Long issueId, Long userId, String action,
                               String fieldName, String oldValue, String newValue,
                               String oldDisplayValue, String newDisplayValue) {
        // Skip no-op changes: if both old and new values are present and identical, don't record
        if (oldValue != null && newValue != null && oldValue.equals(newValue)
                && "updated".equals(action)) {
            return;
        }
        IssueActivity activity = new IssueActivity();
        activity.setIssueId(issueId);
        activity.setUserId(userId);
        activity.setAction(action);
        activity.setFieldName(fieldName);
        activity.setOldValue(oldValue);
        activity.setNewValue(newValue);
        activity.setOldDisplayValue(oldDisplayValue);
        activity.setNewDisplayValue(newDisplayValue);
        if (AutomationActorRunner.isAutomationExecution()) {
            activity.setSource("automation");
        }
        activity.setCreatedAt(LocalDateTime.now());
        activityMapper.insert(activity);
    }

    /**
     * 查询活动列表（简单查询）
     */
    public List<IssueActivity> listActivities(Long issueId) {
        return activityMapper.selectList(
                new LambdaQueryWrapper<IssueActivity>()
                        .eq(IssueActivity::getIssueId, issueId)
                        .orderByDesc(IssueActivity::getCreatedAt)
        );
    }

    /**
     * 获取活动列表 —— 单次 JOIN 查询（消除 N+1）
     * assignee 字段的 old/new value 在 SQL 层自动解析为用户显示名
     */
    @Transactional(readOnly = true)
    public List<IssueActivityVO> listActivitiesWithUser(Long issueId) {
        List<ActivityRow> rows = issueMapper.selectActivitiesWithUser(issueId);
        return rows.stream().map(this::mapActivityRow).toList();
    }

    /**
     * 获取活动列表（分页） —— 按时间倒序，支持加载更多
     */
    @Transactional(readOnly = true)
    public PageResult<IssueActivityVO> listActivitiesWithUserPaged(Long issueId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<ActivityRow> rows = issueMapper.selectActivitiesWithUserPaged(issueId, offset, pageSize);
        long total = issueMapper.countActivities(issueId);
        List<IssueActivityVO> voList = rows.stream().map(this::mapActivityRow).toList();
        return new PageResult<>(voList, total, page, pageSize);
    }

    /**
     * 获取指定工单最近一次状态变更的活动记录
     */
    public IssueActivity getLastStatusChange(Long issueId) {
        return activityMapper.selectLastStatusChange(issueId);
    }

    private IssueActivityVO mapActivityRow(ActivityRow row) {
        IssueActivityVO vo = new IssueActivityVO();
        vo.setId(String.valueOf(row.getId()));
        vo.setIssueId(String.valueOf(row.getIssueId()));
        vo.setUserId(row.getUserId() != null ? String.valueOf(row.getUserId()) : null);
        if ("automation".equals(row.getSource())) {
            vo.setUserName(row.getUserName() != null ? row.getUserName() : "自动化规则");
        } else {
            vo.setUserName(row.getUserName());
        }
        vo.setUserAvatar(row.getUserAvatar());
        vo.setAction(row.getAction());
        vo.setFieldName(row.getFieldName());
        vo.setOldValue(row.getOldValue());
        vo.setNewValue(row.getNewValue());
        vo.setDetail(row.getDetail());
        vo.setSource(row.getSource());
        vo.setCreatedAt(row.getCreatedAt());
        return vo;
    }
}
