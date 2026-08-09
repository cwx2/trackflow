package com.trackflow.automation.node;

import com.trackflow.automation.execution.ExecutionContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 进程型节点共用的执行边界。
 *
 * <p>必须在独立线程持续消费 stdout：若主线程先 readLine 再 waitFor，
 * 无输出的长进程永远不会进入超时判断。这里同时处理超时、取消、输出上限和流式回调。</p>
 */
public final class ProcessExecutionSupport {
    private static final int MAX_OUTPUT_CHARS = 1_000_000;

    private ProcessExecutionSupport() {}

    public record Result(int exitCode, String output) {}

    public static Result run(ProcessBuilder processBuilder, Duration timeout,
                             ExecutionContext context, String nodeId,
                             Consumer<String> outputConsumer) throws NodeExecutionException {
        Process process;
        try {
            processBuilder.redirectErrorStream(true);
            process = processBuilder.start();
        } catch (IOException exception) {
            throw new NodeExecutionException(nodeId, "启动进程失败: " + exception.getMessage(), exception);
        }

        StringBuilder output = new StringBuilder();
        AtomicReference<IOException> readFailure = new AtomicReference<>();
        Thread reader = new Thread(() -> copyOutput(process, output, readFailure, outputConsumer),
                "automation-process-output-" + nodeId);
        reader.setDaemon(true);
        reader.start();

        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                if (context != null && context.isCancelled()) {
                    process.destroyForcibly();
                    waitForReader(reader);
                    throw new NodeExecutionException(nodeId, "执行被取消");
                }
                if (System.nanoTime() >= deadline) {
                    process.destroyForcibly();
                    waitForReader(reader);
                    throw new NodeExecutionException(nodeId, "执行超时（" + timeout.toSeconds() + "秒）");
                }
            }
            waitForReader(reader);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new NodeExecutionException(nodeId, "执行被中断", exception);
        }

        if (readFailure.get() != null) {
            throw new NodeExecutionException(nodeId, "读取进程输出失败: " + readFailure.get().getMessage(), readFailure.get());
        }
        synchronized (output) {
            return new Result(process.exitValue(), output.toString());
        }
    }

    private static void copyOutput(Process process, StringBuilder output,
                                   AtomicReference<IOException> readFailure,
                                   Consumer<String> outputConsumer) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (output) {
                    int remaining = MAX_OUTPUT_CHARS - output.length();
                    if (remaining > 0) {
                        output.append(line, 0, Math.min(line.length(), remaining)).append('\n');
                    }
                }
                if (outputConsumer != null) outputConsumer.accept(line + "\n");
            }
        } catch (IOException exception) {
            readFailure.set(exception);
        }
    }

    private static void waitForReader(Thread reader) throws InterruptedException {
        reader.join(2_000);
    }
}
