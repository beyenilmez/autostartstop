package com.autostartstop.integration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * A handle to a real Velocity proxy running as a child process.
 *
 * <p>Spawns Velocity from a downloaded jar with the plugin under test mounted into {@code
 * plugins/}, captures merged stdout/stderr asynchronously, and exposes a blocking {@link
 * #awaitReady} that returns once Velocity logs its boot-complete line. {@link #close} requests a
 * graceful shutdown via Velocity's {@code end} console command, falling back to {@code
 * destroyForcibly} if the proxy does not exit in time.
 */
final class VelocityProcess implements AutoCloseable {

  /**
   * Velocity logs its boot-complete line as {@code Done (X.XXs)!}. The decimal separator is
   * locale-sensitive ({@code DecimalFormat} default), so accept both '.' and ','.
   */
  private static final Pattern READY_PATTERN = Pattern.compile(".*Done \\([0-9.,]+s\\)!.*");

  private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(15);
  private static final Duration LOG_READER_JOIN = Duration.ofSeconds(2);
  private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

  private final Process process;
  private final List<String> logs = new CopyOnWriteArrayList<>();
  private final Thread logReader;

  private VelocityProcess(Process process) {
    this.process = process;
    this.logReader = new Thread(this::pumpLogs, "velocity-log-reader");
    this.logReader.setDaemon(true);
    this.logReader.start();
  }

  /**
   * Boots Velocity in {@code runDirectory} with {@code pluginJar} mounted at {@code
   * <runDirectory>/plugins/}.
   */
  static VelocityProcess start(Path velocityJar, Path pluginJar, Path runDirectory)
      throws IOException {
    Path pluginsDir = runDirectory.resolve("plugins");
    Files.createDirectories(pluginsDir);
    Files.copy(
        pluginJar,
        pluginsDir.resolve(pluginJar.getFileName()),
        StandardCopyOption.REPLACE_EXISTING);

    String javaBin =
        Path.of(System.getProperty("java.home"), "bin", "java").toAbsolutePath().toString();
    ProcessBuilder pb =
        new ProcessBuilder(
            javaBin,
            "-Xms128M",
            "-Xmx256M",
            // SimpleTerminalConsole probes for a real TTY; suppress JLine to keep logs plain text.
            "-Dterminal.jline=false",
            "-Dterminal.ansi=false",
            "-jar",
            velocityJar.toAbsolutePath().toString());
    pb.directory(runDirectory.toFile());
    pb.redirectErrorStream(true);
    return new VelocityProcess(pb.start());
  }

  private void pumpLogs() {
    try (BufferedReader r =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = r.readLine()) != null) {
        logs.add(line);
      }
    } catch (IOException ignored) {
      // process exited; pump is done
    }
  }

  /**
   * Blocks until Velocity logs its boot-complete line, the process exits early, or the timeout
   * elapses.
   */
  void awaitReady(Duration timeout) throws InterruptedException, TimeoutException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      for (String line : logs) {
        if (READY_PATTERN.matcher(line).matches()) {
          return;
        }
      }
      if (!process.isAlive()) {
        throw new IllegalStateException(
            "Velocity exited before ready (exit="
                + process.exitValue()
                + "). Logs:\n"
                + dumpLogs());
      }
      Thread.sleep(POLL_INTERVAL.toMillis());
    }
    throw new TimeoutException(
        "Velocity did not boot within " + timeout + ". Logs:\n" + dumpLogs());
  }

  /**
   * Blocks until any captured log line contains {@code substring}, or the timeout elapses. Useful
   * for asserting on output produced asynchronously by the rule executor after {@link #awaitReady}
   * has already returned.
   */
  void awaitLogContains(String substring, Duration timeout)
      throws InterruptedException, TimeoutException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      for (String line : logs) {
        if (line.contains(substring)) {
          return;
        }
      }
      Thread.sleep(POLL_INTERVAL.toMillis());
    }
    throw new TimeoutException(
        "Log line containing '"
            + substring
            + "' did not appear within "
            + timeout
            + ". Logs:\n"
            + dumpLogs());
  }

  /** Returns a snapshot of all log lines captured so far, joined by newlines. */
  String dumpLogs() {
    return String.join("\n", logs);
  }

  /** Returns a snapshot of the captured log lines. */
  List<String> logs() {
    return new ArrayList<>(logs);
  }

  @Override
  public void close() {
    if (process.isAlive()) {
      sendEndCommand();
      waitForGracefulExit();
    }
    joinLogReader();
  }

  private void sendEndCommand() {
    try (OutputStream stdin = process.getOutputStream()) {
      stdin.write("end\n".getBytes(StandardCharsets.UTF_8));
      stdin.flush();
    } catch (IOException ignored) {
      // best effort — proxy may already be exiting
    }
  }

  private void waitForGracefulExit() {
    try {
      if (!process.waitFor(SHUTDOWN_GRACE.toSeconds(), TimeUnit.SECONDS)) {
        process.destroyForcibly();
        process.waitFor(5, TimeUnit.SECONDS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
    }
  }

  private void joinLogReader() {
    try {
      logReader.join(LOG_READER_JOIN.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
