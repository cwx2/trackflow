package com.trackflow.automation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.execution.DAGBuilder;
import com.trackflow.automation.execution.WorkflowDefinitionValidator;
import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.NodeRegistry;
import com.trackflow.automation.node.model.WorkflowDefinitionModel;
import com.trackflow.automation.node.nodes.ApprovalNode;
import com.trackflow.automation.node.nodes.ConditionNode;
import com.trackflow.automation.node.nodes.DelayNode;
import com.trackflow.automation.node.nodes.EndNode;
import com.trackflow.automation.node.nodes.IssueSearchNode;
import com.trackflow.automation.node.nodes.StartNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class BuiltinTemplateMigrationContractTest {

    private static final Pattern TEMPLATE_JSON =
            Pattern.compile("\\$(pending|approval|delay)\\$(.*?)\\$\\1\\$", Pattern.DOTALL);

    @Test
    void everySeededBuiltinTemplateSatisfiesTheRealNodeContracts() throws Exception {
        StartNode start = new StartNode();
        IssueSearchNode search = new IssueSearchNode(mock(AutomationIssueFacade.class));
        ConditionNode condition = new ConditionNode();
        ApprovalNode approval = new ApprovalNode();
        DelayNode delay = new DelayNode();
        EndNode end = new EndNode();
        List<NodeDefinition> definitions = List.of(start, search, condition, approval, delay, end);
        List<NodeExecutor> executors = List.of(start, search, condition, approval, delay, end);
        WorkflowDefinitionValidator validator = new WorkflowDefinitionValidator(
                new DAGBuilder(), new NodeRegistry(definitions, executors));

        String migration = migrationText();
        Matcher matcher = TEMPLATE_JSON.matcher(migration);
        List<String> templates = new ArrayList<>();
        while (matcher.find()) {
            templates.add(matcher.group(2));
        }
        assertEquals(3, templates.size(), "内置模板种子必须恰好包含三个可用模板");

        ObjectMapper objectMapper = new ObjectMapper();
        for (String definitionJson : templates) {
            WorkflowDefinitionModel definition = objectMapper.readValue(definitionJson, WorkflowDefinitionModel.class);
            validator.validateExecutable(definition);
        }
    }

    private String migrationText() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "db/migration/V282__ensure_builtin_automation_templates.sql")) {
            assertNotNull(stream, "内置模板迁移文件必须存在");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
