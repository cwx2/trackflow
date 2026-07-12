package com.trackflow.sprint.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.sprint.dto.CreateSprintDTO;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.sprint.vo.SprintVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintMapper sprintMapper;

    /**
     * 查询项目的 Sprint 列表（带工单统计）
     */
    public List<SprintVO> listByProjectWithStats(Long projectId) {
        List<Map<String, Object>> rows = sprintMapper.selectSprintsWithStats(projectId);
        List<SprintVO> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            SprintVO vo = new SprintVO();
            vo.setId(String.valueOf(row.get("id")));
            vo.setProjectId(String.valueOf(row.get("project_id")));
            vo.setName((String) row.get("name"));
            vo.setGoal((String) row.get("goal"));
            vo.setStatus((String) row.get("status"));
            Object startDate = row.get("start_date");
            if (startDate instanceof LocalDate ld) {
                vo.setStartDate(ld);
            } else if (startDate instanceof java.sql.Date sd) {
                vo.setStartDate(sd.toLocalDate());
            }
            Object endDate = row.get("end_date");
            if (endDate instanceof LocalDate ld) {
                vo.setEndDate(ld);
            } else if (endDate instanceof java.sql.Date sd) {
                vo.setEndDate(sd.toLocalDate());
            }
            Object createdAt = row.get("created_at");
            if (createdAt instanceof LocalDateTime ldt) {
                vo.setCreatedAt(ldt);
            } else if (createdAt instanceof java.sql.Timestamp ts) {
                vo.setCreatedAt(ts.toLocalDateTime());
            }
            vo.setTotalIssues(((Number) row.get("total_issues")).intValue());
            vo.setDoneIssues(((Number) row.get("done_issues")).intValue());
            vo.setInProgressIssues(((Number) row.get("in_progress_issues")).intValue());
            vo.setTodoIssues(((Number) row.get("todo_issues")).intValue());
            vo.setOverdueIssues(((Number) row.get("overdue_issues")).intValue());
            result.add(vo);
        }
        return result;
    }

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
