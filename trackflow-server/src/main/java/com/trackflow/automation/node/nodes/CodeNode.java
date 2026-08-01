package com.trackflow.automation.node.nodes;

import com.trackflow.automation.execution.ExecutionContext;
import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 代码节点：执行 Shell 或 Python 脚本，支持输入变量注入为环境变量
 */
@Component
public class CodeNode implements NodeDefinition, NodeExecutor {

    @Override public String getType()        { return "code"; }
    @Override public String getTitle()       { return "代码"; }
    @Override public String getIcon()        { return "</>"; }
    @Override public String getColor()       { return "#7c3aed"; }
    @Override public String getDescription() { return "执行 Shell/Python 脚本，处理自定义逻辑"; }
    @Override public String getCategory()    { return "业务逻辑"; }

    @Override
    public List<InputPortDef> getInputPorts() {
        return List.of(new InputPortDef("input", "object", false, "传入脚本的数据，作为环境变量注入"));
    }

    @Override
    public List<OutputPortDef> getOutputPorts() {
        return List.of(
            new OutputPortDef("result",   "string", "脚本 stdout 输出内容"),
            new OutputPortDef("exitCode", "number", "退出码，0 表示成功")
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, WorkflowNodeModel node, ExecutionContext ctx)
            throws NodeExecutionException {
        Map<String, Object> config = node.config() != null ? node.config() : Map.of();
        String language = (String) config.getOrDefault("language", "shell");
        String script   = (String) config.getOrDefault("script", "echo hello");
        int timeoutSec  = ((Number) config.getOrDefault("timeout", 30)).intValue();

        try {
            List<String> cmd = buildCommand(language, script);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);

            // 将输入数据注入为环境变量
            if (inputs.get("input") instanceof Map<?,?> inputMap) {
                inputMap.forEach((k, v) -> {
                    String envKey = "INPUT_" + k.toString().toUpperCase().replaceAll("[^A-Z0-9_]", "_");
                    pb.environment().put(envKey, v != null ? v.toString() : "");
                });
            }

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new NodeExecutionException(node.id(), "脚本执行超时（" + timeoutSec + "秒）");
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new NodeExecutionException(node.id(), "脚本退出码非零: " + exitCode + "\n" + output);
            }
            return Map.of("result", output.toString().trim(), "exitCode", exitCode);
        } catch (NodeExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new NodeExecutionException(node.id(), "脚本执行失败: " + e.getMessage());
        }
    }

    private List<String> buildCommand(String language, String script) {
        List<String> cmd = new ArrayList<>();
        if ("python".equalsIgnoreCase(language)) {
            cmd.add("python3");
            cmd.add("-c");
        } else {
            cmd.add("sh");
            cmd.add("-c");
        }
        cmd.add(script);
        return cmd;
    }
}
