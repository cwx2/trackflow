package com.trackflow.automation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.entity.AutomationWorkflow;
import com.trackflow.automation.entity.AutomationWorkflowTemplate;
import com.trackflow.automation.execution.WorkflowDefinitionValidator;
import com.trackflow.automation.mapper.AutomationWorkflowMapper;
import com.trackflow.automation.mapper.AutomationWorkflowTemplateMapper;
import com.trackflow.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutomationWorkflowTemplateServiceTest {

    private final AutomationWorkflowTemplateMapper templateMapper = mock(AutomationWorkflowTemplateMapper.class);
    private final AutomationWorkflowMapper workflowMapper = mock(AutomationWorkflowMapper.class);
    private final WorkflowDefinitionValidator definitionValidator = mock(WorkflowDefinitionValidator.class);
    private final AutomationWorkflowTemplateService service = new AutomationWorkflowTemplateService(
            templateMapper, workflowMapper, new ObjectMapper(), definitionValidator);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void clonedBuiltinTemplateUsesCreatorAsItsExecutionIdentity() {
        authenticateAs(1004L);
        AutomationWorkflowTemplate template = template(true, null);
        when(templateMapper.selectById(1L)).thenReturn(template);

        service.cloneFromTemplate(1L);

        ArgumentCaptor<AutomationWorkflow> workflow = ArgumentCaptor.forClass(AutomationWorkflow.class);
        verify(workflowMapper).insert(workflow.capture());
        assertEquals(1004L, workflow.getValue().getActorUserId());
        assertEquals("manual", workflow.getValue().getTriggerType());
        assertEquals("draft", workflow.getValue().getStatus());
    }

    @Test
    void refusesToCloneAnotherUsersPrivateTemplate() {
        authenticateAs(1004L);
        when(templateMapper.selectById(2L)).thenReturn(template(false, 2001L));

        assertThrows(BusinessException.class, () -> service.cloneFromTemplate(2L));
    }

    private void authenticateAs(Long userId) {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("user", "n/a");
        authentication.setAuthenticated(true);
        authentication.setDetails(userId);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private AutomationWorkflowTemplate template(boolean builtin, Long createdBy) {
        AutomationWorkflowTemplate template = new AutomationWorkflowTemplate();
        template.setName("可直接使用的模板");
        template.setDescription("无需额外配置");
        template.setDefinition("{\"globalVariables\":{},\"nodes\":[],\"edges\":[]}");
        template.setIsBuiltin(builtin);
        template.setCreatedBy(createdBy);
        return template;
    }
}
