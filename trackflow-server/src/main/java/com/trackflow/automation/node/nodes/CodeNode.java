package com.trackflow.automation.node.nodes;

import com.trackflow.automation.execution.ExecutionContext;
import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.ProcessExecutionSupport;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.Duration;

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
        String language = String.valueOf(config.getOrDefault("language", "shell"));
        String script   = String.valueOf(config.getOrDefault("script", "echo hello"));
        int timeoutSec  = integerConfig(config.get("timeout"), 30, node.id());

        try {
            List<String> cmd = buildCommand(language, script);
            ProcessBuilder pb = new ProcessBuilder(cmd);

            // 将输入数据注入为环境变量
            if (inputs.get("input") instanceof Map<?,?> inputMap) {
                inputMap.forEach((k, v) -> {
                    String envKey = "INPUT_" + k.toString().toUpperCase().replaceAll("[^A-Z0-9_]", "_");
                    pb.environment().put(envKey, v != null ? v.toString() : "");
                });
            }

            ProcessExecutionSupport.Result result = ProcessExecutionSupport.run(pb, Duration.ofSeconds(timeoutSec),
                    ctx, node.id(), null);
            int exitCode = result.exitCode();
            if (exitCode != 0) {
                throw new NodeExecutionException(node.id(), "脚本退出码非零: " + exitCode + "\n" + result.output());
            }
            return Map.of("result", result.output().trim(), "exitCode", exitCode);
        } catch (NodeExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new NodeExecutionException(node.id(), "脚本执行失败: " + e.getMessage());
        }
    }

    private int integerConfig(Object value, int fallback, String nodeId) throws NodeExecutionException {
        if (value == null) return fallback;
        try {
            int parsed = value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
            if (parsed < 1 || parsed > 3_600) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new NodeExecutionException(nodeId, "timeout 必须是 1 到 3600 的整数");
        }
    }

    private List<String> buildCommand(String language, String script) {
        List<String> cmd = new ArrayList<>();
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if ("python".equalsIgnoreCase(language)) {
            // Windows 默认没有 python3；使用 PATH 中的 python，Linux/macOS 保持 python3。
            cmd.add(windows ? "python" : "python3");
            cmd.add("-c");
        } else if (windows) {
            // 工作流服务当前可部署在 Windows，不能假设 sh 一定存在。
            cmd.add("powershell.exe");
            cmd.add("-NoLogo");
            cmd.add("-NoProfile");
            cmd.add("-NonInteractive");
            cmd.add("-Command");
        } else {
            cmd.add("sh");
            cmd.add("-c");
        }
        cmd.add(script);
        return cmd;
    }
}
