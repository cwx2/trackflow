package com.trackflow.issue.service;

import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueStatusMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Issue 状态缓存助手。
 * <p>
 * issue_status 表为种子数据，极少变更（仅管理员新增/编辑状态时）。
 * 提供关闭状态 ID 集合的本地缓存，避免每次查询都访问 DB。
 * <p>
 * 失效策略：手动调用 invalidate() 或自动过期（30秒 TTL，适配开发时热修改状态的场景）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatusCacheHelper {

    private final IssueStatusMapper statusMapper;

    /** 缓存：关闭状态 ID 集合 */
    private final AtomicReference<Set<Long>> closedIdsCache = new AtomicReference<>();
    /** 缓存：开放状态 ID 集合 */
    private final AtomicReference<Set<Long>> openIdsCache = new AtomicReference<>();
    /** 上次加载时间 */
    private volatile long lastLoadTime = 0;
    /** 缓存 TTL（30 秒，兼顾开发便利和性能） */
    private static final long CACHE_TTL_MS = 30_000;

    /**
     * 获取所有关闭状态（is_closed = true）的 ID 集合。
     * 结果带本地缓存，30秒自动刷新。
     */
    public Set<Long> getClosedStatusIds() {
        ensureLoaded();
        return closedIdsCache.get();
    }

    /**
     * 获取所有开放状态（is_closed = false）的 ID 集合。
     * 结果带本地缓存，30秒自动刷新。
     */
    public Set<Long> getOpenStatusIds() {
        ensureLoaded();
        return openIdsCache.get();
    }

    /**
     * 判断指定状态是否为关闭状态。
     */
    public boolean isClosedStatus(Long statusId) {
        if (statusId == null) return false;
        return getClosedStatusIds().contains(statusId);
    }

    /**
     * 判断指定状态是否为开放状态。
     */
    public boolean isOpenStatus(Long statusId) {
        if (statusId == null) return true;
        return !isClosedStatus(statusId);
    }

    /**
     * 获取状态名称（通过 DB 查询，不缓存名称——仅用于活动记录等低频场景）
     */
    public String getStatusName(Long statusId) {
        if (statusId == null) return null;
        IssueStatus status = statusMapper.selectById(statusId);
        return status != null ? status.getName() : null;
    }

    /**
     * 手动失效缓存（状态表发生变更时调用）。
     */
    public void invalidate() {
        closedIdsCache.set(null);
        openIdsCache.set(null);
        lastLoadTime = 0;
        log.debug("StatusCacheHelper cache invalidated");
    }

    private void ensureLoaded() {
        if (closedIdsCache.get() != null && (System.currentTimeMillis() - lastLoadTime) < CACHE_TTL_MS) {
            return;
        }
        synchronized (this) {
            // 双重检查
            if (closedIdsCache.get() != null && (System.currentTimeMillis() - lastLoadTime) < CACHE_TTL_MS) {
                return;
            }
            loadFromDb();
        }
    }

    private void loadFromDb() {
        List<IssueStatus> allStatuses = statusMapper.selectList(null);

        Set<Long> closed = allStatuses.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsClosed()))
                .map(IssueStatus::getId)
                .collect(Collectors.toUnmodifiableSet());

        Set<Long> open = allStatuses.stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsClosed()))
                .map(IssueStatus::getId)
                .collect(Collectors.toUnmodifiableSet());

        closedIdsCache.set(closed);
        openIdsCache.set(open);
        lastLoadTime = System.currentTimeMillis();
        log.debug("StatusCacheHelper loaded: {} closed, {} open statuses", closed.size(), open.size());
    }
}
