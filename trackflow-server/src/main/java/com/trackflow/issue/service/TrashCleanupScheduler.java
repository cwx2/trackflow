package com.trackflow.issue.service;

import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectStatus;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.service.ProjectService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.issue.mapper.IssueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 回收站自动清理定时任务。
 * <p>
 * 每天凌晨 3:00 执行，按项目维度清理超过保留期限的已删除工单。
 * 保留策略读取自 project.settings JSON 的 trashRetentionDays 字段：
 * - 正整数：保留对应天数后自动永久删除
 * - 0 或 "forever"：永不自动清理（永久保留已删除工单）
 * - 未设置：使用系统默认值 30 天
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrashCleanupScheduler {

    private final ProjectMapper projectMapper;
    private final ProjectService projectService;
    private final IssueMapper issueMapper;
    private final IssueService issueService;

    /**
     * 每天凌晨 3:00 执行清理
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredTrash() {
        log.info("[TrashCleanup] 开始执行回收站自动清理...");
        int totalCleaned = 0;

        // 获取所有活跃项目
        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>().eq(Project::getStatus, ProjectStatus.ACTIVE)
        );

        for (Project project : projects) {
            int retentionDays = projectService.parseRetentionDays(project.getSettings());
            if (retentionDays <= 0) {
                // 永久保留，跳过此项目
                continue;
            }

            LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
            List<Map<String, Object>> expiredIssues = issueMapper.selectExpiredTrash(project.getId(), cutoff);

            for (Map<String, Object> row : expiredIssues) {
                Long issueId = ((Number) row.get("id")).longValue();
                try {
                    issueService.permanentDelete(issueId);
                    totalCleaned++;
                } catch (Exception e) {
                    log.warn("[TrashCleanup] 清理工单 id={} 失败", issueId, e);
                }
            }
        }

        log.info("[TrashCleanup] 清理完成，共永久删除 {} 个过期工单", totalCleaned);
    }
}
