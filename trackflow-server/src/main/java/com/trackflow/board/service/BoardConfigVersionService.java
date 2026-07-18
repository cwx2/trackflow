package com.trackflow.board.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.board.entity.BoardConfigVersion;
import com.trackflow.board.mapper.BoardConfigVersionMapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 看板配置版本管理服务。
 * <p>
 * 提供乐观锁并发控制：每次保存看板配置前检查版本一致性，
 * 不一致则抛出 409 Conflict 告知客户端需要刷新。
 */
@Service
@RequiredArgsConstructor
public class BoardConfigVersionService {

    private final BoardConfigVersionMapper versionMapper;

    /**
     * 获取项目的当前看板配置版本号。
     * 如果项目没有版本记录（尚未保存过配置），返回 0。
     *
     * @param projectId 项目ID
     * @return 当前版本号（0 表示无记录）
     */
    public int getCurrentVersion(Long projectId) {
        BoardConfigVersion record = versionMapper.selectOne(
                new LambdaQueryWrapper<BoardConfigVersion>()
                        .eq(BoardConfigVersion::getProjectId, projectId)
        );
        return record != null ? record.getVersion() : 0;
    }

    /**
     * 检查并递增版本号（CAS 乐观锁）。
     * <p>
     * 如果客户端传入的 expectedVersion 与数据库中的版本不一致，
     * 说明有其他人在此期间修改过配置，抛出 409 Conflict。
     * <p>
     * 如果客户端传入 null（向后兼容旧版前端），跳过版本检查，直接递增。
     *
     * @param projectId       项目ID
     * @param expectedVersion 客户端期望的版本号（null 表示跳过检查）
     * @throws BusinessException 版本冲突时抛出 BOARD_CONFIG_VERSION_CONFLICT
     */
    public void checkAndIncrement(Long projectId, Integer expectedVersion) {
        BoardConfigVersion record = findOrCreate(projectId);

        if (expectedVersion == null) {
            // 向后兼容：旧版前端不传版本号，直接递增不检查
            versionMapper.incrementVersionCAS(
                    projectId, record.getVersion(), SecurityUtils.getCurrentUserId());
            return;
        }

        int updated = versionMapper.incrementVersionCAS(
                projectId, expectedVersion, SecurityUtils.getCurrentUserId());

        if (updated == 0) {
            throw new BusinessException(ErrorCode.BOARD_CONFIG_VERSION_CONFLICT,
                    "看板配置已被其他人修改（当前版本: " + record.getVersion()
                            + "，您的版本: " + expectedVersion + "），请刷新后重试");
        }
    }

    /**
     * 查找版本记录，不存在则创建（初始版本=1）。
     */
    private BoardConfigVersion findOrCreate(Long projectId) {
        BoardConfigVersion record = versionMapper.selectOne(
                new LambdaQueryWrapper<BoardConfigVersion>()
                        .eq(BoardConfigVersion::getProjectId, projectId)
        );
        if (record == null) {
            record = new BoardConfigVersion();
            record.setProjectId(projectId);
            record.setVersion(1);
            record.setUpdatedAt(LocalDateTime.now());
            record.setUpdatedBy(SecurityUtils.getCurrentUserId());
            versionMapper.insert(record);
        }
        return record;
    }
}
