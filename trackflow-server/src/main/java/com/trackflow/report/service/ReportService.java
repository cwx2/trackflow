package com.trackflow.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.report.entity.ReportDefinition;
import com.trackflow.report.mapper.ReportDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportDefinitionMapper reportMapper;
    private final IssueMapper issueMapper;
    private final ObjectMapper objectMapper;

    public List<ReportDefinition> list(Long projectId) {
        LambdaQueryWrapper<ReportDefinition> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.and(w -> w.eq(ReportDefinition::getProjectId, projectId)
                    .or().isNull(ReportDefinition::getProjectId));
        }
        wrapper.orderByAsc(ReportDefinition::getName);
        return reportMapper.selectList(wrapper);
    }

    @Transactional
    public ReportDefinition create(ReportDefinition report) {
        reportMapper.insert(report);
        return report;
    }

    @Transactional
    public void delete(Long id) {
        reportMapper.deleteById(id);
    }

    /**
     * 执行报表：根据报表配置生成数据
     */
    public Map<String, Object> execute(Long id) {
        ReportDefinition report = reportMapper.selectById(id);
        if (report == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Report not found");

        Map<String, Object> config = parseConfig(report.getConfig());
        String groupBy = (String) config.getOrDefault("groupBy", "status");

        // 构建查询条件
        LambdaQueryWrapper<Issue> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(Issue::getDeletedAt);
        if (report.getProjectId() != null) {
            wrapper.eq(Issue::getProjectId, report.getProjectId());
        }

        List<Issue> issues = issueMapper.selectList(wrapper);

        // 按 groupBy 分组统计
        Map<String, Long> grouped = issues.stream()
                .collect(Collectors.groupingBy(issue -> getGroupValue(issue, groupBy), Collectors.counting()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", report.getName());
        result.put("type", report.getType());
        result.put("groupBy", groupBy);
        result.put("labels", new ArrayList<>(grouped.keySet()));
        result.put("data", new ArrayList<>(grouped.values()));
        result.put("total", issues.size());
        return result;
    }

    private String getGroupValue(Issue issue, String groupBy) {
        return switch (groupBy) {
            case "status" -> String.valueOf(issue.getStatusId());
            case "priority" -> issue.getPriority();
            case "type" -> issue.getIssueType();
            case "assignee" -> issue.getAssigneeId() != null ? String.valueOf(issue.getAssigneeId()) : "Unassigned";
            default -> "Other";
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
