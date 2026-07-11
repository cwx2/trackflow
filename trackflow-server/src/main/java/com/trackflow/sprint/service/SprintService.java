package com.trackflow.sprint.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.sprint.dto.CreateSprintDTO;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.mapper.SprintMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintMapper sprintMapper;

    public List<Sprint> listByProject(Long projectId) {
        return sprintMapper.selectList(
                new LambdaQueryWrapper<Sprint>()
                        .eq(Sprint::getProjectId, projectId)
                        .orderByDesc(Sprint::getCreatedAt)
        );
    }

    public Sprint getById(Long id) {
        Sprint sprint = sprintMapper.selectById(id);
        if (sprint == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Sprint not found");
        return sprint;
    }

    @Transactional
    public Sprint create(Long projectId, CreateSprintDTO dto) {
        Sprint sprint = new Sprint();
        sprint.setProjectId(projectId);
        sprint.setName(dto.getName());
        sprint.setGoal(dto.getGoal());
        sprint.setStartDate(dto.getStartDate());
        sprint.setEndDate(dto.getEndDate());
        sprint.setStatus("planned");
        sprintMapper.insert(sprint);
        return sprint;
    }

    @Transactional
    public Sprint activate(Long id) {
        Sprint sprint = getById(id);
        sprint.setStatus("active");
        sprintMapper.updateById(sprint);
        return sprint;
    }

    @Transactional
    public Sprint complete(Long id) {
        Sprint sprint = getById(id);
        sprint.setStatus("completed");
        sprintMapper.updateById(sprint);
        return sprint;
    }

    @Transactional
    public void delete(Long id) {
        getById(id);
        sprintMapper.deleteById(id);
    }
}
