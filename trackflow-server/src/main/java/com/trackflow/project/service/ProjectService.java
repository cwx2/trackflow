package com.trackflow.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.common.service.MinioService;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueAttachmentMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.entity.SprintStatus;
import com.trackflow.project.converter.ProjectConverter;
import com.trackflow.project.dto.AddMemberDTO;
import com.trackflow.project.dto.CreateProjectDTO;
import com.trackflow.project.dto.UpdateProjectDTO;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.project.vo.ProjectDeletePreCheckVO;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysRoleMapper;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trackflow.common.event.ProjectNotificationEvent;
import com.trackflow.integration.entity.NotificationType;

import com.trackflow.project.vo.ProjectDetailVO;
import com.trackflow.project.vo.ProjectMemberVO;
import com.trackflow.project.vo.ProjectStatisticsVO;
import com.trackflow.project.vo.ProjectVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 项目管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private static final Long PROJECT_ADMIN_ROLE_ID = 2L;
    private static final String ACCESSIBLE_PROJECTS_CACHE_PREFIX = "accessible_projects:";

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final ProjectConverter projectConverter;
    private final PermissionService permissionService;
    private final StringRedisTemplate redisTemplate;
    private final IssueMapper issueMapper;
    private final IssueStatusMapper issueStatusMapper;
    private final IssueAttachmentMapper issueAttachmentMapper;
    private final SprintMapper sprintMapper;
    private final ProjectActivityService projectActivityService;
    private final ProjectInitializationService projectInitializationService;
    private final MinioService minioService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 创建项目
     */
    @Transactional
    public Project create(CreateProjectDTO dto) {
        // Key 唯一性检查（不区分大小写）
        Long count = projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().eq(Project::getKey, dto.getKey().toUpperCase())
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.PROJECT_KEY_DUPLICATE);
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();

        // leadId 校验：如果指定了 leadId，校验用户存在且状态为 active
        Long leadId = dto.getLeadId();
        if (leadId != null) {
            SysUser leadUser = userMapper.selectById(leadId);
            if (leadUser == null || !"active".equals(leadUser.getStatus())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "指定的项目负责人用户不存在或已禁用");
            }
        } else {
            leadId = currentUserId;
        }

        Project project = new Project();
        project.setName(dto.getName());
        project.setKey(dto.getKey().toUpperCase());
        project.setDescription(dto.getDescription());
        project.setLeadId(leadId);
        project.setStatus("active");
        project.setVisibility("private");
        project.setIssueSequence(0);
        projectMapper.insert(project);

        // 自动添加创建者为项目管理员
        if (currentUserId != null) {
            ProjectMember member = new ProjectMember();
            member.setProjectId(project.getId());
            member.setUserId(currentUserId);
            member.setRoleId(PROJECT_ADMIN_ROLE_ID);
            member.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(member);
        }

        // 如果 leadId 不是创建者本人，也将 lead 添加为项目管理员
        if (leadId != null && !leadId.equals(currentUserId)) {
            ProjectMember leadMember = new ProjectMember();
            leadMember.setProjectId(project.getId());
            leadMember.setUserId(leadId);
            leadMember.setRoleId(PROJECT_ADMIN_ROLE_ID);
            leadMember.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(leadMember);
        }

        // 根据模板类型初始化项目（工作流、看板列配置等）
        projectInitializationService.initialize(project.getId(), dto.getTemplate());

        return project;
    }

    /**
     * 项目列表（只返回用户有权限的项目）
     * 包含：成员项目 + internal/public 项目（对已登录非成员可见）
     */
    public Page<Project> list(Page<Project> page, String keyword, String status, Long userId) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();

        // 非系统管理员：成员项目 + internal/public 项目
        if (userId != null && !permissionService.isSystemAdmin(userId)) {
            List<Long> memberProjectIds = memberMapper.selectProjectIdsByUserId(userId);
            // 显示：用户参与的项目 OR visibility 为 internal/public 的项目
            wrapper.and(w -> {
                if (!memberProjectIds.isEmpty()) {
                    w.in(Project::getId, memberProjectIds)
                     .or()
                     .in(Project::getVisibility, List.of("internal", "public"));
                } else {
                    w.in(Project::getVisibility, List.of("internal", "public"));
                }
            });
        }

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Project::getName, keyword).or().like(Project::getKey, keyword));
        }

        if (status != null && !status.isBlank()) {
            wrapper.eq(Project::getStatus, status);
        } else {
            // 默认不显示归档项目
            wrapper.eq(Project::getStatus, "active");
        }

        wrapper.orderByDesc(Project::getCreatedAt);
        return projectMapper.selectPage(page, wrapper);
    }

    /**
     * 批量填充项目 VO 的成员摘要信息（memberCount + topMembers）。
     * 使用两次批量查询：一次 COUNT 分组，一次 TOP N 成员查用户名。
     */
    public void populateMemberSummary(List<ProjectVO> voList) {
        if (voList == null || voList.isEmpty()) return;

        List<Long> projectIds = voList.stream()
                .map(vo -> Long.valueOf(vo.getId()))
                .toList();

        // 1. 批量查每个项目的成员数量
        List<ProjectMember> allMembers = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().in(ProjectMember::getProjectId, projectIds)
        );

        // 按 projectId 分组，同一用户去重（一人可能有多角色）
        Map<Long, List<Long>> projectUserMap = allMembers.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ProjectMember::getProjectId,
                        java.util.stream.Collectors.mapping(ProjectMember::getUserId, java.util.stream.Collectors.toList())
                ));

        // 去重 userId per project
        Map<Long, List<Long>> projectDistinctUsers = new java.util.HashMap<>();
        projectUserMap.forEach((pid, userIds) -> {
            projectDistinctUsers.put(pid, userIds.stream().distinct().toList());
        });

        // 2. 收集所有需要查询 displayName 的 userId（取每个项目前5）
        Set<Long> topUserIds = new java.util.HashSet<>();
        projectDistinctUsers.forEach((pid, userIds) -> {
            userIds.stream().limit(5).forEach(topUserIds::add);
        });

        // 3. 批量查用户 displayName
        Map<Long, String> userDisplayNameMap = new java.util.HashMap<>();
        if (!topUserIds.isEmpty()) {
            List<SysUser> users = userMapper.selectBatchIds(topUserIds);
            users.forEach(u -> userDisplayNameMap.put(u.getId(), u.getDisplayName()));
        }

        // 4. 填充 VO
        for (ProjectVO vo : voList) {
            Long pid = Long.valueOf(vo.getId());
            List<Long> distinctUsers = projectDistinctUsers.getOrDefault(pid, List.of());
            vo.setMemberCount(distinctUsers.size());

            List<String> topMembers = distinctUsers.stream()
                    .limit(5)
                    .map(uid -> {
                        String name = userDisplayNameMap.get(uid);
                        return (name != null && !name.isBlank()) ? name : "?";
                    })
                    .toList();
            vo.setTopMembers(topMembers);
        }
    }

    /**
     * 项目详情
     */
    public Project getById(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        return project;
    }

    /**
     * 获取项目详情（含当前用户角色和成员统计）
     */
    public ProjectDetailVO getProjectDetail(Long projectId, Long currentUserId) {
        Project project = getById(projectId);

        // 使用 Converter 映射基础字段
        ProjectDetailVO vo = projectConverter.toDetailVO(project);

        // 查询成员总数
        Long memberCount = memberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId)
        );
        vo.setMemberCount(memberCount.intValue());

        // 查询当前用户在项目中的角色（当前模型：一个用户在一个项目中只有唯一角色）
        if (currentUserId != null) {
            if (permissionService.isSystemAdmin(currentUserId)) {
                vo.setMyRoleName("系统管理员");
                vo.setMyRoleCode("system_admin");
            } else {
                List<Long> roleIds = memberMapper.selectRoleIdsByUserAndProject(currentUserId, projectId);
                if (!roleIds.isEmpty()) {
                    SysRole role = roleMapper.selectById(roleIds.get(0));
                    if (role != null) {
                        vo.setMyRoleName(role.getName());
                        vo.setMyRoleCode(role.getCode());
                    }
                } else {
                    // 非成员但项目可见（internal/public）→ 显示 NonMember 角色
                    String visibility = project.getVisibility();
                    if ("internal".equals(visibility) || "public".equals(visibility)) {
                        vo.setMyRoleName("非成员");
                        vo.setMyRoleCode("non_member");
                    }
                }
            }
        }

        // 查询负责人名称
        if (project.getLeadId() != null) {
            SysUser lead = userMapper.selectById(project.getLeadId());
            if (lead != null) {
                vo.setLeadName(lead.getDisplayName());
            }
        }

        return vo;
    }

    /**
     * 更新项目
     */
    @Transactional
    public Project update(Long id, UpdateProjectDTO dto) {
        Project project = getById(id);
        // 归档项目不允许修改
        assertProjectActive(id);

        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 名称变更
        if (dto.getName() != null) {
            String trimmedName = dto.getName().trim();
            if (trimmedName.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "项目名称不能为空");
            }
            if (!trimmedName.equals(project.getName())) {
                String oldName = project.getName();
                project.setName(trimmedName);
                Map<String, Object> detail = new java.util.LinkedHashMap<>();
                detail.put("field", "name");
                detail.put("old_value", oldName);
                detail.put("new_value", trimmedName);
                projectActivityService.log(id, currentUserId, "update_project", null, detail);
            }
        }

        // 描述变更
        if (dto.getDescription() != null) {
            String trimmedDesc = dto.getDescription().trim();
            if (!trimmedDesc.equals(project.getDescription() != null ? project.getDescription() : "")) {
                String oldDesc = project.getDescription();
                project.setDescription(trimmedDesc);
                Map<String, Object> detail = new java.util.LinkedHashMap<>();
                detail.put("field", "description");
                detail.put("old_value", oldDesc != null ? oldDesc : "");
                detail.put("new_value", trimmedDesc);
                projectActivityService.log(id, currentUserId, "update_project", null, detail);
            }
        }

        // 可见性变更：需要失效所有用户的 accessible_projects 缓存
        if (dto.getVisibility() != null && !dto.getVisibility().equals(project.getVisibility())) {
            String oldVisibility = project.getVisibility();
            project.setVisibility(dto.getVisibility());
            invalidateAllAccessibleProjectsCache();
            Map<String, Object> detail = new java.util.LinkedHashMap<>();
            detail.put("field", "visibility");
            detail.put("old_value", oldVisibility);
            detail.put("new_value", dto.getVisibility());
            projectActivityService.log(id, currentUserId, "change_visibility", null, detail);
        }

        // 负责人变更：需要完整的业务校验和权限联动
        if (dto.getLeadId() != null && !dto.getLeadId().equals(project.getLeadId())) {
            changeLead(project, dto.getLeadId());
        }

        projectMapper.updateById(project);
        return project;
    }

    /**
     * 失效所有用户的 accessible_projects 缓存。
     * 当项目 visibility 变更时调用，确保所有用户及时看到/看不到该项目。
     * 使用 SCAN 避免 KEYS 阻塞 Redis。
     */
    private void invalidateAllAccessibleProjectsCache() {
        Set<String> keys = new java.util.HashSet<>();
        var options = org.springframework.data.redis.core.ScanOptions.scanOptions()
                .match(ACCESSIBLE_PROJECTS_CACHE_PREFIX + "*").count(200).build();
        try (var cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 变更项目负责人
     * 1. 校验新负责人是项目成员
     * 2. 校验新负责人用户状态为 active
     * 3. 如果新负责人当前角色不是 project_admin，自动升级
     * 4. 记录活动日志
     */
    private void changeLead(Project project, Long newLeadId) {
        Long projectId = project.getId();
        Long oldLeadId = project.getLeadId();

        // 1. 校验新负责人用户存在且状态为 active
        SysUser newLead = userMapper.selectById(newLeadId);
        if (newLead == null || !"active".equals(newLead.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新负责人用户不存在或已禁用");
        }

        // 2. 校验新负责人必须是项目成员
        Long memberCount = memberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, newLeadId)
        );
        if (memberCount == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新负责人必须是该项目的成员");
        }

        // 3. 如果新负责人当前没有 project_admin 角色，自动添加
        List<Long> newLeadRoleIds = memberMapper.selectRoleIdsByUserAndProject(newLeadId, projectId);
        if (!newLeadRoleIds.contains(PROJECT_ADMIN_ROLE_ID)) {
            // 添加 project_admin 角色记录
            ProjectMember adminMember = new ProjectMember();
            adminMember.setProjectId(projectId);
            adminMember.setUserId(newLeadId);
            adminMember.setRoleId(PROJECT_ADMIN_ROLE_ID);
            adminMember.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(adminMember);
            permissionService.invalidateCache(newLeadId);

            // 记录角色自动升级的活动日志
            SysRole adminRole = roleMapper.selectById(PROJECT_ADMIN_ROLE_ID);
            List<String> oldRoleNames = newLeadRoleIds.stream()
                    .map(rid -> roleMapper.selectById(rid))
                    .filter(Objects::nonNull)
                    .map(SysRole::getName)
                    .toList();
            Map<String, Object> roleDetailMap = new java.util.LinkedHashMap<>();
            roleDetailMap.put("old_role_ids", newLeadRoleIds);
            roleDetailMap.put("old_role_names", String.join(", ", oldRoleNames));
            roleDetailMap.put("added_role_id", PROJECT_ADMIN_ROLE_ID);
            roleDetailMap.put("added_role_name", adminRole != null ? adminRole.getName() : "项目管理员");
            roleDetailMap.put("reason", "lead_promotion");
            Long currentUserId = SecurityUtils.getCurrentUserId();
            projectActivityService.log(projectId, currentUserId, "change_role", newLeadId, roleDetailMap);
        }

        // 4. 更新负责人字段
        project.setLeadId(newLeadId);

        // 5. 记录负责人变更活动日志
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String oldLeadName = "";
        if (oldLeadId != null) {
            SysUser oldLead = userMapper.selectById(oldLeadId);
            if (oldLead != null) {
                oldLeadName = oldLead.getDisplayName() != null ? oldLead.getDisplayName() : oldLead.getUsername();
            }
        }
        String newLeadName = newLead.getDisplayName() != null ? newLead.getDisplayName() : newLead.getUsername();
        Map<String, Object> leadDetailMap = new java.util.LinkedHashMap<>();
        leadDetailMap.put("old_lead_id", oldLeadId);
        leadDetailMap.put("old_lead_name", oldLeadName);
        leadDetailMap.put("new_lead_id", newLeadId);
        leadDetailMap.put("new_lead_name", newLeadName);
        projectActivityService.log(projectId, currentUserId, "change_lead", newLeadId, leadDetailMap);

        // 6. 通知新负责人 — 事务提交后触发
        eventPublisher.publishEvent(new ProjectNotificationEvent.NewLead(newLeadId, currentUserId, projectId, project.getName()));

        // 7. 通知旧负责人（如果存在且不同）— 事务提交后触发
        if (oldLeadId != null && !oldLeadId.equals(currentUserId)) {
            eventPublisher.publishEvent(new ProjectNotificationEvent.OldLead(oldLeadId, currentUserId, projectId,
                    project.getName(), newLeadName));
        }
    }

    /**
     * 归档项目
     * <p>
     * 归档流程：
     * 1. 校验项目当前状态（必须为 active）
     * 2. 将项目状态设为 archived
     * 3. 将项目内 ACTIVE 状态的 Sprint 自动变为 PLANNED
     * 4. 记录项目活动日志
     * 5. 通知所有项目成员
     */
    @Transactional
    public void archive(Long id) {
        Project project = getById(id);
        if ("archived".equals(project.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目已处于归档状态");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 1. 归档项目
        project.setStatus("archived");
        projectMapper.updateById(project);

        // 2. 暂停活跃 Sprint（ACTIVE → PLANNED）
        int suspendedSprintCount = suspendActiveSprintsForProject(id);

        // 3. 记录活动日志
        Map<String, Object> detail = new java.util.LinkedHashMap<>();
        if (suspendedSprintCount > 0) {
            detail.put("suspended_sprint_count", suspendedSprintCount);
        }
        projectActivityService.log(id, currentUserId, "archive_project", null,
                detail.isEmpty() ? null : detail);

        // 4. 通知所有项目成员 — 事务提交后触发
        eventPublisher.publishEvent(new ProjectNotificationEvent.LifecycleEvent(id, currentUserId,
                "项目归档通知", "项目「" + project.getName() + "」已被归档", NotificationType.project_archived));
    }

    /**
     * 恢复项目
     * <p>
     * 恢复流程：
     * 1. 校验项目当前状态（必须为 archived）
     * 2. 将项目状态设为 active
     * 3. 记录项目活动日志
     * 4. 通知所有项目成员
     */
    @Transactional
    public void restore(Long id) {
        Project project = getById(id);
        if (!"archived".equals(project.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只能恢复归档状态的项目");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 1. 恢复项目
        project.setStatus("active");
        projectMapper.updateById(project);

        // 2. 记录活动日志
        projectActivityService.log(id, currentUserId, "restore_project", null, null);

        // 3. 通知所有项目成员 — 事务提交后触发
        eventPublisher.publishEvent(new ProjectNotificationEvent.LifecycleEvent(id, currentUserId,
                "项目恢复通知", "项目「" + project.getName() + "」已从归档状态恢复", NotificationType.project_restored));
    }

    /**
     * 暂停项目内所有活跃 Sprint（ACTIVE → PLANNED）
     *
     * @return 受影响的 Sprint 数量
     */
    private int suspendActiveSprintsForProject(Long projectId) {
        LambdaUpdateWrapper<Sprint> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Sprint::getProjectId, projectId)
                .eq(Sprint::getStatus, SprintStatus.ACTIVE)
                .set(Sprint::getStatus, SprintStatus.PLANNED)
                .set(Sprint::getUpdatedAt, LocalDateTime.now());
        return sprintMapper.update(null, updateWrapper);
    }

    // ========== 回收站保留策略设置 ==========

    /**
     * 获取项目回收站保留策略
     */
    public Map<String, Object> getTrashSettings(Long id) {
        Project project = getById(id);
        return Map.of("trashRetentionDays", parseRetentionDays(project.getSettings()));
    }

    /**
     * 更新项目回收站保留策略
     * @param days 保留天数，0 表示永久保留
     */
    @Transactional
    public void updateTrashSettings(Long id, int days) {
        Project project = getById(id);
        String existingSettings = project.getSettings();
        try {
            var root = (existingSettings != null && !existingSettings.isBlank())
                    ? (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(existingSettings)
                    : objectMapper.createObjectNode();
            root.put("trashRetentionDays", days);
            project.setSettings(objectMapper.writeValueAsString(root));
        } catch (Exception e) {
            project.setSettings("{\"trashRetentionDays\":" + days + "}");
        }
        projectMapper.updateById(project);
    }

    /**
     * 从 settings JSON 解析保留天数
     */
    public int parseRetentionDays(String settingsJson) {
        if (settingsJson == null || settingsJson.isBlank() || "{}".equals(settingsJson)) {
            return 30;
        }
        try {
            var node = objectMapper.readTree(settingsJson);
            var retentionNode = node.get("trashRetentionDays");
            if (retentionNode == null) return 30;
            if (retentionNode.isTextual() && "forever".equalsIgnoreCase(retentionNode.asText())) return 0;
            int days = retentionNode.asInt(-1);
            return days >= 0 ? days : 30;
        } catch (Exception e) {
            return 30;
        }
    }

    // ========== 成员管理 ==========

    /**
     * 获取项目成员列表（包含用户信息）
     */
    public List<ProjectMemberVO> listMembersVO(Long projectId) {
        List<ProjectMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId)
        );
        if (members.isEmpty()) return List.of();

        // 获取用户信息
        List<Long> userIds = members.stream().map(ProjectMember::getUserId).distinct().toList();
        var users = userMapper.selectBatchIds(userIds);
        Map<Long, com.trackflow.system.entity.SysUser> userMap = users.stream()
                .collect(java.util.stream.Collectors.toMap(com.trackflow.system.entity.SysUser::getId, u -> u));

        // 获取角色信息（用于填充 roleNames）
        List<Long> roleIds = members.stream().map(ProjectMember::getRoleId).distinct().toList();
        var roles = roleMapper.selectBatchIds(roleIds);
        Map<Long, String> roleNameMap = roles.stream()
                .collect(java.util.stream.Collectors.toMap(SysRole::getId, SysRole::getName));

        // 按 userId 聚合（一个用户一条 VO，含多角色）
        Map<Long, List<ProjectMember>> membersByUser = members.stream()
                .collect(java.util.stream.Collectors.groupingBy(ProjectMember::getUserId));

        return membersByUser.entrySet().stream().map(entry -> {
            Long userId = entry.getKey();
            List<ProjectMember> userMembers = entry.getValue();
            ProjectMember first = userMembers.get(0);

            ProjectMemberVO vo = new ProjectMemberVO();
            vo.setId(first.getId() != null ? first.getId().toString() : null);
            vo.setProjectId(first.getProjectId() != null ? first.getProjectId().toString() : null);
            vo.setUserId(userId.toString());
            // 向后兼容：roleId 取第一个角色
            vo.setRoleId(first.getRoleId() != null ? first.getRoleId().toString() : null);
            // 多角色列表
            List<String> allRoleIds = userMembers.stream()
                    .map(m -> m.getRoleId().toString())
                    .toList();
            vo.setRoleIds(allRoleIds);
            List<String> allRoleNames = userMembers.stream()
                    .map(m -> roleNameMap.getOrDefault(m.getRoleId(), ""))
                    .filter(name -> !name.isEmpty())
                    .toList();
            vo.setRoleNames(allRoleNames);
            // 取最早的 joinedAt
            vo.setJoinedAt(userMembers.stream()
                    .map(ProjectMember::getJoinedAt)
                    .filter(java.util.Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .orElse(null));
            var user = userMap.get(userId);
            if (user != null) {
                vo.setUsername(user.getUsername());
                vo.setDisplayName(user.getDisplayName());
                vo.setEmail(user.getEmail());
            }
            return vo;
        }).toList();
    }

    /**
     * 添加项目成员（支持多角色）
     */
    @Transactional
    public void addMember(Long projectId, AddMemberDTO dto) {
        // 1. 校验项目状态（归档项目不允许添加成员）
        Project project = getById(projectId);
        if (!"active".equals(project.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "归档项目不允许管理成员");
        }

        // 2. 校验用户存在且状态为 active
        SysUser user = userMapper.selectById(dto.getUserId());
        if (user == null || !"active".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户不存在或已禁用");
        }

        // 3. 获取有效角色列表
        List<Long> effectiveRoleIds = dto.getEffectiveRoleIds();
        if (effectiveRoleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "至少需要指定一个角色");
        }

        // 4. 校验所有角色类型必须为 project
        List<SysRole> roles = roleMapper.selectBatchIds(effectiveRoleIds);
        if (roles.size() != effectiveRoleIds.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存在无效的角色ID");
        }
        for (SysRole role : roles) {
            if (!"project".equals(role.getRoleType())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "只能分配项目角色，角色「" + role.getName() + "」不是项目角色");
            }
        }

        // 5. 查询该用户已有的角色（多角色场景下过滤已存在的）
        List<Long> existingRoleIds = memberMapper.selectRoleIdsByUserAndProject(dto.getUserId(), projectId);
        List<Long> newRoleIds = effectiveRoleIds.stream()
                .filter(rid -> !existingRoleIds.contains(rid))
                .toList();

        if (newRoleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该用户已拥有所有指定角色");
        }

        // 6. 插入新角色记录
        LocalDateTime now = LocalDateTime.now();
        List<String> addedRoleNames = new java.util.ArrayList<>();
        for (Long roleId : newRoleIds) {
            ProjectMember member = new ProjectMember();
            member.setProjectId(projectId);
            member.setUserId(dto.getUserId());
            member.setRoleId(roleId);
            member.setJoinedAt(now);
            memberMapper.insert(member);
            // 记录角色名称
            roles.stream().filter(r -> r.getId().equals(roleId)).findFirst()
                    .ifPresent(r -> addedRoleNames.add(r.getName()));
        }

        // 失效权限缓存
        permissionService.invalidateCache(dto.getUserId());
        // 失效项目列表缓存
        redisTemplate.delete(ACCESSIBLE_PROJECTS_CACHE_PREFIX + dto.getUserId());

        // 审计日志
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String roleNamesStr = String.join(", ", addedRoleNames);
        projectActivityService.log(projectId, currentUserId, "add_member", dto.getUserId(),
                Map.of("role_ids", newRoleIds, "role_names", roleNamesStr));

        // 通知被添加的用户 — 事务提交后触发
        eventPublisher.publishEvent(new ProjectNotificationEvent.MemberAdded(dto.getUserId(), currentUserId, projectId,
                project.getName(), roleNamesStr));
    }

    /**
     * 更新成员角色（全量替换：设置用户在项目中的角色列表）
     */
    @Transactional
    public void updateMemberRole(Long projectId, Long userId, Long roleId) {
        updateMemberRoles(projectId, userId, List.of(roleId));
    }

    /**
     * 更新成员角色（多角色版本：全量替换）
     */
    @Transactional
    public void updateMemberRoles(Long projectId, Long userId, List<Long> newRoleIds) {
        // 校验项目状态（归档项目不允许管理成员）
        Project project = getById(projectId);
        if (!"active".equals(project.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "归档项目不允许管理成员");
        }

        if (newRoleIds == null || newRoleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "至少需要指定一个角色");
        }

        // 去重（前端多选可能传入重复值）
        List<Long> distinctRoleIds = newRoleIds.stream().distinct().toList();

        // 校验所有角色类型必须为 project
        List<SysRole> newRoles = roleMapper.selectBatchIds(distinctRoleIds);
        if (newRoles.size() != distinctRoleIds.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存在无效的角色ID");
        }
        for (SysRole role : newRoles) {
            if (!"project".equals(role.getRoleType())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "只能分配项目角色");
            }
        }

        // 查询用户当前的成员记录
        List<ProjectMember> existingMembers = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId)
        );
        if (existingMembers.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "该用户不是项目成员");
        }

        List<Long> oldRoleIds = existingMembers.stream().map(ProjectMember::getRoleId).toList();

        // 保护最后一个项目管理员：如果用户当前有 project_admin 角色，且新角色列表中没有，
        // 需要确认还有其他 project_admin
        if (oldRoleIds.contains(PROJECT_ADMIN_ROLE_ID) && !distinctRoleIds.contains(PROJECT_ADMIN_ROLE_ID)) {
            long adminCount = memberMapper.selectCount(
                    new LambdaQueryWrapper<ProjectMember>()
                            .eq(ProjectMember::getProjectId, projectId)
                            .eq(ProjectMember::getRoleId, PROJECT_ADMIN_ROLE_ID)
            );
            // 如果所有 admin 记录都属于当前用户，则不允许移除 admin 角色
            long userAdminCount = existingMembers.stream()
                    .filter(m -> m.getRoleId().equals(PROJECT_ADMIN_ROLE_ID))
                    .count();
            if (adminCount <= userAdminCount) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不能移除项目中最后一个管理员的管理员角色");
            }
        }

        // 计算需要删除和新增的角色
        List<Long> toRemove = oldRoleIds.stream().filter(rid -> !distinctRoleIds.contains(rid)).toList();
        List<Long> toAdd = distinctRoleIds.stream().filter(rid -> !oldRoleIds.contains(rid)).toList();

        // 删除不再需要的角色记录
        if (!toRemove.isEmpty()) {
            memberMapper.delete(
                    new LambdaQueryWrapper<ProjectMember>()
                            .eq(ProjectMember::getProjectId, projectId)
                            .eq(ProjectMember::getUserId, userId)
                            .in(ProjectMember::getRoleId, toRemove)
            );
        }

        // 新增角色记录
        LocalDateTime now = LocalDateTime.now();
        for (Long roleId : toAdd) {
            ProjectMember member = new ProjectMember();
            member.setProjectId(projectId);
            member.setUserId(userId);
            member.setRoleId(roleId);
            member.setJoinedAt(now);
            memberMapper.insert(member);
        }

        // 失效权限缓存
        permissionService.invalidateCache(userId);

        // 审计日志
        Long currentUserId = SecurityUtils.getCurrentUserId();
        List<String> oldRoleNames = oldRoleIds.stream()
                .map(rid -> roleMapper.selectById(rid))
                .filter(java.util.Objects::nonNull)
                .map(SysRole::getName)
                .toList();
        List<String> newRoleNames = newRoles.stream().map(SysRole::getName).toList();
        projectActivityService.log(projectId, currentUserId, "change_role", userId,
                Map.of("old_role_ids", oldRoleIds, "old_role_names", String.join(", ", oldRoleNames),
                       "new_role_ids", distinctRoleIds, "new_role_names", String.join(", ", newRoleNames)));

        // 通知角色变更的用户 — 事务提交后触发
        if (!toRemove.isEmpty() || !toAdd.isEmpty()) {
            eventPublisher.publishEvent(new ProjectNotificationEvent.RoleChanged(userId, currentUserId, projectId,
                    project.getName(), String.join(", ", newRoleNames)));
        }
    }

    /**
     * 查询指定用户在项目中被分配的工单数量（用于移除前确认提示）
     */
    public int countAssignedIssues(Long projectId, Long userId) {
        Long count = issueMapper.selectCount(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getProjectId, projectId)
                        .eq(Issue::getAssigneeId, userId)
                        .isNull(Issue::getDeletedAt)
        );
        return count.intValue();
    }

    /**
     * 移除项目成员（含级联清理：清空该成员被分配的工单负责人）
     *
     * @return 受影响的工单数量
     */
    @Transactional
    public int removeMember(Long projectId, Long userId) {
        // 校验项目状态（归档项目不允许管理成员）
        Project project = getById(projectId);
        if (!"active".equals(project.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "归档项目不允许管理成员");
        }

        // 保护项目负责人：不能将负责人从成员中移除
        if (userId.equals(project.getLeadId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不能移除项目负责人。请先在项目设置中转让负责人后再移除该成员。");
        }

        // 保护最后一个项目管理员
        List<ProjectMember> admins = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getRoleId, PROJECT_ADMIN_ROLE_ID)
        );
        // 如果该用户拥有 project_admin 角色，检查是否是唯一的 admin
        boolean userHasAdmin = admins.stream().anyMatch(a -> a.getUserId().equals(userId));
        long otherAdminCount = admins.stream().filter(a -> !a.getUserId().equals(userId)).count();
        if (userHasAdmin && otherAdminCount == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能移除项目中最后一个管理员");
        }

        // === 级联处理：清空该用户在此项目中所有工单的 assignee_id ===
        List<Issue> assignedIssues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .select(Issue::getId)
                        .eq(Issue::getProjectId, projectId)
                        .eq(Issue::getAssigneeId, userId)
                        .isNull(Issue::getDeletedAt)
        );

        int affectedCount = assignedIssues.size();
        if (affectedCount > 0) {
            Long currentUserId = SecurityUtils.getCurrentUserId();
            LocalDateTime now = LocalDateTime.now();

            // 批量更新 assignee_id 为 null
            issueMapper.update(null,
                    new LambdaUpdateWrapper<Issue>()
                            .eq(Issue::getProjectId, projectId)
                            .eq(Issue::getAssigneeId, userId)
                            .isNull(Issue::getDeletedAt)
                            .set(Issue::getAssigneeId, null)
                            .set(Issue::getUpdatedAt, now)
                            .set(Issue::getUpdatedBy, currentUserId)
            );

            // 批量插入活动日志
            List<IssueActivity> activities = assignedIssues.stream().map(issue -> {
                IssueActivity activity = new IssueActivity();
                activity.setIssueId(issue.getId());
                activity.setUserId(currentUserId);
                activity.setAction("field_change");
                activity.setFieldName("assignee_id");
                activity.setOldValue(userId.toString());
                activity.setNewValue(null);
                activity.setDetail("{\"reason\":\"member_removed\"}");
                activity.setCreatedAt(now);
                return activity;
            }).toList();
            com.baomidou.mybatisplus.extension.toolkit.Db.saveBatch(activities);
        }

        // 删除成员记录
        memberMapper.delete(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId)
        );
        permissionService.invalidateCache(userId);
        // 失效项目列表缓存
        redisTemplate.delete(ACCESSIBLE_PROJECTS_CACHE_PREFIX + userId);

        // 审计日志
        Long operatorId = SecurityUtils.getCurrentUserId();
        Map<String, Object> detailMap = affectedCount > 0
                ? Map.of("affected_issue_count", affectedCount)
                : null;
        projectActivityService.log(projectId, operatorId, "remove_member", userId, detailMap);

        // 通知被移除的用户 — 事务提交后触发
        eventPublisher.publishEvent(new ProjectNotificationEvent.MemberRemoved(userId, operatorId, projectId, project.getName()));

        return affectedCount;
    }

    // ========== 项目成员校验（数据隔离核心方法） ==========

    /**
     * 校验用户是否为项目成员。系统管理员不受限制。
     * 如果不是成员且不是系统管理员，抛出 403 异常。
     *
     * @param userId    当前用户 ID
     * @param projectId 目标项目 ID
     */
    public void assertProjectMember(Long userId, Long projectId) {
        if (userId == null || projectId == null) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, "无权访问该项目");
        }
        // 系统管理员跳过校验
        if (permissionService.isSystemAdmin(userId)) {
            return;
        }
        // 检查是否为项目成员
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId)
        );
        if (count == 0) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, "无权访问该项目");
        }
    }

    /**
     * 校验项目是否处于 active 状态。归档项目不允许写操作。
     *
     * @param projectId 目标项目 ID
     * @throws BusinessException 如果项目已归档
     */
    public void assertProjectActive(Long projectId) {
        if (projectId == null) return;
        Project project = getById(projectId);
        if (!"active".equals(project.getStatus())) {
            throw new BusinessException(ErrorCode.PROJECT_ARCHIVED, "归档项目不允许此操作");
        }
    }

    /**
     * 判断用户是否为项目成员（不抛异常版本）。系统管理员返回 true。
     */
    public boolean isProjectMember(Long userId, Long projectId) {
        if (userId == null || projectId == null) {
            return false;
        }
        if (permissionService.isSystemAdmin(userId)) {
            return true;
        }
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId)
        );
        return count > 0;
    }

    /**
     * 获取用户可访问的所有项目 ID 列表。系统管理员返回 null（表示不限制）。
     * 包含：成员项目 ID + internal/public 项目 ID。
     * 结果缓存在 Redis 中（TTL 30s），避免同一请求内多次查库。
     */
    public List<Long> getAccessibleProjectIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        if (permissionService.isSystemAdmin(userId)) {
            return null; // null 表示无限制
        }

        // 短 TTL Redis 缓存（30s），减少同一用户短时间内重复查库
        String cacheKey = ACCESSIBLE_PROJECTS_CACHE_PREFIX + userId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if ("[]".equals(cached)) {
                return List.of();
            }
            return java.util.Arrays.stream(cached.split(",")).map(Long::parseLong).toList();
        }

        // 成员项目
        List<Long> memberProjectIds = memberMapper.selectProjectIdsByUserId(userId);

        // internal/public 项目
        List<Long> visibleProjectIds = projectMapper.selectProjectIdsByVisibility(List.of("internal", "public"));

        // 合并去重
        Set<Long> allIds = new java.util.LinkedHashSet<>(memberProjectIds);
        allIds.addAll(visibleProjectIds);
        List<Long> projectIds = new java.util.ArrayList<>(allIds);

        // 原子写入（set 自带 TTL，即使并发重复写入也只是覆盖相同值）
        String value = projectIds.isEmpty() ? "[]" : projectIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        redisTemplate.opsForValue().set(cacheKey, value, java.time.Duration.ofSeconds(30));

        return projectIds;
    }

    /**
     * 递增 Issue 序号并返回新序号。
     * 使用 FOR UPDATE 锁防止并发冲突。
     * 如果发现实际 max 序号高于项目记录的 sequence（数据不一致），自动校正。
     */
    @Transactional
    public int nextIssueSequence(Long projectId) {
        // 使用 FOR UPDATE 悲观锁锁定项目行，防止并发生成重复序号
        Project project = projectMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Project>()
                        .eq(Project::getId, projectId)
                        .last("FOR UPDATE")
        );
        if (project == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }

        int currentSeq = project.getIssueSequence();

        // 查询数据库中该项目实际的最大序号，防止 sequence 落后导致唯一约束冲突
        Integer actualMax = projectMapper.selectMaxIssueSequence(projectId);
        if (actualMax != null && actualMax > currentSeq) {
            currentSeq = actualMax;
        }

        int next = currentSeq + 1;
        project.setIssueSequence(next);
        projectMapper.updateById(project);
        return next;
    }

    // ========== 项目删除 ==========

    /**
     * 删除前预检查 — 返回受影响数据量供前端确认弹窗展示
     */
    public ProjectDeletePreCheckVO preCheckDelete(Long projectId) {
        Project project = getById(projectId);

        ProjectDeletePreCheckVO vo = new ProjectDeletePreCheckVO();
        vo.setProjectName(project.getName());
        vo.setProjectKey(project.getKey());

        // 工单总数
        long issueCount = issueMapper.selectCount(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getProjectId, projectId)
                        .isNull(Issue::getDeletedAt));
        vo.setIssueCount((int) issueCount);

        // 未关闭工单数
        List<IssueStatus> statuses = issueStatusMapper.selectList(null);
        Set<Long> doneStatusIds = statuses.stream()
                .filter(s -> "done".equals(s.getCategory()) || "cancelled".equals(s.getCategory()))
                .map(IssueStatus::getId)
                .collect(java.util.stream.Collectors.toSet());
        long openIssueCount = issueMapper.selectCount(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getProjectId, projectId)
                        .isNull(Issue::getDeletedAt)
                        .notIn(!doneStatusIds.isEmpty(), Issue::getStatusId, doneStatusIds));
        vo.setOpenIssueCount((int) openIssueCount);

        // Sprint 数量
        long sprintCount = sprintMapper.selectCount(
                new LambdaQueryWrapper<Sprint>()
                        .eq(Sprint::getProjectId, projectId));
        vo.setSprintCount((int) sprintCount);

        // 成员数量
        long memberCount = memberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId));
        vo.setMemberCount((int) memberCount);

        // 判断是否可删除
        vo.setDeletable(true);
        vo.setReason(null);

        return vo;
    }

    /**
     * 删除项目 — 级联删除所有关联数据（物理删除）
     *
     * 流程参考 OpenProject DeleteService：
     * 1. 通知所有项目成员
     * 2. 失效所有成员的权限缓存
     * 3. 清理 MinIO 中的附件物理文件（防止存储泄漏）
     * 4. 物理删除项目（FK CASCADE 自动清理 issue/sprint/member/tag 等）
     *
     * @param projectId         项目 ID
     * @param confirmProjectKey 前端传入的项目 Key 用于二次确认
     */
    @Transactional
    public void deleteProject(Long projectId, String confirmProjectKey) {
        Project project = getById(projectId);

        // 二次校验：前端传入的 key 必须匹配
        if (!project.getKey().equalsIgnoreCase(confirmProjectKey)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目标识不匹配，请输入正确的项目标识确认删除");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 1. 查询所有成员用于通知和缓存清理
        List<ProjectMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId));

        List<Long> memberUserIds = members.stream()
                .map(ProjectMember::getUserId)
                .distinct()
                .toList();

        // 2. 失效所有成员的权限缓存和项目列表缓存
        for (Long memberId : memberUserIds) {
            permissionService.invalidateCache(memberId);
            redisTemplate.delete(ACCESSIBLE_PROJECTS_CACHE_PREFIX + memberId);
        }

        // 3. 通知所有成员（强制通知，不受偏好控制——不可逆操作）— 事务提交后触发
        eventPublisher.publishEvent(new ProjectNotificationEvent.ProjectDeleted(projectId, memberUserIds, currentUserId,
                project.getName(), project.getKey()));

        // 4. 清理 MinIO 中的附件文件（必须在 DB 删除之前，因为 CASCADE 会删除 issue_attachment 记录）
        cleanupProjectAttachments(projectId);

        // 5. 物理删除项目（FK CASCADE 自动删除所有关联数据）
        projectMapper.deleteById(projectId);
    }

    /**
     * 清理项目下所有工单附件的 MinIO 物理文件。
     * <p>
     * 容错策略：单个文件删除失败不阻塞整体流程，仅记录警告日志。
     * 参考 OpenProject DeleteService#destroy_all_work_packages 的思路：
     * 在 DB 记录被 CASCADE 删除之前，先清理外部存储资源。
     */
    private void cleanupProjectAttachments(Long projectId) {
        List<String> filePaths = issueAttachmentMapper.selectFilePathsByProjectId(projectId);
        if (filePaths == null || filePaths.isEmpty()) {
            return;
        }

        log.info("Cleaning up {} MinIO attachments for project {}", filePaths.size(), projectId);

        int successCount = 0;
        int failCount = 0;
        for (String filePath : filePaths) {
            try {
                minioService.delete(filePath);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.warn("Failed to delete MinIO object during project cleanup: {}", filePath, e);
            }
        }

        if (failCount > 0) {
            log.warn("Project {} attachment cleanup: {} succeeded, {} failed (orphaned in MinIO)",
                    projectId, successCount, failCount);
        } else {
            log.info("Project {} attachment cleanup completed: {} files deleted", projectId, successCount);
        }
    }

    /**
     * 获取项目概览统计数据
     */
    public ProjectStatisticsVO getProjectStatistics(Long projectId) {
        var vo = new ProjectStatisticsVO();

        // 1. 查询各状态工单数量（聚合查询，不加载全量数据）
        List<IssueStatus> allStatuses = issueStatusMapper.selectList(null);
        Map<Long, IssueStatus> statusMap = allStatuses.stream()
                .collect(java.util.stream.Collectors.toMap(IssueStatus::getId, s -> s));

        // 按状态分组统计
        LambdaQueryWrapper<Issue> baseWrapper = new LambdaQueryWrapper<Issue>()
                .eq(Issue::getProjectId, projectId)
                .isNull(Issue::getDeletedAt);
        Long totalCount = issueMapper.selectCount(baseWrapper);
        int total = totalCount != null ? totalCount.intValue() : 0;

        // 统计各状态数量（用 selectMaps 做 GROUP BY）
        Map<Long, Integer> statusCountMap = new java.util.HashMap<>();
        if (total > 0) {
            var groupWrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Issue>();
            groupWrapper.select("status_id", "count(*) as cnt")
                        .eq("project_id", projectId)
                        .isNull("deleted_at")
                        .groupBy("status_id");
            List<Map<String, Object>> groupResults = issueMapper.selectMaps(groupWrapper);
            for (Map<String, Object> row : groupResults) {
                Long statusId = ((Number) row.get("status_id")).longValue();
                int cnt = ((Number) row.get("cnt")).intValue();
                statusCountMap.put(statusId, cnt);
            }
        }

        // 计算已关闭/未关闭
        int closed = 0;
        for (Map.Entry<Long, Integer> entry : statusCountMap.entrySet()) {
            IssueStatus status = statusMap.get(entry.getKey());
            if (status != null && Boolean.TRUE.equals(status.getIsClosed())) {
                closed += entry.getValue();
            }
        }
        int open = total - closed;
        double completionRate = total > 0 ? Math.round((closed * 100.0 / total) * 10) / 10.0 : 0;

        vo.setTotalIssues(total);
        vo.setOpenIssues(open);
        vo.setClosedIssues(closed);
        vo.setCompletionRate(completionRate);

        // 4. 本周新建/关闭（用 COUNT 查询）
        LocalDateTime weekStart = java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay();

        Long createdCount = issueMapper.selectCount(new LambdaQueryWrapper<Issue>()
                .eq(Issue::getProjectId, projectId)
                .isNull(Issue::getDeletedAt)
                .ge(Issue::getCreatedAt, weekStart));
        vo.setCreatedThisWeek(createdCount != null ? createdCount.intValue() : 0);

        Long closedCount = issueMapper.selectCount(new LambdaQueryWrapper<Issue>()
                .eq(Issue::getProjectId, projectId)
                .isNull(Issue::getDeletedAt)
                .ge(Issue::getResolvedAt, weekStart));
        vo.setClosedThisWeek(closedCount != null ? closedCount.intValue() : 0);

        // 5. 状态分布
        List<ProjectStatisticsVO.StatusDistribution> distributions = statusCountMap.entrySet().stream()
                .map(entry -> {
                    IssueStatus status = statusMap.get(entry.getKey());
                    if (status == null) return null;
                    var dist = new ProjectStatisticsVO.StatusDistribution();
                    dist.setStatusId(entry.getKey().toString());
                    dist.setStatusName(status.getName());
                    dist.setStatusColor(status.getColor());
                    dist.setCategory(status.getCategory());
                    dist.setClosed(Boolean.TRUE.equals(status.getIsClosed()));
                    dist.setCount(entry.getValue());
                    return dist;
                })
                .filter(Objects::nonNull)
                .sorted((a, b) -> Integer.compare(b.getCount(), a.getCount()))
                .toList();

        vo.setStatusDistribution(distributions);

        // 6. 当前活跃 Sprint
        LambdaQueryWrapper<Sprint> sprintWrapper = new LambdaQueryWrapper<>();
        sprintWrapper.eq(Sprint::getProjectId, projectId)
                     .eq(Sprint::getStatus, com.trackflow.sprint.entity.SprintStatus.ACTIVE);
        Sprint activeSprint = sprintMapper.selectOne(sprintWrapper);

        if (activeSprint != null) {
            var sprintInfo = new ProjectStatisticsVO.ActiveSprintInfo();
            sprintInfo.setId(activeSprint.getId().toString());
            sprintInfo.setName(activeSprint.getName());
            sprintInfo.setStartDate(activeSprint.getStartDate() != null ? activeSprint.getStartDate().toString() : null);
            sprintInfo.setEndDate(activeSprint.getEndDate() != null ? activeSprint.getEndDate().toString() : null);

            // Sprint 中的工单统计（用 COUNT 代替 selectList）
            Long sprintTotal = issueMapper.selectCount(new LambdaQueryWrapper<Issue>()
                    .eq(Issue::getProjectId, projectId)
                    .eq(Issue::getSprintId, activeSprint.getId())
                    .isNull(Issue::getDeletedAt));
            sprintInfo.setTotalIssues(sprintTotal != null ? sprintTotal.intValue() : 0);

            // Sprint 中已关闭工单（获取所有 closed 状态 ID，用 IN 查询）
            List<Long> closedStatusIds = allStatuses.stream()
                    .filter(s -> Boolean.TRUE.equals(s.getIsClosed()))
                    .map(IssueStatus::getId)
                    .toList();
            if (!closedStatusIds.isEmpty()) {
                Long sprintCompleted = issueMapper.selectCount(new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getProjectId, projectId)
                        .eq(Issue::getSprintId, activeSprint.getId())
                        .isNull(Issue::getDeletedAt)
                        .in(Issue::getStatusId, closedStatusIds));
                sprintInfo.setCompletedIssues(sprintCompleted != null ? sprintCompleted.intValue() : 0);
            } else {
                sprintInfo.setCompletedIssues(0);
            }

            // 剩余天数
            if (activeSprint.getEndDate() != null) {
                long remaining = java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.now(), activeSprint.getEndDate());
                sprintInfo.setRemainingDays((int) Math.max(0, remaining));
            }

            vo.setActiveSprint(sprintInfo);
        }

        return vo;
    }

    /**
     * 更新项目 settings JSONB 中的指定字段。
     */
    @Transactional
    public void updateProjectSetting(Long projectId, String key, Object value) {
        Project project = getById(projectId);
        String existingSettings = project.getSettings();
        try {
            var root = (existingSettings != null && !existingSettings.isBlank())
                    ? (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(existingSettings)
                    : objectMapper.createObjectNode();
            if (value == null) {
                root.remove(key);
            } else if (value instanceof Long) {
                root.put(key, (Long) value);
            } else if (value instanceof Integer) {
                root.put(key, (Integer) value);
            } else if (value instanceof Boolean) {
                root.put(key, (Boolean) value);
            } else {
                root.put(key, String.valueOf(value));
            }
            project.setSettings(objectMapper.writeValueAsString(root));
        } catch (Exception e) {
            log.warn("更新项目 settings 失败，projectId={}, key={}", projectId, key, e);
            return;
        }
        projectMapper.updateById(project);
    }

    /**
     * 从项目 settings JSONB 中读取指定 key 的 Long 值。
     * 如果不存在或解析失败，返回 null。
     */
    public Long getProjectSettingAsLong(Long projectId, String key) {
        Project project = getById(projectId);
        String settingsJson = project.getSettings();
        if (settingsJson == null || settingsJson.isBlank() || "{}".equals(settingsJson)) {
            return null;
        }
        try {
            var node = objectMapper.readTree(settingsJson);
            var valueNode = node.get(key);
            if (valueNode == null || valueNode.isNull()) return null;
            return valueNode.asLong();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 检查项目是否启用了时间追踪功能。
     * 默认启用（兼容已有项目）。
     */
    public boolean isTimeTrackingEnabled(Long projectId) {
        Project project = getById(projectId);
        String settingsJson = project.getSettings();
        if (settingsJson == null || settingsJson.isBlank() || "{}".equals(settingsJson)) {
            return true; // 默认启用
        }
        try {
            var node = objectMapper.readTree(settingsJson);
            var enabledNode = node.get("timeTrackingEnabled");
            if (enabledNode == null || enabledNode.isNull()) return true;
            return enabledNode.asBoolean(true);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 更新项目时间追踪启用/禁用设置。
     */
    @Transactional
    public void updateTimeTrackingEnabled(Long projectId, boolean enabled) {
        updateProjectSetting(projectId, "timeTrackingEnabled", enabled);
    }
}
