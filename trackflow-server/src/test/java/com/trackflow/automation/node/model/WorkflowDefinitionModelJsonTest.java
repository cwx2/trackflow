package com.trackflow.automation.node.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowDefinitionModelJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsCanvasOnlyPortMetadataWhenStartingAnExistingWorkflow() throws Exception {
        String definitionJson = """
                {
                  "globalVariables": {},
                  "nodes": [{
                    "id": "search",
                    "type": "trackflow-issue-search",
                    "position": { "x": 100, "y": 100 },
                    "nodeMeta": { "title": "查找待办需求", "icon": "🔎", "description": "", "color": "#0ea5e9" },
                    "inputs": [{
                      "name": "projectId", "label": "项目 ID", "valueType": "number",
                      "required": false, "optional": true, "description": "", "value": null
                    }],
                    "outputs": [{
                      "name": "issues", "label": "需求列表", "valueType": "array", "description": ""
                    }],
                    "config": {}
                  }],
                  "edges": []
                }
                """;

        WorkflowDefinitionModel definition = objectMapper.readValue(definitionJson, WorkflowDefinitionModel.class);

        assertEquals("projectId", definition.nodes().getFirst().inputs().getFirst().name());
        assertEquals("issues", definition.nodes().getFirst().outputs().getFirst().name());
    }
}
