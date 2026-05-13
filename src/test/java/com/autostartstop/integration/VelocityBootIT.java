package com.autostartstop.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.autostartstop.Constants;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Boots a real Velocity proxy with the relocated AutoStartStop shadowJar across a matrix of
 * Velocity versions and asserts the plugin loads and finishes its own startup lifecycle without
 * errors.
 *
 * <p>Runs as part of {@code ./gradlew test}. Use {@code ./gradlew test -PfastTests} during fast
 * iteration to skip this class via the {@code velocity-boot} tag. Cached Velocity downloads live in
 * {@code build/velocity-cache}; clean it (or run {@code ./gradlew clean}) to refresh.
 *
 * <p>Matrix (each row is a {@code (version, build)} pair, resolved against PaperMC's Fill v3 API):
 *
 * <ul>
 *   <li>{@code 3.4.0 / 566} — stated minimum supported version (vanilla 3.4.0 install).
 *   <li>{@code 3.5.0-SNAPSHOT / 595} — latest 3.5 build, pinned for reproducibility.
 *   <li>{@code latest / latest} — bleeding edge; resolved at run time and tracks newer version
 *       families automatically.
 * </ul>
 */
@Tag("velocity-boot")
class VelocityBootIT {

  /**
   * Matches Velocity log lines that indicate a plugin lifecycle failure: Velocity's own {@code
   * VelocityPluginManager} errors and the plugin's catch-all in {@link
   * com.autostartstop.AutoStartStop#onProxyInitialization}.
   */
  private static final Pattern PLUGIN_LIFECYCLE_FAILURE =
      Pattern.compile(
          "(Can't load plugin|Error loading plugin|Unable to load plugin|The server will shut down|Can't create plugin|Failed to enable AutoStartStop)",
          Pattern.CASE_INSENSITIVE);

  /**
   * Matches the {@code ConfigLoader} summary line, capturing the number of rules parsed. We assert
   * this matches the number of rules in {@link #COVERAGE_CONFIG_RESOURCE}, which proves every rule
   * survived YAML parsing. Activation failures (per-rule) are caught separately by {@link
   * #PLUGIN_LIFECYCLE_FAILURE}.
   */
  private static final Pattern CONFIG_LOADED =
      Pattern.compile("Configuration loaded \\([^)]*rules:\\s*(\\d+)[^)]*\\)");

  /** Velocity logs this when its plugin manager has accepted and instantiated the plugin. */
  private static final String VELOCITY_LOAD_LINE = "Loaded plugin " + Constants.PLUGIN_ID;

  /**
   * The plugin's own end-of-init log line — proves {@code onProxyInitialization} ran to completion
   * without throwing. See {@code AutoStartStop.java}.
   */
  private static final String PLUGIN_READY_LINE = "AutoStartStop enabled successfully";

  private static final Duration BOOT_TIMEOUT = Duration.ofSeconds(120);
  private static final Duration ASYNC_ACTION_TIMEOUT = Duration.ofSeconds(15);

  /**
   * Test fixture seeded into {@code <runDirectory>/plugins/autostartstop/config.yml} before each
   * boot — see {@code src/test/resources/integration/coverage-config.yml}. The shipped default
   * config has every rule {@code enabled: false}, which means none of the trigger event
   * subscriptions exercise the Velocity event API surface. This config flips that on.
   */
  private static final String COVERAGE_CONFIG_RESOURCE = "integration/coverage-config.yml";

  /**
   * Number of rules defined in {@link #COVERAGE_CONFIG_RESOURCE} — one per trigger type the plugin
   * supports. Used to verify the {@code Configuration loaded ... rules: N} log line agrees.
   */
  private static final int COVERAGE_RULE_COUNT = 7;

  /**
   * Substring of the log line that the {@code cov_proxy_start} rule's {@code log} action emits when
   * the rule executor runs it during boot. Catches breakage in the trigger → rule executor → action
   * execution pipeline that registration alone wouldn't.
   */
  private static final String PROXY_START_ACTION_MARKER = "coverage:proxy_start";

  @ParameterizedTest(name = "Velocity {0} build {1}")
  @CsvSource({
    "3.4.0,           566",
    "3.5.0-SNAPSHOT,  595",
    "latest,          latest",
  })
  void pluginLoadsAgainstVelocity(String version, String build, @TempDir Path runDirectory)
      throws Exception {
    Path velocityJar = VelocityJarResolver.resolve(version, build);
    Path pluginJar = locatePluginJar();
    seedPluginConfig(runDirectory, COVERAGE_CONFIG_RESOURCE);

    try (VelocityProcess proxy = VelocityProcess.start(velocityJar, pluginJar, runDirectory)) {
      proxy.awaitReady(BOOT_TIMEOUT);
      // Wait for the proxy_start rule's async LogAction to complete. The rule executor runs
      // actions on its own thread pool, so this log line normally arrives after "Done (X.XXs)!".
      proxy.awaitLogContains(PROXY_START_ACTION_MARKER, ASYNC_ACTION_TIMEOUT);
      String logs = proxy.dumpLogs();

      assertTrue(
          logs.contains(VELOCITY_LOAD_LINE),
          () ->
              "Velocity did not log '"
                  + VELOCITY_LOAD_LINE
                  + "' — plugin metadata may be invalid or jar may be malformed.\n\n"
                  + logs);

      assertTrue(
          logs.contains(PLUGIN_READY_LINE),
          () ->
              "Plugin did not log '"
                  + PLUGIN_READY_LINE
                  + "' — onProxyInitialization did not run to completion.\n\n"
                  + logs);

      Matcher rulesMatch = CONFIG_LOADED.matcher(logs);
      assertTrue(
          rulesMatch.find(),
          () -> "ConfigLoader did not log a 'Configuration loaded' summary.\n\n" + logs);
      int parsedRules = Integer.parseInt(rulesMatch.group(1));
      assertEquals(
          COVERAGE_RULE_COUNT,
          parsedRules,
          () ->
              "ConfigLoader parsed "
                  + parsedRules
                  + " rules, expected "
                  + COVERAGE_RULE_COUNT
                  + " — coverage config may be malformed for this Velocity build.\n\n"
                  + logs);

      Matcher failure = PLUGIN_LIFECYCLE_FAILURE.matcher(logs);
      assertFalse(
          failure.find(),
          () ->
              "Velocity logged a plugin lifecycle failure ("
                  + (failure.hitEnd() ? "<unknown>" : failure.group())
                  + ").\n\n"
                  + logs);
    }
  }

  private static void seedPluginConfig(Path runDirectory, String resource) throws IOException {
    Path target = runDirectory.resolve("plugins/autostartstop/config.yml");
    Files.createDirectories(target.getParent());
    try (InputStream in = VelocityBootIT.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new IOException("Test resource not found on classpath: " + resource);
      }
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static Path locatePluginJar() {
    String jarProp = System.getProperty("autostartstop.jar");
    assertNotNull(
        jarProp,
        "System property 'autostartstop.jar' must be set by the Gradle test task. Run via "
            + "./gradlew test rather than invoking JUnit directly.");
    Path jar = Path.of(jarProp);
    assertTrue(Files.isRegularFile(jar), "AutoStartStop shadowJar not found at " + jar);
    return jar;
  }
}
