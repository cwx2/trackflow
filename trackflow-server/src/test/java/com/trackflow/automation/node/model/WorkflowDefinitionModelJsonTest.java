package com.trackflow.automation.node.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowDefinitionModelJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsExplicitCanvasPortMetadataAsPartOfTheWorkflowContract() throws Exception {
        String definitionJson = """
                {
                  "globalVariables": {},
                  "nodes": [{
                    "id": "search",
                    "type": "trackflow-issue-search",
                    "position": { "x": 100, "y": 100 },
                    "nodeMeta": { "title": "查找待办需求", "icon": "🔎", "description": "", "color": "#0ea5e9", "category": "TrackFlow" },
                    "inputs": [{
                      "name": "savedQueryId", "label": "完整筛选", "valueType": "number",
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

        assertEquals("savedQueryId", definition.nodes().getFirst().inputs().getFirst().name());
        assertEquals("完整筛选", definition.nodes().getFirst().inputs().getFirst().label());
        assertEquals(true, definition.nodes().getFirst().inputs().getFirst().optional());
        assertEquals("issues", definition.nodes().getFirst().outputs().getFirst().name());
        assertEquals("需求列表", definition.nodes().getFirst().outputs().getFirst().label());
        assertEquals("TrackFlow", definition.nodes().getFirst().nodeMeta().category());
    }

    @Test
    void rejectsFieldsOutsideTheWorkflowContract() {
        String definitionJson = """
                { "globalVariables": {}, "nodes": [], "edges": [], "legacyPatch": true }
                """;

        assertThrows(Exception.class,
                () -> objectMapper.readValue(definitionJson, WorkflowDefinitionModel.class));
    }

    @Test
    void preservesReferenceDiscriminatorAsPartOfInputValueContract() throws Exception {
        String definitionJson = """
                {
                  "globalVariables": {},
                  "nodes": [{
                    "id": "start", "type": "start", "position": { "x": 0, "y": 0 },
                    "nodeMeta": { "title": "Start", "icon": "", "description": "", "color": "#000", "category": "特殊节点" },
                    "inputs": [], "outputs": [], "config": {}
                  }, {
                    "id": "end", "type": "end", "position": { "x": 1, "y": 1 },
                    "nodeMeta": { "title": "End", "icon": "", "description": "", "color": "#000", "category": "特殊节点" },
                    "inputs": [{
                      "name": "result", "label": "result", "valueType": "any", "required": false,
                      "description": "", "optional": false,
                      "value": { "type": "ref", "nodeId": "start", "outputName": "trigger" }
                    }], "outputs": [], "config": {}
                  }],
                  "edges": []
                }
                """;

        WorkflowDefinitionModel definition = objectMapper.readValue(definitionJson, WorkflowDefinitionModel.class);
        VariableRef reference = (VariableRef) definition.nodes().get(1).inputs().getFirst().value();
        assertEquals("ref", reference.type());
    }
}
