package com.autostartstop.util;

import com.autostartstop.Log;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class for executing shell commands with proper timeout handling,
 * environment variable support, and resource cleanup.
 * 
 * This class is thread-safe and can be used by multiple components
 * (ShellServerControlApi, ExecAction, etc.)
 */
public class CommandExecutor {
    private static final Logger logger = Log.get(CommandExecutor.class);

    /**
     * Default command timeout (60 seconds).
     */
    public static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofSeconds(60);

    public static final int MAX_OUTPUT_BUFFER_SIZE = 10000;

    private static final AtomicInteger threadCounter = new AtomicInteger(0);

    /**
     * Shared executor for command execution.
     * Uses cached thread pool suitable for blocking I/O operations.
     */
    private static final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setName("command-executor-" + threadCounter.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    private CommandExecutor() {
        // Utility class
    }

    /**
     * Executes a shell command asynchronously.
     *
     * @param command       The command to execute
     * @param operationName A name for logging purposes (e.g., "start", "stop",
     *                      "exec")
     * @param contextName   Context identifier for logging (e.g., server name,
     *                      action name)
     * @return A CompletableFuture with true if command succeeded (exit code 0),
     *         false otherwise
     */
    public static CompletableFuture<Boolean> execute(String command, String operationName, String contextName) {
        return execute(command, operationName, contextName, null, null, DEFAULT_COMMAND_TIMEOUT);
    }

    /**
     * Executes a shell command asynchronously with full options.
     *
     * @param command          The command to execute
     * @param operationName    A name for logging purposes (e.g., "start", "stop",
     *                         "exec")
     * @param contextName      Context identifier for logging (e.g., server name,
     *                         action name)
     * @param workingDirectory Optional working directory (null to use current
     *                         directory)
     * @param environment      Optional environment variables to add (null for none)
     * @param commandTimeout   Command timeout duration
     * @return A CompletableFuture with true if command succeeded (exit code 0),
     *         false otherwise
     */
    public static CompletableFuture<Boolean> execute(
            String command,
            String operationName,
            String contextName,
            String workingDirectory,
            Map<String, String> environment,
            Duration commandTimeout) {

        return CompletableFuture.supplyAsync(
                () -> executeBlocking(command, operationName, contextName, workingDirectory, environment,
                        commandTimeout),
                executor);
    }

    /**
     * Blocking execution of a shell command.
     * Should be called from within a managed executor.
     */
    private static boolean executeBlocking(
            String command,
            String operationName,
            String contextName,
            String workingDirectory,
            Map<String, String> environment,
            Duration commandTimeout) {

        long startTime = System.currentTimeMillis();
        Process process = null;

        try {
            logger.debug("{}: preparing to execute {} command", contextName, operationName);
            logger.debug("{}: command = {}", contextName, command);
            logger.debug("{}: command_timeout = {}s", contextName, commandTimeout.toSeconds());

            ProcessBuilder processBuilder = new ProcessBuilder();

            // Determine the shell based on OS
            String os = System.getProperty("os.name").toLowerCase();
            String shell;
            if (os.contains("win")) {
                processBuilder.command("cmd.exe", "/c", command);
                shell = "cmd.exe";
            } else {
                processBuilder.command("sh", "-c", command);
                shell = "sh";
            }
            logger.debug("{}: using shell '{}' on OS '{}'", contextName, shell, os);

            // Set working directory if specified
            if (workingDirectory != null && !workingDirectory.isBlank()) {
                File workDir = new File(workingDirectory);
                if (workDir.exists() && workDir.isDirectory()) {
                    processBuilder.directory(workDir);
                    logger.debug("{}: working directory set to {}", contextName, workingDirectory);
                } else {
                    logger.warn("{}: working directory '{}' does not exist or is not a directory",
                            contextName, workingDirectory);
                }
            }

            // Add environment variables if specified
            if (environment != null && !environment.isEmpty()) {
                Map<String, String> env = processBuilder.environment();
                for (Map.Entry<String, String> entry : environment.entrySet()) {
                    env.put(entry.getKey(), entry.getValue());
                    logger.debug("{}: setting environment variable {}=[redacted]", contextName, entry.getKey());
                }
                logger.debug("{}: added {} environment variable(s)", contextName, environment.size());
            }

            processBuilder.redirectErrorStream(true);
            logger.debug("{}: starting process for {} command...", contextName, operationName);
            process = processBuilder.start();

            // Read output in a separate thread to prevent buffer blocking
            final Process finalProcess = process;
            StringBuilder outputBuilder = new StringBuilder();
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(finalProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.debug("{} [{}]: {}", contextName, operationName, line);
                        synchronized (outputBuilder) {
                            if (outputBuilder.length() < MAX_OUTPUT_BUFFER_SIZE) { // Limit output buffer
                                outputBuilder.append(line).append("\n");
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.debug("{}: error reading process output: {}", contextName, e.getMessage());
                }
            });
            outputReader.setDaemon(true);
            outputReader.start();

            logger.debug("{}: waiting for {} command to complete (command_timeout: {}s)...",
                    contextName, operationName, commandTimeout.toSeconds());

            boolean finished = process.waitFor(commandTimeout.toSeconds(), TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - startTime;

            if (!finished) {
                logger.error("{}: {} command timed out after {}s ({}ms elapsed)",
                        contextName, operationName, commandTimeout.toSeconds(), duration);
                process.destroyForcibly();
                return false;
            }

            int exitCode = process.exitValue();

            if (exitCode == 0) {
                logger.debug("{}: {} command completed (exit code: 0, duration: {}ms)",
                        contextName, operationName, duration);
                return true;
            } else {
                logger.warn("{}: {} command failed (exit code: {}, duration: {}ms)",
                        contextName, operationName, exitCode, duration);
                synchronized (outputBuilder) {
                    if (outputBuilder.length() > 0) {
                        logger.debug("{}: {} command output:\n{}", contextName, operationName,
                                outputBuilder.toString().trim());
                    }
                }
                return false;
            }

        } catch (InterruptedException e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.warn("{}: {} command was interrupted after {}ms", contextName, operationName, duration);
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("{}: {} command threw exception after {}ms: {}",
                    contextName, operationName, duration, e.getMessage(), e);
            return false;
        } finally {
            // Ensure process is cleaned up
            if (process != null && process.isAlive()) {
                logger.debug("{}: cleaning up process for {} command", contextName, operationName);
                process.destroyForcibly();
            }
        }
    }

    /**
     * Shuts down the command executor.
     * Should be called during plugin shutdown.
     */
    public static void shutdown() {
        logger.debug("Shutting down command executor...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.debug("Command executor did not terminate gracefully, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.debug("Command executor shut down");
    }
}
