package com.trackflow.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.integration.entity.MutedThread;
import com.trackflow.integration.mapper.MutedThreadMapper;
import com.trackflow.integration.vo.MutedThreadVO;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 通知线程静音服务。
 * <p>
 * 提供静音/取消静音/查询功能。
 * 被 NotificationService 在创建通知时调用以判断是否跳过。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MutedThreadService {

    private final MutedThreadMapper mutedThreadMapper;
    private final IssueMapper issueMapper;

    /**
     * 静音指定资源的通知
     */
    @Transactional(rollbackFor = Exception.class)
        public void mute(Long userId, String resourceType, Long resourceId) {
        // 幂等：已存在则不重复插入
        if (isMuted(userId, resourceType, resourceId)) {
            return;
        }
        MutedThread mt = new MutedThread();
        mt.setUserId(userId);
        mt.setResourceType(resourceType);
        mt.setResourceId(resourceId);
        mt.setCreatedAt(LocalDateTime.now());
        mutedThreadMapper.insert(mt);
        log.info("[MutedThread] 用户 {} 静音 {}:{}", userId, resourceType, resourceId);
    }

    /**
     * 取消静音
     */
    @Transactional(rollbackFor = Exception.class)
        public void unmute(Long userId, String resourceType, Long resourceId) {
        mutedThreadMapper.delete(
                new LambdaQueryWrapper<MutedThread>()
                        .eq(MutedThread::getUserId, userId)
                        .eq(MutedThread::getResourceType, resourceType)
                        .eq(MutedThread::getResourceId, resourceId)
        );
        log.info("[MutedThread] 用户 {} 取消静音 {}:{}", userId, resourceType, resourceId);
    }

    /**
     * 检查用户是否已静音指定资源
     */
    public boolean isMuted(Long userId, String resourceType, Long resourceId) {
        if (resourceId == null) {
            return false;
        }
        return mutedThreadMapper.selectCount(
                new LambdaQueryWrapper<MutedThread>()
                        .eq(MutedThread::getUserId, userId)
                        .eq(MutedThread::getResourceType, resourceType)
                        .eq(MutedThread::getResourceId, resourceId)
        ) > 0;
    }

    /**
     * 批量检查哪些用户对指定资源已静音（批量通知发送场景）。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     * @param userIds      待检查的用户 ID 集合
     * @return 已对该资源静音的用户 ID 集合
     */
    public Set<Long> getMutedUserIds(String resourceType, Long resourceId, Collection<Long> userIds) {
        if (resourceId == null || userIds == null || userIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<MutedThread> muted = mutedThreadMapper.selectList(
                new LambdaQueryWrapper<MutedThread>()
                        .eq(MutedThread::getResourceType, resourceType)
                        .eq(MutedThread::getResourceId, resourceId)
                        .in(MutedThread::getUserId, userIds)
        );
        return muted.stream()
                .map(MutedThread::getUserId)
                .collect(Collectors.toSet());
    }

    /**
     * 批量检查静音状态（给前端通知列表用，避免 N+1）
     *
     * @return 已静音的 resourceId 集合
     */
    public Set<Long> getMutedResourceIds(Long userId, String resourceType, Collection<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<MutedThread> muted = mutedThreadMapper.selectList(
                new LambdaQueryWrapper<MutedThread>()
                        .eq(MutedThread::getUserId, userId)
                        .eq(MutedThread::getResourceType, resourceType)
                        .in(MutedThread::getResourceId, resourceIds)
        );
        return muted.stream()
                .map(MutedThread::getResourceId)
                .collect(Collectors.toSet());
    }

    /**
     * 删除指定资源的所有静音记录（资源被删除时调用）。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     * @return 删除的记录数
     */
    @Transactional(rollbackFor = Exception.class)
        public int deleteByResource(String resourceType, Long resourceId) {
        if (resourceId == null) {
            return 0;
        }
        long deleted = mutedThreadMapper.delete(
                new LambdaQueryWrapper<MutedThread>()
                        .eq(MutedThread::getResourceType, resourceType)
                        .eq(MutedThread::getResourceId, resourceId)
        );
        if (deleted > 0) {
            log.info("[MutedThread] 清理资源 {}:{} 的静音记录，共 {} 条", resourceType, resourceId, deleted);
        }
        return (int) deleted;
    }

    /**
     * 批量删除指定资源的所有静音记录（项目删除级联时调用）。
     *
     * @param resourceType 资源类型
     * @param resourceIds  资源 ID 集合
     * @return 删除的记录数
     */
    @Transactional(rollbackFor = Exception.class)
        public int deleteByResources(String resourceType, Collection<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return 0;
        }
        long deleted = mutedThreadMapper.delete(
                new LambdaQueryWrapper<MutedThread>()
                        .eq(MutedThread::getResourceType, resourceType)
                        .in(MutedThread::getResourceId, resourceIds)
        );
        if (deleted > 0) {
            log.info("[MutedThread] 批量清理 {} 类型资源的静音记录，共 {} 条", resourceType, deleted);
        }
        return (int) deleted;
    }

    /**
     * 清理孤立的静音记录（引用的 issue 已不存在）。
     * 由定时任务调用，作为兜底清理机制。
     *
     * @return 删除的记录数
     */
    @Transactional(rollbackFor = Exception.class)
        public int cleanupOrphanedRecords() {
        // 查询所有 resource_type='issue' 的记录
        List<MutedThread> issueThreads = mutedThreadMapper.selectList(
                new LambdaQueryWrapper<MutedThread>()
                        .eq(MutedThread::getResourceType, "issue")
        );
        if (issueThreads.isEmpty()) {
            return 0;
        }

        // 批量检查哪些 issue 仍然存在（包括已软删除的也视为不存在）
        Set<Long> allResourceIds = issueThreads.stream()
                .map(MutedThread::getResourceId)
                .collect(Collectors.toSet());

        Set<Long> existingIds;
        if (!allResourceIds.isEmpty()) {
            List<Issue> existingIssues = issueMapper.selectBatchIds(allResourceIds);
            existingIds = existingIssues.stream()
                    .map(Issue::getId)
                    .collect(Collectors.toSet());
        } else {
            existingIds = Collections.emptySet();
        }

        // 找出孤立记录的 ID
        List<Long> orphanIds = issueThreads.stream()
                .filter(mt -> !existingIds.contains(mt.getResourceId()))
                .map(MutedThread::getId)
                .toList();

        if (orphanIds.isEmpty()) {
            return 0;
        }

        // 批量删除
        mutedThreadMapper.deleteByIds(orphanIds);
        log.info("[MutedThread] 清理孤立静音记录 {} 条", orphanIds.size());
        return orphanIds.size();
    }

    /**
     * 获取用户所有已静音的线程列表（用于设置页面展示）。
     * 自动过滤已删除资源的条目。
     */
    public List<MutedThreadVO> listMutedThreads(Long userId) {
        List<MutedThread> threads = mutedThreadMapper.selectList(
                new LambdaQueryWrapper<MutedThread>()
                        .eq(MutedThread::getUserId, userId)
                        .orderByDesc(MutedThread::getCreatedAt)
        );
        if (threads.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询 issue 标题以填充 resourceTitle
        Set<Long> issueIds = threads.stream()
                .filter(t -> "issue".equals(t.getResourceType()))
                .map(MutedThread::getResourceId)
                .collect(Collectors.toSet());

        Map<Long, String> issueTitleMap = Collections.emptyMap();
        if (!issueIds.isEmpty()) {
            List<Issue> issues = issueMapper.selectBatchIds(issueIds);
            issueTitleMap = issues.stream()
                    .collect(Collectors.toMap(Issue::getId,
                            i -> (i.getIssueKey() != null ? i.getIssueKey() + " " : "") + i.getTitle()));
        }

        // 构建 VO 列表，过滤掉已删除资源的条目（防御性编程）
        Map<Long, String> finalIssueTitleMap = issueTitleMap;
        List<MutedThreadVO> voList = new ArrayList<>();
        for (MutedThread mt : threads) {
            if ("issue".equals(mt.getResourceType())) {
                // 如果 issue 已不存在（已删除），跳过该条目
                if (!finalIssueTitleMap.containsKey(mt.getResourceId())) {
                    continue;
                }
            }

            MutedThreadVO vo = new MutedThreadVO();
            vo.setId(String.valueOf(mt.getId()));
            vo.setUserId(String.valueOf(mt.getUserId()));
            vo.setResourceType(mt.getResourceType());
            vo.setResourceId(String.valueOf(mt.getResourceId()));
            vo.setCreatedAt(mt.getCreatedAt());
            // 填充标题
            if ("issue".equals(mt.getResourceType())) {
                vo.setResourceTitle(finalIssueTitleMap.get(mt.getResourceId()));
            } else {
                vo.setResourceTitle(mt.getResourceType() + ":" + mt.getResourceId());
            }
            voList.add(vo);
        }
        return voList;
    }
}
