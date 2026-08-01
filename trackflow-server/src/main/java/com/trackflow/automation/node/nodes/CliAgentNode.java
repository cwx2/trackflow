package com.trackflow.automation.node.nodes;

import com.trackflow.automation.execution.ExecutionContext;
import com.trackflow.automation.execution.SseNotifier;
import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
        int    timeout  = ((Number) config.getOrDefault("timeout", 2400)).intValue();
        String prompt   = String.valueOf(inputs.getOrDefault("prompt",  ""));
        String workDir  = String.valueOf(inputs.getOrDefault("workDir", System.getProperty("user.dir")));

        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        if (!args.isBlank()) {
            for (String a : args.split("\\s+")) {
                if (!a.isBlank()) cmd.add(a);
            }
        }
        if (!prompt.isBlank()) { cmd.add("--prompt"); cmd.add(prompt); }

        log.info("CliAgentNode executing: {}, workDir={}", cmd, workDir);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(workDir));
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new NodeExecutionException(node.id(), "启动命令失败: " + e.getMessage(), e);
        }

        StringBuilder fullOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                fullOutput.append(line).append("\n");
                ctx.sendSseEvent(SseNotifier.nodeStreaming(node.id(), line + "\n"));
                if (ctx.isCancelled()) {
                    process.destroyForcibly();
                    throw new NodeExecutionException(node.id(), "执行被取消");
                }
            }
        } catch (IOException e) {
            throw new NodeExecutionException(node.id(), "读取输出失败: " + e.getMessage(), e);
        }

        boolean finished;
        try {
            finished = process.waitFor(timeout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new NodeExecutionException(node.id(), "执行被中断");
        }

        if (!finished) {
            process.destroyForcibly();
            throw new NodeExecutionException(node.id(), "执行超时（" + timeout + "秒）");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new NodeExecutionException(node.id(),
                "命令执行失败，exitCode=" + exitCode + "\n" + fullOutput);
        }

        return Map.of("output", fullOutput.toString(), "exitCode", exitCode);
    }
}
