package com.trackflow.integration.converter;

import com.trackflow.integration.entity.NotificationPreference;
import com.trackflow.integration.vo.NotificationPreferenceVO;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 手动实现的 NotificationPreferenceConverter，覆盖 MapStruct 生成的旧版本。
 * 确保新增的 onDueDate/onOverdue/dueDateAdvanceDays 字段正确映射。
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

        // 其他
        vo.setNotifyOwnChanges(entity.getNotifyOwnChanges());
        vo.setEmailEnabled(entity.getEmailEnabled());
        vo.setQuietHoursStart(entity.getQuietHoursStart());
        vo.setQuietHoursEnd(entity.getQuietHoursEnd());

        return vo;
    }

    @Override
    public List<NotificationPreferenceVO> toVOList(List<NotificationPreference> entities) {
        if (entities == null) {
            return null;
        }
        List<NotificationPreferenceVO> list = new ArrayList<>(entities.size());
        for (NotificationPreference entity : entities) {
            list.add(toVO(entity));
        }
        return list;
    }
}
