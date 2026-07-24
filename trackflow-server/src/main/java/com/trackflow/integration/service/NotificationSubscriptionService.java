package com.trackflow.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.integration.dto.CreateSubscriptionDTO;
import com.trackflow.integration.dto.SubscriptionEventsDTO;
import com.trackflow.integration.dto.UpdateSubscriptionEventsDTO;
import com.trackflow.integration.entity.NotificationSubscription;
import com.trackflow.integration.mapper.NotificationSubscriptionMapper;
import com.trackflow.integration.vo.NotificationSubscriptionVO;
import com.trackflow.integration.vo.SubscriptionEventsVO;
import com.trackflow.issue.entity.IssueTag;
import com.trackflow.issue.mapper.IssueTagMapper;
import com.trackflow.project.entity.Project;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.query.entity.SavedQuery;
import com.trackflow.query.mapper.SavedQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 通知订阅管理服务。
 * 提供订阅 CRUD 和基于订阅规则的接收者匹配。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSubscriptionService {

    private final NotificationSubscriptionMapper subscriptionMapper;
    private final IssueTagMapper tagMapper;
    private final SavedQueryMapper savedQueryMapper;
    private final ProjectMapper projectMapper;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ==================== 默认订阅初始化 ====================

    /**
     * 确保用户有默认订阅（首次访问时懒加载创建）。
     * 默认订阅：assigned_to_me / reported_by_me
     */
    public void ensureDefaultSubscriptions(Long userId) {
        long count = subscriptionMapper.selectCount(
                new LambdaQueryWrapper<NotificationSubscription>()
                        .eq(NotificationSubscription::getUserId, userId)
                        .eq(NotificationSubscription::getSourceType, "builtin"));
        if (count > 0) {
            return; // 已有默认订阅
        }

        LocalDateTime now = LocalDateTime.now();
        String defaultEvents = buildDefaultEventsJson();

        // 分配给我
        NotificationSubscription assigned = new NotificationSubscription();
        assigned.setUserId(userId);
        assigned.setName("分配给我");
        assigned.setSourceType("builtin");
        assigned.setBuiltinKey("assigned_to_me");
        assigned.setIsDefault(true);
        assigned.setEvents(defaultEvents);
        assigned.setCreatedAt(now);
        assigned.setUpdatedAt(now);
        subscriptionMapper.insert(assigned);

        // 我报告的
        NotificationSubscription reported = new NotificationSubscription();
        reported.setUserId(userId);
        reported.setName("我报告的");
        reported.setSourceType("builtin");
        reported.setBuiltinKey("reported_by_me");
        reported.setIsDefault(true);
        reported.setEvents(defaultEvents);
        reported.setCreatedAt(now);
        reported.setUpdatedAt(now);
        subscriptionMapper.insert(reported);

        log.debug("[Subscription] 为用户 {} 创建了默认订阅", userId);
    }

    // ==================== CRUD ====================

    /**
     * 获取用户所有订阅
     */
    public List<NotificationSubscriptionVO> listByUser(Long userId) {
        ensureDefaultSubscriptions(userId);

        List<NotificationSubscription> subs = subscriptionMapper.selectList(
                new LambdaQueryWrapper<NotificationSubscription>()
                        .eq(NotificationSubscription::getUserId, userId)
                        .orderByAsc(NotificationSubscription::getIsDefault)
                        .orderByDesc(NotificationSubscription::getCreatedAt));

        return subs.stream().map(this::toVO).toList();
    }

    /**
     * 创建自定义订阅
     */
    @Transactional(rollbackFor = Exception.class)
        public NotificationSubscriptionVO create(Long userId, CreateSubscriptionDTO dto) {
        Long sourceId = Long.parseLong(dto.getSourceId());
        String name;

        // 验证来源存在
        if ("tag".equals(dto.getSourceType())) {
            IssueTag tag = tagMapper.selectById(sourceId);
            if (tag == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "标签不存在");
            }
            name = "标签: " + tag.getName();
        } else if ("saved_query".equals(dto.getSourceType())) {
            SavedQuery query = savedQueryMapper.selectById(sourceId);
            if (query == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "保存搜索不存在");
            }
            name = "搜索: " + query.getName();
        } else if ("project".equals(dto.getSourceType())) {
            Project project = projectMapper.selectById(sourceId);
            if (project == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
            }
            name = "项目: " + project.getName();
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的来源类型");
        }

        // 检查重复
        long exists = subscriptionMapper.selectCount(
                new LambdaQueryWrapper<NotificationSubscription>()
                        .eq(NotificationSubscription::getUserId, userId)
                        .eq(NotificationSubscription::getSourceType, dto.getSourceType())
                        .eq(NotificationSubscription::getSourceId, sourceId));
        if (exists > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "已存在相同的订阅");
        }

        // 构建事件配置
        String eventsJson = dto.getEvents() != null ? buildEventsJson(dto.getEvents()) : buildDefaultEventsJson();

        LocalDateTime now = LocalDateTime.now();
        NotificationSubscription sub = new NotificationSubscription();
        sub.setUserId(userId);
        sub.setName(name);
        sub.setSourceType(dto.getSourceType());
        sub.setSourceId(sourceId);
        sub.setIsDefault(false);
        sub.setEvents(eventsJson);
        sub.setCreatedAt(now);
        sub.setUpdatedAt(now);
        subscriptionMapper.insert(sub);

        return toVO(sub);
    }

    /**
     * 更新订阅的事件配置
     */
    @Transactional(rollbackFor = Exception.class)
        public NotificationSubscriptionVO updateEvents(Long userId, Long subscriptionId, UpdateSubscriptionEventsDTO dto) {
        NotificationSubscription sub = subscriptionMapper.selectById(subscriptionId);
        if (sub == null || !sub.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "订阅不存在");
        }

        if (dto.getEvents() != null) {
            sub.setEvents(buildEventsJson(dto.getEvents()));
        }
        sub.setUpdatedAt(LocalDateTime.now());
        subscriptionMapper.updateById(sub);

        return toVO(sub);
    }

    /**
     * 删除订阅（默认订阅不可删除）
     */
    @Transactional(rollbackFor = Exception.class)
        public void delete(Long userId, Long subscriptionId) {
        NotificationSubscription sub = subscriptionMapper.selectById(subscriptionId);
        if (sub == null || !sub.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "订阅不存在");
        }
        if (Boolean.TRUE.equals(sub.getIsDefault())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "默认订阅不可删除，只能修改事件配置");
        }
        subscriptionMapper.deleteById(subscriptionId);
    }

    // ==================== 订阅匹配（供通知引擎调用） ====================

    /**
     * 根据工单标签查找所有匹配订阅的用户ID。
     *
     * @param tagIds   工单当前标签 ID 列表
     * @param eventKey 事件键（"onCreated"/"onUpdated"/"onResolved"/"onCommented"/"onTagAdded"/"onTagRemoved"）
     * @return 匹配的用户 ID 集合
     */
    public Set<Long> findSubscribersByTags(List<Long> tagIds, String eventKey) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> userIds = new HashSet<>();
        for (Long tagId : tagIds) {
            List<Long> ids = subscriptionMapper.selectUserIdsByTagAndEvent(tagId, eventKey);
            if (ids != null) {
                userIds.addAll(ids);
            }
        }
        return userIds;
    }

    /**
     * 查找"分配给我"内建订阅的所有用户ID（用于通知匹配）。
     */
    public Set<Long> findBuiltinSubscribers(String builtinKey, String eventKey) {
        List<Long> ids = subscriptionMapper.selectUserIdsByBuiltinAndEvent(builtinKey, eventKey);
        return ids != null ? new HashSet<>(ids) : Collections.emptySet();
    }

    /**
     * 根据项目ID查找所有订阅了该项目的用户ID列表。
     *
     * @param projectId 项目ID
     * @param eventKey  事件键（"onCreated"/"onUpdated"/"onResolved"/"onCommented"）
     * @return 匹配的用户 ID 集合
     */
    public Set<Long> findSubscribersByProject(Long projectId, String eventKey) {
        if (projectId == null) {
            return Collections.emptySet();
        }
        List<Long> ids = subscriptionMapper.selectUserIdsByProjectAndEvent(projectId, eventKey);
        return ids != null ? new HashSet<>(ids) : Collections.emptySet();
    }

    /**
     * 查找指定 Saved Query 订阅中对应事件已启用的用户ID集合。
     *
     * @param savedQueryId 保存搜索ID
     * @param eventKey     事件键（"onCreated"/"onUpdated"/"onResolved"/"onCommented"）
     * @return 匹配的用户 ID 集合
     */
    public Set<Long> findSubscribersBySavedQuery(Long savedQueryId, String eventKey) {
        if (savedQueryId == null) {
            return Collections.emptySet();
        }
        List<Long> ids = subscriptionMapper.selectUserIdsBySavedQueryAndEvent(savedQueryId, eventKey);
        return ids != null ? new HashSet<>(ids) : Collections.emptySet();
    }

    /**
     * 获取所有启用了指定事件的 Saved Query 订阅记录。
     * 用于通知分发时批量评估哪些 saved search 匹配当前工单。
     *
     * @param eventKey 事件键
     * @return saved_query 订阅列表（含 sourceId 和 userId）
     */
    public List<NotificationSubscription> getSavedQuerySubscriptionsForEvent(String eventKey) {
        List<NotificationSubscription> subs = subscriptionMapper.selectSavedQuerySubscriptionsByEvent(eventKey);
        return subs != null ? subs : Collections.emptyList();
    }

    /**
     * 一次 JOIN 查询获取所有启用了指定事件的 Saved Query 订阅及其 filters。
     * 用于 collectSavedQuerySubscribers() 批量优化——避免 O(M) 次独立 SQL。
     *
     * @param eventKey 事件键
     * @return 包含 userId、sourceId、filters 的结果行列表
     */
    public List<com.trackflow.integration.dto.SavedQuerySubscriptionRow> getSavedQuerySubsWithFilters(String eventKey) {
        var rows = subscriptionMapper.selectSavedQuerySubsWithFilters(eventKey);
        return rows != null ? rows : Collections.emptyList();
    }

    // ==================== 私有方法 ====================

    private NotificationSubscriptionVO toVO(NotificationSubscription sub) {
        NotificationSubscriptionVO vo = new NotificationSubscriptionVO();
        vo.setId(String.valueOf(sub.getId()));
        vo.setUserId(String.valueOf(sub.getUserId()));
        vo.setName(sub.getName());
        vo.setSourceType(sub.getSourceType());
        vo.setSourceId(sub.getSourceId() != null ? String.valueOf(sub.getSourceId()) : null);
        vo.setBuiltinKey(sub.getBuiltinKey());
        vo.setIsDefault(sub.getIsDefault());

        // 解析来源名称
        vo.setSourceName(resolveSourceName(sub));

        // 解析事件配置
        vo.setEvents(parseEvents(sub.getEvents()));

        if (sub.getCreatedAt() != null) {
            vo.setCreatedAt(sub.getCreatedAt().format(DATETIME_FMT));
        }
        if (sub.getUpdatedAt() != null) {
            vo.setUpdatedAt(sub.getUpdatedAt().format(DATETIME_FMT));
        }
        return vo;
    }

    private String resolveSourceName(NotificationSubscription sub) {
        if ("builtin".equals(sub.getSourceType())) {
            return switch (sub.getBuiltinKey()) {
                case "assigned_to_me" -> "分配给我的工单";
                case "reported_by_me" -> "我报告的工单";
                case "commented_by_me" -> "我评论过的工单";
                default -> sub.getBuiltinKey();
            };
        }
        if (sub.getSourceId() == null) {
            return "";
        }
        if ("tag".equals(sub.getSourceType())) {
            IssueTag tag = tagMapper.selectById(sub.getSourceId());
            return tag != null ? tag.getName() : "已删除的标签";
        }
        if ("saved_query".equals(sub.getSourceType())) {
            SavedQuery query = savedQueryMapper.selectById(sub.getSourceId());
            return query != null ? query.getName() : "已删除的搜索";
        }
        if ("project".equals(sub.getSourceType())) {
            Project project = projectMapper.selectById(sub.getSourceId());
            return project != null ? project.getName() : "已删除的项目";
        }
        return "";
    }

    private SubscriptionEventsVO parseEvents(String eventsJson) {
        SubscriptionEventsVO vo = new SubscriptionEventsVO();
        if (eventsJson == null || eventsJson.isBlank()) {
            return vo;
        }
        try {
            var node = objectMapper.readTree(eventsJson);
            vo.setOnCreated(getBooleanOrDefault(node, "onCreated", true));
            vo.setOnUpdated(getBooleanOrDefault(node, "onUpdated", true));
            vo.setOnResolved(getBooleanOrDefault(node, "onResolved", true));
            vo.setOnCommented(getBooleanOrDefault(node, "onCommented", true));
            vo.setOnTagAdded(getBooleanOrDefault(node, "onTagAdded", true));
            vo.setOnTagRemoved(getBooleanOrDefault(node, "onTagRemoved", true));
        } catch (JacksonException e) {
            log.warn("[Subscription] 解析事件配置失败: {}", eventsJson, e);
        }
        return vo;
    }

    private boolean getBooleanOrDefault(tools.jackson.databind.JsonNode node, String field, boolean defaultValue) {
        var child = node.get(field);
        return child != null ? child.asBoolean(defaultValue) : defaultValue;
    }

    private String buildEventsJson(SubscriptionEventsDTO dto) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        map.put("onCreated", dto.getOnCreated() != null ? dto.getOnCreated() : true);
        map.put("onUpdated", dto.getOnUpdated() != null ? dto.getOnUpdated() : true);
        map.put("onResolved", dto.getOnResolved() != null ? dto.getOnResolved() : true);
        map.put("onCommented", dto.getOnCommented() != null ? dto.getOnCommented() : true);
        map.put("onTagAdded", dto.getOnTagAdded() != null ? dto.getOnTagAdded() : true);
        map.put("onTagRemoved", dto.getOnTagRemoved() != null ? dto.getOnTagRemoved() : true);
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JacksonException e) {
            return "{\"onCreated\":true,\"onUpdated\":true,\"onResolved\":true,\"onCommented\":true,\"onTagAdded\":true,\"onTagRemoved\":true}";
        }
    }

    private String buildDefaultEventsJson() {
        return "{\"onCreated\":true,\"onUpdated\":true,\"onResolved\":true,\"onCommented\":true,\"onTagAdded\":true,\"onTagRemoved\":true}";
    }
}
