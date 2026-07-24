package com.trackflow.integration.converter;

import com.trackflow.integration.entity.NotificationPreference;
import com.trackflow.integration.vo.NotificationPreferenceVO;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 手动实现的 NotificationPreferenceConverter，覆盖 MapStruct 生成的旧版本。
 * <p>
 * 原因：MapStruct 注解处理器需要 Maven compile 才能重新生成实现类。
 * 在 IDEA 增量编译环境下，新增字段不会自动反映到生成的 Impl 中。
 * 此手动实现确保 onIssueUpdated 等新字段正确映射。
 * <p>
 * TODO: 当下次运行 mvn compile 后，可删除此文件——生成的版本会自动包含新字段。
 */
@Component
@Primary
public class NotificationPreferenceConverterManual implements NotificationPreferenceConverter {

    @Override
    public NotificationPreferenceVO toVO(NotificationPreference entity) {
        if (entity == null) {
            return null;
        }

        NotificationPreferenceVO vo = new NotificationPreferenceVO();
        vo.setId(longToString(entity.getId()));
        vo.setUserId(longToString(entity.getUserId()));
        vo.setProjectId(longToString(entity.getProjectId()));

        // Issue 事件
        vo.setOnIssueAssigned(entity.getOnIssueAssigned());
        vo.setOnIssueStatusChanged(entity.getOnIssueStatusChanged());
        vo.setOnIssueCommented(entity.getOnIssueCommented());
        vo.setOnMentioned(entity.getOnMentioned());
        vo.setOnIssueResolved(entity.getOnIssueResolved());
        vo.setOnIssueUpdated(entity.getOnIssueUpdated());

        // Sprint 事件
        vo.setOnSprintStarted(entity.getOnSprintStarted());
        vo.setOnSprintCompleted(entity.getOnSprintCompleted());

        // 项目事件
        vo.setOnProjectMemberChanged(entity.getOnProjectMemberChanged());
        vo.setOnProjectLifecycle(entity.getOnProjectLifecycle());

        // 日期提醒
        vo.setOnDueDate(entity.getOnDueDate());
        vo.setOnOverdue(entity.getOnOverdue());
        vo.setDueDateAdvanceDays(entity.getDueDateAdvanceDays());

        // 行为开关
        vo.setNotifyOwnChanges(entity.getNotifyOwnChanges());
        vo.setEmailEnabled(entity.getEmailEnabled());

        // Per-event 邮件渠道控制
        vo.setEmailOnIssueAssigned(entity.getEmailOnIssueAssigned());
        vo.setEmailOnIssueStatusChanged(entity.getEmailOnIssueStatusChanged());
        vo.setEmailOnIssueCommented(entity.getEmailOnIssueCommented());
        vo.setEmailOnMentioned(entity.getEmailOnMentioned());
        vo.setEmailOnIssueResolved(entity.getEmailOnIssueResolved());
        vo.setEmailOnIssueUpdated(entity.getEmailOnIssueUpdated());
        vo.setEmailOnSprintStarted(entity.getEmailOnSprintStarted());
        vo.setEmailOnSprintCompleted(entity.getEmailOnSprintCompleted());
        vo.setEmailOnProjectMemberChanged(entity.getEmailOnProjectMemberChanged());
        vo.setEmailOnProjectLifecycle(entity.getEmailOnProjectLifecycle());
        vo.setEmailOnDueDate(entity.getEmailOnDueDate());
        vo.setEmailOnOverdue(entity.getEmailOnOverdue());
        vo.setEmailOnWatchedUpdated(entity.getEmailOnWatchedUpdated());

        // 静音时段
        vo.setQuietHoursStart(entity.getQuietHoursStart());
        vo.setQuietHoursEnd(entity.getQuietHoursEnd());

        // Watched 通知开关
        vo.setOnWatchedUpdated(entity.getOnWatchedUpdated());

        // Vote + Spent Time 事件开关
        vo.setOnIssueVoted(entity.getOnIssueVoted());
        vo.setEmailOnIssueVoted(entity.getEmailOnIssueVoted());
        vo.setOnIssueSpentTime(entity.getOnIssueSpentTime());
        vo.setEmailOnIssueSpentTime(entity.getEmailOnIssueSpentTime());

        // 自动关注行为配置
        vo.setAutoWatchOnCreate(entity.getAutoWatchOnCreate());
        vo.setAutoWatchOnComment(entity.getAutoWatchOnComment());
        vo.setAutoWatchOnUpdate(entity.getAutoWatchOnUpdate());
        vo.setAutoWatchOnAssign(entity.getAutoWatchOnAssign());

        return vo;
    }

    @Override
    public List<NotificationPreferenceVO> toVOList(List<NotificationPreference> entities) {
        if (entities == null) {
            return null;
        }
        List<NotificationPreferenceVO> list = new ArrayList<>(entities.size());
        for (NotificationPreference pref : entities) {
            list.add(toVO(pref));
        }
        return list;
    }
}
