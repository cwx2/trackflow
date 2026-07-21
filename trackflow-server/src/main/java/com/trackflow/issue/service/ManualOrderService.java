package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.dto.ManualOrderDTO;
import com.trackflow.issue.dto.MoveOrderDTO;
import com.trackflow.issue.entity.IssueManualOrder;
import com.trackflow.issue.mapper.IssueManualOrderMapper;
import com.trackflow.issue.vo.ManualOrderVO;
import com.trackflow.project.entity.Project;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.query.entity.SavedQuery;
import com.trackflow.query.mapper.SavedQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工单手动排序服务 - 处理拖拽排序的持久化和查询
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManualOrderService {

    private static final Set<String> ALLOWED_CONTEXT_TYPES = Set.of("project", "query");

    private final IssueManualOrderMapper manualOrderMapper;
    private final ProjectMapper projectMapper;
    private final SavedQueryMapper savedQueryMapper;

    /**
     * 获取指定上下文的手动排序
     * 优先返回所有者的全局排序，若无全局排序则返回当前用户的个人排序
     */
    public ManualOrderVO getOrder(String contextType, Long contextId) {
        validateContextType(contextType);
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 1. 先查所有者的全局排序（user_id = null）
        List<Long> globalOrder = manualOrderMapper.selectIssueIdsByContext(contextType, contextId, null);
        if (!globalOrder.isEmpty()) {
            return new ManualOrderVO(
                    contextType,
                    String.valueOf(contextId),
                    true,
                    globalOrder.stream().map(String::valueOf).collect(Collectors.toList())
            );
        }

        // 2. 查当前用户的个人排序
        List<Long> userOrder = manualOrderMapper.selectIssueIdsByContext(contextType, contextId, currentUserId);
        if (!userOrder.isEmpty()) {
            return new ManualOrderVO(
                    contextType,
                    String.valueOf(contextId),
                    false,
                    userOrder.stream().map(String::valueOf).collect(Collectors.toList())
            );
        }

        // 3. 无排序数据
        return new ManualOrderVO(contextType, String.valueOf(contextId), false, Collections.emptyList());
    }

    /**
     * 保存完整的手动排序列表
     * 如果用户是上下文所有者，保存为全局排序；否则保存为个人排序
     */
    @Transactional(rollbackFor = Exception.class)
    public ManualOrderVO saveOrder(ManualOrderDTO dto) {
        validateContextType(dto.getContextType());
        Long currentUserId = SecurityUtils.getCurrentUserId();

        boolean isOwner = isContextOwner(dto.getContextType(), dto.getContextId(), currentUserId);
        Long userId = isOwner ? null : currentUserId;

        // 删除旧排序
        manualOrderMapper.deleteByContext(dto.getContextType(), dto.getContextId(), userId);

        // 插入新排序
        LocalDateTime now = LocalDateTime.now();
        List<IssueManualOrder> orders = new ArrayList<>(dto.getIssueIds().size());
        for (int i = 0; i < dto.getIssueIds().size(); i++) {
            IssueManualOrder order = new IssueManualOrder();
            order.setContextType(dto.getContextType());
            order.setContextId(dto.getContextId());
            order.setUserId(userId);
            order.setIssueId(dto.getIssueIds().get(i));
            order.setPosition(i);
            order.setCreatedAt(now);
            order.setUpdatedAt(now);
            orders.add(order);
        }

        // 批量插入
        for (IssueManualOrder order : orders) {
            manualOrderMapper.insert(order);
        }

        log.info("用户 {} {}保存手动排序: contextType={}, contextId={}, count={}",
                currentUserId, isOwner ? "(所有者)" : "(个人)",
                dto.getContextType(), dto.getContextId(), dto.getIssueIds().size());

        return new ManualOrderVO(
                dto.getContextType(),
                String.valueOf(dto.getContextId()),
                isOwner,
                dto.getIssueIds().stream().map(String::valueOf).collect(Collectors.toList())
        );
    }

    /**
     * 移动单个工单到指定位置（拖拽单个 item）
     */
    @Transactional(rollbackFor = Exception.class)
    public ManualOrderVO moveItem(MoveOrderDTO dto) {
        validateContextType(dto.getContextType());
        Long currentUserId = SecurityUtils.getCurrentUserId();

        boolean isOwner = isContextOwner(dto.getContextType(), dto.getContextId(), currentUserId);
        Long userId = isOwner ? null : currentUserId;

        // 获取当前排序列表
        List<Long> currentOrder = manualOrderMapper.selectIssueIdsByContext(
                dto.getContextType(), dto.getContextId(), userId);

        // 如果当前没有排序记录，直接返回（需要先有基础排序再移动）
        if (currentOrder.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前上下文没有手动排序数据，请先设置排序");
        }

        // 移除旧位置
        currentOrder.remove(dto.getIssueId());

        // 插入到新位置
        int targetPos = Math.min(dto.getTargetPosition(), currentOrder.size());
        targetPos = Math.max(0, targetPos);
        currentOrder.add(targetPos, dto.getIssueId());

        // 重建排序（删除旧数据 + 插入新数据）
        ManualOrderDTO saveDto = new ManualOrderDTO();
        saveDto.setContextType(dto.getContextType());
        saveDto.setContextId(dto.getContextId());
        saveDto.setIssueIds(currentOrder);
        return saveOrder(saveDto);
    }

    /**
     * 丢弃手动排序（恢复默认排序）
     */
    @Transactional(rollbackFor = Exception.class)
    public void discardOrder(String contextType, Long contextId) {
        validateContextType(contextType);
        Long currentUserId = SecurityUtils.getCurrentUserId();

        boolean isOwner = isContextOwner(contextType, contextId, currentUserId);
        Long userId = isOwner ? null : currentUserId;

        int deleted = manualOrderMapper.deleteByContext(contextType, contextId, userId);
        log.info("用户 {} 丢弃手动排序: contextType={}, contextId={}, deleted={}",
                currentUserId, contextType, contextId, deleted);
    }

    /**
     * 判断当前用户是否是上下文的所有者
     * - project: 具有 project:update 权限的用户视为所有者
     * - query: saved_query 的 user_id = 当前用户
     */
    private boolean isContextOwner(String contextType, Long contextId, Long userId) {
        if ("project".equals(contextType)) {
            // 项目维度：项目负责人（leadId）或创建者视为所有者
            Project project = projectMapper.selectById(contextId);
            if (project == null) return false;
            return Objects.equals(project.getLeadId(), userId)
                    || Objects.equals(project.getCreatedBy(), userId);
        } else if ("query".equals(contextType)) {
            // 查询维度：saved_query 的创建者
            SavedQuery query = savedQueryMapper.selectById(contextId);
            return query != null && Objects.equals(query.getUserId(), userId);
        }
        return false;
    }

    private void validateContextType(String contextType) {
        if (!ALLOWED_CONTEXT_TYPES.contains(contextType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "不支持的上下文类型: " + contextType + "，允许值: " + ALLOWED_CONTEXT_TYPES);
        }
    }
}
