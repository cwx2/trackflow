package com.trackflow.automation.node;

import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.OutputPortDef;

import java.util.List;

public interface NodeDefinition {
    String getType();
    String getTitle();
    String getIcon();
    String getColor();
    String getDescription();
    default String getCategory() { return "基础节点"; }
    List<InputPortDef> getInputPorts();
    List<OutputPortDef> getOutputPorts();
}
