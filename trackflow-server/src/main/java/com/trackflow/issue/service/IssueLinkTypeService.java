package com.trackflow.issue.service;

import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.issue.entity.IssueLinkType;
import com.trackflow.issue.mapper.IssueLinkMapper;
import com.trackflow.issue.mapper.IssueLinkTypeMapper;
import com.trackflow.issue.vo.IssueLinkTypeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 工单关联类型服务 - 管理链接类型的 CRUD 和缓存
 * 参考 YouTrack Administration > Link Types 管理功能
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueLinkTypeService {

    private final IssueLinkTypeMapper linkTypeMapper;
    private final IssueLinkMapper issueLinkMapper;

    /** 缓存：name -> IssueLinkType */
    private volatile Map<String, IssueLinkType> cache = null;

    /**
     * 获取所有链接类型（带缓存）
     */
    public List<IssueLinkType> listAll() {
        return List.copyOf(getCache().values());
    }

    /**
     * 获取所有链接类型 VO
     */
    public List<IssueLinkTypeVO> listAllVO() {
        return listAll().stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 根据 name 获取链接类型
     */
    public IssueLinkType getByName(String name) {
        return getCache().get(name);
    }

    /**
     * 验证 linkType 是否合法
     */
    public boolean isValidLinkType(String linkType) {
        return getCache().containsKey(linkType);
    }

    /**
     * 获取反向链接类型名称
     * 在新模型中，issue_link 只存储正向类型（outward 方向），
     * 反向展示由 inward_name 提供。
     * 此方法返回当查询反向关联时应使用的展示名。
     *
     * <p>容错处理：若关联类型已被删除（孤立记录），降级返回类型名本身而非抛出异常。</p>
     */
    public String getInwardName(String linkTypeName) {
        IssueLinkType type = getByName(linkTypeName);
        if (type == null) {
            log.warn("未知的关联类型: {}，降级返回类型名本身", linkTypeName);
            return linkTypeName;
        }
        return type.getInwardName();
    }

    /**
     * 获取正向展示名
     *
     * <p>容错处理：若关联类型已被删除（孤立记录），降级返回类型名本身而非抛出异常。</p>
     */
    public String getOutwardName(String linkTypeName) {
        IssueLinkType type = getByName(linkTypeName);
        if (type == null) {
            log.warn("未知的关联类型: {}，降级返回类型名本身", linkTypeName);
            return linkTypeName;
        }
        return type.getOutwardName();
    }

    /**
     * 判断是否为有向类型（需要检测循环依赖）
     */
    public boolean isDirected(String linkTypeName) {
        IssueLinkType type = getByName(linkTypeName);
        if (type == null) return false;
        return "DIRECTED".equals(type.getDirection()) || "AGGREGATION".equals(type.getDirection());
    }

    /**
     * 判断是否为对称/无向类型
     */
    public boolean isUndirected(String linkTypeName) {
        IssueLinkType type = getByName(linkTypeName);
        if (type == null) return false;
        return "UNDIRECTED".equals(type.getDirection());
    }

    /**
     * 创建链接类型（管理员操作）
     */
    public IssueLinkTypeVO createLinkType(String name, String outwardName, String inwardName, String direction) {
        // 验证名称唯一性
        if (getCache().containsKey(name)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "关联类型名称已存在: " + name);
        }
        // 验证 direction
        if (!"DIRECTED".equals(direction) && !"UNDIRECTED".equals(direction) && !"AGGREGATION".equals(direction)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的方向类型: " + direction);
        }

        IssueLinkType entity = new IssueLinkType();
        entity.setName(name);
        entity.setOutwardName(outwardName);
        entity.setInwardName(inwardName);
        entity.setDirection(direction);
        entity.setIsSystem(false);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        linkTypeMapper.insert(entity);

        invalidateCache();
        log.info("创建工单关联类型: name={}, direction={}", name, direction);
        return toVO(entity);
    }

    /**
     * 更新链接类型（管理员操作，系统类型不可修改方向）
     */
    public IssueLinkTypeVO updateLinkType(Long id, String outwardName, String inwardName, String direction) {
        IssueLinkType entity = linkTypeMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("关联类型", id);
        }
        if (entity.getIsSystem() && !entity.getDirection().equals(direction)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统内置类型不可修改方向");
        }

        entity.setOutwardName(outwardName);
        entity.setInwardName(inwardName);
        entity.setDirection(direction);
        entity.setUpdatedAt(LocalDateTime.now());
        linkTypeMapper.updateById(entity);

        invalidateCache();
        log.info("更新工单关联类型: id={}, name={}", id, entity.getName());
        return toVO(entity);
    }

    /**
     * 统计指定关联类型被使用的数量（用于删除前预检）
     *
     * @param id 关联类型 ID
     * @return 使用该类型的 issue_link 记录数量
     */
    public long countUsageByTypeId(Long id) {
        IssueLinkType entity = linkTypeMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("关联类型", id);
        }
        return issueLinkMapper.countByLinkType(entity.getName());
    }

    /**
     * 删除链接类型（管理员操作，系统类型不可删除）
     *
     * <p>对标 YouTrack 行为：删除关联类型时，自动级联删除所有使用该类型的 issue_link 记录。
     * 参考：https://www.jetbrains.com/help/youtrack/cloud/link-types.html</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteLinkType(Long id) {
        IssueLinkType entity = linkTypeMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("关联类型", id);
        }
        if (entity.getIsSystem()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统内置类型不可删除");
        }

        // 对标 YouTrack：删除关联类型时级联删除所有使用该类型的 issue_link 记录
        long deletedLinks = issueLinkMapper.deleteByLinkType(entity.getName());
        if (deletedLinks > 0) {
            log.info("删除关联类型 {} 时级联删除 {} 条关联记录", entity.getName(), deletedLinks);
        }

        linkTypeMapper.deleteById(id);
        invalidateCache();
        log.info("删除工单关联类型: id={}, name={}", id, entity.getName());
    }

    // ========== 缓存管理 ==========

    private Map<String, IssueLinkType> getCache() {
        if (cache == null) {
            synchronized (this) {
                if (cache == null) {
                    refreshCache();
                }
            }
        }
        return cache;
    }

    private void refreshCache() {
        List<IssueLinkType> all = linkTypeMapper.selectList(null);
        Map<String, IssueLinkType> newCache = new ConcurrentHashMap<>();
        for (IssueLinkType type : all) {
            newCache.put(type.getName(), type);
        }
        this.cache = newCache;
    }

    public void invalidateCache() {
        this.cache = null;
    }

    // ========== 转换方法 ==========

    private IssueLinkTypeVO toVO(IssueLinkType entity) {
        IssueLinkTypeVO vo = new IssueLinkTypeVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setName(entity.getName());
        vo.setOutwardName(entity.getOutwardName());
        vo.setInwardName(entity.getInwardName());
        vo.setDirection(entity.getDirection());
        vo.setIsSystem(entity.getIsSystem());
        return vo;
    }
}
