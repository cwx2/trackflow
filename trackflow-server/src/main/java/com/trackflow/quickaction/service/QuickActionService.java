package com.trackflow.quickaction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.integration.service.EmailSendService;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.issue.service.IssueService;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.quickaction.converter.QuickActionConverter;
import com.trackflow.quickaction.dto.ExecuteQuickActionDTO;
import com.trackflow.quickaction.dto.SaveMailTemplateDTO;
import com.trackflow.quickaction.dto.SaveQuickActionDefinitionDTO;
import com.trackflow.quickaction.entity.MailTemplate;
import com.trackflow.quickaction.entity.QuickActionDefinition;
import com.trackflow.quickaction.entity.QuickActionLog;
import com.trackflow.quickaction.mapper.MailTemplateMapper;
import com.trackflow.quickaction.mapper.QuickActionDefinitionMapper;
import com.trackflow.quickaction.mapper.QuickActionLogMapper;
import com.trackflow.quickaction.vo.MailTemplateVO;
import com.trackflow.quickaction.vo.QuickActionDefinitionVO;
import com.trackflow.quickaction.vo.QuickActionExecutionResultVO;
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysRoleMapper;
import com.trackflow.system.mapper.SysUserMapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuickActionService {

    private final QuickActionDefinitionMapper definitionMapper;
    private final MailTemplateMapper mailTemplateMapper;
    private final QuickActionLogMapper logMapper;
    private final QuickActionConverter converter;
    private final IssueService issueService;
    private final IssueStatusMapper issueStatusMapper;
    private final EmailSendService emailSendService;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ObjectMapper objectMapper;

    public List<QuickActionDefinitionVO> getAvailableActions(Long issueId) {
        Issue issue = issueService.getById(issueId);
        if (issue == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工单不存在");
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Set<String> userRoles = getUserProjectRoles(currentUserId, issue.getProjectId());
        IssueStatus currentStatus = issueStatusMapper.selectById(issue.getStatusId());
        String statusName = currentStatus != null ? currentStatus.getName() : "";
        List<QuickActionDefinition> defs = getEffectiveDefinitions(issue.getProjectId());
        return converter.toDefinitionVOList(defs.stream()
                .filter(d -> isVisible(d, userRoles, statusName)).toList());
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public QuickActionExecutionResultVO execute(Long issueId, String actionKey, ExecuteQuickActionDTO dto) {
        Issue issue = issueService.getById(issueId);
        if (issue == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工单不存在");
        Long uid = SecurityUtils.getCurrentUserId();
        QuickActionDefinition def = findDefinition(issue.getProjectId(), actionKey);
        if (def == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "快捷动作不存在");
        Set<String> roles = getUserProjectRoles(uid, issue.getProjectId());
        IssueStatus curSt = issueStatusMapper.selectById(issue.getStatusId());
        if (!isVisible(def, roles, curSt != null ? curSt.getName() : ""))
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "不允许执行此动作");

        String comment = buildComment(def, dto);
        var created = issueService.addComment(issueId, comment);
        String stBefore = curSt != null ? curSt.getName() : null;
        String stAfter = null;
        if (def.getStatusTransitionTo() != null && !def.getStatusTransitionTo().isBlank())
            stAfter = doTransition(issue, def.getStatusTransitionTo(), dto);

        boolean mailSent = false; String mailErr = null;
        if ("comment_and_mail".equals(dto.getResultType()) && dto.getMailTemplateId() != null) {
            try { doSendMail(issue, def, dto, uid); mailSent = true; }
            catch (Exception e) { mailErr = e.getMessage(); log.error("[QA] mail fail", e); }
        }

        QuickActionLog entry = new QuickActionLog();
        entry.setIssueId(issueId); entry.setActionKey(actionKey); entry.setOperatorId(uid);
        entry.setFormData(dto.getFormData()); entry.setMailTemplateId(dto.getMailTemplateId());
        entry.setResultType(dto.getResultType()); entry.setCommentId(created.getId());
        entry.setMailSent(mailSent); entry.setMailError(mailErr);
        entry.setStatusBefore(stBefore); entry.setStatusAfter(stAfter);
        entry.setCreatedAt(LocalDateTime.now());
        logMapper.insert(entry);

        if (stAfter != null) return QuickActionExecutionResultVO.successWithTransition(
                created.getId().toString(), mailSent, entry.getId().toString(), stBefore, stAfter);
        return QuickActionExecutionResultVO.success(created.getId().toString(), mailSent, entry.getId().toString());
    }

    public List<MailTemplateVO> getMailTemplates(String actionKey, Long projectId) {
        var w = new LambdaQueryWrapper<MailTemplate>();
        w.eq(MailTemplate::getActionKey, actionKey).eq(MailTemplate::getEnabled, true)
                .and(q -> q.isNull(MailTemplate::getProjectId).or().eq(MailTemplate::getProjectId, projectId))
                .orderByAsc(MailTemplate::getSortOrder);
        return converter.toMailTemplateVOList(mailTemplateMapper.selectList(w));
    }

    public List<QuickActionDefinitionVO> listDefinitions(Long projectId) {
        var w = new LambdaQueryWrapper<QuickActionDefinition>();
        if (projectId != null) w.and(q -> q.isNull(QuickActionDefinition::getProjectId).or().eq(QuickActionDefinition::getProjectId, projectId));
        w.orderByAsc(QuickActionDefinition::getSortOrder);
        return converter.toDefinitionVOList(definitionMapper.selectList(w));
    }

    @Transactional
    public QuickActionDefinitionVO createDefinition(SaveQuickActionDefinitionDTO dto) {
        Long uid = SecurityUtils.getCurrentUserId();
        QuickActionDefinition e = new QuickActionDefinition();
        e.setProjectId(dto.getProjectId()); e.setActionKey(dto.getActionKey()); e.setLabel(dto.getLabel());
        e.setIcon(dto.getIcon()); e.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        e.setFormSchema(dto.getFormSchema()); e.setActions(dto.getActions()); e.setVisibility(dto.getVisibility());
        e.setStatusTransitionTo(dto.getStatusTransitionTo());
        e.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        e.setCreatedBy(uid); e.setUpdatedBy(uid); e.setCreatedAt(LocalDateTime.now()); e.setUpdatedAt(LocalDateTime.now());
        definitionMapper.insert(e);
        return converter.toDefinitionVO(e);
    }

    @Transactional
    public QuickActionDefinitionVO updateDefinition(Long id, SaveQuickActionDefinitionDTO dto) {
        QuickActionDefinition e = definitionMapper.selectById(id);
        if (e == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "动作定义不存在");
        e.setLabel(dto.getLabel()); e.setIcon(dto.getIcon());
        e.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : e.getSortOrder());
        e.setFormSchema(dto.getFormSchema()); e.setActions(dto.getActions()); e.setVisibility(dto.getVisibility());
        e.setStatusTransitionTo(dto.getStatusTransitionTo());
        e.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : e.getEnabled());
        e.setUpdatedBy(SecurityUtils.getCurrentUserId()); e.setUpdatedAt(LocalDateTime.now());
        definitionMapper.updateById(e);
        return converter.toDefinitionVO(e);
    }

    @Transactional public void deleteDefinition(Long id) { definitionMapper.deleteById(id); }

    public List<MailTemplateVO> listAllMailTemplates(Long projectId) {
        var w = new LambdaQueryWrapper<MailTemplate>();
        if (projectId != null) w.and(q -> q.isNull(MailTemplate::getProjectId).or().eq(MailTemplate::getProjectId, projectId));
        w.orderByAsc(MailTemplate::getActionKey).orderByAsc(MailTemplate::getSortOrder);
        return converter.toMailTemplateVOList(mailTemplateMapper.selectList(w));
    }

    @Transactional
    public MailTemplateVO createMailTemplate(SaveMailTemplateDTO dto) {
        Long uid = SecurityUtils.getCurrentUserId();
        MailTemplate e = new MailTemplate();
        e.setProjectId(dto.getProjectId()); e.setActionKey(dto.getActionKey()); e.setName(dto.getName());
        e.setSubjectTemplate(dto.getSubjectTemplate()); e.setBodyTemplate(dto.getBodyTemplate());
        e.setRecipientsRule(dto.getRecipientsRule());
        e.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        e.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        e.setCreatedBy(uid); e.setUpdatedBy(uid); e.setCreatedAt(LocalDateTime.now()); e.setUpdatedAt(LocalDateTime.now());
        mailTemplateMapper.insert(e);
        return converter.toMailTemplateVO(e);
    }

    @Transactional
    public MailTemplateVO updateMailTemplate(Long id, SaveMailTemplateDTO dto) {
        MailTemplate e = mailTemplateMapper.selectById(id);
        if (e == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "邮件模板不存在");
        e.setName(dto.getName()); e.setSubjectTemplate(dto.getSubjectTemplate());
        e.setBodyTemplate(dto.getBodyTemplate()); e.setRecipientsRule(dto.getRecipientsRule());
        e.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : e.getSortOrder());
        e.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : e.getEnabled());
        e.setUpdatedBy(SecurityUtils.getCurrentUserId()); e.setUpdatedAt(LocalDateTime.now());
        mailTemplateMapper.updateById(e);
        return converter.toMailTemplateVO(e);
    }

    @Transactional public void deleteMailTemplate(Long id) { mailTemplateMapper.deleteById(id); }

    // ===== Private =====

    private List<QuickActionDefinition> getEffectiveDefinitions(Long projectId) {
        var w = new LambdaQueryWrapper<QuickActionDefinition>();
        w.eq(QuickActionDefinition::getEnabled, true)
                .and(q -> q.isNull(QuickActionDefinition::getProjectId).or().eq(QuickActionDefinition::getProjectId, projectId))
                .orderByAsc(QuickActionDefinition::getSortOrder);
        List<QuickActionDefinition> all = definitionMapper.selectList(w);
        Map<String, QuickActionDefinition> map = new LinkedHashMap<>();
        for (var d : all) { if (d.getProjectId() != null) map.put(d.getActionKey(), d); else map.putIfAbsent(d.getActionKey(), d); }
        return new ArrayList<>(map.values());
    }

    private QuickActionDefinition findDefinition(Long projectId, String key) {
        var w = new LambdaQueryWrapper<QuickActionDefinition>();
        w.eq(QuickActionDefinition::getActionKey, key).eq(QuickActionDefinition::getEnabled, true).eq(QuickActionDefinition::getProjectId, projectId);
        QuickActionDefinition d = definitionMapper.selectOne(w);
        if (d != null) return d;
        var gw = new LambdaQueryWrapper<QuickActionDefinition>();
        gw.eq(QuickActionDefinition::getActionKey, key).eq(QuickActionDefinition::getEnabled, true).isNull(QuickActionDefinition::getProjectId);
        return definitionMapper.selectOne(gw);
    }

    @SuppressWarnings("unchecked")
    private boolean isVisible(QuickActionDefinition def, Set<String> userRoles, String statusName) {
        String vis = def.getVisibility();
        if (vis == null || vis.isBlank() || "{}".equals(vis)) return true;
        try {
            Map<String, Object> m = objectMapper.readValue(vis, new TypeReference<Map<String, Object>>() {});
            Object r = m.get("roles");
            if (r instanceof List<?> rl && !rl.isEmpty() && rl.stream().noneMatch(x -> userRoles.contains(String.valueOf(x)))) return false;
            Object s = m.get("issueStatuses");
            if (s instanceof List<?> sl && !sl.isEmpty() && sl.stream().noneMatch(x -> String.valueOf(x).equalsIgnoreCase(statusName))) return false;
            return true;
        } catch (Exception e) { return true; }
    }

    private Set<String> getUserProjectRoles(Long userId, Long projectId) {
        List<Long> ids = projectMemberMapper.selectRoleIdsByUserAndProject(userId, projectId);
        if (ids == null || ids.isEmpty()) return Collections.emptySet();
        return sysRoleMapper.selectBatchIds(ids).stream().map(SysRole::getCode).collect(Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    private String buildComment(QuickActionDefinition def, ExecuteQuickActionDTO dto) {
        StringBuilder sb = new StringBuilder("**[").append(def.getLabel()).append("]**\n\n");
        try {
            Map<String, Object> data = objectMapper.readValue(dto.getFormData(), new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> schema = objectMapper.readValue(def.getFormSchema(), new TypeReference<List<Map<String, Object>>>() {});
            for (var f : schema) {
                String k = String.valueOf(f.get("key")); String l = String.valueOf(f.get("label"));
                Object v = data.get(k);
                if (v != null) {
                    String d = v instanceof List<?> vl ? vl.stream().map(String::valueOf).collect(Collectors.joining(", ")) : String.valueOf(v);
                    if (!d.isBlank() && !"null".equals(d)) sb.append("**").append(l).append(":** ").append(d).append("\n");
                }
            }
        } catch (Exception e) { sb.append(dto.getFormData()); }
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private String doTransition(Issue issue, String target, ExecuteQuickActionDTO dto) {
        String actual = target;
        try {
            Map<String, Object> data = objectMapper.readValue(dto.getFormData(), new TypeReference<Map<String, Object>>() {});
            Object st = data.get("status_to");
            if (st != null) { String s = String.valueOf(st); if ("Don't Change".equalsIgnoreCase(s)) return null; if (!s.isBlank() && !"null".equals(s)) actual = s; }
        } catch (Exception e) { /* use default */ }
        var w = new LambdaQueryWrapper<IssueStatus>(); w.eq(IssueStatus::getName, actual);
        IssueStatus t = issueStatusMapper.selectOne(w);
        if (t == null) return null;
        try { issueService.transitStatus(issue.getId(), t.getId(), null); return actual; }
        catch (Exception e) { log.warn("[QA] transition failed: {}", e.getMessage()); return null; }
    }

    private void doSendMail(Issue issue, QuickActionDefinition def, ExecuteQuickActionDTO dto, Long uid) {
        if (!emailSendService.isEmailAvailable()) return;
        MailTemplate tpl = mailTemplateMapper.selectById(dto.getMailTemplateId());
        if (tpl == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "邮件模板不存在");
        Map<String, String> vars = buildVars(issue, dto, uid);
        String subj = replaceVars(tpl.getSubjectTemplate(), vars);
        String body = replaceVars(tpl.getBodyTemplate(), vars);
        for (String r : resolveRecipients(tpl, issue.getProjectId())) emailSendService.sendNotificationEmail(r, subj, body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> buildVars(Issue issue, ExecuteQuickActionDTO dto, Long uid) {
        Map<String, String> v = new HashMap<>();
        v.put("issueKey", issue.getIssueKey()); v.put("issueTitle", issue.getTitle());
        SysUser u = sysUserMapper.selectById(uid); v.put("applicant", u != null ? u.getDisplayName() : "");
        Project p = projectMapper.selectById(issue.getProjectId()); v.put("projectName", p != null ? p.getName() : "");
        try {
            Map<String, Object> data = objectMapper.readValue(dto.getFormData(), new TypeReference<Map<String, Object>>() {});
            if (data.containsKey("details")) v.put("details", String.valueOf(data.get("details")));
            if (data.containsKey("modify_type")) { Object mt = data.get("modify_type"); v.put("modifyType", mt instanceof List<?> l ? l.stream().map(String::valueOf).collect(Collectors.joining(", ")) : String.valueOf(mt)); }
            if (data.containsKey("time_spent")) v.put("timeSpent", String.valueOf(data.get("time_spent")));
            if (data.containsKey("sprint")) v.put("sprint", String.valueOf(data.get("sprint")));
            if (data.containsKey("cancellation_reason")) v.put("cancellationReason", String.valueOf(data.get("cancellation_reason")));
        } catch (Exception e) { /* skip */ }
        return v;
    }

    private String replaceVars(String tpl, Map<String, String> vars) {
        String r = tpl; for (var e : vars.entrySet()) r = r.replace("{" + e.getKey() + "}", e.getValue() != null ? e.getValue() : ""); return r;
    }

    @SuppressWarnings("unchecked")
    private List<String> resolveRecipients(MailTemplate tpl, Long projectId) {
        List<String> result = new ArrayList<>();
        try {
            Map<String, Object> rule = objectMapper.readValue(tpl.getRecipientsRule(), new TypeReference<Map<String, Object>>() {});
            String type = rule.containsKey("type") ? String.valueOf(rule.get("type")) : "";
            if ("project_coordinators".equals(type)) {
                var rw = new LambdaQueryWrapper<SysRole>(); rw.eq(SysRole::getCode, "project_admin").eq(SysRole::getRoleType, "project");
                SysRole ar = sysRoleMapper.selectOne(rw);
                if (ar != null) {
                    var mw = new LambdaQueryWrapper<ProjectMember>(); mw.eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getRoleId, ar.getId());
                    Set<Long> uids = projectMemberMapper.selectList(mw).stream().map(ProjectMember::getUserId).collect(Collectors.toSet());
                    if (!uids.isEmpty()) sysUserMapper.selectBatchIds(uids).stream().filter(u -> u.getEmail() != null && !u.getEmail().isBlank()).forEach(u -> result.add(u.getEmail()));
                }
            } else if ("explicit_emails".equals(type)) {
                Object emails = rule.get("emails"); if (emails instanceof List<?> l) l.forEach(e -> result.add(String.valueOf(e)));
            }
        } catch (Exception e) { log.warn("[QA] recipients resolve fail: {}", e.getMessage()); }
        return result;
    }
}
