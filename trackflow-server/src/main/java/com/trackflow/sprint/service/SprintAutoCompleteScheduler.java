package com.trackflow.sprint.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.annotation.DistributedLock;
import com.trackflow.common.event.ReportCacheInvalidationEvent;
import com.trackflow.common.event.SprintNotificationEvent;
import com.trackflow.issue.entity.Issue;
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

/**
 * Sprint 到期自动完成调度器。
 * <p>
 * 对标 YouTrack 行为：Sprint 到达结束日期后自动完成，无需用户操作。
 * "A sprint automatically completes on the end date configuration at 23:59:59 server time."
 * <p>
 * 自动完成时不移除未关闭工单——工单保留在已完成 Sprint 中，直到用户创建新 Sprint 时主动选择迁移。
 * 这与手动完成（SprintService.complete()）不同，后者要求用户选择未完成工单的处理方式。
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
    @DistributedLock(key = "sprint_auto_complete")
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
     * 自动完成单个 Sprint（对标 YouTrack 行为）：
     * <p>
     * 与手动完成不同，自动完成时不移走未关闭工单。工单保留在该 Sprint 中，
     * 直到用户创建新 Sprint 时主动选择"迁移未完成工单"。
     * <p>
     * 流程：
     * 1. 统计未完成工单数量（用于通知和日志）
     * 2. 更新 Sprint 状态为 COMPLETED
     * 3. 记录项目活动日志
     * 4. 发布通知事件（含未完成工单数）
     */
    @Transactional(rollbackFor = Exception.class)
    public void autoCompleteSingleSprint(Sprint sprint) {
        Long sprintId = sprint.getId();
        Long projectId = sprint.getProjectId();

        log.info("[SprintAutoComplete] 自动完成 Sprint: id={}, name={}, endDate={}",
                sprintId, sprint.getName(), sprint.getEndDate());

        // 统计未关闭工单数量（仅用于通知和日志，不移除工单）
        List<Long> openIssueIds = sprintMapper.selectOpenIssueIds(sprintId);
        int unresolvedCount = openIssueIds.size();
        LocalDateTime now = LocalDateTime.now();

        if (unresolvedCount > 0) {
            log.info("[SprintAutoComplete] Sprint '{}' 仍有 {} 个未完成工单，保留在该迭代中",
                    sprint.getName(), unresolvedCount);
        }

        // 完成 Sprint（不移除未关闭工单，对标 YouTrack 行为）
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
        if (unresolvedCount > 0) {
            detail.put("unresolved_issues_count", unresolvedCount);
            detail.put("move_option", "retained"); // 未完成工单保留在 Sprint 中
        }
        projectActivityService.log(projectId, null, "auto_complete_sprint", null, detail);

        // 通知项目成员 Sprint 已自动完成
        // 计算已完成工单数 = 总工单数 - 未关闭工单数
        long totalIssuesInSprint = issueMapper.selectCount(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getSprintId, sprintId)
                        .isNull(Issue::getDeletedAt)
        );
        int completedIssues = (int) (totalIssuesInSprint - unresolvedCount);
        eventPublisher.publishEvent(new SprintNotificationEvent.Completed(sprint, Math.max(completedIssues, 0), null));

        // 失效 Dashboard 缓存
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(projectId, "sprint_auto_completed"));
    }
}
