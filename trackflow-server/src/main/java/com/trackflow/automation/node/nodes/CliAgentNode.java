package com.trackflow.automation.node.nodes;

import com.trackflow.automation.execution.ExecutionContext;
import com.trackflow.automation.execution.SseNotifier;
import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.ProcessExecutionSupport;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.Duration;

/**
 * CLI Agent 节点执行器
 * 调用 kiro-cli（或其他命令行工具），实时流式推送 stdout 到 SSE
 */
@Component
public class CliAgentNode implements NodeDefinition, NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(CliAgentNode.class);

    @Override public String getType()        { return "cli-agent"; }
    @Override public String getTitle()       { return "CLI Agent"; }
    @Override public String getIcon()        { return "🤖"; }
    @Override public String getColor()       { return "#6366f1"; }
    @Override public String getDescription() { return "执行 AI 命令行任务"; }
    @Override public List<InputPortDef> getInputPorts() {
        return List.of(
            new InputPortDef("prompt",  "string", true,  "提示词"),
            new InputPortDef("workDir", "string", false, "工作目录")
        );
    }
    @Override public List<OutputPortDef> getOutputPorts() {
        return List.of(
            new OutputPortDef("output",   "string", "命令行完整输出"),
            new OutputPortDef("exitCode", "number", "退出码，0=成功")
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs,
                                       WorkflowNodeModel node,
                                       ExecutionContext ctx) throws NodeExecutionException {
        Map<String, Object> config  = node.config() != null ? node.config() : Map.of();
        String command  = String.valueOf(config.getOrDefault("command", "kiro-cli"));
        String args     = String.valueOf(config.getOrDefault("args",    "--no-interactive"));
        int    timeout  = integerConfig(config.get("timeout"), 2400, 1, 7200, node.id(), "timeout");
        String prompt   = String.valueOf(inputs.getOrDefault("prompt",  ""));
        Object workDirValue = inputs.get("workDir");
        String workDir = workDirValue == null || workDirValue.toString().isBlank()
                ? System.getProperty("user.dir") : workDirValue.toString();
        if (command.isBlank()) throw new NodeExecutionException(node.id(), "命令不能为空");

        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        if (!args.isBlank()) {
            for (String a : args.split("\\s+")) {
                if (!a.isBlank()) cmd.add(a);
            }
        }
        if (!prompt.isBlank()) { cmd.add("--prompt"); cmd.add(prompt); }

        log.info("CliAgentNode executing: command={}, argumentCount={}, workDir={}",
                command, cmd.size() - 1, workDir);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        File workingDirectory = new File(workDir);
        if (!workingDirectory.isDirectory()) {
            throw new NodeExecutionException(node.id(), "工作目录不存在或不是目录: " + workDir);
        }
        pb.directory(workingDirectory);
        ProcessExecutionSupport.Result result = ProcessExecutionSupport.run(pb, Duration.ofSeconds(timeout), ctx,
                node.id(), line -> ctx.sendSseEvent(SseNotifier.nodeStreaming(node.id(), line)));
        int exitCode = result.exitCode();
        if (exitCode != 0) {
            throw new NodeExecutionException(node.id(),
                "命令执行失败，exitCode=" + exitCode + "\n" + result.output());
        }

        return Map.of("output", result.output(), "exitCode", exitCode);
    }

    private int integerConfig(Object value, int fallback, int min, int max,
                              String nodeId, String name) throws NodeExecutionException {
        if (value == null) return fallback;
        try {
            int parsed = value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
            if (parsed < min || parsed > max) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new NodeExecutionException(nodeId, name + " 必须是 " + min + " 到 " + max + " 的整数");
        }
    }
}
