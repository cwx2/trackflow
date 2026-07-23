package com.trackflow.sprint.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.trackflow.common.event.ReportCacheInvalidationEvent;
import com.trackflow.common.event.SprintNotificationEvent;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.project.service.ProjectActivityService;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.entity.SprintStatus;
import com.trackflow.sprint.mapper.SprintMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sprint 到期自动完成调度器。
 * <p>
 * 对标 YouTrack 行为：Sprint 到达结束日期后自动完成，无需用户操作。
 * "A sprint automatically completes on the end date configuration at 23:59:59 server time."
 * <p>
 * 未完成工单自动移入 Backlog（sprint_id = null）。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SprintAutoCompleteScheduler {

    private final SprintMapper sprintMapper;
    private final IssueMapper issueMapper;
    private final ProjectActivityService projectActivityService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 每日 23:59:00 检查并自动完成已过期的 active Sprint。
     * <p>
     * 选择 23:59 而非 23:59:59 是为了确保在当天日期结束前完成处理。
     * 判断条件：status = ACTIVE 且 endDate <= 当天。
     */
    @Scheduled(cron = "0 59 23 * * ?")
    public void autoCompleteExpiredSprints() {
        log.info("[SprintAutoComplete] 开始检查到期 Sprint...");

        LocalDate today = LocalDate.now();

        List<Sprint> expiredSprints = sprintMapper.selectList(
                new LambdaQueryWrapper<Sprint>()
                        .eq(Sprint::getStatus, SprintStatus.ACTIVE)
                        .le(Sprint::getEndDate, today)
        );

        if (expiredSprints.isEmpty()) {
            log.info("[SprintAutoComplete] 无到期 Sprint 需要自动完成");
            return;
        }

        int successCount = 0;
        int failedCount = 0;

        for (Sprint sprint : expiredSprints) {
            try {
                autoCompleteSingleSprint(sprint);
                successCount++;
            } catch (Exception e) {
                failedCount++;
                log.error("[SprintAutoComplete] Sprint 自动完成失败: id={}, name={}, projectId={}",
                        sprint.getId(), sprint.getName(), sprint.getProjectId(), e);
            }
        }

        log.info("[SprintAutoComplete] 自动完成处理结束: 成功 {} 个, 失败 {} 个", successCount, failedCount);
    }

    /**
     * 自动完成单个 Sprint：
     * 1. 将未完成工单移入 Backlog
     * 2. 更新 Sprint 状态为 COMPLETED
     * 3. 记录项目活动日志
     * 4. 发布通知事件
     */
    @Transactional(rollbackFor = Exception.class)
    public void autoCompleteSingleSprint(Sprint sprint) {
        Long sprintId = sprint.getId();
        Long projectId = sprint.getProjectId();

        log.info("[SprintAutoComplete] 自动完成 Sprint: id={}, name={}, endDate={}",
                sprintId, sprint.getName(), sprint.getEndDate());

        // 查找未关闭工单
        List<Long> openIssueIds = sprintMapper.selectOpenIssueIds(sprintId);
        LocalDateTime now = LocalDateTime.now();

        // 未完成工单移入 Backlog（sprint_id = null）
        if (!openIssueIds.isEmpty()) {
            issueMapper.update(null,
                    new LambdaUpdateWrapper<Issue>()
                            .in(Issue::getId, openIssueIds)
                            .set(Issue::getSprintId, null)
                            .set(Issue::getUpdatedBy, null)
                            .set(Issue::getUpdatedAt, now)
            );

            // 批量记录活动日志：sprint 字段变更
            String oldSprintIdStr = String.valueOf(sprintId);
            String oldSprintName = sprint.getName();
            List<IssueActivity> activities = openIssueIds.stream().map(issueId -> {
                IssueActivity activity = new IssueActivity();
                activity.setIssueId(issueId);
                activity.setUserId(null); // 系统自动操作，无用户
                activity.setAction("updated");
                activity.setFieldName("sprint");
                activity.setOldValue(oldSprintIdStr);
                activity.setNewValue(null);
                activity.setOldDisplayValue(oldSprintName);
                activity.setNewDisplayValue(null);
                activity.setCreatedAt(now);
                return activity;
            }).toList();
            Db.saveBatch(activities);

            log.info("[SprintAutoComplete] 已将 {} 个未完成工单移入 Backlog", openIssueIds.size());
        }

        // 完成 Sprint
        sprint.setStatus(SprintStatus.COMPLETED);
        sprint.setUpdatedAt(now);
        sprint.setUpdatedBy(null); // 系统自动操作
        sprintMapper.updateById(sprint);

        // 记录项目活动日志
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sprint_id", sprintId);
        detail.put("sprint_name", sprint.getName());
        detail.put("auto_completed", true);
        detail.put("end_date", sprint.getEndDate().toString());
        if (!openIssueIds.isEmpty()) {
            detail.put("unresolved_issues_count", openIssueIds.size());
            detail.put("move_option", "backlog");
        }
        projectActivityService.log(projectId, null, "auto_complete_sprint", null, detail);

        // 通知项目成员 Sprint 已自动完成
        long totalIssuesInSprint = issueMapper.selectCount(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getSprintId, sprintId)
                        .isNull(Issue::getDeletedAt)
        );
        // 注意：此时未完成工单已移出，totalIssuesInSprint 只包含已关闭工单
        int completedIssues = (int) totalIssuesInSprint;
        eventPublisher.publishEvent(new SprintNotificationEvent.Completed(sprint, Math.max(completedIssues, 0), null));

        // 失效 Dashboard 缓存
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(projectId, "sprint_auto_completed"));
    }
}
