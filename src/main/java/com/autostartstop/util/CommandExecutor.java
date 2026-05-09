package com.autostartstop.util;

import com.autostartstop.PluginLogger;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;

/**
 * Utility class for executing shell commands with proper timeout handling, environment variable
 * support, and resource cleanup.
 *
 * <p>This class is thread-safe and can be used by multiple components (ShellServerControlApi,
 * ExecAction, etc.)
 */
public class CommandExecutor {
  private static final Logger logger = PluginLogger.get(CommandExecutor.class);

  /** Default command timeout (60 seconds). */
  public static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofSeconds(60);

  public static final int MAX_OUTPUT_BUFFER_SIZE = 10000;

  private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  private CommandExecutor() {}

  /**
   * Executes a shell command asynchronously.
   *
   * @param command The command to execute
   * @param operationName A name for logging purposes (e.g., "start", "stop", "exec")
   * @param contextName Context identifier for logging (e.g., server name, action name)
   * @return A CompletableFuture with true if command succeeded (exit code 0), false otherwise
   */
  public static CompletableFuture<Boolean> execute(
      String command, String operationName, String contextName) {
    return execute(command, operationName, contextName, null, null, DEFAULT_COMMAND_TIMEOUT);
  }

  /**
   * Executes a shell command asynchronously with full options.
   *
   * @param command The command to execute
   * @param operationName A name for logging purposes (e.g., "start", "stop", "exec")
   * @param contextName Context identifier for logging (e.g., server name, action name)
   * @param workingDirectory Optional working directory (null to use current directory)
   * @param environment Optional environment variables to add (null for none)
   * @param commandTimeout Command timeout duration
   * @return A CompletableFuture with true if command succeeded (exit code 0), false otherwise
   */
  public static CompletableFuture<Boolean> execute(
      String command,
      String operationName,
      String contextName,
      String workingDirectory,
      Map<String, String> environment,
      Duration commandTimeout) {

    long startTime = System.currentTimeMillis();

    try {
      logger.debug("{}: preparing to execute {} command", contextName, operationName);
      logger.debug("{}: command = {}", contextName, command);
      logger.debug("{}: command_timeout = {}s", contextName, commandTimeout.toSeconds());

      ProcessBuilder processBuilder =
          createProcessBuilder(command, contextName, workingDirectory, environment);

      logger.debug("{}: starting process for {} command...", contextName, operationName);
      Process process = processBuilder.start();
      logger.debug("{}: process started (pid: {})", contextName, process.pid());

      StringBuilder outputBuilder = new StringBuilder();
      CompletableFuture<Void> outputFuture =
          CompletableFuture.runAsync(
              () -> drainOutput(process, outputBuilder, contextName, operationName), executor);

      return process
          .onExit()
          .orTimeout(commandTimeout.toSeconds(), TimeUnit.SECONDS)
          .thenCompose(p -> outputFuture.thenApply(v -> p))
          .thenApply(
              p -> {
                long duration = System.currentTimeMillis() - startTime;
                int exitCode = p.exitValue();
                if (exitCode == 0) {
                  logger.debug(
                      "{}: {} command completed (exit code: 0, duration: {}ms)",
                      contextName,
                      operationName,
                      duration);
                  return true;
                } else {
                  logger.warn(
                      "{}: {} command failed (exit code: {}, duration: {}ms)",
                      contextName,
                      operationName,
                      exitCode,
                      duration);
                  if (!outputBuilder.isEmpty()) {
                    logger.debug(
                        "{}: {} command output:\n{}",
                        contextName,
                        operationName,
                        outputBuilder.toString().trim());
                  }
                  return false;
                }
              })
          .exceptionally(
              ex -> {
                long duration = System.currentTimeMillis() - startTime;
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                if (cause instanceof TimeoutException) {
                  logger.error(
                      "{}: {} command timed out after {}s ({}ms elapsed)",
                      contextName,
                      operationName,
                      commandTimeout.toSeconds(),
                      duration);
                } else {
                  logger.error(
                      "{}: {} command threw exception after {}ms: {}",
                      contextName,
                      operationName,
                      duration,
                      cause.getMessage(),
                      cause);
                }
                process.destroyForcibly();
                return false;
              });

    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      logger.error(
          "{}: {} command failed to start after {}ms: {}",
          contextName,
          operationName,
          duration,
          e.getMessage(),
          e);
      return CompletableFuture.completedFuture(false);
    }
  }

  private static ProcessBuilder createProcessBuilder(
      String command,
      String contextName,
      String workingDirectory,
      Map<String, String> environment) {

    ProcessBuilder processBuilder = new ProcessBuilder();

    String os = System.getProperty("os.name").toLowerCase();
    String shell;
    if (os.contains("win")) {
      shell =
          System.getenv("COMSPEC") != null
              ? System.getenv("COMSPEC")
              : "C:\\Windows\\System32\\cmd.exe";
      processBuilder.command(shell, "/c", command);
    } else {
      shell = "/bin/sh";
      processBuilder.command(shell, "-c", command);
    }
    logger.debug("{}: using shell '{}' on OS '{}'", contextName, shell, os);

    if (workingDirectory != null && !workingDirectory.isBlank()) {
      File workDir = new File(workingDirectory);
      if (workDir.exists() && workDir.isDirectory()) {
        processBuilder.directory(workDir);
        logger.debug("{}: working directory set to {}", contextName, workingDirectory);
      } else {
        logger.warn(
            "{}: working directory '{}' does not exist or is not a directory",
            contextName,
            workingDirectory);
      }
    }

    if (environment != null && !environment.isEmpty()) {
      Map<String, String> env = processBuilder.environment();
      for (Map.Entry<String, String> entry : environment.entrySet()) {
        env.put(entry.getKey(), entry.getValue());
        logger.debug("{}: setting environment variable {}=[redacted]", contextName, entry.getKey());
      }
      logger.debug("{}: added {} environment variable(s)", contextName, environment.size());
    }

    processBuilder.redirectErrorStream(true);
    return processBuilder;
  }

  private static void drainOutput(
      Process process, StringBuilder outputBuilder, String contextName, String operationName) {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        logger.debug("{} [{}]: {}", contextName, operationName, line);
        if (outputBuilder.length() < MAX_OUTPUT_BUFFER_SIZE) {
          outputBuilder.append(line).append("\n");
        }
      }
    } catch (Exception e) {
      logger.debug("{}: error reading process output: {}", contextName, e.getMessage());
    }
  }

  /** Shuts down the command executor. Should be called during plugin shutdown. */
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
